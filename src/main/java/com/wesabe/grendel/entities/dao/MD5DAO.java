package com.wesabe.grendel.entities.dao;

import org.hibernate.Session;
import com.codahale.shore.dao.AbstractDAO;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wesabe.grendel.entities.MD5Checksum;

public class MD5DAO extends AbstractDAO<MD5Checksum>
{
    @Inject
    public MD5DAO(Provider<Session> provider)
    {
        super(provider, MD5Checksum.class);
    }
    
    public MD5Checksum getById(int id) {
        return get(id);
    }
    
    public MD5Checksum saveOrUpdate(MD5Checksum md5) {
        currentSession().saveOrUpdate(md5);
        return md5;
    }

}
