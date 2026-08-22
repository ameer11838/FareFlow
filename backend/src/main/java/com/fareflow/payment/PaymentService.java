package com.fareflow.payment;

import com.fareflow.budget.BudgetService;
import com.fareflow.discovery.FareConfirmationRequiredException;
import com.fareflow.discovery.JourneyPlanningService;
import com.fareflow.discovery.dto.TakeJourneyRequest;
import com.fareflow.exception.InvalidStateException;
import com.fareflow.exception.ResourceNotFoundException;
import com.fareflow.fare.FareCalculation;
import com.fareflow.fare.UserFareContext;
import com.fareflow.journey.Journey;
import com.fareflow.journey.PersistedJourney;
import com.fareflow.journey.PersistedJourneyRepository;
import com.fareflow.ledger.LedgerEntry;
import com.fareflow.ledger.LedgerService;
import com.fareflow.location.LocationCandidate;
import com.fareflow.location.LocationService;
import com.fareflow.payment.dto.ConfirmPaymentRequest;
import com.fareflow.payment.dto.CreateJourneyPaymentRequest;
import com.fareflow.payment.dto.PaymentIntentResponse;
import com.fareflow.payment.dto.PaymentReconciliationResponse;
import com.fareflow.trip.SelectedLabel;
import com.fareflow.trip.Trip;
import com.fareflow.trip.TripRepository;
import com.fareflow.user.User;
import com.fareflow.session.TransitSession;
import com.fareflow.session.TransitSessionRepository;
import com.fareflow.session.TransitSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative route purchase workflow.
 *
 * <p>FareEngine output becomes an immutable payment amount; authorization and
 * settlement happen before the trip and charge are committed. All state changes,
 * the trip, and the ledger entry share one database transaction.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final LocationService locationService;
    private final JourneyPlanningService planningService;
    private final PersistedJourneyRepository journeyRepository;
    private final PaymentIntentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final TripRepository tripRepository;
    private final LedgerService ledgerService;
    private final BudgetService budgetService;
    private final SimulatedCardGateway cardGateway;
    private final TransitSessionRepository sessionRepository;
    private final Clock clock;

    public PaymentService(LocationService locationService,
                          JourneyPlanningService planningService,
                          PersistedJourneyRepository journeyRepository,
                          PaymentIntentRepository paymentRepository,
                          PaymentEventRepository eventRepository,
                          TripRepository tripRepository,
                          LedgerService ledgerService,
                          BudgetService budgetService,
                          SimulatedCardGateway cardGateway,
                          TransitSessionRepository sessionRepository,
                          Clock clock) {
        this.locationService = locationService;
        this.planningService = planningService;
        this.journeyRepository = journeyRepository;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.tripRepository = tripRepository;
        this.ledgerService = ledgerService;
        this.budgetService = budgetService;
        this.cardGateway = cardGateway;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /**
     * Pays a completed usage session with the fare already calculated by the
     * server. The request selects a rail but never states an amount.
     */
    @Transactional
    public PaymentIntentResponse payTransitSession(User user,
                                                   TransitSession session,
                                                   PaymentMethod paymentMethod,
                                                   String idempotencyKey,
                                                   String simulatedCardToken) {
        String key = requireIdempotencyKey(idempotencyKey);
        if (!session.getUserId().equals(user.getId())) {
            throw new ResourceNotFoundException(
                    "Transit session %s was not found".formatted(session.getId()));
        }
        String fingerprint = fingerprint(session, paymentMethod);
        Optional<PaymentIntent> replay = paymentRepository
                .findByUserIdAndIdempotencyKey(user.getId(), key);
        if (replay.isPresent()) {
            verifyReplay(replay.get(), fingerprint);
            process(replay.get(), simulatedCardToken, true);
            return response(replay.get());
        }
        if (paymentRepository.findByTransitSessionId(session.getId()).isPresent()) {
            throw new InvalidStateException("This transit session already has a payment");
        }
        if (session.getStatus() != TransitSessionStatus.COMPLETED
                || session.getFinalFareCents() == null || session.getFinalFareCents() <= 0) {
            throw new InvalidStateException(
                    "End a transit session with recorded travel before paying");
        }

        PaymentIntent intent = PaymentIntent.createForSession(
                user.getId(), session.getJourney(), session.getId(),
                session.getFinalFareCents(), paymentMethod, key, fingerprint, clock.instant());
        intent = paymentRepository.save(intent);
        eventRepository.save(new PaymentEvent(intent.getId(), null, PaymentStatus.CREATED,
                "Usage fare calculated and payment intent created", clock.instant()));
        process(intent, simulatedCardToken, true);
        return response(intent);
    }

    /** Creates but does not charge an intent. */
    @Transactional
    public Creation createJourneyIntent(User user,
                                        CreateJourneyPaymentRequest request,
                                        String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprint(request);
        Optional<PaymentIntent> existing =
                paymentRepository.findByUserIdAndIdempotencyKey(user.getId(), key);
        if (existing.isPresent()) {
            verifyReplay(existing.get(), fingerprint);
            return new Creation(response(existing.get()), true);
        }

        PaymentIntent intent = createAuthoritativeIntent(user, request, key, fingerprint);
        return new Creation(response(intent), false);
    }

    /** Authorizes and settles an intent exactly once. */
    @Transactional
    public PaymentIntentResponse confirm(User user, UUID intentId, ConfirmPaymentRequest request) {
        PaymentIntent intent = owned(user.getId(), intentId);
        process(intent, request == null ? null : request.simulatedCardToken(), false);
        return response(intent);
    }

    /** Retries only a previously failed authorization. */
    @Transactional
    public PaymentIntentResponse retry(User user, UUID intentId, ConfirmPaymentRequest request) {
        PaymentIntent intent = owned(user.getId(), intentId);
        if (intent.getStatus() != PaymentStatus.FAILED) {
            throw new InvalidStateException(
                    "Only a failed payment can be retried; this payment is %s"
                            .formatted(intent.getStatus()));
        }
        process(intent, request == null ? null : request.simulatedCardToken(), true);
        return response(intent);
    }

    /** Refunds a settled payment; a repeated refund returns the same result. */
    @Transactional
    public PaymentIntentResponse refund(User user, UUID intentId) {
        PaymentIntent intent = owned(user.getId(), intentId);
        refund(intent);
        return response(intent);
    }

    /** Used by the existing trip cancellation endpoint for payment-backed trips. */
    @Transactional
    public Optional<Trip> refundTrip(long userId, long tripId) {
        Optional<PaymentIntent> found = paymentRepository.findByTripId(tripId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PaymentIntent intent = found.get();
        if (intent.getUserId() != userId) {
            throw new ResourceNotFoundException("Trip %d was not found".formatted(tripId));
        }
        refund(intent);
        return Optional.of(tripRepository.findById(tripId).orElseThrow());
    }

    /**
     * Backwards-compatible one-call purchase used by /api/journeys/take.
     * New clients use create + confirm and can render the payment states.
     */
    @Transactional
    public Trip purchaseJourney(User user, TakeJourneyRequest request, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? "legacy:%s".formatted(UUID.randomUUID()) : idempotencyKey.trim();
        CreateJourneyPaymentRequest paymentRequest = new CreateJourneyPaymentRequest(
                request.from(), request.to(), request.journeyId(), request.profile(),
                request.confirmUnknownFare(), PaymentMethod.FAREFLOW_WALLET);
        String fingerprint = fingerprint(paymentRequest);

        Optional<PaymentIntent> existingPayment = paymentRepository
                .findByUserIdAndIdempotencyKey(user.getId(), key);
        if (existingPayment.isPresent()) {
            verifyReplay(existingPayment.get(), fingerprint);
            Trip existingTrip = process(existingPayment.get(), null, true);
            if (existingTrip == null) {
                throw new InvalidStateException("The payment failed and no trip was created");
            }
            return existingTrip;
        }

        // A fare the engine cannot calculate is not a zero-dollar payment. Keep
        // the established explicit-confirmation recording flow, but do not create
        // a PaymentIntent or ledger movement for it.
        AuthoritativeSelection selection = authoritativeSelection(
                user, request.from(), request.to(), request.journeyId());
        if (!selection.selected().fare().isPriced()) {
            if (!request.confirmUnknownFare()) {
                throw new FareConfirmationRequiredException(selection.selected().journey().summary());
            }
            return tripRepository.findByUserIdAndIdempotencyKey(user.getId(), key)
                    .orElseGet(() -> recordUnpricedJourney(user, selection, key));
        }

        PaymentIntent intent = paymentRepository
                .save(createIntent(user, paymentRequest, key, fingerprint, selection));
        eventRepository.save(new PaymentEvent(intent.getId(), null, PaymentStatus.CREATED,
                "Authoritative fare calculated and payment intent created", clock.instant()));

        Trip trip = process(intent, null, true);
        if (trip == null) {
            throw new InvalidStateException("The payment failed and no trip was created");
        }
        return trip;
    }

    public PaymentIntentResponse get(User user, UUID intentId) {
        return response(owned(user.getId(), intentId));
    }

    public Page<PaymentIntentResponse> list(User user, Pageable pageable) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::response);
    }

    /** Checks payment/trip/ledger agreement without changing any records. */
    public PaymentReconciliationResponse reconcile(User user) {
        List<PaymentIntent> intents = paymentRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                .getContent();
        Map<PaymentStatus, Long> rawCounts = new EnumMap<>(PaymentStatus.class);
        List<PaymentReconciliationResponse.Issue> issues = new ArrayList<>();
        long settledCents = 0;

        for (PaymentIntent intent : intents) {
            rawCounts.merge(intent.getStatus(), 1L, Long::sum);
            if (intent.getStatus() == PaymentStatus.SETTLED
                    || intent.getStatus() == PaymentStatus.REFUNDED) {
                settledCents += intent.getAmountCents();
                reconcileFinalIntent(intent, issues);
            } else if (intent.getTripId() != null) {
                issues.add(issue(intent, "Non-final payment unexpectedly references a trip"));
            }
        }

        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            counts.put(status.name(), rawCounts.getOrDefault(status, 0L));
        }
        return new PaymentReconciliationResponse(clock.instant(), counts, settledCents,
                issues.size(), issues.stream().limit(50).toList());
    }

    private PaymentIntent createAuthoritativeIntent(User user,
                                                    CreateJourneyPaymentRequest request,
                                                    String key,
                                                    String fingerprint) {
        AuthoritativeSelection selection = authoritativeSelection(
                user, request.from(), request.to(), request.journeyId());
        if (!selection.selected().fare().isPriced()) {
            if (!request.confirmUnknownFare()) {
                throw new FareConfirmationRequiredException(
                        selection.selected().journey().summary());
            }
            throw new InvalidStateException(
                    "A payment cannot be created until FareFlow has an authoritative fare");
        }
        PaymentIntent intent = createIntent(user, request, key, fingerprint, selection);
        // UUID ids are assigned before persistence, so Spring Data uses merge and
        // returns the managed instance. Continue with that instance or later state
        // transitions would modify the detached pre-merge object.
        intent = paymentRepository.save(intent);
        eventRepository.save(new PaymentEvent(intent.getId(), null, PaymentStatus.CREATED,
                "Authoritative fare calculated and payment intent created", clock.instant()));
        return intent;
    }

    private PaymentIntent createIntent(User user,
                                       CreateJourneyPaymentRequest request,
                                       String key,
                                       String fingerprint,
                                       AuthoritativeSelection selection) {
        Journey journey = selection.selected().journey();
        FareCalculation fare = selection.selected().fare();

        PersistedJourney snapshot = journeyRepository.save(
                PersistedJourney.snapshot(journey, fare, selection.origin(), selection.destination()));
        return PaymentIntent.create(
                user.getId(), snapshot, fare.totalFareCents(), request.paymentMethodOrWallet(), key,
                fingerprint, fastestPricedFare(selection.priced()).orElse(null), SelectedLabel.MANUAL,
                clock.instant());
    }

    private Trip recordUnpricedJourney(User user,
                                       AuthoritativeSelection selection,
                                       String idempotencyKey) {
        Journey journey = selection.selected().journey();
        PersistedJourney snapshot = journeyRepository.save(PersistedJourney.snapshot(
                journey, selection.selected().fare(), selection.origin(), selection.destination()));
        return tripRepository.save(new Trip(
                user.getId(), snapshot, 0, SelectedLabel.MANUAL,
                fastestPricedFare(selection.priced()).orElse(null), clock.instant(), idempotencyKey));
    }

    private AuthoritativeSelection authoritativeSelection(User user, String from, String to,
                                                          String journeyId) {
        LocationCandidate origin = resolve(from);
        LocationCandidate destination = resolve(to);
        List<JourneyPlanningService.PricedJourney> priced = planningService.plan(
                origin, destination, fareContextFor(user));
        JourneyPlanningService.PricedJourney selected = priced.stream()
                .filter(entry -> entry.journey().id().equals(journeyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "That journey is no longer available. Search again to see current options."));
        return new AuthoritativeSelection(origin, destination, priced, selected);
    }

    private Trip process(PaymentIntent intent, String simulatedCardToken, boolean allowFailed) {
        if (intent.getStatus() == PaymentStatus.SETTLED
                || intent.getStatus() == PaymentStatus.REFUNDED) {
            return trip(intent);
        }
        if (intent.getStatus() == PaymentStatus.FAILED && !allowFailed) {
            throw new InvalidStateException("This payment failed. Use the retry action to try again.");
        }
        if (intent.getStatus() != PaymentStatus.CREATED
                && intent.getStatus() != PaymentStatus.FAILED) {
            throw new InvalidStateException(
                    "Payment %s is already %s".formatted(intent.getId(), intent.getStatus()));
        }

        Instant now = clock.instant();
        String reference;
        if (intent.getPaymentMethod() == PaymentMethod.SIMULATED_CARD) {
            SimulatedCardGateway.Authorization authorization =
                    cardGateway.authorize(intent, simulatedCardToken);
            if (!authorization.approved()) {
                PaymentStatus previous = intent.fail(authorization.failureCode(),
                        authorization.failureMessage(), now);
                event(intent, previous, PaymentStatus.FAILED, authorization.failureMessage(), now);
                return null;
            }
            reference = authorization.providerReference();
        } else {
            reference = "fareflow_wallet_%s_%d"
                    .formatted(intent.getId(), intent.getAttemptCount() + 1);
        }

        PaymentStatus previous = intent.authorize(reference, now);
        event(intent, previous, PaymentStatus.AUTHORIZED, "Payment authorized", now);
        previous = intent.startProcessing(now);
        event(intent, previous, PaymentStatus.PROCESSING, "Settlement started", now);

        TransitSession session = null;
        Trip trip;
        if (intent.getTransitSessionId() != null) {
            session = sessionRepository.findById(intent.getTransitSessionId())
                    .orElseThrow(() -> new InvalidStateException(
                            "Payment %s references a missing transit session"
                                    .formatted(intent.getId())));
            if (session.getStatus() != TransitSessionStatus.COMPLETED) {
                throw new InvalidStateException(
                        "Only a completed transit session can be settled");
            }
            trip = tripRepository.save(new Trip(
                    intent.getUserId(), session, intent.getAmountCents(), now,
                    "payment:%s".formatted(intent.getId())));
        } else {
            trip = tripRepository.save(new Trip(
                    intent.getUserId(), intent.getJourney(), intent.getAmountCents(),
                    intent.getSelectedLabel(), intent.getBaselineFareCents(), now,
                    "payment:%s".formatted(intent.getId())));
        }

        if (intent.getAmountCents() > 0) {
            ledgerService.recordTripCharge(
                    intent.getUserId(), trip.getId(), intent.getId(), intent.getAmountCents(),
                    "%s%s — %s to %s".formatted(
                            session == null ? "" : "FareFlow usage · ", intent.getJourney().summary(),
                            intent.getJourney().getOriginDisplayName(),
                            intent.getJourney().getDestinationDisplayName()), now);
        }

        previous = intent.settle(trip.getId(), now);
        event(intent, previous, PaymentStatus.SETTLED,
                "Payment settled; trip and ledger committed", now);
        if (session != null) {
            session.markPaid(now);
        }
        return trip;
    }

    private void refund(PaymentIntent intent) {
        if (intent.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (intent.getStatus() != PaymentStatus.SETTLED) {
            throw new InvalidStateException(
                    "Only a settled payment can be refunded; this payment is %s"
                            .formatted(intent.getStatus()));
        }
        Trip trip = trip(intent);
        if (!trip.isCancelled()) {
            trip.markCancelled();
            tripRepository.save(trip);
        }
        Instant now = clock.instant();
        if (intent.getAmountCents() > 0) {
            ledgerService.recordRefund(intent.getUserId(), trip.getId(), intent.getId(),
                    intent.getAmountCents(),
                    "Refund: cancelled %s trip".formatted(trip.getProvider()), now);
        }
        PaymentStatus previous = intent.refund(now);
        event(intent, previous, PaymentStatus.REFUNDED,
                "Payment refunded; original charge retained", now);
    }

    private void reconcileFinalIntent(PaymentIntent intent,
                                      List<PaymentReconciliationResponse.Issue> issues) {
        if (intent.getTripId() == null || tripRepository.findById(intent.getTripId()).isEmpty()) {
            issues.add(issue(intent, "Final payment has no matching trip"));
            return;
        }
        List<LedgerEntry> entries = ledgerService.findForPayment(intent.getId());
        long net = entries.stream().mapToLong(LedgerEntry::getAmountCents).sum();
        long expected = intent.getStatus() == PaymentStatus.REFUNDED ? 0 : -intent.getAmountCents();
        if (net != expected) {
            issues.add(issue(intent,
                    "Ledger net is %d cents; expected %d cents".formatted(net, expected)));
        }
    }

    private PaymentIntentResponse response(PaymentIntent intent) {
        Trip trip = intent.getTripId() == null ? null
                : tripRepository.findById(intent.getTripId()).orElse(null);
        return PaymentIntentResponse.from(intent, trip,
                eventRepository.findByPaymentIntentIdOrderByIdAsc(intent.getId()));
    }

    private PaymentIntent owned(long userId, UUID id) {
        return paymentRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment %s was not found".formatted(id)));
    }

    private Trip trip(PaymentIntent intent) {
        if (intent.getTripId() == null) {
            throw new InvalidStateException(
                    "Payment %s has no settled trip".formatted(intent.getId()));
        }
        return tripRepository.findById(intent.getTripId())
                .orElseThrow(() -> new InvalidStateException(
                        "Payment %s references a missing trip".formatted(intent.getId())));
    }

    private LocationCandidate resolve(String query) {
        return locationService.resolve(query)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Could not find a place matching '%s'".formatted(query)));
    }

    private UserFareContext fareContextFor(User user) {
        var summary = budgetService.currentWeek(user.getId());
        return new UserFareContext(Math.max(0, summary.spentCents()), 0, Set.of());
    }

    private static Optional<Long> fastestPricedFare(
            List<JourneyPlanningService.PricedJourney> priced) {
        List<JourneyPlanningService.PricedJourney> pricedOnly = priced.stream()
                .filter(entry -> entry.fare().isPriced())
                .toList();
        if (pricedOnly.size() < 2) {
            return Optional.empty();
        }
        return pricedOnly.stream()
                .min(Comparator.comparingInt(entry -> entry.journey().totalMinutes()))
                .map(entry -> entry.fare().totalFareCents());
    }

    private void event(PaymentIntent intent, PaymentStatus from, PaymentStatus to,
                       String reason, Instant now) {
        eventRepository.save(new PaymentEvent(intent.getId(), from, to, reason, now));
    }

    private static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (key.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must be 200 characters or fewer");
        }
        return key.trim();
    }

    private static void verifyReplay(PaymentIntent intent, String fingerprint) {
        if (!intent.getRequestFingerprint().equals(fingerprint)) {
            throw new InvalidStateException(
                    "That idempotency key was already used for a different purchase");
        }
    }

    private static String fingerprint(CreateJourneyPaymentRequest request) {
        String canonical = String.join("|",
                request.from().trim().toLowerCase(java.util.Locale.ROOT),
                request.to().trim().toLowerCase(java.util.Locale.ROOT),
                request.journeyId().trim(),
                request.profile() == null ? "" : request.profile().trim(),
                Boolean.toString(request.confirmUnknownFare()),
                request.paymentMethodOrWallet().name());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String fingerprint(TransitSession session, PaymentMethod paymentMethod) {
        String canonical = "session|%s|%s|%d".formatted(
                session.getId(), paymentMethod.name(), session.getFinalFareCents());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static PaymentReconciliationResponse.Issue issue(
            PaymentIntent intent, String detail) {
        return new PaymentReconciliationResponse.Issue(
                intent.getId(), intent.getStatus().name(), detail);
    }

    public record Creation(PaymentIntentResponse payment, boolean replayed) {
    }

    private record AuthoritativeSelection(
            LocationCandidate origin,
            LocationCandidate destination,
            List<JourneyPlanningService.PricedJourney> priced,
            JourneyPlanningService.PricedJourney selected) {
    }
}
