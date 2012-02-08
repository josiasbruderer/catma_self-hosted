package de.catma.ui.tagger;

import java.io.IOException;

import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.VerticalLayout;

import de.catma.core.document.source.SourceDocument;
import de.catma.ui.client.ui.tagger.shared.TagInstance;
import de.catma.ui.tagger.Tagger.TaggerListener;
import de.catma.ui.tagger.pager.Pager;
import de.catma.ui.tagger.pager.PagerComponent;
import de.catma.ui.tagger.pager.PagerComponent.PageChangeListener;

public class TaggerView extends VerticalLayout {
	
	private SourceDocument sourceDocument;
	private Tagger tagger;

	public TaggerView(SourceDocument sourceDocument) {
		this.sourceDocument = sourceDocument;
		initComponents();
		try {
			tagger.setText(sourceDocument.getContent());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void initComponents() {
		Pager pager = new Pager(80, 30);
		
		tagger = new Tagger(pager, new TaggerListener() {
			
			public void tagInstanceAdded(TagInstance tagInstance) {
			}
		});
		
		tagger.setSizeFull();
		addComponent(tagger);

		PagerComponent pagerComponent = new PagerComponent(pager, new PageChangeListener() {
			public void pageChanged(int number) {
				tagger.setPage(number);
			}
		});

		addComponent(pagerComponent);
	}

	public SourceDocument getSourceDocument() {
		return sourceDocument;
	}
}
