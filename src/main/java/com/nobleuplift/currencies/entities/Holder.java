package com.nobleuplift.currencies.entities;

import java.io.Serializable;

import javax.persistence.*;


/**
 * The persistent class for the currencies_holder database table.
 * 
 */
@Entity
@Table(name="currencies_holder")
@NamedQuery(name="Holder.findAll", query="SELECT h FROM Holder h")
public class Holder implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private HolderPK id;

	@Column(nullable=false)
	private short length;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="child_account_id", nullable=false, insertable=false, updatable=false)
	private Account childAccount;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="parent_account_id", nullable=false, insertable=false, updatable=false)
	private Account parentAccount;

	public Holder() {
	}

	public HolderPK getId() {
		return this.id;
	}

	public void setId(HolderPK id) {
		this.id = id;
	}

	public short getLength() {
		return this.length;
	}

	public void setLength(short length) {
		this.length = length;
	}

	public Account getChildAccount() {
		return this.childAccount;
	}

	public void setChildAccount(Account childAccount) {
		this.childAccount = childAccount;
	}

	public Account getParentAccount() {
		return this.parentAccount;
	}

	public void setParentAccount(Account parentAccount) {
		this.parentAccount = parentAccount;
	}

	@Override
	public String toString() {
		return "Holder [id=" + id + ", length=" + length + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Holder other = (Holder) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}