package com.fareflow.session.dto;

import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.PersistedJourneyLeg;
import com.fareflow.journey.TransitStopGeometry;
import com.fareflow.session.TransitSession;
import com.fareflow.session.TransitSessionStatus;
import com.fareflow.session.TransitFareEvent;
import com.fareflow.session.UsageFareContext;
import com.fareflow.session.UsageFareEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Honest session projection: scheduled/live fields remain null unless supplied. */
public record TransitSessionResponse(
        UUID id,
        String status,
        long journeyId,
        String origin,
        String destination,
        String summary,
        String dataSource,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        boolean hasRealtimeData,
        Instant startedAt,
        Instant endedAt,
        long elapsedSeconds,
        int activeLegIndex,
        String currentLine,
        String currentAgency,
        String currentMode,
        String currentStop,
        String nextStop,
        long nextStopFareIncreaseCents,
        String transferToLine,
        int progressUnitsCompleted,
        int progressUnitsTotal,
        int completedStops,
        int plannedStops,
        long distanceTravelledMetres,
        long plannedDistanceMetres,
        String progressSource,
        long estimatedFareMinCents,
        long estimatedFareMaxCents,
        Long publishedFareCents,
        String publishedFareStatus,
        String publishedFareSource,
        long currentFareCents,
        /** Compatibility alias for existing clients; active-trip fare is exact server state. */
        long currentEstimatedFareCents,
        Long finalFareCents,
        String fareCategory,
        String fareCategoryName,
        long dailyCapCents,
        long weeklyCapCents,
        long dailyCapRemainingCents,
        long weeklyCapRemainingCents,
        long transferDiscountCents,
        long concessionDiscountCents,
        long capDiscountCents,
        List<String> fareBreakdown,
        List<FareEvent> fareEvents,
        List<StopFareProgress> stopFareProgress,
        String pricingVersion,
        boolean canAdvance,
        boolean canEnd,
        boolean canPay,
        String simulationNotice,
        List<Leg> legs
) {
    public record Leg(
            int sequence,
            String mode,
            String agency,
            String lineName,
            String fromName,
            String toName,
            int durationMinutes,
            int waitMinutes,
            double distanceMetres,
            Integer stopCount,
            Instant departureTime,
            Instant arrivalTime,
            boolean realtime,
            List<Waypoint> waypoints
    ) {
    }

    public record Waypoint(String name, double latitude, double longitude) {
    }

    public record StopFareProgress(
            int sequence,
            String stopName,
            String lineName,
            String mode,
            String state,
            long fareIncrementCents,
            long cumulativeFareCents,
            long grossCents,
            long totalDiscountCents,
            String description
    ) {
    }

    public record FareEvent(
            long id,
            int sequence,
            String eventType,
            String stopName,
            String lineName,
            String mode,
            String agency,
            long grossCents,
            long transferDiscountCents,
            long concessionDiscountCents,
            long capDiscountCents,
            long amountCents,
            long cumulativeFareCents,
            String description,
            Instant occurredAt
    ) {
    }

    public static TransitSessionResponse from(TransitSession session,
                                              UsageFareEngine fareEngine,
                                              List<TransitFareEvent> events,
                                              Instant now) {
        var journey = session.getJourney();
        UsageFareContext fareContext = new UsageFareContext(session.getFareCategory(),
                session.getSpentTodayBeforeCents(), session.getSpentWeekBeforeCents());
        ProgressPosition position = position(journey.getLegs(), session.getProgressUnitsCompleted());
        List<PersistedJourneyLeg> transit = journey.getLegs().stream()
                .filter(UsageFareEngine::isTransit)
                .toList();
        Instant scheduledDeparture = transit.stream()
                .map(PersistedJourneyLeg::getDepartureTime)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        Instant scheduledArrival = transit.reversed().stream()
                .map(PersistedJourneyLeg::getArrivalTime)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);

        Instant timerEnd = session.getEndedAt() == null ? now : session.getEndedAt();
        long elapsed = Math.max(0, Duration.between(session.getStartedAt(), timerEnd).toSeconds());
        boolean active = session.getStatus().isActive();
        int completed = session.getProgressUnitsCompleted();
        List<UsageFareEngine.StopFarePoint> futurePoints = fareEngine.stopFareProgress(
                journey, fareContext, completed, session.getCurrentFareCents());
        long nextIncrease = active && !futurePoints.isEmpty()
                ? futurePoints.getFirst().fareIncrementCents() : 0;
        List<StopFareProgress> stopProgress = new java.util.ArrayList<>();
        PersistedJourneyLeg firstTransit = transit.getFirst();
        stopProgress.add(new StopFareProgress(0, firstTransit.getFromName(),
                firstTransit.getLineName(), firstTransit.getMode(),
                completed == 0 ? "CURRENT" : "COMPLETED", 0, 0,
                0, 0, "Boarding point · charges begin only after a stop is reached"));
        events.forEach(event -> stopProgress.add(new StopFareProgress(
                event.getSequence(), event.getStopName(), event.getLineName(), event.getMode(),
                event.getEventType() == com.fareflow.session.TransitFareEventType.STOP_SKIPPED
                        ? "SKIPPED"
                        : event.getEventType() == com.fareflow.session.TransitFareEventType.ROUTE_DIVERSION
                        ? "DIVERTED"
                        : active && event.getSequence() == completed ? "CURRENT" : "COMPLETED",
                event.getAmountCents(), event.getCumulativeFareCents(), event.getGrossCents(),
                event.getTransferDiscountCents() + event.getConcessionDiscountCents()
                        + event.getCapDiscountCents(), event.getDescription())));
        futurePoints.forEach(point -> stopProgress.add(new StopFareProgress(
                point.sequence(), point.stopName(), point.lineName(), point.mode(),
                active && point.sequence() == completed + 1 ? "NEXT" : "UPCOMING",
                point.fareIncrementCents(), point.cumulativeFareCents(), point.grossCents(),
                point.transferDiscountCents() + point.concessionDiscountCents()
                        + point.capDiscountCents(), point.description())));

        long dailyRemaining = Math.max(0, fareEngine.dailyCapCents()
                - session.getSpentTodayBeforeCents() - session.getCurrentFareCents());
        long weeklyRemaining = Math.max(0, fareEngine.weeklyCapCents()
                - session.getSpentWeekBeforeCents() - session.getCurrentFareCents());
        List<FareEvent> responseEvents = events.stream().map(event -> new FareEvent(
                event.getId(), event.getSequence(), event.getEventType().name(),
                event.getStopName(), event.getLineName(), event.getMode(), event.getAgency(),
                event.getGrossCents(), event.getTransferDiscountCents(),
                event.getConcessionDiscountCents(), event.getCapDiscountCents(),
                event.getAmountCents(), event.getCumulativeFareCents(), event.getDescription(),
                event.getOccurredAt())).toList();

        return new TransitSessionResponse(
                session.getId(),
                session.getStatus().name(),
                journey.getId(),
                journey.getOriginDisplayName(),
                journey.getDestinationDisplayName(),
                journey.summary(),
                journey.getDataSource(),
                scheduledDeparture,
                scheduledArrival,
                transit.stream().anyMatch(PersistedJourneyLeg::isRealtime),
                session.getStartedAt(),
                session.getEndedAt(),
                elapsed,
                position.legIndex(),
                position.leg().getLineName(),
                position.leg().getAgency(),
                position.leg().getMode(),
                position.currentStop(),
                position.nextStop(),
                nextIncrease,
                transferLine(journey.getLegs(), position.legIndex()),
                session.getProgressUnitsCompleted(),
                session.getProgressUnitsTotal(),
                session.getCompletedStopCount(),
                session.getPlannedStopCount() == null ? session.getProgressUnitsTotal()
                        : session.getPlannedStopCount(),
                session.getDistanceTravelledMetres(),
                session.getPlannedDistanceMetres(),
                session.getProgressSource(),
                session.getEstimatedFareMinCents(),
                session.getEstimatedFareMaxCents(),
                journey.getTotalFareCents(),
                journey.getFareStatus(),
                journey.getFareSource(),
                session.getCurrentFareCents(),
                session.getCurrentFareCents(),
                session.getFinalFareCents(),
                session.getFareCategory().name(),
                session.getFareCategory().displayName(),
                fareEngine.dailyCapCents(),
                fareEngine.weeklyCapCents(),
                dailyRemaining,
                weeklyRemaining,
                session.getTransferDiscountCents(),
                session.getConcessionDiscountCents(),
                session.getCapDiscountCents(),
                events.isEmpty() ? List.of("No transit progress recorded · no charge")
                        : events.stream().map(TransitFareEvent::getDescription).toList(),
                responseEvents,
                List.copyOf(stopProgress),
                session.getPricingVersion(),
                active && session.getProgressUnitsCompleted() < session.getProgressUnitsTotal(),
                active,
                session.getStatus() == TransitSessionStatus.COMPLETED,
                "FareFlow usage pricing is a simulation. No transit agency partnership or acceptance is implied.",
                journey.getLegs().stream().map(TransitSessionResponse::leg).toList());
    }

    private static Leg leg(PersistedJourneyLeg leg) {
        return new Leg(
                leg.getSequence(), leg.getMode(), leg.getAgency(), leg.getLineName(),
                leg.getFromName(), leg.getToName(), leg.getDurationMinutes(), leg.getWaitMinutes(),
                leg.getDistanceMetres(), leg.getStopCount(), leg.getDepartureTime(),
                leg.getArrivalTime(), leg.isRealtime(), TransitStopGeometry.ensureStopBoundaries(
                        leg.decodedWaypoints(), leg.getFromName(), leg.getToName(),
                        leg.getLineName(), leg.getStopCount()).stream()
                        .map(point -> new Waypoint(point.name(), point.latitude(), point.longitude()))
                        .toList());
    }

    private static ProgressPosition position(List<PersistedJourneyLeg> legs, int completedUnits) {
        int remaining = completedUnits;
        PersistedJourneyLeg lastTransit = null;
        int lastIndex = 0;
        for (int index = 0; index < legs.size(); index++) {
            PersistedJourneyLeg leg = legs.get(index);
            if (!UsageFareEngine.isTransit(leg)) {
                continue;
            }
            lastTransit = leg;
            lastIndex = index;
            int units = UsageFareEngine.unitsFor(leg);
            if (remaining < units) {
                return new ProgressPosition(index, leg,
                        stopName(leg, remaining, false), stopName(leg, remaining + 1, true));
            }
            remaining -= units;
        }
        if (lastTransit == null) {
            throw new IllegalStateException("A transit session has no public-transit leg");
        }
        return new ProgressPosition(lastIndex, lastTransit, lastTransit.getToName(), null);
    }

    private static String stopName(PersistedJourneyLeg leg, int index, boolean next) {
        List<JourneyLeg.Waypoint> points = TransitStopGeometry.ensureStopBoundaries(
                        leg.decodedWaypoints(), leg.getFromName(), leg.getToName(),
                        leg.getLineName(), leg.getStopCount()).stream()
                .filter(point -> point.name() != null && !point.name().isBlank())
                .toList();
        if (index >= 0 && index < points.size()) {
            String name = points.get(index).name();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (index == 0) return leg.getFromName();
        if (next && index >= UsageFareEngine.unitsFor(leg)) return leg.getToName();
        return null;
    }

    private static String transferLine(List<PersistedJourneyLeg> legs, int activeLegIndex) {
        for (int index = activeLegIndex + 1; index < legs.size(); index++) {
            if (UsageFareEngine.isTransit(legs.get(index))) {
                return legs.get(index).getLineName();
            }
        }
        return null;
    }

    private record ProgressPosition(int legIndex, PersistedJourneyLeg leg,
                                    String currentStop, String nextStop) {
    }
}
