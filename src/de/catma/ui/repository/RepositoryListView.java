package de.catma.ui.repository;

import java.util.HashMap;
import java.util.Map;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Table;
import com.vaadin.ui.VerticalLayout;

import de.catma.CatmaApplication;
import de.catma.document.repository.Repository;
import de.catma.document.repository.RepositoryManager;
import de.catma.document.repository.RepositoryReference;
import de.catma.ui.tabbedview.TabComponent;

public class RepositoryListView extends VerticalLayout implements TabComponent {

	private RepositoryManager repositoryManager;
	private Table repositoryTable;
	private Button openBt;
	
	public RepositoryListView(RepositoryManager repositoryManager) {
		this.repositoryManager = repositoryManager;
		initComponents();
		initActions();
	}

	private void initActions() {
		openBt.addListener(new ClickListener() {
			
			public void buttonClick(ClickEvent event) {
				RepositoryReference repositoryReference = 
						(RepositoryReference)repositoryTable.getValue();
				if (repositoryManager.isOpen(repositoryReference)) {
					getWindow().showNotification(
							"Information", "Repository is already open.");
				}
				else {
					if (repositoryReference.isAuthenticationRequired()) {
						AuthenticationDialog authDialog = 
								new AuthenticationDialog(
										"Please authenticate yourself", 
										repositoryReference, repositoryManager);
						authDialog.show(getApplication().getMainWindow());
					}
					else {
						try {
							Map<String,String> userIdentification = 
									new HashMap<String, String>(1);
							userIdentification.put(
								"user.ident", System.getProperty("user.name"));
							userIdentification.put(
								"user.name", System.getProperty("user.name"));
							
							Repository repository = 
									repositoryManager.openRepository(
											repositoryReference, userIdentification);
							
							((CatmaApplication)getApplication()).openRepository(
									repository);
							
						} catch (Exception e) {
							((CatmaApplication)getApplication()).showAndLogError(
								"Error opening the repository!", e);
						}
					}
				}
			}
		});
		
		repositoryTable.addListener(new Table.ValueChangeListener() {
            public void valueChange(ValueChangeEvent event) {
            	openBt.setEnabled(event.getProperty().getValue() != null);
            }
		});
	}


	private void initComponents() {
		repositoryTable = new Table("Available Repositories");
		BeanItemContainer<RepositoryReference> container = 
				new BeanItemContainer<RepositoryReference>(RepositoryReference.class);
		
		for (RepositoryReference ref : repositoryManager.getRepositoryReferences()) {
			container.addBean(ref);
		}
		
		repositoryTable.setContainerDataSource(container);

		repositoryTable.setVisibleColumns(new Object[] {"name"});
		repositoryTable.setColumnHeaderMode(Table.COLUMN_HEADER_MODE_HIDDEN);
		repositoryTable.setSelectable(true);
		repositoryTable.setMultiSelect(false);
		repositoryTable.setPageLength(3);
		repositoryTable.setImmediate(true);
		
		addComponent(repositoryTable);
		setMargin(true);
		setSpacing(true);
		
		
		openBt = new Button("Open");
		openBt.setImmediate(true);
		
		
		addComponent(openBt);
		setComponentAlignment(openBt, Alignment.TOP_RIGHT);
		
		if (container.size() > 0) {
			repositoryTable.setValue(container.getIdByIndex(0));
		}
		else {
			openBt.setEnabled(false);
		}
	}
	
	public RepositoryManager getRepositoryManager() {
		return repositoryManager;
	}
	
	public void addClickshortCuts() { /* noop*/	}
	
	public void removeClickshortCuts() { /* noop*/ }

}
