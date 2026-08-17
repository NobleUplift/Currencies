package com.nobleuplift.currencies.service;

import java.sql.Timestamp;

/**
 * Java has no direct equivalent of MySQL's NOW(); this centralizes the current-time Timestamp construction.
 */
public final class Clock {

    private Clock() {
    }

    public static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
