package com.legatus.studio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimeFormatUtilsTest {

    @Test
    @DisplayName("Should format sub-second execution duration in milliseconds")
    void testFormatDurationMilliseconds() {
        assertEquals("0ms", TimeFormatUtils.formatDuration(0));
        assertEquals("0ms", TimeFormatUtils.formatDuration(-10));
        assertEquals("45ms", TimeFormatUtils.formatDuration(45));
        assertEquals("124ms", TimeFormatUtils.formatDuration(124));
        assertEquals("999ms", TimeFormatUtils.formatDuration(999));
    }

    @Test
    @DisplayName("Should format 1s to 60s execution duration in decimal seconds")
    void testFormatDurationSeconds() {
        assertEquals("1.00s", TimeFormatUtils.formatDuration(1000));
        assertEquals("3.42s", TimeFormatUtils.formatDuration(3420));
        assertEquals("15.50s", TimeFormatUtils.formatDuration(15500));
        assertEquals("59.99s", TimeFormatUtils.formatDuration(59990));
    }

    @Test
    @DisplayName("Should format durations over 60s into minutes and seconds")
    void testFormatDurationMinutesSeconds() {
        assertEquals("1m 0s", TimeFormatUtils.formatDuration(60000));
        assertEquals("1m 15s", TimeFormatUtils.formatDuration(75000));
        assertEquals("2m 5s", TimeFormatUtils.formatDuration(125000));
        assertEquals("10m 30s", TimeFormatUtils.formatDuration(630000));
    }

    @Test
    @DisplayName("Should gracefully format null or placeholder timestamps as --")
    void testFormatTimestampNullOrEmpty() {
        assertEquals("--", TimeFormatUtils.formatTimestamp(null));
        assertEquals("--", TimeFormatUtils.formatTimestamp(""));
        assertEquals("--", TimeFormatUtils.formatTimestamp("   "));
        assertEquals("--", TimeFormatUtils.formatTimestamp("--"));
        assertEquals("--", TimeFormatUtils.formatTimestamp("null"));
    }

    @Test
    @DisplayName("Should format valid epoch milliseconds and ISO-8601 timestamps")
    void testFormatTimestampValid() {
        long epoch = 1774886400000L;
        String formattedEpoch = TimeFormatUtils.formatTimestamp(epoch);
        assertNotNull(formattedEpoch);
        assertTrue(formattedEpoch.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"), "Expected HH:mm:ss.SSS pattern but got: " + formattedEpoch);

        Instant now = Instant.now();
        String formattedIso = TimeFormatUtils.formatTimestamp(now.toString());
        assertNotNull(formattedIso);
        assertTrue(formattedIso.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"), "Expected HH:mm:ss.SSS pattern but got: " + formattedIso);
    }
}
