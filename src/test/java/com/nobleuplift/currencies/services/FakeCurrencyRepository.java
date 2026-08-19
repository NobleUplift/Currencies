package com.nobleuplift.currencies.services;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
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

/**
 * In-memory fake standing in for {@link JdbcCurrencyRepository} in tests. Ignores the
 * {@code Connection} argument entirely (it never touches JDBC), so callers can pass any
 * placeholder {@code Connection}, including a bare Mockito mock.
 */
class FakeCurrencyRepository implements CurrencyRepository {

    private final Map<Integer, Account> accountsById = new HashMap<>();
    private final Map<Short, Currency> currenciesById = new HashMap<>();
    private final Map<Short, Unit> unitsById = new HashMap<>();
    private final Map<String, Holding> holdings = new HashMap<>();
    private final Map<Long, Transaction> transactionsById = new HashMap<>();
    private long nextTransactionId = 1L;

    void addAccount(Account account) {
        accountsById.put(account.getId(), account);
    }

    void addCurrency(Currency currency) {
        currenciesById.put(currency.getId(), currency);
    }

    void addUnit(Unit unit) {
        unitsById.put(unit.getId(), unit);
    }

    void addHolding(int accountId, Unit unit, long amount) {
        Holding h = new Holding();
        HoldingPK pk = new HoldingPK();
        pk.setAccountId(accountId);
        pk.setUnitId(unit.getId());
        h.setId(pk);
        h.setUnit(unit);
        h.setAmount(amount);
        holdings.put(holdingKey(accountId, unit.getId()), h);
    }

    Transaction addTransaction(Transaction t) {
        if (t.getId() == null) {
            t.setId(nextTransactionId++);
        }
        transactionsById.put(t.getId(), t);
        return t;
    }

    Holding getHolding(int accountId, short unitId) {
        return holdings.get(holdingKey(accountId, unitId));
    }

    private static String holdingKey(int accountId, short unitId) {
        return accountId + ":" + unitId;
    }

    @Override
    public Account queryAccountByName(Connection conn, String name) {
        for (Account a : accountsById.values()) {
            if (a.getName() != null && a.getName().equals(name)) {
                return a;
            }
        }
        return null;
    }

    @Override
    public Account queryAccountByUuid(Connection conn, String uuid) {
        for (Account a : accountsById.values()) {
            if (a.getUuid() != null && a.getUuid().equals(uuid)) {
                return a;
            }
        }
        return null;
    }

    @Override
    public Account queryAccountById(Connection conn, int id) {
        return accountsById.get(id);
    }

    @Override
    public List<Account> queryAccountsWithUuid(Connection conn) {
        List<Account> result = new ArrayList<>();
        for (Account a : accountsById.values()) {
            if (a.getUuid() != null) {
                result.add(a);
            }
        }
        result.sort(Comparator.comparing(Account::getId));
        return result;
    }

    @Override
    public Currency queryCurrencyById(Connection conn, short id) {
        return currenciesById.get(id);
    }

    @Override
    public Currency queryCurrencyByAcronym(Connection conn, String acronym) {
        for (Currency c : currenciesById.values()) {
            if (c.getAcronym() != null && c.getAcronym().equals(acronym)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public List<Currency> queryCurrenciesPage(Connection conn, int offset) {
        List<Currency> all = new ArrayList<>();
        for (Currency c : currenciesById.values()) {
            if (c.getDateDeleted() == null) {
                all.add(c);
            }
        }
        all.sort(Comparator.comparing(Currency::getId));
        int from = Math.min(offset, all.size());
        int to = Math.min(offset + 10, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    @Override
    public List<Currency> queryAllCurrencies(Connection conn) {
        List<Currency> all = new ArrayList<>();
        for (Currency c : currenciesById.values()) {
            if (c.getDateDeleted() == null) {
                all.add(c);
            }
        }
        all.sort(Comparator.comparing(Currency::getId));
        return all;
    }

    @Override
    public Currency queryGlobalDefaultCurrency(Connection conn) {
        for (Currency c : currenciesById.values()) {
            if (c.isGlobalDefault() && c.getDateDeleted() == null) {
                return c;
            }
        }
        return null;
    }

    @Override
    public Unit queryUnitById(Connection conn, short id) {
        return unitsById.get(id);
    }

    @Override
    public Unit queryBaseUnit(Connection conn, Currency currency) {
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency) && u.getChildUnit() == null) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Unit queryPrimeUnit(Connection conn, Currency currency) {
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency) && u.isPrime()) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Unit queryUnitBySymbolAndCurrency(Connection conn, Currency currency, String symbol) {
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency) && u.getSymbol() != null && u.getSymbol().equals(symbol)) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Unit queryUnitByName(Connection conn, Currency currency, String name) {
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency) && u.getName() != null && u.getName().equals(name)) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Unit queryUnitByAlternate(Connection conn, Currency currency, String alternate) {
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency) && u.getAlternate() != null && u.getAlternate().equals(alternate)) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Unit queryPrimeUnitBySymbol(Connection conn, String symbol) {
        for (Unit u : unitsById.values()) {
            if (u.isPrime() && u.getSymbol() != null && u.getSymbol().equals(symbol)) {
                return u;
            }
        }
        return null;
    }

