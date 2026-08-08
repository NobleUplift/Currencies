# Plan: Modernize Currencies Plugin for Paper / Spigot

## Context

This plugin was written for Bukkit/Spigot 1.8 (2014) and targets Java 7. Two things have since broken
it entirely against modern servers:

1. **Bukkit removed its built-in Ebean ORM** (~1.10). `getDatabase()`, `getDatabaseClasses()`, and
   `database: true` in plugin.yml no longer exist. Every data access call in the plugin currently
   goes through this removed API.
2. **Java 7 is not supported** by any server newer than ~1.16. Paper 1.18+ requires Java 16;
   Paper 1.20+ requires Java 17.

The goal is to produce a JAR that loads and runs correctly on a modern Paper or Spigot server.

---

## Paper vs. Spigot — DMCA / Distribution Question

**Spigot still requires BuildTools.** Spigot cannot distribute a pre-built server JAR because it
contains modified Mojang code, which is not permitted under Mojang's copyright terms. Developers
must run BuildTools locally, which downloads the vanilla Minecraft JAR and applies Spigot patches.
This requirement has not been dropped.

**Paper does NOT require BuildTools.** Paper ships a small bootstrapper JAR ("Paperclip") that
downloads the unmodified vanilla Minecraft server at first launch and applies binary patches at
runtime. Because Mojang code is never bundled in what Paper distributes, there is no DMCA concern.
Paper JARs download directly from papermc.io.

**Recommendation: target Paper-API.** Paper-API is a strict superset of Spigot-API; plugins built
against Paper-API run on both Paper and Spigot for the chosen MC version. Paper is where modern
plugin development has moved, and it eliminates the BuildTools dependency for server operators.

---

## ASSESSMENT.md: Before or After the Upgrade?

The ASSESSMENT.md identifies 13 issues. They split into two groups:

### Resolve DURING the upgrade (coupled to the ORM rewrite)

| Issue | Why it's coupled |
|---|---|
| #4 ORM lazy-load in `toString()` | Ebean is being deleted; entity classes become plain POJOs — the lazy-load problem disappears |
| #8 No optimistic locking | `@Version` is an ORM feature; JDBC transaction isolation replaces it |
| #9 Command layer uses JPA entities directly | With JDBC, entities become plain POJOs (no live proxy), so coupling is harmless |
| #10 DB init has no rollback | `createSqlUpdate()` is being replaced with JDBC; wrap in a `Connection` with `setAutoCommit(false)` |

**Issue #1 (CurrenciesCore static god object)** is partially resolved during the upgrade: the
rewrite must replace `Currencies.getInstance().getDatabase()` with something — the cleanest move
is to inject the new `DatabaseManager` via `CurrenciesCore.init(db)` at that moment. This
satisfies the spirit of the assessment fix without a full testability overhaul.

### Address AFTER the upgrade (independent refactors)

Issues #2, 3, 5, 6, 7, 11, 12, 13 touch no database layer code. They are safe, incremental
improvements once the plugin is running on modern Paper. Doing them before the upgrade would create
merge noise without reducing migration risk.

---

## Branch & PR Strategy

All work is done on the **`paper-1.21`** branch. Each step below maps to exactly one atomic commit
following the Single Responsibility Principle — one logical change per commit, no bundling. The
branch will be opened as a pull request on GitHub against `master` when complete.

