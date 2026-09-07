package com.fareflow.session;

/** What FareFlow is relying on when it says a rider completed a stop. */
public enum ProgressSource {

    /** The rider said so, and nothing corroborated it. */
    RIDER_CONFIRMED,

    /** At least one stop was corroborated by a location fix inside its geofence. */
    LOCATION_VERIFIED,

    /** An operator feed confirmed the boarding. Not yet available. */
    AGENCY_VERIFIED
}
