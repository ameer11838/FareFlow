package com.fareflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default optimization weights, bound from {@code fareflow.optimization.*}.
 *
 * <p>Configuration rather than constants so the weights can be tuned per
 * environment without a rebuild — and so the numbers are visible in one place
 * instead of buried as magic values in the scorer.
 */
@ConfigurationProperties(prefix = "fareflow.optimization")
public record OptimizationProperties(
        double defaultCostPriority,
        double defaultTimePriority,
        double defaultTransferPriority,
        double budgetPressureBeta
) {
}
