package com.nobleuplift.currencies;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.avaje.ebean.annotation.Transactional;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.Unit;

/**
 * This class is the main interface for accessing Currencies
 * from another plugin.
 * 
 * Currencies have certain rules to their creation and usage.
 * I have listed them here for both my benefit and anyone
 * implementing my plugin:
 * <ul>
 * <li>A currency is comprised of units.</li>
 * <li>
 * Every currency has a prime unit and a base unit.
 * A prime unit is the unit that is considered the unit that all
 * other units in the currency derive from. It is used to identify
 * the currency and for use in currency exchange.
 * A base unit is the smallest possible unit of a currency. 
 * </li>
 * <li>Each unit can only have one child unit, but can have
 * infinite parent units.</li>
 * <li>Two parent units of the same child cannot have the same multiplier.</li>
 * <li>Currencies can have the same prime symbol, but if two currencies
 * with the same prime symbol exist on the server, then users will have
 * to use /currencies setdefault to set a default between these two
 * currencies.</li>
 * <li>A currency cannot have two units with the same symbol.</li>
 * <li>Currencies cannot have children or parent units with
 * the same symbols as prime symbols of other currencies.</li>
 * </ul>
 * 
 * Created on 2015 May 2nd at 07:20:47 PM.
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

	@Transactional
	public static void addPrime(String acronym, String name, String plural, String symbol) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		//Unit symbolUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("symbol", symbol).findUnique();
		//if (symbolUnit != null) {
		//	throw new CurrenciesException(symbol + " is already the prime unit of another currency.");
		//}
		
		Unit u = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (u != null) {
			throw new CurrenciesException("Currency " + acronym + " already has a prime unit of currency.");
		}
		
		if (symbol.length() > 2) {
			throw new CurrenciesException("Symbol can be no more than two characters.");
		}
		
		if (!symbol.matches("\\D+")) {
			throw new CurrenciesException("Symbol cannot contain numbers.");
		}
		
		u = new Unit();
		u.setCurrency(c);
		u.setChildUnit(null);
		u.setName(name);
		u.setAlternate(plural);
		u.setSymbol(symbol);
		u.setPrime(true);
		u.setMain(true);
		u.setChildMultiples(0);
		u.setBaseMultiples(0);
		u.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		u.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(u);
	}

	@Transactional
	public static void addParent(String acronym, String name, String plural, String symbol, int multiplier, String child) throws CurrenciesException {
		// Get the currency
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		// Ensure the currency already has a prime unit
		Unit prime = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (prime == null) {
			throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
		}
		
		// Validate the singular name
		Unit singularUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("name", name)
			.findUnique();
		if (singularUnit != null) {
			throw new CurrenciesException("Unit with name " + name + " already exists for this currency.");
		}
		
		// Validate the plural name
		Unit pluralUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("alternate", plural)
			.findUnique();
		if (pluralUnit != null) {
			throw new CurrenciesException("Unit with plural name " + plural + " already exists for this currency.");
		}
		
		/*
		 * Validate the symbol
		 */
		Unit symbolUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("symbol", symbol)
			.findUnique();
		if (symbolUnit != null) {
			throw new CurrenciesException("Unit with symbol " + symbol + " already exists for currency " + acronym + ".");
		}

		Unit primeUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("symbol", symbol)
			.eq("prime", true)
			.findUnique();
		if (primeUnit != null) {
			throw new CurrenciesException("Unit with symbol " + symbol + " is a prime unit for another currency.");
		}
		
		if (symbol.length() > 2) {
			throw new CurrenciesException("Symbol can be no more than two characters.");
		}
		
		if (!symbol.matches("\\D+")) {
			throw new CurrenciesException("Symbol cannot contain numbers.");
		}
		
		// Validate the child
		Unit childUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("symbol", child).findUnique();
		if (childUnit == null) {
			throw new CurrenciesException("Child unit " + child + " does not exist for currency " + acronym + ".");
		}
		
		// Validate the multiplier
		if (multiplier <= 1) {
			throw new CurrenciesException("Multiplier must be greater than one.");
		}
		
		Unit multiplierUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("childUnit", childUnit)
			.eq("base_multiples", multiplier)
			.findUnique();
		if (multiplierUnit != null) {
			throw new CurrenciesException("A parent of " + child + " with multiplier " + multiplier + " already exists.");
		}
		
		int multiples = childUnit.getBaseMultiples() != 0 ? multiplier * childUnit.getBaseMultiples() : multiplier;
		
		Unit parentUnit = new Unit();
		parentUnit.setCurrency(c);
		parentUnit.setChildUnit(childUnit);
		parentUnit.setName(name);
		parentUnit.setAlternate(plural);
		parentUnit.setSymbol(symbol);
		parentUnit.setPrime(false);
		//u.setBase(false);
		parentUnit.setChildMultiples(multiplier);
		parentUnit.setBaseMultiples(multiples);
		parentUnit.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		parentUnit.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(parentUnit);
	}

	@Transactional
	public static void addChild(String acronym, String name, String plural, String symbol, int divisor, String parent) throws CurrenciesException {
		// Get the currency
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		// Ensure the currency already has a prime unit
		Unit prime = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (prime == null) {
			throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
		}
		
		// Validate the singular name
		Unit singularUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("name", name)
			.findUnique();
		if (singularUnit != null) {
			throw new CurrenciesException("Unit with name " + name + " already exists for this currency.");
		}
		
		// Validate the plural name
		Unit pluralUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("alternate", plural)
			.findUnique();
		if (pluralUnit != null) {
			throw new CurrenciesException("Unit with plural name " + plural + " already exists for this currency.");
		}
		
		/*
		 * Validate the symbol
		 */
		Unit symbolUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c)
			.eq("symbol", symbol)
			.findUnique();
		if (symbolUnit != null) {
			throw new CurrenciesException("Unit with symbol " + symbol + " already exists for currency " + acronym + ".");
		}

		Unit primeUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("symbol", symbol)
			.eq("prime", true)
			.findUnique();
		if (primeUnit != null) {
			throw new CurrenciesException("Unit with symbol " + symbol + " is a prime unit for another currency.");
		}
		
		if (symbol.length() > 2) {
			throw new CurrenciesException("Symbol can be no more than two characters.");
		}
		
		if (!symbol.matches("\\D+") || symbol.contains("-")) {
			throw new CurrenciesException("Symbol cannot contain numbers or the negative symbol.");
		}
		
		/*
		 * Validate the parent unit
		 */
		Unit parentUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("symbol", parent).findUnique();
		if (parentUnit == null) {
			throw new CurrenciesException("Unit " + parent + " does not exist.");
		}
		
		if (parentUnit.getChildUnit() != null) {
			throw new CurrenciesException("Unit " + parent + " already has a child. Units can only have one child.");
		}
		
		// Validate the divisor
		if (divisor <= 1) {
			throw new CurrenciesException("Divisor must be greater than 1.");
		}
		
		List<Unit> units = c.getUnits();
		for (Unit u : units) {
			/*
			 * Parent unit's multiples must be set
			 * separately at the end of this method.
			 */
			if (u.getId() == parentUnit.getId()) {
				continue;
			}
			
			/*
			 * If the base multiples are zero, it is the former
			 * base unit. Simply set the new divisor.
			 * 
			 * If the base multiples are not zero, it is a parent
			 * of the former base unit and each of the prior
			 * base multiples must be multiplied to equal
			 * the new base unit.
			 */
			if (u.getBaseMultiples() == 0) {
				u.setBaseMultiples(divisor);
			} else {
				u.setBaseMultiples(u.getBaseMultiples() * divisor);
			}
			Currencies.getInstance().getDatabase().save(u);
		}
		
		Unit childUnit = new Unit();
		childUnit.setCurrency(c);
		childUnit.setChildUnit(null);
		childUnit.setName(name);
		childUnit.setAlternate(plural);
		childUnit.setSymbol(symbol);
		childUnit.setPrime(false);
		childUnit.setMain(true);
		childUnit.setChildMultiples(0);
		childUnit.setBaseMultiples(0);
		childUnit.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		childUnit.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(childUnit);
		
		/*
		 * Since the parent is no longer the base unit, set its multiples
		 * to match the new child and link it to the child unit.
		 */
		parentUnit.setChildUnit(childUnit);
		parentUnit.setChildMultiples(divisor);
		parentUnit.setBaseMultiples(divisor);
		Currencies.getInstance().getDatabase().save(parentUnit);
	}
	
	public static List<Currency> list() throws CurrenciesException {
		return list(0);
	}
	
	@Transactional
	public static List<Currency> list(int page) throws CurrenciesException {
		return Currencies.getInstance().getDatabase()
			.find(Currency.class)
			.where()
			.eq("deleted", false)
			.setFirstRow((page - 1) * 10)
			.setMaxRows(10)
			.findList();
		
	}
	
	@Transactional
	public static Account openAccount(String name) throws CurrenciesException {
		Account account = new Account();
		account.setName(name);
		account.setUuid(null);
		account.setDefaultCurrency(null);
		account.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		account.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(account);
		
		return account;
	}
	
	@Transactional
	public static void setDefault(String player, String acronym) throws CurrenciesException {
		Account account = getAccountFromPlayer(player, true);
		Currency currency = getCurrencyFromAcronym(acronym, true);
		
		account.setDefaultCurrency(currency);
		account.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(account);
	}
	
	public static Map<Currency, Long> balance(String player) throws CurrenciesException {
		return balance(player, null);
	}
	
	@Transactional
	public static Map<Currency, Long> balance(String player, String acronym) throws CurrenciesException {
		Account account = Currencies.getInstance().getDatabase().find(Account.class).where().eq("name", player).findUnique();
		if (account == null) {
			throw new CurrenciesException("Account " + player + " does not exist.");
		}
		
		if (acronym == null) {
			List<Holding> holdings = account.getHoldings();
			
			return summateHoldings(holdings);
		} else {
			Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
			if (c == null) {
				throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
			}
			
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where().eq("unit.currency", c).findList();
			
			if (holdings.isEmpty()) {
				Map<Currency, Long> emptyMap = new HashMap<>();
				emptyMap.put(c, 0L);
				return emptyMap;
			} else {
				return summateHoldings(holdings);
			}
		}
	}
	
	@Transactional
	public static Transaction pay(String from, String to, String acronym, String amount) throws CurrenciesException {
		Account fromAccount = getAccountFromPlayer(from, true);
		Account toAccount = getAccountFromPlayer(to, true);
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long payAmount = parseCurrency(currency, amount);
		
		if (fromAccount.getId() == toAccount.getId()) {
			throw new CurrenciesException("You cannot pay yourself.");
		}
		
		if (fromAccount.getId() >= 1 && fromAccount.getId() <= 4) {
			throw new CurrenciesException("Reserved accounts cannot pay.");
		}
		
		if (toAccount.getId() >= 1 && toAccount.getId() <= 4) {
			throw new CurrenciesException("Cannot pay a reserved account.");
		}
		
		Transaction t = transferAmount(fromAccount, toAccount, currency, payAmount);
		Currencies.getInstance().getDatabase().save(t);
		return t;
	}
	
	@Transactional
	public static Transaction bill(String to, String from, String acronym, String amount) throws CurrenciesException {
		Account fromAccount = getAccountFromPlayer(from, true);
		Account toAccount = getAccountFromPlayer(to, true);
		Currency currency = getCurrencyFromAcronym(acronym, true);
		Unit base = getBaseUnit(currency);
		long billAmount = parseCurrency(currency, amount);
		
		if (fromAccount.getId() == toAccount.getId()) {
			throw new CurrenciesException("You cannot bill yourself.");
		}
		
		if (fromAccount.getId() >= 1 && fromAccount.getId() <= 4) {
			throw new CurrenciesException("Reserved accounts cannot bill.");
		}
		
		if (toAccount.getId() >= 1 && toAccount.getId() <= 4) {
			throw new CurrenciesException("Cannot bill a reserved account.");
		}
		
		Transaction t = new Transaction();
		t.setSender(fromAccount);
		t.setRecipient(toAccount);
		t.setUnit(base);
		t.setTransactionAmount(billAmount);
		t.setFinalSenderAmount(null);
		t.setFinalRecipientAmount(null);
		t.setPaid(null);
		t.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		t.setDatePaid(null);
		Currencies.getInstance().getDatabase().save(t);
		
		return t;
	}
	
	public static Transaction paybill(String from) throws CurrenciesException {
		return paybill(from, null);
	}
	
	@Transactional
	public static Transaction paybill(String from, String transaction) throws CurrenciesException {
		Account account = getAccountFromPlayer(from, true);
		Transaction t = null;
		if (transaction == null) {
			
			List<Transaction> transactions = Currencies.getInstance().getDatabase().find(Transaction.class)
				.where()
				.eq("sender", account)
				.eq("paid", null)
				.findList();
			
			if (transactions.size() > 1) {
				throw new CurrenciesException("You have more than one bill pending. Please specify the transaction ID. You can find it by running /transactions.");
			} else if (transactions.size() == 0) {
				throw new CurrenciesException("You have no bills pending. ");
			} else {
				t = transactions.get(0);
			}
		} else {
			t = Currencies.getInstance().getDatabase().find(Transaction.class)
				.where()
				.eq("id", transaction)
				.findUnique();
			
			if (t == null) {
				throw new CurrenciesException("Transaction " + transaction + " does not exist.");
			}
			
			if (account.getId() != t.getRecipient().getId()) {
				throw new CurrenciesException("You can only pay bills for which you are the recipient.");
			}
		}
		
		transferAmount(t.getSender(), t.getRecipient(), t.getUnit().getCurrency(), t.getTransactionAmount());
		t.setPaid(true);
		t.setDatePaid(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(t);
		return t;
	}
	
	public static List<Transaction> transactions(String player) throws CurrenciesException {
		return transactions(player, 1);
	}
	
	@Transactional
	public static List<Transaction> transactions(String player, int page) throws CurrenciesException {
		Account account = getAccountFromPlayer(player, false);
		
		return Currencies.getInstance().getDatabase()
			.find(Transaction.class)
			.where()
			.disjunction()
			.eq("sender", account)
			.eq("recipient", account)
			//.orderBy("dateCreated DESC")
			.setFirstRow((page - 1) * 10)
			.setMaxRows(10)
			.findList();
	}
	
	@Transactional
	public static Transaction credit(String player, String acronym, String amount) throws CurrenciesException {
		Account banker = getBanker();
		Account account = getAccountFromPlayer(player, true);
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long addAmount = parseCurrency(currency, amount);
		
		if (account.getId() >= 1 && account.getId() <= 4) {
			throw new CurrenciesException("Cannot credit a reserved account.");
		}
		
		Transaction t = transferAmount(banker, account, currency, addAmount);
		Currencies.getInstance().getDatabase().save(t);
		return t;
	}
	
	@Transactional
	public static Transaction debit(String player, String acronym, String amount) throws CurrenciesException {
		Account account = getAccountFromPlayer(player, true);
		Account banker = getBanker();
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long removeAmount = parseCurrency(currency, amount);
		
		if (account.getId() >= 1 && account.getId() <= 4) {
			throw new CurrenciesException("Cannot debit a reserved account.");
		}
		
		Transaction t = transferAmount(account, banker, currency, removeAmount);
		Currencies.getInstance().getDatabase().save(t);
		return t;
	}
	
	public static void bankrupt(String player) throws CurrenciesException {
		bankrupt(player, null, null);
	}
	
	public static void bankrupt(String player, String acronym) throws CurrenciesException {
		bankrupt(player, acronym, null);
	}
	
	@Transactional
	public static List<Holding> bankrupt(String player, String acronym, String amount) throws CurrenciesException {
		Account account = Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("name", player).findUnique();
		
		if (amount != null) {
			Currency currency = getCurrencyFromAcronym(acronym, true);
			Unit base = getBaseUnit(currency);
			long bankruptAmount = parseCurrency(currency, amount);
			
			// Reset a player's currency to this amount
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where()
				.eq("account", account)
				.eq("unit.currency", currency)
				.findList();
			
			Currencies.getInstance().getDatabase().delete(holdings);
			
			Holding h = new Holding();
			HoldingPK pk = new HoldingPK();
			pk.setAccountId(account.getId());
			pk.setUnitId(base.getId());
			h.setAmount(bankruptAmount);
			Currencies.getInstance().getDatabase().save(h);
			
			return holdings;
		} else if (acronym != null) {
			Currency currency = getCurrencyFromAcronym(acronym, true);
			
			// Delete all of a player's holdings equal to this currency
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where()
				.eq("account", account)
				.eq("unit.currency", currency)
				.findList();
			
			Currencies.getInstance().getDatabase().delete(holdings);
			
			return holdings;
		} else {
			// Delete everything
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where().eq("account", account).findList();
			
			Currencies.getInstance().getDatabase().delete(holdings);
			
			return holdings;
		}
	}
	
	/*
	 * 
	 * Loader Methods
	 * 
	 */
	
	public static Account getCentralBank() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", Currencies.CENTRAL_BANK).findUnique();
	}
	
	public static Account getBanker() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", Currencies.BANKER).findUnique();
	}
	
	public static Account getBlackMarket() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", Currencies.BLACK_MARKET).findUnique();
	}
	
	public static Account getTrader() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", Currencies.TRADER).findUnique();
	}
	
	public static Account getAccountFromPlayer(String player, boolean exception) throws CurrenciesException {
		Account account = Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("name", player).findUnique();
		if (account == null && exception) {
			throw new CurrenciesException("Account " + player + " does not exist.");
		}
		return account;
	}
	
	public static Currency getCurrencyFromAcronym(String acronym, boolean exception) throws CurrenciesException {
		Currency currency = Currencies.getInstance().getDatabase().find(Currency.class)
			.where().eq("acronym", acronym).findUnique();
		if (currency == null && exception) {
			throw new CurrenciesException("Currency " + acronym + " does not exist.");
		}
		return currency;
	}
	
	public static Unit getBaseUnit(Currency currency) throws CurrenciesException {
		Unit base = Currencies.getInstance().getDatabase().find(Unit.class)
			.where()
			.eq("currency", currency)
			.eq("childUnit", null)
			.findUnique();
		if (base == null) {
			throw new CurrenciesException("Currency " + currency.getAcronym() + " has no base.");
		}
		return base;
	}
	
	public static Map<Short, Unit> getUnits(Currency currency) {
		List<Unit> units = Currencies.getInstance().getDatabase().find(Unit.class)
			.where()
			.eq("currency", currency)
			.orderBy("prime DESC")
			.orderBy("main DESC")
			.orderBy("baseMultiples DESC")
			.findList();
		Map<Short, Unit> retval = new HashMap<>();
		for (Unit u : units) {
			retval.put(u.getId(), u);
		}
		return retval;
	}
	
	/*
	 * 
	 * Protected Utility Methods
	 * 
	 */
	
	protected static Map<Currency, Long> summateHoldings(List<Holding> holdings) {
		Map<Currency, Long> currencyBaseAmount = new HashMap<>();
		
		if (holdings.size() == 0) {
			return currencyBaseAmount;
		}
		
		for (Holding h : holdings) {
			Unit u = h.getUnit();
			Currency c = u.getCurrency();
			
			Long amount = currencyBaseAmount.get(c);
			if (amount == null) {
				amount = 0L;
			}
			
			if (u.getChildUnit() == null) {
				amount = amount + h.getAmount();
			} else {
				amount = amount + (h.getAmount() * u.getBaseMultiples());
			}
			
			currencyBaseAmount.put(c, amount);
		}
		return currencyBaseAmount;
	}
	
	private static void compactHoldings(Account account ) {
		List<Holding> holdings = account.getHoldings();
		Map<Currency, Long> swapMap = new HashMap<>();
		
		Iterator<Holding> ih = holdings.iterator();
		while (ih.hasNext()) {
			Holding holding = ih.next();
			
			Unit u = holding.getUnit();
			Currency c = u.getCurrency();
			
			if (!u.getPrime()) {
				long tempAmount = 0;
				if (swapMap.containsKey(c)) {
					tempAmount += swapMap.get(c);
				}
				tempAmount += holding.getAmount();
				swapMap.put(c, tempAmount);
				Currencies.getInstance().getDatabase().delete(holding);
				ih.remove();
			}
		}
		
		for (Holding holding : holdings) {
			Unit u = holding.getUnit();
			Currency c = u.getCurrency();
			
			if (u.getPrime() && swapMap.containsKey(c)) {
				holding.setAmount(holding.getAmount() + swapMap.get(c));
				swapMap.remove(c);
			}
		}
		
		// TODO: This method is horrible, code the simple cases first
	}
	
	/*
	 * 
	 * Public Utility Method
	 * 
	 */
	
	public static Map<Currency, String> formatCurrencies(Map<Currency, Long> currencyAmounts) {
		Map<Currency, String> retval = new HashMap<>();
		for (Map.Entry<Currency, Long> currencyAmount : currencyAmounts.entrySet()) {
			Currency c = currencyAmount.getKey();
			
			retval.put(c, formatCurrency(c, currencyAmount.getValue()));
		}
		return retval;
	}
	
	public static String formatCurrency(Currency c, long amount) {
		List<Unit> units = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", c).eq("main", true).orderBy().desc("base_multiples").findList();
		
		String currency = "";
		long remainder = amount;
		for (Unit u : units) {
			if (u.getBaseMultiples() > 0) {
				long quotient = remainder / u.getBaseMultiples();
				if (quotient == 0) {
					continue;
				}
				
				if (c.getPrefix()) {
					currency += u.getSymbol() + quotient;
				} else {
					currency += quotient + u.getSymbol();
				}
				remainder = remainder % u.getBaseMultiples();
			} else if (remainder != 0) {
				if (c.getPrefix()) {
					currency += u.getSymbol() + remainder;
				} else {
					currency += remainder + u.getSymbol();
				}
			}
		}
		
		return currency;
	}
	
	public static long parseCurrency(Currency currency, String amount) throws CurrenciesException {
		// http://stackoverflow.com/questions/2206378/how-to-split-a-string-but-also-keep-the-delimiters
		String[] parts = amount.replaceAll("([0-9-]+)", "|$1|").replaceAll("(^\\|*)|(\\|*$)","").split("\\|");
		if (Currencies.DEBUG) {
			Currencies.getInstance().getLogger().info("PARSE CURRENCY - ALL: " + parts);
		}
		
		if (parts.length == 0 || parts.length == 1) {
			throw new CurrenciesException("Either no symbol or no currency amount was provided.");
		}
		
		long baseAmount = 0;
		
		Unit partUnit = null;
		Long partAmount = null;
		
		for (String part : parts) {
			if (Currencies.DEBUG) {
				Currencies.getInstance().getLogger().info("PARSE CURRENCY - PART: " + part);
			}
			
			if (part.matches("\\D+")) {
				partUnit = Currencies.getInstance().getDatabase().find(Unit.class)
					.where().eq("currency_id", currency.getId()).eq("symbol", part).findUnique();
				if (partUnit == null) {
					throw new CurrenciesException(part + " is not a valid symbol.");
				}
				
				if (Currencies.DEBUG) {
					Currencies.getInstance().getLogger().info("PARSE CURRENCY - UNIT: " + partUnit.getName());
				}
			} else {
				try {
					partAmount = Long.parseLong(part);
				} catch (NumberFormatException e) {
					throw new CurrenciesException(part + " could not be parsed into a number.");
				}
				
				if (Currencies.DEBUG) {
					Currencies.getInstance().getLogger().info("PARSE CURRENCY - PART AMOUNT: " + partAmount);
				}
			}
			
			if (partUnit != null && partAmount != null) {
				baseAmount += partUnit.getBaseMultiples() != 0 ? partAmount * partUnit.getBaseMultiples() : partAmount;
				
				if (Currencies.DEBUG) {
					Currencies.getInstance().getLogger().info("PARSE CURRENCY - BASE AMOUNT: " + baseAmount);
				}
				
				partUnit = null;
				partAmount = null;
			}
		}
		
		if (Currencies.DEBUG) {
			Currencies.getInstance().getLogger().info("PARSE CURRENCY - FINAL AMOUNT: " + baseAmount);
		}
		
		return baseAmount;
	}
	
	public static Currency getCurrencyFromAmount(Account account, String amount) throws CurrenciesException {
		String[] parts = amount.replaceAll("([0-9-]+)", "|$1|").replaceAll("(^\\|*)|(\\|*$)","").split("\\|");
		
		if (parts.length == 0 || parts.length == 1) {
			throw new CurrenciesException("Either no symbol or no currency amount was provided.");
		}
		
		Currency c = null;
		
		for (String part : parts) {
			List<Unit> primes = Currencies.getInstance().getDatabase().find(Unit.class)
				.where().eq("symbol", part).eq("prime", true).findList();
			
			if (primes.size() == 1) {
				if (c != null) {
					throw new CurrenciesException("Two prime units were provided in the currency string.");
				}
				
				c = primes.get(0).getCurrency();
			} else if (primes.size() > 1) {
				if (account.getDefaultCurrency() == null) {
					throw new CurrenciesException("This currency shares a prime unit with other currencies. You must run /currencies setdefault <currency>.");
				}
				
				for (Unit p : primes) {
					if (p.getCurrency().getId() == account.getDefaultCurrency().getId()) {
						c = p.getCurrency();
						break;
					}
				}
			}
		}
		
		if (c == null) {
			throw new CurrenciesException("No prime unit was located in your currency string.");
		}
		
		return c;
	}
	
	private static Transaction transferAmount(Account fromAccount, Account toAccount, Currency currency, long amount) throws CurrenciesException {
		Unit base = getBaseUnit(currency);
		
		Holding fromHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", fromAccount)
			.eq("unit.currency", currency)
			.eq("unit.childUnit", null)
			.findUnique();
		
		if (fromHolding == null) {
			HoldingPK pk = new HoldingPK();
			pk.setAccountId(fromAccount.getId());
			pk.setUnitId(base.getId());
			
			fromHolding = new Holding();
			fromHolding.setId(pk);
			//fromHolding.setAccount(centralBank);
			//fromHolding.setUnit(base);
			fromHolding.setAmount(0);
		}
		
		long fromAmount = fromHolding.getAmount() - amount;
		if (Currencies.DEBUG) {
			Currencies.getInstance().getLogger().info("CREDIT - FROM AMOUNT: " + fromAmount);
		}
		fromHolding.setAmount(fromAmount);
		Currencies.getInstance().getDatabase().save(fromHolding);
		
		Holding toHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", toAccount)
			.eq("unit.currency", currency)
			.eq("unit.childUnit", null)
			.findUnique();
		
		if (toHolding == null) {
			HoldingPK pk = new HoldingPK();
			pk.setAccountId(toAccount.getId());
			pk.setUnitId(base.getId());
			
			toHolding = new Holding();
			toHolding.setId(pk);
			//toHolding.setAccount(account);
			//toHolding.setUnit(base);
			toHolding.setAmount(0);
		}
		
		long toAmount = toHolding.getAmount() + amount;
		if (Currencies.DEBUG) {
			Currencies.getInstance().getLogger().info("CREDIT - TO AMOUNT: " + toAmount);
		}
		toHolding.setAmount(toAmount);
		Currencies.getInstance().getDatabase().save(toHolding);
		
		Transaction t = new Transaction();
		t.setSender(fromAccount);
		t.setRecipient(toAccount);
		t.setUnit(base);
		t.setTransactionAmount(amount);
		t.setFinalSenderAmount(fromAmount);
		t.setFinalRecipientAmount(toAmount);
		t.setPaid(true);
		t.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		t.setDatePaid(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		
		return t;
	}
}
