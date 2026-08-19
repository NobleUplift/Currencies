# Vault / VaultUnlocked integration notes

Findings from implementing `com.nobleuplift.currencies.vault.CurrenciesEconomy`, verified directly
against source (GitHub API / `gh api`), not assumed from memory. Read this before touching anything
Vault-related.

## Original Vault is dead; VaultUnlocked is the maintained fork

- Original Vault (`net.milkbowl.vault:VaultAPI`, spigotmc.org/resources/vault.34315) last released
  1.7.3 in 2020; maintainer inactive for years. Still technically works but nothing is being merged.
- `TheNewEconomy/VaultUnlocked` (plugin) + `TheNewEconomy/VaultUnlockedAPI` (API jar) is the
  community-maintained continuation — PR-friendly, adds multicurrency support, Folia compatibility,
  BigDecimal amounts, while staying backward-compatible with the classic Vault API surface.
- There is also a separate, distinct "Vault 2.0" project on SpigotMC — not the same thing as
  VaultUnlocked. Don't conflate them.

## Maven coordinates (already wired into `pom.xml`)

```xml
<repository>
    <id>codemc-creatorfromhell</id>
    <url>https://repo.codemc.io/repository/creatorfromhell/</url>
</repository>

<dependency>
    <groupId>net.milkbowl.vault</groupId>
    <artifactId>VaultUnlockedAPI</artifactId>
    <version>2.16</version>
    <scope>provided</scope>
</dependency>
```

`provided` scope is deliberate: this must NOT be shaded into `Currencies-*.jar`. It's a contract
resolved at runtime against the real `VaultUnlocked` plugin's classes. Verified the shaded jar
excludes `net/milkbowl/**` via `jar tf target/Currencies-1.21.0.jar | grep milkbowl`.

`plugin.yml` declares `softdepend: [VaultUnlocked]` — servers without VaultUnlocked installed still
load Currencies fine; the adapter class is only instantiated (and thus only needs
`net.milkbowl.vault2.economy.Economy` on the runtime classpath) inside the
`getPlugin("VaultUnlocked") != null` guard in `Currencies.onEnable()`.

## VaultUnlockedAPI is a pure interface/service-locator layer

No persistence, no concrete economy implementation, no denomination model. Real economy plugins
(TheNewEconomy, Polyconomy, iConomyUnlocked) register themselves as *providers* via Bukkit's
`ServicesManager`. Implementing it makes Currencies one more provider, not a competitor and not a
replacement — SupplyAndDemand and everything else keeps calling `CurrenciesCore`/the services
directly, untouched.

## Two separate interfaces exist — and VaultUnlocked does NOT bridge them

This is the single most important, most easily-missed fact, and it directly contradicts an
assumption made mid-implementation (corrected only after reading `Vault.java` from
`TheNewEconomy/VaultUnlocked` directly):

- **Legacy**: `net.milkbowl.vault.economy.Economy` — `double`-based, keyed by `OfflinePlayer`/player
  name String, no native multi-currency. Deprecated but still what many older shop/economy-consumer
  plugins call.
- **Modern**: `net.milkbowl.vault2.economy.Economy` — `BigDecimal`-based, keyed by `UUID`, native
  multi-currency (`currencies()`, per-call `currency` string params), shared-account support.

**Both interfaces are bundled in the same `VaultUnlockedAPI` artifact** (packages
`net.milkbowl.vault.economy` and `net.milkbowl.vault2.economy` respectively) — no second dependency
needed to implement both.

VaultUnlocked's own plugin code (`Vault.java`) registers/looks these up as **two entirely
independent Bukkit `ServicesManager` registrations** — `Economy.class` (legacy) and
`net.milkbowl.vault2.economy.Economy.class` (modern). It tracks both only for its `/vault-info`
diagnostic display and a manual, admin-triggered `/vault-convert <from> <to>` command that migrates
balances between two *already-registered* providers by iterating every offline player once. There is
**no automatic bridging of live calls** in either direction.

