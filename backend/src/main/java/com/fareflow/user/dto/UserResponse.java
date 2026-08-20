package com.fareflow.user.dto;

import com.fareflow.user.User;

/**
 * The caller's own identity.
 *
 * @param weeklyBudgetCents   null when no budget has been set. The client shows
 *                            "Set a weekly budget" rather than $0.00
 * @param onboardingCompleted whether the rider has finished onboarding. Carried
 *                            here so the app can route a fresh account straight
 *                            to /onboarding without a second round trip on every
 *                            page load
 */
public record UserResponse(
        long id,
        String name,
        String email,
        Long weeklyBudgetCents,
        String timezone,
        boolean onboardingCompleted
) {

    public static UserResponse from(User user, boolean onboardingCompleted) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getWeeklyBudgetCents(),
                user.getTimezone(),
                onboardingCompleted);
    }
}
