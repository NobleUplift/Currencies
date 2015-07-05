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
	
	@Override
	public String toString() {
		return "Holding [id=" + id + ", amount=" + amount + ", account="
				+ account + ", unit=" + unit + "]";
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
		Holding other = (Holding) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
