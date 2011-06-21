package de.catma.ui.tagger.client.ui;

import java.util.Date;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;

public class TaggedSpanFactory {

	private String tag;
	private String instanceID;
	public TaggedSpanFactory(String tag) {
		super();
		this.tag = tag;
		instanceID = String.valueOf(new Date().getTime());
	}
	
	public Element createTaggedSpan(String innerHtml) {
		Element taggedSpan = DOM.createSpan();
		taggedSpan.addClassName(tag);
		taggedSpan.setAttribute("instanceID", instanceID);
		taggedSpan.setInnerHTML(innerHtml);
		return taggedSpan;
	}
	
}
