package com.wesabe.grendel.entities.dao;

import java.util.List;
import org.hibernate.Session;
import com.codahale.shore.dao.AbstractDAO;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wesabe.grendel.entities.Passphrase;

public class PassphraseDAO extends AbstractDAO<Passphrase> {
	
	@Inject
	public PassphraseDAO(Provider<Session> provider) {
		super(provider, Passphrase.class);
	}
	
	public Passphrase saveOrUpdate(Passphrase pp) {
		currentSession().saveOrUpdate(pp);
		return pp;
	}
	
	public List<Passphrase> findEnabledPassphrases() {
	    return list(namedQuery("com.wesabe.grendel.entities.Passphrase.Enabled"));
	}
	
	public List<Passphrase> findAll() {
	    return list(namedQuery("com.wesabe.grendel.entities.Passphrase.All"));
	}
	
	public Passphrase findActivePassphrase() {
	    return uniqueResult(namedQuery("com.wesabe.grendel.entities.Passphrase.Active"));
	}


	
}
