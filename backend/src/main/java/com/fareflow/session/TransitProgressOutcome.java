package com.fareflow.session;

/** What happened at the next planned stop boundary. */
public enum TransitProgressOutcome {
    REACHED,
    SKIPPED,
    DIVERTED;

    public static TransitProgressOutcome parse(String raw) {
        if (raw == null || raw.isBlank()) return REACHED;
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown stop outcome '%s'. Valid values are REACHED, SKIPPED, DIVERTED"
                            .formatted(raw));
        }
    }
}
