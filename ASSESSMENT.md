# Assessment: Programmatic Strengths & Weaknesses

This is an assessment of the Currencies Minecraft plugin's design and patterns, covering all source files: `Currencies.java`, `CurrenciesCore.java`, `CurrenciesCommand.java`, `CurrencyDTO.java`, exception classes, and the entity layer.

---

## Strengths

### 1. Integer-Only Monetary Arithmetic
The entire system stores all amounts as `long` values in the smallest base unit of each currency. No floating-point arithmetic is used anywhere. This is the single most important design decision in the plugin and it is correctly implemented throughout. Monetary precision problems that plague simpler economy plugins are avoided by design.

### 2. Compacting Algorithm
Before every transaction, `CurrenciesCore.compactHoldings()` consolidates a player's multi-denomination holdings into base units, processes the transaction, then redistributes. This correctly handles the edge case where a player has enough *total* wealth but it's split across denominations. The concept is sound and domain-appropriate.

### 3. Transactional Boundaries on Entry Points
Public methods in `CurrenciesCore` that mutate state use explicit JDBC transactions (`conn.setAutoCommit(false)` / `commit()` / `rollback()`). This ensures atomicity at the right level. *(Previously `@Transactional` Ebean annotations; migrated to JDBC in the Paper 1.21 upgrade.)*

### 4. Central Permission Gate
`CurrenciesCommand.subcommands()` checks `sender.hasPermission("currencies." + args[0])` in a single place before entering the switch. Additional per-operation checks (e.g., `currencies.bankrupt.all`) are applied inline. The pattern is consistent.

### 5. Bill State Machine
The two-phase bill workflow (pending → accepted/rejected) is implemented correctly. `bill()` creates a Transaction with `paid = null`; `processBill()` sets it to `true` or `false` and transfers funds only on acceptance. The states are distinct and enforced.

### 6. Composite Key Encapsulation
`HolderPK` and `HoldingPK` properly encapsulate composite primary keys as `Serializable` classes with correct `equals()` and `hashCode()` overrides. This is easy to get wrong and is handled correctly.

### ~~7. Bidirectional Relationship Helpers on Entities~~
~~`Currency.addUnit()` / `removeUnit()` and equivalent methods maintain both sides of JPA bidirectional relationships, reducing the risk of ORM sync bugs.~~
*(Removed — entities are now plain POJOs with no ORM relationships; this pattern no longer applies.)*

### 8. API Documentation in CurrenciesCore
The class-level Javadoc in `CurrenciesCore` documents the currency/unit invariants (one child per unit, symbol uniqueness scoping, reserved account semantics). This is critical information for external plugin authors that would otherwise require reading the database schema.

### 9. UUID + Name Fallback for Player Rename
The player join handler checks UUID first, then falls back to name, and suffixes old name accounts with the account ID on rename. This correctly handles a Minecraft-specific operational concern.

---

## Weaknesses

### 1. CurrenciesCore Is a Static God Object
**Severity: High**

`CurrenciesCore` is ~2400 lines of all-static methods handling currency CRUD, unit management, account management, five transaction types, bill workflow, compacting, parsing, and formatting. It has no instance state and cannot be subclassed, mocked, or extended.

- Zero testability: all database access is now through an injected `DatabaseManager` (resolved in Paper 1.21 upgrade), but the class itself remains a static facade with no decomposition.
- Violates Single Responsibility Principle across every domain concern.

**Partial fix applied:** `CurrenciesCore.init(db)` injects a `DatabaseManager` at startup, eliminating the `Currencies.getInstance().getDatabase()` call in every method. The public static API is preserved for external plugins.

**Remaining fix:** Decompose into domain-specific classes (CurrencyService, AccountService, TransactionService, etc.) behind the static facade.

---

### 2. Massive Code Duplication in addParent / addChild
**Severity: High**

`addParent()` and `addChild()` share approximately 50 lines of identical validation logic: singular name uniqueness, plural name uniqueness, symbol uniqueness per currency, symbol uniqueness globally for prime units, symbol format validation. This code is copy-pasted verbatim.

Any bug fix or rule change in one must be manually replicated in the other.

**Fix:** Extract a shared `validateUnitParameters(acronym, name, plural, symbol)` method.

---

### 3. Magic Numbers for Reserved Accounts and Transaction Types
**Severity: High**

Hard-coded checks for `account.getId() >= 1 && account.getId() <= 4` appear in at least six methods (`pay`, `bill`, `credit`, `debit`, `bankrupt`, etc.). If a reserved account is added, every method needs a manual update.

Transaction type IDs (`TRANSACTION_TYPE_PAY_ID`, etc.) are `short` constants compared directly in `CurrenciesCommand`. They should be an enum with display logic encapsulated on the type itself.

**Fix:** Replace the reserved-account range check with a `boolean isReserved()` method on `Account`. Replace transaction type short constants with an enum.

---

### ~~4. ORM Anti-Patterns in Entity toString()~~
~~**Severity: High**~~

~~`Currency.toString()`, `Account.toString()`, and `Unit.toString()` include lazy-loaded collection fields. Calling `toString()` outside a transaction triggers lazy initialization failures or unintended N+1 queries. `Unit` has a self-referential `childUnit` relationship that causes `StackOverflowError` if cycles form.~~

