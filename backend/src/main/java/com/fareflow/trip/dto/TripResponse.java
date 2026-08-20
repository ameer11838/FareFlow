package com.fareflow.trip.dto;

import com.fareflow.route.TransitProvider;
import com.fareflow.trip.Trip;

import java.time.Instant;

/**
 * @param routeId   null for trips taken from a discovered journey
 * @param journeyId null for trips taken from a seeded route
 * @param savedVersusFastestCents null when no baseline was recorded — meaning
 *        "not computable", which is different from a computed zero
 */
public record TripResponse(
        long id,
        long userId,
        Long routeId,
        Long journeyId,
        String origin,
        String destination,
        String provider,
        String providerName,
        String mode,
        long fareCents,
        int durationMinutes,
        int transfers,
        String selectedLabel,
        Long baselineFareCents,
        Long savedVersusFastestCents,
        String status,
        Instant takenAt
) {

    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getUserId(),
                trip.getTransitRouteId(),
                trip.getJourneyId(),
                trip.getOrigin(),
                trip.getDestination(),
                trip.getProvider(),
                displayNameOf(trip.getProvider()),
                trip.getMode(),
                trip.getFareCents(),
                trip.getDurationMinutes(),
                trip.getTransfers(),
                trip.getSelectedLabel().name(),
                trip.getBaselineFareCents(),
                trip.savedVersusFastestCents().orElse(null),
                trip.getStatus().name(),
                trip.getTakenAt());
    }

    private static String displayNameOf(String provider) {
        try {
            return TransitProvider.valueOf(provider).displayName();
        } catch (IllegalArgumentException exception) {
            // A provider retired from the enum must not break historical trips.
            return provider;
        }
    }
}
