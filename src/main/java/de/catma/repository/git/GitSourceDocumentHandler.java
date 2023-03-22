package de.catma.repository.git;

import com.google.common.collect.Maps;
import de.catma.document.source.*;
import de.catma.document.source.contenthandler.SourceContentHandler;
import de.catma.document.source.contenthandler.StandardContentHandler;
import de.catma.indexer.TermInfo;
import de.catma.repository.git.managers.interfaces.LocalGitRepositoryManager;
import de.catma.repository.git.serialization.SerializationHelper;
import de.catma.repository.git.serialization.model_wrappers.GitTermInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GitSourceDocumentHandler {
	private static final String HEADER_FILE_NAME = "header.json";
	private static final String UTF8_CONVERSION_FILE_EXTENSION = "txt";

	private final LocalGitRepositoryManager localGitRepositoryManager;
	private final File projectDirectory;
	private final String username;
	private final String email;

	public GitSourceDocumentHandler(
			LocalGitRepositoryManager localGitRepositoryManager,
			File projectDirectory,
			String username,
			String email
	) {
		this.localGitRepositoryManager = localGitRepositoryManager;
		this.projectDirectory = projectDirectory;
		this.username = username;
		this.email = email;
	}

	public String create(
			File documentFolder, String sourceDocumentId,
			InputStream originalSourceDocumentStream, String originalSourceDocumentFileName,
			InputStream convertedSourceDocumentStream, String convertedSourceDocumentFileName,
			Map<String, List<TermInfo>> terms,
			String tokenizedSourceDocumentFileName,
			SourceDocumentInfo sourceDocumentInfo
	) throws IOException {

		documentFolder.mkdirs();
		
		// write files into the local repo
		File targetOriginalSourceDocumentFile = 
				new File(documentFolder, originalSourceDocumentFileName);
		File targetConvertedSourceDocumentFile = 
				new File(documentFolder, convertedSourceDocumentFileName);
		File targetTokenizedSourceDocumentFile = 
				new File(documentFolder, tokenizedSourceDocumentFileName);

		// add files to git
		this.localGitRepositoryManager.add(
				targetOriginalSourceDocumentFile, 
				IOUtils.toByteArray(originalSourceDocumentStream));
		byte[] convertedSourceDocumentBytes = 
				IOUtils.toByteArray(convertedSourceDocumentStream);
		this.localGitRepositoryManager.add(
				targetConvertedSourceDocumentFile, convertedSourceDocumentBytes);

		Map<String, List<GitTermInfo>> gitTermInfos = Maps.newHashMap();
		terms.forEach((term, termInfos) -> gitTermInfos.put(
			term,
			termInfos.stream().map(GitTermInfo::new).collect(Collectors.toList())
		));
		this.localGitRepositoryManager.add(
			targetTokenizedSourceDocumentFile, 
			new SerializationHelper<Map<String, List<GitTermInfo>>>()
				.serialize(gitTermInfos)
				.getBytes(StandardCharsets.UTF_8)
		);

		// write header.json into the local repo
		File targetHeaderFile = new File(documentFolder, HEADER_FILE_NAME);

		sourceDocumentInfo.getTechInfoSet().setCharset(StandardCharsets.UTF_8);
		sourceDocumentInfo.getTechInfoSet().setFileType(FileType.TEXT);
		sourceDocumentInfo.getTechInfoSet().setFileOSType(
				FileOSType.getFileOSType(
						new String(convertedSourceDocumentBytes, StandardCharsets.UTF_8)));
		sourceDocumentInfo.getTechInfoSet().setMimeType("text/plain");
		// the source document file URI in the supplied SourceDocumentInfo initially points to a temp file (same as originalSourceDocumentStream)
		// we update it here to point to the converted file within the current user's local copy of the repo (not persisted)
		sourceDocumentInfo.getTechInfoSet().setURI(targetConvertedSourceDocumentFile.toURI());
		String serializedSourceDocumentInfo = 
				new SerializationHelper<SourceDocumentInfo>().serialize(sourceDocumentInfo);

		this.localGitRepositoryManager.add(
				targetHeaderFile, 
				serializedSourceDocumentInfo.getBytes(StandardCharsets.UTF_8));

		// commit newly added files
		String commitMessage = String.format(
				"Created document \"%s\" with ID %s",
				sourceDocumentInfo.getContentInfoSet().getTitle(), 
				sourceDocumentId
		);
		
		String revisionHash = this.localGitRepositoryManager.commit(
				commitMessage, this.username, this.email, false
		);

		return revisionHash;
	}

	public SourceDocument open(String documentId) throws IOException {
		String documentSubDir = String.format(
				"%s/%s", GitProjectHandler.DOCUMENTS_DIRECTORY_NAME, documentId
		);
		File headerFile = Paths.get(
				this.projectDirectory.getAbsolutePath(),
				documentSubDir,
				HEADER_FILE_NAME
		).toFile();
		

		String serializedHeaderFile = 
				FileUtils.readFileToString(headerFile, StandardCharsets.UTF_8);
		SourceDocumentInfo sourceDocumentInfo = 
				new SerializationHelper<SourceDocumentInfo>().deserialize(
						serializedHeaderFile, SourceDocumentInfo.class);

		// set URI as it's not persisted (also see create)
		File convertedSourceDocumentFile = Paths.get(
				projectDirectory.getAbsolutePath(),
				documentSubDir,
				documentId + "." + UTF8_CONVERSION_FILE_EXTENSION
		).toFile();
		sourceDocumentInfo.getTechInfoSet().setURI(convertedSourceDocumentFile.toURI());

		SourceDocumentHandler sourceDocumentHandler = new SourceDocumentHandler();
		SourceContentHandler sourceContentHandler = new StandardContentHandler();
		sourceContentHandler.setSourceDocumentInfo(sourceDocumentInfo);
		SourceDocument sourceDocument = 
				sourceDocumentHandler.loadSourceDocument(
						documentId, sourceContentHandler);

		return sourceDocument;
	}

	public String update(SourceDocumentReference sourceDocument) throws IOException {
		String documentSubDir = String.format(
				"%s/%s", 
				GitProjectHandler.DOCUMENTS_DIRECTORY_NAME, 
				sourceDocument.getUuid()
		);
		File headerFile = Paths.get(
				this.projectDirectory.getAbsolutePath(),
				documentSubDir,
				HEADER_FILE_NAME
		).toFile();

		SourceDocumentInfo newSourceDocumentInfo = 
				sourceDocument.getSourceDocumentInfo();

		String serializedHeader = 
				new SerializationHelper<SourceDocumentInfo>().serialize(newSourceDocumentInfo);

		return this.localGitRepositoryManager.addAndCommit(
				headerFile,
				serializedHeader.getBytes(StandardCharsets.UTF_8),
				String.format(
						"Updated metadata of document \"%s\" with ID %s", 
						newSourceDocumentInfo.getContentInfoSet().getTitle(), 
						sourceDocument.getUuid()
				),
				this.username,
				this.email
		);
	}
	
	public String removeDocument(SourceDocumentReference document) throws IOException {
		String documentSubDir = String.format(
				"%s/%s", 
				GitProjectHandler.DOCUMENTS_DIRECTORY_NAME, 
				document.getUuid()
		);

		File documentFolderAbsolutePath = Paths.get(
				this.projectDirectory.getAbsolutePath(),
				documentSubDir
		).toFile();
		
		String projectRevision = this.localGitRepositoryManager.removeAndCommit(
				documentFolderAbsolutePath, 
				false, // do not delete the parent folder
				String.format(
					"Deleted document \"%s\" with ID %s",
					document.getSourceDocumentInfo().getContentInfoSet().getTitle(),
					document.getUuid()),
				this.username,
				this.email);
			
		return projectRevision;
	}
}