ASSESSMENT.md issues that are resolved during the upgrade (#4, #8, #9, #10) are each committed
separately at the point in the sequence where they naturally arise, not folded into larger commits.

---

## Implementation Plan

### Commit 1 — Update `pom.xml`

**Remove:**
- `org.spigotmc:spigot-api:1.8-R0.1-SNAPSHOT`
- `org.bukkit:bukkit:1.8-R0.1-SNAPSHOT`
- `org.avaje.ebeanorm:avaje-ebeanorm:3.3.1`
- `org.eclipse.persistence:eclipselink:2.5.0-RC1`
- `mysql:mysql-connector-java:5.1.35`
- The `maven-antrun-plugin` block that runs Ebean bytecode enhancement

**Add:**
- `io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT` (provided scope)
- `com.zaxxer:HikariCP:5.1.0` (compile scope — must be shaded)
- `com.mysql:mysql-connector-j:9.3.0` (compile scope — shade or runtime)

**Change Java version:**
```xml
<source>17</source>
<target>17</target>
```

**Add `maven-shade-plugin`** to bundle HikariCP and the MySQL connector into the output JAR
(or relocate under `com.nobleuplift.currencies.libs` to avoid class collisions with other plugins).

**Update Vault** from 1.5.4 to 1.7.3 if it remains a dependency.

Critical file: `pom.xml`

---

### Commit 2 — Update `plugin.yml`

Remove the `database: true` line. This key is not recognized in modern Bukkit/Spigot/Paper and
would cause a warning or load failure.

Critical file: `src/main/resources/plugin.yml`

---

### Commit 3 — Add `config.yml` database section

Add a default `config.yml` in `src/main/resources/` with:
```yaml
database:
  host: localhost
  port: 3306
  name: minecraft
  username: root
  password: ""
```

This replaces the previous hard-coded database connection that Bukkit managed internally.

---

### Commit 4 — Create `DatabaseManager.java`

New file: `src/main/java/com/nobleuplift/currencies/DatabaseManager.java`

Responsibilities:
- Read connection config from `Currencies.getInstance().getConfig()`
- Initialize a `HikariDataSource` with the MySQL driver
- Expose `getConnection()` → `java.sql.Connection`
- Expose `close()` called from `Currencies.onDisable()`

This class replaces the Bukkit `EbeanServer` object returned by `getDatabase()`.

---

### Commit 5 — Rewrite `Currencies.java`

Critical file: `src/main/java/com/nobleuplift/currencies/Currencies.java`

Changes:
- **Remove** `getDatabaseClasses()` override entirely
- In `onEnable()`:
  - Remove all `getDatabase().createSqlUpdate("...").execute()` calls
  - Instantiate `DatabaseManager` and store it on the plugin instance
  - Call `CurrenciesCore.init(databaseManager)` before registering events
  - Run schema DDL via `DatabaseManager.getConnection()` + JDBC `Statement`, wrapped in a
    try-with-resources block; wrap the multi-statement init sequence in a transaction
    (fixing Assessment issue #10)
- In `onDisable()`:
  - Call `databaseManager.close()`
- In the `PlayerJoinEvent` handler:
  - Replace `getDatabase().find(Account.class).where().eq("uuid", ...).findUnique()`
    with a JDBC `PreparedStatement` query against `currencies_account`

---

### Commit 6 — Rewrite `CurrenciesCore.java` data access

Critical file: `src/main/java/com/nobleuplift/currencies/CurrenciesCore.java`

This is the largest change (~1350 lines). Every method that calls `Currencies.getInstance().getDatabase()` must be rewritten.

**Pattern to apply uniformly:**

Old:
```java
Currencies.getInstance().getDatabase()
    .find(Account.class).where().eq("uuid", uuid).findUnique();
```

New:
```java
try (Connection conn = db.getConnection();
     PreparedStatement ps = conn.prepareStatement(
         "SELECT * FROM currencies_account WHERE uuid = ?")) {
    ps.setString(1, uuid);
    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) { return mapAccount(rs); }
    }
}
```

Add:
- `private static DatabaseManager db;`
- `public static void init(DatabaseManager manager) { db = manager; }`
- Private `mapX(ResultSet rs)` helper methods for each entity type (Account, Currency, Unit, etc.)

The `@Transactional` annotations will be removed; transaction demarcation moves to explicit
`conn.setAutoCommit(false)` / `conn.commit()` / `conn.rollback()` within each method that
currently carries the annotation.

This commit also partially resolves **Assessment #1** (static god object) by injecting
`DatabaseManager` via `CurrenciesCore.init(db)` instead of calling
`Currencies.getInstance().getDatabase()` in every method.

---

### Commit 6a — Fix Assessment #10: wrap DB schema init in a transaction

Immediately after the `CurrenciesCore.java` rewrite, commit the DDL init sequence change
separately: wrap all `CREATE TABLE` + reserved-account `INSERT` statements in a single JDBC
transaction with rollback on failure. This is a distinct correctness fix, not part of the API
migration itself.

---

### Commit 7 — Strip JPA from entity classes

Critical files: `src/main/java/com/nobleuplift/currencies/entities/*.java`

- Remove all `javax.persistence.*` imports and annotations (`@Entity`, `@Table`, `@Id`,
  `@GeneratedValue`, `@ManyToOne`, `@OneToMany`, `@ManyToMany`, `@EmbeddedId`, `@Embeddable`,
  `@Column`, `@JoinColumn`, `@JoinTable`)
- Keep all fields and getters/setters — entities become plain POJOs used as data containers
- Remove collection fields (`units`, `holdings`, `parentAccounts`, etc.) from `toString()`
  implementations (fixing Assessment issue #4)
- `HolderPK` and `HoldingPK` keep their `equals()`/`hashCode()` since they are still used as
  map keys in the core logic

This commit resolves **Assessment #4** (ORM lazy-load in `toString()`) and **Assessment #9**
(command layer uses live JPA entities) — both disappear when entities become plain POJOs.

**Assessment #8** (no optimistic locking) is closed by design: HikariCP connections use MySQL's
default transaction isolation (`REPEATABLE READ`), which is sufficient for this workload; `@Version`
was only needed because Ebean could silently clobber concurrent updates.

---

### Commit 8 — Verify API compatibility

Scan for other Bukkit API calls that may have changed between 1.8 and 1.21:

- `PlayerJoinEvent` — stable, no change needed
- `Bukkit.getPluginManager().registerEvents()` — stable
- `Bukkit.getOfflinePlayer()` / `getPlayer()` — stable
- `sender.hasPermission()` / `CommandSender` — stable
- `JavaPlugin` base class — stable

The plugin does not use entity metadata, block/item Material enums, or other APIs that broke
heavily in 1.13 (the "flattening" update), so compatibility risk outside the database layer is low.

---

## Commit Summary (branch: `paper-1.21`)

| Commit | Files | Assessment issue closed |
|---|---|---|
| 1 — Update pom.xml | `pom.xml` | — |
| 2 — Remove `database: true` | `plugin.yml` | — |
| 3 — Add config.yml DB section | `config.yml` (new) | — |
| 4 — Add DatabaseManager | `DatabaseManager.java` (new) | — |
| 5 — Rewrite Currencies.java | `Currencies.java` | — |
| 6 — Rewrite CurrenciesCore.java | `CurrenciesCore.java` | #1 (partial) |
| 6a — Transactional DB init | `Currencies.java` | #10 |
| 7 — Strip JPA from entities | `entities/*.java` | #4, #8, #9 |
| 8 — API compatibility scan | (any files with changed Bukkit calls) | — |

---

## Verification

1. `mvn clean package` — must produce a JAR with no compilation errors
2. Drop JAR into a Paper 1.21 server's `plugins/` folder; start the server — look for
   `[Currencies] Enabled` with no `ClassNotFoundException` or `NoSuchMethodError`
3. Run `/currencies create TST Test` — verify the currency is inserted into the database
4. Run `/balance` as a player — verify account is auto-created on join and balance displays
5. Run `/pay <player> 10` — verify transaction records appear in `currencies_transaction`
6. Run `/currencies list` — verify pagination output is correct
