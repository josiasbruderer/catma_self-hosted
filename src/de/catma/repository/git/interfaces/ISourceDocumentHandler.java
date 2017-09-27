package de.catma.repository.git.interfaces;

import de.catma.document.source.SourceDocument;
import de.catma.repository.git.exceptions.SourceDocumentHandlerException;

import javax.annotation.Nullable;

public interface ISourceDocumentHandler {
    void insert(byte[] originalSourceDocumentBytes, SourceDocument sourceDocument,
				@Nullable Integer projectId)
			throws SourceDocumentHandlerException;

    void remove(String sourceDocumentId);
}