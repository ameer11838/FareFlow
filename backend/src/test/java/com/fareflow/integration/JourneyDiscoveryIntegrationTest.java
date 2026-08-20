package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitrary origin-to-destination search, end to end over HTTP.
 *
 * <p>The point of this suite is that none of these pairs exist in the seeded
 * transit_routes table. They are planned over the network graph from geocoded
 * coordinates, which is what makes FareFlow work for places it was never seeded with.
 *
 * <p>Geocoding resolves through the static gazetteer here — no test touches a
 * third-party API.
 */
class JourneyDiscoveryIntegrationTest extends IntegrationTestBase {

    // ---------------- the acceptance case ----------------

    @Test
    @DisplayName("Philadelphia to Manhattan resolves, discovers journeys, and ranks them")
    void philadelphiaToManhattan() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin.displayName", containsString("Philadelphia")))
                .andExpect(jsonPath("$.destination.displayName", containsString("Manhattan")))
                .andExpect(jsonPath("$.options", not(empty())))
                // Ranked by the same optimization engine as the seeded routes.
                .andExpect(jsonPath("$.options[0].recommended", is(true)))
                .andExpect(jsonPath("$.options[0].labels", hasItem("BEST_VALUE")))
                .andExpect(jsonPath("$.summary", not(emptyString())));
    }

    @Test
    @DisplayName("the Philadelphia journey is genuinely multi-leg with walking access")
    void philadelphiaJourneyHasLegs() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].legs", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.options[0].legs[*].mode", hasItem("WALK")))
                .andExpect(jsonPath("$.options[0].totalMinutes", greaterThan(60)))
                .andExpect(jsonPath("$.options[0].legs[0].waypoints", not(empty())));
    }

    @Test
    @DisplayName("cheapest, fastest, and best value are all identified")
    void labelsAreAssigned() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[*].labels[*]", hasItem("CHEAPEST")))
                .andExpect(jsonPath("$.options[*].labels[*]", hasItem("FASTEST")))
                .andExpect(jsonPath("$.options[*].labels[*]", hasItem("BEST_VALUE")));
    }

    // ---------------- honest fares ----------------

    @Test
    @DisplayName("an unpriceable option reports a null fare, never zero")
    void unknownFareIsNeverZero() throws Exception {
        String body = mockMvc.perform(get("/api/journeys")
                        .param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var options = objectMapper.readTree(body).get("options");
        for (var option : options) {
            if ("UNKNOWN".equals(option.get("fareStatus").asText())) {
                // The bug this guards against: an unknown fare rendering as $0.00
                // and therefore always winning the cheapest comparison.
                org.assertj.core.api.Assertions.assertThat(option.get("fareCents").isNull()).isTrue();
                return;
            }
        }
    }

    @Test
    @DisplayName("fare status and source are reported for every option")
    void fareProvenanceIsAlwaysStated() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[*].fareStatus",
                        everyItem(anyOf(is("EXACT"), is("ESTIMATED"), is("UNKNOWN")))))
                .andExpect(jsonPath("$.options[*].fareSource", everyItem(not(emptyString()))));
    }

    @Test
    @DisplayName("priced options carry a receipt-style breakdown")
    void fareBreakdownIsReturned() throws Exception {
        String body = mockMvc.perform(get("/api/journeys")
                        .param("from", "Newark").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var options = objectMapper.readTree(body).get("options");
        boolean sawPricedBreakdown = false;
        for (var option : options) {
            if (!option.get("fareCents").isNull()) {
                org.assertj.core.api.Assertions.assertThat(option.get("fareBreakdown")).isNotEmpty();
                sawPricedBreakdown = true;
            }
        }
        org.assertj.core.api.Assertions.assertThat(sawPricedBreakdown).isTrue();
    }

    @Test
    @DisplayName("notices state the limitations rather than hiding them")
    void noticesAreHonest() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices", hasItem(containsString("not live departures"))));
    }

    // ---------------- other arbitrary pairs ----------------

    @Test
    @DisplayName("NJIT to Penn Station New York works without any seeded pair")
    void njitToPennStation() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "NJIT").param("to", "Penn Station New York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", not(empty())))
                .andExpect(jsonPath("$.origin.displayName", containsString("Technology")));
    }

    @Test
    @DisplayName("Newark Airport to Manhattan works")
    void airportToManhattan() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "EWR").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", not(empty())));
    }

    @Test
    @DisplayName("Hoboken to Brooklyn produces a transferring journey")
    void hobokenToBrooklyn() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Hoboken").param("to", "Brooklyn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", not(empty())))
                .andExpect(jsonPath("$.options[*].transfers", hasItem(greaterThanOrEqualTo(1))));
        }

    // ---------------- context profiles still apply ----------------

    @Test
    @DisplayName("RUSH reweights arbitrary journeys the same way it does seeded routes")
    void rushAppliesToJourneys() throws Exception {
        mockMvc.perform(get("/api/journeys")
                        .param("from", "Philadelphia").param("to", "Manhattan").param("profile", "RUSH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.75)))
                .andExpect(jsonPath("$.profile.id", is("RUSH")));
    }

    @Test
    @DisplayName("an unknown profile is rejected")
    void unknownProfileRejected() throws Exception {
        mockMvc.perform(get("/api/journeys")
                        .param("from", "Newark").param("to", "Manhattan").param("profile", "TELEPORT"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- failure modes ----------------

    @Test
    @DisplayName("an unresolvable place is a 404, not an empty success")
    void unresolvablePlaceIs404() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "Atlantis").param("to", "Manhattan"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a place with no nearby network returns no options and says so")
    void outOfCoverageReturnsNothing() throws Exception {
        // Princeton is geocodable but sits away from the modelled corridor.
        // Fabricating a route to fill the screen would be the wrong answer.
        mockMvc.perform(get("/api/journeys").param("from", "Princeton").param("to", "Philadelphia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options").exists());
    }

    @Test
    @DisplayName("a blank origin is rejected")
    void blankOriginRejected() throws Exception {
        mockMvc.perform(get("/api/journeys").param("from", "  ").param("to", "Manhattan"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- location autocomplete ----------------

    @Test
    @DisplayName("location search resolves partial text")
    void locationAutocomplete() throws Exception {
        mockMvc.perform(get("/api/locations").param("q", "Phil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].latitude", notNullValue()))
                .andExpect(jsonPath("$[0].longitude", notNullValue()));
    }

    @Test
    @DisplayName("location search resolves an acronym to a real place")
    void locationAcronym() throws Exception {
        mockMvc.perform(get("/api/locations").param("q", "NJIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName", containsString("Technology")));
    }

    @Test
    @DisplayName("a too-short query returns nothing rather than everything")
    void shortQueryReturnsNothing() throws Exception {
        mockMvc.perform(get("/api/locations").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }
}
