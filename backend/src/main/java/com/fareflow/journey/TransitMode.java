package com.fareflow.journey;

/** How a leg is travelled. WALK legs are what connect a street address to a station. */
public enum TransitMode {
    WALK,
    RAIL,
    SUBWAY,
    LIGHT_RAIL,
    BUS,
    FERRY;

    public boolean isTransit() {
        return this != WALK;
    }
}
