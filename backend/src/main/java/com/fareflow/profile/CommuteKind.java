package com.fareflow.profile;

import java.util.Arrays;
import java.util.Optional;

/** What the rider's regular commute is for, or that they do not have one. */
public enum CommuteKind {

    WORK("Work"),
    SCHOOL("School"),
    BOTH("Work and school"),
    /** Explicitly answered "no regular commute" — different from not having asked. */
    NONE("No regular commute");

    private final String displayName;

    CommuteKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Whether a typical origin and destination are meaningful for this answer. */
    public boolean hasRegularCommute() {
        return this != NONE;
    }

    public static Optional<CommuteKind> parse(String raw) {
        return CommuteFrequency.Enums.parse(CommuteKind.class, raw);
    }

    public static String validNames() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }
}
