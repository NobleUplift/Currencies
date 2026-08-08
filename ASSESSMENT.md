# Assessment: Programmatic Strengths & Weaknesses

This is an assessment of the Currencies Minecraft plugin's design and patterns, covering all source files: `Currencies.java`, `CurrenciesCore.java`, `CurrenciesCommand.java`, `CurrencyDTO.java`, exception classes, and the entity layer.

---

## Strengths

### 1. Integer-Only Monetary Arithmetic
The entire system stores all amounts as `long` values in the smallest base unit of each currency. No floating-point arithmetic is used anywhere. This is the single most important design decision in the plugin and it is correctly implemented throughout. Monetary precision problems that plague simpler economy plugins are avoided by design.

### 2. Compacting Algorithm
Before every transaction, `CurrenciesCore.compactHoldings()` consolidates a player's multi-denomination holdings into base units, processes the transaction, then redistributes. This correctly handles the edge case where a player has enough *total* wealth but it's split across denominations. The concept is sound and domain-appropriate.

### 3. Transactional Boundaries on Entry Points
Public methods in `CurrenciesCore` that mutate state carry `@Transactional` annotations (`createCurrency`, `deleteCurrency`, `addPrime`, `addParent`, `addChild`, `pay`, `bill`, etc.). This ensures atomicity at the right level.

### 4. Central Permission Gate
`CurrenciesCommand.subcommands()` checks `sender.hasPermission("currencies." + args[0])` in a single place before entering the switch (line 58). Additional per-operation checks (e.g., `currencies.bankrupt.all`) are applied inline. The pattern is consistent.

### 5. Bill State Machine
The two-phase bill workflow (pending → accepted/rejected) is implemented correctly. `bill()` creates a Transaction with `paid = null`; `processBill()` sets it to `true` or `false` and transfers funds only on acceptance. The states are distinct and enforced.

### 6. Composite Key Encapsulation
`HolderPK` and `HoldingPK` properly encapsulate composite primary keys as `Serializable` classes with correct `equals()` and `hashCode()` overrides. This is a JPA requirement that is easy to get wrong and is handled correctly.

### 7. Bidirectional Relationship Helpers on Entities
`Currency.addUnit()` / `removeUnit()` and equivalent methods maintain both sides of JPA bidirectional relationships, reducing the risk of ORM sync bugs.

### 8. API Documentation in CurrenciesCore
The class-level Javadoc in `CurrenciesCore` documents the currency/unit invariants (one child per unit, symbol uniqueness scoping, reserved account semantics). This is critical information for external plugin authors that would otherwise require reading the database schema.

### 9. UUID + Name Fallback for Player Rename
The player join handler checks UUID first, then falls back to name, and suffixes old name accounts with the account ID on rename. This correctly handles a Minecraft-specific operational concern.

---

## Weaknesses

### 1. CurrenciesCore Is a Static God Object
**Severity: High**

`CurrenciesCore` is ~1350 lines of all-static methods handling currency CRUD, unit management, account management, five transaction types, bill workflow, compacting, parsing, and formatting. It has no instance state and cannot be subclassed, mocked, or extended.

- Zero testability: every method calls `Currencies.getInstance().getDatabase()`, tying unit logic to the live Bukkit plugin singleton.
- Impossible to inject a test double for the database.
- Violates Single Responsibility Principle across every domain concern.

**Fix:** Keep the static public API as a facade; internally delegate to a held instance with the database injected at initialization (`CurrenciesCore.init(db)` called from `onEnable`). No call sites in `CurrenciesCommand` need to change.

---

### 2. Massive Code Duplication in addParent / addChild
**Severity: High**

`addParent()` (lines 144–236) and `addChild()` (lines 238–363) share approximately 50 lines of identical validation logic: singular name uniqueness, plural name uniqueness, symbol uniqueness per currency, symbol uniqueness globally for prime units, symbol format validation. This code is copy-pasted verbatim.

Any bug fix or rule change in one must be manually replicated in the other.

**Fix:** Extract a shared `validateUnitParameters(acronym, name, plural, symbol)` method.

---

### 3. Magic Numbers for Reserved Accounts and Transaction Types
**Severity: High**

Hard-coded checks for `account.getId() >= 1 && account.getId() <= 4` appear in at least six methods (`pay`, `bill`, `credit`, `debit`, `bankrupt`, etc.). If a reserved account is added, every method needs a manual update.

Transaction type IDs (`TRANSACTION_TYPE_PAY_ID`, etc.) are `short` constants compared directly in `CurrenciesCommand` (lines 346–361). They should be an enum with display logic encapsulated on the type itself.

**Fix:** Replace the reserved-account range check with a `boolean isReserved()` method on `Account`. Replace transaction type short constants with an enum.

---

### 4. ORM Anti-Patterns in Entity toString()
**Severity: High**

`Currency.toString()`, `Account.toString()`, and `Unit.toString()` include lazy-loaded collection fields (e.g., `accountDefaults`, `units`, `parentAccounts`, `childAccounts`, `holdings`). Calling `toString()` outside a transaction triggers lazy initialization failures or unintended N+1 database queries.

