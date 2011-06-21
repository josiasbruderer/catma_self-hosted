package de.catma.ui.tagger.client.ui.impl;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Node;


public class SelectionHandlerImplStandard {
	
	public final static class Range {
		private JavaScriptObject javaScriptObject;
		private SelectionHandlerImplStandard impl;

		private Range(SelectionHandlerImplStandard impl, 
				JavaScriptObject javaScriptObject) {
			this.impl = impl;
			this.javaScriptObject = javaScriptObject;
		}
		
		public final Node getStartNode() {
			return impl.getStartNode(javaScriptObject);
		}
		
		public final int getStartOffset() {
			return impl.getStartOffset(javaScriptObject);
		}
		
		public final Node getEndNode() {
			return impl.getEndNode(javaScriptObject);
		}
		
		public final int getEndOffset() {
			return impl.getEndOffset(javaScriptObject);
		}
		
		@Override
		public String toString() {
			if (javaScriptObject == null) {
				return "";
			}
			else {
				return impl.toJSString(javaScriptObject);
			}
		}
		
		public final boolean isEmpty() {
			return ((javaScriptObject == null) 
					|| ((getEndNode().equals(getStartNode()) 
							&& (getEndOffset()==getStartOffset()))));
		}
	}

	public final List<Range> getRangeList() {
		List<Range> result = new ArrayList<Range>();
		int rangeCount = getRangeCount();
		for (int i=0; i<rangeCount; i++) {
			JavaScriptObject jsRange = getRangeAt(i);
			if (jsRange != null) { 
				result.add(new Range(this,jsRange));
			}
		}
		return result;
	}
	
	protected native JavaScriptObject getRangeAt(int idx) /*-{
		if ($wnd.getSelection().rangeCount > idx) {
			return $wnd.getSelection().getRangeAt(idx);
		}
		else {
			return null;
		}
	}-*/;

	protected native int getRangeCount() /*-{
		return $wnd.getSelection().rangeCount;
	}-*/;
	
	protected native Node getStartNode(JavaScriptObject range) /*-{
		return range.startContainer; 
	}-*/;
	
	protected native int getStartOffset(JavaScriptObject range) /*-{
		return range.startOffset; 
	}-*/;
	
	protected native Node getEndNode(JavaScriptObject range) /*-{
		return range.endContainer; 
	}-*/;
	
	protected native int getEndOffset(JavaScriptObject range) /*-{
		return range.endOffset; 
	}-*/;
	
	protected native String toJSString(JavaScriptObject obj) /*-{
		return obj.toString(); 
	}-*/;
}
