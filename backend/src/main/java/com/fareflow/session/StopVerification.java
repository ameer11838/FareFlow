package com.fareflow.session;

/**
 * The verification verdict for one confirmed stop, kept with the fare event.
 *
 * <p>Stored rather than recomputed: the geofence radius is configuration and may
 * change, but what FareFlow concluded at the time — and the distance it concluded
 * it from — must stay fixed, exactly like the fare itself.
 *
 * @param distanceMetres  straight-line distance from the reported position to the
 *                        stop, or null when there was nothing to measure
 */
public record StopVerification(
        StopVerificationStatus status,
        Double distanceMetres,
        Double accuracyMetres,
        Double reportedLatitude,
        Double reportedLongitude,
        String explanation
) {

    public static StopVerification noFix() {
        return new StopVerification(StopVerificationStatus.UNVERIFIED_NO_FIX,
                null, null, null, null,
                "No location was shared · charged on rider confirmation");
    }

    public static StopVerification unverifiableStop(RiderPosition position) {
        return new StopVerification(StopVerificationStatus.UNVERIFIABLE_STOP,
                null, position.accuracyMetres(), position.latitude(), position.longitude(),
                "This stop has no published coordinates · nothing to check against");
    }

    public boolean isVerified() {
        return status.isVerified();
    }
}
