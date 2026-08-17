package com.nobleuplift.currencies.services;

import static com.nobleuplift.currencies.services.RowResultSet.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionType;
import com.nobleuplift.currencies.entities.Unit;

class JdbcCurrencyRepositoryTest {

    private JdbcCurrencyRepository repository;
    private Connection conn;
    private PreparedStatement ps;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new JdbcCurrencyRepository();
        conn = mock(Connection.class);
        ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
    }

    private void returning(ResultSet rs) throws SQLException {
        when(ps.executeQuery()).thenReturn(rs);
    }

    // ---- accounts ----

    @Test
    void queryAccountByNameMapsRowWithDefaultCurrency() throws SQLException {
        Timestamp created = Timestamp.valueOf("2020-01-01 00:00:00");
        returning(RowResultSet.of(row(
                "id", 10, "name", "Alice", "uuid", "uuid-1",
                "date_created", created, "date_modified", created,
                "dc_id", 1, "dc_name", "Pound Sterling", "dc_acronym", "GBP",
                "dc_prefix", true, "dc_global_default", false)));

        Account a = repository.queryAccountByName(conn, "Alice");

        assertEquals(10, a.getId());
        assertEquals("Alice", a.getName());
        assertEquals("uuid-1", a.getUuid());
        assertEquals(created, a.getDateCreated());
        assertEquals((short) 1, a.getDefaultCurrency().getId());
        assertEquals("GBP", a.getDefaultCurrency().getAcronym());
    }

    @Test
    void queryAccountByNameLeavesDefaultCurrencyNullWhenNoneSet() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 10, "name", "Alice", "uuid", null,
                "date_created", null, "date_modified", null,
                "dc_id", null, "dc_name", null, "dc_acronym", null,
                "dc_prefix", null, "dc_global_default", null)));

        Account a = repository.queryAccountByName(conn, "Alice");

        assertNull(a.getDefaultCurrency());
    }

    @Test
    void queryAccountByNameReturnsNullWhenNotFound() throws SQLException {
        returning(RowResultSet.empty());

        assertNull(repository.queryAccountByName(conn, "Nobody"));
    }

    @Test
    void queryAccountByUuidMapsMatchingRow() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 11, "name", "Bob", "uuid", "uuid-2",
                "date_created", null, "date_modified", null,
                "dc_id", null, "dc_name", null, "dc_acronym", null,
                "dc_prefix", null, "dc_global_default", null)));

        Account a = repository.queryAccountByUuid(conn, "uuid-2");

        assertEquals(11, a.getId());
        assertEquals("uuid-2", a.getUuid());
    }

    @Test
    void queryAccountByIdMapsMatchingRow() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 12, "name", "Carol", "uuid", null,
                "date_created", null, "date_modified", null,
                "dc_id", null, "dc_name", null, "dc_acronym", null,
                "dc_prefix", null, "dc_global_default", null)));

        Account a = repository.queryAccountById(conn, 12);

        assertEquals(12, a.getId());
        assertEquals("Carol", a.getName());
    }

    // ---- currencies ----

    @Test
    void queryCurrencyByIdMapsRow() throws SQLException {
        Timestamp created = Timestamp.valueOf("2020-01-01 00:00:00");
        returning(RowResultSet.of(row(
                "id", 1, "name", "Pound Sterling", "acronym", "GBP", "prefix", true,
                "default_currency", false, "date_created", created, "date_modified", created, "date_deleted", null)));

        Currency c = repository.queryCurrencyById(conn, (short) 1);

        assertEquals((short) 1, c.getId());
        assertEquals("GBP", c.getAcronym());
        assertTrue(c.isPrefix());
        assertNull(c.getDateDeleted());
    }

    @Test
    void queryCurrencyByIdReturnsNullWhenNotFound() throws SQLException {
        returning(RowResultSet.empty());

        assertNull(repository.queryCurrencyById(conn, (short) 99));
    }

    @Test
    void queryCurrencyByAcronymMapsRow() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 1, "name", "Pound Sterling", "acronym", "GBP", "prefix", true,
                "default_currency", false, "date_created", null, "date_modified", null, "date_deleted", null)));

        Currency c = repository.queryCurrencyByAcronym(conn, "GBP");

        assertEquals("GBP", c.getAcronym());
    }

    @Test
    void queryCurrenciesPageMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(
                row("id", 1, "name", "Pound Sterling", "acronym", "GBP", "prefix", true,
                        "default_currency", false, "date_created", null, "date_modified", null, "date_deleted", null),
                row("id", 2, "name", "US Dollar", "acronym", "USD", "prefix", true,
                        "default_currency", false, "date_created", null, "date_modified", null, "date_deleted", null)));

        List<Currency> page = repository.queryCurrenciesPage(conn, 0);

        assertEquals(2, page.size());
        assertEquals("GBP", page.get(0).getAcronym());
        assertEquals("USD", page.get(1).getAcronym());
    }

    // ---- units ----

    @Test
    void queryUnitByIdMapsUnitWithCurrency() throws SQLException {
        returning(RowResultSet.of(row(
                "u_id", 1, "currency_id", 1, "child_unit_id", null, "u_name", "Pound", "u_alternate", "Pounds",
                "symbol", "£", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100,
                "c_id", 1, "c_name", "Pound Sterling", "acronym", "GBP", "c_prefix", true, "c_global_default", false)));

        Unit u = repository.queryUnitById(conn, (short) 1);

        assertEquals((short) 1, u.getId());
        assertEquals("Pound", u.getName());
        assertEquals("£", u.getSymbol());
        assertTrue(u.isPrime());
        assertEquals(100, u.getBaseMultiples());
        assertEquals("GBP", u.getCurrency().getAcronym());
    }

    @Test
    void queryBaseUnitReturnsUnitWithChildUnitIdNull() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 2, "currency_id", 1, "child_unit_id", null, "name", "Penny", "alternate", "Pennies",
                "symbol", "p", "prime", false, "main", true, "child_multiples", 0, "base_multiples", 0)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        Unit u = repository.queryBaseUnit(conn, gbp);

        assertEquals((short) 2, u.getId());
        assertEquals(gbp, u.getCurrency());
    }

    @Test
    void queryPrimeUnitMapsFlaggedUnit() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 1, "currency_id", 1, "child_unit_id", 2, "name", "Pound", "alternate", "Pounds",
                "symbol", "£", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        Unit u = repository.queryPrimeUnit(conn, gbp);

        assertTrue(u.isPrime());
        assertEquals(gbp, u.getCurrency());
    }

    @Test
    void queryUnitBySymbolAndCurrencyResolvesChildUnitStub() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 1, "currency_id", 1, "child_unit_id", 2, "name", "Pound", "alternate", "Pounds",
                "symbol", "£", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        Unit u = repository.queryUnitBySymbolAndCurrency(conn, gbp, "£");

        assertEquals((short) 2, u.getChildUnit().getId());
    }

    @Test
    void queryUnitBySymbolAndCurrencyLeavesChildUnitNullWhenColumnIsNull() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 2, "currency_id", 1, "child_unit_id", null, "name", "Penny", "alternate", "Pennies",
                "symbol", "p", "prime", false, "main", true, "child_multiples", 0, "base_multiples", 0)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        Unit u = repository.queryUnitBySymbolAndCurrency(conn, gbp, "p");

        assertNull(u.getChildUnit());
    }

    @Test
    void queryUnitByNameReturnsIdOnlyStub() throws SQLException {
        returning(RowResultSet.of(row("id", 5)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        Unit u = repository.queryUnitByName(conn, gbp, "Farthing");

        assertEquals((short) 5, u.getId());
    }

    @Test
    void queryUnitByAlternateReturnsIdOnlyStub() throws SQLException {
        returning(RowResultSet.of(row("id", 6)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        assertEquals((short) 6, repository.queryUnitByAlternate(conn, gbp, "Farthings").getId());
    }

    @Test
    void queryPrimeUnitBySymbolReturnsIdOnlyStub() throws SQLException {
        returning(RowResultSet.of(row("id", 7)));

        assertEquals((short) 7, repository.queryPrimeUnitBySymbol(conn, "$").getId());
    }

    @Test
    void queryPrimeUnitBySymbolReturnsNullWhenNotFound() throws SQLException {
        returning(RowResultSet.empty());

        assertNull(repository.queryPrimeUnitBySymbol(conn, "$"));
    }

    @Test
    void queryUnitByChildAndBaseMultiplesReturnsIdOnlyStub() throws SQLException {
        returning(RowResultSet.of(row("id", 8)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        Unit penny = Fixtures.baseUnit((short) 2, gbp, "Penny", "p");

        assertEquals((short) 8, repository.queryUnitByChildAndBaseMultiples(conn, gbp, penny, 20).getId());
    }

    @Test
    void queryAllUnitsForCurrencyMapsEveryRowAndResolvesChildStub() throws SQLException {
        returning(RowResultSet.of(
                row("id", 1, "currency_id", 1, "child_unit_id", 2, "name", "Pound", "alternate", "Pounds",
                        "symbol", "£", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100),
                row("id", 2, "currency_id", 1, "child_unit_id", null, "name", "Penny", "alternate", "Pennies",
                        "symbol", "p", "prime", false, "main", true, "child_multiples", 0, "base_multiples", 0)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        List<Unit> units = repository.queryAllUnitsForCurrency(conn, gbp);

        assertEquals(2, units.size());
        assertEquals((short) 2, units.get(0).getChildUnit().getId());
        assertNull(units.get(1).getChildUnit());
    }

    @Test
    void queryUnitsOrderedMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 1, "currency_id", 1, "child_unit_id", null, "name", "Pound", "alternate", "Pounds",
                "symbol", "£", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        List<Unit> units = repository.queryUnitsOrdered(conn, gbp);

        assertEquals(1, units.size());
        assertEquals("Pound", units.get(0).getName());
    }

    @Test
    void queryMainUnitsForCurrencyDescendingMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(row(
                "id", 1, "currency_id", 1, "child_unit_id", null, "name", "Pound", "alternate", "Pounds",
                "symbol", "£", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100)));
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);

        List<Unit> units = repository.queryMainUnitsForCurrencyDescending(conn, gbp);

        assertEquals(100, units.get(0).getBaseMultiples());
    }

    @Test
    void queryPrimeUnitsBySymbolMapsEveryRowWithCurrency() throws SQLException {
        returning(RowResultSet.of(row(
                "u_id", 1, "currency_id", 1, "child_unit_id", null, "u_name", "Dollar", "u_alternate", "Dollars",
                "symbol", "$", "prime", true, "main", true, "child_multiples", 0, "base_multiples", 100,
                "c_id", 2, "c_name", "US Dollar", "acronym", "USD", "c_prefix", true, "c_global_default", false)));

        List<Unit> units = repository.queryPrimeUnitsBySymbol(conn, "$");

        assertEquals(1, units.size());
        assertEquals("USD", units.get(0).getCurrency().getAcronym());
    }

    // ---- holdings ----

    @Test
    void queryBaseHoldingMapsAccountUnitAmount() throws SQLException {
        returning(RowResultSet.of(row("account_id", 10, "unit_id", 2, "amount", 500L)));

        Holding h = repository.queryBaseHolding(conn, 10, (short) 2);

        assertEquals(10, h.getId().getAccountId());
        assertEquals((short) 2, h.getId().getUnitId());
        assertEquals(500L, h.getAmount());
    }

    @Test
    void queryBaseHoldingReturnsNullWhenNotFound() throws SQLException {
        returning(RowResultSet.empty());

        assertNull(repository.queryBaseHolding(conn, 10, (short) 2));
    }

    private static java.util.Map<String, Object> holdingRow(int accountId, short unitId, long amount) {
        return row("account_id", accountId, "unit_id", unitId, "amount", amount,
                "u_id", unitId, "currency_id", 1, "child_unit_id", null, "u_name", "Penny", "u_alternate", "Pennies",
                "symbol", "p", "prime", false, "main", true, "child_multiples", 0, "base_multiples", 0,
                "c_id", 1, "c_name", "Pound Sterling", "acronym", "GBP", "c_prefix", true, "c_global_default", false);
    }

    @Test
    void queryHoldingsWithUnitAndCurrencyMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(holdingRow(10, (short) 2, 500L)));

        List<Holding> holdings = repository.queryHoldingsWithUnitAndCurrency(conn, 10);

        assertEquals(1, holdings.size());
        assertEquals("GBP", holdings.get(0).getUnit().getCurrency().getAcronym());
    }

    @Test
    void queryHoldingsForAccountAndCurrencyMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(holdingRow(10, (short) 2, 500L)));

        List<Holding> holdings = repository.queryHoldingsForAccountAndCurrency(conn, 10, (short) 1);

        assertEquals(500L, holdings.get(0).getAmount());
    }

    @Test
    void queryNonBaseHoldingsMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(holdingRow(10, (short) 1, 2L)));

        List<Holding> holdings = repository.queryNonBaseHoldings(conn, 10);

        assertEquals(1, holdings.size());
    }

    @Test
    void queryBaseHoldingsMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(holdingRow(10, (short) 2, 500L)));

        List<Holding> holdings = repository.queryBaseHoldings(conn, 10);

        assertEquals(1, holdings.size());
    }

    // ---- transactions ----

    private static java.util.Map<String, Object> transactionRow(long id, Boolean paid) {
        return row("id", id, "sender_id", 10, "recipient_id", 11, "unit_id", 2, "type_id", TransactionType.BILL.getId(),
                "transaction_amount", 500L, "final_sender_amount", null, "final_recipient_amount", null,
                "paid", paid, "date_paid", null, "date_created", null,
                "sa_id", 10, "sa_name", "Alice", "ra_id", 11, "ra_name", "Bob",
                "u_id", 2, "currency_id", 1, "child_unit_id", null, "u_name", "Penny", "u_alternate", "Pennies",
                "symbol", "p", "prime", false, "main", true, "child_multiples", 0, "base_multiples", 0,
                "c_id", 1, "c_name", "Pound Sterling", "acronym", "GBP", "c_prefix", true, "c_global_default", false);
    }

    @Test
    void queryPendingBillsForSenderMapsPendingBillWithNullPaid() throws SQLException {
        returning(RowResultSet.of(transactionRow(1L, null)));

        List<Transaction> bills = repository.queryPendingBillsForSender(conn, 10);

        assertEquals(1, bills.size());
        assertNull(bills.get(0).isPaid());
        assertEquals("Alice", bills.get(0).getSender().getName());
        assertEquals("Bob", bills.get(0).getRecipient().getName());
    }

    @Test
    void queryTransactionByIdMapsPaidFlagWhenNotNull() throws SQLException {
        returning(RowResultSet.of(transactionRow(2L, true)));

        Transaction t = repository.queryTransactionById(conn, 2L);

        assertEquals(Boolean.TRUE, t.isPaid());
        assertEquals(TransactionType.BILL.getId(), t.getTypeId());
    }

    @Test
    void queryTransactionByIdReturnsNullWhenNotFound() throws SQLException {
        returning(RowResultSet.empty());

        assertNull(repository.queryTransactionById(conn, 999L));
    }

    @Test
    void queryTransactionsForAccountPageMapsEveryRow() throws SQLException {
        returning(RowResultSet.of(transactionRow(3L, false)));

        List<Transaction> txns = repository.queryTransactionsForAccountPage(conn, 10, 0);

        assertEquals(1, txns.size());
        assertEquals(Boolean.FALSE, txns.get(0).isPaid());
    }

    // ---- writes ----

    @Test
    void upsertHoldingBindsAccountUnitAndAmount() throws SQLException {
        repository.upsertHolding(conn, 10, (short) 2, 500L);

        verify(ps).setInt(1, 10);
        verify(ps).setShort(2, (short) 2);
        verify(ps).setLong(3, 500L);
        verify(ps).executeUpdate();
    }

    @Test
    void deleteHoldingBindsAccountAndUnit() throws SQLException {
        repository.deleteHolding(conn, 10, (short) 2);

        verify(ps).setInt(1, 10);
        verify(ps).setShort(2, (short) 2);
        verify(ps).executeUpdate();
    }
}
