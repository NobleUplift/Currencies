package com.nobleuplift.currencies;

import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

/**
 * Created on Oct 25, 2015 at 12:55:57 PM.
 * 
 * @author Patrick
 */
public class CurrencyDTO {
	private Currency currency;
	private Unit baseUnit;
	private Long baseAmount;
	
	public CurrencyDTO(Currency currency, Unit baseUnit, Long baseAmount) {
		this.currency = currency;
		this.baseUnit = baseUnit;
		this.baseAmount = baseAmount;
	}
	
	/**
	 * @return the currency
	 */
	public Currency getCurrency() {
		return currency;
	}
	/**
	 * @param currency the currency to set
	 */
	public void setCurrency(Currency currency) {
		this.currency = currency;
	}
	/**
	 * @return the baseUnit
	 */
	public Unit getBaseUnit() {
		return baseUnit;
	}
	/**
	 * @param baseUnit the baseUnit to set
	 */
	public void setBaseUnit(Unit baseUnit) {
		this.baseUnit = baseUnit;
	}
	/**
	 * @return the baseAmount
	 */
	public Long getBaseAmount() {
		return baseAmount;
	}
	/**
	 * @param baseAmount the baseAmount to set
	 */
	public void setBaseAmount(Long baseAmount) {
		this.baseAmount = baseAmount;
	}
}
