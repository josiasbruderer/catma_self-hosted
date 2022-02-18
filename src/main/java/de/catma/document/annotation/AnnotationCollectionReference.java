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
package de.catma.document.annotation;

import de.catma.document.source.ContentInfoSet;

/**
 * A reference to a {@link AnnotationCollection}.
 * 
 * @author marco.petris@web.de
 *
 */
public class AnnotationCollectionReference {
	
	private String id;
	private ContentInfoSet contentInfoSet;
	private String sourceDocumentId;
	private String forkedFromCommitURL;
	private String responsableUser;
	
	public AnnotationCollectionReference(AnnotationCollection annotationCollection) {
		this(annotationCollection.getId(),
				annotationCollection.getContentInfoSet(),
				annotationCollection.getSourceDocumentId(),
				annotationCollection.getForkedFromCommitURL(),
				annotationCollection.getResponsableUser());
	}
	
	public AnnotationCollectionReference(
		String id,
		ContentInfoSet contentInfoSet, 
		String sourceDocumentId,
		String forkedFromCommitURL,
		String responsableUser) {
		super();
		this.id = id;
		this.contentInfoSet = contentInfoSet;
		this.sourceDocumentId = sourceDocumentId;
		this.forkedFromCommitURL = forkedFromCommitURL;
		this.responsableUser = responsableUser;
	}

	@Override
	public String toString() {
		return contentInfoSet.getTitle();
	}
	

	public String getId() {
		return id;
	}
	
	public String getName() {
		return contentInfoSet.getTitle();
	}
	
	public ContentInfoSet getContentInfoSet() {
		return contentInfoSet;
	}
	
	public void setContentInfoSet(ContentInfoSet contentInfoSet) {
		this.contentInfoSet = contentInfoSet;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		AnnotationCollectionReference other = (AnnotationCollectionReference) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}
	
	public String getSourceDocumentId() {
		return sourceDocumentId;
	}

	public String getForkedFromCommitURL() {
		return forkedFromCommitURL;
	}
	
	public void setForkedFromCommitURL(String forkedFromCommitURL) {
		this.forkedFromCommitURL = forkedFromCommitURL;
	}
	
	public boolean isResponable(String userIdentifier) {
		if (this.responsableUser != null) {
			return this.responsableUser.equals(userIdentifier);
		}
		return true; //shared repsonsibility
	}
	
	public String getResponsableUser() {
		return responsableUser;
	}
	
	public void setResponsableUser(String responsableUser) {
		this.responsableUser = responsableUser;
	}
}
