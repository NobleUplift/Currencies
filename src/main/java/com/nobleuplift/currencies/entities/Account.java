package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;
import java.sql.Timestamp;
import java.util.List;


/**
 * The persistent class for the currencies_account database table.
 * 
 */
@Entity
@Table(name="currencies_account")
@NamedQuery(name="Account.findAll", query="SELECT a FROM Account a")
public class Account implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(updatable=false, unique=true, nullable=false)
	private Integer id;

	@Column(name="date_created", nullable=false)
	private Timestamp dateCreated;

	@Column(name="date_modified", nullable=false)
	private Timestamp dateModified;

	@Column(nullable=false, length=64)
	private String name;

	@Column(length=37)
	private String uuid;

	//bi-directional many-to-many association to Account
	@ManyToMany
	@JoinTable(
		name="currencies_holder"
		, joinColumns={
			@JoinColumn(name="child_account_id", nullable=false)
			}
		, inverseJoinColumns={
			@JoinColumn(name="parent_account_id", nullable=false)
			}
		)
	private List<Account> parentAccounts;

	//bi-directional many-to-many association to Account
	@ManyToMany(mappedBy="parentAccounts")
	private List<Account> childAccounts;

	//bi-directional many-to-many association to Account
	@ManyToMany
	@JoinTable(
		name="currencies_holder"
		, joinColumns={
			@JoinColumn(name="parent_account_id", nullable=false)
			}
		, inverseJoinColumns={
			@JoinColumn(name="child_account_id", nullable=false)
			}
		)
	private List<Account> parentAccounts;

	//bi-directional many-to-many association to Account
	@ManyToMany(mappedBy="parentAccounts")
	private List<Account> childAccounts;

	//bi-directional many-to-one association to Currency
	@ManyToOne
	@JoinColumn(name="default_currency_id")
	private Currency defaultCurrency;

	//bi-directional many-to-one association to Holding
	@OneToMany(mappedBy="account")
	private List<Holding> holdings;

	//bi-directional many-to-one association to Transaction
	@OneToMany(mappedBy="sender")
	private List<Transaction> senderTransactions;

	//bi-directional many-to-one association to Transaction
	@OneToMany(mappedBy="recipient")
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

}