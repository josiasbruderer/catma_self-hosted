package de.catma.rbac;

/**
 * Roles that are mapped 1:1 to predefined gitlab roles called <code>AccessLevel</code> 
 * @author db
 *
 */
public enum RBACPermission {
	PROJECT_DELETE(RBACRole.OWNER),
	PROJECT_EDIT(RBACRole.MAINTAINER),
	PROJECT_MEMBERS_EDIT(RBACRole.MAINTAINER),
	PROJECT_LEAVE(RBACRole.GUEST),
	DOCUMENT_CREATE_OR_UPLOAD(RBACRole.MAINTAINER),
	DOCUMENT_DELETE_OR_EDIT(RBACRole.MAINTAINER),
	DOCUMENT_READ(RBACRole.REPORTER),
	DOCUMENT_WRITE(RBACRole.ASSISTANT),
	COLLECTION_CREATE(RBACRole.MAINTAINER),
	COLLECTION_DELETE_OR_EDIT(RBACRole.MAINTAINER),
	COLLECTION_WRITE(RBACRole.ASSISTANT),
	COLLECTION_READ(RBACRole.REPORTER),
	TAGSET_CREATE_OR_UPLOAD(RBACRole.MAINTAINER),
	TAGSET_DELETE_OR_EDIT(RBACRole.MAINTAINER),
	TAGSET_WRITE(RBACRole.ASSISTANT),
	TAGSET_READ(RBACRole.REPORTER),
	RESOURCE_READ(RBACRole.REPORTER),
	;
	
	private final RBACRole roleRequired;
	
	private RBACPermission(RBACRole roleRequired) {
		this.roleRequired = roleRequired;
	}

	public RBACRole getRoleRequired() {
		return roleRequired;
	}
	
}
