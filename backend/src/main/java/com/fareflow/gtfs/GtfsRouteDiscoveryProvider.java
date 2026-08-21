package com.fareflow.gtfs;

import com.fareflow.discovery.RouteDiscoveryProvider;
import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.TransitMode;
import com.fareflow.location.LocationCandidate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Time-dependent, multi-agency router over normalized GTFS Schedule data. */
@Component
@Order(10)
public class GtfsRouteDiscoveryProvider implements RouteDiscoveryProvider {

    public static final String SOURCE = "GTFS_SCHEDULE";
    private static final double ACCESS_RADIUS_METRES = 1_600;
    private static final double WALK_METRES_PER_MINUTE = 80;
    private static final int MAX_ACCESS_STOPS = 6;
    private static final int MAX_TRANSIT_LEGS = 3;
    private static final int MAX_EXPANSIONS = 500;
    private static final int MAX_RESULTS = 8;

    private final GtfsScheduleRepository repository;
    private final Clock clock;

    public GtfsRouteDiscoveryProvider(GtfsScheduleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public List<Journey> discover(LocationCandidate origin, LocationCandidate destination) {
        List<GtfsScheduleRepository.Stop> starts = repository.stopsNear(origin.latitude(),
                origin.longitude(), ACCESS_RADIUS_METRES, MAX_ACCESS_STOPS);
        if (starts.isEmpty() || repository.stopsNear(destination.latitude(), destination.longitude(),
                ACCESS_RADIUS_METRES, MAX_ACCESS_STOPS).isEmpty()) {
            return List.of();
        }

        PriorityQueue<State> queue = new PriorityQueue<>(Comparator.comparing(State::arrival));
        Instant now = clock.instant();
        for (GtfsScheduleRepository.Stop start : starts) {
            List<JourneyLeg> legs = new ArrayList<>();
            addWalk(legs, origin.displayName(), origin.latitude(), origin.longitude(), start.name(),
                    start.latitude(), start.longitude(), null);
            int accessMinutes = legs.isEmpty() ? 0 : legs.getLast().durationMinutes();
            queue.add(new State(start, now.plus(Duration.ofMinutes(accessMinutes)), legs,
                    0, null, false));
        }

        Map<String, Instant> bestArrival = new HashMap<>();
        Map<String, Journey> results = new HashMap<>();
        int expansions = 0;
        while (!queue.isEmpty() && expansions++ < MAX_EXPANSIONS && results.size() < MAX_RESULTS) {
            State state = queue.poll();
            String stateKey = state.stop().key() + ":" + state.transitLegs();
            Instant knownBest = bestArrival.get(stateKey);
            if (knownBest != null && knownBest.isBefore(state.arrival())) {
                continue;
            }
            bestArrival.put(stateKey, state.arrival());

            if (state.transitLegs() > 0) {
                registerIfDestination(results, state, origin, destination);
            }
            if (state.transitLegs() >= MAX_TRANSIT_LEGS) {
                continue;
            }

            Instant boardingHorizon = state.arrival().plus(Duration.ofMinutes(90));
            for (GtfsScheduleRepository.Boarding boarding : repository.boardings(
                    state.stop(), state.arrival(), boardingHorizon, 14)) {
                if (boarding.tripId().equals(state.lastTripId())) {
                    continue;
                }
                addRideStates(queue, state, boarding);
            }

            if (state.transitLegs() > 0 && (state.legs().isEmpty()
                    || state.legs().getLast().mode() != TransitMode.WALK)) {
                for (GtfsScheduleRepository.Transfer transfer : repository.transfersFrom(state.stop())) {
                    List<JourneyLeg> legs = new ArrayList<>(state.legs());
                    addWalk(legs, state.stop().name(), state.stop().latitude(), state.stop().longitude(),
                            transfer.to().name(), transfer.to().latitude(), transfer.to().longitude(),
                            (int) Math.ceil(transfer.seconds() / 60.0));
                    State next = new State(transfer.to(),
                            state.arrival().plusSeconds(transfer.seconds()), legs,
                            state.transitLegs(), state.lastTripId(), state.realtime());
                    offerIfUseful(queue, bestArrival, next);
                }
            }
        }

        return results.values().stream()
                .sorted(Comparator.comparingInt(Journey::totalMinutes)
                        .thenComparingInt(Journey::transfers))
                .limit(MAX_RESULTS)
                .toList();
    }

    private void addRideStates(PriorityQueue<State> queue, State state,
                               GtfsScheduleRepository.Boarding boarding) {
        List<GtfsScheduleRepository.TripStop> stops = repository.remainingTripStops(boarding);
        int boardIndex = -1;
        for (int index = 0; index < stops.size(); index++) {
            if (stops.get(index).sequence() == boarding.stopSequence()) {
                boardIndex = index;
                break;
            }
        }
        if (boardIndex < 0) {
            return;
        }

        int considered = 0;
        for (int index = boardIndex + 1; index < stops.size() && considered < 16; index++) {
            GtfsScheduleRepository.TripStop alight = stops.get(index);
            if (alight.dropOffType() != 0 || alight.arrival().isBefore(boarding.departure())) {
                continue;
            }
            considered++;
            List<JourneyLeg.Waypoint> waypoints = stops.subList(boardIndex, index + 1).stream()
                    .map(stop -> new JourneyLeg.Waypoint(stop.stop().name(), stop.stop().latitude(),
                            stop.stop().longitude()))
                    .toList();
            int wait = ceilMinutes(Duration.between(state.arrival(), boarding.departure()));
            int ride = Math.max(0, ceilMinutes(Duration.between(boarding.departure(), alight.arrival())));
            String routeName = firstNonBlank(boarding.shortName(), boarding.longName(), boarding.routeId());
            String lineName = boarding.headsign() == null || boarding.headsign().isBlank()
                    ? routeName : routeName + " toward " + boarding.headsign();
            JourneyLeg leg = new JourneyLeg(boarding.mode(), boarding.agency(),
                    "GTFS:" + boarding.feedKey() + ":" + boarding.routeId(), lineName,
                    boarding.feedKey() + ":" + state.stop().stopId(), state.stop().name(),
                    boarding.feedKey() + ":" + alight.stop().stopId(), alight.stop().name(),
                    ride, wait, pathDistance(waypoints), waypoints,
                    boarding.departure(), alight.arrival(),
                    boarding.realtime() || alight.realtime(), index - boardIndex);
            List<JourneyLeg> legs = new ArrayList<>(state.legs());
            legs.add(leg);
            boolean live = state.realtime() || boarding.realtime()
                    || stops.subList(boardIndex, index + 1).stream()
                    .anyMatch(GtfsScheduleRepository.TripStop::realtime);
            queue.add(new State(alight.stop(), alight.arrival(), legs,
                    state.transitLegs() + 1, boarding.tripId(), live));
        }
    }

    private void registerIfDestination(Map<String, Journey> results, State state,
                                       LocationCandidate origin, LocationCandidate destination) {
        double distance = LocationCandidate.haversineMetres(state.stop().latitude(),
                state.stop().longitude(), destination.latitude(), destination.longitude());
        if (distance > ACCESS_RADIUS_METRES) {
            return;
        }
        List<JourneyLeg> legs = new ArrayList<>(state.legs());
        addWalk(legs, state.stop().name(), state.stop().latitude(), state.stop().longitude(),
                destination.displayName(), destination.latitude(), destination.longitude(), null);
        String signature = legs.stream().filter(leg -> leg.mode().isTransit())
                .map(leg -> leg.lineCode() + ":" + leg.fromStopCode() + ":" + leg.toStopCode())
                .reduce((left, right) -> left + ">" + right).orElseThrow();
        Journey journey = new Journey(signature, origin.displayName(), destination.displayName(),
                legs, state.realtime() ? Journey.DataSource.GTFS_REALTIME : Journey.DataSource.GTFS_SCHEDULE);
        results.merge(signature, journey,
                (oldValue, newValue) -> newValue.totalMinutes() < oldValue.totalMinutes()
                        ? newValue : oldValue);
    }

    private static void offerIfUseful(PriorityQueue<State> queue, Map<String, Instant> best, State state) {
        Instant known = best.get(state.stop().key() + ":" + state.transitLegs());
        if (known == null || state.arrival().isBefore(known)) {
            queue.add(state);
        }
    }

    private static void addWalk(List<JourneyLeg> legs, String fromName, double fromLat,
                                double fromLon, String toName, double toLat, double toLon,
                                Integer forcedMinutes) {
        double metres = LocationCandidate.haversineMetres(fromLat, fromLon, toLat, toLon);
        if (forcedMinutes == null && metres < 120) {
            return;
        }
        int minutes = forcedMinutes == null
                ? Math.max(1, (int) Math.ceil(metres / WALK_METRES_PER_MINUTE))
                : Math.max(1, forcedMinutes);
        legs.add(JourneyLeg.walk(fromName, toName, minutes, metres,
                List.of(new JourneyLeg.Waypoint(fromName, fromLat, fromLon),
                        new JourneyLeg.Waypoint(toName, toLat, toLon))));
    }

    private static int ceilMinutes(Duration duration) {
        return (int) Math.max(0, Math.ceil(duration.toSeconds() / 60.0));
    }

    private static double pathDistance(List<JourneyLeg.Waypoint> points) {
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            JourneyLeg.Waypoint previous = points.get(index - 1);
            JourneyLeg.Waypoint current = points.get(index);
            distance += LocationCandidate.haversineMetres(previous.latitude(), previous.longitude(),
                    current.latitude(), current.longitude());
        }
        return distance;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Transit";
    }

    private record State(GtfsScheduleRepository.Stop stop, Instant arrival, List<JourneyLeg> legs,
                         int transitLegs, String lastTripId, boolean realtime) {
        private State {
            legs = List.copyOf(legs);
        }
    }
}
