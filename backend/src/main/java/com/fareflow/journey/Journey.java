package com.fareflow.journey;

import java.util.List;

/**
 * A complete door-to-door itinerary made of ordered legs.
 *
 * <p>Deliberately carries no fare: a journey describes movement, and the fare
 * engine prices it separately. That split is what lets fare rules change — caps,
 * passes, transfer credits — without touching route discovery.
 *
 * @param dataSource where the itinerary came from, so the UI can be honest about it
 */
public record Journey(
        String id,
        String originName,
        String destinationName,
        List<JourneyLeg> legs,
        String dataSource
) {

    public Journey {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("A journey needs at least one leg");
        }
        legs = List.copyOf(legs);
    }

    /** Door to door, including waits. */
    public int totalMinutes() {
        return legs.stream().mapToInt(JourneyLeg::totalMinutes).sum();
    }

    public int walkingMinutes() {
        return legs.stream()
                .filter(leg -> leg.mode() == TransitMode.WALK)
                .mapToInt(JourneyLeg::totalMinutes)
                .sum();
    }

    /**
     * Transfers between transit legs. Walking between two stations counts as one
     * transfer, not two, which is how a rider would describe it.
     */
    public int transfers() {
        long transitLegs = legs.stream().filter(leg -> leg.mode().isTransit()).count();
        return (int) Math.max(0, transitLegs - 1);
    }

    public List<JourneyLeg> transitLegs() {
        return legs.stream().filter(leg -> leg.mode().isTransit()).toList();
    }

    /** Agencies used, in order of first appearance — the input to transfer rules. */
    public List<String> agencies() {
        return transitLegs().stream().map(JourneyLeg::agency).distinct().toList();
    }

    /** Every coordinate across every leg, for map bounds and drawing. */
    public List<JourneyLeg.Waypoint> allWaypoints() {
        return legs.stream().flatMap(leg -> leg.waypoints().stream()).toList();
    }

    /** A short human summary: "SEPTA → NJ Transit → PATH". */
    public String summary() {
        List<JourneyLeg> transit = transitLegs();
        if (transit.isEmpty()) {
            return "Walk";
        }
        return String.join(" → ", transit.stream().map(JourneyLeg::lineName).toList());
    }

    public static final class DataSource {
        /** Composed from FareFlow's curated network of real stations and services. */
        public static final String CURATED_NETWORK = "CURATED_NETWORK";
        /** From the legacy seeded transit_routes table, kept for fixtures and demos. */
        public static final String SEEDED_FIXTURE = "SEEDED_FIXTURE";
        /** Reserved: a real agency feed (GTFS) or a transit routing API. */
        public static final String LIVE_FEED = "LIVE_FEED";

        private DataSource() {
        }
    }
}
