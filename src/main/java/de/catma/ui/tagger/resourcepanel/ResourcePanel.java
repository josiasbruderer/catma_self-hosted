package de.catma.ui.tagger.resourcepanel;

import java.util.Collection;

import com.vaadin.data.TreeData;
import com.vaadin.data.provider.TreeDataProvider;
import com.vaadin.ui.Grid.Column;
import com.vaadin.ui.Label;
import com.vaadin.ui.TreeGrid;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.renderers.ButtonRenderer;
import com.vaadin.ui.renderers.ClickableRenderer.RendererClickEvent;
import com.vaadin.ui.renderers.HtmlRenderer;

import de.catma.document.repository.Repository;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.ui.component.actiongrid.ActionGridComponent;

public class ResourcePanel extends VerticalLayout {
	
	private Repository project;
	private TreeGrid<DocumentTreeItem> documentTree;
	private TreeData<DocumentTreeItem> documentsData;

	public ResourcePanel(Repository project, SourceDocument currentlySelectedSourceDocument) {
		super();
		this.project = project;
		initComponents();
		initData(currentlySelectedSourceDocument);
	}

	private void initData(SourceDocument currentlySelectedSourceDocument) {
		try {
			documentsData = new TreeData<>();
			
			Collection<SourceDocument> documents = project.getSourceDocuments(); 
			documentsData.addRootItems(
				documents
				.stream()
				.map(document -> new DocumentDataItem(document, document.equals(currentlySelectedSourceDocument))));
			
			for (DocumentTreeItem documentDataItem : documentsData.getRootItems()) {
				for (UserMarkupCollectionReference umcRef : 
					((DocumentDataItem)documentDataItem).getDocument().getUserMarkupCollectionRefs()) {
					documentsData.addItem(documentDataItem, new CollectionDataItem(umcRef));
				}
			}
			
			documentTree.setDataProvider(new TreeDataProvider<>(documentsData));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void initComponents() {
		Label documentTreeLabel = new Label("Documents & Annotations");
		documentTree = new TreeGrid<>();
		documentTree.addStyleName("annotate-resource-grid");
		
		ButtonRenderer<DocumentTreeItem> documentSelectionRenderer = 
				new ButtonRenderer<DocumentTreeItem>(
					documentSelectionClick -> handleVisibilityClickEvent(documentSelectionClick));
		documentSelectionRenderer.setHtmlContentAllowed(true);
		Column<DocumentTreeItem, String> selectionColumn = 
			documentTree.addColumn(
				documentTreeItem -> documentTreeItem.getSelectionIcon(),
				documentSelectionRenderer);
		
		documentTree.setHierarchyColumn(selectionColumn);
		
		documentTree
			.addColumn(documentTreeItem -> documentTreeItem.getName())
			.setCaption("Name")
			.setExpandRatio(3);
		
		documentTree
			.addColumn(documentTreeItem -> documentTreeItem.getIcon(), new HtmlRenderer());
		
		ActionGridComponent<TreeGrid<DocumentTreeItem>> documentActionGridComponent = 
				new ActionGridComponent<TreeGrid<DocumentTreeItem>>(documentTreeLabel, documentTree);
		
		addComponent(documentActionGridComponent);
		
		
	}

	private void handleVisibilityClickEvent(RendererClickEvent<DocumentTreeItem> documentSelectionClick) {
		DocumentTreeItem selectedItem = documentSelectionClick.getItem();
		selectedItem.setSelected(!selectedItem.isSelected());
		
		if (selectedItem.isSingleSelection()) {
			for (DocumentTreeItem item : documentsData.getRootItems()) {
				if (!item.equals(selectedItem)) {
					item.setSelected(false);
					for (DocumentTreeItem child : documentsData.getChildren(item)) {
						child.setSelected(false);
					}
				}
			}
		}		
		documentTree.getDataProvider().refreshAll();
	}
	
	
	
	
	

}
