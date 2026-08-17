package com.nobleuplift.currencies;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionType;
import com.nobleuplift.currencies.entities.Unit;
import com.nobleuplift.currencies.service.AccountService;
import com.nobleuplift.currencies.service.Clock;
import com.nobleuplift.currencies.service.CurrencyFormatter;
import com.nobleuplift.currencies.service.CurrencyRepository;
import com.nobleuplift.currencies.service.CurrencyService;
import com.nobleuplift.currencies.service.JdbcCurrencyRepository;
import com.nobleuplift.currencies.service.Ledger;
import com.nobleuplift.currencies.service.TransactionService;

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

    private static DatabaseManager db;
    private static final CurrencyRepository repository = new JdbcCurrencyRepository();
    private static Ledger ledger;
    private static CurrencyFormatter formatter;
    private static CurrencyService currencyService;
    private static AccountService accountService;
    private static TransactionService transactionService;

    public static void init(DatabaseManager databaseManager) {
        db = databaseManager;
        ledger = new Ledger(databaseManager, repository);
        formatter = new CurrencyFormatter(databaseManager, repository);
        currencyService = new CurrencyService(databaseManager, repository);
        accountService = new AccountService(databaseManager, repository, currencyService);
        transactionService = new TransactionService(databaseManager, repository, ledger, accountService, formatter, currencyService);
    }

    // =========================================================================
    // Currency management
    // =========================================================================

    public static void createCurrency(String acronym, String name) throws CurrenciesException {
        currencyService.createCurrency(acronym, name);
    }

    public static void createCurrency(String acronym, String name, boolean prefix) throws CurrenciesException {
        currencyService.createCurrency(acronym, name, prefix);
    }

    public static void deleteCurrency(String acronym) throws CurrenciesException {
        currencyService.deleteCurrency(acronym);
    }

    public static void addPrime(String acronym, String name, String plural, String symbol) throws CurrenciesException {
        currencyService.addPrime(acronym, name, plural, symbol);
    }

    public static void addParent(String acronym, String name, String plural, String symbol, int multiplier, String child) throws CurrenciesException {
        currencyService.addParent(acronym, name, plural, symbol, multiplier, child);
    }

    public static void addChild(String acronym, String name, String plural, String symbol, int divisor, String parent) throws CurrenciesException {
        currencyService.addChild(acronym, name, plural, symbol, divisor, parent);
    }

    public static List<Currency> list() throws CurrenciesException {
        return currencyService.list();
    }

    public static List<Currency> list(int page) throws CurrenciesException {
        return currencyService.list(page);
    }

    // =========================================================================
    // Account management
    // =========================================================================

    public static Account openAccount(String name, String owner) throws CurrenciesException {
        return accountService.openAccount(name, owner);
    }

    public static void setDefault(String player, String acronym) throws CurrenciesException {
        accountService.setDefault(player, acronym);
    }

    // =========================================================================
    // Balance
    // =========================================================================

    public static Map<Currency, Long> balance(String player) throws CurrenciesException {
        return transactionService.balance(player);
    }

    public static Map<Currency, Long> balance(String player, String acronym) throws CurrenciesException {
        return transactionService.balance(player, acronym);
    }

    // =========================================================================
    // Pay
    // =========================================================================

    public static Transaction pay(String from, String to, String acronym, String amount) throws CurrenciesException {
        return transactionService.pay(from, to, acronym, amount);
    }

    public static Transaction pay(Account fromAccount, Account toAccount, Currency currency, long baseAmount) throws CurrenciesException {
        return transactionService.pay(fromAccount, toAccount, currency, baseAmount);
    }

    // =========================================================================
    // Bill
    // =========================================================================

    public static Transaction bill(String to, String from, String acronym, String amount) throws CurrenciesException {
        return transactionService.bill(to, from, acronym, amount);
    }

    public static Transaction bill(Account toAccount, Account fromAccount, Currency currency, long baseAmount) throws CurrenciesException {
        return transactionService.bill(toAccount, fromAccount, currency, baseAmount);
    }

    // =========================================================================
    // Process Bill
    // =========================================================================

    public static Transaction processBill(String from, boolean pay) throws CurrenciesException {
        return transactionService.processBill(from, pay);
    }

    public static Transaction processBill(String from, boolean pay, String transaction) throws CurrenciesException {
        return transactionService.processBill(from, pay, transaction);
    }

    // =========================================================================
    // Transactions list
    // =========================================================================

    public static List<Transaction> transactions(String player) throws CurrenciesException {
        return transactionService.transactions(player);
    }

    public static List<Transaction> transactions(String player, int page) throws CurrenciesException {
        return transactionService.transactions(player, page);
    }

    // =========================================================================
    // Credit / Debit
    // =========================================================================

    public static Transaction credit(String player, String acronym, String amount) throws CurrenciesException {
        return transactionService.credit(player, acronym, amount);
    }

    public static Transaction credit(Account account, Currency currency, long baseAmount) throws CurrenciesException {
        return transactionService.credit(account, currency, baseAmount);
    }

    public static Transaction debit(String player, String acronym, String amount) throws CurrenciesException {
        return transactionService.debit(player, acronym, amount);
    }

    public static Transaction debit(Account account, Currency currency, long baseAmount) throws CurrenciesException {
        return transactionService.debit(account, currency, baseAmount);
    }

    // =========================================================================
    // Bankrupt
    // =========================================================================

    public static void bankrupt(String player) throws CurrenciesException {
        transactionService.bankrupt(player);
    }

    public static void bankrupt(String player, String acronym) throws CurrenciesException {
        transactionService.bankrupt(player, acronym);
    }

    public static List<Holding> bankrupt(String player, String acronym, String amount) throws CurrenciesException {
        return transactionService.bankrupt(player, acronym, amount);
    }

    // =========================================================================
    // Loader Methods
    // =========================================================================

    public static Account getMinecraftCentralBank() {
        return accountService.getMinecraftCentralBank();
    }

    public static Account getMinecraftCentralBanker() {
        return accountService.getMinecraftCentralBanker();
    }

    public static Account getTheEndermanMarket() {
        return accountService.getTheEndermanMarket();
    }

    public static Account getTheEndermanMarketeer() {
        return accountService.getTheEndermanMarketeer();
    }

    /**
     * Player is not guaranteed to match the name in the database if someone changed their name and
     * another person took that name.
     *
     * However, the account's name will be updated on that account's login, so Currencies doesn't
     * need to be rewritten to use UUIDs.
     */
    public static Account getAccountFromPlayer(String player, boolean exception) throws CurrenciesRuntimeException {
        return accountService.getAccountFromPlayer(player, exception);
    }

    public static Account getAccountFromUniqueId(String uuid, boolean exception) throws CurrenciesRuntimeException {
        return accountService.getAccountFromUniqueId(uuid, exception);
    }

    public static Currency getCurrency(short id, boolean exception) throws CurrenciesRuntimeException {
        return currencyService.getCurrency(id, exception);
    }

    public static Currency getCurrencyFromAcronym(String acronym, boolean exception) throws CurrenciesRuntimeException {
        return currencyService.getCurrencyFromAcronym(acronym, exception);
    }

    public static Unit getUnit(short id, boolean exception) throws CurrenciesRuntimeException {
        return currencyService.getUnit(id, exception);
    }

    public static Unit getBaseUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        return currencyService.getBaseUnit(currency, exception);
    }

    public static Unit getPrimeUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        return currencyService.getPrimeUnit(currency, exception);
    }

    public static Map<Short, Unit> getUnits(Currency currency) {
        return currencyService.getUnits(currency);
    }

    // =========================================================================
    // Public Utility Methods
    // =========================================================================

    public static Map<Currency, String> formatCurrencies(Map<Currency, Long> currencyAmounts) {
        return formatter.formatCurrencies(currencyAmounts);
    }

    public static String formatCurrency(Currency currency, long amount) {
        return formatter.formatCurrency(currency, amount);
    }

    public static long parseCurrency(Currency currency, String amount) throws CurrenciesException {
        return formatter.parseCurrency(currency, amount);
    }

    public static Currency getCurrencyFromAmount(Account account, String amount) throws CurrenciesException {
        return formatter.getCurrencyFromAmount(account, amount);
    }

    /**
     * Resolves an amount string whose currency is ambiguous: the currency is inferred from a prime unit
     * symbol embedded in the string (disambiguated via the account's default currency if the symbol is
     * shared by more than one currency), and the total base-unit amount is computed against that currency
     * in the same pass. Callers that previously called getCurrencyFromAmount() and then parseCurrency()
     * separately can use this instead to avoid resolving the currency and parsing the string twice.
     */
    public static CurrencyDTO resolveCurrency(Account account, String amount) throws CurrenciesException {
        return formatter.resolveCurrency(account, amount);
    }

    /**
     * Public transferAmount — opens its own connection and delegates to the private version.
     * Returns a Transaction POJO with amounts set; the record is also inserted into the DB.
     */
    public static Transaction transferAmount(Account fromAccount, Account toAccount, Currency currency, long amount) throws CurrenciesException {
        return ledger.transferAmount(fromAccount, toAccount, currency, amount);
    }

}