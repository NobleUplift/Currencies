package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

class ClockTest {

    @Test
    void nowReturnsCurrentWallClockTime() {
        long before = System.currentTimeMillis();
        Timestamp now = Clock.now();
        long after = System.currentTimeMillis();

        assertTrue(now.getTime() >= before, "Clock.now() should not be before the call");
        assertTrue(now.getTime() <= after, "Clock.now() should not be after the call returned");
    }

    @Test
    void successiveCallsDoNotGoBackwards() {
        Timestamp first = Clock.now();
        Timestamp second = Clock.now();

        assertFalse(second.before(first), "time should not appear to move backwards between calls");
    }
}
