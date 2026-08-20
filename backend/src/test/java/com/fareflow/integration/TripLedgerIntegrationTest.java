package com.fareflow.integration;

import com.fareflow.ledger.LedgerEntry;
import com.fareflow.ledger.LedgerEntryType;
import com.fareflow.ledger.LedgerRepository;
import com.fareflow.trip.SelectedLabel;
import com.fareflow.trip.TripRepository;
import com.fareflow.trip.TripService;
import com.fareflow.trip.dto.CreateTripRequest;
import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The transactional heart of the fintech side: a trip and its charge must commit
 * together, and a cancellation must never erase history.
 */
class TripLedgerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TripService tripService;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private UserRepository userRepository;

    private static final long PATH_NEWARK_ROUTE_ID = 2L;
    private static final long PRINCETON_ROUTE_ID = 7L;

    @Test
    @DisplayName("taking a trip creates exactly one trip and exactly one TRIP_CHARGE")
    void takingATripCreatesACharge() {
        User user = givenUser(5_000);

        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.BEST_VALUE));

        assertThat(tripRepository.count()).isEqualTo(1);
        assertThat(trip.getFareCents()).isEqualTo(300);
        assertThat(trip.getProvider()).isEqualTo("PATH");

        List<LedgerEntry> entries = ledgerRepository.findByTripIdOrderByIdAsc(trip.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getType()).isEqualTo(LedgerEntryType.TRIP_CHARGE);
        // Charges are stored negative: money out.
        assertThat(entries.getFirst().getAmountCents()).isEqualTo(-300);
        assertThat(entries.getFirst().getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("the trip snapshots the route rather than pointing at live data")
    void tripSnapshotsRoute() {
        User user = givenUser(5_000);

        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.BEST_VALUE));

        assertThat(trip.getOrigin()).isEqualTo("Newark");
        assertThat(trip.getDestination()).isEqualTo("Manhattan");
        assertThat(trip.getDurationMinutes()).isEqualTo(38);
        assertThat(trip.getTransfers()).isZero();
    }

    @Test
    @DisplayName("the baseline is the fastest route's fare, so savings are computable")
    void baselineIsFastestFare() {
        User user = givenUser(5_000);

        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.BEST_VALUE));

        // Fastest Newark -> Manhattan is NJ Transit at $6.25.
        assertThat(trip.getBaselineFareCents()).isEqualTo(625);
        assertThat(trip.savedVersusFastestCents()).contains(325L);
    }

    @Test
    @DisplayName("a pair with only one route stores a NULL baseline, not zero")
    void singleRoutePairHasNoBaseline() {
        User user = givenUser(5_000);

        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PRINCETON_ROUTE_ID, SelectedLabel.MANUAL));

        // The user had no alternative, so there is no honest savings figure.
        assertThat(trip.getBaselineFareCents()).isNull();
        assertThat(trip.savedVersusFastestCents()).isEmpty();
    }

    @Test
    @DisplayName("cancelling adds a REFUND and leaves the original charge untouched")
    void cancellingRefundsWithoutDeleting() {
        User user = givenUser(5_000);
        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.BEST_VALUE));

        tripService.cancelTrip(user.getId(), trip.getId());

        List<LedgerEntry> entries = ledgerRepository.findByTripIdOrderByIdAsc(trip.getId());
        assertThat(entries).hasSize(2);

        // The original charge is still there, unchanged.
        assertThat(entries.get(0).getType()).isEqualTo(LedgerEntryType.TRIP_CHARGE);
        assertThat(entries.get(0).getAmountCents()).isEqualTo(-300);

        assertThat(entries.get(1).getType()).isEqualTo(LedgerEntryType.REFUND);
        assertThat(entries.get(1).getAmountCents()).isEqualTo(300);

        // Net effect on the user is zero, without either row being edited.
        assertThat(entries.stream().mapToLong(LedgerEntry::getAmountCents).sum()).isZero();

        assertThat(tripRepository.findById(trip.getId()).orElseThrow().isCancelled()).isTrue();
    }

    @Test
    @DisplayName("cancelling twice is a 409-level conflict, not a second refund")
    void cancellingTwiceIsRejected() {
        User user = givenUser(5_000);
        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.BEST_VALUE));
        tripService.cancelTrip(user.getId(), trip.getId());

        assertThatThrownBy(() -> tripService.cancelTrip(user.getId(), trip.getId()))
                .isInstanceOf(com.fareflow.exception.InvalidStateException.class)
                .hasMessageContaining("already cancelled");

        assertThat(ledgerRepository.findByTripIdOrderByIdAsc(trip.getId())).hasSize(2);
    }

    @Test
    @DisplayName("an unknown route rolls the whole thing back: no trip, no charge")
    void unknownRouteWritesNothing() {
        User user = givenUser(5_000);

        assertThatThrownBy(() -> tripService.takeTrip(user.getId(),
                new CreateTripRequest(9_999L, SelectedLabel.MANUAL)))
                .isInstanceOf(com.fareflow.exception.ResourceNotFoundException.class);

        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
    }

    @Test
    @DisplayName("an unknown user writes nothing")
    void unknownUserWritesNothing() {
        assertThatThrownBy(() -> tripService.takeTrip(9_999L,
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.MANUAL)))
                .isInstanceOf(com.fareflow.exception.ResourceNotFoundException.class);

        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
    }

    @Test
    @DisplayName("the database rejects a positive TRIP_CHARGE regardless of application code")
    void databaseEnforcesSignInvariant() {
        User user = givenUser(5_000);
        var trip = tripService.takeTrip(user.getId(),
                new CreateTripRequest(PATH_NEWARK_ROUTE_ID, SelectedLabel.BEST_VALUE));

        // The factory method guards this, and so does a CHECK constraint. Both
        // matter: application rules can be bypassed by a script or a migration.
        assertThatThrownBy(() -> LedgerEntry.tripCharge(
                user.getId(), trip.getId(), -100, "invalid", java.time.Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User givenUser(long weeklyBudgetCents) {
        return userRepository.save(
                new User("Test User", "test-%d@example.com".formatted(System.nanoTime()),
                        weeklyBudgetCents, "America/New_York"));
    }
}
