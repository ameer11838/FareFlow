package com.fareflow.recommendation.optimization;

/**
 * Where a set of {@link OptimizationWeights} came from.
 *
 * <p>Recorded on every recommendation response so any decision can be audited
 * and replayed. When AI arrives in a later phase it produces {@link #AI_DERIVED}
 * weights and nothing else — it never selects a route or touches money.
 */
public enum WeightSource {

    /** Configured Phase 1 defaults, unmodified. */
    DEFAULT,

    /** A named {@link ContextProfile} the user chose, such as RUSH or SAVE_MONEY. */
    PROFILE,

    /** Weights shifted toward cost because the user is deep into their weekly budget. */
    BUDGET_ADJUSTED,

    /** Reserved: produced by a future natural-language component, after sanitisation. */
    AI_DERIVED
}
