package com.fareflow.network;

import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.TransitMode;
import com.fareflow.location.LocationCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory view of the transit network, built once and queried per request.
 *
 * <p>Holding the whole network in memory is the right call at this size: it is a few
 * hundred rows, it changes rarely, and journey planning touches most of it. Querying
 * per hop would turn one search into dozens of round trips.
 *
 * <p>Pure data structure — no Spring, no repositories. It is handed the rows it needs.
 */
public final class TransitGraph {

    /** One line's ordered stops, with cumulative running times. */
    public record LineRoute(TransitLine line, List<Stop> stops) {

        public record Stop(TransitStop stop, int minutesFromStart) {
        }

        public Optional<Integer> indexOf(String stopCode) {
            for (int i = 0; i < stops.size(); i++) {
                if (stops.get(i).stop().getCode().equals(stopCode)) {
                    return Optional.of(i);
                }
            }
            return Optional.empty();
        }

        /**
         * Builds the ride leg between two stops on this line.
         *
         * <p>Runs in either direction: a line is a corridor, and a rider can travel
         * it inbound or outbound. Empty when the stops are not both on this line.
         */
        public Optional<JourneyLeg> legBetween(String fromCode, String toCode) {
            Optional<Integer> from = indexOf(fromCode);
            Optional<Integer> to = indexOf(toCode);
            if (from.isEmpty() || to.isEmpty() || from.get().equals(to.get())) {
                return Optional.empty();
            }

            int fromIndex = from.get();
            int toIndex = to.get();
            int minutes = Math.abs(stops.get(toIndex).minutesFromStart()
                    - stops.get(fromIndex).minutesFromStart());

            List<Stop> span = fromIndex < toIndex
                    ? stops.subList(fromIndex, toIndex + 1)
                    : stops.subList(toIndex, fromIndex + 1).reversed();

            List<JourneyLeg.Waypoint> waypoints = span.stream()
                    .map(entry -> new JourneyLeg.Waypoint(
                            entry.stop().getName(),
                            entry.stop().getLatitude(),
                            entry.stop().getLongitude()))
                    .toList();

            double metres = 0;
            for (int i = 1; i < waypoints.size(); i++) {
                metres += LocationCandidate.haversineMetres(
                        waypoints.get(i - 1).latitude(), waypoints.get(i - 1).longitude(),
                        waypoints.get(i).latitude(), waypoints.get(i).longitude());
            }

            JourneyLeg.Waypoint first = waypoints.getFirst();
            JourneyLeg.Waypoint last = waypoints.getLast();

            return Optional.of(new JourneyLeg(
                    TransitMode.valueOf(line.getMode()),
                    line.getAgency(),
                    line.getCode(),
                    line.getName(),
                    fromCode, first.name(),
                    toCode, last.name(),
                    minutes,
                    // Half the headway is the expected wait for a random arrival.
                    Math.max(0, line.getHeadwayMinutes() / 2),
                    metres,
                    waypoints,
                    null,
                    null,
                    false,
                    Math.abs(toIndex - fromIndex)));
        }
    }

    private final List<LineRoute> routes;
    private final Map<String, TransitStop> stopsByCode;
    /** Which lines serve each stop — the adjacency that makes transfers findable. */
    private final Map<String, List<LineRoute>> routesByStopCode;

    public TransitGraph(List<LineRoute> routes) {
        this.routes = List.copyOf(routes);
        this.stopsByCode = new HashMap<>();
        this.routesByStopCode = new HashMap<>();

        for (LineRoute route : routes) {
            for (LineRoute.Stop stop : route.stops()) {
                stopsByCode.putIfAbsent(stop.stop().getCode(), stop.stop());
                routesByStopCode
                        .computeIfAbsent(stop.stop().getCode(), key -> new ArrayList<>())
                        .add(route);
            }
        }
    }

    public List<LineRoute> routes() {
        return routes;
    }

    public Optional<TransitStop> stop(String code) {
        return Optional.ofNullable(stopsByCode.get(code));
    }

    public List<LineRoute> routesServing(String stopCode) {
        return routesByStopCode.getOrDefault(stopCode, List.of());
    }

    /** Stops within {@code radiusMetres}, nearest first. */
    public List<TransitStop> stopsNear(double latitude, double longitude, double radiusMetres, int limit) {
        return stopsByCode.values().stream()
                .filter(stop -> LocationCandidate.haversineMetres(
                        latitude, longitude, stop.getLatitude(), stop.getLongitude()) <= radiusMetres)
                .sorted(Comparator.comparingDouble(stop -> LocationCandidate.haversineMetres(
                        latitude, longitude, stop.getLatitude(), stop.getLongitude())))
                .limit(limit)
                .toList();
    }

    /** Stops shared by two lines — the places a transfer is physically possible. */
    public List<TransitStop> interchangesBetween(LineRoute first, LineRoute second) {
        return first.stops().stream()
                .map(LineRoute.Stop::stop)
                .filter(stop -> second.indexOf(stop.getCode()).isPresent())
                .toList();
    }
}
