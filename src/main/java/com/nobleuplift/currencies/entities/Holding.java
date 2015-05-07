package com.nobleuplift.currencies.entities;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Created on 2015 May 1st at 09:11 PM.
 * 
 * @author Patrick
 */
@Entity
@Table(name="currencies_holding")
public class Holding {
	@EmbeddedId
	private HoldingId holdingId;
	private long amount;
	
	public Holding() {
		super();
	}
	
	public HoldingId getHoldingId() {
		return holdingId;
	}
	
	public void setHoldingId(HoldingId holdingId) {
		this.holdingId = holdingId;
	}
	
	public long getAmount() {
		return amount;
	}
	
	public void setAmount(long amount) {
		this.amount = amount;
	}
}
