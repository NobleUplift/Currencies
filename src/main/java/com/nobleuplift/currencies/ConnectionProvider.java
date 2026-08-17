package com.nobleuplift.currencies;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstraction over acquiring a JDBC {@link Connection}, so that the service
 * layer depends on this port rather than the concrete Hikari-backed
 * {@link DatabaseManager}.
 */
public interface ConnectionProvider {

    Connection getConnection() throws SQLException;
}
