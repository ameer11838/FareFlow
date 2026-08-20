package com.fareflow.profile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How often the rider commutes, as a band rather than a number.
 *
 * <p>People do not know their exact commute count, and asking for one produces a
 * confidently wrong figure. A band is what they can answer honestly, and it is
 * enough for every calculation FareFlow actually makes.
 *
 * <p>{@link #estimatedDaysPerWeek()} is the single place a band becomes a number.
 * The values are deliberately conservative — the low end of each band, so a pass
 * recommendation errs toward "keep paying per ride" rather than toward a sale.
 */
public enum CommuteFrequency {

    ONE_TO_TWO_DAYS(1, "1–2 days a week"),
    THREE_TO_FOUR_DAYS(3, "3–4 days a week"),
    FIVE_PLUS_DAYS(5, "5+ days a week"),
    /** No stable pattern. Estimated at the midpoint, and labelled as an estimate. */
    VARIES(3, "It varies");

    private final int estimatedDaysPerWeek;
    private final String displayName;

    CommuteFrequency(int estimatedDaysPerWeek, String displayName) {
        this.estimatedDaysPerWeek = estimatedDaysPerWeek;
        this.displayName = displayName;
    }

    /**
     * Commuting days a week this band implies. The low end of the band, on purpose:
     * every projection built on it understates rather than overstates spending.
     */
    public int estimatedDaysPerWeek() {
        return estimatedDaysPerWeek;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<CommuteFrequency> parse(String raw) {
        return Enums.parse(CommuteFrequency.class, raw);
    }

    public static String validNames() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }

    /** Shared, case-insensitive, hyphen-tolerant parsing for the profile enums. */
    static final class Enums {
        private Enums() {
        }

        static <E extends Enum<E>> Optional<E> parse(Class<E> type, String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String normalised = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return Arrays.stream(type.getEnumConstants())
                    .filter(constant -> constant.name().equals(normalised))
                    .findFirst();
        }
    }
}
