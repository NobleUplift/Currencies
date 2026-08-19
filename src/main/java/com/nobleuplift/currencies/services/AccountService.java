package com.nobleuplift.currencies.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;

/**
 * Account identity/lookup, including the reserved bank/market accounts.
 * The reserved-account IDs mirror {@code CurrenciesCore}'s public constants
 * (kept there for binary compatibility with external plugins); duplicated
 * here as literals to avoid a dependency back on the facade.
 */
public class AccountService {

    private static final int MINECRAFT_CENTRAL_BANK = 1;
    private static final int MINECRAFT_CENTRAL_BANKER = 2;
    private static final int THE_ENDERMAN_MARKET = 3;
    private static final int THE_ENDERMAN_MARKETEER = 4;

    private final ConnectionProvider connectionProvider;
    private final CurrencyRepository repository;
    private final CurrencyService currencyService;

    public AccountService(ConnectionProvider connectionProvider, CurrencyRepository repository, CurrencyService currencyService) {
        this.connectionProvider = connectionProvider;
        this.repository = repository;
        this.currencyService = currencyService;
    }

    public Account openAccount(String name, String owner) throws CurrenciesException {
        return openAccount(name, UUID.randomUUID().toString(), owner);
    }

    /**
     * Opens a business account with a caller-specified UUID rather than a randomly generated one --
     * used by the Vault adapter's createSharedAccount, where the caller supplies the account
     * identity to use. {@link #openAccount(String, String)} is the normal entry point for everything
     * else (command layer, other plugins) and just generates a random UUID.
     */
    public Account openAccount(String name, String uuid, String owner) throws CurrenciesException {
        if (name.length() <= 16) {
            throw new CurrenciesException("Non-player accounts must be longer than 16 characters.");
        }

        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check name not already taken
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_account WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new CurrenciesException("Account with name " + name + " already exists.");
                        }
                    }
                }

                Timestamp now = Clock.now();
                int newAccountId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO currencies_account (name, uuid, default_currency_id, date_created, date_modified)"
                        + " VALUES (?, ?, NULL, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, uuid);
                    ps.setTimestamp(3, now);
                    ps.setTimestamp(4, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new CurrenciesException("Failed to insert new account.");
                        }
                        newAccountId = keys.getInt(1);
                    }
                }

                // Find owner
                int ownerAccountId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_account WHERE name = ?")) {
                    ps.setString(1, owner);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new CurrenciesException("Owner " + owner + " does not exist.");
                        }
                        ownerAccountId = rs.getInt("id");
                    }
                }

                // Self-referential holder (length=0)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO currencies_holder (parent_account_id, child_account_id, length) VALUES (?, ?, 0)")) {
                    ps.setInt(1, newAccountId);
                    ps.setInt(2, newAccountId);
                    ps.executeUpdate();
                }

                // Owner holder (length=1)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO currencies_holder (parent_account_id, child_account_id, length) VALUES (?, ?, 1)")) {
                    ps.setInt(1, ownerAccountId);
                    ps.setInt(2, newAccountId);
                    ps.executeUpdate();
                }

                conn.commit();

                Account account = new Account();
                account.setId(newAccountId);
                account.setName(name);
                account.setUuid(uuid);
                account.setDefaultCurrency(null);
                account.setDateCreated(now);
                account.setDateModified(now);
                return account;

            } catch (CurrenciesException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in openAccount: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in openAccount: " + e.getMessage(), e);
        }
    }

    public void setDefault(String player, String acronym) throws CurrenciesException {
        Account account = getAccountFromPlayer(player, true);
        Currency currency = currencyService.getCurrencyFromAcronym(acronym, true);

        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_account SET default_currency_id = ?, date_modified = ? WHERE id = ?")) {
                    ps.setShort(1, currency.getId());
                    ps.setTimestamp(2, now);
                    ps.setInt(3, account.getId());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in setDefault: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in setDefault: " + e.getMessage(), e);
        }
    }

    public Account getMinecraftCentralBank() {
        return getAccountById(MINECRAFT_CENTRAL_BANK);
    }

    public Account getMinecraftCentralBanker() {
        return getAccountById(MINECRAFT_CENTRAL_BANKER);
    }

    public Account getTheEndermanMarket() {
        return getAccountById(THE_ENDERMAN_MARKET);
    }

    public Account getTheEndermanMarketeer() {
        return getAccountById(THE_ENDERMAN_MARKETEER);
    }

    private Account getAccountById(int id) {
        try (Connection conn = connectionProvider.getConnection()) {
            return repository.queryAccountById(conn, id);
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountById(" + id + "): " + e.getMessage(), e);
        }
    }

    /**
     * Player is not guaranteed to match the name in the database if someone changed their name and
     * another person took that name.
     *
     * However, the account's name will be updated on that account's login, so Currencies doesn't
     * need to be rewritten to use UUIDs.
     */
    public Account getAccountFromPlayer(String player, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
            Account account = repository.queryAccountByName(conn, player);
            if (account == null && exception) {
                throw new CurrenciesRuntimeException("Account " + player + " does not exist.");
            }
            return account;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountFromPlayer: " + e.getMessage(), e);
        }
    }

    public Account getAccountFromUniqueId(String uuid, boolean exception) throws CurrenciesRuntimeException {
        try (Connection conn = connectionProvider.getConnection()) {
            Account account = repository.queryAccountByUuid(conn, uuid);
            if (account == null && exception) {
                throw new CurrenciesRuntimeException("Account with UUID " + uuid + " does not exist.");
            }
            return account;
        } catch (CurrenciesRuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAccountFromUniqueId: " + e.getMessage(), e);
        }
    }

    /** Every account (player or business) that has a UUID -- for Vault's economy-converter tooling. */
    public List<Account> getAllAccountsWithUuid() {
        try (Connection conn = connectionProvider.getConnection()) {
            return repository.queryAccountsWithUuid(conn);
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in getAllAccountsWithUuid: " + e.getMessage(), e);
        }
    }

    /**
     * Renames the given account, rejecting the change if another account already has that name.
     * Note this only relabels the Currencies-side record: a player account's name is resynced from
     * their actual Minecraft name on their next join (see {@code Currencies.onPlayerJoin}), so a
     * Vault-initiated rename of a player account is only durable until then.
     */
    public boolean renameAccount(Account account, String name) {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM currencies_account WHERE name = ? AND id != ?")) {
                    ps.setString(1, name);
                    ps.setInt(2, account.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                Timestamp now = Clock.now();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE currencies_account SET name = ?, date_modified = ? WHERE id = ?")) {
                    ps.setString(1, name);
                    ps.setTimestamp(2, now);
                    ps.setInt(3, account.getId());
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in renameAccount: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes the given account, if and only if nothing references it: Currencies never destroys
     * transaction history or holdings, and every account table's foreign keys to
     * {@code currencies_account} are {@code ON DELETE NO ACTION}, so the delete will fail (and this
     * returns {@code false}) unless the account is genuinely unused. The account's own self-link
     * Holder row (inserted at creation for every account, player or business) is removed first since
     * it would otherwise always block the delete on its own.
     */
    public boolean deleteAccount(Account account) {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM currencies_holder WHERE parent_account_id = ? OR child_account_id = ?")) {
                    ps.setInt(1, account.getId());
                    ps.setInt(2, account.getId());
                    ps.executeUpdate();
                }

                int affected;
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM currencies_account WHERE id = ?")) {
                    ps.setInt(1, account.getId());
                    affected = ps.executeUpdate();
                }

                conn.commit();
                return affected > 0;
            } catch (SQLException e) {
                conn.rollback();
                // Most likely a foreign-key violation from currencies_holding/currencies_transaction --
                // that's Currencies correctly refusing to destroy real data, not an unexpected error.
                return false;
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in deleteAccount: " + e.getMessage(), e);
        }
    }

    /**
     * True if {@code owner} is the direct (length=1) parent of {@code account} in the Holder chain --
     * i.e. the account that was passed as the owner when the business account was opened. This is
     * narrower than {@link #isMember}: a grandparent (length=2+) is not a direct owner.
     */
    public boolean isOwner(Account owner, Account account) {
        return holderRelationshipExists(owner, account, "length = 1");
    }

    /**
     * True if {@code member} is any ancestor (owner at any depth, length&gt;=1) of {@code account} in
     * the Holder chain. Every direct owner is also a member; the reverse isn't true.
     */
    public boolean isMember(Account member, Account account) {
        return holderRelationshipExists(member, account, "length > 0");
    }

    /** Every account {@code owner} directly (length=1) owns -- the counterpart to {@link #isOwner}. */
    public List<Account> getOwnedAccounts(Account owner) {
        return childAccounts(owner, "length = 1");
    }

    /**
     * Every account {@code member} has any-depth membership in (length&gt;0), including everything
     * they directly own -- the counterpart to {@link #isMember}.
     */
    public List<Account> getMemberAccounts(Account member) {
        return childAccounts(member, "length > 0");
    }

    private List<Account> childAccounts(Account parent, String lengthCondition) {
        try (Connection conn = connectionProvider.getConnection()) {
            List<Account> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.id, a.name, a.uuid FROM currencies_holder h"
                    + " JOIN currencies_account a ON a.id = h.child_account_id"
                    + " WHERE h.parent_account_id = ? AND h." + lengthCondition)) {
                ps.setInt(1, parent.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Account a = new Account();
                        a.setId(rs.getInt("id"));
                        a.setName(rs.getString("name"));
                        a.setUuid(rs.getString("uuid"));
                        result.add(a);
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error listing child accounts: " + e.getMessage(), e);
        }
    }

    private boolean holderRelationshipExists(Account parent, Account child, String lengthCondition) {
        try (Connection conn = connectionProvider.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM currencies_holder WHERE parent_account_id = ? AND child_account_id = ? AND " + lengthCondition)) {
                ps.setInt(1, parent.getId());
                ps.setInt(2, child.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error checking Holder relationship: " + e.getMessage(), e);
        }
    }

    /**
     * Grants {@code owner} direct (length=1) ownership of {@code account}. Currencies' Holder table
     * allows multiple parents per child, so this adds an owner without displacing any existing one --
     * unlike Vault's single-owner assumption, "setting" an owner here means "adding" one.
     */
    public void addOwner(Account owner, Account account) {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO currencies_holder (parent_account_id, child_account_id, length) VALUES (?, ?, 1)")) {
                    ps.setInt(1, owner.getId());
                    ps.setInt(2, account.getId());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in addOwner: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in addOwner: " + e.getMessage(), e);
        }
    }

    /** Revokes {@code owner}'s direct (length=1) ownership of {@code account}, if any. */
    public void removeOwner(Account owner, Account account) {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM currencies_holder WHERE parent_account_id = ? AND child_account_id = ? AND length = 1")) {
                    ps.setInt(1, owner.getId());
                    ps.setInt(2, account.getId());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new CurrenciesRuntimeException("Database error in removeOwner: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Failed to get connection in removeOwner: " + e.getMessage(), e);
        }
    }
}
