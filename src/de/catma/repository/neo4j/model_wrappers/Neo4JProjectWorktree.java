package de.catma.repository.neo4j.model_wrappers;

import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.models.Project;
import de.catma.repository.neo4j.exceptions.Neo4JProjectException;
import de.catma.repository.neo4j.exceptions.Neo4JSourceDocumentException;
import de.catma.repository.neo4j.exceptions.Neo4JMarkupCollectionException;
import de.catma.tag.TagsetDefinition;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NodeEntity(label="ProjectWorktree")
public class Neo4JProjectWorktree {
	@Id
	@GeneratedValue
	private Long id;

	private String uuid;
	private String name;
	private String description;
	private String revisionHash;

	@Relationship(type="HAS_TAGSET", direction=Relationship.OUTGOING)
	private List<Neo4JTagset> tagsets;

	@Relationship(type="HAS_MARKUP_COLLECTION", direction=Relationship.OUTGOING)
	private List<Neo4JMarkupCollection> markupCollections;

	@Relationship(type="HAS_SOURCE_DOCUMENT", direction=Relationship.OUTGOING)
	private List<Neo4JSourceDocument> sourceDocuments;

	public Neo4JProjectWorktree() {
		this.tagsets = new ArrayList<>();
		this.markupCollections = new ArrayList<>();
		this.sourceDocuments = new ArrayList<>();
	}

	public Neo4JProjectWorktree(Project project) throws Neo4JProjectException {
		this();

		this.setProject(project);
	}

	public Long getId() {
		return this.id;
	}

	public String getUuid() {
		return this.uuid;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public String getRevisionHash() {
		return this.revisionHash;
	}

	public Project getProject() throws Neo4JProjectException {
		Project project = new Project(this.uuid, this.name, this.description, this.revisionHash);

		try {
			this.tagsets.forEach(tagset -> project.addTagset(tagset.getTagsetDefinition()));

			// TODO: figure out how to do this with .forEach while handling exceptions properly
//			this.markupCollections.forEach(markupCollection -> project.addMarkupCollection(
//					markupCollection.getUserMarkupCollection())
//			);
//			this.sourceDocuments.forEach(sourceDocument -> project.addSourceDocument(
//					sourceDocument.getSourceDocument())
//			);

			for (Neo4JMarkupCollection markupCollection : this.markupCollections) {
				project.addMarkupCollection(markupCollection.getUserMarkupCollection());
			}

			for (Neo4JSourceDocument sourceDocument : this.sourceDocuments) {
				project.addSourceDocument(sourceDocument.getSourceDocument());
			}
		}
		catch (Neo4JMarkupCollectionException |Neo4JSourceDocumentException e) {
			throw new Neo4JProjectException("");
		}

		return project;
	}

	public void setProject(Project project) throws Neo4JProjectException {
		this.revisionHash = project.getRevisionHash();

		this.uuid = project.getUuid();
		this.name = project.getName();
		this.description = project.getDescription();

		this.setTagsets(project.getTagsets());
		this.setMarkupCollections(project.getMarkupCollections());
		this.setSourceDocuments(project.getSourceDocuments());
	}

	public void setTagsets(List<TagsetDefinition> tagsets) {
		this.tagsets = tagsets.stream().map(Neo4JTagset::new).collect(Collectors.toList());
	}

	public void setMarkupCollections(List<UserMarkupCollection> userMarkupCollections) throws Neo4JProjectException {
		// TODO: figure out how to do this with .stream().map while handling exceptions properly
		// see https://stackoverflow.com/a/33218789 & https://stackoverflow.com/a/30118121 for pointers
		this.markupCollections.clear();
		try {
			for (UserMarkupCollection userMarkupCollection : userMarkupCollections) {
				this.markupCollections.add(new Neo4JMarkupCollection(userMarkupCollection));
			}
		}
		catch (Neo4JMarkupCollectionException e) {
			throw new Neo4JProjectException("Failed to set markup collections", e);
		}
	}

	public void setSourceDocuments(List<SourceDocument> sourceDocuments) throws Neo4JProjectException {
		// TODO: figure out how to do this with .stream().map while handling exceptions properly
		// see https://stackoverflow.com/a/33218789 & https://stackoverflow.com/a/30118121 for pointers
		this.sourceDocuments.clear();
		try {
			for (SourceDocument sourceDocument : sourceDocuments) {
				this.sourceDocuments.add(new Neo4JSourceDocument(sourceDocument));
			}
		}
		catch (Neo4JSourceDocumentException e) {
			throw new Neo4JProjectException("Failed to set source documents", e);
		}
	}
}
