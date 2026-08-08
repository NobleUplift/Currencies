package com.nobleuplift.currencies.entities;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

public class Unit implements Serializable {
	private static final long serialVersionUID = 1L;

	private Short id;
	private String alternate;
	private int baseMultiples;
	private int childMultiples;
	private Timestamp dateCreated;
	private Timestamp dateModified;
	private boolean main;
	private String name;
	private boolean prime;
	private String symbol;

	private List<Holding> holdings;
	private List<Transaction> transactions;
	private Currency currency;
	private Unit childUnit;
	private List<Unit> units;

	public Unit() {
	}

	public Short getId() {
		return this.id;
	}

	public void setId(Short id) {
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

	public boolean isMain() {
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

	public boolean isPrime() {
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

	@Override
	public String toString() {
		return "Unit [id=" + id + ", alternate=" + alternate
				+ ", baseMultiples=" + baseMultiples + ", childMultiples="
				+ childMultiples + ", dateCreated=" + dateCreated
				+ ", dateModified=" + dateModified + ", main=" + main
				+ ", name=" + name + ", prime=" + prime + ", symbol=" + symbol
				+ "]";
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
		Unit other = (Unit) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
