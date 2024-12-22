package de.catma.repository.git.serialization.models.json_ld;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.models.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.catma.document.Range;
import de.catma.document.repository.RepositoryProperties;
import de.catma.document.repository.RepositoryPropertyKey;
import de.catma.document.source.ContentInfoSet;
import de.catma.document.source.FileOSType;
import de.catma.document.source.FileType;
import de.catma.document.source.IndexInfoSet;
import de.catma.document.source.SourceDocumentInfo;
import de.catma.document.source.TechInfoSet;
import de.catma.document.standoffmarkup.usermarkup.TagReference;
import de.catma.repository.git.GitProjectHandler;
import de.catma.repository.git.GitProjectManager;
import de.catma.repository.git.GitTagsetHandler;
import de.catma.repository.git.interfaces.IRemoteGitServerManager;
import de.catma.repository.git.managers.GitLabServerManager;
import de.catma.repository.git.managers.GitLabServerManagerTest;
import de.catma.repository.git.managers.JGitRepoManager;
import de.catma.repository.git.serialization.SerializationHelper;
import de.catma.tag.Property;
import de.catma.tag.PropertyDefinition;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagInstance;
import de.catma.tag.Version;
import de.catma.util.IDGenerator;
import helpers.Randomizer;
import helpers.UserIdentification;

public class JsonLdWebAnnotationTest {
	// this string needs to be formatted with the following 8 pieces of information:
	// projectRootRepositoryName
	// tagsetDefinitionUuid
	// tagDefinitionUuid
	// userPropertyDefinitionUuid
	// systemPropertyDefinitionUuid
	// userMarkupCollectionUuid
	// tagInstanceUuid
	// sourceDocumentUuid
	public static final String EXPECTED_SERIALIZED_ANNOTATION = "" +
			"{\n" +
			"\t\"body\":{\n" +
			"\t\t\"@context\":{\n" +
			"\t\t\t\"UPROP_DEF\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/tagsets/%2$s/%3$s/propertydefs.json/%4$s\",\n" +
			"\t\t\t\"catma_displaycolor\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/tagsets/%2$s/%3$s/propertydefs.json/%5$s\",\n" +
			"\t\t\t\"tag\":\"http://[YOUR-DOMAIN]/portal/tag\",\n" +
			"\t\t\t\"tagset\":\"http://[YOUR-DOMAIN]/portal/tagset\"\n" +
			"\t\t},\n" +
			"\t\t\"properties\":{\n" +
			"\t\t\t\"system\":{\n" +
			"\t\t\t\t\"catma_displaycolor\":[\"SYSPROP_VAL_1\"]\n" +
			"\t\t\t},\n" +
			"\t\t\t\"user\":{\n" +
			"\t\t\t\t\"UPROP_DEF\":[\"UPROP_VAL_2\"]\n" +
			"\t\t\t}\n" +
			"\t\t},\n" +
			"\t\t\"tag\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/tagsets/%2$s/%3$s\",\n" +
			"\t\t\"tagset\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/tagsets/%2$s\",\n" +
			"\t\t\"type\":\"Dataset\"\n" +
			"\t},\n" +
			"\t\"@context\":\"http://www.w3.org/ns/anno.jsonld\",\n" +
			"\t\"id\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/collections/%6$s/annotations/%7$s.json\",\n" +
			"\t\"target\":{\n" +
			"\t\t\"items\":[{\n" +
			"\t\t\t\"selector\":{\n" +
			"\t\t\t\t\"end\":18,\n" +
			"\t\t\t\t\"start\":12,\n" +
			"\t\t\t\t\"type\":\"TextPositionSelector\"\n" +
			"\t\t\t},\n" +
			"\t\t\t\"source\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/documents/%8$s\"\n" +
			"\t\t},\n" +
			"\t\t{\n" +
			"\t\t\t\"selector\":{\n" +
			"\t\t\t\t\"end\":47,\n" +
			"\t\t\t\t\"start\":41,\n" +
			"\t\t\t\t\"type\":\"TextPositionSelector\"\n" +
			"\t\t\t},\n" +
			"\t\t\t\"source\":\"http://[YOUR-DOMAIN]/gitlab/%1$s/documents/%8$s\"\n" +
			"\t\t}],\n" +
			"\t\t\"type\":\"List\"\n" +
			"\t},\n" +
			"\t\"type\":\"Annotation\"\n" +
			"}";

