package de.catma.repository.db;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import de.catma.backgroundservice.DefaultProgressCallable;
import de.catma.backgroundservice.ExecutionListener;
import de.catma.document.ContentInfoSet;
import de.catma.document.Range;
import de.catma.document.repository.Repository.RepositoryChangeEvent;
import de.catma.document.source.SourceDocument;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollection;
import de.catma.document.standoffmarkup.usermarkup.TagReference;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.repository.db.model.DBProperty;
import de.catma.repository.db.model.DBPropertyDefinition;
import de.catma.repository.db.model.DBPropertyValue;
import de.catma.repository.db.model.DBSourceDocument;
import de.catma.repository.db.model.DBTagDefinition;
import de.catma.repository.db.model.DBTagInstance;
import de.catma.repository.db.model.DBTagLibrary;
import de.catma.repository.db.model.DBTagReference;
import de.catma.repository.db.model.DBUserMarkupCollection;
import de.catma.repository.db.model.DBUserUserMarkupCollection;
import de.catma.serialization.UserMarkupCollectionSerializationHandler;
import de.catma.tag.TagLibrary;
import de.catma.tag.Property;
import de.catma.tag.PropertyDefinition;
import de.catma.tag.TagDefinition;
import de.catma.tag.TagInstance;
import de.catma.tag.TagLibraryReference;
import de.catma.tag.TagsetDefinition;
import de.catma.util.CloseSafe;
import de.catma.util.IDGenerator;
import de.catma.util.Pair;

class DBUserMarkupCollectionHandler {
	
	private DBRepository dbRepository;
	private IDGenerator idGenerator;
	
	public DBUserMarkupCollectionHandler(DBRepository dbRepository) {
		this.dbRepository = dbRepository;
		this.idGenerator = new IDGenerator();
	}

	void createUserMarkupCollection(String name,
			SourceDocument sourceDocument) throws IOException {
		
		Session session = dbRepository.getSessionFactory().openSession();
		DBSourceDocument dbSourceDocument = 
				dbRepository.getDbSourceDocumentHandler().getDbSourceDocument(
						session, sourceDocument.getID());
		DBUserMarkupCollection dbUserMarkupCollection = 
				new DBUserMarkupCollection(
						dbSourceDocument.getSourceDocumentId(), 
						name);
		DBUserUserMarkupCollection dbUserUserMarkupCollection =
				new DBUserUserMarkupCollection(
						dbRepository.getCurrentUser(), dbUserMarkupCollection);
		
		dbUserMarkupCollection.getDbUserUserMarkupCollections().add(
				dbUserUserMarkupCollection);
		try {
			session.beginTransaction();
			
			session.save(dbUserMarkupCollection);
			session.save(dbUserUserMarkupCollection);
			
			session.getTransaction().commit();
			
			UserMarkupCollectionReference reference = 
					new UserMarkupCollectionReference(
							dbUserMarkupCollection.getId(), 
							new ContentInfoSet(name));
			
			dbRepository.getPropertyChangeSupport().firePropertyChange(
					RepositoryChangeEvent.userMarkupCollectionChanged.name(),
					null, new Pair<UserMarkupCollectionReference, SourceDocument>(
							reference,sourceDocument));

		}
		catch (Exception e) {
			try {
				if (session.getTransaction().isActive()) {
					session.getTransaction().rollback();
				}
			}
			catch(Exception notOfInterest){}
			throw new IOException(e);
		}
		finally {
			CloseSafe.close(new ClosableSession(session));
		}
		
	}

	void importUserMarkupCollection(InputStream inputStream,
			final SourceDocument sourceDocument) throws IOException {
		dbRepository.setTagManagerListenersEnabled(false);

		UserMarkupCollectionSerializationHandler userMarkupCollectionSerializationHandler = 
				dbRepository.getSerializationHandlerFactory().getUserMarkupCollectionSerializationHandler();
		
		final UserMarkupCollection umc =
				userMarkupCollectionSerializationHandler.deserialize(null, inputStream);

		dbRepository.getDbTagLibraryHandler().importTagLibrary(
				umc.getTagLibrary(), new ExecutionListener<Session>() {
					
					public void error(Throwable t) {}
					
					public void done(Session result) {
						importUserMarkupCollection(
								result, umc, sourceDocument);
					}
				}, 
				false);
	}

