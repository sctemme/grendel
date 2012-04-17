package com.wesabe.grendel.resources;

import java.security.SecureRandom;
import java.util.Set;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wesabe.grendel.GrendelRunner.PassphraseHolder;
import com.wesabe.grendel.auth.Credentials;
import com.wesabe.grendel.auth.Session;
import com.wesabe.grendel.entities.Document;
import com.wesabe.grendel.entities.Passphrase;
import com.wesabe.grendel.entities.User;
import com.wesabe.grendel.entities.dao.PassphraseDAO;
import com.wesabe.grendel.entities.dao.UserDAO;
import com.wesabe.grendel.openpgp.CryptographicException;
import com.wesabe.grendel.openpgp.KeySet;
import com.wesabe.grendel.openpgp.KeySetGenerator;
import com.wesabe.grendel.openpgp.UnlockedKeySet;
import com.wesabe.grendel.util.CipherUtil;
import com.wideplay.warp.persist.Transactional;

/**
 * 
 * @author shuo
 */
@Path("/user/{id}/reset")
@Produces(MediaType.APPLICATION_JSON)
public class PassphraseSingleResetResource {
	final UserDAO userDAO;
	final PassphraseDAO ppDAO;
	final Provider<SecureRandom> randomProvider;
	private final KeySetGenerator generator;
	
	@Inject
	public PassphraseSingleResetResource(UserDAO userDAO, PassphraseDAO ppDAO, Provider<SecureRandom> randomProvider, KeySetGenerator generator) {
		this.userDAO = userDAO;
		this.randomProvider = randomProvider;
		this.generator = generator;
		this.ppDAO = ppDAO;
	}
	
	@POST
	@Transactional
	public Response resetSecret(@Context UriInfo uriInfo,
		@Context Credentials credentials, @PathParam("id") String id) throws CryptographicException {
	    Passphrase activePP = ppDAO.findActivePassphrase();
	    
		final Session session = credentials.buildSession(userDAO, id);
		User user = session.getUser();
		if(user.getPPId() != activePP.getId()) {
		    UnlockedKeySet oldKey = session.getKeySet();
		    Set<Document> documents = user.getDocuments();
		    user.setPPId(activePP.getId());
		    user.setPpLastModifiedAt(new DateTime(DateTimeZone.UTC));
		    char[] newPassword = CipherUtil.xor(credentials.getPassword().toCharArray(), PassphraseHolder.getPassphrase(activePP.getId()));

		    KeySet newKeyset = generator.generate(user.getId(),newPassword);
		    UnlockedKeySet newKey = newKeyset.unlock(newPassword);
		    for(Document doc : documents) {
		        byte[] body = doc.decryptBody(oldKey);
		        doc.encryptAndSetBody(newKey, randomProvider.get(), body);
		        doc.setModifiedAt(new DateTime(DateTimeZone.UTC));
		    }
		    user.setKeySet(newKeyset);
		    userDAO.saveOrUpdate(user);
		}
		
		return Response.ok().build();
	}
}
