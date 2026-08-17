package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionType;
import com.nobleuplift.currencies.entities.Unit;

class LedgerTest {

    private FakeCurrencyRepository repository;
    private Currency gbp;
    private Unit pound;
    private Unit penny;
    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        repository = new FakeCurrencyRepository();
        gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        repository.addCurrency(gbp);
        penny = Fixtures.baseUnit((short) 2, gbp, "Penny", "p");
        pound = Fixtures.parentUnit((short) 1, gbp, "Pound", "£", true, 100, penny);
        repository.addUnit(pound);
        repository.addUnit(penny);

        alice = Fixtures.account(1, "Alice");
        bob = Fixtures.account(2, "Bob");
        repository.addAccount(alice);
        repository.addAccount(bob);
    }

    // ---- compactHoldings ----

    @Test
    void compactHoldingsConsolidatesNonBaseIntoExistingBaseHolding() throws SQLException {
        repository.addHolding(alice.getId(), pound, 2); // 2 Pounds = 200 pence
        repository.addHolding(alice.getId(), penny, 30); // 30p already held

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        int converted = ledger.compactHoldings(mock(Connection.class), alice);

        assertEquals(1, converted);
        assertNull(repository.getHolding(alice.getId(), pound.getId()), "non-base holding should be removed");
        assertEquals(230L, repository.getHolding(alice.getId(), penny.getId()).getAmount());
    }

    @Test
    void compactHoldingsCreatesBaseHoldingWhenNoneExisted() throws SQLException {
        repository.addHolding(alice.getId(), pound, 3); // 300 pence, no existing penny holding

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        ledger.compactHoldings(mock(Connection.class), alice);

        assertEquals(300L, repository.getHolding(alice.getId(), penny.getId()).getAmount());
    }

    @Test
    void compactHoldingsDeletesZeroAmountNonBaseHoldingsWithoutConverting() throws SQLException {
        repository.addHolding(alice.getId(), pound, 0);

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        int converted = ledger.compactHoldings(mock(Connection.class), alice);

        assertEquals(1, converted);
        assertNull(repository.getHolding(alice.getId(), pound.getId()));
        assertNull(repository.getHolding(alice.getId(), penny.getId()), "zero holding should not create a base holding");
    }

    @Test
    void compactHoldingsIsNoOpWhenAccountHasNoNonBaseHoldings() throws SQLException {
        repository.addHolding(alice.getId(), penny, 50);

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        int converted = ledger.compactHoldings(mock(Connection.class), alice);

        assertEquals(0, converted);
        assertEquals(50L, repository.getHolding(alice.getId(), penny.getId()).getAmount());
    }

    // ---- privateTransferAmount ----

    @Test
    void privateTransferAmountMovesFundsBetweenAccounts() throws SQLException, CurrenciesException {
        repository.addHolding(alice.getId(), penny, 500);

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        Transaction t = ledger.privateTransferAmount(mock(Connection.class), alice, bob, gbp, 200);

        assertEquals(300L, repository.getHolding(alice.getId(), penny.getId()).getAmount());
        assertEquals(200L, repository.getHolding(bob.getId(), penny.getId()).getAmount());
        assertEquals(alice, t.getSender());
        assertEquals(bob, t.getRecipient());
        assertEquals(penny, t.getUnit());
        assertEquals(200L, t.getTransactionAmount());
        assertEquals(300L, t.getFinalSenderAmount());
        assertEquals(200L, t.getFinalRecipientAmount());
        assertTrue(t.isPaid());
        assertNotNull(t.getDateCreated());
        assertEquals(t.getDateCreated(), t.getDatePaid());
    }

    @Test
    void privateTransferAmountDeletesSenderHoldingWhenItReachesZero() throws SQLException, CurrenciesException {
        repository.addHolding(alice.getId(), penny, 200);

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        ledger.privateTransferAmount(mock(Connection.class), alice, bob, gbp, 200);

        assertNull(repository.getHolding(alice.getId(), penny.getId()));
    }

    @Test
    void privateTransferAmountTreatsMissingSenderHoldingAsZeroBalance() throws SQLException, CurrenciesException {
        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        Transaction t = ledger.privateTransferAmount(mock(Connection.class), alice, bob, gbp, 100);

        assertEquals(-100L, t.getFinalSenderAmount());
        assertEquals(-100L, repository.getHolding(alice.getId(), penny.getId()).getAmount());
    }

    @Test
    void privateTransferAmountAddsToExistingRecipientHolding() throws SQLException, CurrenciesException {
        repository.addHolding(alice.getId(), penny, 500);
        repository.addHolding(bob.getId(), penny, 50);

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        ledger.privateTransferAmount(mock(Connection.class), alice, bob, gbp, 200);

        assertEquals(250L, repository.getHolding(bob.getId(), penny.getId()).getAmount());
    }

    @Test
    void privateTransferAmountThrowsWhenCurrencyHasNoBaseUnit() {
        Currency noBaseCurrency = Fixtures.currency((short) 9, "XXX", "No Base", true);
        repository.addCurrency(noBaseCurrency);

        Ledger ledger = new Ledger(mock(com.nobleuplift.currencies.ConnectionProvider.class), repository);
        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class,
                () -> ledger.privateTransferAmount(mock(Connection.class), alice, bob, noBaseCurrency, 100));
        assertEquals("Currency XXX has no base unit.", e.getMessage());
    }

    // ---- insertTransaction ----

    @Test
    void insertTransactionWritesAllFieldsAndReturnsGeneratedId() throws SQLException {
        JdbcWriteSupport jdbc = new JdbcWriteSupport();
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getLong(1)).thenReturn(77L);

        Transaction t = new Transaction();
        t.setSender(alice);
        t.setRecipient(bob);
        t.setUnit(penny);
        t.setTypeId(TransactionType.PAY.getId());
        t.setTransactionAmount(200L);
        t.setFinalSenderAmount(300L);
        t.setFinalRecipientAmount(200L);
        t.setPaid(true);
        Timestamp now = Clock.now();
        t.setDateCreated(now);
        t.setDatePaid(now);

        Ledger ledger = new Ledger(jdbc.connectionProvider, repository);
        long id = ledger.insertTransaction(jdbc.connection, t);

        assertEquals(77L, id);
        PreparedStatement ps = jdbc.generatedKeyStatement;
        verify(ps).setInt(1, alice.getId());
        verify(ps).setInt(2, bob.getId());
        verify(ps).setShort(3, penny.getId());
        verify(ps).setShort(4, TransactionType.PAY.getId());
        verify(ps).setLong(5, 200L);
        verify(ps).setLong(6, 300L);
        verify(ps).setLong(7, 200L);
        verify(ps).setBoolean(8, true);
        verify(ps).setTimestamp(9, now);
        verify(ps).setTimestamp(10, now);
    }

    @Test
    void insertTransactionWritesNullsForPendingBillFields() throws SQLException {
        JdbcWriteSupport jdbc = new JdbcWriteSupport();
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getLong(1)).thenReturn(5L);

        Transaction t = new Transaction();
        t.setSender(alice);
        t.setRecipient(bob);
        t.setUnit(penny);
        t.setTypeId(TransactionType.BILL.getId());
        t.setTransactionAmount(200L);
        t.setFinalSenderAmount(null);
        t.setFinalRecipientAmount(null);
        t.setPaid(null);
        t.setDatePaid(null);
        t.setDateCreated(null); // should fall back to Clock.now()

        Ledger ledger = new Ledger(jdbc.connectionProvider, repository);
        ledger.insertTransaction(jdbc.connection, t);

        PreparedStatement ps = jdbc.generatedKeyStatement;
        verify(ps).setNull(6, java.sql.Types.BIGINT);
        verify(ps).setNull(7, java.sql.Types.BIGINT);
        verify(ps).setNull(8, java.sql.Types.TINYINT);
        verify(ps).setNull(9, java.sql.Types.TIMESTAMP);
        verify(ps).setTimestamp(org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.any(Timestamp.class));
    }

    @Test
    void insertTransactionReturnsNegativeOneWhenNoKeyIsGenerated() throws SQLException {
        JdbcWriteSupport jdbc = new JdbcWriteSupport();
        when(jdbc.generatedKeys.next()).thenReturn(false);

        Transaction t = new Transaction();
        t.setSender(alice);
        t.setRecipient(bob);
        t.setUnit(penny);
        t.setTypeId(TransactionType.PAY.getId());
        t.setTransactionAmount(1L);
        t.setDateCreated(Clock.now());

        Ledger ledger = new Ledger(jdbc.connectionProvider, repository);
        long id = ledger.insertTransaction(jdbc.connection, t);

        assertEquals(-1L, id);
    }

    // ---- transferAmount (public entry point) ----

    @Test
    void transferAmountOpensOwnConnectionAndPersistsTheTransaction() throws SQLException, CurrenciesException {
        repository.addHolding(alice.getId(), penny, 500);
        JdbcWriteSupport jdbc = new JdbcWriteSupport();
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getLong(1)).thenReturn(42L);

        Ledger ledger = new Ledger(jdbc.connectionProvider, repository);
        Transaction t = ledger.transferAmount(alice, bob, gbp, 200);

        assertEquals(42L, t.getId());
        assertEquals(TransactionType.PAY.getId(), t.getTypeId());
        verify(jdbc.connection).commit();
        assertEquals(300L, repository.getHolding(alice.getId(), penny.getId()).getAmount());
    }

    @Test
    void transferAmountRollsBackAndRethrowsWhenNoBaseUnitExists() throws SQLException {
        Currency noBaseCurrency = Fixtures.currency((short) 9, "XXX", "No Base", true);
        repository.addCurrency(noBaseCurrency);
        JdbcWriteSupport jdbc = new JdbcWriteSupport();

        Ledger ledger = new Ledger(jdbc.connectionProvider, repository);

        assertThrows(CurrenciesRuntimeException.class, () -> ledger.transferAmount(alice, bob, noBaseCurrency, 100));
    }

    @Test
    void transferAmountWrapsConnectionFailureAsRuntimeException() throws SQLException {
        com.nobleuplift.currencies.ConnectionProvider provider = mock(com.nobleuplift.currencies.ConnectionProvider.class);
        when(provider.getConnection()).thenThrow(new SQLException("connection pool exhausted"));

        Ledger ledger = new Ledger(provider, repository);

        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class,
                () -> ledger.transferAmount(alice, bob, gbp, 100));
        assertTrue(e.getMessage().contains("Failed to get connection in transferAmount"));
    }
}
