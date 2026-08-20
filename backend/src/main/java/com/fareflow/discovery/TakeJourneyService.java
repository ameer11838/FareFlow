package com.fareflow.discovery;

import com.fareflow.discovery.dto.TakeJourneyRequest;
import com.fareflow.exception.ResourceNotFoundException;
import com.fareflow.fare.FareCalculation;
import com.fareflow.fare.UserFareContext;
import com.fareflow.journey.Journey;
import com.fareflow.journey.PersistedJourney;
import com.fareflow.journey.PersistedJourneyRepository;
import com.fareflow.ledger.LedgerService;
import com.fareflow.location.LocationCandidate;
import com.fareflow.location.LocationService;
import com.fareflow.trip.SelectedLabel;
import com.fareflow.trip.Trip;
import com.fareflow.trip.TripRepository;
import com.fareflow.budget.BudgetService;
import com.fareflow.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a selected journey into a persisted trip and a ledger charge.
 *
 * <p>Three properties this class exists to guarantee:
 *
 * <ol>
 *   <li><strong>The fare is the server's.</strong> The client sends a journey id;
 *       the journey is re-discovered and re-priced here. Nothing the browser sends
 *       influences the amount charged.</li>
 *   <li><strong>All or nothing.</strong> Journey snapshot, trip, and charge commit
 *       in one transaction, with {@code LedgerService} on MANDATORY propagation so
 *       a charge cannot be written outside it.</li>
 *   <li><strong>Once only.</strong> A repeated idempotency key returns the original
 *       trip instead of charging again.</li>
 * </ol>
 */
@Service
public class TakeJourneyService {

    private static final Logger log = LoggerFactory.getLogger(TakeJourneyService.class);

    private final LocationService locationService;
    private final JourneyPlanningService planningService;
    private final PersistedJourneyRepository journeyRepository;
    private final TripRepository tripRepository;
    private final LedgerService ledgerService;
    private final BudgetService budgetService;
    private final Clock clock;

    public TakeJourneyService(LocationService locationService,
                              JourneyPlanningService planningService,
                              PersistedJourneyRepository journeyRepository,
                              TripRepository tripRepository,
                              LedgerService ledgerService,
                              BudgetService budgetService,
                              Clock clock) {
        this.locationService = locationService;
        this.planningService = planningService;
        this.journeyRepository = journeyRepository;
        this.tripRepository = tripRepository;
        this.ledgerService = ledgerService;
        this.budgetService = budgetService;
        this.clock = clock;
    }

    @Transactional
    public Trip take(User user, TakeJourneyRequest request, String idempotencyKey) {
        // Idempotency first: a retry must not even re-plan, let alone re-charge.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Trip> existing =
                    tripRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {} -> trip {}", idempotencyKey, existing.get().getId());
                return existing.get();
            }
        }

        LocationCandidate origin = locationService.resolve(request.from())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Could not find a place matching '%s'".formatted(request.from())));
        LocationCandidate destination = locationService.resolve(request.to())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Could not find a place matching '%s'".formatted(request.to())));

        // Re-plan and re-price. Discovery is deterministic over the same network, so
        // the journey the rider saw is the journey found here -- but the fare is
        // computed fresh rather than trusted.
        UserFareContext fareContext = fareContextFor(user);
        List<JourneyPlanningService.PricedJourney> priced =
                planningService.plan(origin, destination, fareContext);

        JourneyPlanningService.PricedJourney selected = priced.stream()
                .filter(entry -> entry.journey().id().equals(request.journeyId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "That journey is no longer available. Search again to see current options."));

        Journey journey = selected.journey();
        FareCalculation fare = selected.fare();

        // An unpriceable journey is refused unless the rider explicitly accepts it.
        // Charging zero here would be the worst possible failure mode.
        if (!fare.isPriced() && !request.confirmUnknownFare()) {
            throw new FareConfirmationRequiredException(journey.summary());
        }

        long authoritativeFareCents = fare.isPriced() ? fare.totalFareCents() : 0L;

        PersistedJourney snapshot = journeyRepository.save(
                PersistedJourney.snapshot(journey, fare, origin, destination));

        Long baselineFareCents = fastestPricedFare(priced).orElse(null);

        Instant now = clock.instant();
        Trip trip = tripRepository.save(new Trip(
                user.getId(), snapshot, authoritativeFareCents,
                selectedLabelFor(request), baselineFareCents, now, idempotencyKey));

        // A zero-fare trip writes no charge: the ledger's sign constraint forbids a
        // non-negative TRIP_CHARGE, and a $0.00 entry would be noise in the history.
        if (authoritativeFareCents > 0) {
            ledgerService.recordTripCharge(
                    user.getId(), trip.getId(), authoritativeFareCents,
                    "%s — %s to %s".formatted(journey.summary(),
                            origin.displayName(), destination.displayName()),
                    now);
        }

        return trip;
    }

    /** The fare of the fastest priced alternative, for "saved vs. fastest". */
    private static Optional<Long> fastestPricedFare(List<JourneyPlanningService.PricedJourney> priced) {
        List<JourneyPlanningService.PricedJourney> pricedOnly = priced.stream()
                .filter(entry -> entry.fare().isPriced())
                .toList();
        // With fewer than two comparable options the rider made no choice, so there
        // is no honest baseline -- the same rule the seeded routes follow.
        if (pricedOnly.size() < 2) {
            return Optional.empty();
        }
        return pricedOnly.stream()
                .min(Comparator.comparingInt(entry -> entry.journey().totalMinutes()))
                .map(entry -> entry.fare().totalFareCents());
    }

    private SelectedLabel selectedLabelFor(TakeJourneyRequest request) {
        // The label is descriptive metadata, not an input to the charge.
        return SelectedLabel.MANUAL;
    }

    private UserFareContext fareContextFor(User user) {
        var summary = budgetService.currentWeek(user.getId());
        return new UserFareContext(Math.max(0, summary.spentCents()), 0, Set.of());
    }
}
