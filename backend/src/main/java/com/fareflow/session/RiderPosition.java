package com.fareflow.session;

/**
 * A position reported by the rider's device at the moment they confirmed a stop.
 *
 * <p>Accuracy is carried alongside the coordinates because a fix without its error
 * radius cannot be judged: 300 m from a stop is a contradiction at 10 m accuracy
 * and meaningless at 500 m. Absent or non-positive accuracy is treated as unknown.
 */
public record RiderPosition(double latitude, double longitude, Double accuracyMetres) {

    public RiderPosition {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    public boolean hasAccuracy() {
        return accuracyMetres != null && accuracyMetres > 0;
    }

    /** Error radius to reason with; an unknown radius is treated as zero slack. */
    public double accuracyOrZero() {
        return hasAccuracy() ? accuracyMetres : 0;
    }
}
