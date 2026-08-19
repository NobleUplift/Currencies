# VaultUnlocked Economy Adapter for Currencies

## Context

Currencies is a JDBC-backed multi-currency plugin that SupplyAndDemand depends on directly — that dependency must stay intact, so nothing here should route SupplyAndDemand's integration through Vault.

The question driving this work: should Currencies implement the VaultUnlocked economy API, or is VaultUnlockedAPI a competing economy implementation that would make Currencies redundant?

Research (via GitHub API against `TheNewEconomy/VaultUnlockedAPI`) confirmed **VaultUnlockedAPI is a pure interface/service-locator layer** — no persistence, no concrete economy, no denomination model. Real economy plugins register themselves as *providers* via Bukkit's `ServicesManager`. Implementing it makes Currencies one more provider, exposing a standard hook for third-party plugins (shops, auction houses) that only know the Vault API — it does not replace or compete with Currencies' own data model, and SupplyAndDemand keeps using `CurrenciesCore`/the services directly, unaffected.

**Codebase has moved since this plan was first drafted** — `CurrenciesCore` was decomposed into `com.nobleuplift.currencies.services`: `AccountService`, `CurrencyService`, `TransactionService`, `Ledger`, `CurrencyFormatter`, plus the `CurrencyRepository`/`JdbcCurrencyRepository` persistence port and `ConnectionProvider` (implemented by `DatabaseManager`). `CurrenciesCore` is now a thin static facade that constructs these five services once in `CurrenciesCore.init(DatabaseManager)` and delegates every call — it's unchanged as the external API surface, but the adapter below is built against the services directly for testability, matching how the existing test suite already treats them as independently injectable collaborators. A real JUnit5/Mockito test suite (`junit-jupiter` 5.11.3, `mockito-core`/`mockito-junit-jupiter` 5.23.0, all test-scoped in `pom.xml`) already exists for these services — the new tests below follow its exact conventions rather than inventing new ones.

Key friction points the design accounts for:
- Vault's `Economy` is `BigDecimal` end-to-end; Currencies is `long` base units end-to-end. Amounts are mapped 1:1 at scale 0 (one Vault "unit" = one Currencies base unit) — this avoids picking an arbitrary "which denomination is 1.00" convention, since Currencies has no fractional concept below its base unit.
- Vault currencies are flat string keys with one balance each; Currencies' Unit parent/child hierarchy stays entirely internal and invisible to Vault.
- No `has()`/sufficiency-check method exists anywhere in the codebase — `pay()`/`bill()` each inline a check against the base `Holding`. The adapter does not add a new production method for this: `withdraw` calls `debit()` directly and translates a thrown `CurrenciesException` into `EconomyResponse.ResponseType.FAILURE`. (Confirm during implementation that `TransactionService.debit` already enforces sufficiency the same way `pay` does — if it doesn't, that's a pre-existing gap worth a one-line fix, not a new abstraction.)
- `CurrencyFormatter.resolveCurrency(Account, String)` throws on a bare integer with no unit symbol (`parts.length <= 1`), contradicting CLAUDE.md:111's documented behavior: *"A plain integer with no symbol is interpreted in the prime unit of the default currency."* Read in context, that sentence follows "if the currency is ambiguous the **player's** default is used" — so "the default currency" means the account's own `Account.defaultCurrency` (set via `/currencies setdefault`), not a separate server-wide flag. `resolveCurrency` is the only place with the `Account` context needed to honor this correctly, and it's what `pay`/`bill`/`credit`/`debit` actually call for un-prefixed amounts — so it's the only method touched. (`CurrencyFormatter.parseCurrency(Currency, String)` has no `Account` parameter, can't validate against a per-account default, and isn't on the adapter's call path — the Vault adapter converts `BigDecimal`↔`long` directly against an already-resolved `Currency`, never routing through string parsing — so it's left untouched.)
- Business accounts (`AccountService.openAccount`, `Holder` rows) have no natural `OfflinePlayer`/UUID identity — mapped via VaultUnlocked's shared-account methods (`createSharedAccount`/`addAccountMember`/`hasAccountPermission`) onto `AccountService.openAccount` and the `Holder` parent/child rows it already writes.
- `getDefaultCurrency(String pluginName)` takes no account/UUID, so it can't be account-scoped. **Currencies already has a global-default-currency concept** — `Currency.globalDefault` (backed by the `currencies_currency.default_currency` column, set via the v1.0.0→v1.1.0 migration). Reuse it rather than inventing a new Vault-only config key: add `CurrencyService.getGlobalDefaultCurrency()` if no equivalent lookup exists yet. The legacy v1 single-currency `Economy` interface (kept for back-compat with old Vault-only plugins) instead uses each account's own `Account.defaultCurrency`, consistent with how the rest of the plugin already disambiguates amounts.
- `Account.uuid` is a `String`, not `java.util.UUID` — the adapter converts `accountID.toString()` before calling `AccountService.getAccountFromUniqueId(String, boolean)`.
- `Account.isReserved()` already exists (IDs 1–4) and is enforced inside `TransactionService`; the adapter doesn't need its own reserved-account check — it just surfaces whatever `TransactionService` throws.

