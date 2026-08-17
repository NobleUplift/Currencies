package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;

class AccountServiceTest {

    private FakeCurrencyRepository repository;
    private JdbcWriteSupport jdbc;
    private CurrencyService currencyService;
    private AccountService accountService;

    private Account alice;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();
        jdbc = new JdbcWriteSupport();
        currencyService = mock(CurrencyService.class);
        accountService = new AccountService(jdbc.connectionProvider, repository, currencyService);

        alice = Fixtures.account(1, "AliceTheMerchant");
        alice.setUuid("11111111-1111-1111-1111-111111111111");
        repository.addAccount(alice);
    }

    // ---- openAccount ----

    @Test
    void openAccountRejectsShortNamesWithoutTouchingTheDatabase() throws SQLException {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> accountService.openAccount("ShortName", "AliceTheMerchant"));
        assertEquals("Non-player accounts must be longer than 16 characters.", e.getMessage());
        verify(jdbc.connection, never()).prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void openAccountRejectsNameAlreadyTaken() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(true);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> accountService.openAccount("The Merchant Guild", "AliceTheMerchant"));
        assertEquals("Account with name The Merchant Guild already exists.", e.getMessage());
        verify(jdbc.connection).rollback();
    }

    @Test
    void openAccountRejectsUnknownOwner() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(false, false);
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getInt(1)).thenReturn(50);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> accountService.openAccount("The Merchant Guild", "NobodyHome"));
        assertEquals("Owner NobodyHome does not exist.", e.getMessage());
        verify(jdbc.connection).rollback();
    }

    @Test
    void openAccountReturnsNewAccountAndCommits() throws CurrenciesException, SQLException {
        when(jdbc.resultSet.next()).thenReturn(false, true);
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getInt(1)).thenReturn(50);

        Account account = accountService.openAccount("The Merchant Guild", "AliceTheMerchant");

        assertEquals(50, account.getId());
        assertEquals("The Merchant Guild", account.getName());
        assertNull(account.getUuid());
        verify(jdbc.connection).commit();
    }

    // ---- setDefault ----

    @Test
    void setDefaultThrowsWhenPlayerAccountUnknownWithoutTouchingTheDatabase() throws SQLException {
        assertThrows(CurrenciesRuntimeException.class, () -> accountService.setDefault("Nobody", "GBP"));
        verify(jdbc.connection, never()).prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void setDefaultUpdatesAccountAndCommits() throws CurrenciesException, SQLException {
        Currency gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);

        accountService.setDefault("AliceTheMerchant", "GBP");

        verify(jdbc.connection).commit();
    }

    // ---- reserved account getters ----

    @Test
    void getMinecraftCentralBankReturnsAccountOne() {
        Account bank = Fixtures.account(1, "Minecraft Central Bank");
        repository.addAccount(bank);

        assertEquals(bank, accountService.getMinecraftCentralBank());
    }

    @Test
    void getMinecraftCentralBankerReturnsAccountTwo() {
        Account banker = Fixtures.account(2, "Minecraft Central Banker");
        repository.addAccount(banker);

        assertEquals(banker, accountService.getMinecraftCentralBanker());
    }

    @Test
    void getTheEndermanMarketReturnsAccountThree() {
        Account market = Fixtures.account(3, "The Enderman Market");
        repository.addAccount(market);

        assertEquals(market, accountService.getTheEndermanMarket());
    }

    @Test
    void getTheEndermanMarketeerReturnsAccountFour() {
        Account marketeer = Fixtures.account(4, "The Enderman Marketeer");
        repository.addAccount(marketeer);

        assertEquals(marketeer, accountService.getTheEndermanMarketeer());
    }

    @Test
    void reservedAccountGetterWrapsConnectionFailureAsRuntimeException() throws SQLException {
        ConnectionProvider failingProvider = mock(ConnectionProvider.class);
        when(failingProvider.getConnection()).thenThrow(new SQLException("pool exhausted"));
        AccountService failing = new AccountService(failingProvider, repository, currencyService);

        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class, failing::getMinecraftCentralBank);
        assertTrue(e.getMessage().contains("getAccountById(1)"));
    }

    // ---- getAccountFromPlayer / getAccountFromUniqueId ----

    @Test
    void getAccountFromPlayerReturnsMatch() {
        assertEquals(alice, accountService.getAccountFromPlayer("AliceTheMerchant", true));
    }

    @Test
    void getAccountFromPlayerThrowsWhenMissingAndExceptionRequested() {
        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class,
                () -> accountService.getAccountFromPlayer("Nobody", true));
        assertEquals("Account Nobody does not exist.", e.getMessage());
    }

    @Test
    void getAccountFromPlayerReturnsNullWhenMissingAndExceptionNotRequested() {
        assertNull(accountService.getAccountFromPlayer("Nobody", false));
    }

    @Test
    void getAccountFromUniqueIdReturnsMatch() {
        assertEquals(alice, accountService.getAccountFromUniqueId("11111111-1111-1111-1111-111111111111", true));
    }

    @Test
    void getAccountFromUniqueIdThrowsWhenMissingAndExceptionRequested() {
        CurrenciesRuntimeException e = assertThrows(CurrenciesRuntimeException.class,
                () -> accountService.getAccountFromUniqueId("does-not-exist", true));
        assertEquals("Account with UUID does-not-exist does not exist.", e.getMessage());
    }

    @Test
    void getAccountFromUniqueIdReturnsNullWhenMissingAndExceptionNotRequested() {
        assertNull(accountService.getAccountFromUniqueId("does-not-exist", false));
    }
}
