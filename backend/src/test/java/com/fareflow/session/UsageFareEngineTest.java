package com.fareflow.session;

import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.TransitMode;
import com.fareflow.profile.FareCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsageFareEngineTest {

    private final UsageFareEngine engine = new UsageFareEngine(new UsageFareProperties(
            "FAREFLOW_USAGE_V2",
            new UsageFareProperties.ModeRule(85, 8, 3),
            new UsageFareProperties.ModeRule(150, 12, 5),
            new UsageFareProperties.ModeRule(120, 10, 5),
            new UsageFareProperties.ModeRule(100, 8, 4),
            new UsageFareProperties.ModeRule(90, 8, 3),
            new UsageFareProperties.ModeRule(175, 14, 6),
            new UsageFareProperties.TransferRules(100, 50),
            new UsageFareProperties.FareCaps(1_200, 6_000),
            new UsageFareProperties.RiderDiscounts(75, 50, 50)));

    @Test
    void pricesBaseDistanceAndStopsInIntegerCents() {
        UsageFareEstimate estimate = engine.estimate(journey(12));

        assertThat(estimate.minimumCents()).isEqualTo(90);
        assertThat(estimate.maximumCents()).isEqualTo(105);
        assertThat(estimate.plannedStops()).isEqualTo(4);
        assertThat(estimate.plannedDistanceMetres()).isEqualTo(1_609);
    }

    @Test
    void aServiceDelayNeverRaisesTheFare() {
        UsageFareEstimate onTime = engine.estimate(journey(12));
        UsageFareEstimate delayed = engine.estimate(journey(95));

        assertThat(delayed.minimumCents()).isEqualTo(onTime.minimumCents());
        assertThat(delayed.maximumCents()).isEqualTo(onTime.maximumCents());
    }

    @Test
    void fareChangesOnlyAtCompletedStopBoundaries() {
        Journey journey = journey(12);

        assertThat(engine.calculate(journey, 0).totalCents()).isZero();
        assertThat(engine.calculate(journey, 1).totalCents()).isEqualTo(90);
        assertThat(engine.calculate(journey, 2).totalCents()).isEqualTo(95);
        assertThat(engine.calculate(journey, 3).totalCents()).isEqualTo(100);
        assertThat(engine.calculate(journey, 4).totalCents()).isEqualTo(105);
    }

    @Test
    void usesExpressPricingWhenTheProviderLabelsABusExpress() {
        Journey standard = journey(12);
        JourneyLeg standardLeg = standard.transitLegs().getFirst();
        JourneyLeg expressLeg = new JourneyLeg(
                standardLeg.mode(), standardLeg.agency(), standardLeg.lineCode(),
                "192 toward New York Express", standardLeg.fromStopCode(),
                standardLeg.fromStopName(), standardLeg.toStopCode(), standardLeg.toStopName(),
                standardLeg.durationMinutes(), standardLeg.waitMinutes(),
                standardLeg.distanceMetres(), standardLeg.waypoints(),
                standardLeg.departureTime(), standardLeg.arrivalTime(),
                standardLeg.realtime(), standardLeg.stopCount());
        Journey express = new Journey("EXPRESS", standard.originName(), standard.destinationName(),
                List.of(expressLeg), standard.dataSource());

        assertThat(engine.calculate(express, 1).totalCents()).isEqualTo(158);
        assertThat(engine.calculate(express, 1).totalCents())
                .isGreaterThan(engine.calculate(standard, 1).totalCents());
    }

    @Test
    void waitingNinetyFiveMinutesAtTheSameStopStillCostsExactlyTheSame() {
        UsageFareCalculation onTimeAtStopTwo = engine.calculate(journey(12), 2);
        UsageFareCalculation delayedAtStopTwo = engine.calculate(journey(95), 2);

        assertThat(delayedAtStopTwo.totalCents()).isEqualTo(onTimeAtStopTwo.totalCents());
        assertThat(delayedAtStopTwo.stops()).isEqualTo(2);
    }

    @Test
    void appliesSameOperatorTransferCreditBeforeTheSecondStopCharge() {
        Journey journey = transferJourney("Test Transit", "Test Transit");

        UsageFareCalculation fare = engine.calculate(journey, 2);

        assertThat(fare.totalCents()).isEqualTo(91);
        assertThat(fare.transferDiscountCents()).isEqualTo(85);
    }

    @Test
    void appliesRiderDiscountAfterTransferRules() {
        UsageFareContext student = new UsageFareContext(FareCategory.STUDENT, 0, 0);

        UsageFareCalculation fare = engine.calculate(journey(12), 1, student);

        assertThat(fare.totalCents()).isEqualTo(67);
        assertThat(fare.concessionDiscountCents()).isEqualTo(23);
    }

    @Test
    void dailyCapLimitsOnlyTheAmountStillRemaining() {
        UsageFareContext nearCap = new UsageFareContext(FareCategory.REGULAR, 1_180, 2_000);

        UsageFareCalculation fare = engine.calculate(journey(12), 1, nearCap);

        assertThat(fare.totalCents()).isEqualTo(20);
        assertThat(fare.capDiscountCents()).isEqualTo(70);
    }

    private static Journey journey(int rideMinutes) {
        JourneyLeg bus = new JourneyLeg(
                TransitMode.BUS, "Test Transit", "B62", "Bus 62", "A", "NJIT",
                "B", "Newark Penn", rideMinutes, 3, 1_609.344,
                List.of(
                        new JourneyLeg.Waypoint("NJIT", 40.742, -74.178),
                        new JourneyLeg.Waypoint("Stop 2", 40.739, -74.174),
                        new JourneyLeg.Waypoint("Stop 3", 40.737, -74.169),
                        new JourneyLeg.Waypoint("Stop 4", 40.736, -74.166),
                        new JourneyLeg.Waypoint("Newark Penn", 40.735, -74.164)),
                Instant.parse("2026-08-22T12:00:00Z"),
                Instant.parse("2026-08-22T12:12:00Z"), false, 4);
        return new Journey("B62:A:B", "NJIT", "Newark Penn", List.of(bus),
                Journey.DataSource.GTFS_SCHEDULE);
    }

    private static Journey transferJourney(String firstAgency, String secondAgency) {
        JourneyLeg first = new JourneyLeg(
                TransitMode.BUS, firstAgency, "B1", "Route B1", "A", "A", "B", "B",
                5, 0, 0, List.of(new JourneyLeg.Waypoint("A", 40.0, -74.0),
                        new JourneyLeg.Waypoint("B", 40.1, -74.0)),
                null, null, false, 1);
        JourneyLeg second = new JourneyLeg(
                TransitMode.BUS, secondAgency, "B2", "Route B2", "B", "B", "C", "C",
                5, 0, 0, List.of(new JourneyLeg.Waypoint("B", 40.1, -74.0),
                        new JourneyLeg.Waypoint("C", 40.2, -74.0)),
                null, null, false, 1);
        return new Journey("transfer", "A", "C", List.of(first, second),
                Journey.DataSource.GTFS_SCHEDULE);
    }
}
