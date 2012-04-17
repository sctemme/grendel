package com.wesabe.grendel.resources;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wesabe.grendel.GrendelRunner.PassphraseHolder;
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
@Path("/users/{pp}/reset")
@Produces(MediaType.APPLICATION_JSON)
public class PassphraseBulkResetResource {
	final UserDAO userDAO;
	final PassphraseDAO ppDAO;
	final Provider<SecureRandom> randomProvider;
	private final KeySetGenerator generator;
	
	@Inject
	public PassphraseBulkResetResource(UserDAO userDAO, PassphraseDAO ppDAO, Provider<SecureRandom> randomProvider, KeySetGenerator generator) {
		this.userDAO = userDAO;
		this.randomProvider = randomProvider;
		this.generator = generator;
		this.ppDAO = ppDAO;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
    @POST
    @Transactional
	public Response reset(@PathParam("pp") String password) throws CryptographicException {
	    
	    Passphrase activedPP = ppDAO.findActivePassphrase();
	    List<User> users = userDAO.findUnconvertedUser(activedPP.getId());
	    for(User u : users) {
	         reencrypt(u, activedPP, password);
	    }
	       
	    HashMap map = new HashMap();
	    map.put("converted", users.size());
	    return Response.ok(map).build();
	}
	
	private void reencrypt(User user, Passphrase enabledPP, String password) throws CryptographicException{
	    UnlockedKeySet oldunlockedKeySet = user.getUnlockedKeySet(password);
	    Set<Document> documents = user.getDocuments();
	    user.setPPId(enabledPP.getId());
	    user.setPpLastModifiedAt(new DateTime(DateTimeZone.UTC));
	    char[] newpp = CipherUtil.xor(password.toCharArray(), PassphraseHolder.getPassphrase(enabledPP.getId()));
	    KeySet newKeyset = generator.generate(user.getId(),newpp);
	    UnlockedKeySet newunlockedKeySet = newKeyset.unlock(newpp);
	    for(Document doc : documents) {
	           byte[] body = doc.decryptBody(oldunlockedKeySet);
	           doc.encryptAndSetBody(newunlockedKeySet, randomProvider.get(), body);
	           doc.setModifiedAt(new DateTime(DateTimeZone.UTC));
	    }
	    user.setKeySet(newKeyset);
	    userDAO.saveOrUpdate(user);
	}
	
}
