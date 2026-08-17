package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * End-to-end scenarios for non-decimal (duodecimal/vigesimal, or single-denomination) British
 * currencies, drawn from README.md's "Creating Currencies"/"Using Currencies" sections and
 * https://en.wikipedia.org/wiki/Template:British_coinage:
 * <ul>
 * <li>the pre-decimalization Great British Pound (README.md's own documented example;
 * https://en.wikipedia.org/wiki/Coins_of_the_pound_sterling) -- a 4-tier £/s/d/farthing
 * hierarchy plus a dozen historically real "extra" denominations layered on via addparent;</li>
 * <li>Anglo-Saxon / early medieval coinage -- the opposite extreme, where only a single
 * denomination (the penny) was ever struck, with larger units existing purely as accounting
 * fictions.</li>
 * </ul>
 * See {@link DecimalCurrencyScenariosTest} for the base-10 counterparts (US Dollar, decimal-era
 * GBP). Every unit here is built with the exact base-unit multiples the documented
 * {@code /currencies addprime/addchild/addparent} command sequence would actually produce.
 */
class NonDecimalCurrencyScenariosTest {

    private FakeCurrencyRepository repository;
    private CurrencyFormatter formatter;

    private Currency gbp;
    private Unit pound, shilling, penny, farthing;
    private Unit guinea, crown, doubleFlorin, florin;
    private Unit halfGuinea, halfcrown, sixpence, threepence, groat, halfgroat;
    private Unit threeHalfpence, halfpenny;

    private Currency ags;
    private Unit agsPenny, agsMark, agsPound;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        when(connectionProvider.getConnection()).thenReturn(mock(Connection.class));
        formatter = new CurrencyFormatter(connectionProvider, repository);

        buildGreatBritishPound();
        buildAngloSaxonCoinage();
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
    // Pre-decimalization Great British Pound, from README.md:
    //
    // /currencies create GBP 'Great British Pound' false
    // /currencies addprime GBP pound pounds L
    // /currencies addchild GBP shilling shillings s 20 L
    // /currencies addchild GBP penny pence d 12 s
    // /currencies addchild GBP farthing farthings f 4 d
    //
    // /currencies addparent GBP 'guinea' 'guinea' gu 21 s
    // /currencies addparent GBP crown crowns c 5 s
    // /currencies addparent GBP 'double florin' 'double florin' df 4 s
    // /currencies addparent GBP florin florins fl 2 s
    //
    // /currencies addparent GBP 'half guinea' 'half guinea' gh 126 d
    // /currencies addparent GBP halfcrown halfcrowns hc 30 d
    // /currencies addparent GBP sixpence sixpence sp 6 d
    // /currencies addparent GBP threepence threepence tp 3 d
    // /currencies addparent GBP groat groats g 4 d
    // /currencies addparent GBP halfgroat halfgroats hg 2 d
    //
    // /currencies addparent GBP 'three halfpence' 'three halfpence' th 6 f
    // /currencies addparent GBP halfpenny halfpence hp 2 f
    //
    // The "twopence" line (multiplier 3, child d) is deliberately omitted here -- see
    // readmeScriptsTwopenceLineCollidesWithThreepence(), which proves that line as literally
    // documented cannot be run against a live server.
    // =========================================================================

    private void buildGreatBritishPound() {
        gbp = Fixtures.currency((short) 1, "GBP", "Great British Pound", false);
        repository.addCurrency(gbp);

        // addprime/addchild chain: base_multiples cascades on every addchild (see
        // CurrencyService.addChild), ending with 1 pound = 960 farthings.
        farthing = Fixtures.baseUnit((short) 4, gbp, "farthing", "f");
        penny = new Unit();
        penny.setId((short) 3);
        penny.setCurrency(gbp);
        penny.setName("penny");
        penny.setAlternate("pence");
        penny.setSymbol("d");
        penny.setPrime(false);
        penny.setMain(true);
        penny.setBaseMultiples(4); // 4 farthings = 1 penny
        penny.setChildUnit(farthing);

        shilling = new Unit();
        shilling.setId((short) 2);
        shilling.setCurrency(gbp);
        shilling.setName("shilling");
        shilling.setAlternate("shillings");
        shilling.setSymbol("s");
        shilling.setPrime(false);
        shilling.setMain(true);
        shilling.setBaseMultiples(48); // 12 pence * 4 farthings
        shilling.setChildUnit(penny);

        pound = new Unit();
        pound.setId((short) 1);
        pound.setCurrency(gbp);
        pound.setName("pound");
        pound.setAlternate("pounds");
        pound.setSymbol("L");
        pound.setPrime(true);
        pound.setMain(true);
        pound.setBaseMultiples(960); // 20 shillings * 48 farthings
        pound.setChildUnit(shilling);

        repository.addUnit(farthing);
        repository.addUnit(penny);
        repository.addUnit(shilling);
        repository.addUnit(pound);

        // addparent-created denominations: CurrencyService.addParent always inserts with
        // prime=0, main=0, so none of these ever appear in formatCurrency's auto-formatting.
        guinea = additionalDenomination(gbp, (short) 10, "guinea", "gu", 21, shilling); // 21s
        crown = additionalDenomination(gbp, (short) 11, "crown", "c", 5, shilling); // 5s
        doubleFlorin = additionalDenomination(gbp, (short) 12, "double florin", "df", 4, shilling); // 4s
        florin = additionalDenomination(gbp, (short) 13, "florin", "fl", 2, shilling); // 2s

        halfGuinea = additionalDenomination(gbp, (short) 20, "half guinea", "gh", 126, penny); // 126d
        halfcrown = additionalDenomination(gbp, (short) 21, "halfcrown", "hc", 30, penny); // 30d
        sixpence = additionalDenomination(gbp, (short) 22, "sixpence", "sp", 6, penny); // 6d
        threepence = additionalDenomination(gbp, (short) 23, "threepence", "tp", 3, penny); // 3d
        groat = additionalDenomination(gbp, (short) 24, "groat", "g", 4, penny); // 4d
        halfgroat = additionalDenomination(gbp, (short) 25, "halfgroat", "hg", 2, penny); // 2d

        threeHalfpence = additionalDenomination(gbp, (short) 30, "three halfpence", "th", 6, farthing); // 6f
        halfpenny = additionalDenomination(gbp, (short) 31, "halfpenny", "hp", 2, farthing); // 2f
    }

    @Test
    void everyDenominationConvertsToTheCorrectNumberOfFarthings() {
        assertEquals(960, pound.getBaseMultiples(), "1 pound = 20s = 960 farthings");
        assertEquals(48, shilling.getBaseMultiples(), "1 shilling = 12d = 48 farthings");
        assertEquals(4, penny.getBaseMultiples(), "1 penny = 4 farthings");
        assertEquals(0, farthing.getBaseMultiples(), "farthing is the base unit");

        assertEquals(1008, guinea.getBaseMultiples(), "1 guinea = 21s");
        assertEquals(240, crown.getBaseMultiples(), "1 crown = 5s");
        assertEquals(192, doubleFlorin.getBaseMultiples(), "1 double florin = 4s");
        assertEquals(96, florin.getBaseMultiples(), "1 florin = 2s");

        assertEquals(504, halfGuinea.getBaseMultiples(), "1 half guinea = 126d = 10s6d");
        assertEquals(120, halfcrown.getBaseMultiples(), "1 halfcrown = 30d = 2s6d");
        assertEquals(24, sixpence.getBaseMultiples(), "1 sixpence = 6d");
        assertEquals(12, threepence.getBaseMultiples(), "1 threepence = 3d");
        assertEquals(16, groat.getBaseMultiples(), "1 groat = 4d");
        assertEquals(8, halfgroat.getBaseMultiples(), "1 halfgroat = 2d");

        assertEquals(6, threeHalfpence.getBaseMultiples(), "1 three-halfpence = 1.5d = 6 farthings");
        assertEquals(2, halfpenny.getBaseMultiples(), "1 halfpenny = 2 farthings");
    }

    @Test
    void additionalDenominationsAreNeverPrimeOrMain() {
        for (Unit denomination : new Unit[] {guinea, crown, doubleFlorin, florin, halfGuinea, halfcrown,
                sixpence, threepence, groat, halfgroat, threeHalfpence, halfpenny}) {
            assertTrue(!denomination.isPrime() && !denomination.isMain(),
                    denomination.getName() + " should be neither prime nor main, matching CurrencyService.addParent's INSERT");
        }
    }

    /**
     * README's script has "/currencies addparent GBP twopence twopence wp 3 d" -- but threepence
     * (added two lines earlier) already claims multiplier 3 against the same child unit (penny).
     * Historically twopence is 2 pence, not 3, but multiplier 2 against penny is *also* already
     * taken, by halfgroat (added three lines before that) -- there's no multiplier value that lets
     * a unit named "twopence" coexist with both. Either way, addParent's own uniqueness rule
     * ("A parent of X with multiplier Y already exists") should reject the literal README line.
     *
     * It didn't, until now: CurrencyService.addParent's duplicate check compared the raw
     * `multiplier` parameter against the *stored* base_multiples column, which is
     * `multiplier * childUnit.getBaseMultiples()` once persisted. Those two values only coincide
     * when the child unit is itself the currency's base unit (base_multiples == 0) -- true for the
     * simplified fixtures in CurrencyServiceTest, false here since penny sits two tiers above
     * farthing. So the guard silently let same-valued duplicates through for any currency with
     * more than two denomination tiers, which is the normal case for real historical currencies.
     * Fixed by comparing against the computed value instead (see CurrencyService.addParent).
     */
    @Test
    void readmeScriptsTwopenceLineCollidesWithThreepence() throws SQLException {
        JdbcWriteSupport jdbc = new JdbcWriteSupport();
        CurrencyService currencyService = new CurrencyService(jdbc.connectionProvider, repository);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("GBP", "twopence", "twopence", "wp", 3, "d"));
        assertEquals("A parent of d with multiplier 3 already exists.", e.getMessage());
    }

    /**
     * Even at twopence's *historically correct* value (2 pence, not the README's 3), it still
     * can't be added: halfgroat already occupies multiplier 2 against the same child (penny).
     * Unlike the threepence collision above, this one isn't a bug -- twopence and halfgroat are
     * genuinely the same face value (2d) under different names, and the plugin's one-unit-per-
     * value rule correctly refuses to register a second name for a value that already exists.
     */
    @Test
    void correctlyValuedTwopenceStillCollidesWithHalfgroatByRealValue() throws SQLException {
        JdbcWriteSupport jdbc = new JdbcWriteSupport();
        CurrencyService currencyService = new CurrencyService(jdbc.connectionProvider, repository);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> currencyService.addParent("GBP", "twopence", "twopence", "wp", 2, "d"));
        assertEquals("A parent of d with multiplier 2 already exists.", e.getMessage());
    }

    // ---- README "Using Currencies": /credit NobleUplift 200L20hc17g ----

    @Test
    void parsesTheReadmeCreditExampleOfTwoHundredPoundsTwentyHalfcrownsSeventeenGroats() throws CurrenciesException {
        long baseAmount = formatter.parseCurrency(gbp, "200L20hc17g");

        // 200*960 (pound) + 20*120 (halfcrown) + 17*16 (groat)
        assertEquals(200L * 960 + 20L * 120 + 17L * 16, baseAmount);
        assertEquals(194672L, baseAmount);
    }

    /**
     * formatCurrency only walks "main" units (queryMainUnitsForCurrencyDescending), and none of
     * the addparent-created denominations -- including halfcrown and groat -- are ever flagged
     * main. So the 200L20hc17g credit does NOT format back the way it was paid in: it re-expands
     * purely in pounds/shillings/pence/farthings, the only chain built via addprime/addchild.
     */
    @Test
    void formattingTheCreditedAmountUsesOnlyThePoundShillingPenceChainNotHalfcrownsOrGroats() {
        String formatted = formatter.formatCurrency(gbp, 194672L);

        assertEquals("202L15s8d", formatted);
    }

    // ---- README "Using Currencies": /debit NobleUplift 0L20hc17g ----

    @Test
    void parsesTheReadmeDebitExampleWithLeadingZeroPounds() throws CurrenciesException {
        long baseAmount = formatter.parseCurrency(gbp, "0L20hc17g");

        assertEquals(20L * 120 + 17L * 16, baseAmount);
        assertEquals(2672L, baseAmount);
    }

    /**
     * README: "Note how I provided 0L in the currency amount. This is a requirement if you are
     * only crediting/debiting minor units of a currency, in order to identify it." That's not
     * about parseCurrency's arithmetic (it works fine without a leading unit) -- it's about
     * resolveCurrency needing a *prime* symbol somewhere in the string to know which currency the
     * amount belongs to at all. Neither "hc" nor "g" is a prime symbol, so without "L" present,
     * currency identification fails outright.
     */
    @Test
    void omittingTheLeadingPrimeUnitMakesTheCurrencyUnidentifiable() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> formatter.resolveCurrency(null, "20hc17g"));
        assertEquals("No prime unit was located in your currency string.", e.getMessage());
    }

    @Test
    void includingTheLeadingZeroPoundIdentifiesTheCurrencyAndParsesCorrectly() throws CurrenciesException {
        CurrencyDTO dto = formatter.resolveCurrency(null, "0L20hc17g");

        assertEquals(gbp, dto.getCurrency());
        assertEquals(farthing, dto.getBaseUnit());
        assertEquals(2672L, dto.getBaseAmount());
    }

    // =========================================================================
    // Anglo-Saxon / early medieval coinage: only the penny was ever struck as a coin.
    // The pound (240d) and mark (160d) existed purely as units of account for reckoning
    // large sums and legal fines -- never minted, but perfectly representable here as
    // addparent denominations of the penny.
    // =========================================================================

    private void buildAngloSaxonCoinage() {
        // /currencies create AGS 'Anglo-Saxon Coinage' false
        ags = Fixtures.currency((short) 9, "AGS", "Anglo-Saxon Coinage", false);
        repository.addCurrency(ags);

        // /currencies addprime AGS penny pence d -- the only coin actually struck: prime AND base at once.
        agsPenny = Fixtures.baseUnit((short) 40, ags, "penny", "d");
        agsPenny.setPrime(true);
        repository.addUnit(agsPenny);

        // /currencies addparent AGS mark marks m 160 d -- 1 mark = 160d (2/3 of a pound), a unit of account.
        agsMark = additionalDenomination(ags, (short) 41, "mark", "m", 160, agsPenny);
        // /currencies addparent AGS pound pounds L 240 d -- 1 (tower) pound = 240d, likewise never minted.
        agsPound = additionalDenomination(ags, (short) 42, "pound", "L", 240, agsPenny);
    }

    @Test
    void poundAndMarkConvertToTheCorrectNumberOfPence() {
        assertEquals(0, agsPenny.getBaseMultiples(), "penny is the only struck coin -- the base unit");
        assertEquals(160, agsMark.getBaseMultiples(), "1 mark = 160 pence (2/3 of a pound)");
        assertEquals(240, agsPound.getBaseMultiples(), "1 (tower) pound = 240 pence");
    }

    /**
     * Neither mark nor pound was ever struck, so neither is flagged "main" (only addprime/
     * addchild units are). formatCurrency therefore can never roll a pence amount up into pounds
     * or marks automatically -- for a currency with no addchild chain at all, auto-formatting
     * degenerates to always displaying the single struck denomination, exactly matching how this
     * coinage actually worked in practice: you reckoned in pounds and marks, but you paid in pence.
     */
    @Test
    void formatCurrencyAlwaysShowsRawPenceSinceNothingWasEverStruckButThePenny() {
        // 2 pounds' worth of pence (480d) -- still displayed as pence, never "2L".
        assertEquals("480d", formatter.formatCurrency(ags, 480));
    }

    @Test
    void parseCurrencyStillUnderstandsPoundAndMarkAsInputEvenThoughFormatNeverProducesThem() throws CurrenciesException {
        // 1 pound (240d) + 1 mark (160d) + 5d = 405d
        long baseAmount = formatter.parseCurrency(ags, "1L1m5d");

        assertEquals(240L + 160L + 5L, baseAmount);
        assertEquals(405L, baseAmount);
    }
}
