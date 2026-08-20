package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationApiIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Newark -> Manhattan returns three labelled routes with PATH as Best Value")
    void newarkToManhattan() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark")
                        .param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", hasSize(3)))
                // Ordered best-value first.
                .andExpect(jsonPath("$.options[0].provider", is("PATH")))
                .andExpect(jsonPath("$.options[0].labels[0]", is("BEST_VALUE")))
                .andExpect(jsonPath("$.options[0].fareCents", is(300)))
                .andExpect(jsonPath("$.options[0].durationMinutes", is(38)))
                // Jackson deserializes JSON decimals as BigDecimal, so compare in kind.
                .andExpect(jsonPath("$.options[0].score",
                        is(closeTo(new java.math.BigDecimal("0.2316"), new java.math.BigDecimal("0.0001")))))
                .andExpect(jsonPath("$.summary", containsString("PATH")));
    }

    @Test
    @DisplayName("NJ Transit is Fastest and NYC Bus is Cheapest")
    void fastestAndCheapestLabels() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark")
                        .param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[?(@.provider == 'NJ_TRANSIT')].labels[0]", is(java.util.List.of("FASTEST"))))
                .andExpect(jsonPath("$.options[?(@.provider == 'NYC_BUS')].labels[0]", is(java.util.List.of("CHEAPEST"))));
    }

    @Test
    @DisplayName("explanations carry the correct dollar and minute deltas")
    void explanations() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark")
                        .param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].explanation", containsString("$3.25")))
                .andExpect(jsonPath("$.options[0].explanation", containsString("16 minutes")));
    }

    @Test
    @DisplayName("the weights used are returned so the decision can be replayed")
    void weightsAreReturned() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark")
                        .param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.45)))
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.45)))
                .andExpect(jsonPath("$.weightsUsed.transferPriority", is(0.1)))
                .andExpect(jsonPath("$.weightsUsed.source", is("DEFAULT")))
                .andExpect(jsonPath("$.profile.id", is("BALANCED")));
    }

    @Test
    @DisplayName("origin and destination match case-insensitively")
    void caseInsensitive() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "  newark ")
                        .param("destination", "MANHATTAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", hasSize(3)));
    }

    @Test
    @DisplayName("an unknown pair returns 200 with an empty options array, not 404")
    void unknownPairReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Atlantis")
                        .param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", is(empty())))
                .andExpect(jsonPath("$.summary", containsString("No routes")));
    }

    @Test
    @DisplayName("a single-route pair labels that route as all three")
    void singleRoutePair() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Princeton")
                        .param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", hasSize(1)))
                .andExpect(jsonPath("$.options[0].labels", hasSize(3)))
                .andExpect(jsonPath("$.options[0].explanation", containsString("Only one route")));
    }

    @Test
    @DisplayName("a blank origin is rejected with 400 in Problem Detail form")
    void blankOriginRejected() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "   ")
                        .param("destination", "Manhattan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("a missing parameter is rejected with 400")
    void missingParameterRejected() throws Exception {
        mockMvc.perform(get("/api/recommendations").param("origin", "Newark"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("the health endpoint reports UP")
    void health() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
