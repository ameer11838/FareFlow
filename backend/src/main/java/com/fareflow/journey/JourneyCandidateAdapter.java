package com.fareflow.journey;

import com.fareflow.fare.FareCalculation;
import com.fareflow.recommendation.optimization.RouteCandidate;

/**
 * Turns a priced {@link Journey} into the value type the optimization engine scores.
 *
 * <p>This adapter is what keeps the engine pure. The scorer knows about fares,
 * minutes, and transfers — not legs, agencies, geometry, or where any of it came
 * from. Multi-leg journeys and the old seeded routes both arrive as the same
 * {@link RouteCandidate}, so adding journey planning required no change to scoring.
 *
 * <p><strong>Unknown fares:</strong> a journey nobody could price is given a
 * sentinel fare far above any real one. That keeps it visible and selectable while
 * making certain it never wins on cost — the alternative, treating unknown as zero,
 * would make the unpriceable option always look cheapest.
 */
public final class JourneyCandidateAdapter {

    /**
     * Stand-in fare for an unpriced journey. Large enough to lose any cost
     * comparison, finite so normalization still works.
     */
    public static final long UNKNOWN_FARE_SENTINEL_CENTS = 100_000;

    private JourneyCandidateAdapter() {
    }

    public static RouteCandidate toCandidate(long routeId, Journey journey, FareCalculation fare) {
        long fareCents = fare.isPriced() ? fare.totalFareCents() : UNKNOWN_FARE_SENTINEL_CENTS;

        return new RouteCandidate(
                routeId,
                primaryAgency(journey),
                journey.summary(),
                primaryMode(journey),
                Math.max(1, journey.totalMinutes()),
                fareCents,
                journey.transfers());
    }

    /** The agency doing most of the moving, used as the journey's identity. */
    private static String primaryAgency(Journey journey) {
        return journey.transitLegs().stream()
                .max(java.util.Comparator.comparingInt(JourneyLeg::durationMinutes))
                .map(JourneyLeg::agency)
                .orElse("WALK");
    }

    private static String primaryMode(Journey journey) {
        return journey.transitLegs().stream()
                .max(java.util.Comparator.comparingInt(JourneyLeg::durationMinutes))
                .map(leg -> leg.mode().name())
                .orElse(TransitMode.WALK.name());
    }
}
