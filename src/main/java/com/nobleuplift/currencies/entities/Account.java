package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

public class Account implements Serializable {
	private static final long serialVersionUID = 1L;

	private Integer id;
	private Timestamp dateCreated;
	private Timestamp dateModified;
	private String name;
	private String uuid;

	private List<Account> parentAccounts;
	private List<Account> childAccounts;
	private Currency defaultCurrency;
	private List<Holding> holdings;
	private List<Transaction> senderTransactions;
	private List<Transaction> recipientTransactions;

	public Account() {
	}

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Timestamp getDateCreated() {
		return this.dateCreated;
	}

	public void setDateCreated(Timestamp dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Timestamp getDateModified() {
		return this.dateModified;
	}

	public void setDateModified(Timestamp dateModified) {
		this.dateModified = dateModified;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUuid() {
		return this.uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public List<Account> getParentAccounts() {
		return this.parentAccounts;
	}

	public void setParentAccounts(List<Account> parentAccounts) {
		this.parentAccounts = parentAccounts;
	}

	public List<Account> getChildAccounts() {
		return this.childAccounts;
	}

	public void setChildAccounts(List<Account> childAccounts) {
		this.childAccounts = childAccounts;
	}

	public Currency getDefaultCurrency() {
		return this.defaultCurrency;
	}

	public void setDefaultCurrency(Currency defaultCurrency) {
		this.defaultCurrency = defaultCurrency;
	}

	public List<Holding> getHoldings() {
		return this.holdings;
	}

	public void setHoldings(List<Holding> holdings) {
		this.holdings = holdings;
	}

	public Holding addHolding(Holding holding) {
		getHoldings().add(holding);
		holding.setAccount(this);
		return holding;
	}

	public Holding removeHolding(Holding holding) {
		getHoldings().remove(holding);
		holding.setAccount(null);
		return holding;
	}

	public List<Transaction> getSenderTransactions() {
		return this.senderTransactions;
	}

	public void setSenderTransactions(List<Transaction> senderTransactions) {
		this.senderTransactions = senderTransactions;
	}

	public Transaction addSenderTransaction(Transaction senderTransaction) {
		getSenderTransactions().add(senderTransaction);
		senderTransaction.setSender(this);
		return senderTransaction;
	}

	public Transaction removeSenderTransaction(Transaction senderTransaction) {
		getSenderTransactions().remove(senderTransaction);
		senderTransaction.setSender(null);
		return senderTransaction;
	}

	public List<Transaction> getRecipientTransactions() {
		return this.recipientTransactions;
	}

	public void setRecipientTransactions(List<Transaction> recipientTransactions) {
		this.recipientTransactions = recipientTransactions;
	}

	public Transaction addRecipientTransaction(Transaction recipientTransaction) {
		getRecipientTransactions().add(recipientTransaction);
		recipientTransaction.setRecipient(this);
		return recipientTransaction;
	}

	public Transaction removeRecipientTransaction(Transaction recipientTransaction) {
		getRecipientTransactions().remove(recipientTransaction);
		recipientTransaction.setRecipient(null);
		return recipientTransaction;
	}

	@Override
	public String toString() {
		return "Account [id=" + id + ", name=" + name + ", uuid=" + uuid + "]";
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
		Account other = (Account) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
