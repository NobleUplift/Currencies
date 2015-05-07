package com.nobleuplift.currencies.entities;

import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Created on 2015 May 1st at 09:11 PM.
 * 
 * @author Patrick
 */
@Entity
@Table(name="currencies_unit")
public class Unit {
	@Id
	private short id;
	private short currencyId;
	private String name;
	private String singular;
	private String symbol;
	private boolean prime;
	private int childMultiples;
	private int baseMultiples;
	//private boolean base;
	private Timestamp dateCreated;
	private Timestamp dateModified;
	
	public Unit() {
		super();
	}
	
	public short getId() {
		return id;
	}
	
	public void setId(short id) {
		this.id = id;
	}
	
	public short getCurrencyId() {
		return currencyId;
	}
	
	public void setCurrencyId(short currencyId) {
		this.currencyId = currencyId;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getSingular() {
		return singular;
	}

	public void setSingular(String singular) {
		this.singular = singular;
	}

	public String getSymbol() {
		return symbol;
	}
	
	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}
	
	public boolean isPrime() {
		return prime;
	}
	
	public void setPrime(boolean prime) {
		this.prime = prime;
	}
	
	/*public boolean isBase() {
		return base;
	}
	
	public void setBase(boolean base) {
		this.base = base;
	}*/

	public int getChildMultiples() {
		return childMultiples;
	}

	public void setChildMultiples(int childMultiples) {
		this.childMultiples = childMultiples;
	}

	public int getBaseMultiples() {
		return baseMultiples;
	}

	public void setBaseMultiples(int baseMultiples) {
		this.baseMultiples = baseMultiples;
	}

	public Timestamp getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Timestamp dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Timestamp getDateModified() {
		return dateModified;
	}

	public void setDateModified(Timestamp dateModified) {
		this.dateModified = dateModified;
	}
}
