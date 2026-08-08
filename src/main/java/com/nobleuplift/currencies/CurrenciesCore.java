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

    private static DatabaseManager db;

    public static void init(DatabaseManager databaseManager) {
        db = databaseManager;
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

                Timestamp now = now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_currency (name, acronym, prefix, deleted, default_currency, date_created, date_modified, date_deleted)"
                        + " VALUES (?, ?, ?, 0, 0, ?, ?, NULL)")) {
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
                throw new CurrenciesRuntimeException("Database error in createCurrency: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in createCurrency: " + e.getMessage());
        }
    }

    public static void deleteCurrency(String acronym) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Could not find currency with acronym " + acronym + ".");
                }

                Timestamp now = now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_currency SET deleted = 1, date_deleted = ?, date_modified = ? WHERE id = ?")) {
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
                throw new CurrenciesRuntimeException("Database error in deleteCurrency: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in deleteCurrency: " + e.getMessage());
        }
    }

    public static void addPrime(String acronym, String name, String plural, String symbol) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                }

                // Check no prime unit already exists
                Unit existingPrime = queryPrimeUnit(conn, c);
                if (existingPrime != null) {
                    throw new CurrenciesException("Currency " + acronym + " already has a prime unit of currency.");
                }

                if (symbol.length() > 2) {
                    throw new CurrenciesException("Symbol can be no more than two characters.");
                }
                if (!symbol.matches("\\D+")) {
                    throw new CurrenciesException("Symbol cannot contain numbers.");
                }

                Timestamp now = now();
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
                throw new CurrenciesRuntimeException("Database error in addPrime: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addPrime: " + e.getMessage());
        }
    }

    public static void addParent(String acronym, String name, String plural, String symbol, int multiplier, String child) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                }

                Unit prime = queryPrimeUnit(conn, c);
                if (prime == null) {
                    throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
                }

                // Validate singular name
                Unit singularUnit = queryUnitByName(conn, c, name);
                if (singularUnit != null) {
                    throw new CurrenciesException("Unit with name " + name + " already exists for this currency.");
                }

                // Validate plural name
                Unit pluralUnit = queryUnitByAlternate(conn, c, plural);
                if (pluralUnit != null) {
                    throw new CurrenciesException("Unit with plural name " + plural + " already exists for this currency.");
                }

                // Validate symbol uniqueness within currency
                Unit symbolUnit = queryUnitBySymbolAndCurrency(conn, c, symbol);
                if (symbolUnit != null) {
                    throw new CurrenciesException("Unit with symbol " + symbol + " already exists for currency " + acronym + ".");
                }

                // Validate symbol not already a prime symbol of another currency
                Unit primeUnit = queryPrimeUnitBySymbol(conn, symbol);
                if (primeUnit != null) {
                    throw new CurrenciesException("Unit with symbol " + symbol + " is a prime unit for another currency.");
                }

                if (symbol.length() > 2) {
                    throw new CurrenciesException("Symbol can be no more than two characters.");
                }
                if (!symbol.matches("\\D+")) {
                    throw new CurrenciesException("Symbol cannot contain numbers.");
                }

                // Find child unit
                Unit childUnit = queryUnitBySymbolAndCurrency(conn, c, child);
                if (childUnit == null) {
                    throw new CurrenciesException("Child unit " + child + " does not exist for currency " + acronym + ".");
                }

                if (multiplier <= 1) {
                    throw new CurrenciesException("Multiplier must be greater than one.");
                }

                // Validate no existing parent with same child and base_multiples == multiplier
                Unit multiplierUnit = queryUnitByChildAndBaseMultiples(conn, c, childUnit, multiplier);
                if (multiplierUnit != null) {
                    throw new CurrenciesException("A parent of " + child + " with multiplier " + multiplier + " already exists.");
                }

                int multiples = childUnit.getBaseMultiples() != 0
                        ? multiplier * childUnit.getBaseMultiples()
                        : multiplier;

                Timestamp now = now();
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
                throw new CurrenciesRuntimeException("Database error in addParent: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addParent: " + e.getMessage());
        }
    }

    public static void addChild(String acronym, String name, String plural, String symbol, int divisor, String parent) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Currency c = queryCurrencyByAcronym(conn, acronym);
                if (c == null) {
                    throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                }

                Unit prime = queryPrimeUnit(conn, c);
                if (prime == null) {
                    throw new CurrenciesException("Currency " + acronym + " does not have a prime unit.");
                }

                // Validate singular name
                Unit singularUnit = queryUnitByName(conn, c, name);
                if (singularUnit != null) {
                    throw new CurrenciesException("Unit with name " + name + " already exists for this currency.");
                }

                // Validate plural name
                Unit pluralUnit = queryUnitByAlternate(conn, c, plural);
                if (pluralUnit != null) {
                    throw new CurrenciesException("Unit with plural name " + plural + " already exists for this currency.");
                }

                // Validate symbol uniqueness within currency
                Unit symbolUnit = queryUnitBySymbolAndCurrency(conn, c, symbol);
                if (symbolUnit != null) {
                    throw new CurrenciesException("Unit with symbol " + symbol + " already exists for currency " + acronym + ".");
                }

                // Validate symbol not already a prime symbol of another currency
                Unit primeUnit = queryPrimeUnitBySymbol(conn, symbol);
                if (primeUnit != null) {
                    throw new CurrenciesException("Unit with symbol " + symbol + " is a prime unit for another currency.");
                }

                if (symbol.length() > 2) {
                    throw new CurrenciesException("Symbol can be no more than two characters.");
                }
                if (!symbol.matches("\\D+") || symbol.contains("-")) {
                    throw new CurrenciesException("Symbol cannot contain numbers or the negative symbol.");
                }

                // Validate parent unit
                Unit parentUnit = queryUnitBySymbolAndCurrency(conn, c, parent);
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
                List<Unit> units = queryAllUnitsForCurrency(conn, c);
                Timestamp now = now();
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
                throw new CurrenciesRuntimeException("Database error in addChild: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addChild: " + e.getMessage());
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
                List<Currency> result = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, name, acronym, prefix, deleted, default_currency, date_created, date_modified, date_deleted"
                        + " FROM currencies_currency WHERE deleted = 0 LIMIT 10 OFFSET ?")) {
                    ps.setInt(1, offset);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(mapCurrencyFromRow(rs));
                        }
                    }
                }
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in list: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in list: " + e.getMessage());
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

                Timestamp now = now();
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
                throw new CurrenciesRuntimeException("Database error in openAccount: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in openAccount: " + e.getMessage());
        }
    }

    public static void setDefault(String player, String acronym) throws CurrenciesException {
        Account account = getAccountFromPlayer(player, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Timestamp now = now();
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
                throw new CurrenciesRuntimeException("Database error in setDefault: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in setDefault: " + e.getMessage());
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
                Account account = queryAccountByName(conn, player);
                if (account == null) {
                    throw new CurrenciesException("Account " + player + " does not exist.");
                }

                Map<Currency, Long> result;
                if (acronym == null) {
                    List<Holding> holdings = queryHoldingsWithUnitAndCurrency(conn, account.getId());
                    result = summateHoldings(holdings);
                } else {
                    Currency c = queryCurrencyByAcronym(conn, acronym);
                    if (c == null) {
                        throw new CurrenciesException("Currency with acronym " + acronym + " does not exist.");
                    }

                    List<Holding> holdings = queryHoldingsForAccountAndCurrency(conn, account.getId(), c.getId());
                    if (holdings.isEmpty()) {
                        Unit pu = queryPrimeUnit(conn, c);
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
                throw new CurrenciesRuntimeException("Database error in balance: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in balance: " + e.getMessage());
        }
    }

    // =========================================================================
    // Pay
    // =========================================================================

    public static Transaction pay(String from, String to, String acronym, String amount) throws CurrenciesException {
        Account fromAccount = getAccountFromPlayer(from, true);
        Account toAccount = getAccountFromPlayer(to, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
        long payAmount = parseCurrency(currency, amount);
        return pay(fromAccount, toAccount, currency, payAmount);
    }

    public static Transaction pay(Account fromAccount, Account toAccount, Currency currency, long baseAmount) throws CurrenciesException {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new CurrenciesException("You cannot pay yourself.");
        }
        if (fromAccount.getId() >= 1 && fromAccount.getId() <= 4) {
            throw new CurrenciesException("Reserved accounts cannot pay.");
        }
        if (toAccount.getId() >= 1 && toAccount.getId() <= 4) {
            throw new CurrenciesException("Cannot pay a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot pay someone a negative amount.");
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                compactHoldings(conn, fromAccount);

                Unit baseUnit = queryBaseUnit(conn, currency);
                if (baseUnit == null) {
                    throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                }

                Holding baseHolding = queryBaseHolding(conn, fromAccount.getId(), baseUnit.getId());
                if (baseHolding == null) {
                    throw new CurrenciesException("You have 0" + baseUnit.getSymbol() + ". You cannot pay "
                            + formatCurrency(currency, baseAmount) + " to " + toAccount.getName() + ".");
                } else if (baseHolding.getAmount() < baseAmount) {
                    throw new CurrenciesException("Cannot pay " + formatCurrency(currency, baseAmount) + " to "
                            + toAccount.getName() + " because it is greater than "
                            + formatCurrency(currency, baseHolding.getAmount()) + ", your current balance.");
                }

                Transaction t = privateTransferAmount(conn, fromAccount, toAccount, currency, baseAmount);
                t.setTypeId(TRANSACTION_TYPE_PAY_ID);
                long txId = insertTransaction(conn, t);
                t.setId(txId);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in pay: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in pay: " + e.getMessage());
        }
    }

    // =========================================================================
    // Bill
    // =========================================================================

    public static Transaction bill(String to, String from, String acronym, String amount) throws CurrenciesException {
        Account fromAccount = getAccountFromPlayer(from, true);
        Account toAccount = getAccountFromPlayer(to, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
        long billAmount = parseCurrency(currency, amount);
        return bill(toAccount, fromAccount, currency, billAmount);
    }

    public static Transaction bill(Account toAccount, Account fromAccount, Currency currency, long baseAmount) throws CurrenciesException {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new CurrenciesException("You cannot bill yourself.");
        }
        if (fromAccount.getId() >= 1 && fromAccount.getId() <= 4) {
            throw new CurrenciesException("Reserved accounts cannot bill.");
        }
        if (toAccount.getId() >= 1 && toAccount.getId() <= 4) {
            throw new CurrenciesException("Cannot bill a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot bill someone a negative amount.");
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Unit base = queryBaseUnit(conn, currency);
                if (base == null) {
                    throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                }

                Timestamp now = now();

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
                    ps.setShort(4, TRANSACTION_TYPE_BILL_ID);
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
                    t.setTypeId(TRANSACTION_TYPE_BILL_ID);
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
                throw new CurrenciesRuntimeException("Database error in bill: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in bill: " + e.getMessage());
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
                Account account = queryAccountByName(conn, from);
                if (account == null) {
                    throw new CurrenciesRuntimeException("Account " + from + " does not exist.");
                }

                Transaction t = null;
                if (transaction == null) {
                    // Find all pending bills where this account is the sender (the one who must pay)
                    List<Transaction> pendingBills = queryPendingBillsForSender(conn, account.getId());
                    if (pendingBills.size() > 1) {
                        throw new CurrenciesException(
                                "You have more than one bill pending. Please specify the transaction ID. You can find it by running /transactions.");
                    } else if (pendingBills.size() == 0) {
                        throw new CurrenciesException("You have no bills pending. ");
                    } else {
                        t = pendingBills.get(0);
                    }
                } else {
                    t = queryTransactionById(conn, Long.parseLong(transaction));
                    if (t == null) {
                        throw new CurrenciesException("Transaction " + transaction + " does not exist.");
                    }
                    if (!account.getId().equals(t.getSender().getId())) {
                        throw new CurrenciesException("You can only pay/reject bills sent to yourself.");
                    }
                }

                if (t.getTypeId() != TRANSACTION_TYPE_BILL_ID) {
                    throw new CurrenciesException("Transaction is not a bill.");
                }
                if (t.getPaid() != null) {
                    throw new CurrenciesException("Bill has already been " + (t.getPaid() ? "paid." : "rejected."));
                }

                if (pay) {
                    // Only actually transfer funds when paying
                    compactHoldings(conn, account);

                    Currency currency = t.getUnit().getCurrency();
                    Unit baseUnit = queryBaseUnit(conn, currency);
                    if (baseUnit == null) {
                        throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                    }

                    Holding baseHolding = queryBaseHolding(conn, account.getId(), baseUnit.getId());
                    if (baseHolding == null) {
                        throw new CurrenciesException("You have 0" + baseUnit.getSymbol() + ". You cannot pay "
                                + formatCurrency(currency, t.getTransactionAmount()) + " to " + t.getRecipient().getName() + ".");
                    } else if (baseHolding.getAmount() < t.getTransactionAmount()) {
                        throw new CurrenciesException("Cannot pay " + formatCurrency(currency, t.getTransactionAmount())
                                + " to " + t.getRecipient().getName() + " because it is greater than "
                                + formatCurrency(currency, baseHolding.getAmount()) + ", your current balance.");
                    }

                    privateTransferAmount(conn, t.getSender(), t.getRecipient(), currency, t.getTransactionAmount());
                }

                // Update the existing bill transaction with paid status
                Timestamp now = now();
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
                throw new CurrenciesRuntimeException("Database error in processBill: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in processBill: " + e.getMessage());
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
                Account account = queryAccountByName(conn, player);
                if (account == null) {
                    throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
                }

                List<Transaction> result = new ArrayList<>();
                String sql = "SELECT t.id, t.sender_id, t.recipient_id, t.unit_id, t.type_id,"
                        + " t.transaction_amount, t.final_sender_amount, t.final_recipient_amount,"
                        + " t.paid, t.date_paid, t.date_created,"
                        + " sa.id AS sa_id, sa.name AS sa_name,"
                        + " ra.id AS ra_id, ra.name AS ra_name,"
                        + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                        + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                        + " u.child_multiples, u.base_multiples,"
                        + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                        + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                        + " FROM currencies_transaction t"
                        + " JOIN currencies_account sa ON t.sender_id = sa.id"
                        + " JOIN currencies_account ra ON t.recipient_id = ra.id"
                        + " JOIN currencies_unit u ON t.unit_id = u.id"
                        + " JOIN currencies_currency c ON u.currency_id = c.id"
                        + " WHERE (t.sender_id = ? OR t.recipient_id = ?)"
                        + " ORDER BY t.date_created DESC"
                        + " LIMIT 10 OFFSET ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, account.getId());
                    ps.setInt(2, account.getId());
                    ps.setInt(3, offset);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(mapTransactionWithRelations(rs));
                        }
                    }
                }

                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in transactions: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in transactions: " + e.getMessage());
        }
    }

    // =========================================================================
    // Credit / Debit
    // =========================================================================

    public static Transaction credit(String player, String acronym, String amount) throws CurrenciesException {
        Account account = getAccountFromPlayer(player, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
        long baseAmount = parseCurrency(currency, amount);
        return credit(account, currency, baseAmount);
    }

    public static Transaction credit(Account account, Currency currency, long baseAmount) throws CurrenciesException {
        if (account.getId() >= 1 && account.getId() <= 4) {
            throw new CurrenciesException("Cannot credit a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot credit someone a negative amount.");
        }

        Account bank = getMinecraftCentralBank();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction t = privateTransferAmount(conn, bank, account, currency, baseAmount);
                t.setTypeId(TRANSACTION_TYPE_CREDIT_ID);
                long txId = insertTransaction(conn, t);
                t.setId(txId);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in credit: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in credit: " + e.getMessage());
        }
    }

    public static Transaction debit(String player, String acronym, String amount) throws CurrenciesException {
        Account account = getAccountFromPlayer(player, true);
        Currency currency = getCurrencyFromAcronym(acronym, true);
        long baseAmount = parseCurrency(currency, amount);
        return debit(account, currency, baseAmount);
    }

    public static Transaction debit(Account account, Currency currency, long baseAmount) throws CurrenciesException {
        if (account.getId() >= 1 && account.getId() <= 4) {
            throw new CurrenciesException("Cannot debit a reserved account.");
        }
        if (baseAmount <= 0) {
            throw new CurrenciesException("Cannot debit someone a negative amount.");
        }

        Account bank = getMinecraftCentralBank();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction t = privateTransferAmount(conn, account, bank, currency, baseAmount);
                t.setTypeId(TRANSACTION_TYPE_DEBIT_ID);
                long txId = insertTransaction(conn, t);
                t.setId(txId);

                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in debit: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in debit: " + e.getMessage());
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
                Account account = queryAccountByName(conn, player);
                if (account == null) {
                    throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
                }
                Account centralBanker = queryAccountById(conn, MINECRAFT_CENTRAL_BANKER);
                Account centralBank = queryAccountById(conn, MINECRAFT_CENTRAL_BANK);

                List<Holding> holdings;

                if (amount != null) {
                    Currency currency = getCurrencyFromAcronym(acronym, true);
                    long bankruptAmount = parseCurrency(currency, amount);

                    compactHoldings(conn, account);

                    holdings = queryHoldingsForAccountAndCurrency(conn, account.getId(), currency.getId());

                    for (Holding h : holdings) {
                        if (h.getAmount() == 0) {
                            deleteHolding(conn, account.getId(), h.getUnit().getId());
                            continue;
                        }
                        Transaction t = privateTransferAmount(conn, account, centralBanker, currency, h.getAmount());
                        t.setTypeId(TRANSACTION_TYPE_BANKRUPT_ID);
                        insertTransaction(conn, t);
                    }

                    Transaction creditT = privateTransferAmount(conn, centralBank, account, currency, bankruptAmount);
                    creditT.setTypeId(TRANSACTION_TYPE_CREDIT_ID);
                    insertTransaction(conn, creditT);

                } else if (acronym != null) {
                    Currency currency = getCurrencyFromAcronym(acronym, true);

                    holdings = queryHoldingsForAccountAndCurrency(conn, account.getId(), currency.getId());

                    for (Holding h : holdings) {
                        if (h.getAmount() == 0) {
                            deleteHolding(conn, account.getId(), h.getUnit().getId());
                            continue;
                        }
                        Transaction t = privateTransferAmount(conn, account, centralBanker, currency, h.getAmount());
                        t.setTypeId(TRANSACTION_TYPE_BANKRUPT_ID);
                        insertTransaction(conn, t);
                    }

                } else {
                    // Delete all holdings — need unit+currency populated
                    holdings = queryHoldingsWithUnitAndCurrency(conn, account.getId());

                    for (Holding h : holdings) {
                        if (h.getAmount() == 0) {
                            deleteHolding(conn, account.getId(), h.getUnit().getId());
                            continue;
                        }
                        Currency hCurrency = h.getUnit().getCurrency();
                        Transaction t = privateTransferAmount(conn, account, centralBanker, hCurrency, h.getAmount());
                        t.setTypeId(TRANSACTION_TYPE_BANKRUPT_ID);
                        insertTransaction(conn, t);
                    }
                }

                conn.commit();
                return holdings;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in bankrupt: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in bankrupt: " + e.getMessage());
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
            return queryAccountById(conn, id);
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountById(" + id + "): " + e.getMessage());
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
            Account account = queryAccountByName(conn, player);
            if (account == null && exception) {
                throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
            }
            return account;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountFromPlayer: " + e.getMessage());
        }
    }

    public static Account getAccountFromUniqueId(String uuid, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Account account = queryAccountByUuid(conn, uuid);
            if (account == null && exception) {
                throw new CurrenciesRuntimeException("Account with UUID " + uuid + " does not exist.");
            }
            return account;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountFromUniqueId: " + e.getMessage());
        }
    }

    public static Currency getCurrency(short id, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Currency currency = queryCurrencyById(conn, id);
            if (currency == null && exception) {
                throw new CurrenciesRuntimeException("Currency with ID " + id + " does not exist.");
            }
            return currency;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getCurrency: " + e.getMessage());
        }
    }

    public static Currency getCurrencyFromAcronym(String acronym, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Currency currency = queryCurrencyByAcronym(conn, acronym);
            if (currency == null && exception) {
                throw new CurrenciesRuntimeException("Currency " + acronym + " does not exist.");
            }
            return currency;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getCurrencyFromAcronym: " + e.getMessage());
        }
    }

    public static Unit getUnit(short id, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Unit unit = queryUnitById(conn, id);
            if (unit == null && exception) {
                throw new CurrenciesRuntimeException("Unit with ID " + id + " does not exist.");
            }
            return unit;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getUnit: " + e.getMessage());
        }
    }

    public static Unit getBaseUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Unit base = queryBaseUnit(conn, currency);
            if (base == null && exception) {
                throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
            }
            return base;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getBaseUnit: " + e.getMessage());
        }
    }

    public static Unit getPrimeUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = db.getConnection()) {
            Unit prime = queryPrimeUnit(conn, currency);
            if (prime == null && exception) {
                throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no prime unit.");
            }
            return prime;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getPrimeUnit: " + e.getMessage());
        }
    }

    public static Map<Short, Unit> getUnits(Currency currency) {
        try (Connection conn = db.getConnection()) {
            // Load units with their child unit populated for display in the list command
            List<Unit> units = queryUnitsOrdered(conn, currency);
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
            throw new CurrenciesRuntimeException("Database error in getUnits: " + e.getMessage());
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
        Map<Currency, String> retval = new HashMap<>();
        for (Map.Entry<Currency, Long> currencyAmount : currencyAmounts.entrySet()) {
            Currency c = currencyAmount.getKey();
            retval.put(c, formatCurrency(c, currencyAmount.getValue()));
        }
        return retval;
    }

    public static String formatCurrency(Currency currency, long amount) {
        try (Connection conn = db.getConnection()) {
            List<Unit> units = queryMainUnitsForCurrencyDescending(conn, currency);

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
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in formatCurrency: " + e.getMessage());
        }
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
        String[] parts = amount.replaceAll("([0-9-]+)", "|$1|").replaceAll("(^\\|*)|(\\|*$)", "").split("\\|");
        if (Currencies.DEBUG) {
            Currencies.getInstance().getLogger().info("PARSE CURRENCY - ALL: " + java.util.Arrays.toString(parts));
        }

        if (parts.length == 0 || parts.length == 1) {
            throw new CurrenciesException("Either no symbol or no currency amount was provided.");
        }

        long baseAmount = 0;
        Unit partUnit = null;
        Long partAmount = null;

        try (Connection conn = db.getConnection()) {
            for (String part : parts) {
                if (Currencies.DEBUG) {
                    Currencies.getInstance().getLogger().info("PARSE CURRENCY - PART: " + part);
                }

                if (part.matches("\\D+")) {
                    partUnit = queryUnitBySymbolAndCurrency(conn, currency, part);
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
                    baseAmount += partUnit.getBaseMultiples() != 0
                            ? partAmount * partUnit.getBaseMultiples()
                            : partAmount;

                    if (Currencies.DEBUG) {
                        Currencies.getInstance().getLogger().info("PARSE CURRENCY - BASE AMOUNT: " + baseAmount);
                    }

                    partUnit = null;
                    partAmount = null;
                }
            }
        } catch (CurrenciesException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in parseCurrency: " + e.getMessage());
        }

        if (Currencies.DEBUG) {
            Currencies.getInstance().getLogger().info("PARSE CURRENCY - FINAL AMOUNT: " + baseAmount);
        }

        return isNegative ? baseAmount * -1 : baseAmount;
    }

    public static Currency getCurrencyFromAmount(Account account, String amount) throws CurrenciesException {
        return resolveCurrency(account, amount).getCurrency();
    }

    /**
     * Resolves an amount string whose currency is ambiguous: the currency is inferred from a prime unit
     * symbol embedded in the string (disambiguated via the account's default currency if the symbol is
     * shared by more than one currency), and the total base-unit amount is computed against that currency
     * in the same pass. Callers that previously called getCurrencyFromAmount() and then parseCurrency()
     * separately can use this instead to avoid resolving the currency and parsing the string twice.
     */
    public static CurrencyDTO resolveCurrency(Account account, String amount) throws CurrenciesException {
        boolean isNegative = false;
        String working = amount;
        if (working.matches("(^-).*")) {
            isNegative = true;
            working = working.replaceAll("(^-)", "");
        }

        String[] parts = working.replaceAll("([0-9-]+)", "|$1|").replaceAll("(^\\|*)|(\\|*$)", "").split("\\|");
        if (parts.length == 0 || parts.length == 1) {
            throw new CurrenciesException("Either no symbol or no currency amount was provided.");
        }

        try (Connection conn = db.getConnection()) {
            Currency currency = null;
            for (String part : parts) {
                if (!part.matches("\\D+")) {
                    continue;
                }
                List<Unit> primes = queryPrimeUnitsBySymbol(conn, part);

                if (primes.size() == 1) {
                    if (currency != null) {
                        throw new CurrenciesException("Two prime units were provided in the currency string.");
                    }
                    currency = primes.get(0).getCurrency();
                } else if (primes.size() > 1) {
                    if (account == null || account.getDefaultCurrency() == null) {
                        throw new CurrenciesException(
                                "This currency shares a prime unit with other currencies. You must run /currencies setdefault <currency>.");
                    }
                    for (Unit p : primes) {
                        if (p.getCurrency().getId().equals(account.getDefaultCurrency().getId())) {
                            currency = p.getCurrency();
                            break;
                        }
                    }
                }
            }

            if (currency == null) {
                throw new CurrenciesException("No prime unit was located in your currency string.");
            }

            Unit baseUnit = queryBaseUnit(conn, currency);
            if (baseUnit == null) {
                throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
            }

            long baseAmount = 0;
            Unit partUnit = null;
            Long partAmount = null;
            for (String part : parts) {
                if (part.matches("\\D+")) {
                    partUnit = queryUnitBySymbolAndCurrency(conn, currency, part);
                    if (partUnit == null) {
                        throw new CurrenciesException(part + " is not a valid symbol.");
                    }
                } else {
                    try {
                        partAmount = Math.abs(Long.parseLong(part));
                    } catch (NumberFormatException e) {
                        throw new CurrenciesException(part + " could not be parsed into a number.");
                    }
                }

                if (partUnit != null && partAmount != null) {
                    baseAmount += partUnit.getBaseMultiples() != 0
                            ? partAmount * partUnit.getBaseMultiples()
                            : partAmount;
                    partUnit = null;
                    partAmount = null;
                }
            }

            return new CurrencyDTO(currency, baseUnit, isNegative ? baseAmount * -1 : baseAmount);
        } catch (CurrenciesException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in resolveCurrency: " + e.getMessage());
        }
    }

    /**
     * Public transferAmount — opens its own connection and delegates to the private version.
     * Returns a Transaction POJO with amounts set; the record is also inserted into the DB.
     */
    public static Transaction transferAmount(Account fromAccount, Account toAccount, Currency currency, long amount) throws CurrenciesException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction t = privateTransferAmount(conn, fromAccount, toAccount, currency, amount);
                t.setTypeId(TRANSACTION_TYPE_PAY_ID);
                long txId = insertTransaction(conn, t);
                t.setId(txId);
                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in transferAmount: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in transferAmount: " + e.getMessage());
        }
    }

    // =========================================================================
    // Private implementation methods
    // =========================================================================

    /**
     * Java has no direct equivalent of MySQL's NOW(); this centralizes the current-time Timestamp construction.
     */
    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * Compact all non-base holdings for the account into the base holding within the given connection/transaction.
     */
    private static int compactHoldings(Connection conn, Account account) throws SQLException {
        // Find all non-base holdings (where unit.child_unit_id IS NOT NULL)
        List<Holding> nonBaseHoldings = queryNonBaseHoldings(conn, account.getId());

        if (Currencies.DEBUG) {
            System.out.println("Non-base holdings: " + nonBaseHoldings);
        }

        if (nonBaseHoldings.isEmpty()) {
            return 0;
        }

        if (Currencies.DEBUG) {
            System.out.println("Non-base holdings count: " + nonBaseHoldings.size());
        }

        // Get all base holdings (where unit.child_unit_id IS NULL)
        List<Holding> baseHoldings = queryBaseHoldings(conn, account.getId());

        if (Currencies.DEBUG) {
            System.out.println("Base holdings: " + baseHoldings);
        }

        // Sort base holdings by currency
        Map<Short, Holding> holdingsByCurrencyId = new HashMap<>();
        for (Holding h : baseHoldings) {
            holdingsByCurrencyId.put(h.getUnit().getCurrency().getId(), h);
        }

        for (Holding h : nonBaseHoldings) {
            if (h.getAmount() == 0) {
                deleteHolding(conn, account.getId(), h.getUnit().getId());
                continue;
            }

            Unit nonBaseUnit = h.getUnit();
            // We need the base unit for this currency
            Unit baseUnit = queryBaseUnit(conn, nonBaseUnit.getCurrency());
            if (baseUnit == null) {
                // If there's no base unit this is unusual — skip
                continue;
            }

            Holding baseHolding = holdingsByCurrencyId.get(nonBaseUnit.getCurrency().getId());
            if (baseHolding == null) {
                baseHolding = new Holding();
                HoldingPK pk = new HoldingPK();
                pk.setAccountId(account.getId());
                pk.setUnitId(baseUnit.getId());
                baseHolding.setId(pk);
                baseHolding.setUnit(baseUnit);
                baseHolding.setAmount(0);
                holdingsByCurrencyId.put(nonBaseUnit.getCurrency().getId(), baseHolding);
            }

            long baseAmount = h.getAmount() * nonBaseUnit.getBaseMultiples();

            if (Currencies.DEBUG) {
                System.out.println("Base Holdings Amount: " + baseHolding.getAmount());
                System.out.println("Base Amount: " + baseAmount);
            }

            long newBaseAmount = baseHolding.getAmount() + baseAmount;
            baseHolding.setAmount(newBaseAmount);

            upsertHolding(conn, account.getId(), baseUnit.getId(), newBaseAmount);
            deleteHolding(conn, account.getId(), nonBaseUnit.getId());
        }

        return nonBaseHoldings.size();
    }

    /**
     * Transfer amount between two accounts within an existing connection/transaction.
     * Does NOT insert a transaction record — callers do that.
     */
    private static Transaction privateTransferAmount(Connection conn, Account fromAccount, Account toAccount,
            Currency currency, long amount) throws SQLException, CurrenciesException {

        Unit base = queryBaseUnit(conn, currency);
        if (base == null) {
            throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base unit.");
        }

        // Get or create from holding
        Holding fromHolding = queryBaseHolding(conn, fromAccount.getId(), base.getId());
        long fromCurrentAmount = (fromHolding != null) ? fromHolding.getAmount() : 0L;
        long fromAmount = fromCurrentAmount - amount;

        if (Currencies.DEBUG) {
            Currencies.getInstance().getLogger().info("CREDIT - FROM AMOUNT: " + fromAmount);
        }

        if (fromAmount == 0) {
            if (fromHolding != null) {
                deleteHolding(conn, fromAccount.getId(), base.getId());
            }
        } else {
            upsertHolding(conn, fromAccount.getId(), base.getId(), fromAmount);
        }

        // Get or create to holding
        Holding toHolding = queryBaseHolding(conn, toAccount.getId(), base.getId());
        long toCurrentAmount = (toHolding != null) ? toHolding.getAmount() : 0L;
        long toAmount = toCurrentAmount + amount;

        if (Currencies.DEBUG) {
            Currencies.getInstance().getLogger().info("CREDIT - TO AMOUNT: " + toAmount);
        }

        upsertHolding(conn, toAccount.getId(), base.getId(), toAmount);

        Timestamp now = now();

        Transaction t = new Transaction();
        t.setSender(fromAccount);
        t.setRecipient(toAccount);
        t.setUnit(base);
        t.setTransactionAmount(amount);
        t.setFinalSenderAmount(fromAmount);
        t.setFinalRecipientAmount(toAmount);
        t.setPaid(true);
        t.setDateCreated(now);
        t.setDatePaid(now);

        return t;
    }

    /**
     * Insert a transaction record and return its generated ID.
     */
    private static long insertTransaction(Connection conn, Transaction t) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO currencies_transaction"
                + " (sender_id, recipient_id, unit_id, type_id, transaction_amount,"
                + "  final_sender_amount, final_recipient_amount, paid, date_paid, date_created)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getSender().getId());
            ps.setInt(2, t.getRecipient().getId());
            ps.setShort(3, t.getUnit().getId());
            ps.setShort(4, t.getTypeId());
            ps.setLong(5, t.getTransactionAmount());

            if (t.getFinalSenderAmount() != null) {
                ps.setLong(6, t.getFinalSenderAmount());
            } else {
                ps.setNull(6, Types.BIGINT);
            }
            if (t.getFinalRecipientAmount() != null) {
                ps.setLong(7, t.getFinalRecipientAmount());
            } else {
                ps.setNull(7, Types.BIGINT);
            }
            if (t.getPaid() != null) {
                ps.setBoolean(8, t.getPaid());
            } else {
                ps.setNull(8, Types.TINYINT);
            }
            if (t.getDatePaid() != null) {
                ps.setTimestamp(9, t.getDatePaid());
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }
            ps.setTimestamp(10, t.getDateCreated() != null
                    ? t.getDateCreated()
                    : now());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    // =========================================================================
    // Query helpers — all accept an open Connection
    // =========================================================================

    private static Account queryAccountByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT a.id, a.name, a.uuid, a.default_currency_id, a.date_created, a.date_modified,"
                + " c.id AS dc_id, c.name AS dc_name, c.acronym AS dc_acronym, c.prefix AS dc_prefix,"
                + " c.deleted AS dc_deleted, c.default_currency AS dc_global_default"
                + " FROM currencies_account a"
                + " LEFT JOIN currencies_currency c ON a.default_currency_id = c.id"
                + " WHERE a.name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAccountWithDefaultCurrency(rs);
                }
            }
        }
        return null;
    }

    private static Account queryAccountByUuid(Connection conn, String uuid) throws SQLException {
        String sql = "SELECT a.id, a.name, a.uuid, a.default_currency_id, a.date_created, a.date_modified,"
                + " c.id AS dc_id, c.name AS dc_name, c.acronym AS dc_acronym, c.prefix AS dc_prefix,"
                + " c.deleted AS dc_deleted, c.default_currency AS dc_global_default"
                + " FROM currencies_account a"
                + " LEFT JOIN currencies_currency c ON a.default_currency_id = c.id"
                + " WHERE a.uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAccountWithDefaultCurrency(rs);
                }
            }
        }
        return null;
    }

    private static Account queryAccountById(Connection conn, int id) throws SQLException {
        String sql = "SELECT a.id, a.name, a.uuid, a.default_currency_id, a.date_created, a.date_modified,"
                + " c.id AS dc_id, c.name AS dc_name, c.acronym AS dc_acronym, c.prefix AS dc_prefix,"
                + " c.deleted AS dc_deleted, c.default_currency AS dc_global_default"
                + " FROM currencies_account a"
                + " LEFT JOIN currencies_currency c ON a.default_currency_id = c.id"
                + " WHERE a.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAccountWithDefaultCurrency(rs);
                }
            }
        }
        return null;
    }

    private static Currency queryCurrencyById(Connection conn, short id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, acronym, prefix, deleted, default_currency, date_created, date_modified, date_deleted"
                + " FROM currencies_currency WHERE id = ?")) {
            ps.setShort(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCurrencyFromRow(rs);
                }
            }
        }
        return null;
    }

    private static Currency queryCurrencyByAcronym(Connection conn, String acronym) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, acronym, prefix, deleted, default_currency, date_created, date_modified, date_deleted"
                + " FROM currencies_currency WHERE acronym = ?")) {
            ps.setString(1, acronym);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCurrencyFromRow(rs);
                }
            }
        }
        return null;
    }

    private static Unit queryUnitById(Connection conn, short id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name, u.alternate AS u_alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_unit u"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE u.id = ?")) {
            ps.setShort(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUnitWithCurrency(rs);
                }
            }
        }
        return null;
    }

    private static Unit queryBaseUnit(Connection conn, Currency currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.currency_id, u.child_unit_id, u.name, u.alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples"
                + " FROM currencies_unit u"
                + " WHERE u.currency_id = ? AND u.child_unit_id IS NULL"
                + " LIMIT 1")) {
            ps.setShort(1, currency.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = mapUnitBasic(rs);
                    u.setCurrency(currency);
                    return u;
                }
            }
        }
        return null;
    }

    private static Unit queryPrimeUnit(Connection conn, Currency currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.currency_id, u.child_unit_id, u.name, u.alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples"
                + " FROM currencies_unit u"
                + " WHERE u.currency_id = ? AND u.prime = 1"
                + " LIMIT 1")) {
            ps.setShort(1, currency.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = mapUnitBasic(rs);
                    u.setCurrency(currency);
                    return u;
                }
            }
        }
        return null;
    }

    private static Unit queryUnitBySymbolAndCurrency(Connection conn, Currency currency, String symbol) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.currency_id, u.child_unit_id, u.name, u.alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples"
                + " FROM currencies_unit u"
                + " WHERE u.currency_id = ? AND u.symbol = ?"
                + " LIMIT 1")) {
            ps.setShort(1, currency.getId());
            ps.setString(2, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = mapUnitBasic(rs);
                    u.setCurrency(currency);
                    // Load child unit reference (id only stub) if present
                    short childUnitId = rs.getShort("child_unit_id");
                    if (!rs.wasNull() && childUnitId != 0) {
                        Unit childStub = new Unit();
                        childStub.setId(childUnitId);
                        u.setChildUnit(childStub);
                    }
                    return u;
                }
            }
        }
        return null;
    }

    private static Unit queryUnitByName(Connection conn, Currency currency, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id FROM currencies_unit u WHERE u.currency_id = ? AND u.name = ? LIMIT 1")) {
            ps.setShort(1, currency.getId());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = new Unit();
                    u.setId(rs.getShort("id"));
                    return u;
                }
            }
        }
        return null;
    }

    private static Unit queryUnitByAlternate(Connection conn, Currency currency, String alternate) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id FROM currencies_unit u WHERE u.currency_id = ? AND u.alternate = ? LIMIT 1")) {
            ps.setShort(1, currency.getId());
            ps.setString(2, alternate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = new Unit();
                    u.setId(rs.getShort("id"));
                    return u;
                }
            }
        }
        return null;
    }

    private static Unit queryPrimeUnitBySymbol(Connection conn, String symbol) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id FROM currencies_unit u WHERE u.symbol = ? AND u.prime = 1 LIMIT 1")) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = new Unit();
                    u.setId(rs.getShort("id"));
                    return u;
                }
            }
        }
        return null;
    }

    private static Unit queryUnitByChildAndBaseMultiples(Connection conn, Currency currency, Unit childUnit, int baseMultiples) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id FROM currencies_unit u"
                + " WHERE u.currency_id = ? AND u.child_unit_id = ? AND u.base_multiples = ? LIMIT 1")) {
            ps.setShort(1, currency.getId());
            ps.setShort(2, childUnit.getId());
            ps.setInt(3, baseMultiples);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Unit u = new Unit();
                    u.setId(rs.getShort("id"));
                    return u;
                }
            }
        }
        return null;
    }

    private static List<Unit> queryAllUnitsForCurrency(Connection conn, Currency currency) throws SQLException {
        List<Unit> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.currency_id, u.child_unit_id, u.name, u.alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples"
                + " FROM currencies_unit u WHERE u.currency_id = ?")) {
            ps.setShort(1, currency.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Unit u = mapUnitBasic(rs);
                    u.setCurrency(currency);
                    short childId = rs.getShort("child_unit_id");
                    if (!rs.wasNull() && childId != 0) {
                        Unit childStub = new Unit();
                        childStub.setId(childId);
                        u.setChildUnit(childStub);
                    }
                    result.add(u);
                }
            }
        }
        return result;
    }

    /**
     * Query units ordered for the getUnits() display method.
     * Returns units with child_unit_id stubs set for later resolution.
     */
    private static List<Unit> queryUnitsOrdered(Connection conn, Currency currency) throws SQLException {
        List<Unit> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.currency_id, u.child_unit_id, u.name, u.alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples"
                + " FROM currencies_unit u"
                + " WHERE u.currency_id = ?"
                + " ORDER BY u.prime DESC, u.main DESC, u.base_multiples DESC")) {
            ps.setShort(1, currency.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Unit u = mapUnitBasic(rs);
                    u.setCurrency(currency);
                    short childId = rs.getShort("child_unit_id");
                    if (!rs.wasNull() && childId != 0) {
                        Unit childStub = new Unit();
                        childStub.setId(childId);
                        u.setChildUnit(childStub);
                    }
                    result.add(u);
                }
            }
        }
        return result;
    }

    private static List<Unit> queryMainUnitsForCurrencyDescending(Connection conn, Currency currency) throws SQLException {
        List<Unit> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.currency_id, u.child_unit_id, u.name, u.alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples"
                + " FROM currencies_unit u"
                + " WHERE u.currency_id = ? AND u.main = 1"
                + " ORDER BY u.base_multiples DESC")) {
            ps.setShort(1, currency.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Unit u = mapUnitBasic(rs);
                    u.setCurrency(currency);
                    short childId = rs.getShort("child_unit_id");
                    if (!rs.wasNull() && childId != 0) {
                        Unit childStub = new Unit();
                        childStub.setId(childId);
                        u.setChildUnit(childStub);
                    }
                    result.add(u);
                }
            }
        }
        return result;
    }

    private static List<Unit> queryPrimeUnitsBySymbol(Connection conn, String symbol) throws SQLException {
        List<Unit> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name, u.alternate AS u_alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_unit u"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE u.symbol = ? AND u.prime = 1")) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapUnitWithCurrency(rs));
                }
            }
        }
        return result;
    }

    private static Holding queryBaseHolding(Connection conn, int accountId, short unitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT h.account_id, h.unit_id, h.amount"
                + " FROM currencies_holding h"
                + " WHERE h.account_id = ? AND h.unit_id = ?")) {
            ps.setInt(1, accountId);
            ps.setShort(2, unitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Holding h = new Holding();
                    HoldingPK pk = new HoldingPK();
                    pk.setAccountId(rs.getInt("account_id"));
                    pk.setUnitId(rs.getShort("unit_id"));
                    h.setId(pk);
                    h.setAmount(rs.getLong("amount"));
                    return h;
                }
            }
        }
        return null;
    }

    private static List<Holding> queryHoldingsWithUnitAndCurrency(Connection conn, int accountId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_holding h"
                + " JOIN currencies_unit u ON h.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE h.account_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHoldingWithUnitAndCurrency(rs));
                }
            }
        }
        return result;
    }

    private static List<Holding> queryHoldingsForAccountAndCurrency(Connection conn, int accountId, short currencyId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_holding h"
                + " JOIN currencies_unit u ON h.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE h.account_id = ? AND u.currency_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setShort(2, currencyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHoldingWithUnitAndCurrency(rs));
                }
            }
        }
        return result;
    }

    /** All holdings where the unit has a child (i.e., non-base holdings). */
    private static List<Holding> queryNonBaseHoldings(Connection conn, int accountId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_holding h"
                + " JOIN currencies_unit u ON h.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE h.account_id = ? AND u.child_unit_id IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHoldingWithUnitAndCurrency(rs));
                }
            }
        }
        return result;
    }

    /** All holdings where the unit has no child (i.e., base holdings). */
    private static List<Holding> queryBaseHoldings(Connection conn, int accountId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_holding h"
                + " JOIN currencies_unit u ON h.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE h.account_id = ? AND u.child_unit_id IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHoldingWithUnitAndCurrency(rs));
                }
            }
        }
        return result;
    }

    private static List<Transaction> queryPendingBillsForSender(Connection conn, int senderId) throws SQLException {
        List<Transaction> result = new ArrayList<>();
        String sql = "SELECT t.id, t.sender_id, t.recipient_id, t.unit_id, t.type_id,"
                + " t.transaction_amount, t.final_sender_amount, t.final_recipient_amount,"
                + " t.paid, t.date_paid, t.date_created,"
                + " sa.id AS sa_id, sa.name AS sa_name,"
                + " ra.id AS ra_id, ra.name AS ra_name,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_transaction t"
                + " JOIN currencies_account sa ON t.sender_id = sa.id"
                + " JOIN currencies_account ra ON t.recipient_id = ra.id"
                + " JOIN currencies_unit u ON t.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE t.sender_id = ? AND t.paid IS NULL AND t.type_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            ps.setShort(2, TRANSACTION_TYPE_BILL_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTransactionWithRelations(rs));
                }
            }
        }
        return result;
    }

    private static Transaction queryTransactionById(Connection conn, long id) throws SQLException {
        String sql = "SELECT t.id, t.sender_id, t.recipient_id, t.unit_id, t.type_id,"
                + " t.transaction_amount, t.final_sender_amount, t.final_recipient_amount,"
                + " t.paid, t.date_paid, t.date_created,"
                + " sa.id AS sa_id, sa.name AS sa_name,"
                + " ra.id AS ra_id, ra.name AS ra_name,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.deleted AS c_deleted, c.default_currency AS c_global_default"
                + " FROM currencies_transaction t"
                + " JOIN currencies_account sa ON t.sender_id = sa.id"
                + " JOIN currencies_account ra ON t.recipient_id = ra.id"
                + " JOIN currencies_unit u ON t.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE t.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTransactionWithRelations(rs);
                }
            }
        }
        return null;
    }

    private static void upsertHolding(Connection conn, int accountId, short unitId, long amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO currencies_holding (account_id, unit_id, amount) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE amount = VALUES(amount)")) {
            ps.setInt(1, accountId);
            ps.setShort(2, unitId);
            ps.setLong(3, amount);
            ps.executeUpdate();
        }
    }

    private static void deleteHolding(Connection conn, int accountId, short unitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM currencies_holding WHERE account_id = ? AND unit_id = ?")) {
            ps.setInt(1, accountId);
            ps.setShort(2, unitId);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Mapper methods
    // =========================================================================

    private static Account mapAccountWithDefaultCurrency(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getInt("id"));
        a.setName(rs.getString("name"));
        a.setUuid(rs.getString("uuid"));
        a.setDateCreated(rs.getTimestamp("date_created"));
        a.setDateModified(rs.getTimestamp("date_modified"));

        // Default currency (from LEFT JOIN, may be null)
        int dcId = rs.getInt("dc_id");
        if (!rs.wasNull() && dcId != 0) {
            Currency dc = new Currency();
            dc.setId((short) dcId);
            dc.setName(rs.getString("dc_name"));
            dc.setAcronym(rs.getString("dc_acronym"));
            dc.setPrefix(rs.getBoolean("dc_prefix"));
            dc.setDeleted(rs.getBoolean("dc_deleted"));
            dc.setGlobalDefault(rs.getBoolean("dc_global_default"));
            a.setDefaultCurrency(dc);
        }
        return a;
    }

    private static Currency mapCurrencyFromRow(ResultSet rs) throws SQLException {
        Currency c = new Currency();
        c.setId(rs.getShort("id"));
        c.setName(rs.getString("name"));
        c.setAcronym(rs.getString("acronym"));
        c.setPrefix(rs.getBoolean("prefix"));
        c.setDeleted(rs.getBoolean("deleted"));
        c.setGlobalDefault(rs.getBoolean("default_currency"));
        c.setDateCreated(rs.getTimestamp("date_created"));
        c.setDateModified(rs.getTimestamp("date_modified"));
        c.setDateDeleted(rs.getTimestamp("date_deleted"));
        return c;
    }

    /**
     * Map a unit row that has currency columns prefixed with c_.
     */
    private static Unit mapUnitWithCurrency(ResultSet rs) throws SQLException {
        Unit u = new Unit();
        u.setId(rs.getShort("u_id"));
        u.setName(rs.getString("u_name"));
        u.setAlternate(rs.getString("u_alternate"));
        u.setSymbol(rs.getString("symbol"));
        u.setPrime(rs.getBoolean("prime"));
        u.setMain(rs.getBoolean("main"));
        u.setChildMultiples(rs.getInt("child_multiples"));
        u.setBaseMultiples(rs.getInt("base_multiples"));

        short childUnitId = rs.getShort("child_unit_id");
        if (!rs.wasNull() && childUnitId != 0) {
            Unit childStub = new Unit();
            childStub.setId(childUnitId);
            u.setChildUnit(childStub);
        }

        Currency c = new Currency();
        c.setId(rs.getShort("c_id"));
        c.setName(rs.getString("c_name"));
        c.setAcronym(rs.getString("acronym"));
        c.setPrefix(rs.getBoolean("c_prefix"));
        c.setDeleted(rs.getBoolean("c_deleted"));
        c.setGlobalDefault(rs.getBoolean("c_global_default"));
        u.setCurrency(c);

        return u;
    }

    /**
     * Map a unit row with plain column names (no prefix), currency not populated.
     */
    private static Unit mapUnitBasic(ResultSet rs) throws SQLException {
        Unit u = new Unit();
        u.setId(rs.getShort("id"));
        u.setName(rs.getString("name"));
        u.setAlternate(rs.getString("alternate"));
        u.setSymbol(rs.getString("symbol"));
        u.setPrime(rs.getBoolean("prime"));
        u.setMain(rs.getBoolean("main"));
        u.setChildMultiples(rs.getInt("child_multiples"));
        u.setBaseMultiples(rs.getInt("base_multiples"));
        // child_unit_id loaded by caller when needed
        return u;
    }

    private static Holding mapHoldingWithUnitAndCurrency(ResultSet rs) throws SQLException {
        Holding h = new Holding();
        HoldingPK pk = new HoldingPK();
        pk.setAccountId(rs.getInt("account_id"));
        pk.setUnitId(rs.getShort("unit_id"));
        h.setId(pk);
        h.setAmount(rs.getLong("amount"));

        // Map unit
        Unit u = new Unit();
        u.setId(rs.getShort("u_id"));
        u.setName(rs.getString("u_name"));
        u.setAlternate(rs.getString("u_alternate"));
        u.setSymbol(rs.getString("symbol"));
        u.setPrime(rs.getBoolean("prime"));
        u.setMain(rs.getBoolean("main"));
        u.setChildMultiples(rs.getInt("child_multiples"));
        u.setBaseMultiples(rs.getInt("base_multiples"));

        short childUnitId = rs.getShort("child_unit_id");
        if (!rs.wasNull() && childUnitId != 0) {
            Unit childStub = new Unit();
            childStub.setId(childUnitId);
            u.setChildUnit(childStub);
        }

        // Map currency
        Currency c = new Currency();
        c.setId(rs.getShort("c_id"));
        c.setName(rs.getString("c_name"));
        c.setAcronym(rs.getString("acronym"));
        c.setPrefix(rs.getBoolean("c_prefix"));
        c.setDeleted(rs.getBoolean("c_deleted"));
        c.setGlobalDefault(rs.getBoolean("c_global_default"));

        u.setCurrency(c);
        h.setUnit(u);

        return h;
    }

    private static Transaction mapTransactionWithRelations(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setId(rs.getLong("id"));
        t.setTransactionAmount(rs.getLong("transaction_amount"));
        t.setTypeId(rs.getShort("type_id"));

        long fsa = rs.getLong("final_sender_amount");
        t.setFinalSenderAmount(rs.wasNull() ? null : fsa);

        long fra = rs.getLong("final_recipient_amount");
        t.setFinalRecipientAmount(rs.wasNull() ? null : fra);

        // paid: stored as TINYINT NULL — 0=false, 1=true, NULL=pending
        Object paidObj = rs.getObject("paid");
        if (paidObj == null) {
            t.setPaid(null);
        } else {
            t.setPaid(rs.getBoolean("paid"));
        }

        t.setDateCreated(rs.getTimestamp("date_created"));
        t.setDatePaid(rs.getTimestamp("date_paid"));

        // Sender account (minimal — id + name)
        Account sender = new Account();
        sender.setId(rs.getInt("sa_id"));
        sender.setName(rs.getString("sa_name"));
        t.setSender(sender);

        // Recipient account (minimal — id + name)
        Account recipient = new Account();
        recipient.setId(rs.getInt("ra_id"));
        recipient.setName(rs.getString("ra_name"));
        t.setRecipient(recipient);

        // Unit with currency
        Unit u = new Unit();
        u.setId(rs.getShort("u_id"));
        u.setName(rs.getString("u_name"));
        u.setAlternate(rs.getString("u_alternate"));
        u.setSymbol(rs.getString("symbol"));
        u.setPrime(rs.getBoolean("prime"));
        u.setMain(rs.getBoolean("main"));
        u.setChildMultiples(rs.getInt("child_multiples"));
        u.setBaseMultiples(rs.getInt("base_multiples"));

        short childUnitId = rs.getShort("child_unit_id");
        if (!rs.wasNull() && childUnitId != 0) {
            Unit childStub = new Unit();
            childStub.setId(childUnitId);
            u.setChildUnit(childStub);
        }

        Currency c = new Currency();
        c.setId(rs.getShort("c_id"));
        c.setName(rs.getString("c_name"));
        c.setAcronym(rs.getString("acronym"));
        c.setPrefix(rs.getBoolean("c_prefix"));
        c.setDeleted(rs.getBoolean("c_deleted"));
        c.setGlobalDefault(rs.getBoolean("c_global_default"));

        u.setCurrency(c);
        t.setUnit(u);

        return t;
    }
}
