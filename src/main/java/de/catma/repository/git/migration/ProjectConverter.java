package de.catma.repository.git.migration;

import de.catma.document.comment.Comment;
import de.catma.properties.CATMAProperties;
import de.catma.properties.CATMAPropertyKey;
import de.catma.rbac.RBACRole;
import de.catma.repository.git.GitAnnotationCollectionHandler;
import de.catma.repository.git.GitLabUtils;
import de.catma.repository.git.GitProjectHandler;
import de.catma.repository.git.managers.JGitCredentialsManager;
import de.catma.repository.git.managers.JGitRepoManager;
import de.catma.repository.git.serialization.SerializationHelper;
import de.catma.repository.git.serialization.models.json_ld.JsonLdWebAnnotation;
import de.catma.tag.TagInstance;
import de.catma.tag.TagLibrary;
import de.catma.user.Member;
import de.catma.user.User;
import de.catma.util.Pair;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.IssuesApi;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import static de.catma.repository.git.managers.GitlabManagerRestricted.CATMA_COMMENT_LABEL;

public class ProjectConverter implements AutoCloseable { 
	private final Logger logger = Logger.getLogger(ProjectConverter.class.getName());

	private final Path backupPath;
	private final GitLabApi privilegedGitLabApi;
	private final LegacyProjectHandler legacyProjectHandler;

	private static final String SYSTEM_COMMITTER_NAME = "CATMA System";
	private static final String SYSTEM_COMMITTER_EMAIL = "support@[YOUR-DOMAIN]";

	public ProjectConverter() throws Exception {
		String propertiesFile = System.getProperties().containsKey("prop") ? System.getProperties().getProperty("prop") : "catma.properties";
		Properties catmaProperties = new Properties();
		catmaProperties.load(new FileInputStream(propertiesFile));
		CATMAProperties.INSTANCE.setProperties(catmaProperties);

		this.backupPath = Paths.get(CATMAPropertyKey.V6_REPO_MIGRATION_BACKUP_PATH.getValue());
		if (!this.backupPath.toFile().exists()) {
			if (!this.backupPath.toFile().mkdirs()) {
				throw new IllegalStateException(String.format("Failed to create backup path %s", this.backupPath));
			}
		}

		this.privilegedGitLabApi = new GitLabApi(
				 CATMAPropertyKey.GITLAB_SERVER_URL.getValue(),
				 CATMAPropertyKey.GITLAB_ADMIN_PERSONAL_ACCESS_TOKEN.getValue()
		);

		this.legacyProjectHandler = new LegacyProjectHandler(this.privilegedGitLabApi);
	}

	private boolean hasAnyResources(Path projectPath) {
		return (projectPath.resolve("documents").toFile().exists() && projectPath.resolve("documents").toFile().list().length > 0)
				|| (projectPath.resolve("collections").toFile().exists() && projectPath.resolve("collections").toFile().list().length > 0)
				|| (projectPath.resolve("tagsets").toFile().exists() && projectPath.resolve("tagsets").toFile().list().length > 0);
	}

