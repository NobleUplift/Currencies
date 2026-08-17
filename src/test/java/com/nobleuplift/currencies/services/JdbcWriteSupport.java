package com.nobleuplift.currencies.services;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.nobleuplift.currencies.ConnectionProvider;

/**
 * Scaffolding for tests that exercise raw-JDBC write paths (INSERT/UPDATE issued directly via
 * {@code Connection.prepareStatement}, outside the repository). Two {@code PreparedStatement}
 * mocks are distinguished by which overload the production code calls: the 1-arg form (plain
 * SELECT/UPDATE/INSERT) and the 2-arg {@code RETURN_GENERATED_KEYS} form. Every 1-arg statement
 * shares one mock, so tests that issue more than one SELECT in sequence (e.g. two uniqueness
 * checks) should stub {@code resultSet.next()} with a multi-value {@code thenReturn(...)} matching
 * call order.
 */
final class JdbcWriteSupport {

    final ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
    final Connection connection = mock(Connection.class);
    final PreparedStatement plainStatement = mock(PreparedStatement.class);
    final PreparedStatement generatedKeyStatement = mock(PreparedStatement.class);
    final ResultSet resultSet = mock(ResultSet.class);
    final ResultSet generatedKeys = mock(ResultSet.class);

    JdbcWriteSupport() throws SQLException {
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(plainStatement);
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(generatedKeyStatement);
        when(plainStatement.executeQuery()).thenReturn(resultSet);
        when(generatedKeyStatement.executeQuery()).thenReturn(resultSet);
        when(generatedKeyStatement.getGeneratedKeys()).thenReturn(generatedKeys);
    }
}
