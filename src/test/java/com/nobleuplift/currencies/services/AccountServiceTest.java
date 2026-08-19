package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
    private Account guild;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();
        jdbc = new JdbcWriteSupport();
        currencyService = mock(CurrencyService.class);
        accountService = new AccountService(jdbc.connectionProvider, repository, currencyService);

        alice = Fixtures.account(1, "AliceTheMerchant");
        alice.setUuid("11111111-1111-1111-1111-111111111111");
        repository.addAccount(alice);

        guild = Fixtures.account(50, "The Merchant Guild");
        guild.setUuid("33333333-3333-3333-3333-333333333333");
        repository.addAccount(guild);
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
        assertNotNull(account.getUuid());
        assertDoesNotThrow(() -> java.util.UUID.fromString(account.getUuid()), "should be assigned a real random UUID");
        verify(jdbc.connection).commit();
    }

    @Test
    void openAccountThreeArgOverloadUsesTheCallerSuppliedUuid() throws CurrenciesException, SQLException {
        when(jdbc.resultSet.next()).thenReturn(false, true);
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getInt(1)).thenReturn(51);
        String uuid = "22222222-2222-2222-2222-222222222222";

        Account account = accountService.openAccount("The Merchant Guild", uuid, "AliceTheMerchant");

        assertEquals(uuid, account.getUuid());
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

    // ---- getAllAccountsWithUuid ----

    @Test
    void getAllAccountsWithUuidReturnsOnlyAccountsThatHaveOne() {
        Account noUuid = Fixtures.account(2, "Bob");
        repository.addAccount(noUuid);

        List<Account> result = accountService.getAllAccountsWithUuid();

        assertTrue(result.contains(alice));
        assertFalse(result.contains(noUuid));
    }

    // ---- renameAccount ----

    @Test
    void renameAccountRejectsNameAlreadyTakenByAnotherAccount() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(true);

        assertFalse(accountService.renameAccount(alice, "TakenName"));
        verify(jdbc.connection).rollback();
    }

    @Test
    void renameAccountUpdatesNameAndCommits() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(false);

        assertTrue(accountService.renameAccount(alice, "AliceTheTrader"));
        verify(jdbc.connection).commit();
    }

    // ---- deleteAccount ----

    @Test
    void deleteAccountReturnsFalseWhenForeignKeyBlocksIt() throws SQLException {
        when(jdbc.plainStatement.executeUpdate()).thenThrow(new SQLException("FK violation"));

        assertFalse(accountService.deleteAccount(alice));
        verify(jdbc.connection).rollback();
    }

    @Test
    void deleteAccountReturnsTrueWhenAccountIsUnused() throws SQLException {
        // First executeUpdate() call is the Holder delete, second is the account delete itself.
        when(jdbc.plainStatement.executeUpdate()).thenReturn(0, 1);

        assertTrue(accountService.deleteAccount(alice));
        verify(jdbc.connection).commit();
    }

    @Test
    void deleteAccountReturnsFalseWhenNoRowWasDeleted() throws SQLException {
        when(jdbc.plainStatement.executeUpdate()).thenReturn(0, 0);

        assertFalse(accountService.deleteAccount(alice));
        verify(jdbc.connection).commit();
    }

    // ---- isOwner / isMember / addOwner / removeOwner ----

    @Test
    void isOwnerReturnsTrueOnlyForADirectLengthOneHolderRow() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(true);

        assertTrue(accountService.isOwner(alice, guild));
        verify(jdbc.plainStatement).setInt(1, alice.getId());
        verify(jdbc.plainStatement).setInt(2, guild.getId());
    }

    @Test
    void isOwnerReturnsFalseWhenNoMatchingRow() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(false);

        assertFalse(accountService.isOwner(alice, guild));
    }

    @Test
    void isMemberReturnsTrueForAnyPositiveDepthRelationship() throws SQLException {
        when(jdbc.resultSet.next()).thenReturn(true);

        assertTrue(accountService.isMember(alice, guild));
    }

    @Test
    void addOwnerInsertsHolderRowAndCommits() throws SQLException {
        accountService.addOwner(alice, guild);

        verify(jdbc.connection).commit();
    }

    @Test
    void removeOwnerDeletesHolderRowAndCommits() throws SQLException {
        accountService.removeOwner(alice, guild);

        verify(jdbc.connection).commit();
    }

    // ---- getOwnedAccounts / getMemberAccounts ----
    // Not currently called by the Vault adapter's accountsAccessTo (the pinned VaultUnlockedAPI
    // 2.16 has no UUID-returning accountsWithAccessTo to back with these), but kept ready for when
    // that lands in a later API version.

    @Test
    void getOwnedAccountsMapsHolderJoinRowsToAccounts() throws SQLException {
        ResultSet rs = RowResultSet.of(
                RowResultSet.row("id", 50, "name", "The Merchant Guild", "uuid", "33333333-3333-3333-3333-333333333333"));
        when(jdbc.plainStatement.executeQuery()).thenReturn(rs);

        List<Account> result = accountService.getOwnedAccounts(alice);

        assertEquals(1, result.size());
        assertEquals("The Merchant Guild", result.get(0).getName());
        assertEquals("33333333-3333-3333-3333-333333333333", result.get(0).getUuid());
    }

    @Test
    void getOwnedAccountsReturnsEmptyListWhenNoneExist() throws SQLException {
        ResultSet rs = RowResultSet.empty();
        when(jdbc.plainStatement.executeQuery()).thenReturn(rs);

        assertTrue(accountService.getOwnedAccounts(alice).isEmpty());
    }

    @Test
    void getMemberAccountsMapsHolderJoinRowsToAccounts() throws SQLException {
        ResultSet rs = RowResultSet.of(
                RowResultSet.row("id", 50, "name", "The Merchant Guild", "uuid", "33333333-3333-3333-3333-333333333333"));
        when(jdbc.plainStatement.executeQuery()).thenReturn(rs);

        List<Account> result = accountService.getMemberAccounts(alice);

        assertEquals(1, result.size());
        assertEquals("The Merchant Guild", result.get(0).getName());
    }
}