**Practical consequence**: a provider plugin that only registers the modern `Economy` service is
*invisible* to any consumer plugin that still looks up the legacy `Economy.class` service (still
common — many shop plugins predate VaultUnlocked's v2 API), and vice versa. To be visible to both
old-API-only and new-API-aware consumer plugins, **a provider must register under both interfaces
separately.**

## Current state of `CurrenciesEconomy` (reassessed against the actual pinned jar, not `master`)

`com.nobleuplift.currencies.vault.CurrenciesEconomy` implements **only** the modern
`net.milkbowl.vault2.economy.Economy`. Verified via `javap -classpath
~/.m2/repository/net/milkbowl/vault/VaultUnlockedAPI/2.16/VaultUnlockedAPI-2.16.jar
net.milkbowl.vault2.economy.Economy` (the actually-resolved jar, not a GitHub source fetch) that
**every `abstract` method on the pinned 2.16 interface is overridden** — nothing is missing from the
version actually in use. Registered/unregistered via `ServicesManager` in
`Currencies.onEnable()`/`onDisable()`, gated on `VaultUnlocked` being installed.

The handful of `default` methods that aren't explicitly overridden all correctly compose through
methods that *are* implemented, so they aren't gaps: `balance()`×3 and `set()`×3 delegate to
`getBalance()`/`withdraw()`/`deposit()`; `fractionalDigits(pluginName, currency)` delegates to
`fractionalDigits(pluginName)`; `accountsOwnedBy`/`accountsMemberOf` delegate to the now-overridden
`accountsAccessTo`.

**Correction to a claim previously made in this doc**: an earlier version of this section said
`transfer()`, `canWithdraw()`/`canDeposit()` were un-overridden `default` methods on `Economy`. That
was wrong — checked again with `javap` against the pinned 2.16 jar, and **none of `transfer`,
`canWithdraw`, `canDeposit`, `supportsAsync`, or `async` exist on `Economy` in 2.16 at all** (they're
later additions on `master`, `@since 2.19`/`2.20`). There was nothing to override because the methods
aren't there yet in this version — not a gap, just N/A until the dependency is bumped. This was the
same "`master` is ahead of the pinned release" pitfall documented below, just not yet caught in this
one spot; always re-verify against `javap` output, not a doc's own prior claims.

### Real remaining caveats on the v2 (implemented) side

#### `createAccount` is existence-only, not creation

Vault's contract for `createAccount(accountID, name, player)` is "make a new account, return whether
creation succeeded." `CurrenciesEconomy` doesn't create anything — it calls
`accountService.getAccountFromUniqueId(accountID.toString(), false)` and returns whether a row already
exists. Currencies has no code path that can satisfy what Vault is actually asking for here:
- **Player accounts** are only ever created inside `Currencies.onPlayerJoin()`, which needs a live
  `PlayerJoinEvent`/`Player` object — there's no service method that inserts a player account from a
  bare UUID+String.
- **Business accounts** go through `AccountService.openAccount(name, owner)`, which requires an
  existing *named* owner account. Vault's `createAccount` signature has no owner parameter at all, so
  there's nothing to satisfy that requirement with even if we wanted to treat this as a business
  account creation.

Practical effect: a Vault consumer that proactively creates accounts for offline players (some
economy-migration or shop-setup tools do this) gets `false` and no account is ever created — not an
error, just silent non-creation. If the account already happens to exist, it returns `true`, but
that only means "yes it's there," not "yes I created it just now."

#### Permissions collapse onto a single owner/member relationship

`hasAccountPermission`/`updateAccountPermission`/`accountsAccessTo` all map Vault's 9
`AccountPermission` values (`DEPOSIT`, `WITHDRAW`, `BALANCE`, `TRANSFER_OWNERSHIP`, `INVITE_MEMBER`,
`REMOVE_MEMBER`, `CHANGE_MEMBER_PERMISSION`, `OWNER`, `DELETE`) onto Currencies' one owner/member
`currencies_holder` relationship, since there's no per-permission storage in the schema. Owner-tier
permissions (`OWNER`, `TRANSFER_OWNERSHIP`, `DELETE`, `INVITE_MEMBER`, `REMOVE_MEMBER`,
`CHANGE_MEMBER_PERMISSION`) require direct ownership (`Holder.length = 1`); operational permissions
(`DEPOSIT`, `WITHDRAW`, `BALANCE`) accept membership at any depth (`length > 0`). This means
`updateAccountPermission` can only grant/revoke the relationship as a whole — there's no way to give
someone `DEPOSIT` without also giving them `WITHDRAW` and `BALANCE`, or to revoke just one of the
three. Deliberate, documented approximation, not full fidelity to Vault's permission model.

#### `deleteAccount` only succeeds for a genuinely unused account

By design, not a bug — see the "Closed" entry below for the mechanism (the schema's
`ON DELETE NO ACTION` foreign keys on `currencies_holding`/`currencies_transaction`/`currencies_holder`
correctly refuse to delete an account that's ever held currency or transacted). The caveat is about
caller expectations: a Vault consumer author calling `deleteAccount` will get `false` far more often
than they might expect from a "delete" call, since almost any real account has transaction history,
and the `boolean`-only return gives no way to distinguish "refused to protect data" from any other
failure mode.

#### UUID-returning listing methods (`accountsWithAccessTo` family) aren't implemented

These don't exist in the currently-pinned `VaultUnlockedAPI` version at all (see the version-pinning
note and the `master`-vs-pinned-release pitfall below) — there's nothing to override yet.
`AccountService.getOwnedAccounts`/`getMemberAccounts` are already built and ready to back an
adapter-side override once the pinned version actually includes them.

#### No live-server verification

Everything above has been checked with `mvn test` (Mockito-mocked collaborators) and `mvn package`
(the jar builds and shades correctly), but never run against a live Paper server with `VaultUnlocked`
and an actual shop/economy-consumer plugin installed. The unit tests prove the adapter's internal
logic is correct against its own assumptions about the `Economy` contract; they don't prove a real
Vault consumer plugin gets what it expects when it actually calls through the registered service.

### Dependency is pinned well behind latest — this was an oversight, not a deliberate choice

`pom.xml` pins `net.milkbowl.vault:VaultUnlockedAPI:2.16`. Checking
`https://repo.codemc.io/repository/creatorfromhell/net/milkbowl/vault/VaultUnlockedAPI/maven-metadata.xml`
directly shows the real available versions are `2.15` through `2.20`, with **`2.20` marked as
latest/release** (updated 2026-08-13) — `2.16` is four releases behind, not a considered "stable/LTS"
pin. It was picked early in this work from an example snippet in the `VaultUnlockedAPI` README without
checking whether it was actually current, and never revisited. `2.20` would bring the UUID-returning
listing methods above plus `transfer`/`canWithdraw`/`canDeposit`/`supportsAsync`/`async` (all
confirmed absent from `2.16` via `javap`) into scope. Bumping the version is a one-line `pom.xml`
change but is a real decision (new methods become available to implement, and worth re-running
`javap` against whatever version is chosen rather than assuming) — hasn't been done yet, pending a
decision on whether to do it now or as part of a later pass.

### Not implemented at all: legacy `net.milkbowl.vault.economy.Economy` (v1)

Currencies is currently **invisible to any consumer plugin that only knows the old Vault API** —
confirmed earlier that VaultUnlocked does not bridge legacy and modern registrations automatically
(see above). This is deliberately scoped as **its own separate piece of work with its own commit**,
not folded into the v2 adapter's changes.

If/when implementing it:
- `net.milkbowl.vault.economy.AbstractEconomy` (also bundled in the same `VaultUnlockedAPI` artifact
  — no new dependency needed) is a real convenience: it implements every `OfflinePlayer`-taking
  overload by delegating to the plain `String playerName` version, roughly halving the ~34-method
  surface to ~28 abstract methods to actually implement. Extend it rather than implementing `Economy`
  directly.
- **Re-verify the legacy interface's method list with `javap` against the pinned jar before writing
  any code** — the same `master`-vs-`2.16` gap that bit the UUID-returning listing methods on the v2
  side could just as easily apply here and wasn't re-checked.
- The legacy interface is **name-keyed**, not UUID-keyed — it maps more directly onto Currencies'
  *existing* player-name-based API (`AccountService.getAccountFromPlayer(String, boolean)`,
  `TransactionService.balance(String, String)`) than the modern UUID-based adapter did.
- Bank methods (`createBank`/`deleteBank`/`bankBalance`/`bankHas`/`bankWithdraw`/`bankDeposit`/
  `isBankOwner`/`isBankMember`/`getBanks`) have no Currencies equivalent (no "bank" concept distinct
  from a business account) — `hasBankSupport()` should return `false` and the bank methods should be
  unsupported stubs. Don't confuse this with shared-account support (business accounts/`Holder`
  chains), which the modern adapter *does* implement.

