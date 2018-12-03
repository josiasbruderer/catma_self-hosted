/*   
 *   CATMA Computer Aided Text Markup and Analysis
 *   
 *   Copyright (C) 2009-2013  University Of Hamburg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.catma.ui.tagger;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.vaadin.sliderpanel.SliderPanel;
import org.vaadin.sliderpanel.SliderPanelBuilder;
import org.vaadin.sliderpanel.client.SliderMode;

import com.vaadin.data.HasValue.ValueChangeEvent;
import com.vaadin.data.HasValue.ValueChangeListener;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Slider.ValueOutOfBoundsException;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import de.catma.document.Corpus;
import de.catma.document.Range;
import de.catma.document.repository.Repository;
import de.catma.document.repository.Repository.RepositoryChangeEvent;
import de.catma.document.source.IndexInfoSet;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.TagInstanceInfo;
import de.catma.document.standoffmarkup.usermarkup.TagReference;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionManager;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.indexer.IndexedRepository;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagInstance;
import de.catma.tag.TagLibrary;
import de.catma.tag.TagManager;
import de.catma.tag.TagsetDefinition;
import de.catma.tag.Version;
import de.catma.ui.CatmaApplication;
import de.catma.ui.Slider;
import de.catma.ui.analyzer.AnalyzerProvider;
import de.catma.ui.client.ui.tagger.shared.ClientTagInstance;
import de.catma.ui.client.ui.tagger.shared.TextRange;
import de.catma.ui.component.IconButton;
import de.catma.ui.tabbedview.ClosableTab;
import de.catma.ui.tagger.MarkupPanel.TagInstanceSelectedListener;
import de.catma.ui.tagger.Tagger.TaggerListener;
import de.catma.ui.tagger.TaggerSplitPanel.SplitterPositionChangedEvent;
import de.catma.ui.tagger.TaggerSplitPanel.SplitterPositionChangedListener;
import de.catma.ui.tagger.annotationpanel.AnnotationPanel;
import de.catma.ui.tagger.pager.Page;
import de.catma.ui.tagger.pager.Pager;
import de.catma.ui.tagger.pager.PagerComponent;
import de.catma.ui.tagger.pager.PagerComponent.PageChangeListener;
import de.catma.ui.tagger.resourcepanel.ResourcePanel;
import de.catma.ui.tagmanager.TagsetSelectionListener;
import de.catma.util.Pair;

public class TaggerView extends HorizontalLayout 
	implements TaggerListener, ClosableTab {
	
	private Logger logger = Logger.getLogger(this.getClass().getName());
	private SourceDocument sourceDocument;
	private Tagger tagger;
	private Pager pager;
	private MarkupPanel markupPanel;
	private TagManager tagManager;
	private int taggerID;
	private Button btAnalyze;
	private Button btHelp;
	private Repository project;
	private PropertyChangeListener sourceDocChangedListener;
	private PagerComponent pagerComponent;
	private Slider linesPerPageSlider;
	private double totalLineCount;
	private PropertyChangeListener tagReferencesChangedListener;
	private int approxMaxLineLength;
	private int maxPageLengthInLines = 30;
	private int initialSplitterPositionInPixels = 785;
	
	private TaggerHelpWindow taggerHelpWindow = new TaggerHelpWindow();
	private CheckBox cbTraceSelection;
	private Button btClearSearchHighlights;
	
	public TaggerView(
			int taggerID, 
			SourceDocument sourceDocument, Repository project, 
			PropertyChangeListener sourceDocChangedListener) {
		this.taggerID = taggerID;
		this.tagManager = project.getTagManager();
		this.project = project;
		this.sourceDocument = sourceDocument;
		this.sourceDocChangedListener = sourceDocChangedListener;
		
		this.approxMaxLineLength = getApproximateMaxLineLengthForSplitterPanel(initialSplitterPositionInPixels);

		initComponents();
		initActions();
		initListeners();
		pager.setMaxPageLengthInLines(maxPageLengthInLines);
		try {
			tagger.setText(sourceDocument.getContent());
			totalLineCount = pager.getTotalLineCount();
			try {
				linesPerPageSlider.setValue((100.0/totalLineCount)*maxPageLengthInLines);
			} catch (ValueOutOfBoundsException toBeIgnored) {}
		} catch (IOException e) {
			((CatmaApplication)UI.getCurrent()).showAndLogError(
				Messages.getString("TaggerView.errorShowingSourceDoc"), e); //$NON-NLS-1$
		}
	}

	private void initListeners() {
		project.addPropertyChangeListener(
			RepositoryChangeEvent.sourceDocumentChanged,
			sourceDocChangedListener);
		
		this.tagReferencesChangedListener = new PropertyChangeListener() {
			
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getNewValue() != null) {
					
					@SuppressWarnings("unchecked")
					Pair<UserMarkupCollection, List<TagReference>> changeValue = 
							(Pair<UserMarkupCollection, List<TagReference>>) evt.getNewValue();
					
					List<TagReference> tagReferences = changeValue.getSecond(); 
					
					List<TagReference> relevantTagReferences = 
							new ArrayList<TagReference>();

					for (TagReference tr : tagReferences) {
						if (isRelevantTagReference(
								tr, 
								markupPanel.getUserMarkupCollections())) {
							relevantTagReferences.add(tr);
						}
					}
					tagger.setVisible(relevantTagReferences, true);

					Set<String> tagInstanceUuids = new HashSet<String>();

					for (TagReference tr : relevantTagReferences){
						tagInstanceUuids.add(tr.getTagInstance().getUuid());
					}
					
					if (tagInstanceUuids.size() == 1){
						markupPanel.showPropertyEditDialog(
								changeValue.getFirst(),
								relevantTagReferences.get(0).getTagInstance());
					}

				}
				else if (evt.getOldValue() != null) {
					@SuppressWarnings("unchecked")
					Pair<String, Collection<String>> changeValue = 
							(Pair<String, Collection<String>>) evt.getOldValue();
					
					Collection<String> annotationsIds = changeValue.getSecond(); 
					List<TagReference> tagReferences = new ArrayList<>();
					UserMarkupCollectionManager umcManager = 
						markupPanel.getUserMarkupCollectionManager();
					if (umcManager.contains(changeValue.getFirst())) {
						for (String annotationId : annotationsIds) {
							tagReferences.addAll(umcManager.getTagReferences(annotationId));
						}
						umcManager.removeTagInstance(annotationsIds, false);
					}
					
					tagger.setVisible(tagReferences, false);
					markupPanel.showTagInstanceInfo(
							tagReferences.toArray(new TagReference[]{}));
				}
			}
		};
		
		project.addPropertyChangeListener(
			RepositoryChangeEvent.tagReferencesChanged, 
			tagReferencesChangedListener);
	}

	private boolean isRelevantTagReference(TagReference tr,
			List<UserMarkupCollection> userMarkupCollections) {
		
		for (UserMarkupCollection umc : userMarkupCollections) {
			if (umc.hasTagInstance(tr.getTagInstanceID())) {
				return true;
			}
		}
		
		return false;
	}

	public void  analyzeDocument(){
		Corpus corpus = new Corpus(sourceDocument.toString());
		corpus.addSourceDocument(sourceDocument);
		for (UserMarkupCollection umc : 
			markupPanel.getUserMarkupCollections()) {
					UserMarkupCollectionReference userMarkupCollRef =
					sourceDocument.getUserMarkupCollectionReference(
							umc.getId());
			if (userMarkupCollRef != null) {
				corpus.addUserMarkupCollectionReference(
						userMarkupCollRef);
			}
		}	
		((AnalyzerProvider)UI.getCurrent()).analyze(
				corpus, (IndexedRepository)markupPanel.getRepository());	
	}


	
	private void initActions() {
		btClearSearchHighlights.addClickListener(new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				tagger.removeHighlights();
			}
		});
		cbTraceSelection.addValueChangeListener(new ValueChangeListener<Boolean>() {
			
			@Override
			public void valueChange(ValueChangeEvent<Boolean> event) {
				Boolean traceSelection = event.getValue();
				tagger.setTraceSelection(traceSelection);
			}
		});
		btAnalyze.addClickListener(new ClickListener() {	
			
			public void buttonClick(ClickEvent event) {	
				analyzeDocument();
			}
		});
		
		linesPerPageSlider.addValueListener(new ValueChangeListener<Double>() {
			
			public void valueChange(ValueChangeEvent<Double> event) {
				Double perCentValue = (Double)linesPerPageSlider.getValue();
				int lines = (int)((totalLineCount/100.0)*perCentValue);
				
				List<ClientTagInstance> absoluteTagInstances = 
						pager.getAbsoluteTagInstances();
				
				Page currentPage = pager.getCurrentPage();
				pager.setMaxPageLengthInLines(lines);
				//recalculate pages
				try {
					pager.setText(sourceDocument.getContent());
					int previousPageNumber = pager.getPageNumberFor(currentPage.getPageStart());
					tagger.setPage(previousPageNumber);					
					tagger.setTagInstancesVisible(absoluteTagInstances, true);

					pagerComponent.setPage(previousPageNumber);
				} catch (IOException e) {
					((CatmaApplication)UI.getCurrent()).showAndLogError(
						Messages.getString("TaggerView.errorShowingSourceDoc"), e); //$NON-NLS-1$
				}

			}
		});
		
		btHelp.addClickListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				
				if(taggerHelpWindow.getParent() == null){
					UI.getCurrent().addWindow(taggerHelpWindow);
				} else {
					UI.getCurrent().removeWindow(taggerHelpWindow);
				}
				
			}
		});
		
	}

	private void initComponents() {
		setSizeFull();
		
		VerticalLayout taggerPanel = new VerticalLayout();
		taggerPanel.setSizeFull();
		taggerPanel.setSpacing(true);
		taggerPanel.setMargin(new MarginInfo(true, true, true, false));

		btHelp = new IconButton(VaadinIcons.QUESTION_CIRCLE);
		btHelp.addStyleName("help-button"); //$NON-NLS-1$
//		btHelp.addStyleName(MaterialTheme.BUTTON_FLAT);
		
		IndexInfoSet indexInfoSet = 
			sourceDocument.getSourceContentHandler().getSourceDocumentInfo().getIndexInfoSet(); 

		pager = new Pager(taggerID, approxMaxLineLength, maxPageLengthInLines, 
				indexInfoSet.isRightToLeftWriting());
		
		tagger = new Tagger(taggerID, pager, this);
		tagger.addStyleName("tagger"); //$NON-NLS-1$
		tagger.setWidth("100%"); //$NON-NLS-1$
		
		taggerPanel.addComponent(tagger);
		taggerPanel.setExpandRatio(tagger, 1.0f);
		
		HorizontalLayout actionPanel = new HorizontalLayout();
		actionPanel.setSpacing(true);
		
		taggerPanel.addComponent(actionPanel);
		
		pagerComponent = new PagerComponent(
				pager, new PageChangeListener() {
					
			public void pageChanged(int number) {
				tagger.setPage(number);
			}
		});
		
		actionPanel.addComponent(btHelp);
		
		actionPanel.addComponent(pagerComponent);
		
		btAnalyze = new Button(Messages.getString("TaggerView.analyzeDocument")); //$NON-NLS-1$
		btAnalyze.addStyleName("primary-button"); //$NON-NLS-1$
		btAnalyze.setEnabled(project instanceof IndexedRepository);
		actionPanel.addComponent(btAnalyze);
		
		linesPerPageSlider =  new Slider(null, 1, 100, Messages.getString("TaggerView.percentPageSize")); //$NON-NLS-1$
		linesPerPageSlider.setWidth("150px"); //$NON-NLS-1$
		actionPanel.addComponent(linesPerPageSlider);
		
		cbTraceSelection = new CheckBox();
		cbTraceSelection.setIcon(VaadinIcons.AREA_SELECT);
		cbTraceSelection.setDescription(Messages.getString("TaggerView.allowMultipleDiscontSelectionsInfo")); //$NON-NLS-1$
		actionPanel.addComponent(cbTraceSelection);
		cbTraceSelection.addStyleName("tagger-trace-checkbox"); //$NON-NLS-1$

		btClearSearchHighlights = new Button(VaadinIcons.ERASER);
		btClearSearchHighlights.setDescription(Messages.getString("TaggerView.clearAllSearchHighlights")); //$NON-NLS-1$
		actionPanel.addComponent(btClearSearchHighlights);
		
		markupPanel = new MarkupPanel(
				project,tagger,
				
				new PropertyChangeListener() {
					
					public void propertyChange(PropertyChangeEvent evt) {
						boolean selected = evt.getNewValue() != null;
						@SuppressWarnings("unchecked")
						List<TagReference> tagReferences = 
							(List<TagReference>)(
									selected?evt.getNewValue():evt.getOldValue());
						if (!tagReferences.isEmpty()) {
							tagger.setVisible(tagReferences, selected);
						}
					}
				},
				new PropertyChangeListener() {
					
					public void propertyChange(PropertyChangeEvent evt) {
						@SuppressWarnings("unchecked")
						Set<TagDefinition> removedTagDefinitions = 
								(Set<TagDefinition>) evt.getOldValue();
						pager.removeTagInstances(removedTagDefinitions);
						tagger.setPage(pager.getCurrentPageNumber());
					}
				},
				new TagInstanceSelectedListener() {
					
					@Override
					public void tagInstanceSelected(TagInstance tagInstance) {
						tagger.setTagInstanceSelected(tagInstance);
					}
				},
				sourceDocument.getID());
		
		AnnotationPanel annotationPanel = new AnnotationPanel(project);
		
		final TaggerSplitPanel splitPanel = new TaggerSplitPanel();
		splitPanel.addComponent(taggerPanel);
//		splitPanel.addComponent(markupPanel);
		splitPanel.addComponent(annotationPanel);
		
		splitPanel.setSplitPosition(initialSplitterPositionInPixels, Unit.PIXELS);
		splitPanel.addStyleName("catma-tab-spacing"); //$NON-NLS-1$
		
		SplitterPositionChangedListener listener = new SplitterPositionChangedListener(){

			@Override
			public void positionChanged(SplitterPositionChangedEvent event) {
				float width = event.getPosition();
				
				// unit != Unit.PERCENTAGE && unit != Unit.PIXELS
				// TODO: if it is PERCENTAGE, work out the splitter position in pixels
				if (event.getPositionUnit() != Unit.PIXELS){
					String message = "Must use PIXELS Unit for split position"; //$NON-NLS-1$
					((CatmaApplication)UI.getCurrent()).showAndLogError(
							message, new IllegalArgumentException(message));
				}							
				
				int approxMaxLineLength = getApproximateMaxLineLengthForSplitterPanel(width);
				
				List<ClientTagInstance> absoluteTagInstances = pager.getAbsoluteTagInstances();
				
				Page currentPage = pager.getCurrentPage();
				pager.setApproxMaxLineLength(approxMaxLineLength);
				//recalculate pages
				try {
					pager.setText(sourceDocument.getContent());
					int previousPageNumber = pager.getPageNumberFor(currentPage.getPageStart());
					tagger.setPage(previousPageNumber);					
					tagger.setTagInstancesVisible(absoluteTagInstances, true);

					pagerComponent.setPage(previousPageNumber);
				} catch (IOException e) {
					((CatmaApplication)UI.getCurrent()).showAndLogError(
						Messages.getString("TaggerView.errorShowingSourceDoc"), e); //$NON-NLS-1$
				}							
			}
			
		};
		
		splitPanel.addListener(SplitterPositionChangedEvent.class,
                listener, SplitterPositionChangedListener.positionChangedMethod);
		
//		TreeData<SourceDocument> sourceDocsTreeData = new TreeData<>();
//		
//		Button btDrawer = new Button(VaadinIcons.GRID_SMALL);
//		
//		TreeGrid<SourceDocument> documents = new TreeGrid<>(new TreeDataProvider<>(sourceDocsTreeData));
//		documents.setSizeFull();
//		documents.addColumn(sd -> sd.toString());
//		
//		try {
//			sourceDocsTreeData.addRootItems(project.getSourceDocuments());
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		PopupView drawer = new PopupView(VaadinIcons.GRID_SMALL.getHtml(), new Label("Test"));
//		drawer.addStyleName("catma-drawer");
//		drawer.setSizeFull();
//		drawer.setWidth("100px");
//		addComponent(btDrawer);
		
		SliderPanel drawer = new SliderPanelBuilder(
			new ResourcePanel(project, sourceDocument))
				.mode(SliderMode.LEFT).expanded(false).build();
		
		addComponent(drawer);
		
		addComponent(splitPanel);
		setExpandRatio(splitPanel, 1.0f);
	}
	
	public int getApproximateMaxLineLengthForSplitterPanel(float width){
		// based on ratio of 80:550
		int approxMaxLineLength = (int) (width * 0.135);
		
		return approxMaxLineLength;
	}

	public SourceDocument getSourceDocument() {
		return sourceDocument;
	}
	
	public UserMarkupCollection openUserMarkupCollection(
			UserMarkupCollectionReference userMarkupCollectionRef) throws IOException {
		UserMarkupCollection umc = project.getUserMarkupCollection(userMarkupCollectionRef);
		openUserMarkupCollection(umc);
		return umc;
	}
	
	public void openUserMarkupCollection(
			UserMarkupCollection userMarkupCollection) {
		markupPanel.openUserMarkupCollection(userMarkupCollection);
		// hier die library holen ueber tagset loopen und dann openTgsetDef ausfuehren
		/*for (TagsetDefinition ts : userMarkupCollection.getTagLibrary()) {
			geht so da TagLib ein Iterable ist...
			und dann sowat 	:
			
								TagLibrary tagLibrary = repository.getTagLibraryFor(tagsetDefinition.getUuid(), null);--> version vielleicht hier doch dabei und nicht null
								if (tagLibrary != null) {
										((CatmaApplication) UI.getCurrent()).openTagLibrary(repository, tagLibrary, false);
										((CatmaApplication) UI.getCurrent()).addTagsetToActiveDocument(tagLibrary.getTagsetDefinition(tagsetDefinition.getUuid()),
												tagsetSelectionListener);
									}
								}.
		}*/
	}

	public void openTagsetDefinition(
			CatmaApplication catmaApplication, String uuid, Version version) throws IOException {
		TagLibrary tagLibrary = project.getTagLibraryFor(uuid, version);
		if (tagLibrary != null) {
			catmaApplication.openTagLibrary(project, tagLibrary, false);
			openTagsetDefinition(catmaApplication, tagLibrary.getTagsetDefinition(uuid));
		}
	}
	

	public void openTagsetDefinition(CatmaApplication catmaApplication, TagsetDefinition tagsetDefinition, TagsetSelectionListener tagsetSelectionListener){
		markupPanel.addOrUpdateTagsetDefinition(catmaApplication, tagsetDefinition,tagsetSelectionListener);
	}
	
	public void openTagsetDefinition(CatmaApplication catmaApplication, TagsetDefinition tagsetDefinition){
		markupPanel.addOrUpdateTagsetDefinition(catmaApplication, tagsetDefinition);
	}

	public void close() {
		markupPanel.close();
		project.removePropertyChangeListener(
				RepositoryChangeEvent.sourceDocumentChanged,
				sourceDocChangedListener);
		project.removePropertyChangeListener(
				RepositoryChangeEvent.tagReferencesChanged, 
				tagReferencesChangedListener);

		sourceDocChangedListener = null;
	}
	
	public void tagInstanceAdded(
			ClientTagInstance clientTagInstance) {
		TagLibrary tagLibrary =
				markupPanel.getCurrentWritableUserMarkupCollection().getTagLibrary();
		
		if (tagLibrary.getTagDefinition(clientTagInstance.getTagDefinitionID())
				== null) {
			TagsetDefinition tagsetDef =
					markupPanel.getTagsetDefinition(
							clientTagInstance.getTagDefinitionID());
			if (tagLibrary.getTagsetDefinition(tagsetDef.getUuid()) == null) {
				tagManager.addTagsetDefinition(
						tagLibrary, new TagsetDefinition(tagsetDef));
			}
			else {
				//this should not happen, because we update TagsetDefinitions immedately
				logger.severe(
					"TagDefinition not found, but TagsetDefinition is present, " + //$NON-NLS-1$
					"expected was either a complete TagsetDefiniton or no TagsetDefinition," + //$NON-NLS-1$
					"adding TagDefinition instead of TagsetDefinition now: orig TagsetDef: " +  //$NON-NLS-1$
					tagsetDef + " orig TagDef: " +tagsetDef.getTagDefinition( //$NON-NLS-1$
							clientTagInstance.getTagDefinitionID()));
				
				tagManager.addTagDefinition(
					tagLibrary.getTagsetDefinition(tagsetDef.getUuid()),
					new TagDefinition(
						tagsetDef.getTagDefinition(
							clientTagInstance.getTagDefinitionID())));
			}
		}
		
		TagDefinition tagDef = 
				tagLibrary.getTagDefinition(
						clientTagInstance.getTagDefinitionID());
		
		TagInstance ti = 
			new TagInstance(clientTagInstance.getInstanceID(), tagDef);
		
		List<TagReference> tagReferences = new ArrayList<TagReference>();
		
		try {
			String userMarkupCollectionUuid = 
				markupPanel.getCurrentWritableUserMarkupCollection().getId();
			for (TextRange tr : clientTagInstance.getRanges()) {
				Range r = new Range(tr.getStartPos(), tr.getEndPos());
				TagReference ref = 
						new TagReference(ti, sourceDocument.getID(), r, userMarkupCollectionUuid);
				tagReferences.add(ref);
			}
			markupPanel.addTagReferences(tagReferences);
		} catch (URISyntaxException e) {
			((CatmaApplication)UI.getCurrent()).showAndLogError(
				Messages.getString("TaggerView.errorAddingAnnotations"), e); //$NON-NLS-1$
		}
	}

	public void show(Range range) {
		try {
			int startPage = pager.getPageNumberFor(range.getStartPoint());
			int endPage = pager.getPageNumberFor(range.getEndPoint());
			
			if (startPage != endPage) { // range spans several pages
				Double perCentValue = 100.0;

				// increase page zoom so that the highlighter fits into one page
				while(startPage != endPage) {
					pager.setMaxPageLengthInLines(pager.getMaxPageLengthInLines()+5);
					try {
						pager.setText(sourceDocument.getContent());
					} catch (IOException e) {
						logger.log(Level.SEVERE, "error adjusting  page zoom", e); //$NON-NLS-1$
					}
	
					startPage = pager.getPageNumberFor(range.getStartPoint());
					endPage = pager.getPageNumberFor(range.getEndPoint());
					
					perCentValue = 
						((double)pager.getApproxMaxLineLength())/(((double)totalLineCount)/100.0);
				}
				// set computed zoom value
				linesPerPageSlider.setValue(perCentValue);
			}
			// set page that contains the range to be highlighted
			int pageNumber = pager.getStartPageNumberFor(range);
			pagerComponent.setPage(pageNumber);
			
			tagger.highlight(range);
		} catch (ValueOutOfBoundsException e) {
			logger.log(Level.SEVERE, "error during highlighting", e); //$NON-NLS-1$
		}
	}
	
	public void tagInstanceSelected(String instancePartID, String lineID) {
		markupPanel.showTagInstanceInfo(
			pager.getCurrentPage().getTagInstanceIDs(instancePartID, lineID), 
			ClientTagInstance.getTagInstanceIDFromPartId(instancePartID));
	}
	
	@Override
	public void tagInstanceSelected(Set<String> tagInstanceIDs) {
		markupPanel.showTagInstanceInfo(tagInstanceIDs, null);
	}
	
	public void addClickshortCuts() { /* noop*/	}
	
	public void removeClickshortCuts() { /* noop*/ }

	public void setSourceDocument(SourceDocument sd) {
		this.sourceDocument = sd;
	}

	@Override
	public TagInstanceInfo getTagInstanceInfo(String tagInstanceId) {
		return markupPanel.getTagInstanceInfo(tagInstanceId);
	}
}
