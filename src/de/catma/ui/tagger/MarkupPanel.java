package de.catma.ui.tagger;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import com.vaadin.data.Container;
import com.vaadin.event.DataBoundTransferable;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.ui.AbstractSelect.AcceptItem;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.VerticalLayout;

import de.catma.core.document.repository.Repository;
import de.catma.core.document.standoffmarkup.usermarkup.TagReference;
import de.catma.core.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.core.tag.TagDefinition;
import de.catma.core.tag.TagManager;
import de.catma.core.tag.TagsetDefinition;
import de.catma.ui.tagger.MarkupCollectionsPanel.MarkupCollectionPanelEvent;
import de.catma.ui.tagmanager.ColorButtonColumnGenerator.ColorButtonListener;
import de.catma.ui.tagmanager.TagsetTree;

public class MarkupPanel extends VerticalLayout {

	private TagsetTree tagsetTree;
	private TabSheet tabSheet;
	private MarkupCollectionsPanel markupCollectionsPanel;
	private boolean init = true;
	
	public MarkupPanel(
			TagManager tagManager,
			Repository repository, ColorButtonListener colorButtonListener, 
			PropertyChangeListener tagDefinitionSelectionListener) {
		initComponents(
				tagManager, repository, 
				colorButtonListener, tagDefinitionSelectionListener);
	}

	private void initComponents(
			final TagManager tagManager, Repository repository, 
			ColorButtonListener colorButtonListener, 
			PropertyChangeListener tagDefinitionSelectionListener) {
		tabSheet = new TabSheet();
		VerticalLayout currentlyActiveMarkupPanel = new VerticalLayout();
		currentlyActiveMarkupPanel.setSpacing(true);
		tabSheet.addTab(currentlyActiveMarkupPanel, "Currently active Tagsets");
		
		tagsetTree = new TagsetTree(tagManager, null, false, colorButtonListener);
		currentlyActiveMarkupPanel.addComponent(tagsetTree);

		final Label currentlyWritableUserMarkupCollectionLabel = new Label(
				"Currently writable Markup Collection: ");
		currentlyActiveMarkupPanel.addComponent(
				currentlyWritableUserMarkupCollectionLabel);
		
		markupCollectionsPanel = 
				new MarkupCollectionsPanel(tagManager, repository);
		markupCollectionsPanel.addPropertyChangeListener(
				MarkupCollectionPanelEvent.tagDefinitionSelected, 
				tagDefinitionSelectionListener);
		markupCollectionsPanel.addPropertyChangeListener(
				MarkupCollectionPanelEvent.userMarkupCollectionSelected, 
				new PropertyChangeListener() {
			
			public void propertyChange(PropertyChangeEvent evt) {
				currentlyWritableUserMarkupCollectionLabel.setValue(
						"Currently writable Markup Collection: " + evt.getNewValue());
			}
		});
		
		tabSheet.addTab(
				markupCollectionsPanel, "Currently active Markup Collections");
		
		addComponent(tabSheet);
	}
	
	@Override
	public void attach() {
		super.attach();
		if (init) {
			//TODO: handle TagLibrary close operations, i.e. remove corresponding TagsetDefs
			//TODO: maybe accept drags only from the TaggerView table, drag only tagset defs
			tagsetTree.getTagTree().setDropHandler(new DropHandler() {
				
				public AcceptCriterion getAcceptCriterion() {
					
					return AcceptItem.ALL;
				}
				
				public void drop(DragAndDropEvent event) {
					DataBoundTransferable transferable = 
							(DataBoundTransferable)event.getTransferable();
					
	                if (!(transferable.getSourceContainer() 
	                		instanceof Container.Hierarchical)) {
	                    return;
	                }
	
	                Object sourceItemId = transferable.getItemId();
	                
	                if (sourceItemId instanceof TagsetDefinition) {
	                	
	                	TagsetDefinition incomingTagsetDef =
	                			(TagsetDefinition)sourceItemId;
	                	
	                	markupCollectionsPanel.updateTagsetDefinition(
	                			incomingTagsetDef);
	                	tagsetTree.addTagsetDefinition(
	                			(TagsetDefinition)sourceItemId);
	                }
				}
			});
			init = false;
		}
	}

	public void openUserMarkupCollection(
			UserMarkupCollection userMarkupCollection) {
		markupCollectionsPanel.openUserMarkupCollection(
				userMarkupCollection);
	}

	public void close() {
		tagsetTree.close();
	}
	
	public TagDefinition getTagDefinition(String tagDefinitionID) {
		return tagsetTree.getTagDefinition(tagDefinitionID);
	}

	public void addTagReferences(List<TagReference> tagReferences) {
		markupCollectionsPanel.addTagReferences(tagReferences);
	}

	public TagsetDefinition getTagsetDefinition(TagDefinition tagDefinition) {
		return tagsetTree.getTagsetDefinition(tagDefinition);
	}

	public UserMarkupCollection getCurrentWritableUserMarkupCollection() {
		return markupCollectionsPanel.getCurrentWritableUserMarkupCollection();
	}	
}
