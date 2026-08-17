package com.nobleuplift.currencies.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionType;
import com.nobleuplift.currencies.entities.Unit;

public class JdbcCurrencyRepository implements CurrencyRepository {

    public Account queryAccountByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT a.id, a.name, a.uuid, a.default_currency_id, a.date_created, a.date_modified,"
                + " c.id AS dc_id, c.name AS dc_name, c.acronym AS dc_acronym, c.prefix AS dc_prefix,"
                + " c.default_currency AS dc_global_default"
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

    public Account queryAccountByUuid(Connection conn, String uuid) throws SQLException {
        String sql = "SELECT a.id, a.name, a.uuid, a.default_currency_id, a.date_created, a.date_modified,"
                + " c.id AS dc_id, c.name AS dc_name, c.acronym AS dc_acronym, c.prefix AS dc_prefix,"
                + " c.default_currency AS dc_global_default"
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

    public Account queryAccountById(Connection conn, int id) throws SQLException {
        String sql = "SELECT a.id, a.name, a.uuid, a.default_currency_id, a.date_created, a.date_modified,"
                + " c.id AS dc_id, c.name AS dc_name, c.acronym AS dc_acronym, c.prefix AS dc_prefix,"
                + " c.default_currency AS dc_global_default"
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

    public Currency queryCurrencyById(Connection conn, short id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, acronym, prefix, default_currency, date_created, date_modified, date_deleted"
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

    public Currency queryCurrencyByAcronym(Connection conn, String acronym) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, acronym, prefix, default_currency, date_created, date_modified, date_deleted"
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

    public List<Currency> queryCurrenciesPage(Connection conn, int offset) throws SQLException {
        List<Currency> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, acronym, prefix, default_currency, date_created, date_modified, date_deleted"
                + " FROM currencies_currency WHERE date_deleted IS NULL LIMIT 10 OFFSET ?")) {
            ps.setInt(1, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapCurrencyFromRow(rs));
                }
            }
        }
        return result;
    }

    public Unit queryUnitById(Connection conn, short id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name, u.alternate AS u_alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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

    public Unit queryBaseUnit(Connection conn, Currency currency) throws SQLException {
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

    public Unit queryPrimeUnit(Connection conn, Currency currency) throws SQLException {
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

    public Unit queryUnitBySymbolAndCurrency(Connection conn, Currency currency, String symbol) throws SQLException {
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

    public Unit queryUnitByName(Connection conn, Currency currency, String name) throws SQLException {
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

    public Unit queryUnitByAlternate(Connection conn, Currency currency, String alternate) throws SQLException {
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

    public Unit queryPrimeUnitBySymbol(Connection conn, String symbol) throws SQLException {
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

    public Unit queryUnitByChildAndBaseMultiples(Connection conn, Currency currency, Unit childUnit, int baseMultiples) throws SQLException {
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

    public List<Unit> queryAllUnitsForCurrency(Connection conn, Currency currency) throws SQLException {
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
    public List<Unit> queryUnitsOrdered(Connection conn, Currency currency) throws SQLException {
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

    public List<Unit> queryMainUnitsForCurrencyDescending(Connection conn, Currency currency) throws SQLException {
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

    public List<Unit> queryPrimeUnitsBySymbol(Connection conn, String symbol) throws SQLException {
        List<Unit> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name, u.alternate AS u_alternate, u.symbol,"
                + " u.prime, u.main, u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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

    public Holding queryBaseHolding(Connection conn, int accountId, short unitId) throws SQLException {
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

    public List<Holding> queryHoldingsWithUnitAndCurrency(Connection conn, int accountId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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

    public List<Holding> queryHoldingsForAccountAndCurrency(Connection conn, int accountId, short currencyId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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
    public List<Holding> queryNonBaseHoldings(Connection conn, int accountId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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
    public List<Holding> queryBaseHoldings(Connection conn, int accountId) throws SQLException {
        List<Holding> result = new ArrayList<>();
        String sql = "SELECT h.account_id, h.unit_id, h.amount,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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

    public List<Transaction> queryPendingBillsForSender(Connection conn, int senderId) throws SQLException {
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
                + " c.default_currency AS c_global_default"
                + " FROM currencies_transaction t"
                + " JOIN currencies_account sa ON t.sender_id = sa.id"
                + " JOIN currencies_account ra ON t.recipient_id = ra.id"
                + " JOIN currencies_unit u ON t.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE t.sender_id = ? AND t.paid IS NULL AND t.type_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            ps.setShort(2, TransactionType.BILL.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTransactionWithRelations(rs));
                }
            }
        }
        return result;
    }

    public List<Transaction> queryTransactionsForAccountPage(Connection conn, int accountId, int offset) throws SQLException {
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
                + " c.default_currency AS c_global_default"
                + " FROM currencies_transaction t"
                + " JOIN currencies_account sa ON t.sender_id = sa.id"
                + " JOIN currencies_account ra ON t.recipient_id = ra.id"
                + " JOIN currencies_unit u ON t.unit_id = u.id"
                + " JOIN currencies_currency c ON u.currency_id = c.id"
                + " WHERE (t.sender_id = ? OR t.recipient_id = ?)"
                + " ORDER BY t.date_created DESC"
                + " LIMIT 10 OFFSET ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, accountId);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTransactionWithRelations(rs));
                }
            }
        }
        return result;
    }

    public Transaction queryTransactionById(Connection conn, long id) throws SQLException {
        String sql = "SELECT t.id, t.sender_id, t.recipient_id, t.unit_id, t.type_id,"
                + " t.transaction_amount, t.final_sender_amount, t.final_recipient_amount,"
                + " t.paid, t.date_paid, t.date_created,"
                + " sa.id AS sa_id, sa.name AS sa_name,"
                + " ra.id AS ra_id, ra.name AS ra_name,"
                + " u.id AS u_id, u.currency_id, u.child_unit_id, u.name AS u_name,"
                + " u.alternate AS u_alternate, u.symbol, u.prime, u.main,"
                + " u.child_multiples, u.base_multiples,"
                + " c.id AS c_id, c.name AS c_name, c.acronym, c.prefix AS c_prefix,"
                + " c.default_currency AS c_global_default"
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

    public void upsertHolding(Connection conn, int accountId, short unitId, long amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO currencies_holding (account_id, unit_id, amount) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE amount = VALUES(amount)")) {
            ps.setInt(1, accountId);
            ps.setShort(2, unitId);
            ps.setLong(3, amount);
            ps.executeUpdate();
        }
    }

    public void deleteHolding(Connection conn, int accountId, short unitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM currencies_holding WHERE account_id = ? AND unit_id = ?")) {
            ps.setInt(1, accountId);
            ps.setShort(2, unitId);
            ps.executeUpdate();
        }
    }

    private Account mapAccountWithDefaultCurrency(ResultSet rs) throws SQLException {
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
            dc.setGlobalDefault(rs.getBoolean("dc_global_default"));
            a.setDefaultCurrency(dc);
        }
        return a;
    }

    private Currency mapCurrencyFromRow(ResultSet rs) throws SQLException {
        Currency c = new Currency();
        c.setId(rs.getShort("id"));
        c.setName(rs.getString("name"));
        c.setAcronym(rs.getString("acronym"));
        c.setPrefix(rs.getBoolean("prefix"));
        c.setGlobalDefault(rs.getBoolean("default_currency"));
        c.setDateCreated(rs.getTimestamp("date_created"));
        c.setDateModified(rs.getTimestamp("date_modified"));
        c.setDateDeleted(rs.getTimestamp("date_deleted"));
        return c;
    }

    /**
     * Map a unit row that has currency columns prefixed with c_.
     */
    private Unit mapUnitWithCurrency(ResultSet rs) throws SQLException {
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
        c.setGlobalDefault(rs.getBoolean("c_global_default"));
        u.setCurrency(c);

        return u;
    }

    /**
     * Map a unit row with plain column names (no prefix), currency not populated.
     */
    private Unit mapUnitBasic(ResultSet rs) throws SQLException {
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

    private Holding mapHoldingWithUnitAndCurrency(ResultSet rs) throws SQLException {
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
        c.setGlobalDefault(rs.getBoolean("c_global_default"));

        u.setCurrency(c);
        h.setUnit(u);

        return h;
    }

    private Transaction mapTransactionWithRelations(ResultSet rs) throws SQLException {
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
        c.setGlobalDefault(rs.getBoolean("c_global_default"));

        u.setCurrency(c);
        t.setUnit(u);

        return t;
    }
}
