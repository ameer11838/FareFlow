package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Onboarding and the travel profile, over HTTP, with authentication on.
 *
 * <p>The suite that matters most here is the ownership one: there is no request
 * shape — parameter, header, or body field — that lets one signed-in rider read or
 * write another rider's profile. That is a property of the contract, not of a
 * check someone remembered to write.
 */
@TestPropertySource(properties = {
        "fareflow.auth.enabled=true",
        "fareflow.jwt.secret=integration-test-secret-that-is-long-enough-32!",
        "fareflow.jwt.expiration-seconds=3600",
})
class OnboardingProfileIntegrationTest extends IntegrationTestBase {

    // ---------------- a fresh account has nothing ----------------

    @Test
    @DisplayName("a new account has not completed onboarding and has no budget")
    void freshAccountIsNotOnboarded() throws Exception {
        String token = register("fresh@example.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted", is(false)))
                // Registration does not ask for a budget, so there is not one yet.
                // Null, not zero: the UI prompts instead of reporting $0.00.
                .andExpect(jsonPath("$.weeklyBudgetCents", is(nullValue())));

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted", is(false)))
                .andExpect(jsonPath("$.onboardingCompletedAt", is(nullValue())))
                .andExpect(jsonPath("$.defaultContextProfile", is("BALANCED")))
                .andExpect(jsonPath("$.weeklyCommuteFrequency", is(nullValue())))
                .andExpect(jsonPath("$.hasTypicalCommute", is(false)))
                .andExpect(jsonPath("$.preferredModes", hasSize(0)));
    }

    @Test
    @DisplayName("reading a profile does not create one")
    void readingDoesNotCreate() throws Exception {
        String token = register("read-only@example.com");

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long userId = userRepository.findByEmailIgnoreCase("read-only@example.com").orElseThrow().getId();
        org.assertj.core.api.Assertions
                .assertThat(travelProfiles.findByUserId(userId))
                .isEmpty();
    }

    // ---------------- completing onboarding ----------------

    @Test
    @DisplayName("submitting onboarding stores every answer and marks it complete")
    void completeOnboarding() throws Exception {
        String token = register("onboard@example.com");

        mockMvc.perform(putOnboarding(token, fullOnboarding()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted", is(true)))
                .andExpect(jsonPath("$.onboardingCompletedAt", is(notNullValue())))
                .andExpect(jsonPath("$.defaultContextProfile", is("SAVE_MONEY")))
                .andExpect(jsonPath("$.defaultContextProfileName", is("Save me money")))
                .andExpect(jsonPath("$.weeklyCommuteFrequency", is("THREE_TO_FOUR_DAYS")))
                .andExpect(jsonPath("$.estimatedCommuteDaysPerWeek", is(3)))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)))
                .andExpect(jsonPath("$.commuteKind", is("WORK")))
                .andExpect(jsonPath("$.passPreference", is("PAY_PER_RIDE")))
                .andExpect(jsonPath("$.preferredModes[*].id", hasItem("TRAIN")))
                .andExpect(jsonPath("$.preferredModes[*].id", hasItem("SUBWAY")));

        // And the flag is visible to the router on the next page load.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.onboardingCompleted", is(true)))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)));
    }

    @Test
    @DisplayName("the typical commute round-trips as coordinates, not as free text")
    void typicalCommutePersists() throws Exception {
        String token = register("commute@example.com");

        mockMvc.perform(putOnboarding(token, fullOnboarding())).andExpect(status().isOk());

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTypicalCommute", is(true)))
                .andExpect(jsonPath("$.typicalOrigin.name", is("Newark, NJ")))
                .andExpect(jsonPath("$.typicalOrigin.latitude", is(closeTo(40.735657, 0.000001))))
                .andExpect(jsonPath("$.typicalOrigin.longitude", is(closeTo(-74.172367, 0.000001))))
                .andExpect(jsonPath("$.typicalOrigin.providerPlaceId", is("static:newark")))
                .andExpect(jsonPath("$.typicalDestination.name", is("Manhattan, NY")))
                .andExpect(jsonPath("$.typicalDestination.latitude", is(closeTo(40.758, 0.000001))));
    }

    @Test
    @DisplayName("editing the profile later never re-opens onboarding")
    void editingKeepsOnboardingComplete() throws Exception {
        String token = register("settings@example.com");
        mockMvc.perform(putOnboarding(token, fullOnboarding())).andExpect(status().isOk());

        String completedAt = mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String firstTimestamp = objectMapper.readTree(completedAt).get("onboardingCompletedAt").asText();

        Map<String, Object> edited = fullOnboarding();
        edited.put("defaultContextProfile", "RUSH");
        edited.put("weeklyBudgetCents", 7500);

        mockMvc.perform(put("/api/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultContextProfile", is("RUSH")))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(7500)))
                .andExpect(jsonPath("$.onboardingCompleted", is(true)))
                // The timestamp records when the rider actually answered, so it does
                // not move every time they change their mind about a preference.
                .andExpect(jsonPath("$.onboardingCompletedAt", is(firstTimestamp)));
    }

    // ---------------- the budget is optional ----------------

    @Test
    @DisplayName("\"I'm not sure\" leaves the budget unset everywhere, and never as $0.00")
    void nullableBudget() throws Exception {
        String token = register("nobudget@example.com");

        Map<String, Object> answers = fullOnboarding();
        answers.put("weeklyBudgetCents", null);

        mockMvc.perform(putOnboarding(token, answers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(nullValue())));

        // A missing budget is an absence all the way through, not a zero.
        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(nullValue())))
                .andExpect(jsonPath("$.availableBalanceCents", is(nullValue())))
                .andExpect(jsonPath("$.budgetUtilization", is(nullValue())))
                .andExpect(jsonPath("$.spentThisWeekCents", is(0)));

        mockMvc.perform(get("/api/insights").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(nullValue())))
                .andExpect(jsonPath("$.remainingCents", is(nullValue())));
    }

    @Test
    @DisplayName("a budget of zero is a budget, and is not confused with having none")
    void zeroBudgetIsDistinctFromNoBudget() throws Exception {
        String token = register("zero@example.com");

        Map<String, Object> answers = fullOnboarding();
        answers.put("weeklyBudgetCents", 0);

        mockMvc.perform(putOnboarding(token, answers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(0)));

        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(0)))
                .andExpect(jsonPath("$.availableBalanceCents", is(0)));
    }

    // ---------------- validation ----------------

    @Test
    @DisplayName("an unknown travel priority is rejected with the valid options")
    void unknownContextProfileRejected() throws Exception {
        String token = register("badprofile@example.com");
        Map<String, Object> answers = fullOnboarding();
        answers.put("defaultContextProfile", "MAKE_ME_RICH");

        mockMvc.perform(putOnboarding(token, answers))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", containsString("SAVE_MONEY")));
    }

    @Test
    @DisplayName("unknown enum values are rejected across every question")
    void unknownEnumsRejected() throws Exception {
        String token = register("badenums@example.com");

        assertRejected(token, "weeklyCommuteFrequency", "EVERY_OTHER_TUESDAY", "VARIES");
        assertRejected(token, "commuteKind", "VACATION", "SCHOOL");
        assertRejected(token, "passPreference", "CRYPTO", "PAY_PER_RIDE");
    }

    @Test
    @DisplayName("an unknown travel mode is rejected")
    void unknownModeRejected() throws Exception {
        String token = register("badmode@example.com");
        Map<String, Object> answers = fullOnboarding();
        answers.put("preferredModes", List.of("TRAIN", "HELICOPTER"));

        mockMvc.perform(putOnboarding(token, answers))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("FERRY")));
    }

    @Test
    @DisplayName("a negative budget, and an implausible one, are both rejected")
    void budgetRangeEnforced() throws Exception {
        String token = register("budgetrange@example.com");

        Map<String, Object> negative = fullOnboarding();
        negative.put("weeklyBudgetCents", -1);
        mockMvc.perform(putOnboarding(token, negative))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.weeklyBudgetCents", containsString("zero or greater")));

        // $9,999.99 a week is a rider typing dollars into a cents field.
        Map<String, Object> absurd = fullOnboarding();
        absurd.put("weeklyBudgetCents", 999_999);
        mockMvc.perform(putOnboarding(token, absurd))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.weeklyBudgetCents", containsString("$2,000.00")));
    }

    @Test
    @DisplayName("half a commute is rejected: a place needs coordinates, and a commute needs both ends")
    void incompleteCommuteRejected() throws Exception {
        String token = register("halfcommute@example.com");

        // A name with no coordinates is the ambiguous free text the schema exists
        // to prevent, so it fails validation rather than being stored hopefully.
        Map<String, Object> noCoordinates = fullOnboarding();
        noCoordinates.put("typicalOrigin", new LinkedHashMap<>(Map.of("name", "Newark")));
        mockMvc.perform(putOnboarding(token, noCoordinates))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", notNullValue()));

        // One end of a commute is not a commute.
        Map<String, Object> onlyOrigin = fullOnboarding();
        onlyOrigin.put("typicalDestination", null);
        mockMvc.perform(putOnboarding(token, onlyOrigin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("both")));
    }

    @Test
    @DisplayName("\"no regular commute\" clears the saved commute instead of contradicting it")
    void noRegularCommuteClearsPlaces() throws Exception {
        String token = register("nocommute@example.com");
        mockMvc.perform(putOnboarding(token, fullOnboarding())).andExpect(status().isOk());

        Map<String, Object> answers = fullOnboarding();
        answers.put("commuteKind", "NONE");

        mockMvc.perform(put("/api/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commuteKind", is("NONE")))
                .andExpect(jsonPath("$.hasTypicalCommute", is(false)))
                .andExpect(jsonPath("$.typicalOrigin", is(nullValue())))
                .andExpect(jsonPath("$.typicalDestination", is(nullValue())));
    }

    // ---------------- ownership ----------------

    @Test
    @DisplayName("a signed-in rider cannot read or write another rider's profile")
    void profilesAreSelfScoped() throws Exception {
        String alice = register("alice-profile@example.com");
        String bob = register("bob-profile@example.com");

        Map<String, Object> aliceAnswers = fullOnboarding();
        aliceAnswers.put("defaultContextProfile", "SAVE_MONEY");
        aliceAnswers.put("weeklyBudgetCents", 5000);
        mockMvc.perform(putOnboarding(alice, aliceAnswers)).andExpect(status().isOk());

        long aliceId = userRepository.findByEmailIgnoreCase("alice-profile@example.com").orElseThrow().getId();

        // Bob writes his own profile while naming Alice every way the transport
        // allows. None of it selects her record, because none of it is read.
        Map<String, Object> bobAnswers = fullOnboarding();
        bobAnswers.put("defaultContextProfile", "RUSH");
        bobAnswers.put("weeklyBudgetCents", 1000);
        bobAnswers.put("userId", aliceId);

        mockMvc.perform(put("/api/onboarding")
                        .header("Authorization", "Bearer " + bob)
                        .header("X-User-Id", String.valueOf(aliceId))
                        .param("userId", String.valueOf(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobAnswers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultContextProfile", is("RUSH")));

        // Alice is untouched.
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.defaultContextProfile", is("SAVE_MONEY")))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)));

        // And reading with a userId parameter still returns the caller's own.
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + bob)
                        .param("userId", String.valueOf(aliceId)))
                .andExpect(jsonPath("$.defaultContextProfile", is("RUSH")))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(1000)));
    }

    @Test
    @DisplayName("the profile endpoints require a token")
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullOnboarding())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(putOnboardingWithoutToken()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the option catalogue is served by the backend, so the client invents no vocabulary")
    void optionsCatalogue() throws Exception {
        mockMvc.perform(get("/api/profile/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextProfiles", hasSize(4)))
                .andExpect(jsonPath("$.commuteFrequencies", hasSize(4)))
                .andExpect(jsonPath("$.commuteKinds", hasSize(4)))
                .andExpect(jsonPath("$.passPreferences", hasSize(4)))
                .andExpect(jsonPath("$.travelModes", hasSize(5)))
                .andExpect(jsonPath("$.travelModes[*].id", hasItem("FERRY")))
                // The weights still come from the server, never from the client.
                .andExpect(jsonPath("$.contextProfiles[?(@.id == 'SAVE_MONEY')].costPriority",
                        is(List.of(0.75))));
    }

    @Test
    @DisplayName("a browser can actually reach the PUT endpoints")
    void putSurvivesCorsPreflight() throws Exception {
        // Regression test with a real cause: the profile and onboarding resources
        // are the first PUTs in the API, and the CORS method list did not include
        // PUT. Nothing failed -- not a single test, not curl -- because a preflight
        // only happens in a browser. The onboarding submit failed silently there.
        for (String path : new String[]{"/api/profile", "/api/onboarding"}) {
            mockMvc.perform(options(path)
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "PUT"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")));
        }
    }

    // ---------------- personalization precedence ----------------

    @Test
    @DisplayName("with no stance given, the onboarding default is used")
    void defaultPreferenceAppliesWhenNoStanceIsGiven() throws Exception {
        String token = register("default-pref@example.com");
        Map<String, Object> answers = fullOnboarding();
        answers.put("defaultContextProfile", "SAVE_MONEY");
        mockMvc.perform(putOnboarding(token, answers)).andExpect(status().isOk());

        mockMvc.perform(get("/api/journeys").header("Authorization", "Bearer " + token)
                        .param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id", is("SAVE_MONEY")))
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.75)));
    }

    @Test
    @DisplayName("the current request overrides the onboarding default")
    void currentContextOverridesDefault() throws Exception {
        String token = register("override@example.com");
        Map<String, Object> answers = fullOnboarding();
        answers.put("defaultContextProfile", "SAVE_MONEY");
        mockMvc.perform(putOnboarding(token, answers)).andExpect(status().isOk());

        // "I'm late today" beats "I usually want to save money".
        mockMvc.perform(get("/api/journeys").header("Authorization", "Bearer " + token)
                        .param("from", "Philadelphia").param("to", "Manhattan")
                        .param("profile", "RUSH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id", is("RUSH")))
                .andExpect(jsonPath("$.weightsUsed.timePriority", is(0.75)));

        // Asking for BALANCED explicitly is also a statement, and also wins.
        mockMvc.perform(get("/api/journeys").header("Authorization", "Bearer " + token)
                        .param("from", "Philadelphia").param("to", "Manhattan")
                        .param("profile", "BALANCED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id", is("BALANCED")))
                .andExpect(jsonPath("$.weightsUsed.costPriority", is(0.45)));
    }

    @Test
    @DisplayName("a rider with no profile still gets BALANCED, not an error")
    void noProfileFallsBackToBalanced() throws Exception {
        String token = register("noprofile@example.com");

        mockMvc.perform(get("/api/journeys").header("Authorization", "Bearer " + token)
                        .param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id", is("BALANCED")));
    }

    @Test
    @DisplayName("an unknown stance is still rejected, personalization or not")
    void unknownStanceStillRejected() throws Exception {
        String token = register("badstance@example.com");
        mockMvc.perform(putOnboarding(token, fullOnboarding())).andExpect(status().isOk());

        mockMvc.perform(get("/api/journeys").header("Authorization", "Bearer " + token)
                        .param("from", "Philadelphia").param("to", "Manhattan")
                        .param("profile", "TELEPORT"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- helpers ----------------

    @org.springframework.beans.factory.annotation.Autowired
    private com.fareflow.profile.UserTravelProfileRepository travelProfiles;

    private void assertRejected(String token, String field, String badValue, String expectedHint)
            throws Exception {
        Map<String, Object> answers = fullOnboarding();
        answers.put(field, badValue);
        mockMvc.perform(putOnboarding(token, answers))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString(expectedHint)));
    }

    /** Every answer a rider gives, as the onboarding flow submits them. */
    private static Map<String, Object> fullOnboarding() {
        Map<String, Object> body = new HashMap<>();
        body.put("defaultContextProfile", "SAVE_MONEY");
        body.put("weeklyCommuteFrequency", "THREE_TO_FOUR_DAYS");
        body.put("weeklyBudgetCents", 5000);
        body.put("commuteKind", "WORK");
        body.put("typicalOrigin", place("Newark, NJ", 40.735657, -74.172367, "static:newark"));
        body.put("typicalDestination", place("Manhattan, NY", 40.758, -73.9855, "static:manhattan"));
        body.put("passPreference", "PAY_PER_RIDE");
        body.put("preferredModes", List.of("TRAIN", "SUBWAY"));
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

    private MockHttpServletRequestBuilder putOnboarding(String token, Map<String, Object> body)
            throws Exception {
        return put("/api/onboarding")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder putOnboardingWithoutToken() throws Exception {
        return put("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fullOnboarding()));
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test Rider",
                                "email", email,
                                "password", "a-good-password"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
