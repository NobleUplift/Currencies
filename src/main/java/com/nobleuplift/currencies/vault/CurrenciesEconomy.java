package com.nobleuplift.currencies.vault;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;
import com.nobleuplift.currencies.services.AccountService;
import com.nobleuplift.currencies.services.CurrencyFormatter;
import com.nobleuplift.currencies.services.CurrencyService;
import com.nobleuplift.currencies.services.TransactionService;

import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.economy.EconomyResponse.ResponseType;

/**
 * Adapts Currencies onto VaultUnlocked's {@code Economy} service so third-party plugins that only
 * know the Vault API (shops, auction houses) can transact through Currencies. This is an
 * additional, optional facade -- SupplyAndDemand and everything else in this project keeps calling
 * {@code CurrenciesCore}/the services directly, unaffected by whether VaultUnlocked is installed.
 * <ul>
 * <li>Amounts are mapped 1:1 between Vault's {@code BigDecimal} and Currencies' base-unit
 * {@code long}: one Vault "unit" is one Currencies base unit (scale 0). Non-integral amounts are
 * rejected -- Currencies has no concept of a fraction below its base unit.</li>
 * <li>Currencies' Unit parent/child denomination hierarchy stays entirely internal; Vault only ever
 * sees one flat balance per currency acronym.</li>
 * <li>Calls that omit a currency fall back to the server-wide {@code Currency.globalDefault}
 * currency, not any particular account's own default.</li>
 * <li>Business accounts now always carry a UUID (assigned at creation; backfilled for pre-existing
 * accounts by the 1.1.0-&gt;1.2.0 migration), so {@link #hasSharedAccountSupport()} is {@code true}.
 * Ownership/membership is mapped onto the existing {@code currencies_holder} parent/child chain
 * rather than a new permissions table: a *direct* parent (chain length 1) is an "owner"; any
 * ancestor at any depth is a "member". Currencies has no per-permission-type storage, so every
 * {@link AccountPermission} collapses onto that single relationship -- owner-tier permissions
 * (OWNER, TRANSFER_OWNERSHIP, DELETE, INVITE_MEMBER, REMOVE_MEMBER, CHANGE_MEMBER_PERMISSION)
 * require direct ownership; operational permissions (DEPOSIT, WITHDRAW, BALANCE) accept membership
 * at any depth. {@code updateAccountPermission} can only grant/revoke that one relationship as a
 * whole, not a specific permission. Unlike Vault's apparent single-owner assumption, the Holder
 * table allows multiple parents per child, so {@code setOwner}/{@code addAccountMember} *add* an
 * owner rather than replacing one.</li>
 * <li>Currencies has no per-world concept, so every {@code worldName} parameter is ignored (the
 * interface documents this as "the provider's default world will be used").</li>
 * </ul>
 */
public class CurrenciesEconomy implements Economy {

    private final AccountService accountService;
    private final CurrencyService currencyService;
    private final TransactionService transactionService;
    private final CurrencyFormatter currencyFormatter;

    public CurrenciesEconomy(AccountService accountService, CurrencyService currencyService,
            TransactionService transactionService, CurrencyFormatter currencyFormatter) {
        this.accountService = accountService;
        this.currencyService = currencyService;
        this.transactionService = transactionService;
        this.currencyFormatter = currencyFormatter;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Account resolveAccount(UUID accountID) {
        return accountService.getAccountFromUniqueId(accountID.toString(), false);
    }

    private Currency resolveCurrencyOrDefault(String acronym) {
        return acronym == null
                ? currencyService.getGlobalDefaultCurrency(false)
                : currencyService.getCurrencyFromAcronym(acronym, false);
    }

    private BigDecimal queryBalance(Account account, Currency currency) {
        if (account == null || currency == null) {
            return BigDecimal.ZERO;
        }
        try {
            Map<Currency, Long> balances = transactionService.balance(account.getName(), currency.getAcronym());
            Long amount = balances.get(currency);
            return amount == null ? BigDecimal.ZERO : BigDecimal.valueOf(amount);
        } catch (CurrenciesException e) {
            // balance() throws when the account owns none of this currency; by this point the account
            // and currency are both already confirmed to exist, so the only remaining failure mode is
            // "owns none of it," which is legitimately a zero balance.
            return BigDecimal.ZERO;
        }
    }

    private boolean has(UUID accountID, String currencyAcronym, BigDecimal amount) {
        return queryBalance(resolveAccount(accountID), resolveCurrencyOrDefault(currencyAcronym)).compareTo(amount) >= 0;
    }

    private BigDecimal getBalance(UUID accountID, String currencyAcronym) {
        return queryBalance(resolveAccount(accountID), resolveCurrencyOrDefault(currencyAcronym));
    }

    private EconomyResponse withdraw(UUID accountID, String currencyAcronym, BigDecimal amount) {
        long baseAmount;
        try {
            baseAmount = amount.longValueExact();
        } catch (ArithmeticException e) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.FAILURE,
                    "Currencies does not support fractional amounts below its base unit.");
        }

