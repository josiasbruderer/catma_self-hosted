package de.catma.ui.client.ui.tagger.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style;

import de.catma.ui.client.ui.tagger.shared.ClientTagInstance;
import de.catma.ui.client.ui.tagger.shared.TextRange;

public class LineNodeToLineConverter {

	private String lineId;
	private TextRange textRange;
	private Set<TextRange> tagInstanceTextRanges;
	private Map<String,ClientTagInstance> relativeTagIntancesByID;
	private String presentationContent;
	private Line line;
	
	public LineNodeToLineConverter(Element lineElement) {
		tagInstanceTextRanges = new HashSet<>();
		relativeTagIntancesByID = new HashMap<>();
		makeLineFromLineNode(lineElement);
	}
	
	private void makeLineFromLineNode(Element lineElement) {
		lineId = lineElement.getId();
		Element lineBodyElement = lineElement.getFirstChildElement();
		
		for (int layerIdx = 0; layerIdx < lineBodyElement.getChildCount(); layerIdx++) {
			Node layerNode = lineBodyElement.getChild(layerIdx);
			if (Element.is(layerNode)) {
				Element layerElement = Element.as(layerNode);
				
				if (layerElement.hasClassName("tagger-display-layer")) {
					handleDisplayLayer(layerElement);
				}
				else if (layerElement.hasClassName("annotation-layer")) {
					handleAnnotationLayer(layerElement);
				}
			}
		}
		
		for (ClientTagInstance tagInstance : relativeTagIntancesByID.values()) {
			tagInstanceTextRanges.addAll(tagInstance.getRanges());
		}
		
		line = new Line(
			lineElement,
			lineId, textRange, tagInstanceTextRanges, 
			new ArrayList<ClientTagInstance>(relativeTagIntancesByID.values()), presentationContent);
	}

	private void handleAnnotationLayer(Element layerElement) {
		for (int annotationSegmentIdx = 0; 
				annotationSegmentIdx<layerElement.getChildCount(); annotationSegmentIdx++) {
			Node annotationSegmentNode = layerElement.getChild(annotationSegmentIdx);
			if (Element.is(annotationSegmentNode)) {
				Element annotationSegmentElement = Element.as(annotationSegmentNode);
				
				if (annotationSegmentElement.getId() != null &&
						annotationSegmentElement.getId().startsWith("CATMA_")) {
					
					String annotationSegmentId = annotationSegmentElement.getId();
					Style annotationSegmentStyle = annotationSegmentElement.getStyle();
					String annotationColor = annotationSegmentStyle.getBackgroundColor();
					handleAnnotationSegmment(annotationSegmentId, annotationColor);
				}
			}
		}
	}

	private void handleAnnotationSegmment(String annotationSegmentId, String annotationColor) {
		annotationColor = getConvertedColor(annotationColor);
		
		String annotationId = ClientTagInstance.getTagInstanceIDFromPartId(annotationSegmentId);
		TextRange textRange = ClientTagInstance.getTextRangeFromPartId(annotationSegmentId);
		ClientTagInstance tagInstance = relativeTagIntancesByID.get(annotationId);
		if (tagInstance != null) {
			TreeSet<TextRange> sortedRanges = new TreeSet<>(tagInstance.getRanges());
			sortedRanges.add(textRange);
			tagInstance = new ClientTagInstance(null, annotationId, annotationColor, ClientTagInstance.mergeRanges(sortedRanges));
		}
		else {
			List<TextRange> textRanges = new ArrayList<>();
			textRanges.add(textRange);
			tagInstance = new ClientTagInstance(null, annotationId, annotationColor, textRanges);
		}
		
		relativeTagIntancesByID.put(annotationId, tagInstance);
	}

	private String getConvertedColor(String annotationColor) {
		if (annotationColor.startsWith("rgb")) {
			String[] colorStrings = annotationColor.substring(4, annotationColor.length()-1).split(",");
			
			int red = Integer.valueOf(colorStrings[0].trim());
			int green = Integer.valueOf(colorStrings[1].trim());
			int blue = Integer.valueOf(colorStrings[2].trim());
			return fillUp(Integer.toHexString(red).toUpperCase()) 
					+ fillUp(Integer.toHexString(green).toUpperCase()) 
					+ fillUp(Integer.toHexString(blue).toUpperCase());
		}
		return annotationColor;
	}
	
	private String fillUp(String hexString) {
		if (hexString.length() < 2) {
			return "0"+hexString;
		}
		
		return hexString;
	}

	private void handleDisplayLayer(Element layerElement) {
		String[] rangePositions = layerElement.getId().split("\\.");
		textRange = new TextRange(Integer.valueOf(rangePositions[0]),Integer.valueOf(rangePositions[1]));
		presentationContent = layerElement.getFirstChildElement().getInnerText();
	}
	
	public Line getLine() {
		return line;
	}

}
