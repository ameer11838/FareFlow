package com.fareflow.discovery;

import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.location.LocationCandidate;
import com.fareflow.network.TransitGraph;
import com.fareflow.network.TransitStop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plans journeys over FareFlow's curated network of real stations and services.
 *
 * <p>Searches direct rides first, then one-transfer and two-transfer itineraries.
 * A bounded search rather than full Dijkstra because the network is small and the
 * product only ever shows a handful of options — and because a rider will not
 * consider a four-transfer itinerary regardless of what it scores.
 *
 * <p>Walking legs connect the street address to the first station and the last
 * station to the destination, so results are genuinely door-to-door.
 *
 * <p>Plain Java: the graph is injected, so this plans journeys with no Spring
 * context and no database.
 */
public final class NetworkRouteDiscoveryProvider implements RouteDiscoveryProvider {

    public static final String SOURCE = "CURATED_NETWORK";

    /** How far someone will plausibly walk to reach a station. */
    private static final double ACCESS_RADIUS_METRES = 2_500;
    /** For a place with no station nearby, widen once before giving up. */
    private static final double FALLBACK_RADIUS_METRES = 25_000;
    private static final int MAX_ACCESS_STOPS = 4;
    private static final double WALK_METRES_PER_MINUTE = 80.0;
    /**
     * Deliberately generous. Discovery does not know fares yet, so trimming hard
     * here would silently discard the cheapest itinerary before anything could
     * price it -- exactly the bias that made every Philadelphia option an
     * unpriceable Amtrak route. The optimizer trims the final set instead.
     */
    private static final int MAX_RESULTS = 14;

    private final TransitGraph graph;

