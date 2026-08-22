package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo mode arrives personalized.
 *
 * <p>A demo whose user is stuck on step one of onboarding demonstrates the
 * onboarding, not the product. The seeded rider therefore has a finished profile,
 * and this suite is what keeps that true: it asserts the seed, and then asserts
 * that the personalized surfaces actually read from it.
 */
class PersonalizedDemoIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("the demo rider arrives with a completed profile")
    void demoProfileIsSeeded() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted", is(true)))
                .andExpect(jsonPath("$.onboardingCompletedAt", is(notNullValue())))
                .andExpect(jsonPath("$.defaultContextProfile", is("BALANCED")))
                .andExpect(jsonPath("$.weeklyCommuteFrequency", is("THREE_TO_FOUR_DAYS")))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)))
                .andExpect(jsonPath("$.commuteKind", is("WORK")))
                .andExpect(jsonPath("$.passPreference", is("PAY_PER_RIDE")))
                .andExpect(jsonPath("$.hasTypicalCommute", is(true)))
                .andExpect(jsonPath("$.typicalOrigin.name", is("Newark")))
                .andExpect(jsonPath("$.typicalDestination.name", is("Manhattan")))
                .andExpect(jsonPath("$.preferredModes[*].id", hasItem("SUBWAY")));
    }

    @Test
    @DisplayName("the demo rider is never sent to onboarding")
    void demoUserSkipsOnboarding() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ameer Demo")))
                .andExpect(jsonPath("$.onboardingCompleted", is(true)));
    }

    @Test
    @DisplayName("the demo commute is a resolved place, so Plan can route it immediately")
    void demoCommuteHasCoordinates() throws Exception {
        String response = mockMvc.perform(get("/api/profile"))
                .andReturn().getResponse().getContentAsString();
        var profile = objectMapper.readTree(response);

        double lat = profile.get("typicalOrigin").get("latitude").asDouble();
        double lon = profile.get("typicalOrigin").get("longitude").asDouble();

        // The saved commute plans as a real journey, without a second geocode.
        mockMvc.perform(get("/api/journeys")
                        .param("from", profile.get("typicalOrigin").get("name").asText())
                        .param("to", profile.get("typicalDestination").get("name").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));

        org.assertj.core.api.Assertions.assertThat(lat).isBetween(40.0, 41.0);
        org.assertj.core.api.Assertions.assertThat(lon).isBetween(-75.0, -73.0);
    }

    // ---------------- the default stance reaches the engine ----------------

    @Test
    @DisplayName("changing the saved default changes how routes are scored")
    void savedDefaultDrivesScoring() throws Exception {
        // Before: the seeded default is BALANCED.
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(jsonPath("$.profile.id", is("BALANCED")))
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.45)));

        updateDefaultStance("SAVE_MONEY");

        // After: the same request, with no stance parameter, now leans on cost.
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(jsonPath("$.profile.id", is("SAVE_MONEY")))
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.75)));

        // But saying "I'm in a rush" about this one trip still wins.
        mockMvc.perform(get("/api/journeys").param("from", "Philadelphia").param("to", "Manhattan")
                        .param("profile", "RUSH"))
                .andExpect(jsonPath("$.profile.id", is("RUSH")))
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.75)));
    }

    // ---------------- personalized insights ----------------

    @Test
    @DisplayName("insights explain the rider's own commute rate, budget buffer, and pace")
    void personalizedInsights() throws Exception {
        // Two PATH trips at $3.00 each, so the average fare is a known number.
        takeSeededTrip();
        takeSeededTrip();

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalization", is(notNullValue())))
                .andExpect(jsonPath("$.personalization.commuteFrequency", is("THREE_TO_FOUR_DAYS")))
                .andExpect(jsonPath("$.personalization.commuteDaysPerWeek", is(3)))
                .andExpect(jsonPath("$.personalization.typicalOriginName", is("Newark")))
                .andExpect(jsonPath("$.personalization.typicalDestinationName", is("Manhattan")))
                // $3.00 average x 3 commuting days x 2 trips a day = $18.00.
                .andExpect(jsonPath("$.personalization.projectedWeeklySpendCents", is(1800)))
                // $50.00 budget - $18.00 projected = $32.00 of buffer.
                .andExpect(jsonPath("$.personalization.budgetBufferCents", is(3200)))
                .andExpect(jsonPath("$.personalization.notes", hasItem(containsString("3–4 days a week"))))
                .andExpect(jsonPath("$.personalization.notes", hasItem(containsString("$18.00"))))
                .andExpect(jsonPath("$.personalization.notes", hasItem(containsString("$32.00"))));
    }

    @Test
    @DisplayName("history exposes only stored completed-trip facts for chart cross-filtering")
    void historyIncludesRealChartObservations() throws Exception {
        takeSeededTrip();

        mockMvc.perform(get("/api/insights/history").param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations.length()", is(1)))
                .andExpect(jsonPath("$.observations[0].provider", is("PATH")))
                .andExpect(jsonPath("$.observations[0].providerName", is("PATH")))
                .andExpect(jsonPath("$.observations[0].mode", is("SUBWAY")))
                .andExpect(jsonPath("$.observations[0].fareCents", is(300)))
                .andExpect(jsonPath("$.observations[0].durationMinutes", greaterThan(0)))
                .andExpect(jsonPath("$.observations[0].tripDate", is(notNullValue())))
                .andExpect(jsonPath("$.observations[0].bucketDate", is(notNullValue())))
                .andExpect(jsonPath("$.observations[0].savedCents", is(notNullValue())))
                .andExpect(jsonPath("$.observations[0].distanceMetres", is(nullValue())));
    }

    @Test
    @DisplayName("a projection never comes in under what has already been spent")
    void projectionIsFlooredAtActualSpend() throws Exception {
        // Ten trips is well past a 3-day commute pattern; the projection has to
        // acknowledge the money that has actually left the ledger.
        for (int i = 0; i < 10; i++) {
            takeSeededTrip();
        }

        String response = mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var insights = objectMapper.readTree(response);
        long spent = insights.get("spentCents").asLong();
        long projected = insights.get("personalization").get("projectedWeeklySpendCents").asLong();

        org.assertj.core.api.Assertions.assertThat(projected).isGreaterThanOrEqualTo(spent);
        org.assertj.core.api.Assertions.assertThat(spent).isGreaterThan(0);
    }

    @Test
    @DisplayName("with no trips yet there is no projection, only the stated frequency")
    void noProjectionWithoutTrips() throws Exception {
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalization.commuteDaysPerWeek", is(3)))
                // Nothing to average, so nothing is projected. Not zero -- absent.
                .andExpect(jsonPath("$.personalization.projectedWeeklySpendCents", is(nullValue())))
                .andExpect(jsonPath("$.personalization.budgetBufferCents", is(nullValue())));
    }

    @Test
    @DisplayName("clearing the budget turns the buffer into a prompt, not a $0.00 figure")
    void noBudgetProducesAPrompt() throws Exception {
        takeSeededTrip();
        clearBudget();

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(nullValue())))
                .andExpect(jsonPath("$.remainingCents", is(nullValue())))
                .andExpect(jsonPath("$.personalization.budgetBufferCents", is(nullValue())))
                .andExpect(jsonPath("$.personalization.notes",
                        hasItem(containsString("Set a weekly budget"))));
    }

    @Test
    @DisplayName("a rider already holding a pass is not advised to buy one")
    void passAdviceIsSkippedForPassHolders() throws Exception {
        for (int i = 0; i < 6; i++) {
            takeSeededTrip();
        }

        Map<String, Object> answers = demoAnswers();
        answers.put("passPreference", "MONTHLY_PASS");
        putProfile(answers);

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalization.suggestedPassCode", is(nullValue())));
    }

    @Test
    @DisplayName("at the seeded fares no pass wins, and FareFlow declines to suggest one")
    void noPassIsSuggestedWhenPayingPerRideIsCheaper() throws Exception {
        for (int i = 0; i < 4; i++) {
            takeSeededTrip();
        }

        Map<String, Object> answers = demoAnswers();
        answers.put("weeklyCommuteFrequency", "FIVE_PLUS_DAYS");
        putProfile(answers);

        // Five days of PATH at $3.00 is $30.00 a week. The cheapest PATH pass
        // works out to $33.13 a week. A recommender that suggested it anyway
        // would be selling, not advising.
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalization.commuteDaysPerWeek", is(5)))
                .andExpect(jsonPath("$.personalization.suggestedPassCode", is(nullValue())))
                .andExpect(jsonPath("$.personalization.suggestedPassSavingsCents", is(nullValue())));
    }

    @Test
    @DisplayName("an expensive daily commute does surface a pass, with the weekly saving stated")
    void passAdviceAppearsWhenItActuallySaves() throws Exception {
        // A PATH route priced at $9.00 -- five days of it is $90.00 a week, which
        // a $142.00 30-day pass ($33.13 a week) comfortably beats.
        long routeId = givenRoute("PATH", "SUBWAY", 900);
        for (int i = 0; i < 3; i++) {
            takeTrip(routeId);
        }

        Map<String, Object> answers = demoAnswers();
        answers.put("weeklyCommuteFrequency", "FIVE_PLUS_DAYS");
        putProfile(answers);

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalization.commuteDaysPerWeek", is(5)))
                .andExpect(jsonPath("$.personalization.suggestedPassCode", is("PATH_30DAY")))
                // $90.00 a week of fares less $33.13 of pass = $56.87.
                .andExpect(jsonPath("$.personalization.suggestedPassSavingsCents", is(5687)))
                .andExpect(jsonPath("$.personalization.notes",
                        hasItem(containsString("could save about $56.87"))));
    }

    // ---------------- helpers ----------------

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** Route 2 is the seeded PATH service, Newark to Manhattan, $3.00. */
    private void takeSeededTrip() throws Exception {
        takeTrip(2);
    }

    private void takeTrip(long routeId) throws Exception {
        mockMvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("routeId", routeId, "selectedLabel", "BEST_VALUE"))))
                .andExpect(status().isCreated());
    }

    /**
     * A route at a fare the seed does not contain. Inserted directly because the
     * catalogue is deliberately read-only over HTTP — there is no endpoint that
     * lets anyone invent a fare, which is the property being preserved here.
     */
    private long givenRoute(String provider, String mode, long fareCents) {
        return jdbc.queryForObject("""
                INSERT INTO transit_routes (origin, destination, provider, mode,
                                            duration_minutes, fare_cents, transfers)
                VALUES ('Newark', 'Manhattan', ?, ?, 40, ?, 0)
                RETURNING id""", Long.class, provider, mode, fareCents);
    }

    private void updateDefaultStance(String stance) throws Exception {
        Map<String, Object> answers = demoAnswers();
        answers.put("defaultContextProfile", stance);
        putProfile(answers);
    }

    private void clearBudget() throws Exception {
        Map<String, Object> answers = demoAnswers();
        answers.put("weeklyBudgetCents", null);
        putProfile(answers);
    }

    private void putProfile(Map<String, Object> answers) throws Exception {
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isOk());
    }

    /** The seeded demo profile, as the settings page would send it back. */
    private static Map<String, Object> demoAnswers() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("defaultContextProfile", "BALANCED");
        body.put("weeklyCommuteFrequency", "THREE_TO_FOUR_DAYS");
        body.put("weeklyBudgetCents", 5000);
        body.put("commuteKind", "WORK");
        body.put("typicalOrigin", place("Newark", 40.735657, -74.164306, "static:newark"));
        body.put("typicalDestination", place("Manhattan", 40.758, -73.9855, "static:manhattan"));
        body.put("passPreference", "PAY_PER_RIDE");
        body.put("preferredModes", List.of("TRAIN", "SUBWAY", "BUS"));
        return body;
    }

    private static Map<String, Object> place(String name, double lat, double lon, String placeId) {
        Map<String, Object> place = new LinkedHashMap<>();
        place.put("name", name);
        place.put("latitude", lat);
        place.put("longitude", lon);
        place.put("providerPlaceId", placeId);
        return place;
    }
}
