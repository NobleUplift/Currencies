# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn clean package          # Build — produces target/Currencies-1.21.0.jar
mvn clean package -DskipTests  # Build without tests
mvn test                   # Run tests
```

HikariCP and `mysql-connector-j` are shaded into the output JAR (relocated to `com.nobleuplift.currencies.libs.*`) via `maven-shade-plugin`. Java 21 source/target. Targets Paper API 1.21.

## Versioning

`<major>.<MC_MINOR>.<patch>` — e.g. `1.21.0` targets Paper 1.21. Bump the patch digit for Currencies-only releases; bump the minor segment when retargeting a new Paper minor version.

## Architecture Overview

Paper/Spigot Minecraft plugin implementing a multi-currency economic system. Three main source files divide responsibility cleanly:

- **`Currencies.java`** — Plugin entry point (`JavaPlugin` + `Listener`). Manages lifecycle: initializes `DatabaseManager`, runs schema DDL / migrations on first run, injects the DB into `CurrenciesCore`, registers events, handles `PlayerJoinEvent` (auto-creates accounts), and routes all commands to `CurrenciesCommand`.
- **`CurrenciesCore.java`** — Static-method API (~2400 lines). All business logic: currency/unit CRUD, transaction processing, the compacting algorithm, bill workflows, and currency string parsing. This is also the public API for other plugins. Receives a `DatabaseManager` via `CurrenciesCore.init(db)` at startup.
- **`CurrenciesCommand.java`** — Command handler. Validates permissions, parses arguments, delegates to `CurrenciesCore`. All 17 commands route through a single switch statement.
- **`DatabaseManager.java`** — Thin wrapper around a HikariCP `HikariDataSource`. Reads connection config from `config.yml`. Call `getConnection()` to obtain a `java.sql.Connection`; always use try-with-resources.

### Data Model

```
Currency (1) ──→ (N) Unit
  └── Units form a hierarchy: each Unit has an optional child_unit_id

Account (1) ──→ (N) Holding ──→ (1) Unit
  └── Amount is always a long (base units, never decimal)

Account ──M:M──→ Account (via Holder)
  └── Parent-child ownership chain for business accounts

Account (1) ──→ (N) Transaction
  └── TypeId: PAY(1), BILL(2), CREDIT(3), DEBIT(4), BANKRUPT(5)
```

Entities in `com.nobleuplift.currencies.entities` are **plain POJOs** — no JPA/ORM annotations. `HoldingPK` and `HolderPK` are composite-key structs (account_id + unit_id / parent_account_id + child_account_id) still used as field types on `Holding` and `Holder`.

### Integer-Only Arithmetic

All monetary amounts are stored as `long` in the smallest base unit — never floating-point. The **compacting algorithm** in `CurrenciesCore` consolidates holdings across all denominations into base units before any transaction, then redistributes into named denominations. This is the central invariant; any change to transaction logic must preserve it.

### JDBC Conventions

- Every method that touches the DB opens its own connection via `try (Connection conn = db.getConnection())`.
- Methods with `@Transactional` semantics call `conn.setAutoCommit(false)` and explicitly `commit()` / `rollback()`.
- Private `mapX(ResultSet rs)` helpers in `CurrenciesCore` construct entity objects from result rows.
- JOIN queries eagerly load related objects (unit → currency on Holding; sender/recipient accounts on Transaction).

### Reserved System Accounts (IDs 1–4)

Created at first startup and never deleted:
1. **Central Bank** — receives funds from bankruptcy
2. **Central Banker** — tracks total currency in circulation
3. **Enderman Market** / **Enderman Marketeer** — reserved for a future feature, currently unused

### Schema & Migrations

Schema DDL lives in `Currencies.initSchema()` (six `CREATE TABLE IF NOT EXISTS` statements). Version migrations are gated on the `version` key in `config.yml` (`new` → `1.0.0` → `1.1.0`). Add new migrations as additional `if` blocks in `onEnable()`.

### Bill Workflow

`BILL` transactions are two-phase: created as pending (`paid = 0`), then either accepted (holdings transferred, `paid = 1`) or rejected. Both states tracked in `currencies_transaction`.

### Currency Parsing

`CurrenciesCore` parses mixed-denomination strings (e.g. `200L20hc17g` = 200 Pounds + 20 Half-Crowns + 17 Groats). Unit symbols are stored on `Unit` and used to split input at parse time.

## Database

MySQL 5.7+. Six tables: `currencies_currency`, `currencies_unit`, `currencies_account`, `currencies_holding`, `currencies_holder`, `currencies_transaction`. Connection configured in `config.yml`:

```yaml
database:
  host: localhost
  port: 3306
  name: minecraft
  username: root
  password: ""
```

## Minecraft Commands

All commands are under `/currencies` (alias `/cur`). Permissions follow `currencies.<subcommand>`.

### Currency & Unit Management (admin)

| Command | Permission |
|---|---|
| `/currencies create <acronym> <name> [prefix]` | `currencies.create` |
| `/currencies delete <acronym>` | `currencies.delete` |
| `/currencies addprime <acronym> <name> <plural> <symbol>` | `currencies.addprime` |
| `/currencies addparent <acronym> <name> <plural> <symbol> <multiplier> <child>` | `currencies.addparent` |
| `/currencies addchild <acronym> <name> <plural> <symbol> <divisor> <parent>` | `currencies.addchild` |
| `/currencies list [page]` | `currencies.list` |

- `addprime` — creates the root/prime unit (e.g. Dollar for USD)
- `addparent` — adds a larger denomination; `<multiplier>` = how many `<child>` units equal one of the new unit
- `addchild` — adds a smaller denomination; `<divisor>` = how many of the new unit equal one `<parent>`

### Account Management

| Command | Permission |
|---|---|
| `/currencies openaccount <name> <owner>` | `currencies.openaccount` |
| `/currencies setdefault <acronym>` | `currencies.setdefault` |

### Player Commands

| Command | Permission | Notes |
|---|---|---|
| `/currencies balance [player] [acronym]` | `currencies.balance` | No args = own balance |
| `/currencies pay <player> <amount>` | `currencies.pay` | Amount is a currency string |
| `/currencies bill <player> <amount>` | `currencies.bill` | Creates a pending bill |
| `/currencies paybill [transaction]` | `currencies.paybill` | No arg = oldest pending |
| `/currencies rejectbill [transaction]` | `currencies.rejectbill` | Same selection as paybill |
| `/currencies transactions [player] [page]` | `currencies.transactions` | |

### Admin Commands

| Command | Permission |
|---|---|
| `/currencies credit <player> <amount>` | `currencies.credit` |
| `/currencies debit <player> <amount>` | `currencies.debit` |
| `/currencies bankrupt <player> [acronym] [amount]` | `currencies.bankrupt` |

### Amount String Format

Concatenated denomination values: `200L20hc17g` = 200 Pounds + 20 Half-Crowns + 17 Groats. Currency inferred from unit symbols; ambiguous symbols resolved via the player's default currency. A plain integer uses the prime unit of the default currency.

## Plugin Registration

Commands and permissions declared in `src/main/resources/plugin.yml`. The `version` and `name` fields are Maven-filtered at build time.
