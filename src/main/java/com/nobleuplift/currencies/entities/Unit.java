package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import javax.persistence.*;
import java.sql.Timestamp;
import java.util.List;


/**
 * The persistent class for the currencies_unit database table.
 * 
 */
@Entity
@Table(name="currencies_unit")
@NamedQuery(name="Unit.findAll", query="SELECT u FROM Unit u")
public class Unit implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(unique=true, nullable=false)
	private Integer id;

	@Column(nullable=false, length=32)
	private String alternate;

	@Column(name="base_multiples", nullable=false)
	private int baseMultiples;

	@Column(name="child_multiples", nullable=false)
	private int childMultiples;

	@Column(name="date_created", nullable=false)
	private Timestamp dateCreated;

	@Column(name="date_modified", nullable=false)
	private Timestamp dateModified;

	@Column(nullable=false)
	private boolean main;

	@Column(nullable=false, length=32)
	private String name;

	@Column(nullable=false)
	private boolean prime;

	@Column(nullable=false, length=2)
	private String symbol;

	//bi-directional many-to-one association to Holding
	@OneToMany(mappedBy="unit")
	private List<Holding> holdings;

	//bi-directional many-to-one association to Transaction
	@OneToMany(mappedBy="unit")
	private List<Transaction> transactions;

	//bi-directional many-to-one association to Currency
	@ManyToOne
	@JoinColumn(name="currency_id", nullable=false)
	private Currency currency;

	//bi-directional many-to-one association to Unit
	@ManyToOne
	@JoinColumn(name="child_unit_id")
	private Unit childUnit;

	//bi-directional many-to-one association to Unit
	@OneToMany(mappedBy="childUnit")
	private List<Unit> units;

	public Unit() {
	}

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getAlternate() {
		return this.alternate;
	}

	public void setAlternate(String alternate) {
		this.alternate = alternate;
	}

	public int getBaseMultiples() {
		return this.baseMultiples;
	}

	public void setBaseMultiples(int baseMultiples) {
		this.baseMultiples = baseMultiples;
	}

	public int getChildMultiples() {
		return this.childMultiples;
	}

	public void setChildMultiples(int childMultiples) {
		this.childMultiples = childMultiples;
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

	public boolean getMain() {
		return this.main;
	}

	public void setMain(boolean main) {
		this.main = main;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean getPrime() {
		return this.prime;
	}

	public void setPrime(boolean prime) {
		this.prime = prime;
	}

	public String getSymbol() {
		return this.symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public List<Holding> getHoldings() {
		return this.holdings;
	}

	public void setHoldings(List<Holding> holdings) {
		this.holdings = holdings;
	}

	public Holding addHolding(Holding holding) {
		getHoldings().add(holding);
		holding.setUnit(this);

		return holding;
	}

	public Holding removeHolding(Holding holding) {
		getHoldings().remove(holding);
		holding.setUnit(null);

		return holding;
	}

	public List<Transaction> getTransactions() {
		return this.transactions;
	}

	public void setTransactions(List<Transaction> transactions) {
		this.transactions = transactions;
	}

	public Transaction addTransaction(Transaction transaction) {
		getTransactions().add(transaction);
		transaction.setUnit(this);

		return transaction;
	}

	public Transaction removeTransaction(Transaction transaction) {
		getTransactions().remove(transaction);
		transaction.setUnit(null);

		return transaction;
	}

	public Currency getCurrency() {
		return this.currency;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public Unit getChildUnit() {
		return this.childUnit;
	}

	public void setChildUnit(Unit childUnit) {
		this.childUnit = childUnit;
	}

	public List<Unit> getUnits() {
		return this.units;
	}

	public void setUnits(List<Unit> units) {
		this.units = units;
	}

	public Unit addUnit(Unit unit) {
		getUnits().add(unit);
		unit.setChildUnit(this);

		return unit;
	}

	public Unit removeUnit(Unit unit) {
		getUnits().remove(unit);
		unit.setChildUnit(null);

		return unit;
	}

}