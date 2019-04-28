package de.catma.ui.analyzenew;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.SerializationUtils;

import com.vaadin.data.TreeData;
import com.vaadin.data.provider.TreeDataProvider;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
//import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TreeGrid;
//import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.themes.ValoTheme;
import de.catma.document.repository.Repository;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.queryengine.result.GroupedQueryResult;
import de.catma.queryengine.result.QueryResult;
import de.catma.queryengine.result.QueryResultRow;
import de.catma.queryengine.result.QueryResultRowArray;
import de.catma.queryengine.result.TagQueryResult;
import de.catma.queryengine.result.TagQueryResultRow;
import de.catma.ui.analyzenew.treehelper.CollectionItem;
import de.catma.ui.analyzenew.treehelper.DocumentItem;
import de.catma.ui.analyzenew.treehelper.RootItem;
import de.catma.ui.analyzenew.treehelper.SingleItem;
import de.catma.ui.analyzenew.treehelper.TreeRowItem;
import de.catma.ui.layout.HorizontalLayout;
import de.catma.ui.layout.VerticalLayout;

public class ResultPanelNew extends Panel {

	private static enum TreePropertyName {
		caption, frequency, visibleInKwic,;
	}

	public static interface ResultPanelCloseListener {
		public void closeRequest(ResultPanelNew resultPanelNew);
	}

	private VerticalLayout contentVerticalLayout;

	private TreeData<TreeRowItem> tagData;
	private TreeGrid<TreeRowItem> treeGridTag;

	private TreeData<TreeRowItem> phraseData;
	private TreeGrid<TreeRowItem> treeGridPhrase;

	private TreeData<TreeRowItem> propData;
	private TreeGrid<TreeRowItem> treeGridProperty;

	private Label queryInfo;
	private HorizontalLayout groupedIcons;
	private Button caretRightBt;
	private Button caretDownBt;
	private Button trashBt;
	private Button optionsBt;
	private Panel treeGridPanel;
	private QueryResult queryResult;
	private String queryAsString;
	private Repository repository;
	private ViewID currentView;
	private ResultPanelCloseListener resultPanelCloseListener;

	public ResultPanelNew(Repository repository, QueryResult result, String queryAsString,
			ResultPanelCloseListener resultPanelCloseListener) throws Exception {

		this.repository = repository;
		this.queryResult = result;
		this.queryAsString = queryAsString;
		this.resultPanelCloseListener = resultPanelCloseListener;

		initComponents();
		initListeners();

		if (queryAsString.contains("tag=")) {
			setDataTagStyle();
			setCurrentView(ViewID.tag);
			treeGridPanel.setContent(treeGridTag);
		}

		if (queryAsString.contains("property=")) {
			setDataPropertyStyle();
			setCurrentView(ViewID.property);
			treeGridPanel.setContent(treeGridProperty);
		}
		if (queryAsString.contains("wild=")) {
			setDataPhraseStyle();
			setCurrentView(ViewID.phrase);
			treeGridPanel.setContent(treeGridPhrase);
		}

	}

	@SuppressWarnings("unchecked")
	public TreeData<TreeRowItem> getCurrentTreeGridData() {

		TreeGrid<TreeRowItem> currentTreeGrid = (TreeGrid<TreeRowItem>) treeGridPanel.getContent();
		TreeDataProvider<TreeRowItem> dataProvider = (TreeDataProvider<TreeRowItem>) currentTreeGrid.getDataProvider();
		TreeData<TreeRowItem> treeData = (TreeData<TreeRowItem>) dataProvider.getTreeData();
		return copyTreeData(treeData);
	}

	private TreeData<TreeRowItem> copyTreeData(TreeData<TreeRowItem> treeData) {
		TreeData<TreeRowItem> toReturn = new TreeData<TreeRowItem>();
		List<TreeRowItem> roots = treeData.getRootItems();
		for (TreeRowItem root : roots) {
			toReturn.addItem(null, root);
			List<TreeRowItem> childrenOne = treeData.getChildren(root);
			List<TreeRowItem> copyOfChildrenOne = new ArrayList<>(childrenOne);
			toReturn.addItems(root, copyOfChildrenOne);
			// add dummy on doclevel for phrase query
			if (treeData.getChildren(childrenOne.get(0)).isEmpty()) {

				for (TreeRowItem childOne : copyOfChildrenOne) {
					SingleItem dummy = new SingleItem();
					dummy.setTreeKey(RandomStringUtils.randomAlphanumeric(7));
					toReturn.addItem(childOne, dummy);
				}

			} else {
				for (TreeRowItem childOne : copyOfChildrenOne) {
					List<TreeRowItem> childrenTwo = treeData.getChildren(childOne);
					List<TreeRowItem> copyOfChildrenTwo = new ArrayList<>(childrenTwo);
					toReturn.addItems(childOne, copyOfChildrenTwo);
					for (TreeRowItem childTwo : copyOfChildrenTwo) {
						SingleItem dummy = new SingleItem();
						dummy.setTreeKey(RandomStringUtils.randomAlphanumeric(7));
						toReturn.addItem(childTwo, dummy);

					}

				}
			}
		}
		return toReturn;

	}

