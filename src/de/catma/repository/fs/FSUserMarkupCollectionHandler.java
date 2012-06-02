package de.catma.repository.fs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

import de.catma.document.ContentInfoSet;
import de.catma.document.source.ISourceDocument;
import de.catma.document.source.contenthandler.BOMFilterInputStream;
import de.catma.document.standoffmarkup.usermarkup.IUserMarkupCollection;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.serialization.UserMarkupCollectionSerializationHandler;
import de.catma.util.CloseSafe;

class FSUserMarkupCollectionHandler {

	private String repoFolderPath;
	private UserMarkupCollectionSerializationHandler userMarkupCollectionSerializationHandler;

	public FSUserMarkupCollectionHandler(
			String repoFolderPath,
			UserMarkupCollectionSerializationHandler userMarkupCollectionSerializationHandler) {
		this.repoFolderPath = repoFolderPath;
		this.userMarkupCollectionSerializationHandler = userMarkupCollectionSerializationHandler;
	}
	
	
	public IUserMarkupCollection loadUserMarkupCollection(
			UserMarkupCollectionReference userMarkupCollectionReference) throws IOException {
		String userMarkupURI = 
				FSRepository.getFileURL(
					userMarkupCollectionReference.getId(), repoFolderPath);
		
		URLConnection urlConnection = 
				new URL(userMarkupURI).openConnection();
		
		InputStream is = null;
		
		try {
			is = urlConnection.getInputStream();
			try {
				if (BOMFilterInputStream.hasBOM(
						new URI(userMarkupURI))) {
					is = new BOMFilterInputStream(
							is, Charset.forName( "UTF-8" ));
				}
			}
			catch (URISyntaxException se) {
				throw new IOException(se);
			}
			
			IUserMarkupCollection userMarkupCollection = 
					userMarkupCollectionSerializationHandler.deserialize(
							userMarkupCollectionReference.getId(), is);
			is.close();
			
			return userMarkupCollection;
		}
		finally {
			CloseSafe.close(is);
		}
		
	}	
	
	public UserMarkupCollectionReference saveUserMarkupCollection(
			IUserMarkupCollection userMarkupCollection, 
			ISourceDocument sourceDocument) throws IOException {
		
		UserMarkupCollectionReference reference = 
				new UserMarkupCollectionReference(
						userMarkupCollection.getId(), 
						new ContentInfoSet(userMarkupCollection.getName()));
	
		String url = FSRepository.getFileURL(
				userMarkupCollection.getId(), repoFolderPath);
		
		OutputStream os = null;
		try {
			os = new FileOutputStream(new File(new URI(url)));
		
			userMarkupCollectionSerializationHandler.serialize(
					userMarkupCollection, sourceDocument, os);
			CloseSafe.close(os);
		}
		catch(Exception exc) {
			CloseSafe.close(os);
			throw new IOException(exc);
		}
		
		return reference;
	}
}