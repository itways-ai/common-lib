package com.itways.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The platform's one rule for times on the wire: every {@link LocalDateTime}
 * holds UTC, and leaves a service as ISO-8601 with an explicit {@code Z}.
 *
 * <p>
 * Without the {@code Z} a browser reads "2026-09-24T06:10:01" as its own local
 * time, so every time the portal showed was off by the viewer's UTC offset.
 */
public final class UtcDateTimes {

    private UtcDateTimes() {
    }

    /** {@code 2026-09-24T06:10:01.84Z}; null stays null. */
    public static String format(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Reads a time sent by another service or the portal. An offset is honoured
     * and converted to UTC; a value without one is taken to be UTC already, which
     * is what every service sent before this rule existed.
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException noOffset) {
            return LocalDateTime.parse(value);
        }
    }
}
