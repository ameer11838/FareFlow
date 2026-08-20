package com.fareflow.integration;

import com.fareflow.passes.PassOptimizationService;
import com.fareflow.passes.dto.PassRecommendation;
import com.fareflow.trip.SelectedLabel;
import com.fareflow.trip.Trip;
import com.fareflow.trip.TripRepository;
import com.fareflow.trip.TripService;
import com.fareflow.trip.dto.CreateTripRequest;
import com.fareflow.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pass advice must be honest in both directions: recommend a pass when it genuinely
 * saves money, and say so plainly when it does not.
 */
class PassOptimizationIntegrationTest extends IntegrationTestBase {

    @Autowired private PassOptimizationService passService;
    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;

    private static final long PATH_ROUTE_ID = 2L;

    @Test
    @DisplayName("no history yields an explicit refusal, not a guess")
    void noHistory() {
        PassRecommendation recommendation = passService.recommendFor(demoUser());

        assertThat(recommendation.hasEnoughHistory()).isFalse();
        assertThat(recommendation.confidence()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(recommendation.recommendedPassCode()).isNull();
        assertThat(recommendation.verdict()).contains("Not enough travel history");
    }

    @Test
    @DisplayName("a few days of trips is still not enough to average a week")
    void tooLittleHistory() {
        User user = demoUser();
        takeTrips(user, 3, 2);

        PassRecommendation recommendation = passService.recommendFor(user);

        assertThat(recommendation.hasEnoughHistory()).isFalse();
        assertThat(recommendation.options()).isEmpty();
    }

    @Test
    @DisplayName("a heavy PATH commuter is told to buy the pass, with the maths shown")
    void heavyCommuterShouldBuyThePass() {
        User user = demoUser();
        // Two PATH rides a day for three weeks: about $60/week at $3.00 a ride,
        // comfortably above the $46/week SmartLink pass.
        takeTrips(user, 42, 21);

        PassRecommendation recommendation = passService.recommendFor(user);

        assertThat(recommendation.hasEnoughHistory()).isTrue();
        assertThat(recommendation.recommendedPassCode()).isNotNull();
        assertThat(recommendation.monthlySavingsCents()).isPositive();
        assertThat(recommendation.verdict()).contains("save");
        // Assumptions are always stated alongside the number.
        assertThat(recommendation.assumptions()).isNotEmpty();
        assertThat(recommendation.assumptions())
                .anyMatch(line -> line.contains("travel pattern continues"));
    }

    @Test
    @DisplayName("an occasional rider is told to keep paying per ride")
    void lightRiderShouldNotBuyAPass() {
        User user = demoUser();
        // Two rides across three weeks -- nowhere near pass territory.
        takeTrips(user, 2, 21);

        PassRecommendation recommendation = passService.recommendFor(user);

        assertThat(recommendation.hasEnoughHistory()).isTrue();
        assertThat(recommendation.recommendedPassCode()).isNull();
        assertThat(recommendation.verdict()).contains("Paying per ride is cheaper");
        // Every option is still shown, with its (negative) savings, rather than hidden.
        assertThat(recommendation.options()).isNotEmpty();
        assertThat(recommendation.options()).allMatch(option -> !option.worthwhile());
    }

    @Test
    @DisplayName("savings are never invented: a rejected pass reports a negative figure")
    void rejectedPassesReportRealNumbers() {
        User user = demoUser();
        takeTrips(user, 2, 21);

        PassRecommendation recommendation = passService.recommendFor(user);

        assertThat(recommendation.options())
                .allSatisfy(option -> assertThat(option.monthlySavingsCents()).isNegative());
    }

    @Test
    @DisplayName("confidence reflects how much history backs the answer")
    void confidenceTracksSampleSize() {
        User user = demoUser();
        takeTrips(user, 30, 21);

        assertThat(passService.recommendFor(user).confidence()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("only agencies the rider actually uses are considered")
    void onlyRelevantAgencies() {
        User user = demoUser();
        takeTrips(user, 20, 14); // PATH only

        PassRecommendation recommendation = passService.recommendFor(user);

        assertThat(recommendation.options())
                .allSatisfy(option -> assertThat(option.agency()).isEqualTo("PATH"));
    }

    @Test
    @DisplayName("the endpoint is scoped to the caller")
    void endpointReturnsCallersRecommendation() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/passes/recommendation"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.hasEnoughHistory").exists());
    }

    /**
     * Creates {@code count} PATH trips spread backwards over {@code overDays}, so
     * the service sees a realistic history window rather than a single instant.
     */
    private void takeTrips(User user, int count, int overDays) {
        for (int i = 0; i < count; i++) {
            Trip trip = tripService.takeTrip(user.getId(),
                    new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));
            // Backdate so the history spans a real window.
            long daysAgo = (long) (overDays * (1.0 - (double) i / Math.max(1, count)));
            tripRepository.backdateForTesting(trip.getId(),
                    java.time.Instant.now().minus(daysAgo, java.time.temporal.ChronoUnit.DAYS));
        }
    }
}
