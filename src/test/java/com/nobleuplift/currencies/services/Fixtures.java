package com.nobleuplift.currencies.services;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

/** Small entity-construction helpers shared across service tests. */
final class Fixtures {

    private Fixtures() {
    }

    static Account account(int id, String name) {
        Account a = new Account();
        a.setId(id);
        a.setName(name);
        return a;
    }

    static Currency currency(short id, String acronym, String name, boolean prefix) {
        Currency c = new Currency();
        c.setId(id);
        c.setAcronym(acronym);
        c.setName(name);
        c.setPrefix(prefix);
        return c;
    }

    /** A prime+base unit in one (no parent/child) -- the simplest possible currency shape. */
    static Unit primeBaseUnit(short id, Currency currency, String name, String symbol) {
        Unit u = new Unit();
        u.setId(id);
        u.setCurrency(currency);
        u.setName(name);
        u.setAlternate(name + "s");
        u.setSymbol(symbol);
        u.setPrime(true);
        u.setMain(true);
        u.setBaseMultiples(0);
        u.setChildMultiples(0);
        return u;
    }

    /** A non-base unit whose base-unit equivalent is baseMultiples of childUnit. */
    static Unit parentUnit(short id, Currency currency, String name, String symbol, boolean prime, int baseMultiples, Unit childUnit) {
        Unit u = new Unit();
        u.setId(id);
        u.setCurrency(currency);
        u.setName(name);
        u.setAlternate(name + "s");
        u.setSymbol(symbol);
        u.setPrime(prime);
        u.setMain(true);
        u.setBaseMultiples(baseMultiples);
        u.setChildUnit(childUnit);
        return u;
    }

    /** The base (smallest) unit of a currency -- no child, not prime. */
    static Unit baseUnit(short id, Currency currency, String name, String symbol) {
        Unit u = new Unit();
        u.setId(id);
        u.setCurrency(currency);
        u.setName(name);
        u.setAlternate(name + "s");
        u.setSymbol(symbol);
        u.setPrime(false);
        u.setMain(true);
        u.setBaseMultiples(0);
        return u;
    }
}
