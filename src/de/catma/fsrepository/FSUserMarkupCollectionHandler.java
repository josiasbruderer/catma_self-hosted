package de.catma.fsrepository;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

import de.catma.core.document.source.contenthandler.BOMFilterInputStream;
import de.catma.core.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.core.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.serialization.UserMarkupCollectionSerializationHandler;

class FSUserMarkupCollectionHandler {

	private UserMarkupCollectionSerializationHandler userMarkupCollectionSerializationHandler;

	public FSUserMarkupCollectionHandler(
			UserMarkupCollectionSerializationHandler userMarkupCollectionSerializationHandler) {
		super();
		this.userMarkupCollectionSerializationHandler = userMarkupCollectionSerializationHandler;
	}
	
	
	public UserMarkupCollection loadUserMarkupCollection(
			UserMarkupCollectionReference userMarkupCollectionReference) throws IOException {
		URLConnection urlConnection = 
				new URL(userMarkupCollectionReference.getId()).openConnection();
		
		FilterInputStream is = new BOMFilterInputStream(
				urlConnection.getInputStream(), Charset.forName( "UTF8" ));
		UserMarkupCollection userMarkupCollection = 
				userMarkupCollectionSerializationHandler.deserialize(
						userMarkupCollectionReference.getId(), is);
		userMarkupCollection.setId(userMarkupCollectionReference.getId());
		userMarkupCollection.setName(userMarkupCollectionReference.getName());
		return userMarkupCollection;
	}	
}