package com.fareflow.recommendation.dto;

import com.fareflow.recommendation.optimization.OptimizationWeights;

/**
 * The weights that produced a recommendation.
 *
 * <p>Returned on every response so any decision is reproducible: replay the same
 * weights against the same candidate set and the same route wins. When AI later
 * supplies these, this field is the audit trail proving it influenced only the
 * priorities and never selected the route.
 */
public record WeightsDto(
        double costPriority,
        double timePriority,
        double transferPriority,
        String source,
        double budgetPressure
) {

    public static WeightsDto from(OptimizationWeights weights) {
        return new WeightsDto(
                weights.costPriority(),
                weights.timePriority(),
                weights.transferPriority(),
                weights.source().name(),
                weights.budgetPressure());
    }
}
