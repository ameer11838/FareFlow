package com.fareflow.session;

import com.fareflow.journey.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic authority for FareFlow's proposed stop-based fare.
 *
 * <p>Only confirmed route boundaries affect price. Time, waiting, traffic, and
 * delays are deliberately absent. The calculation order is stable and visible:
 * mode charge, transfer credit, rider discount, then daily/weekly caps.
 */
@Service
public class UsageFareEngine {

    private static final double METRES_PER_MILE = 1_609.344;
    private final UsageFareProperties properties;

    public UsageFareEngine(UsageFareProperties properties) {
        this.properties = properties;
    }

    public int totalProgressUnits(PersistedJourney journey) {
        return boundaries(journey).size();
    }

    public long plannedDistanceMetres(PersistedJourney journey) {
        return Math.round(journey.getLegs().stream().filter(UsageFareEngine::isTransit)
                .mapToDouble(PersistedJourneyLeg::getDistanceMetres).sum());
    }

    public UsageFareCalculation calculate(PersistedJourney journey, int completedUnits) {
        return calculate(boundaries(journey), completedUnits, UsageFareContext.regular());
    }

    public UsageFareCalculation calculate(PersistedJourney journey, int completedUnits,
                                           UsageFareContext context) {
        return calculate(boundaries(journey), completedUnits, context);
    }

    /** Same deterministic calculation for a discovered journey before persistence. */
    public UsageFareCalculation calculate(Journey journey, int completedUnits) {
        return calculate(boundaries(journey), completedUnits, UsageFareContext.regular());
    }

    public UsageFareCalculation calculate(Journey journey, int completedUnits,
                                           UsageFareContext context) {
        return calculate(boundaries(journey), completedUnits, context);
    }

