package com.nobleuplift.currencies.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

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
                        + " VALUES (?, NULL, NULL, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setTimestamp(2, now);
                    ps.setTimestamp(3, now);
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
                account.setUuid(null);
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
}
