package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the context profiles change the recommendation end to end over HTTP, and
 * that the server — not the client — owns the weights.
 */
class ProfileApiIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("the profile catalog is served to clients")
    void profilesEndpoint() throws Exception {
        mockMvc.perform(get("/api/recommendations/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id", is("BALANCED")))
                .andExpect(jsonPath("$[?(@.id == 'RUSH')].timePriority", is(java.util.List.of(0.75))));
    }

    @Test
    @DisplayName("BALANCED recommends PATH")
    void balancedPicksPath() throws Exception {
        mockMvc.perform(search("BALANCED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider", is("PATH")))
                .andExpect(jsonPath("$.options[0].recommended", is(true)))
                .andExpect(jsonPath("$.profile.id", is("BALANCED")))
                // No note: the stance did not change anything.
                .andExpect(jsonPath("$.contextNote", is(nullValue())));
    }

    @Test
    @DisplayName("RUSH recommends NJ Transit and explains why")
    void rushPicksNjTransit() throws Exception {
        mockMvc.perform(search("RUSH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider", is("NJ_TRANSIT")))
                .andExpect(jsonPath("$.options[0].recommended", is(true)))
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.75)))
                .andExpect(jsonPath("$.weightsUsed.source", is("PROFILE")))
                .andExpect(jsonPath("$.contextNote", notNullValue()))
                .andExpect(jsonPath("$.contextNote", containsString("NJ Transit")))
                .andExpect(jsonPath("$.contextNote", containsString("$3.25")))
                .andExpect(jsonPath("$.contextNote", containsString("16 minutes sooner")));
    }

    @Test
    @DisplayName("SAVE_MONEY shifts the weights toward cost but stays value-aware")
    void saveMoneyShiftsTowardCost() throws Exception {
        // On the seeded data the cheapest route saves $0.10 and costs 17 extra
        // minutes, so PATH still wins even at a 0.75 cost weight. The engine
        // declining to recommend the bus here is correct, not a bug -- and there is
        // no context note precisely because the outcome did not change.
        mockMvc.perform(search("SAVE_MONEY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.75)))
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.15)))
                .andExpect(jsonPath("$.weightsUsed.source", is("PROFILE")))
                .andExpect(jsonPath("$.options[0].provider", is("PATH")))
                .andExpect(jsonPath("$.contextNote", is(nullValue())));
    }

    @Test
    @DisplayName("SAVE_MONEY reorders the alternatives even when the winner is unchanged")
    void saveMoneyReordersAlternatives() throws Exception {
        // Under BALANCED, NJ Transit and the bus tie at 0.450 and the bus ranks
        // second on the fare tie-break. Under SAVE_MONEY the bus pulls clearly ahead.
        mockMvc.perform(search("SAVE_MONEY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[1].provider", is("NYC_BUS")))
                .andExpect(jsonPath("$.options[2].provider", is("NJ_TRANSIT")));
    }

    @Test
    @DisplayName("profile names are case-insensitive")
    void caseInsensitiveProfile() throws Exception {
        mockMvc.perform(search("rush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider", is("NJ_TRANSIT")));
    }

    @Test
    @DisplayName("omitting the profile defaults to BALANCED")
    void defaultProfile() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id", is("BALANCED")));
    }

    @Test
    @DisplayName("an unknown profile is rejected with 400 listing the valid options")
    void unknownProfileRejected() throws Exception {
        mockMvc.perform(search("MAKE_ME_RICH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", containsString("SAVE_MONEY")));
    }

    @Test
    @DisplayName("raw weights sent by a client are ignored — the server owns the numbers")
    void rawWeightsAreNotAccepted() throws Exception {
        // Unknown query parameters must not influence scoring in any way.
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark")
                        .param("destination", "Manhattan")
                        .param("costPriority", "0.99")
                        .param("timePriority", "0.01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.45)))
                .andExpect(jsonPath("$.options[0].provider", is("PATH")));
    }

    @Test
    @DisplayName("routes carry trade-off deltas against the fastest option")
    void comparisonDeltas() throws Exception {
        mockMvc.perform(search("BALANCED"))
                .andExpect(status().isOk())
                // PATH vs NJ Transit: $3.25 cheaper, 16 minutes slower.
                .andExpect(jsonPath("$.options[0].vsFastest.fareDeltaCents", is(-325)))
                .andExpect(jsonPath("$.options[0].vsFastest.minutesDelta", is(16)))
                .andExpect(jsonPath("$.options[0].vsFastest.referenceProvider", is("NJ Transit")))
                // The best-value route has no comparison against itself.
                .andExpect(jsonPath("$.options[0].vsBestValue", is(nullValue())));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder search(String profile) {
        return get("/api/recommendations")
                .param("origin", "Newark")
                .param("destination", "Manhattan")
                .param("profile", profile);
    }
}