    private UsageFareCalculation calculate(List<FareBoundary> boundaries, int completedUnits,
                                            UsageFareContext context) {
        if (completedUnits < 0 || completedUnits > boundaries.size()) {
            throw new IllegalArgumentException("Trip progress is outside the selected route");
        }
        long cumulative = 0;
        long base = 0;
        long distance = 0;
        long stop = 0;
        long transfer = 0;
        long concession = 0;
        long cap = 0;
        long travelled = 0;
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < completedUnits; index++) {
            StopFarePoint point = price(boundaries.get(index), context, cumulative,
                    TransitProgressOutcome.REACHED);
            cumulative = point.cumulativeFareCents();
            base += point.baseCents();
            distance += point.distanceCents();
            stop += point.stopCents();
            transfer += point.transferDiscountCents();
            concession += point.concessionDiscountCents();
            cap += point.capDiscountCents();
            travelled += point.distanceMetres();
            lines.add(point.description());
        }
        if (completedUnits == 0) lines.add("No transit progress recorded · no charge");
        return new UsageFareCalculation(cumulative, base, distance, stop, transfer,
                concession, cap, travelled, completedUnits, completedUnits,
                properties.version(), List.copyOf(lines));
    }

    /** Prices the next stop against the session's actual running total. */
    public StopFarePoint quote(PersistedJourney journey, int sequence,
                               UsageFareContext context, long currentFareCents,
                               TransitProgressOutcome outcome) {
        List<FareBoundary> boundaries = boundaries(journey);
        if (sequence <= 0 || sequence > boundaries.size()) {
            throw new IllegalArgumentException("Trip progress is outside the selected route");
        }
        return price(boundaries.get(sequence - 1), context, currentFareCents, outcome);
    }

    /** Projected stop ledger when every remaining planned stop is reached. */
    public List<StopFarePoint> stopFareProgress(PersistedJourney journey) {
        return stopFareProgress(journey, UsageFareContext.regular(), 0, 0);
    }

    public List<StopFarePoint> stopFareProgress(PersistedJourney journey,
                                                UsageFareContext context,
                                                int completedUnits,
                                                long currentFareCents) {
        List<FareBoundary> boundaries = boundaries(journey);
        List<StopFarePoint> points = new ArrayList<>();
        long cumulative = currentFareCents;
        for (FareBoundary boundary : boundaries) {
            if (boundary.sequence() <= completedUnits) continue;
            StopFarePoint point = price(boundary, context, cumulative,
                    TransitProgressOutcome.REACHED);
            points.add(point);
            cumulative = point.cumulativeFareCents();
        }
        return List.copyOf(points);
    }

    public UsageFareEstimate estimate(Journey journey) {
        int units = boundaries(journey).size();
        UsageFareCalculation minimum = calculate(journey, 1);
        UsageFareCalculation maximum = calculate(journey, units);
        long distance = Math.round(journey.transitLegs().stream()
                .mapToDouble(leg -> Math.max(0, leg.distanceMetres())).sum());
        return new UsageFareEstimate(minimum.totalCents(), maximum.totalCents(),
                units, distance, properties.version());
    }

    private StopFarePoint price(FareBoundary boundary, UsageFareContext context,
                                long currentFareCents, TransitProgressOutcome outcome) {
        if (outcome != TransitProgressOutcome.REACHED) {
            String reason = outcome == TransitProgressOutcome.SKIPPED
                    ? "%s was skipped · no stop charge".formatted(displayStop(boundary))
                    : "Route diverted before %s · no additional charge"
                            .formatted(displayStop(boundary));
            return point(boundary, 0, 0, 0, 0, 0, 0,
                    currentFareCents, reason);
        }

        UsageFareProperties.ModeRule rule = properties.forService(
                boundary.mode(), boundary.lineName());
        long base = boundary.firstInLeg() ? rule.baseCents() : 0;
        long distance = boundary.distanceFareCents();
        long stop = rule.centsPerStop();
        long gross = base + distance + stop;

        long transferDiscount = 0;
        if (boundary.firstInLeg() && boundary.previousAgency() != null) {
            boolean sameOperator = Objects.equals(normalize(boundary.previousAgency()),
                    normalize(boundary.agency()));
            int percent = sameOperator
                    ? properties.transfers().sameOperatorCreditPercent()
                    : properties.transfers().crossOperatorCreditPercent();
            transferDiscount = percentage(base, percent);
        }

        long afterTransfer = gross - transferDiscount;
        int payablePercent = properties.payablePercent(context.fareCategory());
        long concessionDiscount = percentage(afterTransfer, 100 - payablePercent);
        long afterConcession = afterTransfer - concessionDiscount;

        long dailyRemaining = Math.max(0,
                properties.caps().dailyCents() - context.spentTodayCents() - currentFareCents);
        long weeklyRemaining = Math.max(0,
                properties.caps().weeklyCents() - context.spentThisWeekCents() - currentFareCents);
        long chargeable = Math.min(afterConcession, Math.min(dailyRemaining, weeklyRemaining));
        long capDiscount = afterConcession - chargeable;
        long cumulative = currentFareCents + chargeable;

        List<String> details = new ArrayList<>();
        if (base > 0) details.add("boarding %d¢".formatted(base));
        details.add("stop %d¢".formatted(stop));
        details.add("distance %d¢".formatted(distance));
        if (transferDiscount > 0) details.add("transfer −%d¢".formatted(transferDiscount));
        if (concessionDiscount > 0) {
            details.add("%s −%d¢".formatted(
                    context.fareCategory().displayName().toLowerCase(java.util.Locale.ROOT),
                    concessionDiscount));
        }
        if (capDiscount > 0) details.add("fare cap −%d¢".formatted(capDiscount));
        String description = "%s reached %s · %s · charge %d¢ · total %d¢".formatted(
                boundary.lineName(), displayStop(boundary), String.join(" + ", details),
                chargeable, cumulative);
        return point(boundary, base, distance, stop, transferDiscount,
                concessionDiscount, capDiscount, cumulative, description);
    }

    private static StopFarePoint point(FareBoundary boundary, long base, long distance,
                                       long stop, long transfer, long concession, long cap,
                                       long cumulative, String description) {
        long gross = base + distance + stop;
        long increment = gross - transfer - concession - cap;
        return new StopFarePoint(boundary.sequence(), boundary.stopName(), boundary.lineName(),
                boundary.mode(), boundary.agency(), base, distance, stop, gross, transfer,
                concession, cap, increment, cumulative, boundary.distanceMetres(), description);
    }

    private static long percentage(long cents, int percent) {
        return Math.round(cents * (percent / 100.0));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String displayStop(FareBoundary boundary) {
        return boundary.stopName() == null || boundary.stopName().isBlank()
                ? "stop %d".formatted(boundary.sequence()) : boundary.stopName();
    }

    private List<FareBoundary> boundaries(PersistedJourney journey) {
        List<FareBoundary> result = new ArrayList<>();
        int sequence = 0;
        String previousAgency = null;
        for (PersistedJourneyLeg leg : journey.getLegs()) {
            if (!isTransit(leg)) continue;
            int units = unitsFor(leg);
            UsageFareProperties.ModeRule rule = properties.forService(
                    leg.getMode(), leg.getLineName());
            long previousDistanceFare = 0;
            for (int unit = 1; unit <= units; unit++) {
                long usedDistance = Math.round(leg.getDistanceMetres() * unit / units);
                long totalDistanceFare = Math.round(
                        (usedDistance / METRES_PER_MILE) * rule.centsPerMile());
                long segmentDistance = unit == 1 ? usedDistance
                        : usedDistance - Math.round(leg.getDistanceMetres() * (unit - 1) / units);
                result.add(new FareBoundary(++sequence, stopName(leg, unit, units),
                        leg.getLineName(), leg.getMode(), leg.getAgency(), previousAgency,
                        unit == 1, totalDistanceFare - previousDistanceFare, segmentDistance));
                previousDistanceFare = totalDistanceFare;
            }
            previousAgency = leg.getAgency();
        }
        return List.copyOf(result);
    }

    private List<FareBoundary> boundaries(Journey journey) {
        List<FareBoundary> result = new ArrayList<>();
        int sequence = 0;
        String previousAgency = null;
        for (JourneyLeg leg : journey.transitLegs()) {
            int units = unitsFor(leg);
            UsageFareProperties.ModeRule rule = properties.forService(
                    leg.mode().name(), leg.lineName());
            long previousDistanceFare = 0;
            for (int unit = 1; unit <= units; unit++) {
                double legDistance = Math.max(0, leg.distanceMetres());
                long usedDistance = Math.round(legDistance * unit / units);
                long totalDistanceFare = Math.round(
                        (usedDistance / METRES_PER_MILE) * rule.centsPerMile());
                long segmentDistance = unit == 1 ? usedDistance
                        : usedDistance - Math.round(legDistance * (unit - 1) / units);
                result.add(new FareBoundary(++sequence, stopName(leg, unit, units),
                        leg.lineName(), leg.mode().name(), leg.agency(), previousAgency,
                        unit == 1, totalDistanceFare - previousDistanceFare, segmentDistance));
                previousDistanceFare = totalDistanceFare;
            }
            previousAgency = leg.agency();
        }
        return List.copyOf(result);
    }

    public String version() { return properties.version(); }
    public long dailyCapCents() { return properties.caps().dailyCents(); }
    public long weeklyCapCents() { return properties.caps().weeklyCents(); }

    public static int unitsFor(PersistedJourneyLeg leg) {
        if (leg.getStopCount() != null && leg.getStopCount() > 0) return leg.getStopCount();
        return Math.max(1, leg.decodedWaypoints().size() - 1);
    }

    private static int unitsFor(JourneyLeg leg) {
        if (leg.stopCount() != null && leg.stopCount() > 0) return leg.stopCount();
        return Math.max(1, leg.waypoints().size() - 1);
    }

    private static String stopName(PersistedJourneyLeg leg, int unit, int totalUnits) {
        List<JourneyLeg.Waypoint> waypoints = TransitStopGeometry.ensureStopBoundaries(
                        leg.decodedWaypoints(), leg.getFromName(), leg.getToName(),
                        leg.getLineName(), leg.getStopCount()).stream()
                .filter(point -> point.name() != null && !point.name().isBlank()).toList();
        if (unit >= 0 && unit < waypoints.size()) return waypoints.get(unit).name();
        return unit == totalUnits ? leg.getToName() : null;
    }

    private static String stopName(JourneyLeg leg, int unit, int totalUnits) {
        List<JourneyLeg.Waypoint> points = TransitStopGeometry.ensureStopBoundaries(
                leg.waypoints(), leg.fromStopName(), leg.toStopName(), leg.lineName(), leg.stopCount());
        if (unit >= 0 && unit < points.size()) return points.get(unit).name();
        return unit == totalUnits ? leg.toStopName() : null;
    }

    public static boolean isTransit(PersistedJourneyLeg leg) {
        return !"WALK".equals(leg.getMode());
    }

    private record FareBoundary(int sequence, String stopName, String lineName, String mode,
                                String agency, String previousAgency, boolean firstInLeg,
                                long distanceFareCents, long distanceMetres) {}

    public record StopFarePoint(
            int sequence, String stopName, String lineName, String mode, String agency,
            long baseCents, long distanceCents, long stopCents, long grossCents,
            long transferDiscountCents, long concessionDiscountCents, long capDiscountCents,
            long fareIncrementCents, long cumulativeFareCents, long distanceMetres,
            String description) {}
}
