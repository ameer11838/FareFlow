package com.fareflow.session.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Optional rider-confirmed exception; an omitted body means the stop was reached.
 *
 * <p>The position is equally optional and is evidence, not permission. A rider who
 * declines location, or whose phone has no fix underground, still advances and is
 * still charged — the stop is simply recorded as unverified. Sending a position can
 * only ever improve the record, never deny the trip.
 *
 * @param accuracyMetres the device's own reported error radius, without which a
 *                       distance cannot be judged fairly
 */
public record AdvanceTransitSessionRequest(
        String outcome,

        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        Double longitude,

        @PositiveOrZero(message = "accuracyMetres must not be negative")
        Double accuracyMetres
) {

    /** A position only counts when both coordinates arrived; a lone axis is noise. */
    public boolean hasPosition() {
        return latitude != null && longitude != null;
    }
}
