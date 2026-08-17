package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrencyDTO;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

class CurrencyFormatterTest {

    private FakeCurrencyRepository repository;
    private CurrencyFormatter formatter;

    private Currency gbp;
    private Unit pound;
    private Unit penny;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();

        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        when(connectionProvider.getConnection()).thenReturn(mock(Connection.class));
        formatter = new CurrencyFormatter(connectionProvider, repository);

        gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        repository.addCurrency(gbp);

        // Pound = 100 Pence (base unit), both flagged "main" so formatCurrency shows both denominations.
        penny = Fixtures.baseUnit((short) 2, gbp, "Penny", "p");
        pound = Fixtures.parentUnit((short) 1, gbp, "Pound", "£", true, 100, penny);
        repository.addUnit(pound);
        repository.addUnit(penny);
    }

    @Test
    void formatCurrencyPrefixSplitsIntoDenominationsDescending() {
        assertEquals("£2p50", formatter.formatCurrency(gbp, 250));
    }

    @Test
    void formatCurrencySuffixSplitsIntoDenominationsDescending() {
        Currency usd = Fixtures.currency((short) 2, "USD", "US Dollar", false);
        repository.addCurrency(usd);
        Unit cent = Fixtures.baseUnit((short) 3, usd, "Cent", "c");
        Unit dollar = Fixtures.parentUnit((short) 4, usd, "Dollar", "$", true, 100, cent);
        repository.addUnit(dollar);
        repository.addUnit(cent);

        assertEquals("2$50c", formatter.formatCurrency(usd, 250));
    }

    @Test
    void formatCurrencyNegativeAmountIsPrefixedWithMinusOnce() {
        assertEquals("-£1p50", formatter.formatCurrency(gbp, -150));
    }

    @Test
    void formatCurrencyZeroAmountShowsPrimeUnitOnly() {
        assertEquals("£0", formatter.formatCurrency(gbp, 0));
    }

    @Test
    void formatCurrenciesFormatsEveryEntryOfTheMap() {
        Map<Currency, Long> amounts = new HashMap<>();
        amounts.put(gbp, 250L);

        Map<Currency, String> formatted = formatter.formatCurrencies(amounts);

        assertEquals("£2p50", formatted.get(gbp));
    }

    @Test
    void parseCurrencyConvertsMixedDenominationStringToBaseUnits() throws CurrenciesException {
        assertEquals(250L, formatter.parseCurrency(gbp, "£2p50"));
    }

    @Test
    void parseCurrencyHonorsLeadingNegativeSign() throws CurrenciesException {
        assertEquals(-150L, formatter.parseCurrency(gbp, "-£1p50"));
    }

    @Test
    void parseCurrencyRejectsStringWithNoSymbol() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> formatter.parseCurrency(gbp, "100"));
        assertEquals("Either no symbol or no currency amount was provided.", e.getMessage());
    }

    @Test
    void parseCurrencyRejectsUnknownSymbol() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> formatter.parseCurrency(gbp, "100z"));
        assertEquals("z is not a valid symbol.", e.getMessage());
    }

    @Test
    void resolveCurrencyFindsUniquePrimeSymbol() throws CurrenciesException {
        CurrencyDTO dto = formatter.resolveCurrency(null, "£100");

        assertEquals(gbp, dto.getCurrency());
        assertEquals(penny, dto.getBaseUnit());
        assertEquals(10000L, dto.getBaseAmount());
    }

    @Test
    void resolveCurrencyNegatesBaseAmountForLeadingMinus() throws CurrenciesException {
        CurrencyDTO dto = formatter.resolveCurrency(null, "-£1p50");

        assertEquals(-150L, dto.getBaseAmount());
    }

    @Test
    void getCurrencyFromAmountDelegatesToResolveCurrency() throws CurrenciesException {
        assertEquals(gbp, formatter.getCurrencyFromAmount(null, "£100"));
    }

    @Test
    void resolveCurrencyDisambiguatesSharedPrimeSymbolUsingAccountDefault() throws CurrenciesException {
        Currency usd = Fixtures.currency((short) 2, "USD", "US Dollar", true);
        Currency aud = Fixtures.currency((short) 3, "AUD", "Australian Dollar", true);
        repository.addCurrency(usd);
        repository.addCurrency(aud);

        Unit usdCent = Fixtures.baseUnit((short) 5, usd, "Cent", "c");
        Unit usdDollar = Fixtures.parentUnit((short) 6, usd, "Dollar", "$", true, 100, usdCent);
        repository.addUnit(usdDollar);
        repository.addUnit(usdCent);

        Unit audCent = Fixtures.baseUnit((short) 7, aud, "Cent", "c");
        Unit audDollar = Fixtures.parentUnit((short) 8, aud, "Dollar", "$", true, 100, audCent);
        repository.addUnit(audDollar);
        repository.addUnit(audCent);

        Account account = Fixtures.account(1, "Alice");
        account.setDefaultCurrency(aud);

        CurrencyDTO dto = formatter.resolveCurrency(account, "$50");

        assertEquals(aud, dto.getCurrency());
    }

    @Test
    void resolveCurrencyRequiresDefaultWhenPrimeSymbolIsAmbiguous() {
        Currency usd = Fixtures.currency((short) 2, "USD", "US Dollar", true);
        Currency aud = Fixtures.currency((short) 3, "AUD", "Australian Dollar", true);
        repository.addCurrency(usd);
        repository.addCurrency(aud);
        repository.addUnit(Fixtures.parentUnit((short) 6, usd, "Dollar", "$", true, 100,
                Fixtures.baseUnit((short) 5, usd, "Cent", "c")));
        repository.addUnit(Fixtures.parentUnit((short) 8, aud, "Dollar", "$", true, 100,
                Fixtures.baseUnit((short) 7, aud, "Cent", "c")));

        CurrenciesException e = assertThrows(CurrenciesException.class, () -> formatter.resolveCurrency(null, "$50"));
        assertEquals("This currency shares a prime unit with other currencies. You must run /currencies setdefault <currency>.",
                e.getMessage());
    }

    @Test
    void resolveCurrencyRejectsTwoDistinctPrimeSymbolsInOneString() {
        Currency usd = Fixtures.currency((short) 2, "USD", "US Dollar", true);
        repository.addCurrency(usd);
        repository.addUnit(Fixtures.parentUnit((short) 6, usd, "Dollar", "$", true, 100,
                Fixtures.baseUnit((short) 5, usd, "Cent", "c")));

        CurrenciesException e = assertThrows(CurrenciesException.class, () -> formatter.resolveCurrency(null, "£10$5"));
        assertEquals("Two prime units were provided in the currency string.", e.getMessage());
    }

    @Test
    void resolveCurrencyRejectsStringWithNoRecognizedPrimeSymbol() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> formatter.resolveCurrency(null, "z100"));
        assertEquals("No prime unit was located in your currency string.", e.getMessage());
    }
}
