package com.fareflow.recommendation.optimization;

/**
 * The inputs a {@link PreferenceResolver} may consider when choosing weights.
 *
 * <p>Budget fields are nullable: recommendations can be requested without a user,
 * in which case the resolver falls back to the profile's base weights.
 *
 * <p>A future natural-language field ({@code contextText}) would be added here,
 * consumed only by an AI-backed resolver, and would still result in nothing more
 * than a set of {@link OptimizationWeights}.
 */
public record PreferenceContext(
        Long userId,
        Long weeklyBudgetCents,
        Long spentCentsThisWeek,
        ContextProfile profile
) {

    public PreferenceContext {
        if (profile == null) {
            profile = ContextProfile.defaultProfile();
        }
    }

    public static PreferenceContext anonymous() {
        return new PreferenceContext(null, null, null, ContextProfile.defaultProfile());
    }

    public static PreferenceContext anonymous(ContextProfile profile) {
        return new PreferenceContext(null, null, null, profile);
    }

    /**
     * How squeezed the user's weekly budget is, in [0,1]. Zero when there is no
     * user, no budget, or a zero budget — which is also what keeps this free of
     * division by zero.
     */
    public double budgetPressure() {
        if (weeklyBudgetCents == null || weeklyBudgetCents <= 0 || spentCentsThisWeek == null) {
            return 0.0;
        }
        double ratio = (double) spentCentsThisWeek / (double) weeklyBudgetCents;
        return Math.clamp(ratio, 0.0, 1.0);
    }
}
