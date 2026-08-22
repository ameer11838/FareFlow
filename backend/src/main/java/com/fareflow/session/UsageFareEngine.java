package com.fareflow.session;

import com.fareflow.journey.PersistedJourney;
import com.fareflow.journey.PersistedJourneyLeg;
import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic authority for FareFlow's proposed usage-based fare.
 *
 * <p>Elapsed time is deliberately absent. A delay may help verify a session later,
 * but it can never increase the rider's fare. The engine prices only completed
 * public-transit progress using configured base, distance, and stop rules.
 */
@Service
public class UsageFareEngine {

    private static final double METRES_PER_MILE = 1_609.344;

    private final UsageFareProperties properties;

    public UsageFareEngine(UsageFareProperties properties) {
        this.properties = properties;
    }

    public int totalProgressUnits(PersistedJourney journey) {
        return journey.getLegs().stream()
                .filter(UsageFareEngine::isTransit)
                .mapToInt(UsageFareEngine::unitsFor)
                .sum();
    }

    public long plannedDistanceMetres(PersistedJourney journey) {
        return Math.round(journey.getLegs().stream()
                .filter(UsageFareEngine::isTransit)
                .mapToDouble(PersistedJourneyLeg::getDistanceMetres)
                .sum());
    }

    public UsageFareCalculation calculate(PersistedJourney journey, int completedUnits) {
        return calculate(journey.getLegs().stream()
                .filter(UsageFareEngine::isTransit)
                .map(leg -> new FareLeg(leg.getMode(), leg.getLineName(),
                        leg.getDistanceMetres(), unitsFor(leg)))
                .toList(), completedUnits);
    }

    /** Same deterministic calculation for a discovered journey before persistence. */
    public UsageFareCalculation calculate(Journey journey, int completedUnits) {
        return calculate(journey.transitLegs().stream()
                .map(leg -> new FareLeg(leg.mode().name(), leg.lineName(),
                        Math.max(0, leg.distanceMetres()), unitsFor(leg)))
                .toList(), completedUnits);
    }

    /**
     * Authoritative fare delta at each stop boundary.
     *
     * <p>Every amount is derived from the same base/distance/stop rules as
     * {@link #calculate(PersistedJourney, int)}. There is deliberately no clock,
     * duration, wait, delay, or arrival-time input. Distance is recognized only
     * at a completed stop boundary, never continuously while a rider waits.
     */
    public List<StopFarePoint> stopFareProgress(PersistedJourney journey) {
        List<StopFarePoint> points = new ArrayList<>();
        long cumulative = 0;
        int sequence = 0;
        for (PersistedJourneyLeg leg : journey.getLegs()) {
            if (!isTransit(leg)) {
                continue;
            }
            int units = unitsFor(leg);
            UsageFareProperties.ModeRule rule = properties.forMode(leg.getMode());
            long previousDistanceFare = 0;
            for (int unit = 1; unit <= units; unit++) {
                long usedDistance = Math.round(leg.getDistanceMetres() * unit / units);
                long distanceFare = Math.round(
                        (usedDistance / METRES_PER_MILE) * rule.centsPerMile());
                long increment = Math.addExact(rule.centsPerStop(),
                        distanceFare - previousDistanceFare);
                if (unit == 1) {
                    increment = Math.addExact(increment, rule.baseCents());
                }
                cumulative = Math.addExact(cumulative, increment);
                points.add(new StopFarePoint(++sequence, stopName(leg, unit, units),
                        leg.getLineName(), leg.getMode(), increment, cumulative));
                previousDistanceFare = distanceFare;
            }
        }
        return List.copyOf(points);
    }

