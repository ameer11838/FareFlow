package com.fareflow.trip;

import com.fareflow.exception.InvalidStateException;
import com.fareflow.ledger.LedgerService;
import com.fareflow.recommendation.RecommendationService;
import com.fareflow.route.TransitRoute;
import com.fareflow.route.TransitRouteService;
import com.fareflow.trip.dto.CreateTripRequest;
import com.fareflow.user.User;
import com.fareflow.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final LedgerService ledgerService;
    private final UserService userService;
    private final TransitRouteService routeService;
    private final RecommendationService recommendationService;
    private final Clock clock;

    public TripService(TripRepository tripRepository,
                       LedgerService ledgerService,
                       UserService userService,
                       TransitRouteService routeService,
                       RecommendationService recommendationService,
                       Clock clock) {
        this.tripRepository = tripRepository;
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.routeService = routeService;
        this.recommendationService = recommendationService;
        this.clock = clock;
    }

    /**
     * Takes a route: creates the trip and its TRIP_CHARGE in one transaction.
     *
     * <p>A trip without its charge is a free ride; a charge without its trip is
     * unexplained money. Neither may exist alone, so both writes commit together
     * or neither does. {@link LedgerService} uses MANDATORY propagation to make
     * that structural rather than a convention.
     */
    @Transactional
    public Trip takeTrip(long userId, CreateTripRequest request) {
        User user = userService.getById(userId);
        TransitRoute route = routeService.getById(request.routeId());

        if (!route.isActive()) {
            throw new InvalidStateException("Route %d is no longer available".formatted(route.getId()));
        }

        // Baseline for "saved vs. fastest route", snapshotted now so the figure is
        // stable and auditable later. Empty when the user had no alternative.
        Long baselineFareCents = recommendationService
                .findFastestFareBaseline(route.getOrigin(), route.getDestination())
                .orElse(null);

        Instant now = clock.instant();
        Trip trip = tripRepository.save(
                new Trip(user.getId(), route, request.selectedLabelOrManual(), baselineFareCents, now));

        ledgerService.recordTripCharge(
                user.getId(),
                trip.getId(),
                route.getFareCents(),
                "%s — %s to %s".formatted(route.getProvider().displayName(),
                        route.getOrigin(), route.getDestination()),
                now);

        return trip;
    }

    /**
     * Cancels a trip and appends a REFUND.
     *
     * <p>The original charge is never touched. The refund's {@code occurredAt} is
     * now, not the trip's original time, so cancelling last week's trip reduces
     * this week's spend — the way a card statement handles a credit. Backdating
     * would silently rewrite a closed week.
     */
    @Transactional
    public Trip cancelTrip(long userId, long tripId) {
        Trip trip = getOwnedById(userId, tripId);
        if (trip.isCancelled()) {
            throw new InvalidStateException("Trip %d is already cancelled".formatted(tripId));
        }

        trip.markCancelled();
        tripRepository.save(trip);

        ledgerService.recordRefund(
                trip.getUserId(),
                trip.getId(),
                trip.getFareCents(),
                "Refund: cancelled %s trip".formatted(trip.getProvider()),
                clock.instant());

        return trip;
    }

    public Trip getById(long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new com.fareflow.exception.ResourceNotFoundException(
                        "Trip %d was not found".formatted(tripId)));
    }

    /**
     * Loads a trip only if it belongs to the caller.
     *
     * <p>Returns 404 rather than 403 for someone else's trip: confirming that a
     * trip id exists is itself a small leak, and the caller has no legitimate way
     * to know the difference.
     */
    public Trip getOwnedById(long userId, long tripId) {
        Trip trip = getById(tripId);
        if (trip.getUserId() != userId) {
            throw new com.fareflow.exception.ResourceNotFoundException(
                    "Trip %d was not found".formatted(tripId));
        }
        return trip;
    }

    public Page<Trip> findForUser(long userId, Pageable pageable) {
        userService.getById(userId);
        return tripRepository.findByUserIdOrderByTakenAtDescIdDesc(userId, pageable);
    }

    public List<Trip> findRecentForUser(long userId) {
        return tripRepository.findTop5ByUserIdOrderByTakenAtDescIdDesc(userId);
    }
}
