package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the currencies_holder database table.
 * 
 */
@Embeddable
public class HolderPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="parent_account_id", insertable=false, updatable=false, unique=true, nullable=false)
	private Integer parentAccountId;

	@Column(name="child_account_id", insertable=false, updatable=false, unique=true, nullable=false)
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

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof HolderPK)) {
			return false;
		}
		HolderPK castOther = (HolderPK)other;
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