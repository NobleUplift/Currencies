package com.nobleuplift.currencies;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private final HikariDataSource dataSource;

    public DatabaseManager(FileConfiguration config) {
        String host     = config.getString("database.host", "localhost");
        int    port     = config.getInt("database.port", 3306);
        String name     = config.getString("database.name", "minecraft");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "");

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(10);
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(30000);
        hikari.setIdleTimeout(600000);
        hikari.setMaxLifetime(1800000);
        hikari.setPoolName("CurrenciesPool");

        dataSource = new HikariDataSource(hikari);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
