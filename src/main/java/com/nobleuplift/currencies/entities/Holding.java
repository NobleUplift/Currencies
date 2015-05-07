package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the currencies_holding database table.
 * 
 */
@Entity
@Table(name="currencies_holding")
@NamedQuery(name="Holding.findAll", query="SELECT h FROM Holding h")
public class Holding implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private HoldingPK id;

	@Column(nullable=false)
	private long amount;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="account_id", nullable=false, insertable=false, updatable=false)
	private Account account;

	//bi-directional many-to-one association to Unit
	@ManyToOne
	@JoinColumn(name="unit_id", nullable=false, insertable=false, updatable=false)
	private Unit unit;

	public Holding() {
	}

	public HoldingPK getId() {
		return this.id;
	}

	public void setId(HoldingPK id) {
		this.id = id;
	}

	public long getAmount() {
		return this.amount;
	}

	public void setAmount(long amount) {
		this.amount = amount;
	}

	public Account getAccount() {
		return this.account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public Unit getUnit() {
		return this.unit;
	}

	public void setUnit(Unit unit) {
		this.unit = unit;
	}

}