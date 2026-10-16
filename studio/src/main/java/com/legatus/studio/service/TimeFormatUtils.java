package com.legatus.studio.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reusable high-precision time formatting utilities (MS-010).
 */
public final class TimeFormatUtils {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private TimeFormatUtils() {}

    /**
     * Formats an execution duration in milliseconds into an adaptive, human-readable string.
     * Examples: 124ms, 3.42s, 1m 15s.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0ms";
        }
        if (millis < 1000) {
            return millis + "ms";
        }
        if (millis < 60_000) {
            return String.format(Locale.ROOT, "%.2fs", millis / 1000.0);
        }
        long mins = millis / 60_000;
        long secs = (millis % 60_000) / 1000;
        return mins + "m " + secs + "s";
    }

    /**
     * Formats an ISO-8601 string, epoch millis, or Instant into HH:mm:ss.SSS.
     */
    public static String formatTimestamp(Object timestamp) {
        if (timestamp == null) return "--";
        String str = timestamp.toString().trim();
        if (str.isEmpty() || "--".equals(str) || "null".equalsIgnoreCase(str)) return "--";

        try {
            if (str.matches("^\\d+$")) {
                long epoch = Long.parseLong(str);
                return TIME_FORMATTER.format(Instant.ofEpochMilli(epoch));
            }
            Instant instant = Instant.parse(str);
            return TIME_FORMATTER.format(instant);
        } catch (Exception e) {
            return str;
        }
    }
}
