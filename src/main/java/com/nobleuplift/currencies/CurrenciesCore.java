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

    public static void init(DatabaseManager databaseManager) {
        db = databaseManager;
        ledger = new Ledger(databaseManager, repository);
        formatter = new CurrencyFormatter(databaseManager, repository);
        currencyService = new CurrencyService(databaseManager, repository);
        accountService = new AccountService(databaseManager, repository, currencyService);
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
        return balance(player, null);
    }

    public static Map<Currency, Long> balance(String player, String acronym) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = repository.queryAccountByName(conn, player);
                if (account == null) {
                    throw new CurrenciesException("Account " + player + " does not exist.");
                }

                Map<Currency, Long> result;
                if (acronym == null) {
                    List<Holding> holdings = repository.queryHoldingsWithUnitAndCurrency(conn, account.getId());
                    result = summateHoldings(holdings);
                } else {
                    Currency c = repository.queryCurrencyByAcronym(conn, acronym);
                    if (c == null) {
                        throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                    }

                    List<Holding> holdings = repository.queryHoldingsForAccountAndCurrency(conn, account.getId(), c.getId());
                    if (holdings.isEmpty()) {
                        Unit pu = repository.queryPrimeUnit(conn, c);
                        String unitName = (pu != null) ? pu.getAlternate() : acronym;
                        throw new CurrenciesException("Account " + player + " does not own any " + unitName + ".");
                    }
                    result = summateHoldings(holdings);
                }

                conn.commit();
                return result;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in balance: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in balance: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Pay
    // =========================================================================

    public static Transaction pay(String from, String to, String acronym, String amount) throws CurrenciesException {
        Account fromAccount = accountService.getAccountFromPlayer(from, true);
        Account toAccount = accountService.getAccountFromPlayer(to, true);
        Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);
        long payAmount = formatter.parseCurrency(currency, amount);
        return pay(fromAccount, toAccount, currency, payAmount);
    }

    public static Transaction pay(Account fromAccount, Account toAccount, Currency currency, long baseAmount) throws CurrenciesException {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new CurrenciesException("You cannot pay yourself.");
        }
        if (fromAccount.isReserved()) {
            throw new CurrenciesException("Reserved accounts cannot pay.");
        }
        if (toAccount.isReserved()) {
            throw new CurrenciesException("Cannot pay a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot pay someone a negative amount.");
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ledger.compactHoldings(conn, fromAccount);

                Unit baseUnit = repository.queryBaseUnit(conn, currency);
                if (baseUnit == null) {
                    throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                }

                Holding baseHolding = repository.queryBaseHolding(conn, fromAccount.getId(), baseUnit.getId());
                if (baseHolding == null) {
                    throw new CurrenciesException("You have 0" + baseUnit.getSymbol() + ". You cannot pay "
                            + formatter.formatCurrency(currency, baseAmount) + " to " + toAccount.getName() + ".");
                } else if (baseHolding.getAmount() < baseAmount) {
                    throw new CurrenciesException("Cannot pay " + formatter.formatCurrency(currency, baseAmount) + " to "
                            + toAccount.getName() + " because it is greater than "
                            + formatter.formatCurrency(currency, baseHolding.getAmount()) + ", your current balance.");
                }

                Transaction t = ledger.privateTransferAmount(conn, fromAccount, toAccount, currency, baseAmount);
                t.setTypeId(TransactionType.PAY.getId());
                long txId = ledger.insertTransaction(conn, t);
                t.setId(txId);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in pay: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in pay: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Bill
    // =========================================================================

    public static Transaction bill(String to, String from, String acronym, String amount) throws CurrenciesException {
        Account fromAccount = accountService.getAccountFromPlayer(from, true);
        Account toAccount = accountService.getAccountFromPlayer(to, true);
        Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);
        long billAmount = formatter.parseCurrency(currency, amount);
        return bill(toAccount, fromAccount, currency, billAmount);
    }

    public static Transaction bill(Account toAccount, Account fromAccount, Currency currency, long baseAmount) throws CurrenciesException {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new CurrenciesException("You cannot bill yourself.");
        }
        if (fromAccount.isReserved()) {
            throw new CurrenciesException("Reserved accounts cannot bill.");
        }
        if (toAccount.isReserved()) {
            throw new CurrenciesException("Cannot bill a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot bill someone a negative amount.");
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Unit base = repository.queryBaseUnit(conn, currency);
                if (base == null) {
                    throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                }

                Timestamp now = Clock.now();

                // bill: sender=fromAccount (the biller), recipient=toAccount (the one being billed),
                // paid=NULL (pending)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_transaction"
                        + " (sender_id, recipient_id, unit_id, type_id, transaction_amount,"
                        + "  final_sender_amount, final_recipient_amount, paid, date_paid, date_created)"
                        + " VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, fromAccount.getId());
                    ps.setInt(2, toAccount.getId());
                    ps.setShort(3, base.getId());
                    ps.setShort(4, TransactionType.BILL.getId());
                    ps.setLong(5, baseAmount);
                    ps.setTimestamp(6, now);
                    ps.executeUpdate();

                    long txId;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new CurrenciesException("Failed to insert bill transaction.");
                        }
                        txId = keys.getLong(1);
                    }

                    conn.commit();

                    Transaction t = new Transaction();
                    t.setId(txId);
                    t.setSender(fromAccount);
                    t.setRecipient(toAccount);
                    t.setUnit(base);
                    t.setTypeId(TransactionType.BILL.getId());
                    t.setTransactionAmount(baseAmount);
                    t.setFinalSenderAmount(null);
                    t.setFinalRecipientAmount(null);
                    t.setPaid(null);
                    t.setDateCreated(now);
                    t.setDatePaid(null);
                    return t;
                }
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in bill: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in bill: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Process Bill
    // =========================================================================

    public static Transaction processBill(String from, boolean pay) throws CurrenciesException {
        return processBill(from, pay, null);
    }

    public static Transaction processBill(String from, boolean pay, String transaction) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = repository.queryAccountByName(conn, from);
                if (account == null) {
                    throw new CurrenciesRuntimeException("Account " + from + " does not exist.");
                }

                Transaction t = null;
                if (transaction == null) {
                    // Find all pending bills where this account is the sender (the one who must pay)
                    List<Transaction> pendingBills = repository.queryPendingBillsForSender(conn, account.getId());
                    if (pendingBills.size() > 1) {
                        throw new CurrenciesException(
                                "You have more than one bill pending. Please specify the transaction ID. You can find it by running /transactions.");
                    } else if (pendingBills.size() == 0) {
                        throw new CurrenciesException("You have no bills pending. ");
                    } else {
                        t = pendingBills.get(0);
                    }
                } else {
                    t = repository.queryTransactionById(conn, Long.parseLong(transaction));
                    if (t == null) {
                        throw new CurrenciesException("Transaction " + transaction + " does not exist.");
                    }
                    if (!account.getId().equals(t.getSender().getId())) {
                        throw new CurrenciesException("You can only pay/reject bills sent to yourself.");
                    }
                }

                if (t.getTypeId() != TransactionType.BILL.getId()) {
                    throw new CurrenciesException("Transaction is not a bill.");
                }
                if (t.isPaid() != null) {
                    throw new CurrenciesException("Bill has already been " + (t.isPaid() ? "paid." : "rejected."));
                }

                if (pay) {
                    // Only actually transfer funds when paying
                    ledger.compactHoldings(conn, account);

                    Currency currency = t.getUnit().getCurrency();
                    Unit baseUnit = repository.queryBaseUnit(conn, currency);
                    if (baseUnit == null) {
                        throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                    }

                    Holding baseHolding = repository.queryBaseHolding(conn, account.getId(), baseUnit.getId());
                    if (baseHolding == null) {
                        throw new CurrenciesException("You have 0" + baseUnit.getSymbol() + ". You cannot pay "
                                + formatter.formatCurrency(currency, t.getTransactionAmount()) + " to " + t.getRecipient().getName() + ".");
                    } else if (baseHolding.getAmount() < t.getTransactionAmount()) {
                        throw new CurrenciesException("Cannot pay " + formatter.formatCurrency(currency, t.getTransactionAmount())
                                + " to " + t.getRecipient().getName() + " because it is greater than "
                                + formatter.formatCurrency(currency, baseHolding.getAmount()) + ", your current balance.");
                    }

                    ledger.privateTransferAmount(conn, t.getSender(), t.getRecipient(), currency, t.getTransactionAmount());
                }

                // Update the existing bill transaction with paid status
                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_transaction SET paid = ?, date_paid = ? WHERE id = ?")) {
                    ps.setBoolean(1, pay);
                    ps.setTimestamp(2, now);
                    ps.setLong(3, t.getId());
                    ps.executeUpdate();
                }

                t.setPaid(pay);
                t.setDatePaid(now);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in processBill: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in processBill: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Transactions list
    // =========================================================================

    public static List<Transaction> transactions(String player) throws CurrenciesException {
        return transactions(player, 1);
    }

    public static List<Transaction> transactions(String player, int page) throws CurrenciesException {
        int offset = (page <= 1) ? 0 : (page - 1) * 10;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = repository.queryAccountByName(conn, player);
                if (account == null) {
                    throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
                }

                List<Transaction> result = repository.queryTransactionsForAccountPage(conn, account.getId(), offset);

                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in transactions: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in transactions: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Credit / Debit
    // =========================================================================

    public static Transaction credit(String player, String acronym, String amount) throws CurrenciesException {
        Account account = accountService.getAccountFromPlayer(player, true);
        Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);
        long baseAmount = formatter.parseCurrency(currency, amount);
        return credit(account, currency, baseAmount);
    }

    public static Transaction credit(Account account, Currency currency, long baseAmount) throws CurrenciesException {
        if (account.isReserved()) {
            throw new CurrenciesException("Cannot credit a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot credit someone a negative amount.");
        }

        Account bank = accountService.getMinecraftCentralBank();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction t = ledger.privateTransferAmount(conn, bank, account, currency, baseAmount);
                t.setTypeId(TransactionType.CREDIT.getId());
                long txId = ledger.insertTransaction(conn, t);
                t.setId(txId);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in credit: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in credit: " + e.getMessage(), e);
        }
    }

    public static Transaction debit(String player, String acronym, String amount) throws CurrenciesException {
        Account account = accountService.getAccountFromPlayer(player, true);
        Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);
        long baseAmount = formatter.parseCurrency(currency, amount);
        return debit(account, currency, baseAmount);
    }

    public static Transaction debit(Account account, Currency currency, long baseAmount) throws CurrenciesException {
        if (account.isReserved()) {
            throw new CurrenciesException("Cannot debit a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot debit someone a negative amount.");
        }

        Account bank = accountService.getMinecraftCentralBank();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction t = ledger.privateTransferAmount(conn, account, bank, currency, baseAmount);
                t.setTypeId(TransactionType.DEBIT.getId());
                long txId = ledger.insertTransaction(conn, t);
                t.setId(txId);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in debit: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in debit: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Bankrupt
    // =========================================================================

    public static void bankrupt(String player) throws CurrenciesException {
        bankrupt(player, null, null);
    }

    public static void bankrupt(String player, String acronym) throws CurrenciesException {
        bankrupt(player, acronym, null);
    }

    public static List<Holding> bankrupt(String player, String acronym, String amount) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = repository.queryAccountByName(conn, player);
                if (account == null) {
                    throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
                }
                Account centralBanker = repository.queryAccountById(conn, MINECRAFT_CENTRAL_BANKER);
                Account centralBank = repository.queryAccountById(conn, MINECRAFT_CENTRAL_BANK);

                List<Holding> holdings;

                if (amount != null) {
                    Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);
                    long bankruptAmount = formatter.parseCurrency(currency, amount);

                    ledger.compactHoldings(conn, account);

                    holdings = repository.queryHoldingsForAccountAndCurrency(conn, account.getId(), currency.getId());

                    for (Holding h : holdings) {
                        if (h.getAmount() == 0) {
                            repository.deleteHolding(conn, account.getId(), h.getUnit().getId());
                            continue;
                        }
                        Transaction t = ledger.privateTransferAmount(conn, account, centralBanker, currency, h.getAmount());
                        t.setTypeId(TransactionType.BANKRUPT.getId());
                        ledger.insertTransaction(conn, t);
                    }

                    Transaction creditT = ledger.privateTransferAmount(conn, centralBank, account, currency, bankruptAmount);
                    creditT.setTypeId(TransactionType.CREDIT.getId());
                    ledger.insertTransaction(conn, creditT);

                } else if (acronym != null) {
                    Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);

                    holdings = repository.queryHoldingsForAccountAndCurrency(conn, account.getId(), currency.getId());

                    for (Holding h : holdings) {
                        if (h.getAmount() == 0) {
                            repository.deleteHolding(conn, account.getId(), h.getUnit().getId());
                            continue;
                        }
                        Transaction t = ledger.privateTransferAmount(conn, account, centralBanker, currency, h.getAmount());
                        t.setTypeId(TransactionType.BANKRUPT.getId());
                        ledger.insertTransaction(conn, t);
                    }

                } else {
                    // Delete all holdings — need unit+currency populated
                    holdings = repository.queryHoldingsWithUnitAndCurrency(conn, account.getId());

                    for (Holding h : holdings) {
                        if (h.getAmount() == 0) {
                            repository.deleteHolding(conn, account.getId(), h.getUnit().getId());
                            continue;
                        }
                        Currency hCurrency = h.getUnit().getCurrency();
                        Transaction t = ledger.privateTransferAmount(conn, account, centralBanker, hCurrency, h.getAmount());
                        t.setTypeId(TransactionType.BANKRUPT.getId());
                        ledger.insertTransaction(conn, t);
                    }
                }

                conn.commit();
                return holdings;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in bankrupt: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in bankrupt: " + e.getMessage(), e);
        }
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
    // Protected Utility Methods
    // =========================================================================

    protected static Map<Currency, Long> summateHoldings(List<Holding> holdings) {
        Map<Currency, Long> currencyBaseAmount = new HashMap<>();

        if (holdings.isEmpty()) {
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