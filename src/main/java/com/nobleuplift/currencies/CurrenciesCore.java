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

	@Transactional
	public static void addPrime(String acronym, String name, String plural, String symbol) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		Unit symbolUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("symbol", symbol).findUnique();
		if (symbolUnit != null) {
			throw new CurrenciesException(symbol + " is already the prime unit of another currency.");
		}
		
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
	public static void addParent(String acronym, String name, String plural, String symbol, String child, int multiplier) throws CurrenciesException {
		Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", acronym).findUnique();
		if (c == null) {
			throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
		}
		
		Unit prime = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("prime", true).findUnique();
		if (prime == null) {
			throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
		}
		
		Unit childUnit = Currencies.getInstance().getDatabase().find(Unit.class).where().eq("currency_id", c.getId()).eq("name", child).findUnique();
		if (childUnit == null) {
			throw new CurrenciesException("Child unit " + child + " does not exist for currency " + acronym + ".");
		}
		
		if (symbol.length() > 2) {
			throw new CurrenciesException("Symbol can be no more than two characters.");
		}
		
		if (!symbol.matches("\\D+")) {
			throw new CurrenciesException("Symbol cannot contain numbers.");
		}
		
		//List<Unit> otherPrimes = Currencies.getInstance().getDatabase().find(Unit.class).where()
		//	.eq("symbol", symbol).eq("prime", true).findList();
		//if (!otherPrimes.isEmpty()) {
		//	throw new CurrenciesException(symbol + " is a prime unit for another currency.");
		//}
		
		if (multiplier <= 1) {
			throw new CurrenciesException("Multiplier must be greater than one.");
		}
		
		Unit u = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency_id", c.getId())
			.eq("symbol", symbol)
			.eq("name", name)
			.findUnique();
		
		// TODO: Find out how to validate this later
		/*Unit singularUnit = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency_id", c.getId())
			.eq("singular", singular)
			.findUnique();*/
		
		if (u != null) {
			throw new CurrenciesException("Unit " + name + " (" + symbol + ") already exists for currency " + acronym + ".");
		}
		
		int multiples = childUnit.getBaseMultiples() != 0 ? multiplier * childUnit.getBaseMultiples() : multiplier;
		
		u = new Unit();
		u.setCurrency(c);
		u.setChildUnit(childUnit);
		u.setName(name);
		u.setAlternate(plural);
		u.setSymbol(symbol);
		u.setPrime(false);
		//u.setBase(false);
		u.setChildMultiples(multiplier);
		u.setBaseMultiples(multiples);
		u.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		u.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(u);
	}

	@Transactional
	public static void addChild(String acronym, String name, String plural, String symbol, String parent, int divisor) throws CurrenciesException {
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
		
		if (symbol.length() > 2) {
			throw new CurrenciesException("Symbol can be no more than two characters.");
		}
		
		if (!symbol.matches("\\D+")) {
			throw new CurrenciesException("Symbol cannot contain numbers.");
		}
		
		//List<Unit> otherPrimes = Currencies.getInstance().getDatabase().find(Unit.class).where()
		//	.eq("symbol", symbol).eq("prime", true).findList();
		//if (!otherPrimes.isEmpty()) {
		//	throw new CurrenciesException(symbol + " is a prime unit for another currency.");
		//}
		
		if (divisor <= 1) {
			throw new CurrenciesException("Divisor must be greater than 1.");
		}
		
		List<Unit> units = c.getUnits();
		for (Unit u : units) {
			if (u.getId() == parentUnit.getId()) {
				continue;
			}
			
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
		
		parentUnit.setChildUnit(childUnit);
		parentUnit.setChildMultiples(divisor);
		parentUnit.setBaseMultiples(divisor);
		Currencies.getInstance().getDatabase().save(parentUnit);
	}
	
	public static Map<Currency, Long> balance(String player) throws CurrenciesException {
		return balance(player, null);
	}
	
	@Transactional
	public static Map<Currency, Long> balance(String player, String currency) throws CurrenciesException {
		Account account = Currencies.getInstance().getDatabase().find(Account.class).where().eq("name", player).findUnique();
		if (account == null) {
			throw new CurrenciesException("Account " + player + " does not exist.");
		}
		
		if (currency == null) {
			List<Holding> holdings = account.getHoldings();
			
			return summateHoldings(holdings);
		} else {
			Currency c = Currencies.getInstance().getDatabase().find(Currency.class).where().eq("acronym", currency).findUnique();
			if (c == null) {
				throw new CurrenciesException("Currency with acronym " + currency + " does not exist.");
			}
			
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where().eq("unit.currency", c).findList();
			
			return summateHoldings(holdings);
		}
	}
	
	@Transactional
	public static void pay(String from, String to, String currency, String amount) throws CurrenciesException {
		//Account fromAccount = getAccountFromPlayer(from);
		//Account toAccount = getAccountFromPlayer(to);
		
	}
	
	@Transactional
	public static void bill(String from, String to, String currency, String amount) throws CurrenciesException {
		//Account fromAccount = getAccountFromPlayer(from);
		//Account toAccount = getAccountFromPlayer(to);
		
	}
	
	public static void paybill() throws CurrenciesException {
		paybill(null);
	}
	
	@Transactional
	public static void paybill(String transaction) throws CurrenciesException {
		
	}
	
	@Transactional
	public static void credit(String player, String acronym, String amount) throws CurrenciesException {
		Account centralBank = getCentralBank();
		Account account = getAccountFromPlayer(player, true);
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long addAmount = parseCurrency(currency, amount);
		
		Unit base = Currencies.getInstance().getDatabase().find(Unit.class)
			.where()
			.eq("currency", currency)
			.eq("childUnit", null)
			.findUnique();
		if (base == null) {
			throw new CurrenciesException("Currency " + acronym + " has no base.");
		}
		
		Holding fromHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", centralBank)
			.eq("unit.currency", currency)
			.eq("unit.childUnit", null)
			.findUnique();
		
		if (fromHolding == null) {
			fromHolding = new Holding();
			fromHolding.setAccount(centralBank);
			fromHolding.setUnit(base);
			fromHolding.setAmount(0);
		}
		
		long fromAmount = fromHolding.getAmount() - addAmount;
		fromHolding.setAmount(fromAmount);
		Currencies.getInstance().getDatabase().save(fromHolding);
		
		Holding toHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", account)
			.eq("unit.currency", currency)
			.eq("unit.childUnit", null)
			.findUnique();
		
		if (toHolding == null) {
			toHolding = new Holding();
			toHolding.setAccount(account);
			toHolding.setUnit(base);
			toHolding.setAmount(0);
		}
		
		long toAmount = toHolding.getAmount() + addAmount;
		toHolding.setAmount(toAmount);
		Currencies.getInstance().getDatabase().save(toHolding);
		
		/*Transaction t = new Transaction();
		t.setSender(centralBank);
		t.setRecipient(account);
		t.setUnit(base);
		t.setAmount(removeAmount);
		t.setPaid(true);
		t.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		t.setDatePaid(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(t);*/
	}
	
	@Transactional
	public static void debit(String player, String acronym, String amount) throws CurrenciesException {
		Account account = getAccountFromPlayer(player, true);
		Account centralBank = getCentralBank();
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long removeAmount = parseCurrency(currency, amount);
		
		Unit base = Currencies.getInstance().getDatabase().find(Unit.class)
			.where()
			.eq("currency", currency)
			.eq("childUnit", null)
			.findUnique();
		if (base == null) {
			throw new CurrenciesException("Currency " + acronym + " has no base.");
		}
		
		Holding fromHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", account)
			.eq("unit.currency", currency)
			.eq("unit.childUnit", null)
			.findUnique();
		
		if (fromHolding == null) {
			fromHolding = new Holding();
			fromHolding.setAccount(account);
			fromHolding.setUnit(base);
			fromHolding.setAmount(0);
		}
		
		long fromAmount = fromHolding.getAmount() - removeAmount;
		fromHolding.setAmount(fromAmount);
		Currencies.getInstance().getDatabase().save(fromHolding);
		
		Holding toHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", centralBank)
			.eq("unit.currency", currency)
			.eq("unit.childUnit", null)
			.findUnique();
		
		if (toHolding == null) {
			toHolding = new Holding();
			toHolding.setAccount(centralBank);
			toHolding.setUnit(base);
			toHolding.setAmount(0);
		}
		
		long toAmount = toHolding.getAmount() + removeAmount;
		toHolding.setAmount(toAmount);
		Currencies.getInstance().getDatabase().save(toHolding);
		
		/*Transaction t = new Transaction();
		t.setSender(account);
		t.setRecipient(centralBank);
		t.setUnit(base);
		t.setAmount(removeAmount);
		t.setPaid(true);
		t.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		t.setDatePaid(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(t);*/
	}
	
	public static void bankrupt(String player) throws CurrenciesException {
		bankrupt(player, null, null);
	}
	
	public static void bankrupt(String player, String acronym) throws CurrenciesException {
		bankrupt(player, acronym, null);
	}
	
	@Transactional
	public static void bankrupt(String player, String acronym, String amount) throws CurrenciesException {
		Account account = Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("name", player).findUnique();
		
		if (amount == null) {
			// Reset a player's currency to this amount
			Currencies.getInstance().getDatabase().delete(
				Currencies.getInstance().getDatabase().find(Holding.class)
				.where().eq("account", account).findList()
			);
			
			// TODO: Write the rest
		} else if (acronym == null) {
			// Delete all of a player's holdings equal to this currency
			Currencies.getInstance().getDatabase().delete(
					Currencies.getInstance().getDatabase().find(Holding.class)
					.where().eq("account", account).findList()
				);
			
			// TODO: Write the rest
		} else {
			// Delete everything
			Currencies.getInstance().getDatabase().delete(
				Currencies.getInstance().getDatabase().find(Holding.class)
				.where().eq("account", account).findList()
			);
		}
	}
	
	protected static Map<Currency, Long> summateHoldings(List<Holding> holdings) {
		Map<Currency, Long> currencyBaseAmount = new HashMap<>();
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
	
	protected static void compactHoldings(Account account ) {
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
	
	public static Currency getCurrencyFromAmount(Account account, String currency) throws CurrenciesException {
		String[] parts = currency.split("((?<=\\D)|(?=\\D))");
		
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
					if (p.getCurrency().equals(account.getDefaultCurrency())) {
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
	
	public static long parseCurrency(Currency currency, String amount) throws CurrenciesException {
		// http://stackoverflow.com/questions/2206378/how-to-split-a-string-but-also-keep-the-delimiters
		String[] parts = amount.split("((?<=\\D)|(?=\\D))");
		
		if (parts.length == 0 || parts.length == 1) {
			throw new CurrenciesException("Either no symbol or no currency amount was provided.");
		}
		
		long baseAmount = 0;
		
		Unit partUnit = null;
		Long partAmount = null;
		/*Currency c = null;
		
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
					if (p.getCurrency().equals(account.getDefaultCurrency())) {
						c = p.getCurrency();
						break;
					}
				}
			}
		}
		
		if (c == null) {
			throw new CurrenciesException("No prime unit was located in your currency string.");
		}*/
		
		for (String part : parts) {
			if (part.matches("\\D+")) {
				partUnit = Currencies.getInstance().getDatabase().find(Unit.class)
					.where().eq("currency_id", currency.getId()).eq("symbol", part).findUnique();
				if (partUnit == null) {
					throw new CurrenciesException(part + " is not a valid symbol.");
				}
			} else {
				try {
					partAmount = Long.parseLong(part);
				} catch (NumberFormatException e) {
					throw new CurrenciesException(part + " could not be parsed into a number.");
				}
			}
			
			if (partUnit != null && partAmount != null) {
				baseAmount += partUnit.getBaseMultiples() != 0 ? partAmount * partUnit.getBaseMultiples() : 1;
				
				partUnit = null;
				partAmount = null;
			}
		}
		
		return baseAmount;
	}
}
