# Plan: Next Round of Technical Debt Paydown

## Context

`ASSESSMENT.md` tracks known design/architecture weaknesses in the Currencies plugin. The Paper 1.21 / JDBC rewrite already resolved several items (ORM anti-patterns, optimistic locking, layering, DB-init rollback). Nine items remain open or partially open. This plan prioritizes the remaining open items into independently reviewable branches, sequenced so smaller/safer changes land first and the riskiest items (schema migration, large-scale decomposition) come last or are explicitly deferred.

Two research passes over the current code (`CurrenciesCore.java`, `CurrenciesCommand.java`, entities, `CurrencyDTO.java`, exception classes) confirmed exact line ranges and scope for each item — referenced below. Per user decision: **one branch per logical group**, and the `CurrenciesCore` god-object decomposition (#1, ~2400 lines) is **deferred** as a separate future initiative — not part of this round, since it benefits from the smaller fixes landing first.

---

## Recommended order & branches

### 1. Branch: `command-housekeeping`
**Severity: Low · Risk: trivial · Do first (fast, unblocks nothing but builds momentum)**

Three independent, non-overlapping fixes bundled because each is tiny and none touch schema:

- **Pagination display bug** (`CurrenciesCommand.java` `list` ~line 161, `transactions` ~line 344): the "N through M" label uses the raw parsed `page` directly, while `CurrenciesCore.list()`/`transactions()` normalize `page <= 1` to offset 0 (`CurrenciesCore.java:435-437`, `889-890`). Fix: apply the same `(page <= 1) ? 1 : page` normalization before computing the display range, and cap the upper bound at the actual number of rows returned rather than always `+9`.
- **Remove unused `CurrencyDTO.java`**: confirmed zero references anywhere in `src/` (not even tests). Delete the file per CLAUDE.md guidance on removing confirmed-dead code rather than "completing" an unused abstraction.
- **Extract a `now()` timestamp helper**: `new Timestamp(Calendar.getInstance().getTimeInMillis())` is repeated 11 times in `CurrenciesCore.java` (lines 110, 144, 188, 280, 370, 487, 564, 731, 855, 1629, 1683). Replace with a single private static helper.

### 2. Branch: `unit-validation-dedup`
**Severity: High · Risk: low (well-scoped, no schema change)**

`addParent()` (`CurrenciesCore.java:215-308`) and `addChild()` (`CurrenciesCore.java:310-429`) duplicate ~6 validation steps verbatim: currency lookup, prime-exists check, singular-name uniqueness, plural-name uniqueness, symbol uniqueness per currency, symbol uniqueness as a global prime, symbol length. Only the final symbol-format regex differs (addChild additionally forbids `-`).

Fix: extract a shared `validateUnitParameters(Currency currency, String name, String plural, String symbol)`-style private method returning/throwing on the shared checks; keep the divergent parent/child-specific logic (multiplier vs. divisor, lines ~260-295 vs ~355-416) in each method. Pass the symbol-format regex as a parameter or do it as a small post-check in each caller since it differs.

### 3. Branch: `magic-number-cleanup`
**Severity: High · Risk: low-medium (touches many call sites, but mechanical)**

Two related sub-fixes from the same assessment item, bundled because they touch the same methods (`pay`, `bill`, `credit`, `debit`, `bankrupt`) and bundling avoids repeated merge conflicts:

- **Reserved account checks**: `id >= 1 && id <= 4` is repeated as raw literals in 6 places (`CurrenciesCore.java:648, 651, 713, 716, 954, 993`) despite named constants `MINECRAFT_CENTRAL_BANK`/`MINECRAFT_CENTRAL_BANKER`/`THE_ENDERMAN_MARKET`/`THE_ENDERMAN_MARKETEER` already existing (lines 56-59). Add a `boolean isReserved()` method on the `Account` entity (or a static helper using the existing constants) and replace all 6 checks.
- **Transaction type enum**: `TRANSACTION_TYPE_*_ID` are `short` constants (`CurrenciesCore.java:61-65`) used at 10+ call sites (679, 744, 764, 824, 967, 1006, 1063/1068/1082/1097, 1493, 2167) plus display logic in `CurrenciesCommand.java`. Replace with an enum (e.g. `TransactionType`) carrying the numeric ID and any display label; update `Transaction.typeId` usage and comparisons accordingly. Keep the underlying DB column as a `short`/`int` — map at the boundary.

### 4. Branch: `entity-boolean-cleanup`
**Severity: Medium · Risk: trivial**

Dual `isX()`/`getX()` getters on boolean fields: `Currency.deleted/prefix/globalDefault`, `Unit.main/prime`, `Transaction.paid`. Research shows `getDeleted()`, `getMain()`, `getGlobalDefault()`, `getPrime()` are **already dead** (only the `isX()` form is called anywhere). `getPrefix()` and `getPaid()` are actually used, inconsistently alongside their `isX()` twins in the same file (e.g. `CurrenciesCore.java:1337,1344` use `getPrefix()` while `:1353` uses `isPrefix()`; similar for `getPaid()` at `CurrenciesCommand.java:354,363` and `CurrenciesCore.java:827,828,1671,1672`).

Fix: normalize all call sites to the `isX()` form, then delete every `getX()` method for these six boolean fields.

### 5. Branch: `exception-cause-chaining`
**Severity: Medium · Risk: low**

`CurrenciesException` and `CurrenciesRuntimeException` each have only a `(String)` constructor — no cause chaining, so every catch-and-rethrow in `CurrenciesCore.java` discards the original `SQLException`/etc. stack trace. All 28 catch sites in `CurrenciesCommand.java` already catch both types together via multi-catch and handle them identically (`Currencies.tell(sender, e.getMessage())`), confirming no behavioral distinction currently exists between the two types.

Fix: add a `(String message, Throwable cause)` constructor to both exception classes; update `CurrenciesCore.java` throw sites that wrap a caught exception to pass the cause through. Given the confirmed identical handling, also collapse to a single exception type unless a concrete reason to keep both surfaces — recommend keeping `CurrenciesException` (checked) only and removing `CurrenciesRuntimeException`, since the checked/unchecked distinction is doing no work today; confirm with the user before removing the public unchecked type since it's part of the plugin's external API surface.

### 6. Branch: `soft-delete-consolidation`
**Severity: Medium · Risk: higher — schema migration required — do last**

`Currency` has both `deleted` (boolean, the actual filter used everywhere — e.g. `WHERE deleted = 0` at line 445, plus ~13 other SELECTs aliasing `deleted`/`c_deleted`/`dc_deleted`) and `dateDeleted` (timestamp, populated only in `deleteCurrency()` at line 146, read back only in one row-mapper at line 2262, never used in a `WHERE` clause or business logic). No invariant enforces they stay in sync.

Fix: treat `dateDeleted IS NOT NULL` as the sole soft-delete signal; drop the `deleted` column. This touches: the DDL in `Currencies.java:82-83`, a version-gated migration block per CLAUDE.md's migration convention (bump `version` in `config.yml`, add a new `if` block in `onEnable()` to migrate existing `deleted=1` rows and drop the column), the `deleteCurrency` UPDATE (`CurrenciesCore.java:146`), ~15 SELECT statements' `WHERE`/alias clauses, and the `Currency` entity (remove field + `isDeleted`/`getDeleted`/`setDeleted`, replace with a derived `isDeleted()` computed from `dateDeleted != null`).

Sequenced last among the near-term branches because it's the only one requiring a live-data migration path; land it after the mechanical cleanups so there's less code to touch and re-verify.

---

## Deferred (not in this round)

**`core-service-decomposition`** — Decomposing `CurrenciesCore`'s ~2400 lines (currency/unit CRUD ~390 lines, account ~117, five transaction types ~420 combined, compacting/transfer ~184, ~529 lines of private query helpers, ~198 lines of mappers) into domain services (`CurrencyService`, `AccountService`, `TransactionService`, etc.) behind the existing static facade. Left out of this round per user decision — it's the largest and riskiest item, and doing branches 2-5 first (validation dedup, magic-number cleanup, boolean cleanup, exception chaining) reduces the surface area that needs to move during decomposition. Revisit as its own initiative once the above land.

---

## Verification

For each branch:
- `mvn clean package` must succeed (Java 21 / Paper 1.21 target per CLAUDE.md).
- `mvn test` — run existing test suite (`CurrenciesTest.java`); no regressions.
- For `unit-validation-dedup` and `magic-number-cleanup`: manually exercise `/currencies addparent`, `/currencies addchild`, `/currencies pay`, `/currencies bill`, `/currencies credit`, `/currencies debit` against reserved and non-reserved accounts to confirm identical behavior pre/post-refactor.
- For `command-housekeeping`: exercise `/currencies list 0`, `/currencies list -1`, and a page with fewer than 10 remaining items to confirm the displayed range now matches reality.
- For `soft-delete-consolidation`: test the migration path against a database with existing `deleted=1` rows from a prior version to confirm data isn't lost, and confirm `/currencies list` / `/currencies delete` still correctly exclude/soft-delete currencies post-migration.
