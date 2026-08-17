package com.nobleuplift.currencies.services;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a Mockito {@code ResultSet} mock backed by an in-memory row cursor, so JDBC-mapping
 * tests can express expected data as {@code Map<String,Object>} rows instead of hand-stubbing
 * every {@code getX(String)} accessor. Only the accessors {@code JdbcCurrencyRepository}'s
 * mappers actually call are wired: {@code getString}, {@code getBoolean}, {@code getShort},
 * {@code getInt}, {@code getLong}, {@code getTimestamp}, {@code getObject}, plus
 * {@code next()}/{@code wasNull()}.
 */
final class RowResultSet {

    private RowResultSet() {
    }

    @SafeVarargs
    static ResultSet of(Map<String, Object>... rows) {
        return of(Arrays.asList(rows));
    }

    static ResultSet empty() {
        return of(List.of());
    }

    static Map<String, Object> row(Object... labelsAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            row.put((String) labelsAndValues[i], labelsAndValues[i + 1]);
        }
        return row;
    }

    private static ResultSet of(List<Map<String, Object>> rows) {
        ResultSet rs = mock(ResultSet.class);
        int[] cursor = {-1};
        boolean[] wasNull = {false};

        try {
            when(rs.next()).thenAnswer(inv -> {
                cursor[0]++;
                return cursor[0] < rows.size();
            });
            when(rs.wasNull()).thenAnswer(inv -> wasNull[0]);
            when(rs.getString(anyString())).thenAnswer(inv -> (String) value(rows, cursor, wasNull, inv.getArgument(0)));
            when(rs.getObject(anyString())).thenAnswer(inv -> value(rows, cursor, wasNull, inv.getArgument(0)));
            when(rs.getTimestamp(anyString())).thenAnswer(inv -> (Timestamp) value(rows, cursor, wasNull, inv.getArgument(0)));
            when(rs.getBoolean(anyString())).thenAnswer(inv -> {
                Object v = value(rows, cursor, wasNull, inv.getArgument(0));
                return v != null && (Boolean) v;
            });
            when(rs.getShort(anyString())).thenAnswer(inv -> {
                Object v = value(rows, cursor, wasNull, inv.getArgument(0));
                return v == null ? (short) 0 : ((Number) v).shortValue();
            });
            when(rs.getInt(anyString())).thenAnswer(inv -> {
                Object v = value(rows, cursor, wasNull, inv.getArgument(0));
                return v == null ? 0 : ((Number) v).intValue();
            });
            when(rs.getLong(anyString())).thenAnswer(inv -> {
                Object v = value(rows, cursor, wasNull, inv.getArgument(0));
                return v == null ? 0L : ((Number) v).longValue();
            });
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return rs;
    }

    private static Object value(List<Map<String, Object>> rows, int[] cursor, boolean[] wasNull, String label) {
        Map<String, Object> current = rows.get(cursor[0]);
        Object v = current.get(label);
        wasNull[0] = (v == null);
        return v;
    }
}
