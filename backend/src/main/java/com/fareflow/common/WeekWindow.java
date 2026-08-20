package com.fareflow.common;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * A Monday-to-Monday week in a specific timezone, expressed as a half-open
 * instant range {@code [start, end)}.
 *
 * <p><strong>Half-open on purpose:</strong> an entry at exactly midnight on Monday
 * belongs to exactly one week. An inclusive range on both ends double-counts
 * boundary rows, which is a classic off-by-one on a headline number.
 *
 * <p>Built with {@code atStartOfDay(zone)} rather than manual hour arithmetic so
 * daylight-saving transitions are handled correctly — in March one local week is
 * 167 hours long, and {@code minusDays(7)} on an instant gets it wrong.
 *
 * <p>Pure Java: no Spring, no database.
 */
public record WeekWindow(Instant start, Instant end, LocalDate weekStartDate, ZoneId zone) {

    public static WeekWindow containing(Instant instant, ZoneId zone) {
        ZonedDateTime zoned = instant.atZone(zone);
        LocalDate monday = zoned.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Instant start = monday.atStartOfDay(zone).toInstant();
        Instant end = monday.plusWeeks(1).atStartOfDay(zone).toInstant();

        return new WeekWindow(start, end, monday, zone);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