## Implementation

**1. Dependency wiring**
- Add `net.milkbowl.vault:VaultUnlockedAPI` to `pom.xml` as `provided` scope (must NOT be shaded/relocated — it's a contract resolved at runtime against the real VaultUnlocked plugin's classes; also keeps `com.nobleuplift.currencies.vault.CurrenciesEconomy` lazily-loaded so servers without VaultUnlocked installed never need those classes on the runtime classpath).
- Add `softdepend: [VaultUnlocked]` to `src/main/resources/plugin.yml`.
- No new config key. `getDefaultCurrency` reads the existing `Currency.globalDefault` flag.

**2. Small additions to the services layer (all in `com.nobleuplift.currencies.services`)**
- `CurrencyService`: add `getGlobalDefaultCurrency()` (or confirm one already exists) backed by `Currency.globalDefault`. Needed by the adapter's `getDefaultCurrency(pluginName)` (see below). Add a corresponding case to `CurrencyServiceTest`.
- `CurrencyFormatter.resolveCurrency(Account account, String amount)`: when `parts.length <= 1` (no unit symbol present), instead of throwing, resolve to `account.getDefaultCurrency()` and compute `baseAmount` against that currency's prime unit (reuse the existing `queryBaseUnit`/prime-unit lookup already in the method). If the account has no default currency set, throw the existing "no prime unit located" error rather than silently falling back to the global default — CLAUDE.md ties this specifically to the *player's* default, not a server-wide one, so an account with no default configured should be told to set one, matching how the shared-symbol-ambiguity branch a few lines above already behaves. No signature change.
- Extend `CurrencyFormatterTest` with cases for: bare integer with an account default set (resolves correctly), bare integer with no account default set (still throws, with the existing message).
- `CurrenciesCore`: add accessor methods (`getAccountService()`, `getCurrencyService()`, `getTransactionService()`, `getCurrencyFormatter()`) exposing the singleton service instances already constructed in `init(...)`. This lets `Currencies.java` wire the adapter with the *same* service instances the rest of the plugin uses, without `CurrenciesCore` itself importing any Vault classes (keeping it usable when VaultUnlocked isn't installed).

**3. New adapter class** `com.nobleuplift.currencies.vault.CurrenciesEconomy`
- Constructor-injected with `AccountService`, `CurrencyService`, `TransactionService`, `CurrencyFormatter` — calls these directly rather than the static `CurrenciesCore` facade, so tests can mock each collaborator individually (matching `TransactionServiceTest`'s pattern of mocking peer services).
- Implements `net.milkbowl.vault2.economy.Economy` (and the legacy `net.milkbowl.vault.economy.Economy` for back-compat, per VaultUnlocked's stated full-backward-compatibility goal).
- `hasMultiCurrencySupport()` → `true`; `currencies()` → acronyms of all currencies (via `CurrencyService`); `hasCurrency(String)` → lookup via `getCurrencyFromAcronym(acronym, false)`.
- `getDefaultCurrency(String pluginName)` → `currencyService.getGlobalDefaultCurrency()`.
- `balance`/`deposit`/`withdraw`(`pluginName, accountID, world, currency, amount`): resolve `Account` via `accountService.getAccountFromUniqueId(accountID.toString(), false)`, resolve `Currency` via `currencyService.getCurrencyFromAcronym(currency, false)`, delegate to `TransactionService`'s `(Account, Currency, long)` balance/credit/debit overloads, converting `BigDecimal` ↔ `long` at scale 0 (`BigDecimal.longValueExact()`; reject non-integral amounts with `ResponseType.FAILURE` before calling anything).
- `transfer(...)` → delegates to `TransactionService.pay(Account, Account, Currency, long)`.
- Legacy v1 single-currency methods (`getBalance(OfflinePlayer)` etc.) → resolve the account's own `Account.defaultCurrency` and delegate the same way.
- `createSharedAccount`/`addAccountMember`/`hasAccountPermission` (exact signatures to confirm from the API jar during implementation) → map to `AccountService.openAccount` and the `Holder` rows it writes for business accounts.
- `format(BigDecimal, currency)` → convert to `long` then delegate to `CurrencyFormatter.formatCurrency`.
- `fractionalDigits(currency)` → `0` (no sub-base-unit precision exists in Currencies).
- Any `CurrenciesException`/`CurrenciesRuntimeException` from a service call is caught and translated to `EconomyResponse`/`MultiEconomyResponse` with `ResponseType.FAILURE` and the exception message (the exception's own message/cause chain is preserved for logging, not just swallowed).

**4. Registration** in `Currencies.java` `onEnable()`, after `CurrenciesCore.init(db)`:
```java
if (getServer().getPluginManager().getPlugin("VaultUnlocked") != null) {
    getServer().getServicesManager().register(
        Economy.class,
        new CurrenciesEconomy(
            CurrenciesCore.getAccountService(),
            CurrenciesCore.getCurrencyService(),
            CurrenciesCore.getTransactionService(),
            CurrenciesCore.getCurrencyFormatter()),
        this, ServicePriority.Normal);
}
```
Unregister in `onDisable()` via `getServer().getServicesManager().unregisterAll(this)`.

**5. Unit tests** — `src/test/java/com/nobleuplift/currencies/vault/CurrenciesEconomyTest.java`, following the existing suite's conventions exactly:
- Package-private class, no `public` modifier, `@BeforeEach setUp()` building fresh `Mockito.mock(...)` instances for `AccountService`/`CurrencyService`/`TransactionService`/`CurrencyFormatter` (peer services, per the established "fake the repository you own, mock everything else" rule — there's no repository owned here, so everything is mocked) and reusing `Fixtures` for `Account`/`Currency`/`Unit` construction.
- JUnit5 `Assertions` static imports, no AssertJ/Hamcrest; descriptive camelCase test method names (no `test` prefix); flat class with `// ---- methodName ----` section comments, no `@Nested`.
- Cases: `hasMultiCurrencySupport`/`currencies`/`hasCurrency` delegate correctly; `getDefaultCurrency` reads the global default; `balance`/`deposit`/`withdraw` resolve account+currency and convert `BigDecimal`↔`long` correctly at the boundary (including rejecting non-integral amounts); `withdraw` on a thrown `CurrenciesException` returns `ResponseType.FAILURE` rather than propagating; `transfer` delegates to `pay`; `format`/`fractionalDigits` delegate correctly; legacy v1 methods resolve `Account.defaultCurrency`.
- Extend `CurrencyFormatterTest` for the bare-integer `resolveCurrency` fix (account-default-set and no-default-still-throws cases), and `CurrencyServiceTest` for `getGlobalDefaultCurrency()`.

## Verification
- `mvn clean package` — confirm the shaded jar does NOT bundle `VaultUnlockedAPI` classes (provided scope), and that HikariCP/mysql-connector-j relocation still works.
- `mvn test` — full suite green, including the new/extended tests above.
- Manual: run a Paper test server with VaultUnlocked + a shop/economy-consumer plugin installed alongside Currencies; verify `/currencies` commands still work unchanged, and that the shop plugin can read/deposit/withdraw balances through Vault and see them reflected in `/currencies balance`.
- Confirm SupplyAndDemand (separate plugin) is untouched — it keeps calling `CurrenciesCore`/services directly, not through the new adapter.
