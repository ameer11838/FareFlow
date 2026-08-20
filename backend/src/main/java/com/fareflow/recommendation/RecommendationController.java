package com.fareflow.recommendation;

import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.recommendation.dto.ProfileDto;
import com.fareflow.recommendation.dto.RecommendationResponse;
import com.fareflow.recommendation.optimization.ContextProfile;
import com.fareflow.recommendation.optimization.PreferenceContext;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Route search.
 *
 * <p>{@code userId} is optional. Without it the engine uses the profile's base
 * weights; with it, weights also shift toward cost as the weekly budget fills up.
 *
 * <p>{@code profile} names a stance — the server looks up the weights. Raw weights are
 * deliberately not accepted over the wire: every financial trade-off decision stays
 * inside the Java engine.
 *
 * <p>When {@code profile} is omitted the rider's onboarding default applies. When it
 * is present it wins: what someone says about <em>this</em> trip outranks what they
 * said about their travel in general.
 *
 * <p>A future phase adds {@code POST /api/recommendations} carrying natural-language
 * context, which resolves to a profile or a sanitised weight vector. Both would
 * delegate to the same service, so this contract does not break.
 */
// No @Validated here on purpose: that annotation activates AOP-based method
// validation, which raises ConstraintViolationException. Spring MVC 6.1+ validates
// constrained handler parameters natively and raises HandlerMethodValidationException,
// which carries the parameter details the exception handler needs.
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final BudgetService budgetService;
    private final TravelProfileService travelProfileService;

    public RecommendationController(RecommendationService recommendationService,
                                    BudgetService budgetService,
                                    TravelProfileService travelProfileService) {
        this.recommendationService = recommendationService;
        this.budgetService = budgetService;
        this.travelProfileService = travelProfileService;
    }

    /** The stances a client may choose from, so the UI never hardcodes weights. */
    @GetMapping("/profiles")
    public List<ProfileDto> profiles() {
        return ProfileDto.all();
    }

    @GetMapping
    public RecommendationResponse recommend(
            @RequestParam @NotBlank(message = "origin is required") String origin,
            @RequestParam @NotBlank(message = "destination is required") String destination,
            @RequestParam(required = false) String profile,
            @RequestParam(required = false) Long userId) {

        // current request > onboarding default > BALANCED
        ContextProfile selected = travelProfileService.resolveContextProfile(profile, userId);

        if (userId == null) {
            return recommendationService.recommend(origin.trim(), destination.trim(),
                    PreferenceContext.anonymous(selected), null);
        }

        WeeklySummary summary = budgetService.currentWeek(userId);
        PreferenceContext context = new PreferenceContext(
                userId, summary.weeklyBudgetCents(), summary.spentCents(), selected);

        return recommendationService.recommend(origin.trim(), destination.trim(),
                context, summary.remainingCents());
    }
}
