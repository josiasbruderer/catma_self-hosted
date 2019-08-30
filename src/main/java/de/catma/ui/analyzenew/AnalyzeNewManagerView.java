package de.catma.ui.analyzenew;

import com.google.common.eventbus.EventBus;
import com.vaadin.ui.UI;

import de.catma.document.Corpus;
import de.catma.indexer.IndexedRepository;
import de.catma.ui.CatmaApplication;
import de.catma.ui.tabbedview.TabbedView;

public class AnalyzeNewManagerView extends TabbedView {
	
	private EventBus eventBus;
	
	public AnalyzeNewManagerView(EventBus eventBus, IndexedRepository project) {
		super(() -> new AnalyzeNewView(new Corpus(), project));	
		this.eventBus = eventBus;
	}
	
	public void analyzeNewDocuments(Corpus corpus, IndexedRepository repository) {
		try {
			AnalyzeNewView analyzeNewView = new AnalyzeNewView(corpus, repository);	
			
			String caption= null;
			String substring=null;
			String substring2= null;
			int documents=corpus.getSourceDocuments().size();
			int collections=corpus.getUserMarkupCollectionRefs().size();
			
			//TODO:
	 if(documents==1) {
		 substring = "1 document, ";
	 }else {
		 substring = documents+" documents, ";
	 } 
	 if(collections==1) {
		 substring2="1 collection";	 
	 }else {
		 substring2= collections+ " collections";
	 }
		caption= substring+substring2;
			addClosableTab(analyzeNewView, caption);		
		}	
		catch (Exception e) {
			((CatmaApplication)UI.getCurrent()).showAndLogError("error initializing Analyzer", e);
		}
			
	}
}
