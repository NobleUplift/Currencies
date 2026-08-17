package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrencyDTO;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

/**
 * End-to-end scenarios for decimal (base-10) currencies, drawn from README.md's "Creating
 * Currencies"/"Using Currencies" sections and https://en.wikipedia.org/wiki/Template:British_coinage:
 * <ul>
 * <li>the US Dollar (README.md's own decimalized example -- and the trick where "." is
 * literally a registered unit symbol, not a decimal point);</li>
 * <li>decimal-era British coinage (1971-present) -- a flat base-10 ladder, in direct contrast to
 * the multi-tier duodecimal/vigesimal pre-decimal system.</li>
 * </ul>
 * See {@link NonDecimalCurrencyScenariosTest} for the £sd and Anglo-Saxon counterparts. Every
 * unit here is built with the exact base-unit multiples the documented
 * {@code /currencies addprime/addchild/addparent} command sequence would actually produce.
 */
class DecimalCurrencyScenariosTest {

    private FakeCurrencyRepository repository;
    private CurrencyFormatter formatter;

    private Currency usd;
    private Unit dollar, cent;

    private Currency gbd;
    private Unit decimalPenny, decimalPound;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        when(connectionProvider.getConnection()).thenReturn(mock(Connection.class));
        formatter = new CurrencyFormatter(connectionProvider, repository);

