package com.nobleuplift.currencies.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;
import com.nobleuplift.currencies.services.AccountService;
import com.nobleuplift.currencies.services.CurrencyFormatter;
import com.nobleuplift.currencies.services.CurrencyService;
import com.nobleuplift.currencies.services.Fixtures;
import com.nobleuplift.currencies.services.TransactionService;

import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.economy.EconomyResponse.ResponseType;

class CurrenciesEconomyTest {

    private AccountService accountService;
    private CurrencyService currencyService;
    private TransactionService transactionService;
    private CurrencyFormatter currencyFormatter;
    private CurrenciesEconomy economy;

    private Currency gbp;
    private Unit pound;
    private Unit penny;
    private Account alice;
    private UUID aliceUuid;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        currencyService = mock(CurrencyService.class);
        transactionService = mock(TransactionService.class);
        currencyFormatter = mock(CurrencyFormatter.class);
        economy = new CurrenciesEconomy(accountService, currencyService, transactionService, currencyFormatter);

        gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        penny = Fixtures.baseUnit((short) 2, gbp, "Penny", "p");
        pound = Fixtures.parentUnit((short) 1, gbp, "Pound", "£", true, 100, penny);

        aliceUuid = UUID.randomUUID();
        alice = Fixtures.account(10, "Alice");
        alice.setUuid(aliceUuid.toString());
    }

    // ---- plugin/currency info ----

    @Test
    void hasMultiCurrencySupportIsAlwaysTrue() {
        assertTrue(economy.hasMultiCurrencySupport());
    }

    @Test
    void currenciesReturnsAcronymsOfEveryCurrency() {
        Currency usd = Fixtures.currency((short) 2, "USD", "US Dollar", true);
        when(currencyService.getAllCurrencies()).thenReturn(List.of(gbp, usd));

        Collection<String> result = economy.currencies();

        assertTrue(result.contains("GBP"));
        assertTrue(result.contains("USD"));
        assertEquals(2, result.size());
    }

    @Test
    void hasCurrencyDelegatesToCurrencyServiceLookup() {
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);

        assertTrue(economy.hasCurrency("GBP"));
    }

    @Test
    void hasCurrencyReturnsFalseWhenUnknown() {
        assertFalse(economy.hasCurrency("ZZZ"));
    }

    @Test
    void getDefaultCurrencyReturnsGlobalDefaultAcronym() {
        when(currencyService.getGlobalDefaultCurrency(false)).thenReturn(gbp);

        assertEquals("GBP", economy.getDefaultCurrency("SomePlugin"));
    }

    @Test
    void getDefaultCurrencyReturnsEmptyStringWhenNoneSet() {
        assertEquals("", economy.getDefaultCurrency("SomePlugin"));
    }

    @Test
    void fractionalDigitsIsAlwaysZero() {
        assertEquals(0, economy.fractionalDigits("SomePlugin"));
    }

    @Test
    void formatDelegatesToCurrencyFormatterAfterResolvingCurrency() {
        when(currencyService.getGlobalDefaultCurrency(false)).thenReturn(gbp);
        when(currencyFormatter.formatCurrency(gbp, 250L)).thenReturn("£2p50");

        assertEquals("£2p50", economy.format("SomePlugin", new BigDecimal("250")));
    }

    // ---- balance / has ----

    @Test
    void getBalanceReturnsZeroWhenAccountUnknown() {
        assertEquals(BigDecimal.ZERO, economy.getBalance("SomePlugin", aliceUuid, "world", "GBP"));
    }

    @Test
    void getBalanceReturnsZeroWhenAccountOwnsNoneOfCurrency() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);
        when(transactionService.balance("Alice", "GBP")).thenThrow(new CurrenciesException("Account Alice does not own any Pennies."));

        assertEquals(BigDecimal.ZERO, economy.getBalance("SomePlugin", aliceUuid, "world", "GBP"));
    }

    @Test
    void getBalanceReturnsHeldAmount() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);
        when(transactionService.balance("Alice", "GBP")).thenReturn(Map.of(gbp, 250L));

        assertEquals(BigDecimal.valueOf(250L), economy.getBalance("SomePlugin", aliceUuid, "world", "GBP"));
    }

    @Test
    void getBalanceWithNoCurrencyFallsBackToGlobalDefault() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getGlobalDefaultCurrency(false)).thenReturn(gbp);
        when(transactionService.balance("Alice", "GBP")).thenReturn(Map.of(gbp, 100L));

        assertEquals(BigDecimal.valueOf(100L), economy.getBalance("SomePlugin", aliceUuid));
    }

    @Test
    void hasComparesResolvedBalanceAgainstRequestedAmount() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);
        when(transactionService.balance("Alice", "GBP")).thenReturn(Map.of(gbp, 250L));

        assertTrue(economy.has("SomePlugin", aliceUuid, "world", "GBP", BigDecimal.valueOf(200L)));
        assertFalse(economy.has("SomePlugin", aliceUuid, "world", "GBP", BigDecimal.valueOf(300L)));
    }

    // ---- withdraw ----

    @Test
    void withdrawRejectsNonIntegralAmount() throws CurrenciesException {
        EconomyResponse response = economy.withdraw("SomePlugin", aliceUuid, "world", "GBP", new BigDecimal("1.5"));

        assertEquals(ResponseType.FAILURE, response.type);
        verify(transactionService, never()).debit(any(), any(), anyLong());
    }

    @Test
    void withdrawFailsWhenAccountUnknown() {
        EconomyResponse response = economy.withdraw("SomePlugin", aliceUuid, "world", "GBP", BigDecimal.TEN);

        assertEquals(ResponseType.FAILURE, response.type);
    }

    @Test
    void withdrawFailsWhenCurrencyUnknown() {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        EconomyResponse response = economy.withdraw("SomePlugin", aliceUuid, "world", "ZZZ", BigDecimal.TEN);

        assertEquals(ResponseType.FAILURE, response.type);
    }

    @Test
    void withdrawTranslatesCurrenciesExceptionToFailureResponse() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);
        when(transactionService.debit(alice, gbp, 100L)).thenThrow(
                new CurrenciesException("Cannot debit £1 from Alice because it is greater than 50p, your current balance."));
        when(transactionService.balance("Alice", "GBP")).thenReturn(Map.of());

        EconomyResponse response = economy.withdraw("SomePlugin", aliceUuid, "world", "GBP", BigDecimal.valueOf(100));

        assertEquals(ResponseType.FAILURE, response.type);
        assertEquals("Cannot debit £1 from Alice because it is greater than 50p, your current balance.", response.errorMessage);
    }

    @Test
    void withdrawSucceedsAndReturnsRequestedAmount() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);
        when(transactionService.balance("Alice", "GBP")).thenReturn(Map.of(gbp, 150L));

        EconomyResponse response = economy.withdraw("SomePlugin", aliceUuid, "world", "GBP", BigDecimal.valueOf(100));

        verify(transactionService).debit(alice, gbp, 100L);
        assertEquals(ResponseType.SUCCESS, response.type);
        assertEquals(BigDecimal.valueOf(100), response.amount);
        assertEquals(BigDecimal.valueOf(150L), response.balance);
    }

    // ---- deposit ----

    @Test
    void depositSucceedsAndCallsCredit() throws CurrenciesException {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", false)).thenReturn(gbp);
        when(transactionService.balance("Alice", "GBP")).thenReturn(Map.of(gbp, 350L));

        EconomyResponse response = economy.deposit("SomePlugin", aliceUuid, "world", "GBP", BigDecimal.valueOf(100));

        verify(transactionService).credit(alice, gbp, 100L);
        assertEquals(ResponseType.SUCCESS, response.type);
    }

    @Test
    void depositRejectsNonIntegralAmount() throws CurrenciesException {
        EconomyResponse response = economy.deposit("SomePlugin", aliceUuid, "world", "GBP", new BigDecimal("0.01"));

        assertEquals(ResponseType.FAILURE, response.type);
        verify(transactionService, never()).credit(any(), any(), anyLong());
    }

    // ---- accounts ----

    @Test
    void hasAccountReflectsWhetherAnAccountExistsForTheUuid() {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        UUID unknownUuid = UUID.randomUUID();

        assertTrue(economy.hasAccount(aliceUuid));
        assertFalse(economy.hasAccount(unknownUuid));
    }

    @Test
    void createAccountReportsExistenceRatherThanCreatingOne() {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        UUID unknownUuid = UUID.randomUUID();

        assertTrue(economy.createAccount(aliceUuid, "Alice", true));
        assertFalse(economy.createAccount(unknownUuid, "Ghost", true));
    }

    @Test
    void getAccountNameReturnsEmptyOptionalWhenAccountUnknown() {
        assertTrue(economy.getAccountName(aliceUuid).isEmpty());
    }

    @Test
    void getAccountNameReturnsNameWhenAccountExists() {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        assertEquals("Alice", economy.getAccountName(aliceUuid).get());
    }

    @Test
    void deleteAccountAndRenameAccountFailWhenAccountUnknown() {
        assertFalse(economy.deleteAccount("SomePlugin", aliceUuid));
        assertFalse(economy.renameAccount(aliceUuid, "NewName"));
    }

    @Test
    void deleteAccountDelegatesToAccountService() {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.deleteAccount(alice)).thenReturn(true);

        assertTrue(economy.deleteAccount("SomePlugin", aliceUuid));
    }

    @Test
    void renameAccountDelegatesToAccountService() {
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.renameAccount(alice, "NewName")).thenReturn(true);

        assertTrue(economy.renameAccount(aliceUuid, "NewName"));
        assertTrue(economy.renameAccount("SomePlugin", aliceUuid, "NewName"));
    }

    @Test
    void getUUIDNameMapBuildsMapFromAccountsWithUuid() {
        Account bob = Fixtures.account(11, "Bob");
        UUID bobUuid = UUID.randomUUID();
        bob.setUuid(bobUuid.toString());
        when(accountService.getAllAccountsWithUuid()).thenReturn(List.of(alice, bob));

        Map<UUID, String> result = economy.getUUIDNameMap();

        assertEquals("Alice", result.get(aliceUuid));
        assertEquals("Bob", result.get(bobUuid));
        assertEquals(2, result.size());
    }

    // ---- shared accounts (mapped onto the Holder parent/child chain -- see class Javadoc) ----

    @Test
    void hasSharedAccountSupportIsTrue() {
        assertTrue(economy.hasSharedAccountSupport());
    }

    @Test
    void createSharedAccountOpensABusinessAccountWithTheCallerSuppliedUuid() throws CurrenciesException {
        UUID sharedUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        boolean result = economy.createSharedAccount("SomePlugin", sharedUuid, "The Merchant Guild", aliceUuid);

        assertTrue(result);
        verify(accountService).openAccount("The Merchant Guild", sharedUuid.toString(), "Alice");
    }

    @Test
    void createSharedAccountFailsWhenOwnerUuidIsUnknown() {
        UUID sharedUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();

        assertFalse(economy.createSharedAccount("SomePlugin", sharedUuid, "The Merchant Guild", ownerUuid));
    }

    @Test
    void createSharedAccountFailsWhenAccountServiceThrows() throws CurrenciesException {
        UUID sharedUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.openAccount("The Merchant Guild", sharedUuid.toString(), "Alice"))
                .thenThrow(new CurrenciesException("Account with name The Merchant Guild already exists."));

        assertFalse(economy.createSharedAccount("SomePlugin", sharedUuid, "The Merchant Guild", aliceUuid));
    }

    @Test
    void isAccountOwnerDelegatesToAccountServiceIsOwner() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.isOwner(alice, guild)).thenReturn(true);

        assertTrue(economy.isAccountOwner("SomePlugin", guildUuid, aliceUuid));
    }

    @Test
    void setOwnerAddsOwnershipAndReturnsTrue() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        assertTrue(economy.setOwner("SomePlugin", guildUuid, aliceUuid));
        verify(accountService).addOwner(alice, guild);
    }

    @Test
    void isAccountMemberDelegatesToAccountServiceIsMember() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.isMember(alice, guild)).thenReturn(true);

        assertTrue(economy.isAccountMember("SomePlugin", guildUuid, aliceUuid));
    }

    @Test
    void addAccountMemberIgnoresInitialPermissionsAndGrantsMembership() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        assertTrue(economy.addAccountMember("SomePlugin", guildUuid, aliceUuid, AccountPermission.DEPOSIT, AccountPermission.WITHDRAW));
        verify(accountService).addOwner(alice, guild);
    }

    @Test
    void removeAccountMemberRevokesOwnershipAndReturnsTrue() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        assertTrue(economy.removeAccountMember("SomePlugin", guildUuid, aliceUuid));
        verify(accountService).removeOwner(alice, guild);
    }

    @Test
    void hasAccountPermissionRequiresDirectOwnershipForOwnerTierPermissions() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.isOwner(alice, guild)).thenReturn(false);
        when(accountService.isMember(alice, guild)).thenReturn(true);

        // A mere member (not a direct owner) should not be granted an owner-tier permission,
        // even though they pass the looser isMember check used for operational permissions.
        assertFalse(economy.hasAccountPermission("SomePlugin", guildUuid, aliceUuid, AccountPermission.DELETE));
        assertTrue(economy.hasAccountPermission("SomePlugin", guildUuid, aliceUuid, AccountPermission.DEPOSIT));
    }

    @Test
    void updateAccountPermissionGrantsOrRevokesTheSingleUnderlyingRelationship() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        UUID guildUuid = UUID.randomUUID();
        when(accountService.getAccountFromUniqueId(guildUuid.toString(), false)).thenReturn(guild);
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);

        assertTrue(economy.updateAccountPermission("SomePlugin", guildUuid, aliceUuid, AccountPermission.WITHDRAW, true));
        verify(accountService).addOwner(alice, guild);

        assertTrue(economy.updateAccountPermission("SomePlugin", guildUuid, aliceUuid, AccountPermission.WITHDRAW, false));
        verify(accountService).removeOwner(alice, guild);
    }

    @Test
    void sharedAccountMethodsFailWhenEitherAccountIsUnknown() {
        UUID unknownUuid = UUID.randomUUID();

        assertFalse(economy.isAccountOwner("SomePlugin", unknownUuid, aliceUuid));
        assertFalse(economy.setOwner("SomePlugin", unknownUuid, aliceUuid));
        assertFalse(economy.addAccountMember("SomePlugin", unknownUuid, aliceUuid));
        assertFalse(economy.removeAccountMember("SomePlugin", unknownUuid, aliceUuid));
        assertFalse(economy.hasAccountPermission("SomePlugin", unknownUuid, aliceUuid, AccountPermission.BALANCE));
        assertFalse(economy.updateAccountPermission("SomePlugin", unknownUuid, aliceUuid, AccountPermission.BALANCE, true));
    }

    // ---- accountsAccessTo (backs the deprecated accountsOwnedBy/accountsMemberOf defaults) ----

    @Test
    void accountsAccessToReturnsEmptyListWhenAccountIsUnknown() {
        assertTrue(economy.accountsAccessTo("SomePlugin", UUID.randomUUID(), AccountPermission.OWNER).isEmpty());
    }

    @Test
    void accountsAccessToUsesDirectOwnershipForAnOwnerTierPermission() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.getOwnedAccounts(alice)).thenReturn(List.of(guild));

        List<String> result = economy.accountsAccessTo("SomePlugin", aliceUuid, AccountPermission.OWNER);

        assertEquals(List.of("The Merchant Guild"), result);
        verify(accountService, never()).getMemberAccounts(any());
    }

    @Test
    void accountsAccessToUsesAnyDepthMembershipForAnOperationalPermission() {
        Account guild = Fixtures.account(50, "The Merchant Guild");
        when(accountService.getAccountFromUniqueId(aliceUuid.toString(), false)).thenReturn(alice);
        when(accountService.getMemberAccounts(alice)).thenReturn(List.of(guild));

        List<String> result = economy.accountsAccessTo("SomePlugin", aliceUuid, AccountPermission.DEPOSIT);

        assertEquals(List.of("The Merchant Guild"), result);
        verify(accountService, never()).getOwnedAccounts(any());
    }
}