	private Properties catmaProperties;
	private de.catma.user.User catmaUser;
	private GitLabServerManager gitLabServerManager;

	private ArrayList<String> projectsToDeleteOnTearDown = new ArrayList<>();

	private ArrayList<File> directoriesToDeleteOnTearDown = new ArrayList<>();

	public JsonLdWebAnnotationTest() throws Exception {
		String propertiesFile = System.getProperties().containsKey("prop") ?
				System.getProperties().getProperty("prop") : "catma.properties";

		this.catmaProperties = new Properties();
		this.catmaProperties.load(new FileInputStream(propertiesFile));
	}

	@Before
	public void setUp() throws Exception {
		// create a fake CATMA user which we'll use to instantiate the GitLabServerManager & JGitRepoManager
		this.catmaUser = Randomizer.getDbUser();
		RepositoryProperties.INSTANCE.setProperties(catmaProperties);
		this.gitLabServerManager = new GitLabServerManager(
				UserIdentification.userToMap(this.catmaUser.getIdentifier()));
	}

	@After
	public void tearDown() throws Exception {
		if (this.projectsToDeleteOnTearDown.size() > 0) {
			GitProjectManager gitProjectHandler = new GitProjectManager(
					RepositoryPropertyKey.GitBasedRepositoryBasePath.getValue(),
					UserIdentification.userToMap(this.catmaUser.getIdentifier()));

			for (String projectId : this.projectsToDeleteOnTearDown) {
				gitProjectHandler.delete(projectId);
			}
			this.projectsToDeleteOnTearDown.clear();
		}

		if (this.directoriesToDeleteOnTearDown.size() > 0) {
			for (File dir : this.directoriesToDeleteOnTearDown) {
				FileUtils.deleteDirectory(dir);
			}
			this.directoriesToDeleteOnTearDown.clear();
		}

		// delete the GitLab user that the GitLabServerManager constructor in setUp would have
		// created - see GitLabServerManagerTest tearDown() for more info
		User user = this.gitLabServerManager.getGitLabUser();
		this.gitLabServerManager.getAdminGitLabApi().getUserApi().deleteUser(user.getId());
		GitLabServerManagerTest.awaitUserDeleted(
			this.gitLabServerManager.getAdminGitLabApi().getUserApi(), user.getId()
		);
	}