	private void setCurrentView(ViewID currentView) {
		this.currentView = currentView;
	}

	public ViewID getCurrentView() {
		return this.currentView;
	}

	private void initComponents() {
		this.setWidth(80, Unit.PERCENTAGE);
		contentVerticalLayout = new VerticalLayout();
		contentVerticalLayout.addStyleName("analyze_queryresultpanel__card");

		addStyleName("analyze_queryresultpanel__card_frame");
		setContent(contentVerticalLayout);

		treeGridTag = new TreeGrid<TreeRowItem>();
		treeGridTag.addStyleNames("annotation-details-panel-annotation-details-grid",
				"flat-undecorated-icon-buttonrenderer", "no-focused-before-border");

		treeGridPhrase = new TreeGrid<TreeRowItem>();
		treeGridPhrase.addStyleNames("annotation-details-panel-annotation-details-grid",
				"flat-undecorated-icon-buttonrenderer", "no-focused-before-border");

		treeGridProperty = new TreeGrid<TreeRowItem>();
		treeGridProperty.addStyleNames("annotation-details-panel-annotation-details-grid",
				"flat-undecorated-icon-buttonrenderer", "no-focused-before-border");

		createResultInfoBar();
		createButtonBar();
		treeGridPanel = new Panel();
	}

	private void createResultInfoBar() {
		QueryResultRowArray resultRowArrayArrayList = queryResult.asQueryResultRowArray();
		int resultSize = resultRowArrayArrayList.size();
		queryInfo = new Label(queryAsString + "(" + resultSize + ")");
		queryInfo.addStyleName("analyze_queryresultpanel_infobar");
		contentVerticalLayout.addComponent(queryInfo);
	}

	private void createButtonBar() {
		groupedIcons = new HorizontalLayout();

		caretRightBt = new Button(VaadinIcons.CARET_RIGHT);
		caretRightBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);