    public NetworkRouteDiscoveryProvider(TransitGraph graph) {
        this.graph = graph;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public List<Journey> discover(LocationCandidate origin, LocationCandidate destination) {
        List<TransitStop> originStops = accessStops(origin);
        List<TransitStop> destinationStops = accessStops(destination);

        if (originStops.isEmpty() || destinationStops.isEmpty()) {
            // No station within reach. An empty list is the honest answer; inventing
            // a route to fill the screen would be worse than showing nothing.
            return List.of();
        }

        // Keyed by the sequence of lines used, so two itineraries riding the same
        // lines do not both appear.
        Map<String, Journey> found = new LinkedHashMap<>();

        for (TransitStop from : originStops) {
            for (TransitStop to : destinationStops) {
                if (from.getCode().equals(to.getCode())) {
                    continue;
                }
                addDirect(found, origin, destination, from, to);
                addOneTransfer(found, origin, destination, from, to);
                addTwoTransfers(found, origin, destination, from, to);
            }
        }

        // Sorted by duration only as a stable ordering, not as a ranking: the
        // optimization engine decides what is actually best once fares exist.
        return found.values().stream()
                .sorted(Comparator.comparingInt(Journey::totalMinutes)
                        .thenComparingInt(Journey::transfers))
                .limit(MAX_RESULTS)
                .toList();
    }

    private void addDirect(Map<String, Journey> found, LocationCandidate origin,
                           LocationCandidate destination, TransitStop from, TransitStop to) {
        for (TransitGraph.LineRoute route : graph.routesServing(from.getCode())) {
            route.legBetween(from.getCode(), to.getCode())
                    .ifPresent(leg -> register(found, origin, destination, List.of(leg), from, to));
        }
    }

    private void addOneTransfer(Map<String, Journey> found, LocationCandidate origin,
                                LocationCandidate destination, TransitStop from, TransitStop to) {
        for (TransitGraph.LineRoute first : graph.routesServing(from.getCode())) {
            for (TransitGraph.LineRoute second : graph.routesServing(to.getCode())) {
                if (first.line().getCode().equals(second.line().getCode())) {
                    continue;
                }
                for (TransitStop interchange : graph.interchangesBetween(first, second)) {
                    Optional<JourneyLeg> legOne = first.legBetween(from.getCode(), interchange.getCode());
                    Optional<JourneyLeg> legTwo = second.legBetween(interchange.getCode(), to.getCode());
                    if (legOne.isPresent() && legTwo.isPresent()) {
                        register(found, origin, destination,
                                List.of(legOne.get(), legTwo.get()), from, to);
                    }
                }
            }
        }
    }

    /**
     * Two transfers, which is what a Philadelphia to Brooklyn trip genuinely needs:
     * regional rail, then a corridor train, then a subway.
     */
    private void addTwoTransfers(Map<String, Journey> found, LocationCandidate origin,
                                 LocationCandidate destination, TransitStop from, TransitStop to) {
        for (TransitGraph.LineRoute first : graph.routesServing(from.getCode())) {
            for (TransitGraph.LineRoute third : graph.routesServing(to.getCode())) {
                if (first.line().getCode().equals(third.line().getCode())) {
                    continue;
                }
                for (TransitGraph.LineRoute middle : graph.routes()) {
                    if (middle.line().getCode().equals(first.line().getCode())
                            || middle.line().getCode().equals(third.line().getCode())) {
                        continue;
                    }
                    for (TransitStop firstInterchange : graph.interchangesBetween(first, middle)) {
                        for (TransitStop secondInterchange : graph.interchangesBetween(middle, third)) {
                            if (firstInterchange.getCode().equals(secondInterchange.getCode())) {
                                continue;
                            }
                            Optional<JourneyLeg> a = first.legBetween(from.getCode(), firstInterchange.getCode());
                            Optional<JourneyLeg> b = middle.legBetween(firstInterchange.getCode(), secondInterchange.getCode());
                            Optional<JourneyLeg> c = third.legBetween(secondInterchange.getCode(), to.getCode());
                            if (a.isPresent() && b.isPresent() && c.isPresent()) {
                                register(found, origin, destination,
                                        List.of(a.get(), b.get(), c.get()), from, to);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Wraps transit legs with access walks and stores the journey under a line-sequence key. */
    private void register(Map<String, Journey> found, LocationCandidate origin,
                          LocationCandidate destination, List<JourneyLeg> transitLegs,
                          TransitStop boardAt, TransitStop alightAt) {

        String key = transitLegs.stream().map(JourneyLeg::lineCode).reduce("", (a, b) -> a + ">" + b);

        List<JourneyLeg> legs = new ArrayList<>();
        walkLeg(origin.displayName(), boardAt.getName(),
                origin.latitude(), origin.longitude(),
                boardAt.getLatitude(), boardAt.getLongitude()).ifPresent(legs::add);
        legs.addAll(transitLegs);
        walkLeg(alightAt.getName(), destination.displayName(),
                alightAt.getLatitude(), alightAt.getLongitude(),
                destination.latitude(), destination.longitude()).ifPresent(legs::add);

        Journey journey = new Journey(
                key.substring(1),
                origin.displayName(),
                destination.displayName(),
                legs,
                Journey.DataSource.CURATED_NETWORK);

        // Keep the quickest itinerary for any given sequence of lines.
        found.merge(key, journey,
                (existing, candidate) -> candidate.totalMinutes() < existing.totalMinutes() ? candidate : existing);
    }

    /** Empty for trivial distances — nobody calls a 40-metre stroll a leg. */
    private Optional<JourneyLeg> walkLeg(String fromName, String toName,
                                         double fromLat, double fromLon,
                                         double toLat, double toLon) {
        double metres = LocationCandidate.haversineMetres(fromLat, fromLon, toLat, toLon);
        if (metres < 120) {
            return Optional.empty();
        }
        int minutes = (int) Math.max(1, Math.round(metres / WALK_METRES_PER_MINUTE));
        return Optional.of(JourneyLeg.walk(fromName, toName, minutes, metres,
                List.of(new JourneyLeg.Waypoint(fromName, fromLat, fromLon),
                        new JourneyLeg.Waypoint(toName, toLat, toLon))));
    }

    private List<TransitStop> accessStops(LocationCandidate place) {
        List<TransitStop> near = graph.stopsNear(
                place.latitude(), place.longitude(), ACCESS_RADIUS_METRES, MAX_ACCESS_STOPS);
        if (!near.isEmpty()) {
            return near;
        }
        // Widen once: a place like "Philadelphia, PA" geocodes to a city centroid
        // that can sit further from a station than a walkable radius allows.
        return graph.stopsNear(place.latitude(), place.longitude(), FALLBACK_RADIUS_METRES, 2);
    }
}
