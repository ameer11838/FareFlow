package com.fareflow.profile;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/** Rider category used by the simulated stop-based fare engine. */
public enum FareCategory {
    REGULAR("Regular fare", "Standard stop-based pricing"),
    STUDENT("Student", "Discounted stop and boarding charges"),
    SENIOR("Senior", "Reduced-fare stop and boarding charges"),
    REDUCED("Reduced fare", "Reduced pricing for eligible riders");

    private final String displayName;
    private final String detail;

    FareCategory(String displayName, String detail) {
        this.displayName = displayName;
        this.detail = detail;
    }

    public String displayName() { return displayName; }
    public String detail() { return detail; }

    public static Optional<FareCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.of(REGULAR);
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_').replace(' ', '_')));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static String validNames() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