		caretDownBt = new Button(VaadinIcons.CARET_DOWN);
		caretDownBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);

		optionsBt = new Button(VaadinIcons.ELLIPSIS_V);
		optionsBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);

		trashBt = new Button(VaadinIcons.TRASH);
		trashBt.addStyleName(ValoTheme.BUTTON_BORDERLESS);

		groupedIcons.addComponents(trashBt, optionsBt, caretRightBt);
		groupedIcons.addStyleName("analyze_queryresultpanel_buttonbar");
		contentVerticalLayout.addComponent(groupedIcons);

	}

	private void initListeners() {

		caretRightBt.addClickListener(new ClickListener() {

			public void buttonClick(ClickEvent event) {
				contentVerticalLayout.addComponent(treeGridPanel);
				groupedIcons.replaceComponent(caretRightBt, caretDownBt);

			}
		});

		caretDownBt.addClickListener(new ClickListener() {

			public void buttonClick(ClickEvent event) {
				contentVerticalLayout.removeComponent(treeGridPanel);
				groupedIcons.replaceComponent(caretDownBt, caretRightBt);
			}
		});

		optionsBt.addClickListener(new ClickListener() {

			public void buttonClick(ClickEvent event) {
				try {
					swichView();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		trashBt.addClickListener(new ClickListener() {

			public void buttonClick(ClickEvent event) {
				resultPanelCloseListener.closeRequest(ResultPanelNew.this);
			}
		});

	}

	private void setDataTagStyle() throws Exception {

		tagData = new TreeData<>();
		tagData = populateTreeDataWithTags(repository, tagData, queryResult);
		TreeDataProvider<TreeRowItem> dataProvider = new TreeDataProvider<>(tagData);

		treeGridTag.addColumn(TreeRowItem::getShortenTreeKey).setCaption("Tag").setId("tagID");
		treeGridTag.getColumn("tagID").setExpandRatio(5);

		treeGridTag.addColumn(TreeRowItem::getFrequency).setCaption("Frequency").setId("freqID");
		treeGridTag.getColumn("freqID").setExpandRatio(1);

		dataProvider.refreshAll();
		treeGridTag.setDataProvider(dataProvider);
		treeGridTag.recalculateColumnWidths();
		treeGridTag.setWidth("100%");
		treeGridTag.setCaption(queryAsString);

		treeGridPanel.setContent(treeGridTag);

		setDataPhraseStyle();

	}

	private void setDataPhraseStyle() {

		phraseData = new TreeData<>();

		Set<GroupedQueryResult> resultAsSet = queryResult.asGroupedSet();

		for (GroupedQueryResult onePhraseGroupedQueryResult : resultAsSet) {

			String phrase = (String) onePhraseGroupedQueryResult.getGroup();
			RootItem rootPhrase = new RootItem();

			Set<String> allDocsForThatPhrase = onePhraseGroupedQueryResult.getSourceDocumentIDs();

			rootPhrase.setTreeKey(phrase);

			QueryResultRowArray queryResultArray = transformGroupedResultToArray(onePhraseGroupedQueryResult);

			rootPhrase.setRows(queryResultArray);
			phraseData.addItem(null, rootPhrase);
			ArrayList<TreeRowItem> allDocuments = new ArrayList<>();

			for (String docID : allDocsForThatPhrase) {
				GroupedQueryResult oneDocGroupedQueryResult = onePhraseGroupedQueryResult.getSubResult(docID);
				DocumentItem docItem = new DocumentItem();

				try {
					String docName = repository.getSourceDocument(docID).toString();
					docItem.setTreeKey(docName);
				} catch (Exception e) {

					e.printStackTrace();
				}

				docItem.setRows(transformGroupedResultToArray(oneDocGroupedQueryResult));
				allDocuments.add(docItem);
			}

			phraseData.addItems(rootPhrase, allDocuments);

		}

		TreeDataProvider<TreeRowItem> phraseDataProvider = new TreeDataProvider<>(phraseData);
		treeGridPhrase.setDataProvider(phraseDataProvider);
		treeGridPanel.setContent(treeGridPhrase);
		phraseDataProvider.refreshAll();
		treeGridPhrase.addColumn(TreeRowItem::getTreeKey).setCaption("Phrase").setId("phraseID");
		treeGridPhrase.getColumn("phraseID").setExpandRatio(7);
		treeGridPhrase.addColumn(TreeRowItem::getFrequency).setCaption("Frequency").setId("freqID");
		treeGridPhrase.getColumn("freqID").setExpandRatio(1);
		treeGridPhrase.setWidth("100%");

	}

	private void setDataPropertyStyle() throws Exception {

		propData = new TreeData<>();
		propData = populateTreeDataWithProperties(repository, propData, queryResult); // TODO !!!!!!

		TreeDataProvider<TreeRowItem> propertyDataProvider = new TreeDataProvider<>(propData);

		treeGridProperty.addColumn(TreeRowItem::getShortenTreeKey).setCaption("Tag").setId("tagID");
		treeGridProperty.getColumn("tagID").setExpandRatio(3);

		treeGridProperty.addColumn(TreeRowItem::getPropertyName).setCaption("Property name").setId("propNameID");
		treeGridProperty.getColumn("propNameID").setExpandRatio(3);

		treeGridProperty.addColumn(TreeRowItem::getPropertyValue).setCaption("Property value").setId("propValueID");
		treeGridProperty.getColumn("propValueID").setExpandRatio(3);

		treeGridProperty.addColumn(TreeRowItem::getFrequency).setCaption("Frequency").setId("freqID");
		treeGridProperty.getColumn("freqID").setExpandRatio(1);

		propertyDataProvider.refreshAll();
		treeGridProperty.setDataProvider(propertyDataProvider);
		treeGridProperty.setWidth("100%");
		treeGridProperty.setCaption(queryAsString);

		treeGridPanel.setContent(treeGridProperty);

		setDataPhraseStyle();

	}

	private TreeData<TreeRowItem> populateTreeDataWithTags(Repository repository, TreeData<TreeRowItem> treeData,
			QueryResult queryResult) throws Exception {

		HashMap<String, QueryResultRowArray> allRoots = groupRootsGroupedByTagDefinitionPath(queryResult);

		Set<String> keys = allRoots.keySet();

		for (String key : keys) {
			RootItem root = new RootItem();
			root.setTreeKey(key);
			root.setRows(allRoots.get(key));
			treeData.addItems(null, (TreeRowItem) root);

			HashMap<String, QueryResultRowArray> docsForARoot = new HashMap<String, QueryResultRowArray>();
			docsForARoot = groupDocumentsForRoot(root);
			Set<String> sourceDocs = root.getRows().getSourceDocumentIDs();

			for (String doc : sourceDocs) {

				QueryResultRowArray oneDocArray = docsForARoot.get(doc);

				DocumentItem docItem = new DocumentItem();
				SourceDocument sourceDoc = repository.getSourceDocument(doc);
				docItem.setTreeKey(sourceDoc.toString());
				docItem.setRows(oneDocArray);
				treeData.addItem(root, docItem);
				// adding collections

				QueryResultRowArray itemsForADoc = docItem.getRows();
				HashMap<String, QueryResultRowArray> collectionsForADoc = new HashMap<String, QueryResultRowArray>();

				for (QueryResultRow queryResultRow : itemsForADoc) {

					TagQueryResultRow tRow = (TagQueryResultRow) queryResultRow;

					QueryResultRowArray queryResultRowArray = new QueryResultRowArray();

					String collID = tRow.getMarkupCollectionId();
					String collName = sourceDoc.getUserMarkupCollectionReference(collID).getName();

					if (collectionsForADoc.containsKey(collName)) {
						queryResultRowArray = collectionsForADoc.get(collName);
						queryResultRowArray.add(queryResultRow);
					} else {
						queryResultRowArray.add(queryResultRow);
						collectionsForADoc.put(collName, queryResultRowArray);

					}
				}

				Set<String> collections = collectionsForADoc.keySet();
				for (String coll : collections) {
					CollectionItem collItem = new CollectionItem();
					collItem.setTreeKey(coll);
					collItem.setRows(collectionsForADoc.get(coll));
					treeData.addItem(docItem, collItem);

				}

			}

		}

		return treeData;
	}

	private HashMap<String, QueryResultRowArray> groupDocumentsForRoot(RootItem root) {
		HashMap<String, QueryResultRowArray> documentsForARoot = new HashMap<String, QueryResultRowArray>();
		QueryResultRowArray allDocsArray = root.getRows();

		for (QueryResultRow queryResultRow : allDocsArray) {
			if (queryResultRow instanceof TagQueryResultRow) {
				TagQueryResultRow tRow = (TagQueryResultRow) queryResultRow;

				QueryResultRowArray rows = documentsForARoot.get(tRow.getSourceDocumentId());

				if (rows == null) {
					rows = new QueryResultRowArray();
					documentsForARoot.put(tRow.getSourceDocumentId(), rows);
				}
				rows.add(tRow);
			}

		}
		return documentsForARoot;

	}

	private HashMap<String, QueryResultRowArray> groupRootsGroupedByTagDefinitionPath(QueryResult queryResults)
			throws Exception {

		HashMap<String, QueryResultRowArray> rowsGroupedByTagDefinitionPath = new HashMap<String, QueryResultRowArray>();

		for (QueryResultRow row : queryResult) {

			if (row instanceof TagQueryResultRow) {
				TagQueryResultRow tRow = (TagQueryResultRow) row;
				QueryResultRowArray rows = rowsGroupedByTagDefinitionPath.get(tRow.getTagDefinitionPath());

				if (rows == null) {
					rows = new QueryResultRowArray();
					rowsGroupedByTagDefinitionPath.put(tRow.getTagDefinitionPath(), rows);
				}
				rows.add(tRow);
			}
		}
		return rowsGroupedByTagDefinitionPath;
	}

	private QueryResultRowArray transformGroupedResultToArray(GroupedQueryResult groupedQueryResult) {
		QueryResultRowArray queryResultRowArray = new QueryResultRowArray();

		for (QueryResultRow queryResultRow : groupedQueryResult) {
			queryResultRowArray.add(queryResultRow);
		}
		return queryResultRowArray;

	}

	private TreeData<TreeRowItem> populateTreeDataWithProperties(Repository repository, TreeData<TreeRowItem> treeData,
			QueryResult queryResult) throws Exception {
		TreeData<TreeRowItem> data = populateTreeDataWithTags(repository, treeData, queryResult);

		return data;
	}

	public String getQueryAsString() {
		return this.queryAsString;
	}

	private void swichView() throws Exception {

		switch (currentView) {

		case tag:
			setCurrentView(ViewID.phraseTag);
			treeGridPanel.setContent(treeGridPhrase);
			break;

		case property:
			setCurrentView(ViewID.phraseProperty);
			treeGridPanel.setContent(treeGridPhrase);
			break;

		case phrase:
			Notification.show("no tag view available for that query", Notification.Type.HUMANIZED_MESSAGE);
			break;

		case phraseProperty:
			setCurrentView(ViewID.property);
			treeGridPanel.setContent(treeGridProperty);
			break;

		case phraseTag:
			setCurrentView(ViewID.tag);
			treeGridPanel.setContent(treeGridTag);
			break;

		default:
			Notification.show("no view available ", Notification.Type.HUMANIZED_MESSAGE);
			break;

		}
	}

}