	private void importUserMarkupCollection(
			final Session session, final UserMarkupCollection umc,
			final SourceDocument sourceDocument) {
		
		dbRepository.getBackgroundServiceProvider().submit(
				new DefaultProgressCallable<DBUserMarkupCollection>() {
			public DBUserMarkupCollection call() throws Exception {
				
				DBSourceDocument dbSourceDocument = 
						dbRepository.getDbSourceDocumentHandler().getDbSourceDocument(
								session, sourceDocument.getID());
				
				DBUserMarkupCollection dbUserMarkupCollection =
					new DBUserMarkupCollection(
						dbSourceDocument.getSourceDocumentId(), 
						umc,
						Integer.valueOf(umc.getTagLibrary().getId()));
				
				addDbTagReferences(session, dbUserMarkupCollection, umc);

				dbUserMarkupCollection.getDbUserUserMarkupCollections().add(
					new DBUserUserMarkupCollection(
						dbRepository.getCurrentUser(), dbUserMarkupCollection));
				
				try {
					session.save(dbUserMarkupCollection);
//TODO: indexing
//					dbRepository.getIndexer().index(
//							dbUserMarkupCollection.getTagReferences(), 
//							sourceDocument.getID(),
//							dbUserMarkupCollection.getId(),
//							umc.getTagLibrary());

					session.getTransaction().commit();

					return dbUserMarkupCollection;
				}
				catch (Exception e) {
					try {
						if (session.getTransaction().isActive()) {
							session.getTransaction().rollback();
						}
					}
					catch(Exception notOfInterest){}
					throw new IOException(e);
				}
				finally {
					CloseSafe.close(new ClosableSession(session));
				}
			};
		}, 
		new ExecutionListener<DBUserMarkupCollection>() {
			public void done(DBUserMarkupCollection result) {
				umc.setId(result.getId());
				UserMarkupCollectionReference umcRef = 
						new UserMarkupCollectionReference(
								result.getId(), umc.getContentInfoSet());
				sourceDocument.addUserMarkupCollectionReference(umcRef);
				
				dbRepository.setTagManagerListenersEnabled(true);

				dbRepository.getPropertyChangeSupport().firePropertyChange(
					RepositoryChangeEvent.userMarkupCollectionChanged.name(),
					null, new Pair<UserMarkupCollectionReference, SourceDocument>(
							umcRef, sourceDocument));
			}
			public void error(Throwable t) {
				dbRepository.setTagManagerListenersEnabled(true);

				dbRepository.getPropertyChangeSupport().firePropertyChange(
						RepositoryChangeEvent.exceptionOccurred.name(),
						null, 
						t);				
			}
		});
		
	}
	
	private void addDbTagReferences(
			Session session,
			DBUserMarkupCollection dbUserMarkupCollection, 
			UserMarkupCollection umc) {
		
		HashMap<String, DBTagInstance> dbTagInstances = 
				new HashMap<String, DBTagInstance>();
		
		for (TagReference tr : umc.getTagReferences()) {
			DBTagInstance dbTagInstance = null;

			if (dbTagInstances.containsKey(tr.getTagInstanceID())) {
				dbTagInstance = dbTagInstances.get(tr.getTagInstanceID());
			}
			else {
				TagInstance ti = tr.getTagInstance();

				TagDefinition tDef = 
					umc.getTagLibrary().getTagDefinition(
						tr.getTagInstance().getTagDefinition().getUuid());

				DBTagDefinition dbTagDefinition  = 
						(DBTagDefinition) session.load(
								DBTagDefinition.class, tDef.getId());
				
				dbTagInstance = new DBTagInstance(
					idGenerator.catmaIDToUUIDBytes(tr.getTagInstanceID()),
					dbTagDefinition);
				
				dbTagInstances.put(tr.getTagInstanceID(), dbTagInstance);
				
				for (Property p : ti.getSystemProperties()) {
					DBProperty dbProperty = 
						new DBProperty(
							dbTagDefinition.getDbPropertyDefinition(
									p.getPropertyDefinition().getUuid()),
							dbTagInstance, 
							p.getPropertyValueList().getFirstValue());

					dbTagInstance.getDbProperties().add(dbProperty);
				}
				addAuthorIfAbsent(
					dbTagDefinition.getDbPropertyDefinition(
						tDef.getPropertyDefinitionByName(
							PropertyDefinition.SystemPropertyName.catma_markupauthor.name()).getUuid()), 
						dbTagInstance);
				
				for (Property p : ti.getUserDefinedProperties()) {
					DBProperty dbProperty = 
						new DBProperty(
							dbTagDefinition.getDbPropertyDefinition(
									p.getPropertyDefinition().getUuid()),
							dbTagInstance, 
							p.getPropertyValueList().getFirstValue());

					dbTagInstance.getDbProperties().add(dbProperty);
				}
				
			}
			
			DBTagReference dbTagReference = 
				new DBTagReference(
					tr.getRange().getStartPoint(), 
					tr.getRange().getEndPoint(),
					dbUserMarkupCollection, 
					dbTagInstance);
			

			dbUserMarkupCollection.getDbTagReferences().add(dbTagReference);
		}
	}

