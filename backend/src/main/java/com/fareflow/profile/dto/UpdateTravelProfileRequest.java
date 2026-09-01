package com.fareflow.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * The whole profile, sent as one document.
 *
 * <p>A full replace rather than a patch: onboarding and the settings page both
 * render every field, so a partial update would only introduce a question about
 * what an omitted field means. Here it means one thing — cleared.
 *
 * <p>Enums arrive as names, not as numbers or weights. The server looks up what a
 * name means, exactly as it does for {@code ContextProfile}, so a client can never
 * supply its own scoring weights or its own vocabulary.
 *
 * @param weeklyBudgetCents null is a real answer: "I'm not sure". It clears the
 *                          budget rather than setting it to zero, because zero is
 *                          a budget and no budget is not
 */
public record UpdateTravelProfileRequest(

        String defaultContextProfile,

        String weeklyCommuteFrequency,

        // $2,000 a week is far beyond any real transit budget; a number above it is
        // a typo (cents entered as dollars), and silently accepting it would make
        // budget pressure meaningless for that rider.
        @Min(value = 0, message = "Weekly budget must be zero or greater")
        @Max(value = 200_000, message = "Weekly budget must be $2,000.00 or less")
        Long weeklyBudgetCents,

        String commuteKind,

        @Valid TypicalPlaceDto typicalOrigin,

        @Valid TypicalPlaceDto typicalDestination,

        String passPreference,

        String fareCategory,

        List<String> preferredModes
) {
}
