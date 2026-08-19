package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

class CurrencyServiceTest {

    private FakeCurrencyRepository repository;
    private JdbcWriteSupport jdbc;
    private CurrencyService currencyService;

    private Currency gbp;
    private Unit pound;
    private Unit penny;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();
        jdbc = new JdbcWriteSupport();
        currencyService = new CurrencyService(jdbc.connectionProvider, repository);

        gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        repository.addCurrency(gbp);
        penny = Fixtures.baseUnit((short) 2, gbp, "Penny", "p");
        pound = Fixtures.parentUnit((short) 1, gbp, "Pound", "£", true, 100, penny);
        repository.addUnit(pound);
        repository.addUnit(penny);
    }

    // ---- createCurrency ----

    @Test
    void createCurrencyRejectsAcronymNotThreeCharacters() throws SQLException {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.createCurrency("US", "US Dollar"));
        assertEquals("All currency acronyms must be three characters.", e.getMessage());
        verify(jdbc.connection, never()).prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createCurrencySucceedsAndCommitsWhenAcronymAndNameAreFree() throws CurrenciesException, SQLException {
        when(jdbc.resultSet.next()).thenReturn(false);

        currencyService.createCurrency("USD", "US Dollar", true);

        verify(jdbc.connection).commit();
    }

    @Test
    void createCurrencyTwoArgOverloadDefaultsPrefixTrue() throws CurrenciesException, SQLException {
        when(jdbc.resultSet.next()).thenReturn(false);

        currencyService.createCurrency("USD", "US Dollar");

        verify(jdbc.connection).commit();
    }

    @Test
    void createCurrencyRejectsTakenAcronymAndRollsBack() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(true);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.createCurrency("GBP", "Pound Sterling", true));
        assertEquals("GBP has been taken by another currency.", e.getMessage());
        verify(jdbc.connection).rollback();
    }

    @Test
    void createCurrencyRejectsTakenNameAndRollsBack() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(false, true);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.createCurrency("USD", "Pound Sterling", true));
        assertEquals("Pound Sterling has been taken by another currency.", e.getMessage());
        verify(jdbc.connection).rollback();
    }

    // ---- deleteCurrency ----

    @Test
    void deleteCurrencyThrowsAndRollsBackWhenAcronymUnknown() throws SQLException {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.deleteCurrency("ZZZ"));
        assertEquals("Could not find currency with acronym ZZZ.", e.getMessage());
        verify(jdbc.connection).rollback();
    }

    @Test
    void deleteCurrencyCommitsWhenCurrencyExists() throws CurrenciesException, SQLException {
        currencyService.deleteCurrency("GBP");

        verify(jdbc.connection).commit();
    }

    // ---- addPrime ----

    @Test
    void addPrimeThrowsWhenCurrencyDoesNotExist() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addPrime("ZZZ", "Zed", "Zeds", "Z"));
        assertEquals("Currency with acronym ZZZ does not exist.", e.getMessage());
    }

    @Test
    void addPrimeThrowsWhenCurrencyAlreadyHasPrimeUnit() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addPrime("GBP", "Dollar", "Dollars", "$"));
        assertEquals("Currency GBP already has a prime unit of currency.", e.getMessage());
    }

    @Test
    void addPrimeThrowsWhenSymbolTooLong() {
        Currency usd = Fixtures.currency((short) 5, "USD", "US Dollar", true);
        repository.addCurrency(usd);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addPrime("USD", "Dollar", "Dollars", "USD"));
        assertEquals("Symbol can be no more than two characters.", e.getMessage());
    }

    @Test
    void addPrimeThrowsWhenSymbolContainsDigits() {
        Currency usd = Fixtures.currency((short) 5, "USD", "US Dollar", true);
        repository.addCurrency(usd);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addPrime("USD", "Dollar", "Dollars", "$1"));
        assertEquals("Symbol cannot contain numbers.", e.getMessage());
    }

    @Test
    void addPrimeSucceedsAndCommits() throws CurrenciesException, SQLException {
        Currency usd = Fixtures.currency((short) 5, "USD", "US Dollar", true);
        repository.addCurrency(usd);

        currencyService.addPrime("USD", "Dollar", "Dollars", "$");

        verify(jdbc.connection).commit();
    }

    // ---- addParent ----

    @Test
    void addParentThrowsWhenCurrencyDoesNotExist() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("ZZZ", "Note", "Notes", "N", 5, "p"));
        assertEquals("Currency with acronym ZZZ does not exist.", e.getMessage());
    }

    @Test
    void addParentPropagatesValidationFailureFromSharedHelper() {
        Currency noPrime = Fixtures.currency((short) 6, "NPX", "No Prime", true);
        repository.addCurrency(noPrime);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("NPX", "Note", "Notes", "N", 5, "p"));
        assertEquals("Currency NPX does not have a prime unit.", e.getMessage());
    }

    @Test
    void addParentThrowsWhenChildUnitDoesNotExist() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("GBP", "Guinea", "Guineas", "G", 21, "shilling"));
        assertEquals("Child unit shilling does not exist for currency GBP.", e.getMessage());
    }

    @Test
    void addParentThrowsWhenMultiplierNotGreaterThanOne() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("GBP", "Guinea", "Guineas", "G", 1, "p"));
        assertEquals("Multiplier must be greater than one.", e.getMessage());
    }

    @Test
    void addParentThrowsWhenAnotherParentAlreadyUsesTheSameMultiplier() {
        Unit crown = Fixtures.parentUnit((short) 3, gbp, "Crown", "C", false, 20, penny);
        repository.addUnit(crown);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("GBP", "Score", "Scores", "S", 20, "p"));
        assertEquals("A parent of p with multiplier 20 already exists.", e.getMessage());
    }

    @Test
    void addParentSucceedsAndCommits() throws CurrenciesException, SQLException {
        currencyService.addParent("GBP", "Crown", "Crowns", "C", 20, "p");

        verify(jdbc.connection).commit();
    }

    @Test
    void addParentDetectsCollisionEvenWhenChildUnitIsNotTheCurrencyBaseUnit() {
        // Regression test: the duplicate-multiplier guard used to compare the raw multiplier
        // parameter against the stored (already-multiplied) base_multiples column, so it only
        // caught collisions when the child unit was itself the currency's base unit. Here "S"
        // (Shilling) is a parent of Farthing, not the base unit -- the scenario where the bug hid
        // (see ReadmeCurrencyScenariosTest.readmeScriptsTwopenceLineCollidesWithThreepence).
        Unit farthing = Fixtures.baseUnit((short) 5, gbp, "Farthing", "f");
        repository.addUnit(farthing);
        Unit shilling = Fixtures.parentUnit((short) 6, gbp, "Shilling", "S", false, 4, farthing); // 1 Shilling = 4 Farthings
        repository.addUnit(shilling);

        Unit crown = new Unit();
        crown.setId((short) 7);
        crown.setCurrency(gbp);
        crown.setName("Crown");
        crown.setAlternate("Crowns");
        crown.setSymbol("C");
        crown.setChildUnit(shilling);
        crown.setBaseMultiples(20 * shilling.getBaseMultiples()); // what addParent(multiplier=20) would actually persist
        repository.addUnit(crown);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("GBP", "Score", "Scores", "Sc", 20, "S"));
        assertEquals("A parent of S with multiplier 20 already exists.", e.getMessage());
    }

    // ---- addChild ----

    @Test
    void addChildThrowsWhenCurrencyDoesNotExist() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addChild("ZZZ", "Farthing", "Farthings", "f", 4, "p"));
        assertEquals("Currency with acronym ZZZ does not exist.", e.getMessage());
    }

    @Test
    void addChildRejectsSymbolWithNegativeSign() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addChild("GBP", "Farthing", "Farthings", "-f", 4, "p"));
        assertEquals("Symbol cannot contain numbers or the negative symbol.", e.getMessage());
    }

    @Test
    void addChildThrowsWhenParentUnitDoesNotExist() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addChild("GBP", "Farthing", "Farthings", "f", 4, "halfpenny"));
        assertEquals("Unit halfpenny does not exist.", e.getMessage());
    }

    @Test
    void addChildThrowsWhenParentAlreadyHasAChild() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addChild("GBP", "Farthing", "Farthings", "f", 4, "£"));
        assertEquals("Unit £ already has a child. Units can only have one child.", e.getMessage());
    }

    @Test
    void addChildThrowsWhenDivisorNotGreaterThanOne() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addChild("GBP", "Farthing", "Farthings", "f", 1, "p"));
        assertEquals("Divisor must be greater than 1.", e.getMessage());
    }

    @Test
    void addChildSucceedsAndCommits() throws CurrenciesException, SQLException {
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getShort(1)).thenReturn((short) 10);

        currencyService.addChild("GBP", "Farthing", "Farthings", "f", 4, "p");

        verify(jdbc.connection).commit();
    }

    // ---- list ----

    @Test
    void listFirstPageReturnsCurrenciesFromOffsetZero() throws CurrenciesException {
        List<Currency> page = currencyService.list();

        assertEquals(1, page.size());
        assertTrue(page.contains(gbp));
    }

    @Test
    void listPageTwoUsesOffsetTen() throws CurrenciesException {
        List<Currency> page = currencyService.list(2);

        assertTrue(page.isEmpty(), "only one currency exists, so page 2 (offset 10) should be empty");
    }

    // ---- lookups ----

    @Test
    void getCurrencyReturnsMatchWhenPresent() {
        assertEquals(gbp, currencyService.getCurrency((short) 1, true));
    }

    @Test
    void getCurrencyThrowsWhenMissingAndExceptionRequested() {
        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class,
                () -> currencyService.getCurrency((short) 99, true));
        assertEquals("Currency with ID 99 does not exist.", e.getMessage());
    }

    @Test
    void getCurrencyReturnsNullWhenMissingAndExceptionNotRequested() {
        assertNull(currencyService.getCurrency((short) 99, false));
    }

    @Test
    void getAllCurrenciesReturnsEveryNonDeletedCurrencyUnpaginated() throws CurrenciesException {
        for (int i = 0; i < 15; i++) {
            Currency c = Fixtures.currency((short) (100 + i), "C" + i, "Currency " + i, true);
            repository.addCurrency(c);
        }

        List<Currency> all = currencyService.getAllCurrencies();

        assertEquals(16, all.size(), "should return every currency, not just one page of 10");
        assertTrue(all.contains(gbp));
    }

    @Test
    void getGlobalDefaultCurrencyReturnsTheFlaggedCurrency() {
        gbp.setGlobalDefault(true);

        assertEquals(gbp, currencyService.getGlobalDefaultCurrency(true));
    }

    @Test
    void getGlobalDefaultCurrencyThrowsWhenNoneIsSetAndExceptionRequested() {
        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class,
                () -> currencyService.getGlobalDefaultCurrency(true));
        assertEquals("No global default currency has been set.", e.getMessage());
    }

    @Test
    void getGlobalDefaultCurrencyReturnsNullWhenNoneIsSetAndExceptionNotRequested() {
        assertNull(currencyService.getGlobalDefaultCurrency(false));
    }

    @Test
    void getCurrencyFromAcronymReturnsMatch() {
        assertEquals(gbp, currencyService.getCurrencyFromAcronym("GBP", true));
    }

    @Test
    void getCurrencyFromAcronymThrowsWhenMissing() {
        assertThrows(CurrenciesRuntimeException.class, () -> currencyService.getCurrencyFromAcronym("ZZZ", true));
    }

    @Test
    void getUnitReturnsMatch() {
        assertEquals(pound, currencyService.getUnit((short) 1, true));
    }

    @Test
    void getUnitThrowsWhenMissing() {
        assertThrows(CurrenciesRuntimeException.class, () -> currencyService.getUnit((short) 99, true));
    }

    @Test
    void getBaseUnitReturnsUnitWithNoChild() {
        assertEquals(penny, currencyService.getBaseUnit(gbp, true));
    }

    @Test
    void getBaseUnitThrowsWhenCurrencyHasNoBase() {
        Currency noBase = Fixtures.currency((short) 9, "XXX", "No Base", true);
        repository.addCurrency(noBase);

        assertThrows(CurrenciesRuntimeException.class, () -> currencyService.getBaseUnit(noBase, true));
    }

    @Test
    void getPrimeUnitReturnsPrimeFlaggedUnit() {
        assertEquals(pound, currencyService.getPrimeUnit(gbp, true));
    }

    @Test
    void getPrimeUnitThrowsWhenCurrencyHasNoPrime() {
        Currency noPrime = Fixtures.currency((short) 9, "XXX", "No Prime", true);
        repository.addCurrency(noPrime);

        assertThrows(CurrenciesRuntimeException.class, () -> currencyService.getPrimeUnit(noPrime, true));
    }

    @Test
    void getUnitsResolvesChildUnitStubsWithinTheReturnedMap() {
        // Mirrors JdbcCurrencyRepository.queryUnitsOrdered: the raw query result only carries a
        // child-unit ID stub, which getUnits() must resolve against its own result set.
        Currency usd = Fixtures.currency((short) 5, "USD", "US Dollar", true);
        repository.addCurrency(usd);

        Unit cent = Fixtures.baseUnit((short) 20, usd, "Cent", "c");
        Unit dollar = Fixtures.parentUnit((short) 21, usd, "Dollar", "$", true, 100, cent);
        Unit childStub = new Unit();
        childStub.setId(cent.getId());
        dollar.setChildUnit(childStub);

        repository.addUnit(dollar);
        repository.addUnit(cent);

        Map<Short, Unit> units = currencyService.getUnits(usd);

        assertEquals(2, units.size());
        assertEquals(cent, units.get(dollar.getId()).getChildUnit());
        assertEquals("Cent", units.get(dollar.getId()).getChildUnit().getName(),
                "resolved child should be the full entity, not the ID-only stub");
    }
}