### Pitfall: `VaultUnlockedAPI`'s GitHub `master` branch is ahead of the pinned Maven release

Fetching `Economy.java` from `master` shows methods (the UUID-returning `accountsWithAccessTo`
family; `transfer`/`canWithdraw`/`canDeposit`/`supportsAsync`/`async`) that **do not exist** in the
pinned `2.16` jar — they're later additions (`@since 2.17` through `2.20`). Attempting to `@Override`
a `master`-only method against the pinned jar is a compile error, not a silent bug, so it gets caught
— but the doc-writing pitfall is subtler: describing a `master`-sourced method as an "un-overridden
default that exists" (as this doc itself did, twice, for two different method groups, before being
corrected each time) is *not* caught by the compiler, since it's just documentation being wrong, not
code. **Always cross-check `javap -classpath <resolved-jar-path> <class>` against the actual pinned
jar before writing anything here** — don't infer version-availability from a `master` source fetch,
and don't trust this doc's own prior claims either without re-checking.

### Closed (previously listed as gaps, since fixed)

- `getUUIDNameMap()` now returns real data via `AccountService.getAllAccountsWithUuid()` (backed by
  `CurrencyRepository.queryAccountsWithUuid`, a new `SELECT ... WHERE uuid IS NOT NULL` query) instead
  of an empty map.
