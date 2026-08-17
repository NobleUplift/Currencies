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
import com.nobleuplift.currencies.service.Clock;
import com.nobleuplift.currencies.service.CurrencyFormatter;
import com.nobleuplift.currencies.service.CurrencyRepository;
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

    public static void init(DatabaseManager databaseManager) {
        db = databaseManager;
        ledger = new Ledger(databaseManager, repository);
        formatter = new CurrencyFormatter(databaseManager, repository);
    }

    // =========================================================================
    // Currency management
    // =========================================================================

    public static void createCurrency(String acronym, String name) throws CurrenciesException {
        createCurrency(acronym, name, true);
    }

    public static void createCurrency(String acronym, String name, boolean prefix) throws CurrenciesException {
        if (acronym.length() != 3) {
            throw new CurrenciesException("All currency acronyms must be three characters.");
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check acronym uniqueness
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_currency WHERE acronym = ?")) {
                    ps.setString(1, acronym);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new CurrenciesException(acronym + " has been taken by another currency.");
                        }
                    }
                }
                // Check name uniqueness
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_currency WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new CurrenciesException(name + " has been taken by another currency.");
                        }
                    }
                }

                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_currency (name, acronym, prefix, default_currency, date_created, date_modified, date_deleted)"
                        + " VALUES (?, ?, ?, 0, ?, ?, NULL)")) {
                    ps.setString(1, name);
                    ps.setString(2, acronym);
                    ps.setBoolean(3, prefix);
                    ps.setTimestamp(4, now);
                    ps.setTimestamp(5, now);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in createCurrency: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in createCurrency: " + e.getMessage(), e);
        }
    }

    public static void deleteCurrency(String acronym) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = repository.queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Could not find currency with acronym " + acronym + ".");
                }

                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_currency SET date_deleted = ?, date_modified = ? WHERE id = ?")) {
                    ps.setTimestamp(1, now);
                    ps.setTimestamp(2, now);
                    ps.setShort(3, c.getId());
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in deleteCurrency: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in deleteCurrency: " + e.getMessage(), e);
        }
    }

    public static void addPrime(String acronym, String name, String plural, String symbol) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = repository.queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                }

                // Check no prime unit already exists
                Unit existingPrime = repository.queryPrimeUnit(conn, c);
                if (existingPrime != null) {
                    throw new CurrenciesException("Currency " + acronym + " already has a prime unit of currency.");
                }

                if (symbol.length() > 2) {
                    throw new CurrenciesException("Symbol can be no more than two characters.");
                }
                if (!symbol.matches("\\D+")) {
                    throw new CurrenciesException("Symbol cannot contain numbers.");
                }

                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_unit"
                        + " (currency_id, child_unit_id, name, alternate, symbol, prime, main, child_multiples, base_multiples, date_created, date_modified)"
                        + " VALUES (?, NULL, ?, ?, ?, 1, 1, 0, 0, ?, ?)")) {
                    ps.setShort(1, c.getId());
                    ps.setString(2, name);
                    ps.setString(3, plural);
                    ps.setString(4, symbol);
                    ps.setTimestamp(5, now);
                    ps.setTimestamp(6, now);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in addPrime: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addPrime: " + e.getMessage(), e);
        }
    }

    /**
     * Shared validation for addParent/addChild: confirms the currency has a prime unit, and that the new
     * unit's name, plural name, and symbol don't collide with an existing unit. The symbol-format check is
     * left to each caller since addChild additionally forbids the negative sign.
     */
    private static void validateUnitParameters(Connection conn, Currency currency, String name, String plural, String symbol)
            throws SQLException, CurrenciesException {
        Unit prime = repository.queryPrimeUnit(conn, currency);
        if (prime == null) {
            throw new CurrenciesException("Currency " + currency.getAcronym() + " does not have a prime unit.");
        }

        // Validate singular name
        Unit singularUnit = repository.queryUnitByName(conn, currency, name);
        if (singularUnit != null) {
            throw new CurrenciesException("Unit with name " + name + " already exists for this currency.");
        }

        // Validate plural name
        Unit pluralUnit = repository.queryUnitByAlternate(conn, currency, plural);
        if (pluralUnit != null) {
            throw new CurrenciesException("Unit with plural name " + plural + " already exists for this currency.");
        }

        // Validate symbol uniqueness within currency
        Unit symbolUnit = repository.queryUnitBySymbolAndCurrency(conn, currency, symbol);
        if (symbolUnit != null) {
            throw new CurrenciesException("Unit with symbol " + symbol + " already exists for currency " + currency.getAcronym() + ".");
        }

        // Validate symbol not already a prime symbol of another currency
        Unit primeUnit = repository.queryPrimeUnitBySymbol(conn, symbol);
        if (primeUnit != null) {
            throw new CurrenciesException("Unit with symbol " + symbol + " is a prime unit for another currency.");
        }

        if (symbol.length() > 2) {
            throw new CurrenciesException("Symbol can be no more than two characters.");
        }
    }

    public static void addParent(String acronym, String name, String plural, String symbol, int multiplier, String child) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = repository.queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                }

                validateUnitParameters(conn, c, name, plural, symbol);
                if (!symbol.matches("\\D+")) {
                    throw new CurrenciesException("Symbol cannot contain numbers.");
                }

                // Find child unit
                Unit childUnit = repository.queryUnitBySymbolAndCurrency(conn, c, child);
                if (childUnit == null) {
                    throw new CurrenciesException("Child unit " + child + " does not exist for currency " + acronym + ".");
                }

                if (multiplier <= 1) {
                    throw new CurrenciesException("Multiplier must be greater than one.");
                }

                // Validate no existing parent with same child and base_multiples == multiplier
                Unit multiplierUnit = repository.queryUnitByChildAndBaseMultiples(conn, c, childUnit, multiplier);
                if (multiplierUnit != null) {
                    throw new CurrenciesException("A parent of " + child + " with multiplier " + multiplier + " already exists.");
                }

                int multiples = childUnit.getBaseMultiples() != 0
                        ? multiplier * childUnit.getBaseMultiples()
                        : multiplier;

                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_unit"
                        + " (currency_id, child_unit_id, name, alternate, symbol, prime, main, child_multiples, base_multiples, date_created, date_modified)"
                        + " VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?)")) {
                    ps.setShort(1, c.getId());
                    ps.setShort(2, childUnit.getId());
                    ps.setString(3, name);
                    ps.setString(4, plural);
                    ps.setString(5, symbol);
                    ps.setInt(6, multiplier);
                    ps.setInt(7, multiples);
                    ps.setTimestamp(8, now);
                    ps.setTimestamp(9, now);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in addParent: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addParent: " + e.getMessage(), e);
        }
    }

    public static void addChild(String acronym, String name, String plural, String symbol, int divisor, String parent) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = repository.queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                }

                validateUnitParameters(conn, c, name, plural, symbol);
                if (!symbol.matches("\\D+") || symbol.contains("-")) {
                    throw new CurrenciesException("Symbol cannot contain numbers or the negative symbol.");
                }

                // Validate parent unit
                Unit parentUnit = repository.queryUnitBySymbolAndCurrency(conn, c, parent);
                if (parentUnit == null) {
                    throw new CurrenciesException("Unit " + parent + " does not exist.");
                }
                if (parentUnit.getChildUnit() != null) {
                    throw new CurrenciesException("Unit " + parent + " already has a child. Units can only have one child.");
                }

                if (divisor <= 1) {
                    throw new CurrenciesException("Divisor must be greater than 1.");
                }

                // Get all units for this currency and update their base_multiples
                List<Unit> units = repository.queryAllUnitsForCurrency(conn, c);
                Timestamp now = Clock.now();
                for (Unit u : units) {
                    if (u.getId().equals(parentUnit.getId())) {
                        continue;
                    }
                    int newBaseMultiples = (u.getBaseMultiples() == 0) ? divisor : u.getBaseMultiples() * divisor;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE currencies_unit SET base_multiples = ?, date_modified = ? WHERE id = ?")) {
                        ps.setInt(1, newBaseMultiples);
                        ps.setTimestamp(2, now);
                        ps.setShort(3, u.getId());
                        ps.executeUpdate();
                    }
                }

                // Insert new child unit
                short newChildId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_unit"
                        + " (currency_id, child_unit_id, name, alternate, symbol, prime, main, child_multiples, base_multiples, date_created, date_modified)"
                        + " VALUES (?, NULL, ?, ?, ?, 0, 1, 0, 0, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setShort(1, c.getId());
                    ps.setString(2, name);
                    ps.setString(3, plural);
                    ps.setString(4, symbol);
                    ps.setTimestamp(5, now);
                    ps.setTimestamp(6, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new CurrenciesException("Failed to insert new child unit.");
                        }
                        newChildId = keys.getShort(1);
                    }
                }

                // Update parent unit to point to new child
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_unit SET child_unit_id = ?, child_multiples = ?, base_multiples = ?, date_modified = ? WHERE id = ?")) {
                    ps.setShort(1, newChildId);
                    ps.setInt(2, divisor);
                    ps.setInt(3, divisor);
                    ps.setTimestamp(4, now);
                    ps.setShort(5, parentUnit.getId());
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in addChild: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addChild: " + e.getMessage(), e);
        }
    }

    public static List<Currency> list() throws CurrenciesException {
        return list(0);
    }

    public static List<Currency> list(int page) throws CurrenciesException {
        // page=0 or page=1 both map to OFFSET 0
        int offset = (page <= 1) ? 0 : (page - 1) * 10;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Currency> result = repository.queryCurrenciesPage(conn, offset);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in list: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in list: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Account management
    // =========================================================================

    public static Account openAccount(String name, String owner) throws CurrenciesException {
        if (name.length() <= 16) {
            throw new CurrenciesException("Non-player accounts must be longer than 16 characters.");
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check name not already taken
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_account WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new CurrenciesException("Account with name " + name + " already exists.");
                        }
                    }
                }

                Timestamp now = Clock.now();
                int newAccountId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_account (name, uuid, default_currency_id, date_created, date_modified)"
                        + " VALUES (?, NULL, NULL, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setTimestamp(2, now);
                    ps.setTimestamp(3, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new CurrenciesException("Failed to insert new account.");
                        }
                        newAccountId = keys.getInt(1);
                    }
                }

                // Find owner
                int ownerAccountId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_account WHERE name = ?")) {
                    ps.setString(1, owner);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new CurrenciesException("Owner " + owner + " does not exist.");
                        }
                        ownerAccountId = rs.getInt("id");
                    }
                }

                // Self-referential holder (length=0)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO currencies_holder (parent_account_id, child_account_id, length) VALUES (?, ?, 0)")) {
                    ps.setInt(1, newAccountId);
                    ps.setInt(2, newAccountId);
                    ps.executeUpdate();
                }

                // Owner holder (length=1)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO currencies_holder (parent_account_id, child_account_id, length) VALUES (?, ?, 1)")) {
                    ps.setInt(1, ownerAccountId);
                    ps.setInt(2, newAccountId);
                    ps.executeUpdate();
                }

                conn.commit();

                Account account = new Account();
                account.setId(newAccountId);
                account.setName(name);
                account.setUuid(null);
                account.setDefaultCurrency(null);
                account.setDateCreated(now);
                account.setDateModified(now);
                return account;

            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in openAccount: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in openAccount: " + e.getMessage(), e);
        }
    }

    public static void setDefault(String player, String acronym) throws CurrenciesException {
        Account account = getAccountFromPlayer(player, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_account SET default_currency_id = ?, date_modified = ? WHERE id = ?")) {
                    ps.setShort(1, currency.getId());
                    ps.setTimestamp(2, now);
                    ps.setInt(3, account.getId());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in setDefault: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in setDefault: " + e.getMessage(), e);
        }
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
        Account fromAccount = getAccountFromPlayer(from, true);
        Account toAccount = getAccountFromPlayer(to, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
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
        Account fromAccount = getAccountFromPlayer(from, true);
        Account toAccount = getAccountFromPlayer(to, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
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
        Account account = getAccountFromPlayer(player, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
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

        Account bank = getMinecraftCentralBank();

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
        Account account = getAccountFromPlayer(player, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
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

        Account bank = getMinecraftCentralBank();

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
                    Currency currency = getCurrencyFromAcronym(acronym, true);
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
                    Currency currency = getCurrencyFromAcronym(acronym, true);

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
        return getAccountById(MINECRAFT_CENTRAL_BANK);
    }

    public static Account getMinecraftCentralBanker() {
        return getAccountById(MINECRAFT_CENTRAL_BANKER);
    }

    public static Account getTheEndermanMarket() {
        return getAccountById(THE_ENDERMAN_MARKET);
    }

    public static Account getTheEndermanMarketeer() {
        return getAccountById(THE_ENDERMAN_MARKETEER);
    }

    private static Account getAccountById(int id) {
        try (Connection conn = db.getConnection()) {
            return repository.queryAccountById(conn, id);
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountById(" + id + "): " + e.getMessage(), e);
        }
    }

    /**
     * Player is not guaranteed to match the name in the database if someone changed their name and
     * another person took that name.
     *
     * However, the account's name will be updated on that account's login, so Currencies doesn't
     * need to be rewritten to use UUIDs.
     */
    public static Account getAccountFromPlayer(String player, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Account account = repository.queryAccountByName(conn, player);
            if (account == null && exception) {
                throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
            }
            return account;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountFromPlayer: " + e.getMessage(), e);
        }
    }

    public static Account getAccountFromUniqueId(String uuid, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Account account = repository.queryAccountByUuid(conn, uuid);
            if (account == null && exception) {
                throw new CurrenciesRuntimeException("Account with UUID " + uuid + " does not exist.");
            }
            return account;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountFromUniqueId: " + e.getMessage(), e);
        }
    }

    public static Currency getCurrency(short id, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Currency currency = repository.queryCurrencyById(conn, id);
            if (currency == null && exception) {
                throw new CurrenciesRuntimeException("Currency with ID " + id + " does not exist.");
            }
            return currency;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getCurrency: " + e.getMessage(), e);
        }
    }

    public static Currency getCurrencyFromAcronym(String acronym, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Currency currency = repository.queryCurrencyByAcronym(conn, acronym);
            if (currency == null && exception) {
                throw new CurrenciesRuntimeException("Currency " + acronym + " does not exist.");
            }
            return currency;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getCurrencyFromAcronym: " + e.getMessage(), e);
        }
    }

    public static Unit getUnit(short id, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Unit unit = repository.queryUnitById(conn, id);
            if (unit == null && exception) {
                throw new CurrenciesRuntimeException("Unit with ID " + id + " does not exist.");
            }
            return unit;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getUnit: " + e.getMessage(), e);
        }
    }

    public static Unit getBaseUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Unit base = repository.queryBaseUnit(conn, currency);
            if (base == null && exception) {
                throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
            }
            return base;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getBaseUnit: " + e.getMessage(), e);
        }
    }

    public static Unit getPrimeUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Unit prime = repository.queryPrimeUnit(conn, currency);
            if (prime == null && exception) {
                throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no prime unit.");
            }
            return prime;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getPrimeUnit: " + e.getMessage(), e);
        }
    }

    public static Map<Short, Unit> getUnits(Currency currency) {
        try (Connection conn = db.getConnection()) {
            // Load units with their child unit populated for display in the list command
            List<Unit> units = repository.queryUnitsOrdered(conn, currency);
            Map<Short, Unit> retval = new HashMap<>();
            for (Unit u : units) {
                retval.put(u.getId(), u);
            }
            // Resolve child unit references within the map
            for (Unit u : retval.values()) {
                if (u.getChildUnit() != null && u.getChildUnit().getId() != null) {
                    Unit child = retval.get(u.getChildUnit().getId());
                    if (child != null) {
                        u.setChildUnit(child);
                    }
                }
            }
            return retval;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getUnits: " + e.getMessage(), e);
        }
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