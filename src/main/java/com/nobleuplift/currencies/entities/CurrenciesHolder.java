package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the currencies_holder database table.
 * 
 */
@Entity
@Table(name="currencies_holder")
@NamedQuery(name="CurrenciesHolder.findAll", query="SELECT c FROM CurrenciesHolder c")
public class CurrenciesHolder implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private CurrenciesHolderPK id;

	@Column(nullable=false)
	private int length;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="child_account_id", nullable=false, insertable=false, updatable=false)
	private Account childAccount;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="parent_account_id", nullable=false, insertable=false, updatable=false)
	private Account parentAccount;

	public CurrenciesHolder() {
	}

	public CurrenciesHolderPK getId() {
		return this.id;
	}

	public void setId(CurrenciesHolderPK id) {
		this.id = id;
	}

	public int getLength() {
		return this.length;
	}

	public void setLength(int length) {
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

}