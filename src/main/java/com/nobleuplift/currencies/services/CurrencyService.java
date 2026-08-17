package com.nobleuplift.currencies.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

/**
 * Currency CRUD (currencies + their units) and unit/currency lookups.
 */
public class CurrencyService {

    private final ConnectionProvider connectionProvider;
    private final CurrencyRepository repository;

    public CurrencyService(ConnectionProvider connectionProvider, CurrencyRepository repository) {
        this.connectionProvider = connectionProvider;
        this.repository = repository;
    }

    public void createCurrency(String acronym, String name) throws CurrenciesException {
        createCurrency(acronym, name, true);
    }

    public void createCurrency(String acronym, String name, boolean prefix) throws CurrenciesException {
        if (acronym.length() != 3) {
            throw new CurrenciesException("All currency acronyms must be three characters.");
        }

        try (Connection conn = connectionProvider.getConnection()) {
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

    public void deleteCurrency(String acronym) throws CurrenciesException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public void addPrime(String acronym, String name, String plural, String symbol) throws CurrenciesException {
        try (Connection conn = connectionProvider.getConnection()) {
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
    private void validateUnitParameters(Connection conn, Currency currency, String name, String plural, String symbol)
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

    public void addParent(String acronym, String name, String plural, String symbol, int multiplier, String child) throws CurrenciesException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public void addChild(String acronym, String name, String plural, String symbol, int divisor, String parent) throws CurrenciesException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public List<Currency> list() throws CurrenciesException {
        return list(0);
    }

    public List<Currency> list(int page) throws CurrenciesException {
        // page=0 or page=1 both map to OFFSET 0
        int offset = (page <= 1) ? 0 : (page - 1) * 10;

        try (Connection conn = connectionProvider.getConnection()) {
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

    public Currency getCurrency(short id, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public Currency getCurrencyFromAcronym(String acronym, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public Unit getUnit(short id, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public Unit getBaseUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public Unit getPrimeUnit(Currency currency, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
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

    public Map<Short, Unit> getUnits(Currency currency) {
        try (Connection conn = connectionProvider.getConnection()) {
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
}
