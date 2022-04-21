package de.catma.repository.git.serialization.models.json_ld;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;

import de.catma.document.Range;
import de.catma.document.annotation.TagReference;
import de.catma.repository.git.GitProjectHandler;
import de.catma.tag.Property;
import de.catma.tag.TagInstance;
import de.catma.tag.TagLibrary;
import de.catma.tag.Version;

/**
 * Represents an annotation instance that, when serialized, conforms to the Web Annotation Data Model and JSON-LD
 * specifications.
 *
 * @see <a href="https://www.w3.org/TR/annotation-model/">Web Annotation Data Model</a>
 * @see <a href="https://json-ld.org/spec/latest/json-ld/">JSON-LD</a>
 */
public class JsonLdWebAnnotation {
	
	private @SerializedName("@context") String context = "http://www.w3.org/ns/anno.jsonld";
	private String type = "Annotation";
	private String id;
	private JsonLdWebAnnotationBody_Dataset body;
	private JsonLdWebAnnotationTarget_List target;
	private transient String pageFilename;
	
	/**
	 * Constructor for deserialization
	 */
	public JsonLdWebAnnotation() {

	}

	public JsonLdWebAnnotation(
			Collection<TagReference> tagReferences, TagLibrary tagLibrary, 
			String pageFilename)
			throws IOException {
		// assert that all TagReference objects are for the same TagInstance and thus share the same TagDefinition and
		// properties
		Set<TagInstance> uniqueTagInstances = new HashSet<>(tagReferences.stream().map(TagReference::getTagInstance)
				.collect(Collectors.toSet()));
		if (uniqueTagInstances.size() > 1) {
			throw new IOException(
				"Supplied TagReference objects are not all for the same TagInstance"
			);
		}

		this.id = this.buildTagInstanceUrl(
			tagReferences.iterator().next().getUserMarkupCollectionUuid(),
			tagReferences.iterator().next().getTagInstance().getUuid()
		).toString();

		this.body = new JsonLdWebAnnotationBody_Dataset(tagReferences, tagLibrary);
		this.target = new JsonLdWebAnnotationTarget_List(tagReferences);
		this.pageFilename = pageFilename;
	}

	private URI buildTagInstanceUrl(String userMarkupCollectionUuid, String tagInstanceUuid)
			throws IOException {

		try {
			return new URI(
					String.format(
							"%s/%s/annotations/%s",
							GitProjectHandler.ANNOTATION_COLLECTIONS_DIRECTORY_NAME,
							userMarkupCollectionUuid,
							tagInstanceUuid
					)
			);
		} catch (URISyntaxException ue) {
			throw new IOException(ue);
		}
	}

	public String getContext() {
		return this.context;
	}

	public String getType() {
		return this.type;
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public void setPageFilename(String pageFilename) {
		this.pageFilename = pageFilename;
	}

	public JsonLdWebAnnotationBody_Dataset getBody() {
		return this.body;
	}

	public void setBody(JsonLdWebAnnotationBody_Dataset body) {
		this.body = body;
	}

	public JsonLdWebAnnotationTarget_List getTarget() {
		return this.target;
	}

	public void setTarget(JsonLdWebAnnotationTarget_List target) {
		this.target = target;
	}

	public List<TagReference> toTagReferenceList(String projectId, String markupCollectionId)
				throws Exception {
		TagInstance tagInstance = this.getTagInstance();
		String sourceDocumentUri = this.getSourceDocumentUri();
		List<Range> ranges = this.getRanges();

		List<TagReference> tagReferences = new ArrayList<>();
		try {
			for (Range range : ranges) {
				tagReferences.add(new TagReference(tagInstance, sourceDocumentUri, range, markupCollectionId));
			}
		}
		catch (URISyntaxException e) {
			throw new IOException(
				String.format("error loading Collection %1$s of project %2$s ",
						markupCollectionId,
						projectId), 
				e);
		}

		return tagReferences;
	}

	private String getSourceDocumentUri() throws IOException {
		return this.target.getItems().first().getSource();
	}

	private List<Range> getRanges() throws IOException {
		return this.target.getItems().stream().map(jsonLdWebAnnotationTarget -> {
			JsonLdWebAnnotationTextPositionSelector selector = jsonLdWebAnnotationTarget.getSelector();
			return new Range(selector.getStart(), selector.getEnd());
		}).collect(Collectors.toList());
	}

	public String getTagInstanceUuid() {
		return this.getLastPathSegmentFromUrl(this.id).replace(".json", "");
	}

	public TagInstance getTagInstance()
			throws Exception {

		TagInstance tagInstance = new TagInstance(
			this.getTagInstanceUuid(),
			getBody().getTag().substring(getBody().getTag().lastIndexOf('/')+1),
			"", //author gets redefined with the system properties below
			ZonedDateTime.now().format(DateTimeFormatter.ofPattern(Version.DATETIMEPATTERN)),
			Collections.emptyList(), //these get added with the user defined properties below
			getBody().getTagset().substring(getBody().getTagset().lastIndexOf('/')+1)
		);
		tagInstance.setPageFilename(pageFilename);
		
		TreeMap<String, TreeMap<String, TreeSet<String>>> properties = this.body.getProperties();

		for (Map.Entry<String, TreeMap<String, TreeSet<String>>> entry : properties.entrySet()) {
			for (Map.Entry<String, TreeSet<String>> subEntry : entry.getValue().entrySet()) {
				Property property = new Property(
					subEntry.getKey(),
					subEntry.getValue()
				);
				if (entry.getKey().equals(JsonLdWebAnnotationBody_Dataset.SYSTEM_PROPERTIES_KEY)) {
					tagInstance.addSystemProperty(property);
				}
				else {
					tagInstance.addUserDefinedProperty(property);
				}
			}
		}

		return tagInstance;
	}


	private String getLastPathSegmentFromUrl(String url) {
		return url.substring(url.lastIndexOf("/") + 1);
	}
	
	public String getPageFilename() {
		return pageFilename;
	}
}
