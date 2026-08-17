# Decompose `CurrenciesCore` God Object (ASSESSMENT.md Issue #1)

## Context

`ASSESSMENT.md` flags `CurrenciesCore` (2445 lines, `public final class` with every member `static`) as a High-severity static god object: it mixes currency/unit CRUD, account management, five transaction types, the bill workflow, the compacting algorithm, and currency string parsing/formatting in one class with no instance state, so it can't be subclassed, mocked, or unit tested. A prior pass already injected a `DatabaseManager` via `CurrenciesCore.init(db)`, removing the old service-locator call (`Currencies.getInstance().getDatabase()`) from every method — but the class itself is still one undivided static facade. ASSESSMENT.md explicitly defers full decomposition as "a separate future initiative" and requires the public static API to stay intact for external plugins that depend on it (the class javadoc calls it "the main interface for accessing Currencies from another plugin"). This plan is that initiative, plus a follow-on unit-test initiative done as its own separate commit sequence once decomposition lands, per the user's request.

Confirmed facts this plan relies on (verified directly, not just from exploration agents):
- `CurrenciesCore.java:54-65` — `public final class`, 4 reserved-account `public static final int` constants, `private static DatabaseManager db`, `init(DatabaseManager)` setter. Single composition seam.
- `DatabaseManager.java` — concrete, non-final class wrapping a Hikari `HikariDataSource`, two methods: `Connection getConnection() throws SQLException` and `void close()`. (See the dependency-inversion decision below: this method set becomes the `ConnectionProvider` interface, which `DatabaseManager` implements.)
- The **entire** external blast radius is `Currencies.java` (one call: `CurrenciesCore.init(db)` in `onEnable()`) and `CurrenciesCommand.java` (~20 of the 34 public methods, ~49 call sites). No other file in the repo touches `CurrenciesCore`.
- Entities (`Currency`, `Unit`, `Account`, `Holding`, `Transaction`, `Holder`) are already plain POJOs, no ORM, no static state — nothing to change there.
- Test infra is currently a no-op: `src/test/java/.../CurrenciesTest.java` is an empty stub `main()`, and `pom.xml` only has `junit:junit:3.8.1` (JUnit 3, pre-`@Test`). No Mockito, no in-memory DB. No antrun/Ebean bytecode-enhancement step exists to conflict with adding real test tooling (CLAUDE.md's Java-7/Ebean/antrun claims are stale — actual `pom.xml` targets Java 21 — but fixing that doc is out of scope here beyond this one-line note).

**Dependency inversion decision.** An earlier draft of this plan used plain constructor injection of concrete classes (dependency injection without inversion — every service would depend on concrete `CurrencyRepository`, `DatabaseManager`, etc.). After discussion, the plan now inverts the one seam that's actually worth inverting: the persistence boundary. `CurrencyRepository` becomes an interface (a "port"), implemented by `JdbcCurrencyRepository` (an "adapter"); `DatabaseManager` similarly gets a `ConnectionProvider` interface. Domain/orchestration classes (`Ledger`, `CurrencyFormatter`, `CurrencyService`, `AccountService`, `TransactionService`) stay concrete — they're pure coordination logic with no second implementation ever likely, so wrapping them in interfaces would be ceremony with no real substitution behind it. This is the ports-and-adapters pattern: invert at the I/O boundary, keep orchestration concrete.

## Target decomposition

New subpackage `com.nobleuplift.currencies.service` for orchestration classes and the repository port; the `ConnectionProvider` interface lives in the root package next to `DatabaseManager`, which implements it (keeps the root package as the thin plugin-entry/facade/wiring layer, mirrors the existing `entities` subpackage convention):

| New type | Kind | Responsibility | Depends on |
|---|---|---|---|
| `ConnectionProvider` | interface (root package) | `Connection getConnection() throws SQLException` — the single method every service needs to open a connection for its own transaction boundary. `DatabaseManager implements ConnectionProvider` (no rename, no behavior change — just adds the interface). | nothing |
| `CurrencyRepository` | interface (`service` package) | The persistence port: signatures for all 23 `query*` methods + `upsertHolding`/`deleteHolding`, each still taking `Connection conn` as first param (transaction-boundary ownership stays with the calling service, unchanged from today). | nothing |
| `JdbcCurrencyRepository` | class, `implements CurrencyRepository` | The JDBC adapter: same method bodies as today's private `query*`/`upsertHolding`/`deleteHolding` methods, plus the 6 `map*` `ResultSet`→entity helpers as private implementation detail (not exposed on the interface — callers never need to know how rows get mapped). | nothing |
| `Clock` | static utility class | One static method, `now()` (was a private helper called from ~15 sites). | nothing |
| `Ledger` | concrete class | `compactHoldings`, `privateTransferAmount`, `insertTransaction`, public `transferAmount`. Shared by Pay/Bill/ProcessBill/Credit/Debit/Bankrupt. | `ConnectionProvider`, `CurrencyRepository` (interface) |
| `CurrencyFormatter` | concrete class | `formatCurrencies`, `formatCurrency`, `parseCurrency`, `getCurrencyFromAmount`, `resolveCurrency` — pure read-only string parsing/formatting. | `ConnectionProvider`, `CurrencyRepository` (interface) |
| `CurrencyService` | concrete class | Currency CRUD (`createCurrency` x2, `deleteCurrency`, `addPrime`, `addParent`, `addChild`, `list` x2, `validateUnitParameters`) + unit/currency lookups (`getUnit`, `getBaseUnit`, `getPrimeUnit`, `getUnits`, `getCurrency`, `getCurrencyFromAcronym`). | `ConnectionProvider`, `CurrencyRepository` (interface) |
| `AccountService` | concrete class | `openAccount`, `setDefault`, `getAccountFromPlayer`, `getAccountFromUniqueId`, private `getAccountById` helper, the 4 bank/market getters, the 4 reserved-account-id constants. | `ConnectionProvider`, `CurrencyRepository` (interface), `CurrencyService` (for `setDefault`) |
| `TransactionService` | concrete class | `balance` x2, `pay` x2, `bill` x2, `processBill` x2, `transactions` x2, `credit` x2, `debit` x2, `bankrupt` x3, `summateHoldings`. | `ConnectionProvider`, `CurrencyRepository` (interface), `Ledger`, `AccountService`, `CurrencyFormatter`, `CurrencyService` |

Rationale for the shape:
- **Only the persistence boundary is inverted.** `CurrencyRepository`/`ConnectionProvider` are interfaces because they're the actual I/O edge — the place where JDBC's concreteness (connections, `ResultSet`, SQL) would otherwise leak into every domain class. `Ledger`, `CurrencyFormatter`, `CurrencyService`, `AccountService`, `TransactionService` stay concrete classes: they're orchestration/domain logic with no second implementation ever likely, so an interface there would be indirection with nothing behind it.
- `CurrencyRepository`/`JdbcCurrencyRepository` is extracted first/lowest-risk regardless of the interface: the method set is already self-contained (query/map methods only call each other), was already private, and isn't part of the public API — nothing external can break.
- `Ledger` gets its own class rather than living inside `TransactionService` because `compactHoldings`/`privateTransferAmount`/`insertTransaction` are the shared transactional core for 5 different transaction types; keeping them separate means every transaction-producing method depends *down* on `Ledger`, never sideways on a sibling domain method.
- Bill/`processBill` stay merged into `TransactionService` rather than a separate `BillingService` — they share `TransactionType.BILL` semantics and the same balance-check/transfer shape as `pay`, and splitting them out would just add another service that needs the identical `Ledger` + `CurrencyRepository` + `AccountService` wiring for ~180 lines of code.
- `AccountService -> CurrencyService` is the only cross-service dependency edge in the whole graph (via `setDefault`), and it's one-way/acyclic.
- The 4 reserved-account constants stay declared on `CurrenciesCore` itself (not moved and re-exported), since they're `public static final int` and external plugins may already be compiled against them as inlined constants — moving them would be a binary-incompatible change even though source-compatible.
- `now()` becomes a static `Clock.now()` utility rather than living on one arbitrary service and being injected everywhere — it's a single pure function with no state, called from ~15 sites across nearly every service; forcing every other service to inject a "clock service" for one timestamp call would be over-engineering, but duplicating the one-liner across 6 files would be worse.
- **Method-to-class mapping note:** the `CurrencyRepository` interface exposes only the ~23 `query*` methods + `upsertHolding`/`deleteHolding` (the actual data-access operations). The 6 `map*` (`ResultSet` → entity) helper methods are NOT part of the interface — they're wiring detail specific to the JDBC adapter and move to `JdbcCurrencyRepository` as private methods. Everything else from the original method-to-class mapping (which methods land on `Ledger`/`CurrencyFormatter`/`CurrencyService`/`AccountService`/`TransactionService`/`Clock`) is unchanged from the earlier analysis — only `CurrencyRepository`'s shape changed (interface + adapter instead of one concrete class).

## Dependency injection & facade strategy

No DI framework — manual constructor injection, wired once in `CurrenciesCore.init()` as a composition root (unchanged call site: `Currencies.java` still calls `CurrenciesCore.init(db)` exactly once, from `onEnable()`):

```java
// service fields are declared by interface type where an abstraction exists:
private static CurrencyRepository repository;   // interface type
private static Ledger ledger;
private static CurrencyFormatter formatter;
private static CurrencyService currencyService;
private static AccountService accountService;
private static TransactionService transactionService;

public static void init(DatabaseManager databaseManager) {
    // databaseManager satisfies ConnectionProvider; passed as that type to every service
    repository = new JdbcCurrencyRepository();
    ledger = new Ledger(databaseManager, repository);
    formatter = new CurrencyFormatter(databaseManager, repository);
    currencyService = new CurrencyService(databaseManager, repository);
    accountService = new AccountService(databaseManager, repository, currencyService);
    transactionService = new TransactionService(databaseManager, repository, ledger, accountService, formatter, currencyService);
}
```

`init()`'s own signature stays `init(DatabaseManager databaseManager)` — unchanged, since it's only ever called once from `Currencies.java`'s `onEnable()`, never by external plugins. Internally, `databaseManager` (a `DatabaseManager`, which `implements ConnectionProvider`) is simply passed to constructors typed to accept `ConnectionProvider`; no cast needed, no behavior change, no external API impact.

Every one of `CurrenciesCore`'s 34 existing public static methods keeps its exact signature, `throws CurrenciesException` declaration, and behavior, and becomes a one-line delegation to the matching instance method, e.g.:

```java
public static void createCurrency(String acronym, String name) throws CurrenciesException {
    currencyService.createCurrency(acronym, name);
}
```

This guarantees `CurrenciesCommand.java` needs **zero changes**, and external plugins compiled against `CurrenciesCore`'s current static surface see no difference.

**Transaction-boundary handling — explicitly not normalized.** Write methods use explicit `setAutoCommit(false)`/commit/rollback; some read-only getters (`getUnit`, `getCurrencyFromAcronym`, `getAccountFromPlayer`, etc.) use implicit autocommit instead. This inconsistency is preserved byte-for-byte as methods move — normalizing it would be a behavior change outside this plan's "mechanical decomposition, preserve behavior exactly" scope, and overlaps with the separately-tracked soft-delete/data-integrity issue. File as its own future cleanup once each concern has a clear owning class.

## Migration sequencing (one commit per step, build green throughout, `CurrenciesCommand.java` untouched the whole way)

1. **Extract `ConnectionProvider` + `CurrencyRepository`/`JdbcCurrencyRepository`.** Add `ConnectionProvider` interface (root package), `DatabaseManager implements ConnectionProvider` (no other change to `DatabaseManager`). Add `CurrencyRepository` interface (`service` package) declaring the 23 `query*` + `upsertHolding`/`deleteHolding` signatures; move the method bodies verbatim into `JdbcCurrencyRepository implements CurrencyRepository`, and move the 6 `map*` helpers in as private methods on `JdbcCurrencyRepository` only (not on the interface). `CurrenciesCore` gets `private static final CurrencyRepository repository = new JdbcCurrencyRepository();` and all in-class call sites route through it.
2. **Extract `Clock`.** Move `now()`; update ~15 call sites to `Clock.now()`.
3. **Extract `Ledger`.** Move `compactHoldings`, `privateTransferAmount`, `insertTransaction`; keep `transferAmount` as a facade delegation. Constructor takes `(ConnectionProvider, CurrencyRepository)` — both abstractions, not the concrete `DatabaseManager`/`JdbcCurrencyRepository`. Still-monolithic method bodies in `CurrenciesCore` (pay/bill/processBill/credit/debit/bankrupt haven't moved yet) call `ledger.compactHoldings(conn, account)` etc. directly.
4. **Extract `CurrencyFormatter`.** Move the 5 parsing/formatting methods; facade delegates; remaining monolithic bodies switch internal calls to `formatter.formatCurrency(...)`.
5. **Extract `CurrencyService`.** Move currency CRUD + unit lookups + `validateUnitParameters`; facade delegates for those 12 methods.
6. **Extract `AccountService`.** Move `openAccount`, `setDefault`, account/bank lookups; facade delegates.
7. **Extract `TransactionService`.** The big one — move `balance`/`pay`/`bill`/`processBill`/`transactions`/`credit`/`debit`/`bankrupt`/`summateHoldings`. After this commit, `CurrenciesCore` contains only constants, `init()`, service fields, and 34 one-line delegating methods.
8. **Cleanup.** Remove the now-dead `private static DatabaseManager db` field from `CurrenciesCore` if nothing references it directly anymore.

After each step: `mvn clean package` must stay green, and `CurrenciesCommand.java` should show zero diff across the entire sequence (verify with `git diff` on that file after each commit).

## Follow-on: unit tests (separate commit sequence, strictly after decomposition lands)

1. **pom.xml commit:** add `org.junit.jupiter:junit-jupiter` and `org.mockito:mockito-core` + `org.mockito:mockito-junit-jupiter` (test scope); remove the unused `junit:junit:3.8.1` dependency (the existing `CurrenciesTest.java` stub has no real logic to migrate); confirm/bump `maven-surefire-plugin` for JUnit 5 platform support.
2. **Test double strategy — use the interfaces the decomposition already introduced.** Because `CurrencyRepository` is now a port, most tests need no JDBC mocking at all: write a small hand-rolled in-memory `FakeCurrencyRepository implements CurrencyRepository` (backed by `Map`s of entities) once, shared across test classes, and use it wherever a test needs unit/currency/holding lookups — it just ignores the `Connection conn` parameter since it never touches JDBC. For the outermost transaction-boundary calls (where a service does `connectionProvider.getConnection()`), stub `ConnectionProvider` with Mockito to return a bare-bones mock `Connection` (only needs no-op `setAutoCommit`/`commit`/`rollback`/`close`) — the fake repository handles everything past that point. `JdbcCurrencyRepository` itself is the adapter and isn't a unit-test target — it's SQL plumbing best covered by an integration test against a real/staging database later, out of scope here.
3. **Test priority order** (cheapest/highest-value first):
   - `summateHoldings` (on `TransactionService`) — pure function over in-memory `Holding`/`Unit`/`Currency` POJOs, zero mocking or fakes needed at all.
   - `CurrencyFormatter.formatCurrency` / `parseCurrency` — needs only the `FakeCurrencyRepository` for unit lookups; high edge-case value (negative amounts, multi-denomination strings, prefix vs. suffix).
   - `Ledger.compactHoldings` — the algorithmically interesting, highest-blast-radius piece (shared by 5 transaction types); drive it entirely off `FakeCurrencyRepository` seeded with canned `Holding` lists.
   - `CurrencyService` validation logic (`validateUnitParameters`, symbol/format checks) — also purely `FakeCurrencyRepository`-driven.
   - Lowest priority: `TransactionService` orchestration methods (`pay`/`bill`/`processBill`/`bankrupt`) — cover key branches (insufficient balance, self-pay, reserved-account rejection) using the fake repository + mocked `ConnectionProvider`/`Connection`, rather than exhaustive coverage, since the interesting logic already lives in `Ledger`/`CurrencyFormatter`.

No test code or production code is written in this planning pass — implementation begins after this plan is approved.

## Verification

- After each decomposition commit: `mvn clean package` (build must stay green) and `git diff -- src/main/java/com/nobleuplift/currencies/CurrenciesCommand.java` must be empty for the whole sequence.
- After the full decomposition: manually diff `CurrenciesCore.java` before/after to confirm every one of the 34 public methods is a pure delegation with no leftover logic, and re-read the class javadoc to confirm it's still accurate.
- Smoke-test in-game (or via existing manual test flow) after decomposition: `/currencies create`, `/pay`, `/bill` + `/paybill`, `/bankrupt` — confirm compacting and the two-phase bill workflow still behave identically, since these are the highest-risk paths (shared `Ledger` code, bill's inline-INSERT asymmetry with `insertTransaction`).
- After the test-infra commits: `mvn test` runs and passes the new JUnit 5 suite.
