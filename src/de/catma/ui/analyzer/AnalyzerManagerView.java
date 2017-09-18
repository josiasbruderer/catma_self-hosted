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
package de.catma.ui.analyzer;

import java.util.HashSet;

import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.Notification;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;

import de.catma.document.Corpus;
import de.catma.indexer.IndexedRepository;
import de.catma.ui.CatmaApplication;
import de.catma.ui.analyzer.AnalyzerView.CloseListener;
import de.catma.ui.tabbedview.TabbedView;

public class AnalyzerManagerView extends TabbedView {
	
	
	

	public AnalyzerManagerView() {
		super(Messages.getString("AnalyzerManagerView.intro")); //$NON-NLS-1$
		
		initActions();
	
	}

	public void analyzeDocuments(Corpus corpus, IndexedRepository repository) {
		// super.btnAnalyzeCurrentActiveDoc.setVisible(false);

		AnalyzerView analyzerView = new AnalyzerView(corpus, repository, new CloseListener() {

			public void closeRequest(AnalyzerView analyzerView) {
				onTabClose(analyzerView);
			}
		});

		HashSet<String> captions = new HashSet<String>();

		for (Component c : this.getTabSheet()) {
			captions.add(getCaption(c));
		}

		String base = (corpus == null) ? Messages.getString("AnalyzerManagerView.allDocuments") : corpus.toString(); //$NON-NLS-1$
		String caption = base;

		int captionIndex = 1;
		while (captions.contains(caption)) {
			caption = base + "(" + captionIndex + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			captionIndex++;
		}

		addClosableTab(analyzerView, caption);
	}
	
	 private void initActions(){
		super.btnAnalyzeCurrentActiveDoc.addClickListener(new ClickListener() {
			public void buttonClick(ClickEvent event) {
				((CatmaApplication) UI.getCurrent()).analyzeCurrentlyActiveDocument();
			}});	
	}
	
	




}
