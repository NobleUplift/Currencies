package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import java.sql.Timestamp;

public class Transaction implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;
	private Timestamp dateCreated;
	private Timestamp datePaid;
	private Long finalRecipientAmount;
	private Long finalSenderAmount;
	private Boolean paid;
	private Long transactionAmount;
	private Short typeId;

	private Account recipient;
	private Account sender;
	private Unit unit;

	public Transaction() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Timestamp getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Timestamp dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Timestamp getDatePaid() {
		return this.datePaid;
	}

	public void setDatePaid(Timestamp datePaid) {
		this.datePaid = datePaid;
	}

	public Long getFinalRecipientAmount() {
		return this.finalRecipientAmount;
	}

	public void setFinalRecipientAmount(Long finalRecipientAmount) {
		this.finalRecipientAmount = finalRecipientAmount;
	}

	public Long getFinalSenderAmount() {
		return this.finalSenderAmount;
	}

	public void setFinalSenderAmount(Long finalSenderAmount) {
		this.finalSenderAmount = finalSenderAmount;
	}

	public Boolean isPaid() {
		return this.paid;
	}

	public Boolean getPaid() {
		return this.paid;
	}

	public void setPaid(Boolean paid) {
		this.paid = paid;
	}

	public Long getTransactionAmount() {
		return this.transactionAmount;
	}

	public void setTransactionAmount(Long transactionAmount) {
		this.transactionAmount = transactionAmount;
	}

	public Short getTypeId() {
		return this.typeId;
	}

	public void setTypeId(Short typeId) {
		this.typeId = typeId;
	}

	public Account getRecipient() {
		return this.recipient;
	}

	public void setRecipient(Account recipient) {
		this.recipient = recipient;
	}

	public Account getSender() {
		return this.sender;
	}

	public void setSender(Account sender) {
		this.sender = sender;
	}

	public Unit getUnit() {
		return this.unit;
	}

	public void setUnit(Unit unit) {
		this.unit = unit;
	}

	@Override
	public String toString() {
		return "Transaction [id=" + id + ", dateCreated=" + dateCreated
				+ ", datePaid=" + datePaid + ", finalRecipientAmount="
				+ finalRecipientAmount + ", finalSenderAmount="
				+ finalSenderAmount + ", paid=" + paid + ", transactionAmount="
				+ transactionAmount + ", typeId=" + typeId + "]";
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
		Transaction other = (Transaction) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