	private void addAuthorIfAbsent(
			DBPropertyDefinition authorPDef, DBTagInstance dbTagInstance) {
		if (!dbTagInstance.hasProperty(authorPDef)) {
			DBProperty dbProperty = 
				new DBProperty(
						authorPDef, 
						dbTagInstance, 
						dbRepository.getCurrentUser().getIdentifier());
			dbTagInstance.getDbProperties().add(dbProperty);
		}
	}

	void delete(
			UserMarkupCollectionReference userMarkupCollectionReference) throws IOException {
		
		Session session = dbRepository.getSessionFactory().openSession();
		try {
			session.beginTransaction();
			DBUserMarkupCollection dbUserMarkupCollection = 
				(DBUserMarkupCollection) session.get(
					DBUserMarkupCollection.class,
					Integer.valueOf(userMarkupCollectionReference.getId()));
			
			Object dbTagLibrary = 
				session.get(
						DBTagLibrary.class,
						dbUserMarkupCollection.getDbTagLibraryId());
			
			session.delete(dbUserMarkupCollection);
			session.delete(dbTagLibrary);
			
			session.getTransaction().commit();
			
			dbRepository.getPropertyChangeSupport().firePropertyChange(
					RepositoryChangeEvent.userMarkupCollectionChanged.name(),
					userMarkupCollectionReference, null);
		}
		catch (Exception e) {
			try {
				if (session.getTransaction().isActive()) {
					session.getTransaction().rollback();
				}
			}
			catch(Exception notOfInterest){}
			throw new IOException(e);
		}
		finally {
			CloseSafe.close(new ClosableSession(session));
		}
	}

	UserMarkupCollection getUserMarkupCollection(
			UserMarkupCollectionReference userMarkupCollectionReference) throws IOException {
		String localSourceDocUri = 
			dbRepository.getDbSourceDocumentHandler().getLocalUriFor(
					userMarkupCollectionReference);
		
		Session session = dbRepository.getSessionFactory().openSession();
		try {
			Query query = session.createQuery(
					"select umc from " + 
					DBUserMarkupCollection.class.getSimpleName() + " as umc " +
					" left join umc.dbTagReferences as tr " +
					" left join tr.dbTagInstance ti " +
					" left join ti.dbProperties p " +
					" left join p.dbPropertyValues " +
					" where umc.usermarkupCollectionId = " + 
					userMarkupCollectionReference.getId()
					);
			
			DBUserMarkupCollection dbUserMarkupCollection = 
					(DBUserMarkupCollection)query.list().get(0);

			
			TagLibrary tagLibrary = 
					dbRepository.getDbTagLibraryHandler().loadTagLibrayContent(
						session, 
						new TagLibraryReference(
							String.valueOf(dbUserMarkupCollection.getDbTagLibraryId()), 
							dbUserMarkupCollection.getTitle()));
			
			try {
				
				UserMarkupCollection userMarkupCollection = createUserMarkupCollection(
						dbUserMarkupCollection, localSourceDocUri, tagLibrary);
				return userMarkupCollection;
				
			} catch (URISyntaxException e) {
				throw new IOException(e);
			}
		}
		finally {
			CloseSafe.close(new ClosableSession(session));
		}
	}

