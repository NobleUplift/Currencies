package com.nobleuplift.currencies.entities;

import javax.persistence.Embeddable;

/**
 * Created on 2015 May 2nd at 03:36 PM.
 * 
 * @author Patrick
 */
@Embeddable
public class HoldingId {
	private long accountId;
	private short unitId;
	
	public HoldingId() {
		super();
	}
	
	public long getAccountId() {
		return accountId;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

	public short getUnitId() {
		return unitId;
	}

	public void setUnitId(short unitId) {
		this.unitId = unitId;
	}
}
