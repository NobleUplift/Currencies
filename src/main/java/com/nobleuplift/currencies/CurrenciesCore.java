package com.nobleuplift.currencies;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.avaje.ebean.annotation.Transactional;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holder;
import com.nobleuplift.currencies.entities.HolderPK;
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
	public static final int MINECRAFT_CENTRAL_BANK = 1;
	public static final int MINECRAFT_CENTRAL_BANKER = 2;
	public static final int THE_ENDERMAN_MARKET = 3;
	public static final int THE_ENDERMAN_MARKETEER = 4;
	
	public static final short TRANSACTION_TYPE_PAY_ID = 1;
	public static final short TRANSACTION_TYPE_BILL_ID = 2;
	public static final short TRANSACTION_TYPE_CREDIT_ID = 3;
	public static final short TRANSACTION_TYPE_DEBIT_ID = 4;
	public static final short TRANSACTION_TYPE_BANKRUPT_ID = 5;
	
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

	@Transactional
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
	public static Account openAccount(String name, String owner) throws CurrenciesException {
		if (name.length() <= 16) {
			throw new CurrenciesException("Non-player accounts must be longer than 16 characters.");
		}
		
		Account nameAccount = Currencies.getInstance().getDatabase().find(Account.class)
			.where()
			.eq("name", name)
			.findUnique();
		if (nameAccount != null) {
			throw new CurrenciesException("Account with name " + name + " already exists.");
		}
		
		Account account = new Account();
		// TODO: See if this name is in-use by a Minecraft account to move check out of event
		account.setName(name);
		account.setUuid(null);
		account.setDefaultCurrency(null);
		account.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		account.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		Currencies.getInstance().getDatabase().save(account);
		
		Account ownerAccount = Currencies.getInstance().getDatabase().find(Account.class)
			.where()
			.eq("name", owner)
			.findUnique();
		
		if (ownerAccount == null) {
			throw new CurrenciesException("Owner " + owner + " does not exist.");
		}
		
		account = Currencies.getInstance().getDatabase().find(Account.class)
			.where()
			.eq("name", name)
			.findUnique();
		
		Holder root = new Holder();
		HolderPK rpk = new HolderPK();
		rpk.setParentAccountId(account.getId());
		rpk.setChildAccountId(account.getId());
		root.setId(rpk);
		root.setLength((short) 0);
		Currencies.getInstance().getDatabase().save(root);
		
		Holder h = new Holder();
		HolderPK hpk = new HolderPK();
		hpk.setParentAccountId(ownerAccount.getId());
		hpk.setChildAccountId(account.getId());
		h.setId(hpk);
		h.setLength((short) 1);
		Currencies.getInstance().getDatabase().save(h);
		
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
		
		if (payAmount <= 0) {
			throw new CurrenciesException("Cannot pay someone a negative amount.");
		}
		
		compactHoldings(fromAccount);
		
		Unit baseUnit = getBaseUnit(currency);
		Holding baseHoldings = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", fromAccount)
			.eq("unit", baseUnit)
			.findUnique();
		if (baseHoldings == null) {
			throw new CurrenciesException("You have 0" + baseUnit.getSymbol() + ". You cannot pay " + amount + " to " + to + ".");
		} else if (baseHoldings.getAmount() < payAmount) {
			throw new CurrenciesException("Cannot pay " + amount + " to " + to + " because it is greater than " + 
				formatCurrency(currency, baseHoldings.getAmount()) + ", your current balance.");
		}
		
		Transaction t = transferAmount(fromAccount, toAccount, currency, payAmount);
		t.setTypeId(TRANSACTION_TYPE_PAY_ID);
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
		
		if (billAmount <= 0) {
			throw new CurrenciesException("Cannot bill someone a negative amount.");
		}
		
		Transaction t = new Transaction();
		t.setSender(fromAccount);
		t.setRecipient(toAccount);
		t.setUnit(base);
		t.setTypeId(TRANSACTION_TYPE_BILL_ID);
		t.setTransactionAmount(billAmount);
		t.setFinalSenderAmount(null);
		t.setFinalRecipientAmount(null);
		t.setPaid(null);
		t.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
		t.setDatePaid(null);
		Currencies.getInstance().getDatabase().save(t);
		
		return t;
	}
	
	public static Transaction processBill(String from, boolean pay) throws CurrenciesException {
		return processBill(from, pay, null);
	}
	
	@Transactional
	public static Transaction processBill(String from, boolean pay, String transaction) throws CurrenciesException {
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
			
			if (account.getId() != t.getSender().getId()) {
				throw new CurrenciesException("You can only pay/reject bills sent to yourself.");
			}
		}
		
		if (t.getTypeId() != TRANSACTION_TYPE_BILL_ID) {
			throw new CurrenciesException("Transaction is not a bill.");
		}
		
		if (t.getPaid() != null) {
			throw new CurrenciesException("Bill has already been " + (t.getPaid() ? "paid." : "rejected."));
		}
		
		compactHoldings(account);
		
		Currency currency = t.getUnit().getCurrency();
		Unit baseUnit = getBaseUnit(currency);
		Holding baseHoldings = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", account)
			.eq("unit", baseUnit)
			.findUnique();
		if (baseHoldings == null) {
			throw new CurrenciesException("You have 0" + baseUnit.getSymbol() + ". You cannot pay " + formatCurrency(currency, t.getTransactionAmount()) + " to " + t.getRecipient().getName() + ".");
		} else if (baseHoldings.getAmount() < t.getTransactionAmount()) {
			throw new CurrenciesException("Cannot pay " + formatCurrency(currency, t.getTransactionAmount()) + " to " + t.getRecipient().getName() + " because it is greater than " + 
				formatCurrency(currency, baseHoldings.getAmount()) + ", your current balance.");
		}
		
		transferAmount(t.getSender(), t.getRecipient(), t.getUnit().getCurrency(), t.getTransactionAmount());
		t.setPaid(pay);
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
		Account bank = getMinecraftCentralBank();
		Account account = getAccountFromPlayer(player, true);
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long addAmount = parseCurrency(currency, amount);
		
		if (account.getId() >= 1 && account.getId() <= 4) {
			throw new CurrenciesException("Cannot credit a reserved account.");
		}
		
		if (addAmount <= 0) {
			throw new CurrenciesException("Cannot credit someone a negative amount.");
		}
		
		Transaction t = transferAmount(bank, account, currency, addAmount);
		t.setTypeId(TRANSACTION_TYPE_CREDIT_ID);
		Currencies.getInstance().getDatabase().save(t);
		return t;
	}
	
	@Transactional
	public static Transaction debit(String player, String acronym, String amount) throws CurrenciesException {
		Account account = getAccountFromPlayer(player, true);
		Account bank = getMinecraftCentralBank();
		Currency currency = getCurrencyFromAcronym(acronym, true);
		long removeAmount = parseCurrency(currency, amount);
		
		if (account.getId() >= 1 && account.getId() <= 4) {
			throw new CurrenciesException("Cannot debit a reserved account.");
		}
		
		if (removeAmount <= 0) {
			throw new CurrenciesException("Cannot debit someone a negative amount.");
		}
		
		Transaction t = transferAmount(account, bank, currency, removeAmount);
		t.setTypeId(TRANSACTION_TYPE_DEBIT_ID);
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
		Account centralBank = getMinecraftCentralBank();
		Account centralBanker = getMinecraftCentralBanker();
		
		if (amount != null) {
			Currency currency = getCurrencyFromAcronym(acronym, true);
			//Unit base = getBaseUnit(currency);
			long bankruptAmount = parseCurrency(currency, amount);
			
			compactHoldings(account);
			
			// Reset a player's currency to this amount
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where()
				.eq("account", account)
				.eq("unit.currency", currency)
				.findList();
			
			for (Holding h : holdings) {
				if (h.getAmount() == 0) {
					Currencies.getInstance().getDatabase().delete(h);
					continue;
				}
				
				Transaction t = transferAmount(account, centralBanker, currency, h.getAmount());
				t.setTypeId(TRANSACTION_TYPE_BANKRUPT_ID);
				Currencies.getInstance().getDatabase().save(t);
			}
			
			//Currencies.getInstance().getDatabase().delete(holdings);
			
			Transaction t = transferAmount(centralBank, account, currency, bankruptAmount);
			t.setTypeId(TRANSACTION_TYPE_CREDIT_ID);
			Currencies.getInstance().getDatabase().save(t);
			
			/*Holding h = new Holding();
			HoldingPK pk = new HoldingPK();
			pk.setAccountId(account.getId());
			pk.setUnitId(base.getId());
			h.setAmount(bankruptAmount);
			Currencies.getInstance().getDatabase().save(h);*/
			
			return holdings;
		} else if (acronym != null) {
			Currency currency = getCurrencyFromAcronym(acronym, true);
			
			// Delete all of a player's holdings equal to this currency
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where()
				.eq("account", account)
				.eq("unit.currency", currency)
				.findList();
			
			for (Holding h : holdings) {
				if (h.getAmount() == 0) {
					Currencies.getInstance().getDatabase().delete(h);
					continue;
				}
				
				Transaction t = transferAmount(account, centralBanker, currency, h.getAmount());
				t.setTypeId(TRANSACTION_TYPE_BANKRUPT_ID);
				Currencies.getInstance().getDatabase().save(t);
			}
			
			//Currencies.getInstance().getDatabase().delete(holdings);
			
			return holdings;
		} else {
			// Delete everything
			List<Holding> holdings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where().eq("account", account).findList();
			
			for (Holding h : holdings) {
				if (h.getAmount() == 0) {
					Currencies.getInstance().getDatabase().delete(h);
					continue;
				}
				
				Transaction t = transferAmount(account, centralBanker, h.getUnit().getCurrency(), h.getAmount());
				t.setTypeId(TRANSACTION_TYPE_BANKRUPT_ID);
				Currencies.getInstance().getDatabase().save(t);
			}
			
			//Currencies.getInstance().getDatabase().delete(holdings);
			
			return holdings;
		}
	}
	
	/*
	 * 
	 * Loader Methods
	 * 
	 */
	
	public static Account getMinecraftCentralBank() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", CurrenciesCore.MINECRAFT_CENTRAL_BANK).findUnique();
	}
	
	public static Account getMinecraftCentralBanker() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", CurrenciesCore.MINECRAFT_CENTRAL_BANKER).findUnique();
	}
	
	public static Account getTheEndermanMarket() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", CurrenciesCore.THE_ENDERMAN_MARKET).findUnique();
	}
	
	public static Account getTheEndermanMarketeer() {
		return Currencies.getInstance().getDatabase().find(Account.class)
			.where().eq("id", CurrenciesCore.THE_ENDERMAN_MARKETEER).findUnique();
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
	
	public static Unit getBaseUnit(Currency currency) {
		Unit base = Currencies.getInstance().getDatabase().find(Unit.class)
			.where()
			.eq("currency", currency)
			.isNull("childUnit")
			.findUnique();
		//if (base == null) {
		//	throw new CurrenciesException("Currency " + currency.getAcronym() + " has no base.");
		//}
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
	
	private static int compactHoldings(Account account) {
		List<Holding> nonBaseHoldings = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", account)
			.isNotNull("unit.childUnit")
			.findList();
		if (Currencies.DEBUG) {
			System.out.println("Non-base holdings: " + nonBaseHoldings);
		}
		
		if (nonBaseHoldings.size() != 0) {
			if (Currencies.DEBUG) {
				System.out.println("Non-base holdings count: " + nonBaseHoldings.size());
			}
			
			List<Holding> baseHoldings = Currencies.getInstance().getDatabase().find(Holding.class)
				.where()
				.eq("account", account)
				.isNull("unit.childUnit")
				.findList();
			if (Currencies.DEBUG) {
				System.out.println("Base holdings: " + nonBaseHoldings);
			}
			
			Map<Currency, Holding> holdingsByCurrency = new HashMap<>();
			for (Holding h : baseHoldings) {
				Currency c = h.getUnit().getCurrency();
				holdingsByCurrency.put(c, h);
			}
			
			for (Holding h : nonBaseHoldings) {
				if (h.getAmount() == 0) {
					Currencies.getInstance().getDatabase().delete(h);
					continue;
				}
				
				Unit nonBaseUnit = h.getUnit();
				Unit baseUnit = getBaseUnit(nonBaseUnit.getCurrency());
				
				Holding baseHolding = holdingsByCurrency.get(nonBaseUnit.getCurrency());
				if (baseHolding == null) {
					baseHolding = new Holding();
					HoldingPK baseHoldingPK = new HoldingPK();
					baseHoldingPK.setAccountId(account.getId());
					baseHoldingPK.setUnitId(baseUnit.getId());
					baseHolding.setAmount(0);
				}
				
				long baseAmount = h.getAmount() * h.getUnit().getBaseMultiples();
				if (Currencies.DEBUG) {
					System.out.println("Base Holdings Amount: " + baseHolding.getAmount());
					System.out.println("Base Amount: " + baseAmount);
				}
				
				baseHolding.setAmount(baseHolding.getAmount() + baseAmount);
				Currencies.getInstance().getDatabase().save(baseHolding);
				Currencies.getInstance().getDatabase().delete(h);
			}
		}
		
		return nonBaseHoldings.size();
		
		/*
		 * First, create a map of each currency to all holdings
		 * with units in that currency.
		 */
		/*Map<Currency, List<Holding>> holdingsByCurrency = new HashMap<>();
		for (Holding h : account.getHoldings()) {
			Currency c = h.getUnit().getCurrency();
			
			if (holdingsByCurrency.containsKey(c)) {
				List<Holding> holdingsList = holdingsByCurrency.get(c);
				holdingsList.add(h);
			} else {
				List<Holding> holdingsList = new ArrayList<>();
				holdingsList.add(h);
				holdingsByCurrency.put(c, holdingsList);
			}
		}*/
		
		/*
		 * Process each of the currencies, and do extra work when more than
		 * one unit is found.
		 */
		/*for (Map.Entry<Currency, List<Holding>> entry : holdingsByCurrency.entrySet()) {
			Currency c = entry.getKey();
			List<Holding> holdings = entry.getValue();
			
			Unit baseUnit = getBaseUnit(c);
			if (holdings.size() > 1) {
				Holding baseHolding = null;
				Iterator<Holding> i = holdings.iterator();
				while (i.hasNext()) {
					Holding next = i.next();
					if (next.getUnit().getId() == baseUnit.getId()) {
						baseHolding = next;
						i.remove();
					}
				}
				
				if (baseHolding == null) {
					baseHolding = new Holding();
					HoldingPK baseHoldingPK = new HoldingPK();
					baseHoldingPK.setAccountId(account.getId());
					baseHoldingPK.setUnitId(baseUnit.getId());
					baseHolding.setAmount(0);
				}
				
				i = holdings.iterator();
				while (i.hasNext()) {
					Holding next = i.next();
					long baseAmount = next.getAmount() * next.getUnit().getBaseMultiples();
					baseHolding.setAmount(baseHolding.getAmount() + baseAmount);
					Currencies.getInstance().getDatabase().delete(next);
					i.remove();
				}
			}
		}*/
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
	
	public static String formatCurrency(Currency currency, long amount) {
		List<Unit> units = Currencies.getInstance().getDatabase().find(Unit.class).where()
			.eq("currency", currency).eq("main", true).orderBy().desc("base_multiples").findList();
		
		String retval = "";
		if (amount < 0) {
			retval += "-";
			amount = Math.abs(amount);
		}
		Unit prime = null;
		long remainder = amount;
		for (Unit u : units) {
			if (u.isPrime()) {
				prime = u;
			}
			
			if (u.getBaseMultiples() > 0) {
				long quotient = remainder / u.getBaseMultiples();
				if (quotient == 0) {
					continue;
				}
				
				if (currency.getPrefix()) {
					retval += u.getSymbol() + quotient;
				} else {
					retval += quotient + u.getSymbol();
				}
				remainder = remainder % u.getBaseMultiples();
			} else if (remainder != 0) {
				if (currency.getPrefix()) {
					retval += u.getSymbol() + remainder;
				} else {
					retval += remainder + u.getSymbol();
				}
			}
		}
		
		if (amount == 0 && prime != null) {
			retval += currency.isPrefix() ? prime.getSymbol() + "0" : "0" + prime.getSymbol();
		}
		
		return retval;
	}
	
	public static long parseCurrency(Currency currency, String amount) throws CurrenciesException {
		boolean isNegative = false;
		if (amount.matches("(^-).*")) {
			isNegative = true;
			amount = amount.replaceAll("(^-)", "");
			if (Currencies.DEBUG) {
				Currencies.getInstance().getLogger().info("PARSED CURRENCY WILL BE NEGATIVE: " + amount);
			}
		}
		
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
					partAmount = Math.abs(Long.parseLong(part));
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
		
		return isNegative ? baseAmount * -1 : baseAmount;
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
			.isNull("unit.childUnit")
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
		if (fromAmount == 0) {
			Currencies.getInstance().getDatabase().delete(fromHolding);
		} else {
			Currencies.getInstance().getDatabase().save(fromHolding);
		}
		
		Holding toHolding = Currencies.getInstance().getDatabase().find(Holding.class)
			.where()
			.eq("account", toAccount)
			.eq("unit.currency", currency)
			.isNull("unit.childUnit")
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
