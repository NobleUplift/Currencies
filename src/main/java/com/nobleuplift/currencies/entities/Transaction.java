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

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(unique=true, nullable=false)
	private Long id;

	@Column(name="date_created", nullable=false)
	private Timestamp dateCreated;

	@Column(name="date_paid")
	private Timestamp datePaid;

	@Column(name="final_recipient_amount")
	private Long finalRecipientAmount;

	@Column(name="final_sender_amount")
	private Long finalSenderAmount;

	private Boolean paid;

	@Column(name="transaction_amount", nullable=false)
	private long transactionAmount;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="sender_id", nullable=false)
	private Account sender;

	//bi-directional many-to-one association to Account
	@ManyToOne
	@JoinColumn(name="recipient_id", nullable=false)
	private Account recipient;

	//bi-directional many-to-one association to Unit
	@ManyToOne
	@JoinColumn(name="unit_id", nullable=false)
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

	public Boolean getPaid() {
		return this.paid;
	}

	public void setPaid(Boolean paid) {
		this.paid = paid;
	}

	public long getTransactionAmount() {
		return this.transactionAmount;
	}

	public void setTransactionAmount(long transactionAmount) {
		this.transactionAmount = transactionAmount;
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