package com.wesabe.grendel.entities;

import static com.google.common.base.Objects.equal;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.google.common.collect.Sets;
import com.wesabe.grendel.GrendelRunner.PassphraseHolder;
import com.wesabe.grendel.openpgp.CryptographicException;
import com.wesabe.grendel.openpgp.KeySet;
import com.wesabe.grendel.openpgp.UnlockedKeySet;
import com.wesabe.grendel.util.CipherUtil;
import com.wesabe.grendel.util.HashCode;

/**
 * A Grendel user.
 * 
 * @author coda
 */
@Entity
@Table(name="users")
@NamedQueries({
	@NamedQuery(
		name="com.wesabe.grendel.entities.User.Exists",
		query="SELECT u.id FROM User AS u WHERE u.id = :id"
	),
	@NamedQuery(
		name="com.wesabe.grendel.entities.User.All",
		query="SELECT u FROM User AS u ORDER BY u.id"
	),
	
	@NamedQuery(name ="com.wesabe.grendel.entities.User.unconvertedUserCount",
	        query = "SELECT COUNT(*) FROM User AS u WHERE u.pp_id != :enabled_pp"),
    
	@NamedQuery(name ="com.wesabe.grendel.entities.User.selectUserRange",
	        query ="SELECT u FROM User AS u WHERE u.pp_id != :enabled_pp")

})
public class User implements Serializable {
	private static final long serialVersionUID = -8270919660085011028L;

	@Id
	@Column(name="id")
	private String id;
	
	@Column(name="keyset", nullable=false)
	@Lob
	private byte[] encodedKeySet;
	
	
	@Column(name="pp_id", nullable=false)
    private int pp_id;
    
	
	
	@Transient
	private KeySet keySet = null;
	
	@Column(name="created_at", nullable=false)
	@Type(type="org.joda.time.contrib.hibernate.PersistentDateTime")
	private DateTime createdAt;
	
	@Column(name="modified_at", nullable=false)
	@Type(type="org.joda.time.contrib.hibernate.PersistentDateTime")
	private DateTime modifiedAt;
	
	@Column(name="pp_modified_at")
    @Type(type="org.joda.time.contrib.hibernate.PersistentDateTime")
    private DateTime ppLastModifiedAt;
	
	// FIXME coda@wesabe.com -- Dec 27, 2009: User#documents double-loads document primary keys.
	// This may be a bug in Hibernate, but the SQL this generates produces
	// queries which look like this:
	//    documents0_.owner_id as owner6_1_,
	//    documents0_.name as name1_,
	//    documents0_.name as name1_0_,
	//    documents0_.owner_id as owner6_1_0_,
	//    documents0_.body as body1_0_,
	//    documents0_.content_type as content3_1_0_,
	//    documents0_.created_at as created4_1_0_,
	//    documents0_.modified_at as modified5_1_0_
	//
	// I've tried just about every approach to composite keys in Hibernate that
	// I found, and none of them changed this behavior.
	@OneToMany(mappedBy="owner", fetch=FetchType.LAZY, cascade={CascadeType.ALL})
	@OnDelete(action=OnDeleteAction.CASCADE)
	private Set<Document> documents = Sets.newHashSet();
	
	@ManyToMany(fetch=FetchType.LAZY, cascade={CascadeType.ALL})
	@ForeignKey(
		name="FK_LINK_TO_USER",
		inverseName="FK_LINK_TO_DOCUMENT"
	)
	@JoinTable(
		name="links",
		joinColumns={
			@JoinColumn(name="user_id", nullable=false, referencedColumnName="id")
		},
		inverseJoinColumns={
			@JoinColumn(name="document_name", nullable=false, referencedColumnName="name"),
			@JoinColumn(name="document_owner_id", nullable=false, referencedColumnName="owner_id")
		}
	)
	// FIXME coda@wesabe.com -- Dec 31, 2009: Fix links's ON CASCADE actions.
	// The foreign-key constraint FK_LINK_TO_USER and FK_USER_TO_LINK don't work
	// with this @OnDelete annotation or the one on Document#linkedUsers, which
	// means there's a potential consistency problem. The code *should* take
	// care of that, but it's still a worry. Apparently this is a long-standing
	// bug/deficiency with Hibernate's @OnDelete:
	// http://opensource.atlassian.com/projects/hibernate/browse/HHH-4404
	// @OnDelete(action=OnDeleteAction.CASCADE)
	private Set<Document> linkedDocuments = Sets.newHashSet();
	
