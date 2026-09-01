package com.fareflow.journey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransitStopGeometryTest {

    @Test
    void retrofitsProviderGeometryThatOnlyNamedItsEndpoints() {
        List<JourneyLeg.Waypoint> geometry = List.of(
                new JourneyLeg.Waypoint("Boarding Stop", 40.0, -74.0),
                new JourneyLeg.Waypoint("", 40.01, -73.99),
                new JourneyLeg.Waypoint("Arrival Stop", 40.02, -73.98));

        List<JourneyLeg.Waypoint> result = TransitStopGeometry.ensureStopBoundaries(
                geometry, "Boarding Stop", "Arrival Stop", "105", 4);

        assertThat(result.stream().filter(point -> !point.name().isBlank())
                .map(JourneyLeg.Waypoint::name))
                .containsExactly("Boarding Stop", "105 · stop 1 of 4", "105 · stop 2 of 4",
                        "105 · stop 3 of 4", "Arrival Stop");
    }

    @Test
    void preservesACompleteGtfsStopSequence() {
        List<JourneyLeg.Waypoint> stops = List.of(
                new JourneyLeg.Waypoint("A", 40.0, -74.0),
                new JourneyLeg.Waypoint("B", 40.01, -73.99),
                new JourneyLeg.Waypoint("C", 40.02, -73.98));

        assertThat(TransitStopGeometry.ensureStopBoundaries(stops, "A", "C", "Rail", 2))
                .isEqualTo(stops);
    }
}
