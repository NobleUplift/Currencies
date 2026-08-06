package com.nobleuplift.currencies.entities;

import java.io.Serializable;

public class HolderPK implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer parentAccountId;
	private Integer childAccountId;

	public HolderPK() { }

	public Integer getParentAccountId() {
		return this.parentAccountId;
	}

	public void setParentAccountId(Integer parentAccountId) {
		this.parentAccountId = parentAccountId;
	}

	public Integer getChildAccountId() {
		return this.childAccountId;
	}

	public void setChildAccountId(Integer childAccountId) {
		this.childAccountId = childAccountId;
	}

	@Override
	public String toString() {
		return "HolderPK [parentAccountId=" + parentAccountId
				+ ", childAccountId=" + childAccountId + "]";
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof HolderPK)) {
			return false;
		}
		HolderPK castOther = (HolderPK) other;
		return
			(this.parentAccountId == null ? castOther.parentAccountId == null : this.parentAccountId.equals(castOther.parentAccountId))
			&& (this.childAccountId == null ? castOther.childAccountId == null : this.childAccountId.equals(castOther.childAccountId));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + (parentAccountId == null ? 0 : parentAccountId);
		hash = hash * prime + (childAccountId == null ? 0 : childAccountId);
		return hash;
	}
}
