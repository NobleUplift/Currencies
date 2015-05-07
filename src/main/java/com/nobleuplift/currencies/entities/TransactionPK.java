package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the currencies_transaction database table.
 * 
 */
@Embeddable
public class TransactionPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="sender_id", insertable=false, updatable=false, unique=true, nullable=false)
	private String senderId;

	@Column(name="recipient_id", insertable=false, updatable=false, unique=true, nullable=false)
	private String recipientId;

	@Column(name="unit_id", insertable=false, updatable=false, unique=true, nullable=false)
	private int unitId;

	public TransactionPK() {
	}
	public String getSenderId() {
		return this.senderId;
	}
	public void setSenderId(String senderId) {
		this.senderId = senderId;
	}
	public String getRecipientId() {
		return this.recipientId;
	}
	public void setRecipientId(String recipientId) {
		this.recipientId = recipientId;
	}
	public int getUnitId() {
		return this.unitId;
	}
	public void setUnitId(int unitId) {
		this.unitId = unitId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TransactionPK)) {
			return false;
		}
		TransactionPK castOther = (TransactionPK)other;
		return 
			this.senderId.equals(castOther.senderId)
			&& this.recipientId.equals(castOther.recipientId)
			&& (this.unitId == castOther.unitId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.senderId.hashCode();
		hash = hash * prime + this.recipientId.hashCode();
		hash = hash * prime + this.unitId;
		
		return hash;
	}
}