package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

public class Currency implements Serializable {
	private static final long serialVersionUID = 1L;

	private Short id;
	private String acronym;
	private Timestamp dateCreated;
	private Timestamp dateDeleted;
	private Timestamp dateModified;
	private boolean deleted;
	private String name;
	private boolean prefix;
	private boolean globalDefault;

	private List<Account> accountDefaults;
	private List<Unit> units;

	public Currency() {
	}

	public Short getId() {
		return this.id;
	}

	public void setId(Short id) {
		this.id = id;
	}

	public String getAcronym() {
		return this.acronym;
	}

	public void setAcronym(String acronym) {
		this.acronym = acronym;
	}

	public Timestamp getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Timestamp dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Timestamp getDateDeleted() {
		return this.dateDeleted;
	}

	public void setDateDeleted(Timestamp dateDeleted) {
		this.dateDeleted = dateDeleted;
	}

	public Timestamp getDateModified() {
		return this.dateModified;
	}

	public void setDateModified(Timestamp dateModified) {
		this.dateModified = dateModified;
	}

	public boolean isDeleted() {
		return this.deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isPrefix() {
		return this.prefix;
	}

	public void setPrefix(boolean prefix) {
		this.prefix = prefix;
	}

	public boolean isGlobalDefault() {
		return globalDefault;
	}

	public void setGlobalDefault(boolean globalDefault) {
		this.globalDefault = globalDefault;
	}

	public List<Account> getAccountDefaults() {
		return this.accountDefaults;
	}

	public void setAccountDefaults(List<Account> accountDefaults) {
		this.accountDefaults = accountDefaults;
	}

	public Account addAccountDefault(Account accountDefault) {
		getAccountDefaults().add(accountDefault);
		accountDefault.setDefaultCurrency(this);
		return accountDefault;
	}

	public Account removeAccountDefault(Account accountDefault) {
		getAccountDefaults().remove(accountDefault);
		accountDefault.setDefaultCurrency(null);
		return accountDefault;
	}

	public List<Unit> getUnits() {
		return this.units;
	}

	public void setUnits(List<Unit> units) {
		this.units = units;
	}

	public Unit addUnit(Unit unit) {
		getUnits().add(unit);
		unit.setCurrency(this);
		return unit;
	}

	public Unit removeUnit(Unit unit) {
		getUnits().remove(unit);
		unit.setCurrency(null);
		return unit;
	}

	@Override
	public String toString() {
		return "Currency [id=" + id + ", acronym=" + acronym + ", name=" + name
				+ ", prefix=" + prefix + ", deleted=" + deleted + "]";
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
		Currency other = (Currency) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