    @Override
    public Unit queryUnitByChildAndBaseMultiples(Connection conn, Currency currency, Unit childUnit, int baseMultiples) {
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency)
                    && u.getChildUnit() != null
                    && u.getChildUnit().getId().equals(childUnit.getId())
                    && u.getBaseMultiples() == baseMultiples) {
                return u;
            }
        }
        return null;
    }

    @Override
    public List<Unit> queryAllUnitsForCurrency(Connection conn, Currency currency) {
        List<Unit> result = new ArrayList<>();
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency)) {
                result.add(u);
            }
        }
        return result;
    }

    @Override
    public List<Unit> queryUnitsOrdered(Connection conn, Currency currency) {
        List<Unit> result = queryAllUnitsForCurrency(conn, currency);
        result.sort(Comparator
                .comparing(Unit::isPrime).reversed()
                .thenComparing(Comparator.comparing(Unit::isMain).reversed())
                .thenComparing(Comparator.comparingInt(Unit::getBaseMultiples).reversed()));
        return result;
    }

    @Override
    public List<Unit> queryMainUnitsForCurrencyDescending(Connection conn, Currency currency) {
        List<Unit> result = new ArrayList<>();
        for (Unit u : unitsById.values()) {
            if (sameCurrency(u, currency) && u.isMain()) {
                result.add(u);
            }
        }
        result.sort(Comparator.comparingInt(Unit::getBaseMultiples).reversed());
        return result;
    }

    @Override
    public List<Unit> queryPrimeUnitsBySymbol(Connection conn, String symbol) {
        List<Unit> result = new ArrayList<>();
        for (Unit u : unitsById.values()) {
            if (u.isPrime() && u.getSymbol() != null && u.getSymbol().equals(symbol)) {
                result.add(u);
            }
        }
        return result;
    }

    @Override
    public Holding queryBaseHolding(Connection conn, int accountId, short unitId) {
        return holdings.get(holdingKey(accountId, unitId));
    }

    @Override
    public List<Holding> queryHoldingsWithUnitAndCurrency(Connection conn, int accountId) {
        List<Holding> result = new ArrayList<>();
        for (Holding h : holdings.values()) {
            if (h.getId().getAccountId() == accountId) {
                result.add(h);
            }
        }
        return result;
    }

    @Override
    public List<Holding> queryHoldingsForAccountAndCurrency(Connection conn, int accountId, short currencyId) {
        List<Holding> result = new ArrayList<>();
        for (Holding h : holdings.values()) {
            if (h.getId().getAccountId() == accountId && h.getUnit().getCurrency().getId().equals(currencyId)) {
                result.add(h);
            }
        }
        return result;
    }

    @Override
    public List<Holding> queryNonBaseHoldings(Connection conn, int accountId) {
        List<Holding> result = new ArrayList<>();
        for (Holding h : holdings.values()) {
            if (h.getId().getAccountId() == accountId && h.getUnit().getChildUnit() != null) {
                result.add(h);
            }
        }
        return result;
    }

    @Override
    public List<Holding> queryBaseHoldings(Connection conn, int accountId) {
        List<Holding> result = new ArrayList<>();
        for (Holding h : holdings.values()) {
            if (h.getId().getAccountId() == accountId && h.getUnit().getChildUnit() == null) {
                result.add(h);
            }
        }
        return result;
    }

    @Override
    public List<Transaction> queryPendingBillsForSender(Connection conn, int senderId) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : transactionsById.values()) {
            if (t.getSender().getId() == senderId && t.isPaid() == null && t.getTypeId() == TransactionType.BILL.getId()) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    public Transaction queryTransactionById(Connection conn, long id) {
        return transactionsById.get(id);
    }

    @Override
    public List<Transaction> queryTransactionsForAccountPage(Connection conn, int accountId, int offset) {
        List<Transaction> all = new ArrayList<>();
        for (Transaction t : transactionsById.values()) {
            if (t.getSender().getId() == accountId || t.getRecipient().getId() == accountId) {
                all.add(t);
            }
        }
        all.sort(Comparator.comparing(Transaction::getDateCreated, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min(offset, all.size());
        int to = Math.min(offset + 10, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    @Override
    public void upsertHolding(Connection conn, int accountId, short unitId, long amount) {
        String key = holdingKey(accountId, unitId);
        Holding existing = holdings.get(key);
        if (existing != null) {
            existing.setAmount(amount);
            return;
        }
        Holding h = new Holding();
        HoldingPK pk = new HoldingPK();
        pk.setAccountId(accountId);
        pk.setUnitId(unitId);
        h.setId(pk);
        h.setUnit(unitsById.get(unitId));
        h.setAmount(amount);
        holdings.put(key, h);
    }

    @Override
    public void deleteHolding(Connection conn, int accountId, short unitId) {
        holdings.remove(holdingKey(accountId, unitId));
    }

    private static boolean sameCurrency(Unit u, Currency currency) {
        return u.getCurrency() != null && u.getCurrency().getId().equals(currency.getId());
    }
}