        Account account = resolveAccount(accountID);
        if (account == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.FAILURE,
                    "No account exists for " + accountID + ".");
        }

        Currency currency = resolveCurrencyOrDefault(currencyAcronym);
        if (currency == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.FAILURE,
                    currencyAcronym == null ? "No default currency has been set." : "Currency " + currencyAcronym + " does not exist.");
        }

        try {
            transactionService.debit(account, currency, baseAmount);
            return new EconomyResponse(amount, queryBalance(account, currency), ResponseType.SUCCESS, "");
        } catch (CurrenciesException e) {
            return new EconomyResponse(BigDecimal.ZERO, queryBalance(account, currency), ResponseType.FAILURE, e.getMessage());
        }
    }

    private EconomyResponse deposit(UUID accountID, String currencyAcronym, BigDecimal amount) {
        long baseAmount;
        try {
            baseAmount = amount.longValueExact();
        } catch (ArithmeticException e) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.FAILURE,
                    "Currencies does not support fractional amounts below its base unit.");
        }

        Account account = resolveAccount(accountID);
        if (account == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.FAILURE,
                    "No account exists for " + accountID + ".");
        }

        Currency currency = resolveCurrencyOrDefault(currencyAcronym);
        if (currency == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.FAILURE,
                    currencyAcronym == null ? "No default currency has been set." : "Currency " + currencyAcronym + " does not exist.");
        }

        try {
            transactionService.credit(account, currency, baseAmount);
            return new EconomyResponse(amount, queryBalance(account, currency), ResponseType.SUCCESS, "");
        } catch (CurrenciesException e) {
            return new EconomyResponse(BigDecimal.ZERO, queryBalance(account, currency), ResponseType.FAILURE, e.getMessage());
        }
    }

    // =========================================================================
    // Plugin info
    // =========================================================================

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "Currencies";
    }

    @Override
    public boolean hasSharedAccountSupport() {
        return true;
    }

    @Override
    public boolean hasMultiCurrencySupport() {
        return true;
    }

    // =========================================================================
    // Currency-related
    // =========================================================================

    @Override
    public int fractionalDigits(String pluginName) {
        return 0;
    }

    @Override
    public String format(BigDecimal amount) {
        return format(null, amount, null);
    }

    @Override
    public String format(String pluginName, BigDecimal amount) {
        return format(pluginName, amount, null);
    }

    @Override
    public String format(BigDecimal amount, String currency) {
        return format(null, amount, currency);
    }

    @Override
    public String format(String pluginName, BigDecimal amount, String currency) {
        Currency c = resolveCurrencyOrDefault(currency);
        return c == null ? amount.toPlainString() : currencyFormatter.formatCurrency(c, amount.longValue());
    }

    @Override
    public boolean hasCurrency(String currency) {
        return currencyService.getCurrencyFromAcronym(currency, false) != null;
    }

    @Override
    public String getDefaultCurrency(String pluginName) {
        Currency c = currencyService.getGlobalDefaultCurrency(false);
        return c == null ? "" : c.getAcronym();
    }

    @Override
    public String defaultCurrencyNamePlural(String pluginName) {
        Currency c = currencyService.getGlobalDefaultCurrency(false);
        if (c == null) {
            return "";
        }
        Unit prime = currencyService.getPrimeUnit(c, false);
        return prime == null ? c.getName() : prime.getAlternate();
    }

    @Override
    public String defaultCurrencyNameSingular(String pluginName) {
        Currency c = currencyService.getGlobalDefaultCurrency(false);
        if (c == null) {
            return "";
        }
        Unit prime = currencyService.getPrimeUnit(c, false);
        return prime == null ? c.getName() : prime.getName();
    }

    @Override
    public Collection<String> currencies() {
        List<String> acronyms = new ArrayList<>();
        for (Currency c : currencyService.getAllCurrencies()) {
            acronyms.add(c.getAcronym());
        }
        return acronyms;
    }

    // =========================================================================
    // Account-related
    // =========================================================================

    @Override
    public boolean createAccount(UUID accountID, String name) {
        return createAccount(accountID, name, true);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, boolean player) {
        // Player accounts are created automatically on PlayerJoinEvent, not on demand -- Currencies
        // has no method to create a UUID-keyed account outside of that flow, so this can only report
        // whether one already exists.
        return resolveAccount(accountID) != null;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName) {
        return createAccount(accountID, name, true);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName, boolean player) {
        return createAccount(accountID, name, player);
    }

    @Override
    public Map<UUID, String> getUUIDNameMap() {
        Map<UUID, String> result = new HashMap<>();
        for (Account account : accountService.getAllAccountsWithUuid()) {
            try {
                result.put(UUID.fromString(account.getUuid()), account.getName());
            } catch (IllegalArgumentException e) {
                // Skip a malformed UUID rather than fail the whole map -- shouldn't happen given the
                // column is only ever written via UUID.randomUUID()/MySQL's UUID(), but a manually
                // edited row shouldn't crash Vault's economy-converter tooling.
            }
        }
        return result;
    }

    @Override
    public Optional<String> getAccountName(UUID accountID) {
        Account account = resolveAccount(accountID);
        return Optional.ofNullable(account == null ? null : account.getName());
    }

    @Override
    public boolean hasAccount(UUID accountID) {
        return resolveAccount(accountID) != null;
    }

    @Override
    public boolean hasAccount(UUID accountID, String worldName) {
        return hasAccount(accountID);
    }

    @Override
    public boolean renameAccount(UUID accountID, String name) {
        Account account = resolveAccount(accountID);
        return account != null && accountService.renameAccount(account, name);
    }

    @Override
    public boolean renameAccount(String pluginName, UUID accountID, String name) {
        return renameAccount(accountID, name);
    }

    @Override
    public boolean deleteAccount(String pluginName, UUID accountID) {
        Account account = resolveAccount(accountID);
        return account != null && accountService.deleteAccount(account);
    }

    // =========================================================================
    // Account balance related
    // =========================================================================

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency) {
        return hasAccount(accountID) && currencyService.getCurrencyFromAcronym(currency, false) != null;
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency, String world) {
        return accountSupportsCurrency(pluginName, accountID, currency);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID) {
        return getBalance(accountID, null);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String world) {
        return getBalance(accountID, null);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String world, String currency) {
        return getBalance(accountID, currency);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, BigDecimal amount) {
        return has(accountID, null, amount);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return has(accountID, null, amount);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        return has(accountID, currency, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, BigDecimal amount) {
        return withdraw(accountID, null, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return withdraw(accountID, null, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        return withdraw(accountID, currency, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, BigDecimal amount) {
        return deposit(accountID, null, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return deposit(accountID, null, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        return deposit(accountID, currency, amount);
    }

    // =========================================================================
    // Shared Account Methods -- mapped onto the currencies_holder parent/child chain; see class
    // Javadoc for the owner-vs-member and permission-collapsing rules.
    // =========================================================================

    /** Owner-tier permissions require direct ownership; everything else accepts membership at any depth. */
    private static boolean requiresDirectOwnership(AccountPermission permission) {
        switch (permission) {
            case OWNER:
            case TRANSFER_OWNERSHIP:
            case DELETE:
            case INVITE_MEMBER:
            case REMOVE_MEMBER:
            case CHANGE_MEMBER_PERMISSION:
                return true;
            default:
                return false;
        }
    }

    /**
     * Accounts {@code accountID} can access under any of the given permissions. If any permission
     * requested requires direct ownership, only directly-owned accounts qualify (matching
     * {@link #hasAccountPermission}'s stricter check for that tier); otherwise any-depth membership
     * is enough.
     */
    private List<Account> accessibleAccounts(UUID accountID, AccountPermission... permissions) {
        Account account = resolveAccount(accountID);
        if (account == null) {
            return List.of();
        }
        for (AccountPermission permission : permissions) {
            if (requiresDirectOwnership(permission)) {
                return accountService.getOwnedAccounts(account);
            }
        }
        return accountService.getMemberAccounts(account);
    }

    /**
     * The pinned VaultUnlockedAPI version (2.16) only has the deprecated, name-returning
     * `accountsAccessTo` -- the UUID-returning `accountsWithAccessTo`/`accountsWithOwnerOf`/
     * `accountsWithMembershipTo` family is `@since 2.17` on the API's `master` branch and doesn't
     * exist in this version, so there's nothing to override for it. `accountsOwnedBy`/
     * `accountsMemberOf` are both `default` methods that delegate to this one, so overriding just
     * this covers all three real entry points.
     */
    @Override
    public List<String> accountsAccessTo(String pluginName, UUID accountID, AccountPermission... permissions) {
        List<String> names = new ArrayList<>();
        for (Account a : accessibleAccounts(accountID, permissions)) {
            names.add(a.getName());
        }
        return names;
    }

    @Override
    public boolean createSharedAccount(String pluginName, UUID accountID, String name, UUID owner) {
        Account ownerAccount = resolveAccount(owner);
        if (ownerAccount == null) {
            return false;
        }
        try {
            accountService.openAccount(name, accountID.toString(), ownerAccount.getName());
            return true;
        } catch (RuntimeException | CurrenciesException e) {
            return false;
        }
    }

    @Override
    public boolean isAccountOwner(String pluginName, UUID accountID, UUID uuid) {
        Account account = resolveAccount(accountID);
        Account owner = resolveAccount(uuid);
        return account != null && owner != null && accountService.isOwner(owner, account);
    }

    @Override
    public boolean setOwner(String pluginName, UUID accountID, UUID uuid) {
        Account account = resolveAccount(accountID);
        Account owner = resolveAccount(uuid);
        if (account == null || owner == null) {
            return false;
        }
        accountService.addOwner(owner, account);
        return true;
    }

    @Override
    public boolean isAccountMember(String pluginName, UUID accountID, UUID uuid) {
        Account account = resolveAccount(accountID);
        Account member = resolveAccount(uuid);
        return account != null && member != null && accountService.isMember(member, account);
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid) {
        Account account = resolveAccount(accountID);
        Account member = resolveAccount(uuid);
        if (account == null || member == null) {
            return false;
        }
        accountService.addOwner(member, account);
        return true;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid, AccountPermission... initialPermissions) {
        return addAccountMember(pluginName, accountID, uuid);
    }

    @Override
    public boolean removeAccountMember(String pluginName, UUID accountID, UUID uuid) {
        Account account = resolveAccount(accountID);
        Account member = resolveAccount(uuid);
        if (account == null || member == null) {
            return false;
        }
        accountService.removeOwner(member, account);
        return true;
    }

    @Override
    public boolean hasAccountPermission(String pluginName, UUID accountID, UUID uuid, AccountPermission permission) {
        Account account = resolveAccount(accountID);
        Account member = resolveAccount(uuid);
        if (account == null || member == null) {
            return false;
        }
        return requiresDirectOwnership(permission) ? accountService.isOwner(member, account) : accountService.isMember(member, account);
    }

    @Override
    public boolean updateAccountPermission(String pluginName, UUID accountID, UUID uuid, AccountPermission permission, boolean value) {
        Account account = resolveAccount(accountID);
        Account member = resolveAccount(uuid);
        if (account == null || member == null) {
            return false;
        }
        if (value) {
            accountService.addOwner(member, account);
        } else {
            accountService.removeOwner(member, account);
        }
        return true;
    }
}
