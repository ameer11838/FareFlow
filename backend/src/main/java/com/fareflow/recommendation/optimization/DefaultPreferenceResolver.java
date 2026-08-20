package com.fareflow.recommendation.optimization;

/**
 * Phase 1 preference resolution: start from the chosen {@link ContextProfile}'s base
 * weights, then shift toward cost as the user's weekly budget fills up.
 *
 * <pre>
 *   p     = clamp(spentThisWeek / weeklyBudget, 0, 1)
 *   shift = beta * p * baseTimePriority
 *   timePriority = baseTimePriority - shift
 *   costPriority = baseCostPriority + shift
 * </pre>
 *
 * <p>The shift is symmetric — time loses exactly what cost gains — so the weights
 * still sum to 1 and there is no renormalisation step to get wrong. With BALANCED
 * (0.45/0.45/0.10) and beta 0.4, full budget pressure yields 0.63 cost / 0.27 time /
 * 0.10 transfers. Bounded, monotonic, and easy to test.
 *
 * <p>Note the ordering: the user's stated intent sets the baseline, and budget
 * reality nudges it. Someone who says "I'm in a rush" still gets a time-weighted
 * result even when broke — just slightly less so.
 *
 * <p>Plain Java: configuration values arrive through the constructor so this class
 * carries no Spring annotations and can be unit tested directly.
 */
public final class DefaultPreferenceResolver implements PreferenceResolver {

    private final double baseCostPriority;
    private final double baseTimePriority;
    private final double baseTransferPriority;
    private final double budgetPressureBeta;

    public DefaultPreferenceResolver(double baseCostPriority,
                                     double baseTimePriority,
                                     double baseTransferPriority,
                                     double budgetPressureBeta) {
        if (!Double.isFinite(budgetPressureBeta) || budgetPressureBeta < 0.0 || budgetPressureBeta > 1.0) {
            throw new IllegalArgumentException(
                    "budgetPressureBeta must be a finite value in [0,1] but was " + budgetPressureBeta);
        }
        // Validate the base triple eagerly by constructing a weights instance.
        new OptimizationWeights(baseCostPriority, baseTimePriority, baseTransferPriority,
                WeightSource.DEFAULT, 0.0);

        this.baseCostPriority = baseCostPriority;
        this.baseTimePriority = baseTimePriority;
        this.baseTransferPriority = baseTransferPriority;
        this.budgetPressureBeta = budgetPressureBeta;
    }

    /** Resolver using the built-in Phase 1 defaults. */
    public static DefaultPreferenceResolver withDefaults() {
        return new DefaultPreferenceResolver(
                OptimizationWeights.DEFAULT_COST_PRIORITY,
                OptimizationWeights.DEFAULT_TIME_PRIORITY,
                OptimizationWeights.DEFAULT_TRANSFER_PRIORITY,
                0.40);
    }

    @Override
    public OptimizationWeights resolve(PreferenceContext context) {
        PreferenceContext effective = context == null ? PreferenceContext.anonymous() : context;
        ContextProfile profile = effective.profile();

        // BALANCED uses the configured defaults so operators can tune them without a
        // rebuild. Named profiles carry their own weights, which are fixed by design.
        boolean balanced = profile == ContextProfile.BALANCED;
        double baseCost = balanced ? baseCostPriority : profile.costPriority();
        double baseTime = balanced ? baseTimePriority : profile.timePriority();
        double baseTransfer = balanced ? baseTransferPriority : profile.transferPriority();

        double pressure = effective.budgetPressure();

        if (pressure == 0.0) {
            return new OptimizationWeights(baseCost, baseTime, baseTransfer,
                    balanced ? WeightSource.DEFAULT : WeightSource.PROFILE, 0.0);
        }

        double shift = budgetPressureBeta * pressure * baseTime;
        // Clamp defensively: unusual base configurations could otherwise push a
        // priority outside [0,1] and trip the OptimizationWeights validation.
        return new OptimizationWeights(
                Math.clamp(baseCost + shift, 0.0, 1.0),
                Math.clamp(baseTime - shift, 0.0, 1.0),
                baseTransfer,
                WeightSource.BUDGET_ADJUSTED,
                pressure);
    }
}