    public UsageFareEstimate estimate(Journey journey) {
        List<FareLeg> legs = journey.transitLegs().stream()
                .map(leg -> new FareLeg(leg.mode().name(), leg.lineName(),
                        Math.max(0, leg.distanceMetres()), unitsFor(leg)))
                .toList();
        int units = legs.stream().mapToInt(FareLeg::units).sum();
        UsageFareCalculation minimum = calculate(legs, 1);
        UsageFareCalculation maximum = calculate(legs, units);
        long distance = Math.round(legs.stream().mapToDouble(FareLeg::distanceMetres).sum());
        return new UsageFareEstimate(minimum.totalCents(), maximum.totalCents(),
                units, distance, properties.version());
    }

    private UsageFareCalculation calculate(List<FareLeg> legs, int completedUnits) {
        int totalUnits = legs.stream().mapToInt(FareLeg::units).sum();
        if (completedUnits < 0 || completedUnits > totalUnits) {
            throw new IllegalArgumentException("Trip progress is outside the selected route");
        }

        int remaining = completedUnits;
        long base = 0;
        long distanceFare = 0;
        long stopFare = 0;
        long distance = 0;
        int stops = 0;
        List<String> lines = new ArrayList<>();

        for (FareLeg leg : legs) {
            if (remaining <= 0) {
                continue;
            }
            int legUnits = leg.units();
            int usedUnits = Math.min(remaining, legUnits);
            remaining -= usedUnits;

            UsageFareProperties.ModeRule rule = properties.forMode(leg.mode());
            long usedDistance = Math.round(leg.distanceMetres() * usedUnits / legUnits);
            long legDistanceFare = Math.round(
                    (usedDistance / METRES_PER_MILE) * rule.centsPerMile());
            long legStopFare = Math.multiplyExact(usedUnits, rule.centsPerStop());

            base = Math.addExact(base, rule.baseCents());
            distanceFare = Math.addExact(distanceFare, legDistanceFare);
            stopFare = Math.addExact(stopFare, legStopFare);
            distance = Math.addExact(distance, usedDistance);
            stops = Math.addExact(stops, usedUnits);

            lines.add("%s · base %d¢ + distance %d¢ + %d stop%s %d¢"
                    .formatted(leg.lineName(), rule.baseCents(), legDistanceFare,
                            usedUnits, usedUnits == 1 ? "" : "s", legStopFare));
        }

        long total = Math.addExact(base, Math.addExact(distanceFare, stopFare));
        if (completedUnits == 0) {
            lines.add("No transit progress recorded · no charge");
        }
        return new UsageFareCalculation(total, base, distanceFare, stopFare, distance,
                stops, completedUnits, properties.version(), List.copyOf(lines));
    }

    public String version() {
        return properties.version();
    }

    public static int unitsFor(PersistedJourneyLeg leg) {
        if (leg.getStopCount() != null && leg.getStopCount() > 0) {
            return leg.getStopCount();
        }
        // A persisted waypoint sequence comes from the route provider. Each pair
        // is one observed segment; this fallback never invents intermediate stops.
        return Math.max(1, leg.decodedWaypoints().size() - 1);
    }

    private static int unitsFor(JourneyLeg leg) {
        if (leg.stopCount() != null && leg.stopCount() > 0) {
            return leg.stopCount();
        }
        return Math.max(1, leg.waypoints().size() - 1);
    }

    private static String stopName(PersistedJourneyLeg leg, int unit, int totalUnits) {
        List<JourneyLeg.Waypoint> waypoints = leg.decodedWaypoints();
        if (unit >= 0 && unit < waypoints.size()) {
            String name = waypoints.get(unit).name();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        // The destination is a provider fact even when intermediate stop names
        // were omitted. Intermediate names stay null instead of being invented.
        return unit == totalUnits ? leg.getToName() : null;
    }

    public static boolean isTransit(PersistedJourneyLeg leg) {
        return !"WALK".equals(leg.getMode());
    }

    private record FareLeg(String mode, String lineName, double distanceMetres, int units) {
    }

    public record StopFarePoint(int sequence, String stopName, String lineName, String mode,
                                long fareIncrementCents, long cumulativeFareCents) {
    }
}
