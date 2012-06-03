package de.catma.repository.db.model;

// Generated 22.05.2012 21:58:37 by Hibernate Tools 3.4.0.CR1

import static javax.persistence.GenerationType.IDENTITY;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import de.catma.util.IDGenerator;

/**
 * Taglibrary generated by hbm2java
 */
@Entity
@Table(name = "taglibrary", catalog = "CatmaRepository")
public class DBTagLibrary implements java.io.Serializable {

	private Integer tagLibraryId;
	private boolean independent;
	private Set<DBUserTagLibrary> dbUserTagLibraries = 
			new HashSet<DBUserTagLibrary>();
	
//	private Set<DBTagsetDefinition> dbTagsetDefinitions = new HashSet<DBTagsetDefinition>();
	private String name;
	
	public DBTagLibrary() {
	}
	
	public DBTagLibrary(String name, boolean independent) {
		this.name = name;
		this.independent = independent;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "tagLibraryID", unique = true, nullable = false)
	public Integer getTagLibraryId() {
		return this.tagLibraryId;
	}

	public void setTagLibraryId(Integer tagLibraryId) {
		this.tagLibraryId = tagLibraryId;
	}

	@Column(name = "independent", nullable = false)
	public boolean isIndependent() {
		return independent;
	}
	
	public void setIndependent(boolean independent) {
		this.independent = independent;
	}
	
	@Column(name = "name", nullable = false, length = 300)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@OneToMany(mappedBy = "dbTagLibrary")
	@Cascade({CascadeType.SAVE_UPDATE, CascadeType.DELETE})
	public Set<DBUserTagLibrary> getDbUserTagLibraries() {
		return dbUserTagLibraries;
	}
	
	public void setDbUserTagLibraries(Set<DBUserTagLibrary> dbUserTagLibraries) {
		this.dbUserTagLibraries = dbUserTagLibraries;
	}
	
//	@OneToMany(mappedBy = "tagLibraryId")
//	@Cascade({CascadeType.DELETE})
//	public Set<DBTagsetDefinition> getDbTagsetDefinitions() {
//		return dbTagsetDefinitions;
//	}
//
//	public void setDbTagsetDefinitions(
//			Set<DBTagsetDefinition> dbTagsetDefinitions) {
//		this.dbTagsetDefinitions = dbTagsetDefinitions;
//	}
	
	@Override
	public String toString() {
		return getName();
	}

	@Transient
	public String getId() {
		return (getTagLibraryId() == null) ? null : String.valueOf(getTagLibraryId());
	}
}