	/**
	 * @return a HashMap<String, Object> with these keys:
	 *         'jsonLdWebAnnotation' - for the JsonLdWebAnnotation object
	 *         'projectUuid'
	 *         --- following additional keys which are to be used when formatting EXPECTED_SERIALIZED_ANNOTATION ---:
	 *         projectRootRepositoryName, tagsetDefinitionUuid, tagDefinitionUuid, userPropertyDefinitionUuid,
	 *         systemPropertyDefinitionUuid, userMarkupCollectionUuid, tagInstanceUuid, sourceDocumentUuid
	 */
	public static HashMap<String, Object> getJsonLdWebAnnotation(JGitRepoManager jGitRepoManager,
																 IRemoteGitServerManager gitLabServerManager,
																 de.catma.user.User catmaUser
	) throws Exception {

		try (JGitRepoManager localJGitRepoManager = jGitRepoManager) {
			// caller should do the following:
//			this.directoriesToDeleteOnTearDown.add(localJGitRepoManager.getRepositoryBasePath());

			// create project
			GitProjectManager gitProjectManager = new GitProjectManager(
					RepositoryPropertyKey.GitBasedRepositoryBasePath.getValue(),
					UserIdentification.userToMap(catmaUser.getIdentifier()));

			String projectId = gitProjectManager.create(
					"Test CATMA Project", "This is a test CATMA project"
			);
			// caller should do the following:
//			this.projectsToDeleteOnTearDown.add(projectId);

			GitProjectHandler gitProjectHandler = new GitProjectHandler(null, projectId, jGitRepoManager, gitLabServerManager);

			// add new tagset to project
			String tagsetId = gitProjectHandler.createTagset(
					null, "Test Tagset", null
			);

			// add new source document to project
			File originalSourceDocument = new File("testdocs/rose_for_emily.pdf");
			File convertedSourceDocument = new File("testdocs/rose_for_emily.txt");

			FileInputStream originalSourceDocumentStream = new FileInputStream(originalSourceDocument);
			FileInputStream convertedSourceDocumentStream = new FileInputStream(convertedSourceDocument);

			IndexInfoSet indexInfoSet = new IndexInfoSet();
			indexInfoSet.setLocale(Locale.ENGLISH);

			ContentInfoSet contentInfoSet = new ContentInfoSet(
					"William Faulkner",
					"",
					"",
					"A Rose for Emily"
			);

			TechInfoSet techInfoSet = new TechInfoSet(
					FileType.TEXT,
					StandardCharsets.UTF_8,
					FileOSType.DOS,
					705211438L
			);

			SourceDocumentInfo sourceDocumentInfo = new SourceDocumentInfo(
					indexInfoSet, contentInfoSet, techInfoSet
			);

			String sourceDocumentId = gitProjectHandler.createSourceDocument(
					null, originalSourceDocumentStream, originalSourceDocument.getName(),
					convertedSourceDocumentStream, convertedSourceDocument.getName(),
					null, null,
					sourceDocumentInfo
			);

			// add new markup collection to project
			String markupCollectionId = gitProjectHandler.createMarkupCollection(
					null, "Test Markup Collection", null,
					sourceDocumentId, "fakeSourceDocumentVersion"
			);

			// commit the changes to the project root repo (addition of tagset, source document and markup collection
			// submodules)
			String projectRootRepositoryName = GitProjectManager.getProjectRootRepositoryName(projectId);
			localJGitRepoManager.open(projectId, projectRootRepositoryName);
			localJGitRepoManager.commit(
					String.format(
							"Adding new tagset %s, source document %s and markup collection %s",
							tagsetId,
							sourceDocumentId,
							markupCollectionId
					),
					"Test Committer",
					"testcommitter@[YOUR-DOMAIN]"
			);
			localJGitRepoManager.detach();  // can't call open on an attached instance

			// construct TagDefinition object
			IDGenerator idGenerator = new IDGenerator();

			List<String> systemPropertyPossibleValues = 
					Arrays.asList("SYSPROP_VAL_1", "SYSPROP_VAL_2");
			
			PropertyDefinition systemPropertyDefinition = new PropertyDefinition(
					PropertyDefinition.SystemPropertyName.catma_displaycolor.toString(),
					systemPropertyPossibleValues
			);

			List<String> userPropertyPossibleValues = 
					Arrays.asList("UPROP_VAL_1", "UPROP_VAL_2");
			
			PropertyDefinition userPropertyDefinition = new PropertyDefinition(
					"UPROP_DEF", userPropertyPossibleValues
			);

			String tagDefinitionUuid = idGenerator.generate();
			TagDefinition tagDefinition = new TagDefinition(
					null, tagDefinitionUuid, "TAG_DEF", new Version(), null, null,
					tagsetId
			);
			tagDefinition.addSystemPropertyDefinition(systemPropertyDefinition);
			tagDefinition.addUserDefinedPropertyDefinition(userPropertyDefinition);

			// call createTagDefinition
			// NB: in this case we know that the tagset submodule is on the master branch tip, ie: not in a detached
			// head state, so it's safe to make changes to the submodule and commit them
			// TODO: createTagDefinition should probably do some validation and fail fast if the tagset submodule is in
			// a detached head state - in that case the submodule would need to be updated first
			// see the "Updating a submodule in-place in the container" scenario at
			// https://medium.com/@porteneuve/mastering-git-submodules-34c65e940407
			GitTagsetHandler gitTagsetHandler = new GitTagsetHandler(localJGitRepoManager, gitLabServerManager);
			String returnedTagDefinitionId = gitTagsetHandler.createOrUpdateTagDefinition(projectId, tagsetId, tagDefinition);

			assertNotNull(returnedTagDefinitionId);
			assert returnedTagDefinitionId.startsWith("CATMA_");

			// the JGitRepoManager instance should always be in a detached state after GitTagsetHandler calls return
			assertFalse(localJGitRepoManager.isAttached());

			assertEquals(tagDefinitionUuid, returnedTagDefinitionId);

			// commit and push submodule changes (creation of tag definition)
			// TODO: add methods to JGitRepoManager to do this
			localJGitRepoManager.open(projectId, projectRootRepositoryName);

			Repository projectRootRepository = localJGitRepoManager.getGitApi().getRepository();
			String tagsetSubmodulePath = String.format(
					"%s/%s", GitProjectHandler.TAGSET_SUBMODULES_DIRECTORY_NAME, tagsetId
			);
			Repository tagsetSubmoduleRepository = SubmoduleWalk.getSubmoduleRepository(
					projectRootRepository, tagsetSubmodulePath
			);
			Git submoduleGit = new Git(tagsetSubmoduleRepository);
			submoduleGit.add().addFilepattern(tagDefinitionUuid).call();
			submoduleGit.commit().setMessage(
					String.format("Adding tag definition %s", tagDefinitionUuid)
			).setCommitter("Test Committer", "testcommitter@[YOUR-DOMAIN]").call();
			submoduleGit.push().setCredentialsProvider(
					new UsernamePasswordCredentialsProvider(
							gitLabServerManager.getUsername(),
							gitLabServerManager.getPassword()
					)
			).call();
			tagsetSubmoduleRepository.close();
			submoduleGit.close();

			// commit and push project root repo changes (update of tagset submodule)
			localJGitRepoManager.getGitApi().add().addFilepattern(tagsetSubmodulePath).call();
			localJGitRepoManager.commit(
					String.format("Updating tagset %s", tagsetId),
					"Test Committer",
					"testcommitter@[YOUR-DOMAIN]"
			);

			// construct TagInstance object
			Property systemProperty = new Property(systemPropertyDefinition, Collections.singleton("SYSPROP_VAL_1"));
			Property userProperty = new Property(userPropertyDefinition, Collections.singleton("UPROP_VAL_2"));

			String tagInstanceUuid = idGenerator.generate();
			TagInstance tagInstance = new TagInstance(tagInstanceUuid, tagDefinition);
			tagInstance.addSystemProperty(systemProperty);
			tagInstance.addUserDefinedProperty(userProperty);

			// construct JsonLdWebAnnotation object
			String sourceDocumentUri = String.format(
					"http://[YOUR-DOMAIN]/gitlab/%s/%s/%s",
					projectRootRepositoryName,
					GitProjectHandler.SOURCE_DOCUMENT_SUBMODULES_DIRECTORY_NAME,
					sourceDocumentId
			);

			Range range1 = new Range(12, 18);
			Range range2 = new Range(41, 47);

			List<TagReference> tagReferences = new ArrayList<>(
					Arrays.asList(
							new TagReference(
									tagInstance, sourceDocumentUri, range1, markupCollectionId
							),
							new TagReference(
									tagInstance, sourceDocumentUri, range2, markupCollectionId
							)
					)
			);

			JsonLdWebAnnotation jsonLdWebAnnotation = new JsonLdWebAnnotation(
					"http://[YOUR-DOMAIN]/gitlab", projectId, tagReferences
			);

			HashMap<String, Object> returnValue = new HashMap<>();
			returnValue.put("jsonLdWebAnnotation", jsonLdWebAnnotation);
			returnValue.put("projectRootRepositoryName", projectRootRepositoryName);
			returnValue.put("projectUuid", projectId);
			returnValue.put("tagsetDefinitionUuid", tagsetId);
			returnValue.put("tagDefinitionUuid", tagDefinitionUuid);
			returnValue.put("userMarkupCollectionUuid", markupCollectionId);
			returnValue.put("tagInstanceUuid", tagInstanceUuid);
			returnValue.put("sourceDocumentUuid", sourceDocumentId);

			return returnValue;
		}
	}

