package com.fareflow.profile.dto;

import com.fareflow.profile.TypicalPlace;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A place on the wire.
 *
 * <p>Coordinates are required, not optional. The onboarding search box resolves
 * what the rider typed through the same geocoder the planner uses, so by the time
 * a place reaches this DTO it is a real location — not a string FareFlow will have
 * to guess about again later.
 */
public record TypicalPlaceDto(

        @NotBlank(message = "A saved place needs a name")
        @Size(max = 200, message = "Place names must be 200 characters or fewer")
        String name,

        @NotNull(message = "Pick a suggestion so FareFlow can resolve the place")
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Pick a suggestion so FareFlow can resolve the place")
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        Double longitude,

        @Size(max = 200, message = "Place ids must be 200 characters or fewer")
        String providerPlaceId
) {

    public TypicalPlace toDomain() {
        return TypicalPlace.of(name, latitude, longitude, providerPlaceId);
    }

    /** Null for an absent place, so the response says "unset" rather than "blank". */
    public static TypicalPlaceDto from(TypicalPlace place) {
        if (place == null || !place.isPresent()) {
            return null;
        }
        return new TypicalPlaceDto(place.name(), place.latitude(), place.longitude(),
                place.providerPlaceId());
    }
}
