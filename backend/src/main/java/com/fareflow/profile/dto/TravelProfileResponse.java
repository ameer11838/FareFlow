package com.fareflow.profile.dto;

import com.fareflow.profile.CommuteFrequency;
import com.fareflow.profile.PassPreference;
import com.fareflow.profile.PreferredTravelMode;
import com.fareflow.profile.UserTravelProfile;
import com.fareflow.recommendation.optimization.ContextProfile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The rider's profile as the client sees it.
 *
 * <p>Every enum is sent as an id <em>and</em> a display label. The id is what the
 * client sends back; the label is what it renders. That keeps the wording of
 * "3–4 days a week" in one place — here — instead of drifting between the
 * onboarding summary, the settings page, and the insights copy.
 *
 * @param weeklyBudgetCents read from the user record, which is the only place a
 *                          budget is stored. Null means no budget has been set,
 *                          which the UI shows as "Set a weekly budget" rather
 *                          than as $0.00
 */
public record TravelProfileResponse(
        boolean onboardingCompleted,
        Instant onboardingCompletedAt,

        String defaultContextProfile,
        String defaultContextProfileName,

        String weeklyCommuteFrequency,
        String weeklyCommuteFrequencyName,
        Integer estimatedCommuteDaysPerWeek,

        Long weeklyBudgetCents,

        String commuteKind,
        String commuteKindName,

        TypicalPlaceDto typicalOrigin,
        TypicalPlaceDto typicalDestination,
        boolean hasTypicalCommute,

        String passPreference,
        String passPreferenceName,

        String fareCategory,
        String fareCategoryName,

        List<ModeOption> preferredModes
) {

    /** A selected mode, carrying its own label so the client renders no vocabulary. */
    public record ModeOption(String id, String displayName) {
    }

    public static TravelProfileResponse from(UserTravelProfile profile, Long weeklyBudgetCents) {
        ContextProfile stance = profile.getDefaultContextProfile();
        CommuteFrequency frequency = profile.getWeeklyCommuteFrequency();
        PassPreference pass = profile.getPassPreference();

        return new TravelProfileResponse(
                profile.isOnboardingCompleted(),
                profile.getOnboardingCompletedAt(),

                stance.name(),
                stance.displayName(),

                frequency == null ? null : frequency.name(),
                frequency == null ? null : frequency.displayName(),
                frequency == null ? null : frequency.estimatedDaysPerWeek(),

                weeklyBudgetCents,

                profile.getCommuteKind() == null ? null : profile.getCommuteKind().name(),
                profile.getCommuteKind() == null ? null : profile.getCommuteKind().displayName(),

                TypicalPlaceDto.from(profile.getTypicalOrigin()),
                TypicalPlaceDto.from(profile.getTypicalDestination()),
                profile.hasTypicalCommute(),

                pass == null ? null : pass.name(),
                pass == null ? null : pass.displayName(),

                profile.getFareCategory().name(),
                profile.getFareCategory().displayName(),

                profile.getPreferredModes().stream()
                        // Declaration order, so the answers read back in the order
                        // they were offered rather than in hash order.
                        .sorted(Comparator.comparingInt(Enum::ordinal))
                        .map(mode -> new ModeOption(mode.name(), mode.displayName()))
                        .toList());
    }

    /** Convenience for building the mode catalogue from the enum itself. */
    public static List<ModeOption> allModes() {
        return java.util.Arrays.stream(PreferredTravelMode.values())
                .map(mode -> new ModeOption(mode.name(), mode.displayName()))
                .toList();
    }
}
