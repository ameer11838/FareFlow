package com.fareflow.discovery;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.discovery.dto.JourneySearchResponse;
import com.fareflow.discovery.dto.TakeJourneyRequest;
import com.fareflow.trip.dto.TripResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.fareflow.fare.UserFareContext;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.recommendation.optimization.ContextProfile;
import com.fareflow.recommendation.optimization.PreferenceContext;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Arbitrary origin-to-destination journey search.
 *
 * <p>Unlike the seeded-route endpoint this places no constraint on where a rider is
 * going: both ends are geocoded, and journeys are planned over the transit network.
 *
 * <p>Public, like the recommendation endpoint — a journey quote carries no personal
 * data. When a signed-in user is present their weekly spend feeds budget pressure
 * and fare caps, but that is derived server-side from the authenticated identity,
 * never from a parameter.
 */
@RestController
@RequestMapping("/api/journeys")
public class JourneyController {

    private final JourneyRecommendationService recommendationService;
    private final TakeJourneyService takeJourneyService;
    private final CurrentUserService currentUserService;
    private final BudgetService budgetService;
    private final TravelProfileService travelProfileService;

    public JourneyController(JourneyRecommendationService recommendationService,
                             TakeJourneyService takeJourneyService,
                             CurrentUserService currentUserService,
                             BudgetService budgetService,
                             TravelProfileService travelProfileService) {
        this.recommendationService = recommendationService;
        this.takeJourneyService = takeJourneyService;
        this.currentUserService = currentUserService;
        this.budgetService = budgetService;
        this.travelProfileService = travelProfileService;
    }

    /**
     * Takes a discovered journey: snapshots it, creates the trip, and charges the
     * ledger — atomically, at a fare this server computes.
     *
     * <p>The {@code Idempotency-Key} header makes a double-submitted selection safe.
     * Requires authentication: this moves money.
     */
    @PostMapping("/take")
    public ResponseEntity<TripResponse> take(
            @Valid @RequestBody TakeJourneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        var user = currentUserService.require();
        var trip = takeJourneyService.take(user, request, idempotencyKey);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(TripResponse.from(trip));
    }

    @GetMapping
    public JourneySearchResponse search(
            @RequestParam @NotBlank(message = "from is required") String from,
            @RequestParam @NotBlank(message = "to is required") String to,
            @RequestParam(required = false) String profile) {

        // Personalise only when a session exists. An anonymous quote is still useful,
        // it just has no budget pressure, no cap headroom, and no saved default.
        PreferenceContext preference = PreferenceContext.anonymous(
                travelProfileService.resolveContextProfile(profile, null));
        UserFareContext fareContext = UserFareContext.anonymous();

        try {
            var user = currentUserService.require();

            // Three inputs, in strict order of authority: what the rider asked for
            // right now, then their onboarding default, then BALANCED. A stated
            // situation always beats a stated habit.
            ContextProfile selected =
                    travelProfileService.resolveContextProfile(profile, user.getId());

            WeeklySummary summary = budgetService.currentWeek(user.getId());
            preference = new PreferenceContext(user.getId(), summary.weeklyBudgetCents(),
                    summary.spentCents(), selected);
            fareContext = new UserFareContext(
                    Math.max(0, summary.spentCents()), 0, Set.of());
        } catch (com.fareflow.auth.UnauthenticatedException | com.fareflow.exception.ResourceNotFoundException ignored) {
            // Not signed in, or no demo user seeded: fall through with the anonymous
            // context. Narrow on purpose -- a broad catch here would swallow a bad
            // profile name and silently score the trip with the wrong weights.
        }

        return recommendationService.search(from.trim(), to.trim(), preference, fareContext);
    }
}
