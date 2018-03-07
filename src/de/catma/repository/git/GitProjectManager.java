package de.catma.repository.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import de.catma.Pager;
import de.catma.document.repository.Repository;
import de.catma.project.OpenProjectListener;
import de.catma.project.ProjectManager;
import de.catma.project.ProjectReference;
import de.catma.repository.git.exceptions.GitProjectHandlerException;
import de.catma.repository.git.exceptions.LocalGitRepositoryManagerException;
import de.catma.repository.git.exceptions.RemoteGitServerManagerException;
import de.catma.repository.git.interfaces.ILocalGitRepositoryManager;
import de.catma.repository.git.interfaces.IRemoteGitServerManager;
import de.catma.repository.git.managers.GitLabServerManager;
import de.catma.repository.git.managers.JGitRepoManager;
import de.catma.user.User;
import de.catma.util.IDGenerator;

public class GitProjectManager implements ProjectManager {
	private final ILocalGitRepositoryManager localGitRepositoryManager;
	private final IRemoteGitServerManager remoteGitServerManager;

	private final IDGenerator idGenerator;
	private String gitBasedRepositoryBasePath;
	private GitUser user;

	private static final String PROJECT_ROOT_REPOSITORY_NAME_FORMAT = "%s_root";

	public static String getProjectRootRepositoryName(String projectId) {
		return String.format(PROJECT_ROOT_REPOSITORY_NAME_FORMAT, projectId);
	}

	public GitProjectManager(
			String gitLabServerUrl, 
			String gitLabAdminPersonalAccessToken,
			String gitBasedRepositoryBasePath,
			Map<String, String>	userIdentification) 
					throws RemoteGitServerManagerException {
		this.gitBasedRepositoryBasePath = gitBasedRepositoryBasePath;
		this.remoteGitServerManager = 
				new GitLabServerManager(
					gitLabAdminPersonalAccessToken, 
					gitLabAdminPersonalAccessToken, 
					userIdentification);
		this.user = new GitUser(((GitLabServerManager) this.remoteGitServerManager).getGitLabUser());
		this.localGitRepositoryManager = new JGitRepoManager(this.gitBasedRepositoryBasePath, this.user);

		this.idGenerator = new IDGenerator();
	}

	/**
	 * Creates a new project.
	 *
	 * @param name the name of the project to create
	 * @param description the description of the project to create
	 * @return the new project ID
	 * @throws GitProjectHandlerException if an error occurs when creating the project
	 */
	@Override
	public String create(String name, String description) throws GitProjectHandlerException {

		//TODO: consider creating local git projects for offline use

		String projectId = idGenerator.generate() + "_" + name.replaceAll("[^\\p{Alnum}]", "_");

		try (ILocalGitRepositoryManager localGitRepoManager = this.localGitRepositoryManager) {
			// create the group
			String groupPath = this.remoteGitServerManager.createGroup(
				projectId, projectId, name
			);

			// create the root repository
			String projectNameAndPath = GitProjectManager.getProjectRootRepositoryName(projectId);

			IRemoteGitServerManager.CreateRepositoryResponse response =
					this.remoteGitServerManager.createRepository(
				projectNameAndPath, projectNameAndPath, groupPath
			);

			// clone the root repository locally
			localGitRepoManager.clone(
				projectId,
				response.repositoryHttpUrl,
				null,
				remoteGitServerManager.getUsername(),
				remoteGitServerManager.getPassword()
			);
		}
		catch (RemoteGitServerManagerException|LocalGitRepositoryManagerException e) {
			throw new GitProjectHandlerException("Failed to create project", e);
		}

		return projectId;
	}

	/**
	 * Deletes an existing project.
	 * <p>
	 * This will also delete any associated repositories automatically (local & remote).
	 *
	 * @param projectId the ID of the project to delete
	 * @throws GitProjectHandlerException if an error occurs when deleting the project
	 */
	@Override
	public void delete(String projectId) throws GitProjectHandlerException {
		try {
			List<String> repositoryNames = this.remoteGitServerManager.getGroupRepositoryNames(
				projectId
			);

			for (String name : repositoryNames) {
				FileUtils.deleteDirectory(
					new File(this.localGitRepositoryManager.getRepositoryBasePath(), name)
				);
			}

			this.remoteGitServerManager.deleteGroup(projectId);
		}
		catch (RemoteGitServerManagerException|IOException e) {
			throw new GitProjectHandlerException("Failed to delete project", e);
		}
	}
	
	@Override
	public User getUser() {
		return user;
	}

	@Override
	public Pager<ProjectReference> getProjectReferences() throws Exception {
		return remoteGitServerManager.getProjectReferences();
	}

	@Override
	public ProjectReference createProject(String name, String description) throws Exception {
		String projectId = create(name, description);
		
		return new ProjectReference(projectId, name, description);
	}

	@Override
	public void openProject(ProjectReference projectReference, OpenProjectListener openProjectListener) {
		try {
			cloneRootLocallyIfNotExists(projectReference, openProjectListener);
			Repository project = 
				new GraphWorktreeProject(
						this.user,
						new GitProjectHandler(this.localGitRepositoryManager, this.remoteGitServerManager),
						projectReference);

			project.open(openProjectListener);

			
		} catch (Exception e) {
			openProjectListener.failure(e);
		}
		//TODO ensure existence in graphdb via plugin
		// check commit checksum
		// if not: 
		// -> load: parse .gitmodules 

	}

	public boolean existsLocally(ProjectReference projectReference) {
		return Paths.get(new File(this.gitBasedRepositoryBasePath).toURI())
				.resolve(projectReference.getProjectId())
				.toFile()
				.exists();
	}
	
	private void cloneRootLocallyIfNotExists(ProjectReference projectReference, OpenProjectListener openProjectListener) throws Exception {
		if (!Paths.get(new File(this.gitBasedRepositoryBasePath).toURI())
			.resolve(user.getIdentifier())
			.resolve(projectReference.getProjectId())
			.resolve(GitProjectManager.getProjectRootRepositoryName(projectReference.getProjectId()))
			.toFile()
			.exists()) {
			try (ILocalGitRepositoryManager localRepoManager = this.localGitRepositoryManager) {
				openProjectListener.progress("Cloning the git repository");
				
				// clone the repository locally
				localRepoManager.clone(
					projectReference.getProjectId(),
					remoteGitServerManager.getProjectRootRepositoryUrl(projectReference),
					null,
					remoteGitServerManager.getUsername(),
					remoteGitServerManager.getPassword(),
					true // init and update submodules
					
				);
				
			}
		}
	}
	
}