	private UserMarkupCollection createUserMarkupCollection(
			DBUserMarkupCollection dbUserMarkupCollection, 
			String localSourceDocUri, TagLibrary tagLibrary) throws URISyntaxException {
		UserMarkupCollection userMarkupCollection = 
				new UserMarkupCollection(
						dbUserMarkupCollection.getId(),
						new ContentInfoSet(
								dbUserMarkupCollection.getAuthor(), 
								dbUserMarkupCollection.getDescription(), 
								dbUserMarkupCollection.getPublisher(), 
								dbUserMarkupCollection.getTitle()), 
						tagLibrary, new ArrayList<TagReference>());
		
		HashMap<DBTagInstance, TagInstance> tagInstances = 
				new HashMap<DBTagInstance, TagInstance>();
		
		for (DBTagReference dbTagReference : 
			dbUserMarkupCollection.getDbTagReferences()) {
			DBTagInstance dbTagInstance = dbTagReference.getDbTagInstance();
			TagInstance tagInstance = 
					tagInstances.get(dbTagInstance);
			
			if (tagInstance == null) {
				tagInstance = 
					new TagInstance(
						idGenerator.uuidBytesToCatmaID(
							dbTagInstance.getUuid()), 
						tagLibrary.getTagDefinition(
							idGenerator.uuidBytesToCatmaID(
								dbTagInstance.getDbTagDefinition().getUuid())));
				
				tagInstances.put(dbTagInstance, tagInstance);
			}
			
			
			TagReference tr = 
				new TagReference(
						tagInstance, 
						localSourceDocUri,
						new Range(
								dbTagReference.getCharacterStart(), 
								dbTagReference.getCharacterEnd()));
			
			userMarkupCollection.addTagReference(tr);
		}
		
		return userMarkupCollection;
	}

	void addTagReferences(UserMarkupCollection userMarkupCollection,
			Collection<TagReference> tagReferences) throws IOException {
		Session session = dbRepository.getSessionFactory().openSession();
		try {
			Set<TagInstance> incomingTagInstances = 
					new HashSet<TagInstance>();
			
			for (TagReference tr : tagReferences) {
				incomingTagInstances.add(tr.getTagInstance());
			}

			session.beginTransaction();
			DBUserMarkupCollection dbUserMarkupCollection = 
					(DBUserMarkupCollection) session.load(
							DBUserMarkupCollection.class, 
							Integer.valueOf(userMarkupCollection.getId()));
			
			for (TagInstance ti : incomingTagInstances) {
				addTagInstance(session, ti, userMarkupCollection, dbUserMarkupCollection);
			}
			
			session.getTransaction().commit();
		}
		catch (Exception e) {
			try {
				if (session.getTransaction().isActive()) {
					session.getTransaction().rollback();
				}
			}
			catch(Exception notOfInterest){}
			throw new IOException(e);
		}
		finally {
			CloseSafe.close(new ClosableSession(session));
		}
	}
	
	private void updateUserMarkupCollection(
			Session session, UserMarkupCollection userMarkupCollection) {

		DBUserMarkupCollection dbUserMarkupCollection =
				(DBUserMarkupCollection) session.load(
						DBUserMarkupCollection.class, 
						Integer.valueOf(userMarkupCollection.getId()));
		
		Map<String, DBTagInstance> persistentTagInstances = 
				new HashMap<String,DBTagInstance>();
		
		for (DBTagReference dbTr : dbUserMarkupCollection.getDbTagReferences()) {
			persistentTagInstances.put(
				idGenerator.uuidBytesToCatmaID(dbTr.getDbTagInstance().getUuid()),
				dbTr.getDbTagInstance());
		}
		
		Map<String, TagInstance> incomingTagInstances = 
				new HashMap<String, TagInstance>();
		
		for (TagReference tr : userMarkupCollection.getTagReferences()) {
			incomingTagInstances.put(tr.getTagInstanceID(), tr.getTagInstance());
		}
		
		removeTagInstances(session, persistentTagInstances, incomingTagInstances);
		
		updateTagInstances(session, persistentTagInstances, 
				dbUserMarkupCollection, incomingTagInstances, 
				userMarkupCollection);
		
		session.saveOrUpdate(dbUserMarkupCollection);
	}

