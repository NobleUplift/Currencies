package com.nobleuplift.currencies.services;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.Unit;

/**
 * Persistence port for Currencies data access. Every method accepts an
 * already-open {@link Connection} so that callers keep ownership of the
 * transaction boundary (multiple repository calls can participate in the
 * same transaction).
 */
public interface CurrencyRepository {

    Account queryAccountByName(Connection conn, String name) throws SQLException;

    Account queryAccountByUuid(Connection conn, String uuid) throws SQLException;

    Account queryAccountById(Connection conn, int id) throws SQLException;

    List<Account> queryAccountsWithUuid(Connection conn) throws SQLException;

    Currency queryCurrencyById(Connection conn, short id) throws SQLException;

    Currency queryCurrencyByAcronym(Connection conn, String acronym) throws SQLException;

    List<Currency> queryCurrenciesPage(Connection conn, int offset) throws SQLException;

    List<Currency> queryAllCurrencies(Connection conn) throws SQLException;

    Currency queryGlobalDefaultCurrency(Connection conn) throws SQLException;

    Unit queryUnitById(Connection conn, short id) throws SQLException;

    Unit queryBaseUnit(Connection conn, Currency currency) throws SQLException;

    Unit queryPrimeUnit(Connection conn, Currency currency) throws SQLException;

    Unit queryUnitBySymbolAndCurrency(Connection conn, Currency currency, String symbol) throws SQLException;

    Unit queryUnitByName(Connection conn, Currency currency, String name) throws SQLException;

    Unit queryUnitByAlternate(Connection conn, Currency currency, String alternate) throws SQLException;

    Unit queryPrimeUnitBySymbol(Connection conn, String symbol) throws SQLException;

    Unit queryUnitByChildAndBaseMultiples(Connection conn, Currency currency, Unit childUnit, int baseMultiples) throws SQLException;

    List<Unit> queryAllUnitsForCurrency(Connection conn, Currency currency) throws SQLException;

    List<Unit> queryUnitsOrdered(Connection conn, Currency currency) throws SQLException;

    List<Unit> queryMainUnitsForCurrencyDescending(Connection conn, Currency currency) throws SQLException;

    List<Unit> queryPrimeUnitsBySymbol(Connection conn, String symbol) throws SQLException;

    Holding queryBaseHolding(Connection conn, int accountId, short unitId) throws SQLException;

    List<Holding> queryHoldingsWithUnitAndCurrency(Connection conn, int accountId) throws SQLException;

    List<Holding> queryHoldingsForAccountAndCurrency(Connection conn, int accountId, short currencyId) throws SQLException;

    List<Holding> queryNonBaseHoldings(Connection conn, int accountId) throws SQLException;

    List<Holding> queryBaseHoldings(Connection conn, int accountId) throws SQLException;

    List<Transaction> queryPendingBillsForSender(Connection conn, int senderId) throws SQLException;

    Transaction queryTransactionById(Connection conn, long id) throws SQLException;

    List<Transaction> queryTransactionsForAccountPage(Connection conn, int accountId, int offset) throws SQLException;

    void upsertHolding(Connection conn, int accountId, short unitId, long amount) throws SQLException;

    void deleteHolding(Connection conn, int accountId, short unitId) throws SQLException;
}
