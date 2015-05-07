package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;
import java.sql.Timestamp;


/**
 * The persistent class for the currencies_transaction database table.
 * 
 */
@Entity
@Table(name="currencies_transaction")
@NamedQuery(name="Transaction.findAll", query="SELECT t FROM Transaction t")
public class Transaction implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private TransactionPK id;

	@Column(nullable=false)
	private long amount;

	@Column(name="date_created", nullable=false)
	private Timestamp dateCreated;

	@Column(name="date_paid")
	private Timestamp datePaid;

	@Column(nullable=false)
	private boolean paid;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="sender_id", nullable=false, insertable=false, updatable=false)
	private Account sender;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="recipient_id", nullable=false, insertable=false, updatable=false)
	private Account recipient;

	//bi-directional many-to-one association to Unit
	@ManyToOne
	@JoinColumn(name="unit_id", nullable=false, insertable=false, updatable=false)
	private Unit unit;

	public Transaction() {
	}

	public TransactionPK getId() {
		return this.id;
	}

	public void setId(TransactionPK id) {
		this.id = id;
	}

	public long getAmount() {
		return this.amount;
	}

	public void setAmount(long amount) {
		this.amount = amount;
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

	public boolean getPaid() {
		return this.paid;
	}

	public void setPaid(boolean paid) {
		this.paid = paid;
	}

	public Account getSender() {
		return this.sender;
	}

	public void setSender(Account sender) {
		this.sender = sender;
	}

	public Account getRecipient() {
		return this.recipient;
	}

	public void setRecipient(Account recipient) {
		this.recipient = recipient;
	}

	public Unit getUnit() {
		return this.unit;
	}

	public void setUnit(Unit unit) {
		this.unit = unit;
	}

}