        buildUsDollar();
        buildDecimalCoinage();
    }

    /** Builds a unit the way CurrencyService.addParent actually persists one: never prime, never main. */
    private Unit additionalDenomination(Currency currency, short id, String name, String symbol, int multiplier, Unit childUnit) {
        Unit u = new Unit();
        u.setId(id);
        u.setCurrency(currency);
        u.setName(name);
        u.setAlternate(name);
        u.setSymbol(symbol);
        u.setPrime(false);
        u.setMain(false);
        int baseMultiples = childUnit.getBaseMultiples() != 0 ? multiplier * childUnit.getBaseMultiples() : multiplier;
        u.setBaseMultiples(baseMultiples);
        u.setChildUnit(childUnit);
        repository.addUnit(u);
        return u;
    }

    // =========================================================================
    // US Dollar, from README.md:
    //
    // /currencies create USD 'United States Dollar'
    // /currencies addprime USD dollar dollars $
    // /currencies addchild USD cent cents . 100 $
    // =========================================================================

    private void buildUsDollar() {
        usd = Fixtures.currency((short) 2, "USD", "United States Dollar", true);
        repository.addCurrency(usd);

        cent = Fixtures.baseUnit((short) 6, usd, "cent", ".");
        dollar = new Unit();
        dollar.setId((short) 5);
        dollar.setCurrency(usd);
        dollar.setName("dollar");
        dollar.setAlternate("dollars");
        dollar.setSymbol("$");
        dollar.setPrime(true);
        dollar.setMain(true);
        dollar.setBaseMultiples(100);
        dollar.setChildUnit(cent);

        repository.addUnit(cent);
        repository.addUnit(dollar);
    }

    // ---- README "Using Currencies": USD decimal notation via a "." unit symbol ----

    @Test
    void dollarSignDecimalNotationIsActuallyACentUnitSymbolNotADecimalPoint() throws CurrenciesException {
        long baseAmount = formatter.parseCurrency(usd, "$29.99");

        assertEquals(29L * 100 + 99, baseAmount);
        assertEquals(2999L, baseAmount);
    }

    @Test
    void formattingRoundTripsBackToTheFamiliarDollarDecimalNotation() {
        assertEquals("$29.99", formatter.formatCurrency(usd, 2999L));
    }

    // =========================================================================
    // Decimal-era British coinage (1971-present): a flat base-10 ladder, the polar opposite of
    // the pre-decimal £sd system in NonDecimalCurrencyScenariosTest. Modeled under a separate
    // acronym ("GBD") purely because this fixture and the pre-decimal GBP fixture there can't
    // share a currency registry -- historically both eras use the same real-world acronym, GBP.
    // =========================================================================

    private void buildDecimalCoinage() {
        // /currencies create GBD 'Decimal Pound Sterling'
        gbd = Fixtures.currency((short) 3, "GBD", "Decimal Pound Sterling", true);
        repository.addCurrency(gbd);

        // /currencies addprime GBD pound pounds £
        // /currencies addchild GBD penny pence p 100 £
        decimalPenny = Fixtures.baseUnit((short) 20, gbd, "penny", "p");
        repository.addUnit(decimalPenny);
        decimalPound = new Unit();
        decimalPound.setId((short) 21);
        decimalPound.setCurrency(gbd);
        decimalPound.setName("pound");
        decimalPound.setAlternate("pounds");
        decimalPound.setSymbol("£");
        decimalPound.setPrime(true);
        decimalPound.setMain(true);
        decimalPound.setBaseMultiples(100);
        decimalPound.setChildUnit(decimalPenny);
        repository.addUnit(decimalPound);

        // addparent denominations below a pound -- all distinct values, so none collide.
        additionalDenomination(gbd, (short) 22, "twopence", "tw", 2, decimalPenny);
        additionalDenomination(gbd, (short) 23, "fivepence", "fi", 5, decimalPenny);
        additionalDenomination(gbd, (short) 24, "tenpence", "te", 10, decimalPenny);
        additionalDenomination(gbd, (short) 25, "twentypence", "ty", 20, decimalPenny);
        additionalDenomination(gbd, (short) 26, "fiftypence", "ff", 50, decimalPenny);

        // addparent denominations ABOVE the prime unit -- addParent doesn't require its "child"
        // to be the currency's base unit, so banknotes can extend the hierarchy past the coin
        // that was originally registered as prime.
        additionalDenomination(gbd, (short) 27, "two pound", "TP", 2, decimalPound); // 1 TP = 2 pounds = 200p
        additionalDenomination(gbd, (short) 28, "five pound", "FP", 5, decimalPound); // 1 FP = 5 pounds = 500p
    }

    @Test
    void decimalDenominationsConvertToTheCorrectNumberOfPence() {
        assertEquals(100, decimalPound.getBaseMultiples(), "1 pound = 100 pence");
        assertEquals(0, decimalPenny.getBaseMultiples(), "penny is the base unit");
        assertEquals(200, repository.queryUnitBySymbolAndCurrency(null, gbd, "TP").getBaseMultiples(), "1 Two Pound coin = 200 pence");
        assertEquals(500, repository.queryUnitBySymbolAndCurrency(null, gbd, "FP").getBaseMultiples(), "1 Five Pound note = 500 pence");
    }

    @Test
    void formatCurrencyRoundTripsThroughPoundAndPenceOnly() {
        // Only pound and penny are "main" (addprime/addchild); the addparent coins (2p, 5p, 10p,
        // 20p, 50p, £2, £5) are valid for input but never appear in auto-formatted output --
        // the same asymmetry the README's own GBP example demonstrates.
        assertEquals("£1p50", formatter.formatCurrency(gbd, 150));
    }

    @Test
    void parseCurrencyAcceptsEveryAddedDenominationIncludingBanknotesAboveThePrimeUnit() throws CurrenciesException {
        assertEquals(200L, formatter.parseCurrency(gbd, "1TP"), "1 Two Pound coin");
        assertEquals(1000L, formatter.parseCurrency(gbd, "2FP"), "2 Five Pound notes");
        // 1 of each smaller coin: 50p + 20p + 10p + 5p + 2p = 87p
        assertEquals(87L, formatter.parseCurrency(gbd, "1ff1ty1te1fi1tw"));
    }

    @Test
    void decimalDenominationsDoNotCascadeUnlikeThePreDecimalShillingPennyChain() {
        // penny (their shared child) is itself the base unit (base_multiples == 0), so every
        // addparent denomination's base_multiples equals its raw multiplier directly -- no
        // multi-tier multiplication like the README's pound/shilling/penny/farthing chain.
        assertEquals(10, repository.queryUnitBySymbolAndCurrency(null, gbd, "te").getBaseMultiples());
        assertEquals(50, repository.queryUnitBySymbolAndCurrency(null, gbd, "ff").getBaseMultiples());
    }

    // ---- README "Using Currencies": seamlessly using two currencies with distinct symbols ----

    @Test
    void twoCurrenciesWithDifferentPrimeSymbolsNeedNoDefaultCurrencyDisambiguation() throws CurrenciesException {
        CurrencyDTO dollarsDto = formatter.resolveCurrency(null, "$29.99");
        CurrencyDTO poundsDto = formatter.resolveCurrency(null, "£1p50");

        assertEquals(usd, dollarsDto.getCurrency());
        assertEquals(gbd, poundsDto.getCurrency());
    }
}