	private boolean wasOpenedToday(String projectId) throws IOException {
		LocalDate todayUtc = LocalDate.now(ZoneId.of("UTC"));
		File currentLogFile = new File(
				CATMAPropertyKey.V6_REPO_MIGRATION_OPENCHECK_LOG_PATH.getValue(),
				String.format("%s.jetty.log", todayUtc.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))) // eg: 2023_09_18.jetty.log
		);

		String currentLogFileContents = FileUtils.readFileToString(currentLogFile, StandardCharsets.UTF_8);

		return currentLogFileContents.contains(projectId);
	}

	public void convertProject(String projectId) {
		logger.info(String.format("Converting project with ID %s", projectId));

		try {
			if (!CATMAPropertyKey.V6_REPO_MIGRATION_SKIP_OPENCHECK.getBooleanValue() && wasOpenedToday(projectId)) {
				logger.warning(String.format("Project with ID %s might be being worked on, skipping conversion", projectId));
				return;
			}

			logger.info(String.format("Retrieving members of project with ID %s", projectId));
			Set<Member> members = legacyProjectHandler.getLegacyProjectMembers(projectId);
			Member owner = members.stream().filter(member -> member.getRole().equals(RBACRole.OWNER)).findAny().orElse(null);
			if (owner == null) {
				throw new IllegalStateException(String.format("Failed to find an owner for project with ID %s", projectId));
			}

			Pair<User, String> userAndImpersonationToken = legacyProjectHandler.acquireUser(owner.getIdentifier());

			if (CATMAPropertyKey.V6_REPO_MIGRATION_ONLY_COMMENT_MIGRATION.getBooleanValue()) {
				// only run comment migration, nothing else
				// (for this to work convertProject must have run previously with V6_REPO_MIGRATION_ONLY_COMMENT_MIGRATION set to False)
				onlyMigrateComments(projectId, userAndImpersonationToken.getFirst().getIdentifier(), userAndImpersonationToken.getSecond());
				return;
			}

			try (GitLabApi restrictedGitLabApi = new GitLabApi(CATMAPropertyKey.GITLAB_SERVER_URL.getValue(), userAndImpersonationToken.getSecond())) {
				logger.info(String.format("Retrieving legacy project (group) with ID %s", projectId));
				Group legacyProject = restrictedGitLabApi.getGroupApi().getGroup(projectId);

				User ownerUser = userAndImpersonationToken.getFirst();

				JGitCredentialsManager jGitCredentialsManager = new JGitCredentialsManager(new GitUserInformationProviderMigrationImpl(restrictedGitLabApi));

				logger.info(String.format("Creating temp directory for project with ID %s and owner \"%s\"", projectId, ownerUser.getIdentifier()));
				String migrationTempPath = new File(CATMAPropertyKey.TEMP_DIR.getValue(), "project_migration").getAbsolutePath();
				Path userTempPath = Paths.get(migrationTempPath, ownerUser.getIdentifier()); // this is the same path that JGitRepoManager constructs internally

				if (!userTempPath.toFile().exists() && !userTempPath.toFile().mkdirs()) {
					throw new IllegalStateException(String.format("Failed to create temp directory at path %s", userTempPath));
				}

				Path projectPath = userTempPath.resolve(projectId);
				if (projectPath.toFile().exists()) {
					legacyProjectHandler.setUserWritablePermissions(projectPath);
					FileUtils.deleteDirectory(projectPath.toFile());
				}

				String rootRepoName = projectId + "_root";

				try (JGitRepoManager repoManager = new JGitRepoManager(migrationTempPath, userAndImpersonationToken.getFirst())) {
					logger.info(String.format("Cloning project with ID %s", projectId));
					repoManager.cloneWithSubmodules(
							projectId,
							legacyProjectHandler.getProjectRootRepositoryUrl(restrictedGitLabApi, projectId, rootRepoName),
							jGitCredentialsManager
					);
				}

				logger.info(String.format("Creating backup for project with ID %s", projectId));
				Path projectBackupPath = backupPath.resolve(projectId);

				if (projectBackupPath.toFile().exists() && projectBackupPath.toFile().list().length > 0) {
					if (!CATMAPropertyKey.V6_REPO_MIGRATION_OVERWRITE_V6_PROJECT_BACKUP.getBooleanValue()) {
						throw new IllegalStateException(String.format("Project already has a non-empty backup at path %s", projectBackupPath));
					}

					legacyProjectHandler.setUserWritablePermissions(projectBackupPath);
					FileUtils.deleteDirectory(projectBackupPath.toFile());
				}

				FileUtils.copyDirectory(projectPath.toFile(), projectBackupPath.toFile());

				legacyProjectHandler.setUserWritablePermissions(projectPath);

				logger.info(String.format(
						"Opening root repo, checking out migration branch and initing/updating submodules for project with ID %s", projectId)
				);
				Path projectRootPath = projectPath.resolve(rootRepoName);

				try (JGitRepoManager repoManager = new JGitRepoManager(migrationTempPath, userAndImpersonationToken.getFirst())) {
					repoManager.open(projectId, rootRepoName);
					String migrationBranch = CATMAPropertyKey.V6_REPO_MIGRATION_BRANCH.getValue();

					if (!repoManager.hasRemoteRef(Constants.DEFAULT_REMOTE_NAME + "/" + migrationBranch)) {
						logger.warning(
								String.format("Project with ID %s has no migration branch \"%s\" and cannot be converted",	projectId, migrationBranch)
						);
						return;
					}

					repoManager.checkoutFromOrigin(migrationBranch);
					repoManager.initAndUpdateSubmodules(jGitCredentialsManager, new HashSet<>(repoManager.getSubmodulePaths()));

					// at this stage there could be untracked old submodule dirs because we checked out the migration branch and called initAndUpdateSubmodules
					// delete them so that we don't accidentally add them again later
					Set<String> untracked = repoManager.getStatus().getUntracked();
					if (!untracked.isEmpty()) {
						logger.info(String.format("Deleting untracked old submodule dirs and associated config for project with ID %s", projectId));

						for (String relativePath : untracked) {
							String unixStyleRelativePath = FilenameUtils.separatorsToUnix(relativePath);
							if (!unixStyleRelativePath.startsWith("documents/")
									&& !unixStyleRelativePath.startsWith("collections/")
									&& !unixStyleRelativePath.startsWith("tagsets/")
							) {
								throw new IllegalStateException(String.format("Encountered unexpected untracked path: %s", unixStyleRelativePath));
							}
						}

						for (String relativeSubmodulePath : untracked) {
							String unixStyleRelativeSubmodulePath = FilenameUtils.separatorsToUnix(relativeSubmodulePath);

							StoredConfig repositoryConfig = repoManager.getGitApi().getRepository().getConfig();
							repositoryConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, unixStyleRelativeSubmodulePath);
							repositoryConfig.save();

							File submoduleGitDir = projectRootPath
									.resolve(Constants.DOT_GIT)
									.resolve(Constants.MODULES)
									.resolve(relativeSubmodulePath)
									.toFile();
							File submoduleDir = projectRootPath.resolve(relativeSubmodulePath).toFile();
							FileUtils.deleteDirectory(submoduleGitDir);
							FileUtils.deleteDirectory(submoduleDir);
						}
					}

					List<String> relativeSubmodulePaths = repoManager.getSubmodulePaths();

					logger.info(String.format("Integrating submodule resources into project with ID %s", projectId));
					for (String relativeSubmodulePath : relativeSubmodulePaths) {
						// create a copy of the submodule
						File absoluteSubmodulePath = projectRootPath.resolve(relativeSubmodulePath).toFile();
						File absoluteSubmoduleCopyPath = projectRootPath.resolve(relativeSubmodulePath + "_temp").toFile();

						FileUtils.copyDirectory(absoluteSubmodulePath, absoluteSubmoduleCopyPath);

						// remove the submodule
						legacyProjectHandler.setUserWritablePermissions(projectRootPath);
						repoManager.removeSubmodule(absoluteSubmodulePath);

						// submodule removal detaches so we have to reopen again
						repoManager.open(projectId, rootRepoName);

						// move the submodule files back into the repo
						FileUtils.moveDirectory(absoluteSubmoduleCopyPath, absoluteSubmodulePath);

						// delete the old .git file
						File dotGitFile = absoluteSubmodulePath.toPath().resolve(".git").toFile();

						if (!dotGitFile.delete()) {
							throw new IllegalStateException(
								String.format(
										"Tried to delete .git at path %s for project with ID %s but there was none! "
												+ "You need to check before proceeding with the conversion!",
										dotGitFile,
										projectId
								)
							);
						}

						// add the files for what was once the submodule
						repoManager.add(new File(relativeSubmodulePath));
					}

					logger.info(String.format("Deleting .gitmodules from project with ID %s", projectId));
					repoManager.remove(projectRootPath.resolve(".gitmodules").toFile());
					// explicitly add .gitmodules here, otherwise it remains unstaged on the live server for some reason
					repoManager.add(projectRootPath.resolve(".gitmodules").toFile());

					logger.info(String.format("Committing integration of submodules for project with ID %s", projectId));
					repoManager.commit("Direct integration of submodules", SYSTEM_COMMITTER_NAME, SYSTEM_COMMITTER_EMAIL, false);

					if (!hasAnyResources(projectRootPath)) {
						logger.warning(String.format("Project with ID %s does not seem to have any resources, skipping conversion", projectId));
						return;
					}

					String newProjectId = cleanProjectId(projectId);

					logger.info(
							String.format("Creating new target project with ID %s in the owner's namespace \"%s\"", newProjectId, ownerUser.getIdentifier())
					);
					Project project = restrictedGitLabApi.getProjectApi().createProject(
							newProjectId,
							null,
							legacyProject.getDescription(),
							null,
							null,
							null,
							null,
							Visibility.PRIVATE,
							null,
							null
					);
					project.setRemoveSourceBranchAfterMerge(false);
					restrictedGitLabApi.getProjectApi().updateProject(project);

					logger.info(
							String.format(
									"Updating remote 'origin' to new target project with ID %s in the owner's namespace \"%s\"",
									newProjectId,
									ownerUser.getIdentifier()
							)
					);
					repoManager.remoteRemove(Constants.DEFAULT_REMOTE_NAME);
					repoManager.remoteAdd(Constants.DEFAULT_REMOTE_NAME, GitLabUtils.rewriteGitLabServerUrl(project.getHttpUrlToRepo()));

					// convert collections to the new storage layout
					logger.info(String.format("Converting collections for project with ID %s", projectId));
					TagLibrary tagLibrary = legacyProjectHandler.getTagLibrary(repoManager, projectRootPath.toFile(), ownerUser);

					for (String relativeSubmodulePath : relativeSubmodulePaths) {
						if (relativeSubmodulePath.startsWith(GitProjectHandler.ANNOTATION_COLLECTIONS_DIRECTORY_NAME)) {
							String collectionId = relativeSubmodulePath.substring(relativeSubmodulePath.indexOf('/') + 1);
							convertCollection(
									projectId,
									projectRootPath.toFile(),
									collectionId,
									projectRootPath.resolve(GitProjectHandler.ANNOTATION_COLLECTIONS_DIRECTORY_NAME).resolve(collectionId)
											.resolve(GitAnnotationCollectionHandler.ANNNOTATIONS_DIR),
									tagLibrary,
									repoManager,
									ownerUser
							);
						}
					}

					repoManager.addAllAndCommit("Converted annotation collections", SYSTEM_COMMITTER_NAME, SYSTEM_COMMITTER_EMAIL, false);

					logger.info(String.format("Merging migration branch into master for project with ID %s", projectId));
					MergeResult mergeResult = null;

					if (repoManager.hasRef(Constants.MASTER)) {
						repoManager.checkout(Constants.MASTER);
						mergeResult = repoManager.merge(migrationBranch);
					}
					else {
						repoManager.checkoutNewFromBranch(Constants.MASTER, migrationBranch);
					}

					if (mergeResult != null && !mergeResult.getMergeStatus().isSuccessful()) {
						logger.severe(String.format("Failed to merge \"%s\" into \"%s\": %s", migrationBranch, Constants.MASTER, mergeResult));
						return;
					}

					logger.info("Pushing converted project (master branch)");
					repoManager.pushMaster(jGitCredentialsManager);

					logger.info("Adding original team members to the new project");
					for (Member member : members) {
						if (member.getIdentifier().equals(ownerUser.getIdentifier())) {
							continue;
						}

						RBACRole role = member.getRole();
						if (role.getAccessLevel() < RBACRole.ASSISTANT.getAccessLevel()) {
							role = RBACRole.ASSISTANT; // this is the lowest role now for CATMA projects
						}

						restrictedGitLabApi.getProjectApi().addMember(
								project.getId(),
								member.getUserId(),
								AccessLevel.forValue(role.getAccessLevel())
						);
					}

					// migrate comments
					if (!CATMAPropertyKey.V6_REPO_MIGRATION_SKIP_COMMENT_MIGRATION.getBooleanValue()) {
						migrateComments(projectId, restrictedGitLabApi, project);
					}

					if (
							CATMAPropertyKey.V6_REPO_MIGRATION_CLEANUP_CONVERTED_V6_PROJECT.getBooleanValue()
									// don't allow cleanup if comments haven't been migrated
									&& !CATMAPropertyKey.V6_REPO_MIGRATION_SKIP_COMMENT_MIGRATION.getBooleanValue()
					) {
						logger.info(String.format("Deleting legacy project (group) with ID %s", projectId));
						restrictedGitLabApi.getGroupApi().deleteGroup(projectId);

						logger.info(String.format("Deleting local Git repositories for project with ID %s", projectId));
						File gitRepositoryBaseDir = new File(CATMAPropertyKey.GIT_REPOSITORY_BASE_PATH.getValue());

						for (File userDir : gitRepositoryBaseDir.listFiles()) {
							if (userDir.isFile()) {
								logger.warning(String.format("Skipping unexpected file %s in %s", userDir.getName(), gitRepositoryBaseDir));
								continue;
							}

							for (File projectDir : userDir.listFiles()) {
								if (projectDir.getName().equals(projectId)) {
									legacyProjectHandler.setUserWritablePermissions(Paths.get(projectDir.getAbsolutePath()));

									try {
										FileUtils.deleteDirectory(projectDir);
									}
									catch (FileSystemException fse) {
										logger.log(Level.WARNING, String.format("Couldn't clean up project directory at path %s", projectDir), fse);
									}
								}
							}
						}
					}
				}

				if (CATMAPropertyKey.V6_REPO_MIGRATION_REMOVE_USER_TEMP_DIRECTORY.getBooleanValue()) {
					logger.info(String.format("Deleting temp directory at path %s", userTempPath.toFile()));

					try {
						legacyProjectHandler.deleteUserTempPath(userTempPath);
					}
					catch (Exception e) {
						logger.log(Level.WARNING, String.format("Couldn't clean up user temp directory at path %s", userTempPath), e);
					}
				}
			}
		}
		catch (Exception e) {
			logger.log(Level.SEVERE, String.format("Error converting project with ID %s", projectId), e);
		}
	}

	public void convertProjects(int limit) throws Exception {
		logger.info("Converting projects...");

		boolean hasLimit = limit != -1;

		Pager<Group> pager = legacyProjectHandler.getLegacyProjectReferences();

		while (pager.hasNext()) {
			for (Group group : pager.next()) {
				if (hasLimit && limit <= 0) {
					return;
				}

				// we are only interested in group-based CATMA projects
				if (group.getName().startsWith("CATMA")) {
					convertProject(group.getName());
					limit--;
				}
			}
		}
	}

	private void convertCollection(
			String projectId, File projectDirectory,
			String collectionId, Path annotationsPath,
			TagLibrary tagLibrary, JGitRepoManager repoManager, User ownerUser
	) throws Exception {
		logger.info(String.format("Converting collection with ID %s", collectionId));

		if (annotationsPath.toFile().exists() && annotationsPath.toFile().list().length > 0) {
			// load legacy annotation files
			List<Pair<JsonLdWebAnnotation, TagInstance>> annotations = legacyProjectHandler.loadLegacyTagInstances(
					projectId, collectionId, annotationsPath.toFile(), tagLibrary
			);
			Set<String> annotationIds = annotations.stream().map(entry -> entry.getSecond().getUuid()).collect(Collectors.toSet());

			// group and sort annotations
			Map<String, List<Pair<JsonLdWebAnnotation, TagInstance>>> annotationsGroupedByAuthorSortedByTimestamp = new HashMap<>();
			annotations.forEach(entry -> {
				String author = entry.getSecond().getAuthor();
				if (!annotationsGroupedByAuthorSortedByTimestamp.containsKey(author)) {
					annotationsGroupedByAuthorSortedByTimestamp.put(author, new ArrayList<>());
				}
				annotationsGroupedByAuthorSortedByTimestamp.get(author).add(entry);
			});
			annotationsGroupedByAuthorSortedByTimestamp.values().forEach(list -> list.sort(Comparator.comparing(pair -> pair.getSecond().getTimestamp())));

			// write annotations to user-specific page files
			for (String author : annotationsGroupedByAuthorSortedByTimestamp.keySet()) {
				User authorUser;

				if (author.equals(ownerUser.getIdentifier())) {
					authorUser = ownerUser;
				}
				else {
					Pair<User, String> userAndImpersonationToken = legacyProjectHandler.acquireUser(author);

					if (userAndImpersonationToken == null) {
						// if a user couldn't be found then 'author' is probably an email address rather than a username
						// this happens as a result of importing annotations and https://github.com/forTEXT/catma/issues/251
						// as there is a good chance that 'author' is wrong anyway, we simply write these annotations into ownerUser's pages - the actual
						// property value in the annotation JSON remains unchanged
						logger.info(String.format("Couldn't find a user for author \"%s\", defaulting to project owner", author));
						authorUser = ownerUser;
					}
					else {
						authorUser = userAndImpersonationToken.getFirst();
					}
				}

				GitAnnotationCollectionHandler gitAnnotationCollectionHandler =	new GitAnnotationCollectionHandler(
						repoManager, projectDirectory, projectId, authorUser.getIdentifier(), authorUser.getEmail()
				);
				gitAnnotationCollectionHandler.createTagInstances(collectionId, annotationsGroupedByAuthorSortedByTimestamp.get(author));
			}

			// delete legacy annotation files
			for (String annotationId : annotationIds) {
				File legacyAnnotationFile = annotationsPath.resolve(annotationId + ".json").toFile();
				if (!legacyAnnotationFile.delete()) {
					throw new IllegalStateException(String.format("Failed to delete legacy annotation file at path %s", legacyAnnotationFile));
				}
				repoManager.remove(legacyAnnotationFile);
			}
		}
	}

	private void migrateComments(String projectId, GitLabApi restrictedGitLabApi, Project project) throws GitLabApiException {
		logger.info(String.format("Migrating comments for project with ID %s", projectId));

		IssuesApi issuesApi = restrictedGitLabApi.getIssuesApi();

		// get a pager for all issues of the group
		IssueFilter issueFilter = new IssueFilter()
				.withLabels(Collections.singletonList(CATMA_COMMENT_LABEL))
				.withOrderBy(org.gitlab4j.api.Constants.IssueOrderBy.CREATED_AT)
				.withSort(org.gitlab4j.api.Constants.SortOrder.ASC);

		// if comment migration fails but some comments have already been migrated, set this filter to exclude the ones already migrated
		// attempting to move an issue that has already been moved results in: GitLabApiException: Cannot move issue due to insufficient permissions!
		String updatedBeforeSettingValue = CATMAPropertyKey.V6_REPO_MIGRATION_ONLY_COMMENT_MIGRATION_UPDATED_BEFORE.getValue();
		if (updatedBeforeSettingValue != null && !updatedBeforeSettingValue.trim().isEmpty()) {
			issueFilter.withUpdatedBefore(
					Date.from(ZonedDateTime.parse(updatedBeforeSettingValue, DateTimeFormatter.ISO_DATE_TIME).toInstant())
			);
		}

		Pager<Issue> issues = issuesApi.getGroupIssues(projectId, issueFilter, 100);

		List<Long> processedIssueIds = new ArrayList<>();

		// move issues to the new project and add the document ID label
		for (Issue issue : issues.all()) {
			// guard against multiple processing (happened during testing, not sure how)
			if (processedIssueIds.contains(issue.getId())) {
				continue;
			}

			Comment comment = new SerializationHelper<Comment>().deserialize(issue.getDescription(), Comment.class);

			// move issue
			Issue movedIssue = issuesApi.moveIssue(issue.getProjectId(), issue.getIid(), project.getId());

			// update labels
			List<String> labels = new ArrayList<>();
			labels.add(CATMA_COMMENT_LABEL); // existing labels 'belong' to the source project and are not moved
			labels.add(comment.getDocumentId());

			issuesApi.updateIssue(
					movedIssue.getProjectId(),
					movedIssue.getIid(),
					movedIssue.getTitle(),
					movedIssue.getDescription(),
					null,
					null,
					null,
					String.join(",", labels),
					null,
					null,
					null
			);

			processedIssueIds.add(issue.getId());
		}

		logger.info(String.format("Migrated %d comments for project with ID %s", processedIssueIds.size(), projectId));
	}

	private void onlyMigrateComments(String projectId, String username, String impersonationToken) throws GitLabApiException {
		logger.info(String.format("ONLY migrating comments for project with ID %s", projectId));

		String newProjectId = cleanProjectId(projectId);

		try (GitLabApi restrictedGitLabApi = new GitLabApi(CATMAPropertyKey.GITLAB_SERVER_URL.getValue(), impersonationToken)) {
			Project project = restrictedGitLabApi.getProjectApi().getProject(username, newProjectId);

			if (project != null) {
				migrateComments(projectId, restrictedGitLabApi, project);
			}
		}
	}

	private String cleanProjectId(String projectId) {
		// clean the project ID, ref 39770b
		if (!projectId.matches("CATMA_[\\w&&[^_]]{8}-[\\w&&[^_]]{4}-[\\w&&[^_]]{4}-[\\w&&[^_]]{4}-[\\w&&[^_]]{12}_.*")) {
			throw new IllegalStateException(String.format("Encountered unexpected project ID format: %s", projectId));
		}

		String uuidPart = projectId.substring(0, 43);
		String namePart = projectId.substring(43);

		if (namePart.matches("_+")) {
			// if the name part is all underscores, convert it to all xs (as would be the case for an all Hebrew name, for example)
			namePart = namePart.replaceAll("_", "x");
		}

		String cleanedName = namePart.trim()
				.replaceAll("[\\p{Punct}\\p{Space}]", "_") // replace punctuation and whitespace characters with underscore ( _ )
				.replaceAll("_+", "_") // collapse multiple consecutive underscores into one
				.replaceAll("[^\\p{Alnum}_]", "x") // replace any remaining non-alphanumeric characters with x (excluding underscore)
				.replaceAll("^_|_$", ""); // strip any leading or trailing underscore
		String newProjectId = uuidPart + cleanedName;

		return newProjectId;
	}

	@Override
	public void close() throws Exception {
		privilegedGitLabApi.close();
	}

	public static void main(String[] args) throws Exception {
		FileHandler fileHandler = new FileHandler("project_converter.log");
		fileHandler.setFormatter(new SimpleFormatter());
		Logger.getLogger("").addHandler(fileHandler);

		try (ProjectConverter projectConverter = new ProjectConverter()) {
			String projectList = CATMAPropertyKey.V6_REPO_MIGRATION_PROJECT_ID_LIST.getValue();

			if ((projectList != null) && !projectList.isEmpty()) {
				for (String projectId : projectList.split(",")) {
					projectConverter.convertProject(projectId);
				}
			}
			else {
				int limit = CATMAPropertyKey.V6_REPO_MIGRATION_MAX_PROJECTS.getIntValue();
				projectConverter.convertProjects(limit);
			}
		}
	}
}
