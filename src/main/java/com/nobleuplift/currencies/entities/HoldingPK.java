package com.nobleuplift.currencies.entities;

import java.io.Serializable;

public class HoldingPK implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer accountId;
	private Short unitId;

	public HoldingPK() { }

	public Integer getAccountId() {
		return this.accountId;
	}

	public void setAccountId(Integer accountId) {
		this.accountId = accountId;
	}

	public Short getUnitId() {
		return this.unitId;
	}

	public void setUnitId(Short unitId) {
		this.unitId = unitId;
	}

	@Override
	public String toString() {
		return "HoldingPK [accountId=" + accountId + ", unitId=" + unitId + "]";
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof HoldingPK)) {
			return false;
		}
		HoldingPK castOther = (HoldingPK) other;
		return
			(this.accountId == null ? castOther.accountId == null : this.accountId.equals(castOther.accountId))
			&& (this.unitId == null ? castOther.unitId == null : this.unitId.equals(castOther.unitId));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + (accountId == null ? 0 : accountId);
		hash = hash * prime + (unitId == null ? 0 : unitId);
		return hash;
	}
}
