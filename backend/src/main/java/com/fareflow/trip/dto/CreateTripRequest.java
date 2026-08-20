package com.fareflow.trip.dto;

import com.fareflow.trip.SelectedLabel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Note the absence of a userId: the trip is created for whoever is authenticated.
 * Accepting one here would let any caller spend from another account.
 */
public record CreateTripRequest(

        @NotNull(message = "routeId is required")
        @Positive(message = "routeId must be positive")
        Long routeId,

        SelectedLabel selectedLabel
) {

    /** Routes chosen outside a labelled recommendation are recorded as MANUAL. */
    public SelectedLabel selectedLabelOrManual() {
        return selectedLabel == null ? SelectedLabel.MANUAL : selectedLabel;
    }
}