	@Test
	public void serialize() throws Exception {
		try (JGitRepoManager jGitRepoManager = new JGitRepoManager(this.catmaProperties.getProperty(RepositoryPropertyKey.GitBasedRepositoryBasePath.name()), this.catmaUser)) {
			this.directoriesToDeleteOnTearDown.add(jGitRepoManager.getRepositoryBasePath());

			HashMap<String, Object> getJsonLdWebAnnotationResult = JsonLdWebAnnotationTest.getJsonLdWebAnnotation(
					jGitRepoManager, this.gitLabServerManager, this.catmaUser
			);
			this.projectsToDeleteOnTearDown.add((String)getJsonLdWebAnnotationResult.get("projectUuid"));

			JsonLdWebAnnotation jsonLdWebAnnotation = (JsonLdWebAnnotation) getJsonLdWebAnnotationResult.get(
					"jsonLdWebAnnotation"
			);

			String serialized = new SerializationHelper<JsonLdWebAnnotation>().serialize(jsonLdWebAnnotation);

			String expectedSerializedRepresentation = JsonLdWebAnnotationTest.EXPECTED_SERIALIZED_ANNOTATION
					.replaceAll("[\n\t]", "");
			expectedSerializedRepresentation = String.format(
					expectedSerializedRepresentation,
					getJsonLdWebAnnotationResult.get("projectRootRepositoryName"),
					getJsonLdWebAnnotationResult.get("tagsetDefinitionUuid"),
					getJsonLdWebAnnotationResult.get("tagDefinitionUuid"),
					getJsonLdWebAnnotationResult.get("userPropertyDefinitionUuid"),
					getJsonLdWebAnnotationResult.get("systemPropertyDefinitionUuid"),
					getJsonLdWebAnnotationResult.get("userMarkupCollectionUuid"),
					getJsonLdWebAnnotationResult.get("tagInstanceUuid"),
					getJsonLdWebAnnotationResult.get("sourceDocumentUuid")
			);

			assertEquals(expectedSerializedRepresentation, serialized);
		}
	}

