package com.fareflow.journey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JourneyScopeTest {

    @Test
    void publicTransitModesAreTheOnlyRoutableModes() {
        assertThat(List.of(TransitMode.values()))
                .containsExactly(TransitMode.WALK, TransitMode.RAIL, TransitMode.SUBWAY,
                        TransitMode.LIGHT_RAIL, TransitMode.BUS, TransitMode.FERRY);
        assertThat(List.of(TransitMode.values()).stream().filter(TransitMode::isTransit))
                .containsExactly(TransitMode.RAIL, TransitMode.SUBWAY,
                        TransitMode.LIGHT_RAIL, TransitMode.BUS, TransitMode.FERRY);
    }

    @Test
    void walkingOnlyJourneyIsRejected() {
        assertThatThrownBy(() -> journey(List.of(walk("Home", "Campus"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain public transit");
    }

    @Test
    void walkingCanConnectAccessTransferAndEgress() {
        Journey journey = journey(List.of(
                walk("Home", "Station A"),
                ride(TransitMode.BUS, "Station A", "Station B"),
                walk("Station B", "Station C"),
                ride(TransitMode.FERRY, "Station C", "Terminal"),
                walk("Terminal", "Office")));

        assertThat(journey.walkingMinutes()).isEqualTo(15);
        assertThat(journey.transfers()).isEqualTo(1);
    }

    @Test
    void adjacentWalkingLegsAreRejectedAsAStandaloneWalkingRouteFragment() {
        assertThatThrownBy(() -> journey(List.of(
                ride(TransitMode.SUBWAY, "A", "B"),
                walk("B", "C"),
                walk("C", "D"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Walking legs must only connect");
    }

    private static Journey journey(List<JourneyLeg> legs) {
        return new Journey("scope", "Home", "Office", legs, Journey.DataSource.CURATED_NETWORK);
    }

    private static JourneyLeg walk(String from, String to) {
        return JourneyLeg.walk(from, to, 5, 400, List.of());
    }

    private static JourneyLeg ride(TransitMode mode, String from, String to) {
        return new JourneyLeg(mode, "TEST_TRANSIT", "TEST_LINE", "Test public transit",
                from, from, to, to, 10, 2, 2_000, List.of());
    }
}
