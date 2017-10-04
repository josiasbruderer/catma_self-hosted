package de.catma.repository.git.managers;

import de.catma.repository.git.exceptions.LocalGitRepositoryManagerException;
import de.catma.repository.git.interfaces.ILocalGitRepositoryManager;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class LocalGitRepositoryManager implements ILocalGitRepositoryManager, AutoCloseable {
	private final String repositoryBasePath;

	private Git gitApi;

	Git getGitApi() {
		return this.gitApi;
	}

	/**
	 * Whether this instance is attached to a Git repository.
	 *
	 * @return true if attached, otherwise false
	 */
	public boolean isAttached() {
		return this.gitApi != null;
	}

	/**
	 * Gets the repository base path for this instance.
	 *
	 * @return the base path as a String
	 */
	public String getRepositoryBasePath() {
		return this.repositoryBasePath;
	}

	/**
	 * Gets the current Git working tree for the repository this instance is attached to, if any.
	 *
	 * @return a {@link File} object
	 */
	public File getRepositoryWorkTree() {
		if (!this.isAttached()) {
			return null;
		}

		return this.gitApi.getRepository().getWorkTree();
	}

	public LocalGitRepositoryManager(Properties catmaProperties) {
		this.repositoryBasePath = catmaProperties.getProperty("GitBasedRepositoryBasePath");
	}

	/**
	 * Creates an instance of this class and opens an existing Git repository with the directory
	 * name <code>repositoryName</code>.
	 * <p>
	 * Calls <code>open(String)</code> internally.
	 *
	 * @param catmaProperties a {@link Properties} object
	 * @param repositoryName the directory name of the Git repository to open
	 * @throws LocalGitRepositoryManagerException if the Git repository couldn't be found or
	 *         couldn't be opened for some other reason
	 */
	public LocalGitRepositoryManager(Properties catmaProperties, String repositoryName)
			throws LocalGitRepositoryManagerException {
		this(catmaProperties);

		this.open(repositoryName);
	}

	/**
	 * Initialises a new Git repository with the directory name <code>name</code> and stores the
	 * <code>description</code> in '.git/description'.
	 *
	 * @param name the directory name of the Git repository to initialise
	 * @param description the description of the Git repository to initialise
	 * @throws LocalGitRepositoryManagerException if the Git repository already exists or couldn't
	 *         be initialised for some other reason
	 */
	@Override
	public void init(String name, @Nullable String description)
			throws LocalGitRepositoryManagerException {
		if (isAttached()) {
			throw new IllegalStateException("Can't call `init` on an attached instance");
		}

		File repositoryPath = new File(
			this.repositoryBasePath + "/" + name + "/"
		);

		// if the directory exists we assume it's a Git repo, could also check for a child .git
		// directory
		if (repositoryPath.exists() && repositoryPath.isDirectory()) {
			throw new LocalGitRepositoryManagerException(
				String.format(
					"A Git repository with the name '%s' already exists at base path '%s'. " +
					"Did you mean to call `open`?",
						name,
					this.repositoryBasePath
				)
			);
		}

		try {
			this.gitApi = Git.init().setDirectory(repositoryPath).call();
			this.gitApi.getRepository().setGitwebDescription(description);
		}
		catch (GitAPIException|IOException e) {
			throw new LocalGitRepositoryManagerException("Failed to init Git repository", e);
		}
	}

	/**
	 * Clones a remote repository whose address is specified via the <code>uri</code> parameter.
	 *
	 * @param uri the URI of the remote repository to clone
	 * @param username the username to authenticate with
	 * @param password the password to authenticate with
	 * @return the name of the cloned repository
	 */
	@Override
	public String clone(String uri, @Nullable String username, @Nullable String password)
			throws LocalGitRepositoryManagerException {
		if (isAttached()) {
			throw new IllegalStateException("Can't call `clone` on an attached instance");
		}

		String repositoryName = uri.substring(uri.lastIndexOf("/") + 1);
		if (repositoryName.endsWith(".git")) {
			repositoryName = repositoryName.substring(0, repositoryName.length() - 4);
		}
		File repositoryPath = new File(this.repositoryBasePath, repositoryName);

		try {
			CloneCommand cloneCommand = Git.cloneRepository().setURI(uri).setDirectory(
				repositoryPath
			);
			if (username != null) {
				cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
					username, password
				));
			}
			this.gitApi = cloneCommand.call();
		}
		catch (GitAPIException e) {
			throw new LocalGitRepositoryManagerException(
				"Failed to clone remote Git repository", e
			);
		}

		return repositoryName;
	}

	/**
	 * Opens an existing Git repository with the directory name <code>name</code>.
	 *
	 * @param name the directory name of the Git repository to open
	 * @throws LocalGitRepositoryManagerException if the Git repository couldn't be found or
	 *         couldn't be opened for some other reason
	 */
	@Override
	public void open(String name) throws LocalGitRepositoryManagerException {
		if (isAttached()) {
			throw new IllegalStateException("Can't call `open` on an attached instance");
		}

		File repositoryPath = new File(
			this.repositoryBasePath + "/" + name + "/"
		);

		// could also check for the absence of a child .git directory
		if (!repositoryPath.exists() || !repositoryPath.isDirectory()) {
			throw new LocalGitRepositoryManagerException(
				String.format(
					"Couldn't find a Git repository with the name '%s' at base path '%s'. " +
					"Did you mean to call `init`?",
						name,
					this.repositoryBasePath
				)
			);
		}

		try {
			this.gitApi = Git.open(repositoryPath);
		}
		catch (IOException e) {
			throw new LocalGitRepositoryManagerException("Failed to open Git repository", e);
		}
	}

	/**
	 * Writes a new file with contents <code>bytes</code> to disk at path <code>targetFile</code>
	 * and adds it to the attached Git repository.
	 * <p>
	 * It's the caller's responsibility to call <code>commit(String)</code>.
	 *
	 * @param targetFile a {@link File} object representing the target path
	 * @param bytes the file contents
	 * @throws LocalGitRepositoryManagerException if the file contents couldn't be written to disk
	 *         or the file couldn't be added for some other reason
	 */
	@Override
	public void add(File targetFile, byte[] bytes) throws LocalGitRepositoryManagerException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `add` on a detached instance");
		}

		try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
			fileOutputStream.write(bytes);

			Path basePath = this.gitApi.getRepository().getWorkTree().toPath();
			Path absoluteFilePath = Paths.get(targetFile.getAbsolutePath());
			Path relativeFilePath = basePath.relativize(absoluteFilePath);

			this.gitApi.add().addFilepattern(relativeFilePath.toString()).call();
		}
		catch (IOException e) {
			throw new LocalGitRepositoryManagerException("Failed to write file", e);
		}
		catch (GitAPIException e) {
			throw new LocalGitRepositoryManagerException("");
		}
	}

	/**
	 * Writes a new file with contents <code>bytes</code> to disk at path <code>targetFile</code>,
	 * adds it to the attached Git repository and commits.
	 * <p>
	 * Calls <code>add(File, byte[])</code> and <code>commit(String)</code> internally.
	 *
	 * @param targetFile a {@link File} object representing the target path
	 * @param bytes the file contents
	 * @throws LocalGitRepositoryManagerException if the file contents couldn't be written to disk
	 *         or the commit operation failed
	 */
	@Override
	public void addAndCommit(File targetFile, byte[] bytes)
			throws LocalGitRepositoryManagerException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `addAndCommit` on a detached instance");
		}

		this.add(targetFile, bytes);
		String commitMessage = String.format("Adding %s", targetFile.getName());
		this.commit(commitMessage);
	}

	/**
	 * Commits pending changes to the attached Git repository.
	 *
	 * @param message the commit message
	 * @throws LocalGitRepositoryManagerException if the commit operation failed
	 */
	@Override
	public void commit(String message) throws LocalGitRepositoryManagerException {
		if (!isAttached()) {
			throw new IllegalStateException("Can't call `commit` on a detached instance");
		}

		try {
			this.gitApi.commit().setMessage(message).call();
		}
		catch (GitAPIException e) {
			throw new LocalGitRepositoryManagerException("Failed to commit", e);
		}
	}

	@Override // AutoCloseable
	public void close() throws Exception {
		if (this.gitApi != null) {
			this.gitApi.close();
		}
	}
}
