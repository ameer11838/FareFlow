package com.fareflow.session;

import com.fareflow.budget.BudgetService;
import com.fareflow.discovery.JourneyPlanningService;
import com.fareflow.exception.InvalidStateException;
import com.fareflow.exception.ResourceNotFoundException;
import com.fareflow.fare.UserFareContext;
import com.fareflow.journey.PersistedJourney;
import com.fareflow.journey.PersistedJourneyRepository;
import com.fareflow.location.LocationCandidate;
import com.fareflow.location.LocationService;
import com.fareflow.session.dto.StartTransitSessionRequest;
import com.fareflow.session.dto.TransitSessionResponse;
import com.fareflow.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransitSessionService {

    private static final List<TransitSessionStatus> OPEN = List.of(
            TransitSessionStatus.STARTED, TransitSessionStatus.IN_PROGRESS,
            TransitSessionStatus.COMPLETED);

    private final TransitSessionRepository sessionRepository;
    private final PersistedJourneyRepository journeyRepository;
    private final LocationService locationService;
    private final JourneyPlanningService planningService;
    private final BudgetService budgetService;
    private final UsageFareEngine usageFareEngine;
    private final Clock clock;

    public TransitSessionService(TransitSessionRepository sessionRepository,
                                 PersistedJourneyRepository journeyRepository,
                                 LocationService locationService,
                                 JourneyPlanningService planningService,
                                 BudgetService budgetService,
                                 UsageFareEngine usageFareEngine,
                                 Clock clock) {
        this.sessionRepository = sessionRepository;
        this.journeyRepository = journeyRepository;
        this.locationService = locationService;
        this.planningService = planningService;
        this.budgetService = budgetService;
        this.usageFareEngine = usageFareEngine;
        this.clock = clock;
    }

    @Transactional
    public Creation start(User user, StartTransitSessionRequest request, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        String fingerprint = fingerprint(request);
        Optional<TransitSession> replay = sessionRepository
                .findByUserIdAndIdempotencyKey(user.getId(), key);
        if (replay.isPresent()) {
            if (!replay.get().getRequestFingerprint().equals(fingerprint)) {
                throw new InvalidStateException(
                        "That idempotency key was already used for another transit session");
            }
            return new Creation(response(replay.get()), true);
        }

        if (sessionRepository.findFirstByUserIdAndStatusInOrderByStartedAtDesc(
                user.getId(), OPEN).isPresent()) {
            throw new InvalidStateException(
                    "Finish the current transit session before starting another trip");
        }

        LocationCandidate origin = resolve(request.from());
        LocationCandidate destination = resolve(request.to());
        var summary = budgetService.currentWeek(user.getId());
        var candidates = planningService.plan(origin, destination,
                new UserFareContext(Math.max(0, summary.spentCents()), 0, Set.of()));
        var selected = candidates.stream()
                .filter(entry -> entry.journey().id().equals(request.journeyId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "That journey is no longer available. Search again to start a trip."));

        PersistedJourney journey = journeyRepository.save(PersistedJourney.snapshot(
                selected.journey(), selected.fare(), origin, destination));
        int totalUnits = usageFareEngine.totalProgressUnits(journey);
        UsageFareCalculation minimum = usageFareEngine.calculate(journey, 1);
        UsageFareCalculation maximum = usageFareEngine.calculate(journey, totalUnits);
        TransitSession session = TransitSession.start(
                user.getId(), journey, totalUnits, usageFareEngine.plannedDistanceMetres(journey),
                minimum.totalCents(), maximum.totalCents(), usageFareEngine.version(), key,
                fingerprint, clock.instant());
        session = sessionRepository.save(session);
        return new Creation(response(session), false);
    }

    @Transactional
    public TransitSessionResponse advance(User user, UUID id) {
        TransitSession session = owned(user, id);
        UsageFareCalculation fare = usageFareEngine.calculate(
                session.getJourney(), session.getProgressUnitsCompleted() + 1);
        session.advance(fare, clock.instant());
        return response(session);
    }

    @Transactional
    public TransitSessionResponse end(User user, UUID id) {
        TransitSession session = owned(user, id);
        UsageFareCalculation fare = usageFareEngine.calculate(
                session.getJourney(), session.getProgressUnitsCompleted());
        session.end(fare, clock.instant());
        return response(session);
    }

    public TransitSessionResponse get(User user, UUID id) {
        return response(owned(user, id));
    }

    public Optional<TransitSessionResponse> active(User user) {
        return sessionRepository.findFirstByUserIdAndStatusInOrderByStartedAtDesc(
                        user.getId(), OPEN)
                .map(this::response);
    }

    public TransitSession owned(User user, UUID id) {
        return sessionRepository.findByUserIdAndId(user.getId(), id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transit session %s was not found".formatted(id)));
    }

    public TransitSessionResponse response(TransitSession session) {
        return TransitSessionResponse.from(session, usageFareEngine, clock.instant());
    }

    private LocationCandidate resolve(String query) {
        return locationService.resolve(query)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Could not find a place matching '%s'".formatted(query)));
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (key.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must be 200 characters or fewer");
        }
        return key.trim();
    }

    private static String fingerprint(StartTransitSessionRequest request) {
        String canonical = String.join("|",
                request.from().trim().toLowerCase(java.util.Locale.ROOT),
                request.to().trim().toLowerCase(java.util.Locale.ROOT),
                request.journeyId().trim(),
                request.profile() == null ? "" : request.profile().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Creation(TransitSessionResponse session, boolean replayed) {
    }
}
