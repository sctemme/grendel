package com.wesabe.grendel.entities;

import static com.google.common.base.Objects.equal;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.wesabe.grendel.util.HashCode;

/**
 * A Grendel passphrase.
 * 
 * @author shuo
 */
@Entity
@Table(name="passphrases")
@NamedQueries({
	@NamedQuery(
		name="com.wesabe.grendel.entities.Passphrase.All",
		query="SELECT pp FROM Passphrase as pp ORDER BY pp.id"
	),
	@NamedQuery(
		name="com.wesabe.grendel.entities.Passphrase.Enabled",
		query="SELECT pp FROM Passphrase AS pp WHERE pp.enabled = true"
	),
	@NamedQuery(
        name="com.wesabe.grendel.entities.Passphrase.Active",
        query="SELECT pp FROM Passphrase AS pp WHERE pp.active = true and pp.enabled = true"
    )
})
public class Passphrase implements Serializable {
	private static final long serialVersionUID = -8240919660234501128L;

	@Id
	@Column(name="id")
	private int id;
	
	@Column(name="nickname", nullable=false)
	@Lob
	private String nickname;
	
	
	@Column(name="enabled", nullable=false)
    private boolean enabled;
	
	@Column(name="active", nullable=false)
	private boolean active;
    
	
	
	public Passphrase() {
	    
	}
	
	public Passphrase(int id, String nickname) {
		this.id = id;
		this.nickname = nickname;
	}
	
	public void setNickName(String nickname) {
	    this.nickname = nickname;
	}
	
	public boolean isEnabled() {
	    return enabled;
	}
	
	public void setEnabled(boolean enabled) {
	    this.enabled = enabled;
	}
	
	public void setActive(boolean active) {
	    this.active = active;
	}
	
	public boolean isActive() {
	    return active;
	}
	
	public String getNickName() {
	    return nickname;
	}

	/**
	 * Returns the user's id.
	 */
	public int getId() {
		return id;
	}
	

	@Override
	public int hashCode() {
		return HashCode.calculate(nickname, id, enabled);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		
		if (!(obj instanceof Passphrase)) {
			return false;
		}
		
		final Passphrase that = (Passphrase) obj;
		return equal(id, that.id) && equal(nickname, that.nickname) &&
				equal(enabled, that.enabled);
	}
	
	
	@Override
	public String toString() {
		return id + "";
	}
	
}
