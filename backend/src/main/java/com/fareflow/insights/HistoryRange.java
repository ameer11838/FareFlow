package com.fareflow.insights;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * The four time windows the history endpoints support, each paired with the
 * bucket size that reads sensibly over that span.
 *
 * <p>The pairing is deliberate rather than configurable. Ninety daily bars is an
 * unreadable smear, and twelve monthly bars over one week is eleven empty
 * columns; letting a client ask for any combination would let it ask for both.
 */
public enum HistoryRange {

    SEVEN_DAYS("7d", "Last 7 days", 7, Granularity.DAY),
    THIRTY_DAYS("30d", "Last 30 days", 30, Granularity.DAY),
    THREE_MONTHS("3m", "Last 3 months", 91, Granularity.WEEK),
    ONE_YEAR("1y", "Last 12 months", 365, Granularity.MONTH);

    /** How wide one bar is. */
    public enum Granularity {
        DAY, WEEK, MONTH
    }

    private final String code;
    private final String displayName;
    private final int days;
    private final Granularity granularity;

    HistoryRange(String code, String displayName, int days, Granularity granularity) {
        this.code = code;
        this.displayName = displayName;
        this.days = days;
        this.granularity = granularity;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public int days() {
        return days;
    }

    public Granularity granularity() {
        return granularity;
    }

    public static HistoryRange defaultRange() {
        return THIRTY_DAYS;
    }

    public static Optional<HistoryRange> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (HistoryRange range : values()) {
            if (range.code.equals(normalized) || range.name().equalsIgnoreCase(normalized)) {
                return Optional.of(range);
            }
        }
        return Optional.empty();
    }

    /**
     * The first day of the bucket that {@code date} falls in.
     *
     * <p>Weeks start Monday, matching {@link com.fareflow.common.WeekWindow}, so a
     * weekly bar on Insights covers exactly the week the budget is measured over.
     */
    public LocalDate bucketStart(LocalDate date) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    public LocalDate nextBucket(LocalDate bucketStart) {
        return switch (granularity) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }
}
