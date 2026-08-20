package com.fareflow.integration;

import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.trip.SelectedLabel;
import com.fareflow.trip.TripService;
import com.fareflow.trip.dto.CreateTripRequest;
import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weekly figures are derived from the ledger on every request. Nothing is stored,
 * so these assertions are really checking that the SUM and the trips agree.
 */
class BudgetDashboardIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BudgetService budgetService;
    @Autowired
    private TripService tripService;
    @Autowired
    private UserRepository userRepository;

    private static final long NJ_TRANSIT_ROUTE_ID = 1L;
    private static final long PATH_ROUTE_ID = 2L;
    private static final long PRINCETON_ROUTE_ID = 7L;

    @Test
    @DisplayName("a new user has spent nothing and their whole budget remains")
    void freshUser() {
        User user = givenUser(5_000);

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.spentCents()).isZero();
        assertThat(summary.remainingCents()).isEqualTo(5_000);
        assertThat(summary.tripCount()).isZero();
        // Not computable rather than zero: no trips have happened.
        assertThat(summary.savedVersusFastestCents()).isNull();
    }

    @Test
    @DisplayName("taking a trip moves spent, remaining, trips, and savings together")
    void takingATripUpdatesEveryFigure() {
        User user = givenUser(5_000);
        tripService.takeTrip(user.getId(), new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.spentCents()).isEqualTo(300);
        assertThat(summary.remainingCents()).isEqualTo(4_700);
        assertThat(summary.tripCount()).isEqualTo(1);
        assertThat(summary.budgetUtilization()).isEqualTo(0.06);
        // Chose PATH ($3.00) over the fastest, NJ Transit ($6.25).
        assertThat(summary.savedVersusFastestCents()).isEqualTo(325);
    }

    @Test
    @DisplayName("cancelling reverses spending and removes the savings contribution")
    void cancellingReverses() {
        User user = givenUser(5_000);
        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));

        tripService.cancelTrip(user.getId(), trip.getId());
        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.spentCents()).isZero();
        assertThat(summary.remainingCents()).isEqualTo(5_000);
        // Only COMPLETED trips count.
        assertThat(summary.tripCount()).isZero();
        assertThat(summary.savedVersusFastestCents()).isNull();
    }

    @Test
    @DisplayName("taking the fastest route honestly yields zero savings, not a fake number")
    void fastestRouteYieldsZeroSavings() {
        User user = givenUser(5_000);
        tripService.takeTrip(user.getId(), new CreateTripRequest(NJ_TRANSIT_ROUTE_ID, SelectedLabel.FASTEST));

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.spentCents()).isEqualTo(625);
        assertThat(summary.savedVersusFastestCents()).isZero();
    }

    @Test
    @DisplayName("a trip with no baseline contributes nothing rather than being counted as zero")
    void baselinelessTripIsExcludedFromSavings() {
        User user = givenUser(10_000);
        tripService.takeTrip(user.getId(), new CreateTripRequest(PRINCETON_ROUTE_ID, SelectedLabel.MANUAL));

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.spentCents()).isEqualTo(1_875);
        assertThat(summary.tripCount()).isEqualTo(1);
        // The only trip has a NULL baseline, so savings are not computable at all.
        assertThat(summary.savedVersusFastestCents()).isNull();
    }

    @Test
    @DisplayName("savings sum only over trips that have a baseline")
    void savingsSumSkipsNullBaselines() {
        User user = givenUser(10_000);
        tripService.takeTrip(user.getId(), new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));
        tripService.takeTrip(user.getId(), new CreateTripRequest(PRINCETON_ROUTE_ID, SelectedLabel.MANUAL));

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.tripCount()).isEqualTo(2);
        assertThat(summary.spentCents()).isEqualTo(2_175);
        // Only the PATH trip contributes: $6.25 baseline - $3.00 fare.
        assertThat(summary.savedVersusFastestCents()).isEqualTo(325);
    }

    @Test
    @DisplayName("multiple trips accumulate")
    void multipleTrips() {
        User user = givenUser(5_000);
        tripService.takeTrip(user.getId(), new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));
        tripService.takeTrip(user.getId(), new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));
        tripService.takeTrip(user.getId(), new CreateTripRequest(NJ_TRANSIT_ROUTE_ID, SelectedLabel.FASTEST));

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.spentCents()).isEqualTo(300 + 300 + 625);
        assertThat(summary.remainingCents()).isEqualTo(5_000 - 1_225);
        assertThat(summary.tripCount()).isEqualTo(3);
        // 325 + 325 + 0
        assertThat(summary.savedVersusFastestCents()).isEqualTo(650);
    }

    @Test
    @DisplayName("a zero budget yields a null utilization rather than dividing by zero")
    void zeroBudget() {
        User user = givenUser(0);
        tripService.takeTrip(user.getId(), new CreateTripRequest(PATH_ROUTE_ID, SelectedLabel.BEST_VALUE));

        WeeklySummary summary = budgetService.currentWeek(user.getId());

        assertThat(summary.budgetUtilization()).isNull();
        assertThat(summary.remainingCents()).isEqualTo(-300);
    }

    private User givenUser(long weeklyBudgetCents) {
        return userRepository.save(
                new User("Budget User", "budget-%d@example.com".formatted(System.nanoTime()),
                        weeklyBudgetCents, "America/New_York"));
    }
}
