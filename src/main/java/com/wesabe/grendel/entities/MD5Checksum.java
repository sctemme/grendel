package com.wesabe.grendel.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name="md5checksums")
public class MD5Checksum implements Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 7622015255884350593L;
    @Id
    @Column(name="id")
    private int id;
    @Column(name="md5", nullable=false)
    @Lob
    byte[] md5;
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public byte[] getMD5() {
        return md5;
    }
    
    public void setMD5(byte[] md5) {
        this.md5 = md5;
    }
}
