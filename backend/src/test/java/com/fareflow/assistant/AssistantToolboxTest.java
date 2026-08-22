package com.fareflow.assistant;

import com.fareflow.discovery.dto.JourneyOptionDto;
import com.fareflow.discovery.dto.JourneySearchResponse;
import com.fareflow.location.LocationCandidate;
import com.fareflow.recommendation.dto.ProfileDto;
import com.fareflow.recommendation.dto.WeightsDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantToolboxTest {

    @Test
    void fareLimitIsAppliedByJavaAndExcludesUnknownFares() {
        JourneySearchResponse source = response(List.of(
                option("fast", 725L, true),
                option("under-five", 475L, false),
                option("unknown", null, false)));

        JourneySearchResponse filtered = AssistantToolbox.applyFareLimit(source, 500);

        assertThat(filtered.options()).extracting(JourneyOptionDto::journeyId)
                .containsExactly("under-five");
        assertThat(filtered.options().getFirst().recommended()).isTrue();
        assertThat(filtered.notices()).contains("Only routes at or below the requested fare limit are shown.");
    }

    @Test
    void fareLimitReturnsNoRouteInsteadOfInventingAPrice() {
        JourneySearchResponse source = response(List.of(
                option("too-expensive", 600L, true),
                option("unpriced", null, false)));

        JourneySearchResponse filtered = AssistantToolbox.applyFareLimit(source, 500);

        assertThat(filtered.options()).isEmpty();
        assertThat(filtered.notices()).contains("No priced route met the requested fare limit.");
    }

    private static JourneySearchResponse response(List<JourneyOptionDto> options) {
        LocationCandidate origin = new LocationCandidate(
                null, "Origin", "Origin", "NJ", "US", 40, -74, "PLACE", "TEST");
        LocationCandidate destination = new LocationCandidate(
                null, "Destination", "Destination", "NY", "US", 41, -73, "PLACE", "TEST");
        return new JourneySearchResponse(
                origin, destination,
                new ProfileDto("BALANCED", "Balanced", "balanced", .45, .45, .1),
                new WeightsDto(.45, .45, .1, "DEFAULT", 0), null,
                "Routes", null, options, List.of("Provider data only"));
    }

    private static JourneyOptionDto option(String id, Long fareCents, boolean recommended) {
        return new JourneyOptionDto(
                id, id, 30, 5, 0, fareCents,
                fareCents == null ? "UNKNOWN" : "EXACT", "TEST", List.of(), List.of(),
                recommended, .2, "Deterministic explanation", "TEST", List.of());
    }
}