- `renameAccount`/`deleteAccount` are real now, not `false` stubs — see
  `AccountService.renameAccount`/`deleteAccount`. `deleteAccount` only succeeds for a genuinely unused
  account: it deletes the account's own Holder self-link first (every account, player or business,
  gets one at creation, so it would otherwise always block the delete on its own), then attempts the
  account row delete itself, which the schema's `ON DELETE NO ACTION` foreign keys
  (`currencies_holding`, `currencies_transaction`, remaining `currencies_holder` rows) will correctly
  reject if the account has ever held currency or appeared in a transaction — that rejection is
  Currencies protecting real data, not a bug, and surfaces as a `false` return, not an exception.
- `accountsAccessTo(pluginName, accountID, AccountPermission...)` (the deprecated, name-returning
  method that does exist in 2.16) is overridden, backing `accountsOwnedBy`/`accountsMemberOf` for free
  since both are `default` methods delegating to it. Reuses the same owner-vs-member classification as
  `hasAccountPermission`.

## Shared-account support: business accounts now always have a UUID

Originally `hasSharedAccountSupport()` returned `false`: Currencies' business accounts
(`AccountService.openAccount`) were identified by name only, with `Account.uuid` always `NULL`, so
there was no way to honor Vault's UUID-keyed shared-account calls. **This has since been fixed** —
business accounts always get a UUID now (random, or the caller-supplied one for
`createSharedAccount`), and `hasSharedAccountSupport()` is `true`.

- `currencies_account.uuid` was already a nullable, unique-indexed column shared by both player and
  business accounts (no separate table) — no schema change was needed, only a migration to backfill
  existing rows and a code change to stop leaving new business accounts uuid-less.
- Migration `migrateFromV110()` (config version `1.1.0` -> `1.2.0`, in `Currencies.java`) runs
  `UPDATE currencies_account SET uuid = UUID() WHERE uuid IS NULL AND id > 4` — backfills every
  pre-existing business account, deliberately skipping the four reserved system accounts (IDs 1-4),
  which were never business accounts and have no reason to be Vault-addressable.
- `AccountService.openAccount(String name, String owner)` now generates `UUID.randomUUID()` and
  passes it through to a new `openAccount(String name, String uuid, String owner)` overload, which
  the Vault adapter's `createSharedAccount` calls directly with Vault's caller-supplied `accountID`
  instead of a random one.
- Ownership/membership is mapped onto the **existing** `currencies_holder` parent/child chain rather
  than a new permissions table (`AccountService.isOwner`/`isMember`/`addOwner`/`removeOwner`, all raw
  JDBC against `currencies_holder`, matching how `openAccount` already wrote that table): a *direct*
  parent (chain `length = 1`) is an "owner"; any ancestor at any depth (`length > 0`) is a "member."
  Vault's `AccountPermission` enum (`DEPOSIT`, `WITHDRAW`, `BALANCE`, `TRANSFER_OWNERSHIP`,
  `INVITE_MEMBER`, `REMOVE_MEMBER`, `CHANGE_MEMBER_PERMISSION`, `OWNER`, `DELETE`) has no
  per-permission storage in Currencies, so every permission collapses onto that single relationship:
  owner-tier permissions (`OWNER`, `TRANSFER_OWNERSHIP`, `DELETE`, `INVITE_MEMBER`, `REMOVE_MEMBER`,
  `CHANGE_MEMBER_PERMISSION`) require direct ownership; operational permissions (`DEPOSIT`,
  `WITHDRAW`, `BALANCE`) accept membership at any depth. `updateAccountPermission` can only
  grant/revoke that one relationship as a whole, not a specific permission — documented as a known
  simplification, not a bug.
- Unlike Vault's apparent single-owner assumption, `currencies_holder`'s primary key is
  `(parent_account_id, child_account_id)` — it already allows multiple parents per child. So
  `setOwner`/`addAccountMember` **add** an owner rather than replacing one; there's no "the" owner to
  displace.

## Other real gaps found and fixed along the way (not Vault-specific, but surfaced by this work)

- `TransactionService.debit(Account, Currency, long)` had **no balance-sufficiency check at all**
  (unlike `pay()`) — an admin running `/currencies debit` could push a balance negative. Fixed with
  the same `compactHoldings` + sufficiency-check pattern `pay()` uses.
- `CurrencyFormatter.resolveCurrency(Account, String)` threw on a bare integer with no unit symbol,
  contradicting CLAUDE.md's documented "a plain integer is interpreted in the prime unit of the
  player's default currency" behavior. Fixed — falls back to `account.getDefaultCurrency()`, still
  throws if the account has none set (no server-wide fallback; CLAUDE.md ties this specifically to
  the *player's* default).
