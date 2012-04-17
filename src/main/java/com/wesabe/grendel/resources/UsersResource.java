package com.wesabe.grendel.resources;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.wesabe.grendel.GrendelRunner.PassphraseHolder;
import com.wesabe.grendel.entities.Passphrase;
import com.wesabe.grendel.entities.User;
import com.wesabe.grendel.entities.dao.PassphraseDAO;
import com.wesabe.grendel.entities.dao.UserDAO;
import com.wesabe.grendel.openpgp.CryptographicException;
import com.wesabe.grendel.openpgp.KeySet;
import com.wesabe.grendel.openpgp.KeySetGenerator;
import com.wesabe.grendel.representations.CreateUserRepresentation;
import com.wesabe.grendel.representations.UserListRepresentation;
import com.wesabe.grendel.representations.ValidationException;
import com.wesabe.grendel.util.CipherUtil;
import com.wideplay.warp.persist.Transactional;

/**
 * A resource for managing the collection of registered {@link User}s.
 * 
 * @author coda
 */
@Path("/users/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UsersResource {
	private final KeySetGenerator generator;
	private final UserDAO userDAO;
	private final PassphraseDAO ppDAO;
	
	@Inject
	public UsersResource(KeySetGenerator generator, UserDAO userDAO, PassphraseDAO ppDao) {
		this.generator = generator;
		this.userDAO = userDAO;
		this.ppDAO = ppDao;
	}
	
	/**
	 * Responds to a {@link GET} request with a list of all the registered
	 * users.
	 * 
	 * @see UserListRepresentation
	 */
	@GET
	public UserListRepresentation list(@Context UriInfo uriInfo) {
		final List<User> users = userDAO.findAll();
		return new UserListRepresentation(uriInfo, users);
	}
	
	/**
	 * Responds to a {@link POST} request by generating a new {@link KeySet},
	 * creating a new {@link User}, and returning the user's info URI.
	 * 
	 * @throws CryptographicException
	 *             if there is an error generating the {@link KeySet}
	 * @see UserResource
	 */
	@POST
	@Transactional
	public Response create(@Context UriInfo uriInfo, CreateUserRepresentation request) throws CryptographicException {
		request.validate();
		
		if (userDAO.contains(request.getId())) {
			final ValidationException e = new ValidationException();
			e.addReason("username is already taken");
			throw e;
		}
		
		Passphrase pp = ppDAO.findActivePassphrase();

		String encodedPassword = new String(CipherUtil.xor(request.getPassword(), PassphraseHolder.getPassphrase(pp.getId())));
		final KeySet keySet = generator.generate(request.getId(), encodedPassword.toCharArray());
		User u = new User(keySet);
		u.setPPId(pp.getId());
		u.setPpLastModifiedAt(new DateTime());
		final User user = userDAO.saveOrUpdate(u);
		
		request.sanitize();
		
		return Response.created(
			uriInfo.getBaseUriBuilder()
						.path(UserResource.class)
						.build(user)
		).build();
	}
}
