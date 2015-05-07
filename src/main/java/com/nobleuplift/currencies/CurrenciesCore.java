package com.nobleuplift.currencies;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.annotation.Transactional;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

/**
 * Created on 2015 May 2nd at 07:20:47 PM.
 * 
 * @author NobleUplift
 */
public final class CurrenciesCore {
	public static void createCurrency(String acronym, String name) throws CurrenciesException {
		createCurrency(acronym, name, true);
	}
	
	@Transactional
	public static void createCurrency(String acronym, String name, boolean prefix) throws CurrenciesException {
		if (acronym.length() != 3) {
			throw new CurrenciesException("All currency acronyms must be three characters.");
		}
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c != null) {
			throw new CurrenciesException(acronym + " has been taken by another currency.");
		}
		c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("name", name).findUnique();
		if (c != null) {
			throw new CurrenciesException(name + " has been taken by another currency.");
		}
		
		c = new Currency();
		c.setName(name);
		c.setAcronym(acronym);
		c.setPrefix(prefix);
		c.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		c.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		c.setDateDeleted(null);
		c.setDeleted(false);
		Currencies.getInstance().getDatabase().save(c);
	}
	
	public static void deleteCurrency(String acronym) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Could not find currency with acronym " + acronym + ".");
		}
		
		c.setDeleted(true);
		c.setDateDeleted(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(c);
	}
	
	public static void addPrime(String acronym, String symbol, String name) throws CurrenciesException {
		addPrime(acronym, symbol, name, name);
	}

	@Transactional
	public static void addPrime(String acronym, String symbol, String name, String singular) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		Unit u = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (u != null) {
			throw new CurrenciesException("Currency " + acronym + " already has a prime unit of currency.");
		}
		
		u = new Unit();
		u.setCurrency(c);
		u.setChildUnit(null);
		u.setName(name);
		u.setSingular(singular);
		u.setSymbol(symbol);
		u.setPrime(true);
		//u.setBase(true);
		u.setChildMultiples(0);
		u.setBaseMultiples(0);
		u.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		u.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(u);
	}
	
	public static void addParent(String acronym, String child, int multiplier, String symbol, String name) throws CurrenciesException {
		addParent(acronym, child, multiplier, symbol, name, name);
	}

	@Transactional
	public static void addParent(String acronym, String child, int multiplier, String symbol, String name, String singular) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		Unit prime = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (prime == null) {
			throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
		}
		
		Unit childUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("symbol", child).findUnique();
		if (childUnit == null) {
			throw new CurrenciesException("Child unit " + child + " does not exist for currency " + acronym + ".");
		}
		
		Unit u = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currencyId", c.getId())
			.eq("symbol", symbol)
			.eq("name", name)
			.findUnique();
		
		// TODO: Find out how to validate this later
		/*Unit singularUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currencyId", c.getId())
			.eq("singular", singular)
			.findUnique();*/
		
		if (u != null) {
			throw new CurrenciesException("Unit " + name + " (" + symbol + ") already exists for currency " + acronym + ".");
		}
		
		int multiples = childUnit.getBaseMultiples() != 0 ? multiplier * childUnit.getBaseMultiples() : multiplier;
		
		u = new Unit();
		u.setCurrency(c);
		u.setChildUnit(null);
		u.setName(name);
		u.setSingular(singular);
		u.setSymbol(symbol);
		u.setPrime(false);
		//u.setBase(false);
		u.setChildMultiples(multiplier);
		u.setBaseMultiples(multiples);
		u.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		u.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(u);
	}
	
	public static void addChild(String acronym, String parent, int divisor, String symbol, String name) throws CurrenciesException {
		addChild(acronym, parent, divisor, symbol, name, name);
	}

	@Transactional
	public static void addChild(String acronym, String parent, int divisor, String symbol, String name, String singular) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		Unit prime = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (prime == null) {
			throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
		}
		
		Unit parentUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("name", parent).findUnique();
		if (parentUnit == null) {
			throw new CurrenciesException("Unit " + parent + " does not exist.");
		}
		
		if (parentUnit.getChildUnit() != null) {
			throw new CurrenciesException("Unit " + parent + " already has a child. Units can only have one child.");
		}
		
		Unit childUnit = new Unit();
		childUnit.setCurrency(c);
		childUnit.setChildUnit(null);
		childUnit.setName(name);
		childUnit.setSingular(singular);
		childUnit.setSymbol(symbol);
		childUnit.setPrime(false);
		childUnit.setChildMultiples(0);
		childUnit.setBaseMultiples(0);
		childUnit.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		childUnit.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(childUnit);
		
		List<Unit> units = c.getUnits();
		for (Unit u : units) {
			// Unit is the parent
			if (u.getChildMultiples() == 0) {
				u.setChildMultiples(divisor);
			}
			
			if (u.getBaseMultiples() == 0) {
				u.setBaseMultiples(divisor);
			} else {
				u.setBaseMultiples(u.getBaseMultiples() * divisor);
			}
			Currencies.getInstance().getDatabase().save(u);
		}
		
	}
	
	public static void balance() throws CurrenciesException {
		balance(null, null);
	}
	
	public static void balance(String player) throws CurrenciesException {
		balance(null, null);
	}
	
	@Transactional
	public static void balance(String player, String currency) throws CurrenciesException {
		
	}
	
	@Transactional
	public static void pay(String player, String currency, String amount) throws CurrenciesException {
		
	}
	
	@Transactional
	public static void bill(String player, String currency, String amount) throws CurrenciesException {
		
	}
	
	public static void paybill() throws CurrenciesException {
		paybill(null);
	}
	
	@Transactional
	public static void paybill(String transaction) throws CurrenciesException {
		
	}
	
	@Transactional
	public static void credit(String player, String currency, String amount) throws CurrenciesException {
		
	}
	
	@Transactional
	public static void debit(String player, String currency, String amount) throws CurrenciesException {
		
	}
	
	public static void bankrupt(String player) throws CurrenciesException {
		bankrupt(player, null, null);
	}
	
	public static void bankrupt(String player, String currency) throws CurrenciesException {
		bankrupt(player, null, null);
	}
	
	@Transactional
	public static void bankrupt(String player, String currency, String amount) throws CurrenciesException {
		
	}
}