	private void removeTagInstances(
		Session session, Map<String, DBTagInstance> persistentTagInstances, 
		Map<String, TagInstance> incomingTagInstances) {
		
		Iterator<Map.Entry<String, DBTagInstance>> iterator = 
				persistentTagInstances.entrySet().iterator();
		while (iterator.hasNext()) {
			DBTagInstance dbTagInstance = iterator.next().getValue();
			if (!incomingTagInstances.containsKey(
				idGenerator.uuidBytesToCatmaID(dbTagInstance.getUuid()))) {
				session.delete(dbTagInstance);
				iterator.remove();
			}
		}
	}

	private void updateTagInstances(
			Session session,  
			Map<String, DBTagInstance> persistentTagInstances,
			DBUserMarkupCollection dbUserMarkupCollection, 
			Map<String, TagInstance> incomingTagInstances, 
			UserMarkupCollection userMarkupCollection) {
		
		for (TagInstance ti : incomingTagInstances.values()) {
			if (persistentTagInstances.containsKey(ti.getUuid())) {
				DBTagInstance dbTagInstance = 
						persistentTagInstances.get(ti.getUuid());
				updateTagInstance(dbTagInstance,dbUserMarkupCollection, ti, 
						userMarkupCollection.getTagReferences(ti.getUuid()));
			}
			else {
				addTagInstance(
					session, ti, userMarkupCollection, 
					dbUserMarkupCollection);
			}
		}
	}

	private DBTagInstance addTagInstance(
			Session session, TagInstance ti, 
			UserMarkupCollection userMarkupCollection, 
			DBUserMarkupCollection dbUserMarkupCollection) {
		
		DBTagDefinition dbTagDefinition = 
			(DBTagDefinition) session.load(
					DBTagDefinition.class, 
					ti.getTagDefinition().getId());
		
		DBTagInstance dbTagInstance = 
			new DBTagInstance(
				idGenerator.catmaIDToUUIDBytes(ti.getUuid()),
				dbTagDefinition);
		
		for (Property prop : ti.getSystemProperties()) {
			DBPropertyDefinition dbPropDef =
					(DBPropertyDefinition) session.load(
							DBPropertyDefinition.class,
							prop.getPropertyDefinition().getId());
						
			DBProperty sysProp = 
				new DBProperty(
					dbPropDef, dbTagInstance,
					prop.getPropertyValueList().getFirstValue());
			dbTagInstance.getDbProperties().add(sysProp);
		}
		
		for (Property prop : ti.getUserDefinedProperties()) {
			DBPropertyDefinition dbPropDef =
					(DBPropertyDefinition) session.load(
							DBPropertyDefinition.class,
							prop.getPropertyDefinition().getId());
			DBProperty userProp = 
					new DBProperty(
							dbPropDef, dbTagInstance);
			for (String value : prop.getPropertyValueList().getValues()) {
				userProp.getDbPropertyValues().add(
						new DBPropertyValue(userProp, value));
			}
			dbTagInstance.getDbProperties().add(userProp);
		}
		
		session.save(dbTagInstance);
		
		for (TagReference tr : userMarkupCollection.getTagReferences(ti.getUuid())) {
			DBTagReference dbTagReference = 
				new DBTagReference(
						tr.getRange().getStartPoint(), 
						tr.getRange().getEndPoint(), 
						dbUserMarkupCollection, dbTagInstance);
			session.save(dbTagReference);
		}
		return dbTagInstance;
	}


