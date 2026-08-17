package com.nobleuplift.currencies.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.Currencies;
import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionType;
import com.nobleuplift.currencies.entities.Unit;

/**
 * Shared transactional core for money movement, used by every transaction
 * type (Pay, Bill/ProcessBill, Credit, Debit, Bankrupt).
 */
public class Ledger {

    private final ConnectionProvider connectionProvider;
    private final CurrencyRepository repository;

    public Ledger(ConnectionProvider connectionProvider, CurrencyRepository repository) {
        this.connectionProvider = connectionProvider;
        this.repository = repository;
    }

    /**
     * Public transferAmount — opens its own connection and delegates to the private version.
     * Returns a Transaction POJO with amounts set; the record is also inserted into the DB.
     */
    public Transaction transferAmount(Account fromAccount, Account toAccount, Currency currency, long amount) throws CurrenciesException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Transaction t = privateTransferAmount(conn, fromAccount, toAccount, currency, amount);
                t.setTypeId(TransactionType.PAY.getId());
                long txId = insertTransaction(conn, t);
                t.setId(txId);
                conn.commit();
                return t;
            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in transferAmount: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in transferAmount: " + e.getMessage(), e);
        }
    }

    /**
     * Compact all non-base holdings for the account into the base holding within the given connection/transaction.
     */
    public int compactHoldings(Connection conn, Account account) throws SQLException {
        // Find all non-base holdings (where unit.child_unit_id IS NOT NULL)
        List<Holding> nonBaseHoldings = repository.queryNonBaseHoldings(conn, account.getId());

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
        List<Holding> baseHoldings = repository.queryBaseHoldings(conn, account.getId());

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
                repository.deleteHolding(conn, account.getId(), h.getUnit().getId());
                continue;
            }

            Unit nonBaseUnit = h.getUnit();
            // We need the base unit for this currency
            Unit baseUnit = repository.queryBaseUnit(conn, nonBaseUnit.getCurrency());
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

            repository.upsertHolding(conn, account.getId(), baseUnit.getId(), newBaseAmount);
            repository.deleteHolding(conn, account.getId(), nonBaseUnit.getId());
        }

        return nonBaseHoldings.size();
    }

    /**
     * Transfer amount between two accounts within an existing connection/transaction.
     * Does NOT insert a transaction record — callers do that.
     */
    public Transaction privateTransferAmount(Connection conn, Account fromAccount, Account toAccount,
            Currency currency, long amount) throws SQLException, CurrenciesException {

        Unit base = repository.queryBaseUnit(conn, currency);
        if (base == null) {
            throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base unit.");
        }

        // Get or create from holding
        Holding fromHolding = repository.queryBaseHolding(conn, fromAccount.getId(), base.getId());
        long fromCurrentAmount = (fromHolding != null) ? fromHolding.getAmount() : 0L;
        long fromAmount = fromCurrentAmount - amount;

        if (Currencies.DEBUG) {
            Currencies.getPluginLogger().info("CREDIT - FROM AMOUNT: " + fromAmount);
        }

        if (fromAmount == 0) {
            if (fromHolding != null) {
                repository.deleteHolding(conn, fromAccount.getId(), base.getId());
            }
        } else {
            repository.upsertHolding(conn, fromAccount.getId(), base.getId(), fromAmount);
        }

        // Get or create to holding
        Holding toHolding = repository.queryBaseHolding(conn, toAccount.getId(), base.getId());
        long toCurrentAmount = (toHolding != null) ? toHolding.getAmount() : 0L;
        long toAmount = toCurrentAmount + amount;

        if (Currencies.DEBUG) {
            Currencies.getPluginLogger().info("CREDIT - TO AMOUNT: " + toAmount);
        }

        repository.upsertHolding(conn, toAccount.getId(), base.getId(), toAmount);

        Timestamp now = Clock.now();

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
    public long insertTransaction(Connection conn, Transaction t) throws SQLException {
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
            if (t.isPaid() != null) {
                ps.setBoolean(8, t.isPaid());
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
                    : Clock.now());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }
}
