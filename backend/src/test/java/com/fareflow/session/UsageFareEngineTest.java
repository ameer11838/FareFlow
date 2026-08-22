package com.fareflow.session;

import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.TransitMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsageFareEngineTest {

    private final UsageFareEngine engine = new UsageFareEngine(new UsageFareProperties(
            "FAREFLOW_USAGE_V1",
            new UsageFareProperties.ModeRule(85, 8, 3),
            new UsageFareProperties.ModeRule(120, 10, 5),
            new UsageFareProperties.ModeRule(100, 8, 4),
            new UsageFareProperties.ModeRule(90, 8, 3),
            new UsageFareProperties.ModeRule(175, 14, 6)));

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
    void waitingNinetyFiveMinutesAtTheSameStopStillCostsExactlyTheSame() {
        UsageFareCalculation onTimeAtStopTwo = engine.calculate(journey(12), 2);
        UsageFareCalculation delayedAtStopTwo = engine.calculate(journey(95), 2);

        assertThat(delayedAtStopTwo.totalCents()).isEqualTo(onTimeAtStopTwo.totalCents());
        assertThat(delayedAtStopTwo.stops()).isEqualTo(2);
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
}