	@Version
	@Column(name="version", nullable=false)
	private long version = 0;
	
	@Deprecated
	public User() {
		// blank constructor to be used by Hibernate
	}
	
	/**
	 * Creates a new Grendel user with a given {@link KeySet}.
	 * 
	 * @param keySet the {@link KeySet} belonging to the user
	 */
	public User(KeySet keySet) {
		setKeySet(keySet);
		this.createdAt = new DateTime(DateTimeZone.UTC);
		this.modifiedAt = new DateTime(DateTimeZone.UTC);
	}
	
	/**
	 * Returns the user's id.
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * Returns the user's {@link KeySet}.
	 */
	public KeySet getKeySet() {
		if (keySet == null) {
			try {
				this.keySet = KeySet.load(encodedKeySet);
			} catch (CryptographicException e) {
				throw new RuntimeException(e);
			}
		}
		return keySet;
	}
	
	public UnlockedKeySet getUnlockedKeySet(String password) throws CryptographicException {
	    char[] encodedPassword = CipherUtil.xor(password, new String(PassphraseHolder.getPassphrase(getPPId())));
        return  getKeySet().unlock(encodedPassword);
	}
	
	/**
	 * Replaces the user's {@link KeySet} with another.
	 * 
	 * @param keySet a new {@link KeySet}
	 */
	public void setKeySet(KeySet keySet) {
		this.keySet = keySet;
		this.id = keySet.getUserID();
		this.encodedKeySet = keySet.getEncoded();
	}
	
	public DateTime getPpLastModifiedAt() {
	    return toUTC(ppLastModifiedAt);
	}
	
	public void setPpLastModifiedAt(DateTime ppLastModifiedAt) {
	    this.ppLastModifiedAt = toUTC(ppLastModifiedAt);
	}
	
	/**
	 * Returns a UTC timestamp of when this user was created.
	 */
	public DateTime getCreatedAt() {
		return toUTC(createdAt);
	}
	
	/**
	 * Sets a UTC timestamp of when this user was created.
	 */
	public void setCreatedAt(DateTime createdAt) {
		this.createdAt = toUTC(createdAt);
	}
	
	/**
	 * Returns a UTC timestamp of when this user was last modified.
	 */
	public DateTime getModifiedAt() {
		return toUTC(modifiedAt);
	}
	
	/**
	 * Sets a UTC timestamp of when this user was last modified.
	 */
	public void setModifiedAt(DateTime modifiedAt) {
		this.modifiedAt = toUTC(modifiedAt);
	}
	
	/**
	 * Returns a set of the user's {@link Document}s.
	 */
	public Set<Document> getDocuments() {
		return documents;
	}
	
	/**
	 * Returns a set of the user's linked {@link Document}s.
	 */
	public Set<Document> getLinkedDocuments() {
		return linkedDocuments;
	}
	
	private DateTime toUTC(DateTime dateTime) {
		return dateTime.toDateTime(DateTimeZone.UTC);
	}

	@Override
	public int hashCode() {
		return HashCode.calculate(createdAt, encodedKeySet, id, modifiedAt);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		
		if (!(obj instanceof User)) {
			return false;
		}
		
		final User that = (User) obj;
		return equal(id, that.id) && equal(encodedKeySet, that.encodedKeySet) &&
				equal(createdAt, that.createdAt) && equal(modifiedAt, that.modifiedAt) &&
				equal(encodedKeySet, that.encodedKeySet);
	}
	
	
	public int getPPId() {
	    return pp_id;
	}
	
	public void setPPId(int id) {
	    this.pp_id = id;
	}
	
	
	@Override
	public String toString() {
		return id;
	}
	
	/**
	 * Returns an opaque string indicating the {@link User}'s name and
	 * version.
	 */
	public String getEtag() {
		return new StringBuilder("user-").append(id).append('-').append(version).toString();
	}
}
