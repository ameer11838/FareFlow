package com.fareflow.profile;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.profile.dto.ProfileOptionsResponse;
import com.fareflow.profile.dto.TravelProfileResponse;
import com.fareflow.profile.dto.UpdateTravelProfileRequest;
import com.fareflow.user.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rider's own travel profile.
 *
 * <p><strong>There is no {@code userId} anywhere in this contract.</strong> The
 * identity comes from the JWT via {@link CurrentUserService}, exactly as it does
 * for trips and the ledger, so "update someone else's profile" is not a request
 * this API can express — it is not a check that can be forgotten.
 *
 * <p>Two write endpoints, one operation. {@code PUT /api/onboarding} is the
 * onboarding submission and marks onboarding finished; {@code PUT /api/profile} is
 * the ongoing settings edit and leaves that flag alone. Same body, same validation,
 * different meaning — which is precisely what two resources are for. Neither is a
 * PATCH: both screens render every field, so a full replace has no ambiguity to
 * resolve about omitted values.
 */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private final TravelProfileService travelProfileService;
    private final CurrentUserService currentUserService;

    public ProfileController(TravelProfileService travelProfileService,
                             CurrentUserService currentUserService) {
        this.travelProfileService = travelProfileService;
        this.currentUserService = currentUserService;
    }

    /**
     * The vocabularies onboarding may offer. Public and static: it contains no
     * personal data, and the client must not invent its own options.
     */
    @GetMapping("/profile/options")
    public ProfileOptionsResponse options() {
        return ProfileOptionsResponse.build();
    }

    @GetMapping("/profile")
    public TravelProfileResponse profile() {
        User user = currentUserService.require();
        return TravelProfileResponse.from(
                travelProfileService.findOrEmpty(user.getId()), user.getWeeklyBudgetCents());
    }

    /** Editing from Settings. Never re-opens onboarding for someone who finished it. */
    @PutMapping("/profile")
    public TravelProfileResponse update(@Valid @RequestBody UpdateTravelProfileRequest request) {
        User user = currentUserService.require();
        UserTravelProfile saved = travelProfileService.save(user, request, false);
        return TravelProfileResponse.from(saved, user.getWeeklyBudgetCents());
    }

    /** The onboarding submission. Same payload; this one finishes onboarding. */
    @PutMapping("/onboarding")
    public TravelProfileResponse completeOnboarding(@Valid @RequestBody UpdateTravelProfileRequest request) {
        User user = currentUserService.require();
        UserTravelProfile saved = travelProfileService.save(user, request, true);
        return TravelProfileResponse.from(saved, user.getWeeklyBudgetCents());
    }
}
