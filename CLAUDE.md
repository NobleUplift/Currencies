# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn clean package        # Build and produce target/Currencies-*.jar
mvn test                 # Run tests
mvn clean package -DskipTests  # Build without running tests
```

The build includes a compile-time Ebean ORM bytecode enhancement step via `maven-antrun-plugin`. The output JAR is `target/Currencies-1.1.0-b063.jar`. Java 7 source/target compatibility is required.

## Architecture Overview

This is a Bukkit/Spigot Minecraft server plugin implementing a multi-currency economic system. The three main source files divide responsibility cleanly:

- **`Currencies.java`** — Plugin entry point (`JavaPlugin` + `Listener`). Handles lifecycle (onEnable/onDisable), Bukkit ORM registration, player join events (auto-creates accounts), and routes all commands to `CurrenciesCommand`. On first run, creates the full database schema via raw SQL and inserts four reserved system accounts.
- **`CurrenciesCore.java`** — Static-method API (~1350 lines). All business logic lives here: currency/unit CRUD, transaction processing, the compacting algorithm, bill workflows, and currency string parsing. This is also the public API for other plugins to depend on.
- **`CurrenciesCommand.java`** — Command handler. Validates permissions, parses arguments, and delegates to `CurrenciesCore`. All 17 commands route through a single switch statement.

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

### Integer-Only Arithmetic

All monetary amounts are stored as `long` values in the smallest base unit of a currency — never floating-point. The **compacting algorithm** in `CurrenciesCore` consolidates holdings across all denominations into base units before any transaction, then redistributes back into named denominations. This is the central invariant of the system; any change to transaction logic must preserve it.

### Reserved System Accounts (IDs 1–4)

Created at first startup and never deleted:
1. **Central Bank** — receives funds from bankruptcy operations to keep currency in circulation
2. **Central Banker** — tracks total currency in circulation
3. **Enderman Market** / **Enderman Marketeer** — reserved for a future feature, currently unused. The intended design: the Enderman Marketeer is the debt-side counterpart to a normal merchant. When a player goes bankrupt, overdrafts, or falls into debt, the Enderman Marketeer repossesses land the player owns — the land is literally evicted into The End.

### Bill Workflow

`BILL` transactions are two-phase: created as pending, then either accepted (triggers a PAY) or rejected. Both states are tracked in the `currencies_transaction` table via `typeId`.

### Currency Parsing

`CurrenciesCore` includes a parser for mixed-denomination strings (e.g., `200L20hc17g` for Pounds, Half-Crowns, Groats). Unit symbols are stored on `Unit` entities and used during parsing to split input strings.

## Minecraft Commands

All commands are under `/currencies` (alias `/cur`). Several subcommands also have their own top-level aliases (e.g., `/balance`, `/pay`, `/bill`). Permissions follow the pattern `currencies.<subcommand>`.

### Currency & Unit Management (admin)

| Command | Permission |
|---|---|
| `/currencies create <acronym> <name> [prefix]` | `currencies.create` |
| `/currencies delete <acronym>` | `currencies.delete` |
| `/currencies addprime <acronym> <name> <plural> <symbol>` | `currencies.addprime` |
| `/currencies addparent <acronym> <name> <plural> <symbol> <multiplier> <child>` | `currencies.addparent` |
| `/currencies addchild <acronym> <name> <plural> <symbol> <divisor> <parent>` | `currencies.addchild` |
| `/currencies list [page]` | `currencies.list` |

- `prefix` — boolean (`true`/`false`); whether the symbol precedes the amount (default `true`)
- `addprime` — creates the root/prime unit of a currency (e.g. Dollar for USD)
- `addparent` — adds a larger denomination above an existing unit; `<multiplier>` is how many `<child>` units equal one of the new unit
- `addchild` — adds a smaller denomination below an existing unit; `<divisor>` is how many of the new unit equal one `<parent>`

### Account Management

| Command | Permission |
|---|---|
| `/currencies openaccount <name> <owner>` | `currencies.openaccount` |
| `/currencies setdefault <acronym>` | `currencies.setdefault` |

- `openaccount` is for creating non-player (business) accounts; player accounts are created automatically on first join
- `setdefault` resolves ambiguity when two currencies share the same prime symbol

### Player Commands

| Command | Permission | Notes |
|---|---|---|
| `/currencies balance [player] [acronym]` | `currencies.balance` | No args = own balance; one arg = named player or acronym filter |
| `/currencies pay <player> <amount>` | `currencies.pay` | `<amount>` is a currency string (see below) |
| `/currencies bill <player> <amount>` | `currencies.bill` | Creates a pending bill the recipient must accept or reject |
| `/currencies paybill [transaction]` | `currencies.paybill` | No arg = oldest pending bill; arg = specific transaction ID |
| `/currencies rejectbill [transaction]` | `currencies.rejectbill` | Same selection logic as `paybill` |
| `/currencies transactions [player] [page]` | `currencies.transactions` | No args = own history page 1; one integer arg = page; player + page = other player's history |

### Admin Commands

| Command | Permission | Notes |
|---|---|---|
| `/currencies credit <player> <amount>` | `currencies.credit` | Adds funds to an account |
| `/currencies debit <player> <amount>` | `currencies.debit` | Removes funds from an account |
| `/currencies bankrupt <player>` | `currencies.bankrupt` + `currencies.bankrupt.all` | Zeros balance on all currencies |
| `/currencies bankrupt <player> <acronym>` | `currencies.bankrupt` | Zeros balance on one currency |
| `/currencies bankrupt <player> <acronym> <amount>` | `currencies.bankrupt` + `currencies.credit` | Zeros then sets a starting balance |

### Amount String Format

`<amount>` arguments use concatenated denomination values, e.g. `200L20hc17g` means 200 Pounds + 20 Half-Crowns + 17 Groats. The currency is inferred from the unit symbols; if the currency is ambiguous the player's default is used. A plain integer with no symbol is interpreted in the prime unit of the default currency.

## Database

MySQL 5.1+. Six tables: `currencies_currency`, `currencies_unit`, `currencies_account`, `currencies_holding`, `currencies_holder`, `currencies_transaction`. Schema is created via raw SQL in `Currencies.onEnable()` (not Ebean auto-DDL). Version migration hooks are also handled there.

Ebean entities are in `com.nobleuplift.currencies.entities` and must be declared in `getDatabaseClasses()` for Bukkit to register them.

## Plugin Registration

Commands and permissions are declared in `src/main/resources/plugin.yml`. The `version` and `name` fields are filtered from Maven properties at build time.
