package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the currencies_holding database table.
 * 
 */
@Embeddable
public class HoldingPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="account_id", insertable=false, updatable=false, unique=true, nullable=false)
	private int accountId;

	@Column(name="unit_id", insertable=false, updatable=false, unique=true, nullable=false)
	private short unitId;

	public HoldingPK() {
	}
	public int getAccountId() {
		return this.accountId;
	}
	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}
	public short getUnitId() {
		return this.unitId;
	}
	public void setUnitId(short unitId) {
		this.unitId = unitId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof HoldingPK)) {
			return false;
		}
		HoldingPK castOther = (HoldingPK)other;
		return 
			(this.accountId == castOther.accountId)
			&& (this.unitId == castOther.unitId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.accountId;
		hash = hash * prime + this.unitId;
		
		return hash;
	}
}