package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The acceptance test, driven entirely over HTTP in demo mode.
 *
 * <p>Note that no request carries a userId anywhere: the server resolves identity
 * itself. That is the property that makes the demo safe to expose.
 */
class EndToEndFlowIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("full flow: budget, search, take a trip, cancel, reconcile at every step")
    void fullFlow() throws Exception {
        // 1. The demo identity exists and is resolved server-side.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ameer Demo")));

        // 2. Set a $50.00 weekly budget on the caller's own account.
        mockMvc.perform(patch("/api/users/me/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("weeklyBudgetCents", 5000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)));

        // 3. Search Newark -> Manhattan. PATH should be Best Value.
        String search = mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.options[0].provider", is("PATH")))
                .andReturn().getResponse().getContentAsString();

        long pathRouteId = objectMapper.readTree(search).get("options").get(0).get("routeId").asLong();

        // 4. Take PATH. No userId in the body -- there is nowhere to put one.
        long tripId = objectMapper.readTree(
                        mockMvc.perform(post("/api/trips")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(Map.of(
                                                "routeId", pathRouteId,
                                                "selectedLabel", "BEST_VALUE"))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.fareCents", is(300)))
                                .andExpect(jsonPath("$.savedVersusFastestCents", is(325)))
                                .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        // 5. Dashboard: spent $3.00, remaining $47.00, 1 trip, saved $3.25.
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spentCents", is(300)))
                .andExpect(jsonPath("$.remainingCents", is(4700)))
                .andExpect(jsonPath("$.tripCount", is(1)))
                .andExpect(jsonPath("$.savedVersusFastestCents", is(325)));

        // 6. Ledger holds exactly one charge.
        mockMvc.perform(get("/api/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type", is("TRIP_CHARGE")))
                .andExpect(jsonPath("$.content[0].amountCents", is(-300)));

        // 7. Wallet is a projection of the same ledger, not a second balance.
        mockMvc.perform(get("/api/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalanceCents", is(4700)))
                .andExpect(jsonPath("$.spentThisWeekCents", is(300)))
                .andExpect(jsonPath("$.recentActivity", hasSize(1)));

        // 8. Insights derive from the same trips.
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spentCents", is(300)))
                .andExpect(jsonPath("$.tripCount", is(1)))
                .andExpect(jsonPath("$.averageFareCents", is(300)))
                .andExpect(jsonPath("$.spendingByProvider", hasSize(1)))
                .andExpect(jsonPath("$.spendingByProvider[0].provider", is("PATH")));

        // 9. Cancel the trip.
        mockMvc.perform(post("/api/trips/{id}/cancel", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        // 10. Dashboard back to zero; savings no longer computable.
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spentCents", is(0)))
                .andExpect(jsonPath("$.tripCount", is(0)))
                .andExpect(jsonPath("$.savedVersusFastestCents", is(nullValue())));

        // 11. Both ledger rows survive. Nothing was deleted.
        mockMvc.perform(get("/api/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].type", is("REFUND")))
                .andExpect(jsonPath("$.content[1].type", is("TRIP_CHARGE")));

        // 12. Cancelling again conflicts.
        mockMvc.perform(post("/api/trips/{id}/cancel", tripId))
                .andExpect(status().isConflict());

        // 13. The trip remains in history.
        mockMvc.perform(get("/api/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status", is("CANCELLED")));
    }

    @Test
    @DisplayName("budget pressure shifts the returned weights toward cost")
    void budgetPressureShiftsWeights() throws Exception {
        mockMvc.perform(patch("/api/users/me/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("weeklyBudgetCents", 1000))))
                .andExpect(status().isOk());

        // Spend $6.25 of a $10.00 budget -> pressure 0.625.
        mockMvc.perform(post("/api/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "routeId", 1, "selectedLabel", "FASTEST"))))
                .andExpect(status().isCreated());

        long userId = demoUser().getId();
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark")
                        .param("destination", "Manhattan")
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightsUsed.source", is("BUDGET_ADJUSTED")))
                // shift = 0.40 * 0.625 * 0.45 = 0.1125
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.5625)))
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.3375)));
    }
}