`Unit` has a self-referential `childUnit` relationship. If cycles form, `toString()` causes a `StackOverflowError`.

**Fix:** Remove all collection fields from `toString()` implementations.

---

### 5. Dual Getter Naming on Boolean Fields
**Severity: Medium**

`Currency` exposes both `isDeleted()` and `getDeleted()` returning the same value (lines 99–105). `Unit` has the same pattern for `prime`. This breaks reflection-based frameworks (Jackson, ORM mappers) that expect a single canonical getter and violates JavaBeans conventions.

**Fix:** Remove the `getX()` form for boolean fields; keep only `isX()`.

---

### 6. Exception Architecture Is Unclear
**Severity: Medium**

`CurrenciesException` (checked) and `CurrenciesRuntimeException` (unchecked) are caught together in every single `catch` block in `CurrenciesCommand`:

```java
} catch (CurrenciesException | CurrenciesRuntimeException e) {
    Currencies.tell(sender, e.getMessage());
}
```

If both are always caught together and handled identically, the checked/unchecked distinction is doing no work. Additionally:
- `CurrenciesException` has a `protected` constructor, preventing external plugins from throwing it.
- Neither exception class accepts a `Throwable cause`, so database exception stack traces are always lost.

**Fix:** Make `CurrenciesException` public. Add a `(String message, Throwable cause)` constructor to both types. Reconsider whether two exception types are needed or whether a single exception with an error-code enum suffices.

---

### 7. Inconsistent Soft-Delete Pattern
**Severity: Medium**

`Currency` has both a `deleted` boolean field and a `dateDeleted` timestamp. There is no enforced invariant that `deleted == true IFF dateDeleted != null`. Queries may or may not filter by either field, creating a consistency gap.

**Fix:** Remove the `deleted` boolean and treat `dateDeleted IS NOT NULL` as the authoritative soft-delete signal. Add a derived `isDeleted()` method that returns `dateDeleted != null`.

---

### 8. No Optimistic Locking
**Severity: Medium**

No entity has a `@Version` field. Concurrent transactions modifying the same `Currency`, `Account`, or `Holding` will silently overwrite each other's changes. Given that Minecraft servers can have concurrent player activity, this is a realistic risk during peak play.

**Fix:** Add `@Version private long version;` to `Account` and `Holding` (the entities modified most frequently during normal play).

---

### 9. Layering Violation: Command Layer Uses JPA Entities Directly
**Severity: Medium**

`CurrenciesCommand` accesses `.getAcronym()`, `.getName()`, `.getSymbol()`, `.getAlternate()`, `.getChildUnit()`, `.getChildMultiples()` directly on entity objects in the display loop (lines 163–168). The command layer is tightly coupled to the persistence model. `CurrencyDTO` exists in the codebase but is never used in the command layer.

**Fix:** Have `CurrenciesCore` return DTOs (or simple value records) to the command layer, not live entity objects.

---

### 10. Database Schema Initialization in onEnable with No Rollback
**Severity: Medium**

`Currencies.onEnable()` executes raw SQL DDL and DML across 50+ lines with no transaction wrapping and no rollback strategy. If any statement fails mid-sequence (e.g., a table is created but the reserved account insert fails), the plugin leaves the database in a partially initialized state with no recovery path. Return values of `.execute()` calls are not checked.

Additionally, a version migration branch (lines 101–117) can never execute because the version string was already updated to `"1.1.0"` at line 95, making the `"1.0.0".equals(version)` condition on line 101 always false — dead code.

---

### 11. Repeated Timestamp Creation
**Severity: Low**

`new Timestamp(Calendar.getInstance().getTimeInMillis())` appears six or more times across `Currencies.java` and `CurrenciesCore.java`. This should be a single static utility method.

---

### 12. Pagination Displays Wrong Count on Last Page
**Severity: Low**

Both `list` and `transactions` display "N through M" where M is always `((page-1)*10)+10`, even when the actual list returned has fewer than 10 items (i.e., the last page). This tells the user there are items that don't exist.

---

### 13. CurrencyDTO Is Unused at the Command Layer
**Severity: Low**

`CurrencyDTO` was likely introduced to decouple the command layer from entity objects, but `CurrenciesCommand` never instantiates or receives one. The DTO is either incomplete work or intended for the external plugin API. Its purpose should be clarified and either completed or removed.

---

## Summary Table

| Issue | Category | Severity |
|---|---|---|
| CurrenciesCore static god object | Architecture | High |
| addParent/addChild validation duplication | DRY | High |
| Magic numbers (account IDs, transaction type shorts) | Design | High |
| ORM lazy-load in toString() | ORM anti-pattern | High |
| Dual boolean getter naming | Convention | Medium |
| Exception architecture unclear, no cause chaining | Error handling | Medium |
| Inconsistent soft-delete | Data integrity | Medium |
| No optimistic locking | Concurrency | Medium |
| Command layer uses JPA entities directly | Layering | Medium |
| DB init has no rollback / dead migration code | Reliability | Medium |
| Repeated timestamp creation | DRY | Low |
| Pagination displays wrong item count on last page | UX/correctness | Low |
| CurrencyDTO unused | Incomplete design | Low |
