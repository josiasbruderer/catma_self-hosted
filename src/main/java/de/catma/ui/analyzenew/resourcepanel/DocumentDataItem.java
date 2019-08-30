package de.catma.ui.analyzenew.resourcepanel;

import com.vaadin.icons.VaadinIcons;

import de.catma.document.Corpus;
import de.catma.document.source.SourceDocument;

public class DocumentDataItem implements DocumentTreeItem {
	
	private SourceDocument document;
	
	public DocumentDataItem(SourceDocument document) {
		this.document = document;
	}

	@Override
	public String getName() {
		return document.toString();
	}

	@Override
	public String getIcon() {
		return VaadinIcons.BOOK.getHtml();
	}

	public SourceDocument getDocument() {
		return document;
	}
	
	@Override
	public String getPermissionIcon() {
		return null; //writable docs not supported yet
	}
	
	@Override
	public void addToCorpus(Corpus corpus) {
		corpus.addSourceDocument(document);
	}
}