	private void updateTagInstance(DBTagInstance dbTagInstance, 
			DBUserMarkupCollection dbUserMarkupCollection, 
			TagInstance ti,
			Set<TagReference> incomingTagReferences) {
		
		Set<Range> incomingRanges = new HashSet<Range>();
		
		for (TagReference tr : incomingTagReferences) {
			incomingRanges.add(tr.getRange());
		}
		
		Map<Range, DBTagReference> dbTagRefsByRange = 
				new HashMap<Range, DBTagReference>();
		
		Iterator<DBTagReference> iterator = 
				dbTagInstance.getDbTagReferences().iterator();
		while (iterator.hasNext()) {
			DBTagReference curDbTagRef = iterator.next();
			Range curRange = 
					new Range(
						curDbTagRef.getCharacterStart(), 
						curDbTagRef.getCharacterEnd());
			if (!incomingRanges.contains(curRange)) {
				iterator.remove();
			}
			else {
				dbTagRefsByRange.put(curRange, curDbTagRef);
			}
		}
		
		for (TagReference tr : incomingTagReferences) {
			if (!dbTagRefsByRange.containsKey(tr.getRange())) {
				DBTagReference dbTagReference = 
						new DBTagReference(
							tr.getRange().getStartPoint(), 
							tr.getRange().getEndPoint(), 
							dbUserMarkupCollection, dbTagInstance);
				dbUserMarkupCollection.getDbTagReferences().add(dbTagReference);
			}
		}
		
		for (DBProperty dbProperty : dbTagInstance.getDbProperties()) {
			Property property = 
				ti.getProperty(
					idGenerator.uuidBytesToCatmaID(
							dbProperty.getDbPropertyDefinition().getUuid()));
			Iterator<DBPropertyValue> dbPropertyValIterator = 
					dbProperty.getDbPropertyValues().iterator();
			while (dbPropertyValIterator.hasNext()) {
				DBPropertyValue curValue = dbPropertyValIterator.next();
				if (!property.getPropertyValueList().getValues().contains(curValue.getValue())) {
					dbPropertyValIterator.remove();
				}
			}
			
			for (String value : property.getPropertyValueList().getValues()) {
				if (!dbProperty.hasPropertyValue(value)) {
					dbProperty.getDbPropertyValues().add(
							new DBPropertyValue(dbProperty, value));
				}
			}
 		}
	}

	void updateTagsetDefinitionInUserMarkupCollections(
			List<UserMarkupCollection> userMarkupCollections,
			TagsetDefinition tagsetDefinition) throws IOException {
		/**
		 * siehe dann auch todos in MarkupPanel!
		 */
		
		Session session = dbRepository.getSessionFactory().openSession();
		try {
			session.beginTransaction();
			for (UserMarkupCollection userMarkupCollection : 
				userMarkupCollections) {
				DBTagLibraryHandler dbTagLibraryHandler = 
						dbRepository.getDbTagLibraryHandler();
				TagLibrary tagLibrary = 
						userMarkupCollection.getTagLibrary();
				
				dbTagLibraryHandler.updateTagsetDefinition(
					session, tagLibrary,
					tagLibrary.getTagsetDefinition(tagsetDefinition.getUuid()));

				updateUserMarkupCollection(session, userMarkupCollection);
			}
			session.getTransaction().commit();
		}
		catch (Exception e) {
			try {
				if (session.getTransaction().isActive()) {
					session.getTransaction().rollback();
				}
			}
			catch(Exception notOfInterest){}
			throw new IOException(e);
		}
		finally {
			CloseSafe.close(new ClosableSession(session));
		}
	}

	void removeTagReferences(
			Collection<TagReference> tagReferences) throws IOException {

		Session session = dbRepository.getSessionFactory().openSession();
		try {
			Set<TagInstance> incomingTagInstances = 
					new HashSet<TagInstance>();
			
			for (TagReference tr : tagReferences) {
				incomingTagInstances.add(tr.getTagInstance());
			}
			
			session.beginTransaction();
			for (TagInstance ti : incomingTagInstances) {
				Criteria criteria = session.createCriteria(
						DBTagInstance.class).add(
							Restrictions.eq(
								"uuid", idGenerator.catmaIDToUUIDBytes(ti.getUuid())));
				@SuppressWarnings("unchecked")
				List<DBTagInstance> dbTagInstances = criteria.list();
				session.delete(dbTagInstances.get(0));
			}
			session.getTransaction().commit();
		}
		catch (Exception e) {
			try {
				if (session.getTransaction().isActive()) {
					session.getTransaction().rollback();
				}
			}
			catch(Exception notOfInterest){}
			throw new IOException(e);
		}
		finally {
			CloseSafe.close(new ClosableSession(session));
		}
	}

}
