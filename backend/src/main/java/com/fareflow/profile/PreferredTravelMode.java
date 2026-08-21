package com.fareflow.profile;

import java.util.Arrays;
import java.util.Optional;

/**
 * Modes the rider says they normally use.
 *
 * <p>Separate from {@code journey.TransitMode}, which describes what a leg of a
 * real itinerary <em>is</em>. This describes what a person says they <em>use</em>,
 * and the two vocabularies should be free to diverge: a rider thinks "train", the
 * network distinguishes RAIL from LIGHT_RAIL.
 */
public enum PreferredTravelMode {

    TRAIN("Train"),
    SUBWAY("Subway"),
    BUS("Bus"),
    FERRY("Ferry");

    private final String displayName;

    PreferredTravelMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<PreferredTravelMode> parse(String raw) {
        return CommuteFrequency.Enums.parse(PreferredTravelMode.class, raw);
    }

    public static String validNames() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }
}
