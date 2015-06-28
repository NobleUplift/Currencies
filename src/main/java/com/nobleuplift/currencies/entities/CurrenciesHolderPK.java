package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the currencies_holder database table.
 * 
 */
@Embeddable
public class CurrenciesHolderPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="parent_account_id", insertable=false, updatable=false, unique=true, nullable=false)
	private int parentAccountId;

	@Column(name="child_account_id", insertable=false, updatable=false, unique=true, nullable=false)
	private int childAccountId;

	public CurrenciesHolderPK() {
	}
	public int getParentAccountId() {
		return this.parentAccountId;
	}
	public void setParentAccountId(int parentAccountId) {
		this.parentAccountId = parentAccountId;
	}
	public int getChildAccountId() {
		return this.childAccountId;
	}
	public void setChildAccountId(int childAccountId) {
		this.childAccountId = childAccountId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof CurrenciesHolderPK)) {
			return false;
		}
		CurrenciesHolderPK castOther = (CurrenciesHolderPK)other;
		return 
			(this.parentAccountId == castOther.parentAccountId)
			&& (this.childAccountId == castOther.childAccountId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.parentAccountId;
		hash = hash * prime + this.childAccountId;
		
		return hash;
	}
}