	@Test
	public void deserialize() throws Exception {
		String toDeserialize = JsonLdWebAnnotationTest.EXPECTED_SERIALIZED_ANNOTATION
				.replaceAll("[\n\t]", "");
		toDeserialize = String.format(
				toDeserialize,
				"fakeProjectRootRepositoryName",
				"fakeTagsetDefinitionUuid",
				"fakeTagDefinitionUuid",
				"fakeUserPropertyDefinitionUuid",
				"fakeSystemPropertyDefinitionUuid",
				"fakeUserMarkupCollectionUuid",
				"fakeTagInstanceUuid",
				"fakeSourceDocumentUuid"
		);

		JsonLdWebAnnotation jsonLdWebAnnotation = new SerializationHelper<JsonLdWebAnnotation>().deserialize(
				toDeserialize, JsonLdWebAnnotation.class
		);

		assertNotNull(jsonLdWebAnnotation);

		// re-serialize and assert that what comes out is what went in
		String serialized = new SerializationHelper<JsonLdWebAnnotation>().serialize(jsonLdWebAnnotation);

		assertEquals(toDeserialize, serialized);
	}

	@Test
	public void toTagReferenceList() throws Exception {
		try (JGitRepoManager jGitRepoManager = new JGitRepoManager(this.catmaProperties.getProperty(RepositoryPropertyKey.GitBasedRepositoryBasePath.name()), this.catmaUser)) {
			this.directoriesToDeleteOnTearDown.add(jGitRepoManager.getRepositoryBasePath());

			// TODO: test with a hierarchy of tag definitions
			HashMap<String, Object> getJsonLdWebAnnotationResult = JsonLdWebAnnotationTest.getJsonLdWebAnnotation(
					jGitRepoManager, this.gitLabServerManager, this.catmaUser
			);
			JsonLdWebAnnotation jsonLdWebAnnotation = (JsonLdWebAnnotation)getJsonLdWebAnnotationResult.get(
					"jsonLdWebAnnotation"
			);
			this.projectsToDeleteOnTearDown.add((String)getJsonLdWebAnnotationResult.get("projectUuid"));

			assertNotNull(jsonLdWebAnnotation);

			List<TagReference> tagReferences = jsonLdWebAnnotation.toTagReferenceList(
					(String)getJsonLdWebAnnotationResult.get("projectUuid"),
					(String)getJsonLdWebAnnotationResult.get("userMarkupCollectionUuid"),
					jGitRepoManager, this.gitLabServerManager
			);

			assertEquals(2, tagReferences.size());

			for (TagReference tagReference : tagReferences) {
				TagDefinition tagDefinition = tagReference.getTagDefinition();
				TagInstance tagInstance = tagReference.getTagInstance();

				assertEquals(
						getJsonLdWebAnnotationResult.get("tagsetDefinitionUuid"),
						tagDefinition.getTagsetDefinitionUuid()
				);
				assertEquals(getJsonLdWebAnnotationResult.get("tagDefinitionUuid"), tagDefinition.getUuid());
				assertEquals("TAG_DEF", tagDefinition.getName());
				assertEquals("", tagDefinition.getParentUuid());

				PropertyDefinition[] systemPropertyDefinitions = tagDefinition.getSystemPropertyDefinitions()
						.toArray(new PropertyDefinition[0]);
				assertEquals(1, systemPropertyDefinitions.length);
				assertEquals(
						getJsonLdWebAnnotationResult.get("systemPropertyDefinitionUuid"),
						systemPropertyDefinitions[0].getName()
				);
				assertEquals("catma_displaycolor", systemPropertyDefinitions[0].getName());
				List<String> possibleSystemPropertyValues = systemPropertyDefinitions[0].getPossibleValueList();
				assertEquals(2, possibleSystemPropertyValues.size());
				assertArrayEquals(
					new String[]{"SYSPROP_VAL_1", "SYSPROP_VAL_2"}, possibleSystemPropertyValues.toArray(new String[0])
				);

				PropertyDefinition[] userPropertyDefinitions = tagDefinition.getUserDefinedPropertyDefinitions()
						.toArray(new PropertyDefinition[0]);
				assertEquals(1, userPropertyDefinitions.length);
				assertEquals(
						getJsonLdWebAnnotationResult.get("userPropertyDefinitionUuid"),
						userPropertyDefinitions[0].getName()
				);
				assertEquals("UPROP_DEF", userPropertyDefinitions[0].getName());
				List<String> possibleUserPropertyValues = userPropertyDefinitions[0].getPossibleValueList();
				assertEquals(2, possibleUserPropertyValues.size());
				assertArrayEquals(
					new String[]{"UPROP_VAL_1", "UPROP_VAL_2"}, possibleUserPropertyValues.toArray(new String[0])
				);

				assertEquals(getJsonLdWebAnnotationResult.get("tagInstanceUuid"), tagInstance.getUuid());

				Property[] systemProperties = tagInstance.getSystemProperties().toArray(new Property[0]);
				assertEquals(1, systemProperties.length);
				assertEquals(systemPropertyDefinitions[0], systemProperties[0].getPropertyDefinition());
				List<String> systemPropertyValues = systemProperties[0].getPropertyValueList();
				assertEquals(1, systemPropertyValues.size());
				assertEquals("SYSPROP_VAL_1", systemPropertyValues.get(0));

				Property[] userProperties = tagInstance.getUserDefinedProperties().toArray(new Property[0]);
				assertEquals(1, userProperties.length);
				assertEquals(userPropertyDefinitions[0], userProperties[0].getPropertyDefinition());
				List<String> userPropertyValues = userProperties[0].getPropertyValueList();
				assertEquals(1, userPropertyValues.size());
				assertEquals("UPROP_VAL_2", userPropertyValues.get(0));
			}

			assertEquals(
					new URI(
							String.format(
									"http://[YOUR-DOMAIN]/gitlab/%s/%s/%s",
									getJsonLdWebAnnotationResult.get("projectRootRepositoryName"),
									GitProjectHandler.SOURCE_DOCUMENT_SUBMODULES_DIRECTORY_NAME,
									getJsonLdWebAnnotationResult.get("sourceDocumentUuid")
							)
					),
					tagReferences.get(0).getTarget()
			);
			assertEquals(new Range(12, 18), tagReferences.get(0).getRange());

			assertEquals(
					new URI(
							String.format(
									"http://[YOUR-DOMAIN]/gitlab/%s/%s/%s",
									getJsonLdWebAnnotationResult.get("projectRootRepositoryName"),
									GitProjectHandler.SOURCE_DOCUMENT_SUBMODULES_DIRECTORY_NAME,
									getJsonLdWebAnnotationResult.get("sourceDocumentUuid")
							)
					),
					tagReferences.get(1).getTarget()
			);
			assertEquals(new Range(41, 47), tagReferences.get(1).getRange());
		}
	}
}
