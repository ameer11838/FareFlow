package com.fareflow.recommendation.optimization;

/**
 * How much each dimension matters when scoring routes.
 *
 * <p><strong>This type is the AI boundary.</strong> Note what it does not contain:
 * no fare, no route id, no provider, no dollar amount, no budget. A future
 * natural-language component can produce one of these and nothing else, so it is
 * structurally incapable of naming a route or computing money. That guarantee is
 * enforced by the type system rather than by convention.
 *
 * <p>The three priorities are normalised on construction so they always sum to 1.
 * Values are validated eagerly: NaN, infinity, negatives, and all-zero weight
 * vectors are rejected rather than silently producing meaningless scores.
 */
public record OptimizationWeights(
        double costPriority,
        double timePriority,
        double transferPriority,
        WeightSource source,
        double budgetPressure
) {

    private static final double MINIMUM_WEIGHT_SUM = 1e-9;

    public static final double DEFAULT_COST_PRIORITY = 0.45;
    public static final double DEFAULT_TIME_PRIORITY = 0.45;
    public static final double DEFAULT_TRANSFER_PRIORITY = 0.10;

    public OptimizationWeights {
        requireValidPriority(costPriority, "costPriority");
        requireValidPriority(timePriority, "timePriority");
        requireValidPriority(transferPriority, "transferPriority");

        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (!Double.isFinite(budgetPressure) || budgetPressure < 0.0 || budgetPressure > 1.0) {
            throw new IllegalArgumentException(
                    "budgetPressure must be a finite value in [0,1] but was " + budgetPressure);
        }

        double sum = costPriority + timePriority + transferPriority;
        if (sum <= MINIMUM_WEIGHT_SUM) {
            throw new IllegalArgumentException("Priorities must sum to more than zero");
        }

        // Normalise so the weights always sum to 1. This keeps scores comparable
        // and means callers can pass raw proportions without pre-scaling them.
        costPriority = costPriority / sum;
        timePriority = timePriority / sum;
        transferPriority = transferPriority / sum;
    }

    /** The configured Phase 1 defaults: 0.45 cost / 0.45 time / 0.10 transfers. */
    public static OptimizationWeights defaults() {
        return new OptimizationWeights(
                DEFAULT_COST_PRIORITY,
                DEFAULT_TIME_PRIORITY,
                DEFAULT_TRANSFER_PRIORITY,
                WeightSource.DEFAULT,
                0.0);
    }

    /** Convenience factory for tests and callers that do not care about provenance. */
    public static OptimizationWeights of(double cost, double time, double transfer) {
        return new OptimizationWeights(cost, time, transfer, WeightSource.DEFAULT, 0.0);
    }

    private static void requireValidPriority(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number but was " + value);
        }
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0,1] but was " + value);
        }
    }
}
