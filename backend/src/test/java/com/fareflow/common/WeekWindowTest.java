package com.fareflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WeekWindowTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    @DisplayName("the window starts on Monday at local midnight")
    void startsOnMonday() {
        Instant wednesday = ZonedDateTime.of(2026, 8, 19, 14, 30, 0, 0, NEW_YORK).toInstant();
        WeekWindow week = WeekWindow.containing(wednesday, NEW_YORK);

        assertThat(week.weekStartDate().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(week.start().atZone(NEW_YORK).getHour()).isZero();
        assertThat(week.weekStartDate().toString()).isEqualTo("2026-08-17");
    }

    @Test
    @DisplayName("the range is half-open: the start is inside, the end is not")
    void halfOpen() {
        Instant now = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, NEW_YORK).toInstant();
        WeekWindow week = WeekWindow.containing(now, NEW_YORK);

        // An entry at exactly midnight Monday must fall in exactly one week.
        assertThat(week.contains(week.start())).isTrue();
        assertThat(week.contains(week.end())).isFalse();
        assertThat(week.contains(week.end().minusMillis(1))).isTrue();
    }

    @Test
    @DisplayName("a Monday belongs to the week it starts, not the previous one")
    void mondayBelongsToItsOwnWeek() {
        Instant monday = ZonedDateTime.of(2026, 8, 17, 0, 0, 0, 0, NEW_YORK).toInstant();
        WeekWindow week = WeekWindow.containing(monday, NEW_YORK);

        assertThat(week.weekStartDate().toString()).isEqualTo("2026-08-17");
        assertThat(week.start()).isEqualTo(monday);
    }

    @Test
    @DisplayName("a Sunday belongs to the week that began six days earlier")
    void sundayBelongsToThePrecedingMonday() {
        Instant sunday = ZonedDateTime.of(2026, 8, 23, 23, 59, 0, 0, NEW_YORK).toInstant();
        WeekWindow week = WeekWindow.containing(sunday, NEW_YORK);

        assertThat(week.weekStartDate().toString()).isEqualTo("2026-08-17");
        assertThat(week.contains(sunday)).isTrue();
    }

    @Test
    @DisplayName("the spring DST week is 167 hours, not 168")
    void springForwardWeekIsShorter() {
        // US DST began Sunday 8 March 2026, so the week from Monday 2 March
        // loses an hour. Naive minusDays(7) arithmetic on instants gets this wrong.
        Instant duringDstWeek = ZonedDateTime.of(2026, 3, 4, 12, 0, 0, 0, NEW_YORK).toInstant();
        WeekWindow week = WeekWindow.containing(duringDstWeek, NEW_YORK);

        assertThat(week.weekStartDate().toString()).isEqualTo("2026-03-02");
        assertThat(Duration.between(week.start(), week.end())).isEqualTo(Duration.ofHours(167));
    }

    @Test
    @DisplayName("the autumn DST week is 169 hours")
    void fallBackWeekIsLonger() {
        // US DST ended Sunday 1 November 2026.
        Instant duringDstWeek = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, NEW_YORK).toInstant();
        WeekWindow week = WeekWindow.containing(duringDstWeek, NEW_YORK);

        assertThat(week.weekStartDate().toString()).isEqualTo("2026-10-26");
        assertThat(Duration.between(week.start(), week.end())).isEqualTo(Duration.ofHours(169));
    }

    @Test
    @DisplayName("the same instant falls in different weeks in different timezones")
    void timezoneMatters() {
        // Sunday 23:30 in New York is already Monday in London.
        Instant instant = ZonedDateTime.of(2026, 8, 23, 23, 30, 0, 0, NEW_YORK).toInstant();

        WeekWindow newYork = WeekWindow.containing(instant, NEW_YORK);
        WeekWindow london = WeekWindow.containing(instant, ZoneId.of("Europe/London"));

        assertThat(newYork.weekStartDate().toString()).isEqualTo("2026-08-17");
        assertThat(london.weekStartDate().toString()).isEqualTo("2026-08-24");
    }
}
