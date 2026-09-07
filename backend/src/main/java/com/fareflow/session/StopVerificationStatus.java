package com.fareflow.session;

/**
 * How much FareFlow trusts that the rider was actually at the stop they confirmed.
 *
 * <p>Deliberately graded rather than boolean. A subway platform has no sky, a phone
 * can refuse the permission, and a provider sometimes names a stop without placing
 * it — none of which is evidence of fare evasion. Every value except
 * {@link #VERIFIED} still records progress and still charges: this grades the
 * evidence, it does not gate the fare.
 */
public enum StopVerificationStatus {

    /** A location fix placed the rider inside the stop's geofence. */
    VERIFIED,

    /** The rider sent no position — permission denied, or no fix available. */
    UNVERIFIED_NO_FIX,

    /** A fix arrived, but too imprecise to place the rider at a specific stop. */
    UNVERIFIED_IMPRECISE,

    /** The route provider named this stop without giving it coordinates. */
    UNVERIFIABLE_STOP,

    /** A good fix placed the rider outside the geofence. The one real red flag. */
    UNVERIFIED_TOO_FAR;

    public boolean isVerified() {
        return this == VERIFIED;
    }

    /**
     * Whether this outcome reflects a rider who looks to be somewhere else, as
     * opposed to one whose phone simply could not answer. Only this deserves a
     * reviewer's attention; the rest are ordinary conditions of moving underground.
     */
    public boolean isContradicted() {
        return this == UNVERIFIED_TOO_FAR;
    }
}