**Fixed in Paper 1.21 upgrade:** Entities are now plain POJOs with no ORM annotations or lazy-loaded collections. All collection fields removed from `toString()` implementations.

---

### 5. Dual Getter Naming on Boolean Fields
**Severity: Medium**

`Currency` exposes both `isDeleted()` and `getDeleted()` returning the same value. `Unit` has the same pattern for `prime`. This breaks reflection-based frameworks and violates JavaBeans conventions.

**Fix:** Remove the `getX()` form for boolean fields; keep only `isX()`.

---

### 6. Exception Architecture Is Unclear
**Severity: Medium**

`CurrenciesException` (checked) and `CurrenciesRuntimeException` (unchecked) are caught together in every `catch` block in `CurrenciesCommand` and handled identically. The checked/unchecked distinction does no work. Neither exception class accepts a `Throwable cause`, so database exception stack traces are always lost.

**Fix:** Add a `(String message, Throwable cause)` constructor to both types. Reconsider whether two exception types are needed or whether a single exception with an error-code enum suffices.

---

### 7. Inconsistent Soft-Delete Pattern
**Severity: Medium**

`Currency` has both a `deleted` boolean field and a `dateDeleted` timestamp with no enforced invariant that `deleted == true IFF dateDeleted != null`.

**Fix:** Remove the `deleted` boolean; treat `dateDeleted IS NOT NULL` as the authoritative soft-delete signal.

---

### ~~8. No Optimistic Locking~~
~~**Severity: Medium**~~

~~No entity has a `@Version` field. Concurrent transactions modifying the same `Holding` will silently overwrite each other's changes.~~

**Resolved by design in Paper 1.21 upgrade:** JDBC transactions use MySQL's default `REPEATABLE READ` isolation, which prevents concurrent overwrites without requiring application-level version columns. `@Version` was only needed because Ebean could silently clobber concurrent updates outside of explicit transactions.

---

### ~~9. Layering Violation: Command Layer Uses JPA Entities Directly~~
~~**Severity: Medium**~~

~~`CurrenciesCommand` accesses entity fields directly. The command layer is tightly coupled to the persistence model. `CurrencyDTO` exists but is never used in the command layer.~~

**Resolved by design in Paper 1.21 upgrade:** Entities are now plain POJOs (not live JPA proxies), so accessing their fields from the command layer carries no ORM risk. The coupling is still architecturally impure but no longer causes runtime hazards.

---

### ~~10. Database Schema Initialization in onEnable with No Rollback~~
~~**Severity: Medium**~~

~~`Currencies.onEnable()` executes raw SQL DDL and DML with no transaction wrapping. A mid-sequence failure leaves the database partially initialized with no recovery path.~~

**Fixed in Paper 1.21 upgrade:** `initSchema()` and `migrateFromV100()` are wrapped in try-with-resources JDBC connections. Failures are caught, logged at SEVERE, and the plugin is disabled cleanly via `getServer().getPluginManager().disablePlugin(this)`.

---

### 11. Repeated Timestamp Creation
**Severity: Low**

`new Timestamp(Calendar.getInstance().getTimeInMillis())` appears multiple times. Most instances were eliminated by the Paper 1.21 JDBC rewrite (SQL `NOW()` is used instead), but any remaining occurrences should use a single static utility method.

---

### 12. Pagination Displays Wrong Count on Last Page
**Severity: Low**

Both `list` and `transactions` display "N through M" where M is always `((page-1)*10)+10`, even when the actual list has fewer than 10 items. This tells the user there are items that don't exist.

---

### 13. CurrencyDTO Is Unused at the Command Layer
**Severity: Low**

`CurrencyDTO` was likely introduced to decouple the command layer from entity objects, but `CurrenciesCommand` never instantiates or receives one. Its purpose should be clarified and either completed or removed.

---

## Summary Table

| Issue | Category | Severity | Status |
|---|---|---|---|
| CurrenciesCore static god object | Architecture | High | Partial (DB injection fixed) |
| addParent/addChild validation duplication | DRY | High | Open |
| Magic numbers (account IDs, transaction type shorts) | Design | High | Open |
| ~~ORM lazy-load in toString()~~ | ~~ORM anti-pattern~~ | ~~High~~ | **Fixed** |
| Dual boolean getter naming | Convention | Medium | Open |
| Exception architecture unclear, no cause chaining | Error handling | Medium | Open |
| Inconsistent soft-delete | Data integrity | Medium | Open |
| ~~No optimistic locking~~ | ~~Concurrency~~ | ~~Medium~~ | **Resolved by design** |
| ~~Command layer uses JPA entities directly~~ | ~~Layering~~ | ~~Medium~~ | **Resolved by design** |
| ~~DB init has no rollback / dead migration code~~ | ~~Reliability~~ | ~~Medium~~ | **Fixed** |
| Repeated timestamp creation | DRY | Low | Mostly fixed |
| Pagination displays wrong item count on last page | UX/correctness | Low | Open |
| CurrencyDTO unused | Incomplete design | Low | Open |
