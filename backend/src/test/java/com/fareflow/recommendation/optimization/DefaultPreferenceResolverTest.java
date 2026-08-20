package com.fareflow.recommendation.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPreferenceResolverTest {

    private static final org.assertj.core.data.Offset<Double> TOLERANCE =
            org.assertj.core.data.Offset.offset(1e-9);

    private final DefaultPreferenceResolver resolver = DefaultPreferenceResolver.withDefaults();

    @Test
    @DisplayName("no user means the configured defaults, unmodified")
    void anonymousUsesDefaults() {
        OptimizationWeights weights = resolver.resolve(PreferenceContext.anonymous());

        assertThat(weights.source()).isEqualTo(WeightSource.DEFAULT);
        assertThat(weights.costPriority()).isEqualTo(0.45, TOLERANCE);
        assertThat(weights.budgetPressure()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a zero budget produces no pressure rather than dividing by zero")
    void zeroBudgetIsSafe() {
        OptimizationWeights weights =
                resolver.resolve(new PreferenceContext(1L, 0L, 5_000L, ContextProfile.BALANCED));

        assertThat(weights.budgetPressure()).isEqualTo(0.0);
        assertThat(weights.source()).isEqualTo(WeightSource.DEFAULT);
    }

    @Test
    @DisplayName("spending nothing leaves the weights at their defaults")
    void noSpendNoShift() {
        OptimizationWeights weights =
                resolver.resolve(new PreferenceContext(1L, 5_000L, 0L, ContextProfile.BALANCED));

        assertThat(weights.costPriority()).isEqualTo(0.45, TOLERANCE);
        assertThat(weights.timePriority()).isEqualTo(0.45, TOLERANCE);
    }

    @Test
    @DisplayName("half the budget spent shifts weight from time to cost symmetrically")
    void halfBudgetShifts() {
        OptimizationWeights weights =
                resolver.resolve(new PreferenceContext(1L, 5_000L, 2_500L, ContextProfile.BALANCED));

        // shift = 0.40 * 0.5 * 0.45 = 0.09
        assertThat(weights.budgetPressure()).isEqualTo(0.5, TOLERANCE);
        assertThat(weights.costPriority()).isEqualTo(0.54, TOLERANCE);
        assertThat(weights.timePriority()).isEqualTo(0.36, TOLERANCE);
        assertThat(weights.transferPriority()).isEqualTo(0.10, TOLERANCE);
        assertThat(weights.source()).isEqualTo(WeightSource.BUDGET_ADJUSTED);
    }

    @Test
    @DisplayName("a fully spent budget produces the maximum shift and still sums to 1")
    void fullPressure() {
        OptimizationWeights weights =
                resolver.resolve(new PreferenceContext(1L, 5_000L, 5_000L, ContextProfile.BALANCED));

        // shift = 0.40 * 1.0 * 0.45 = 0.18
        assertThat(weights.costPriority()).isEqualTo(0.63, TOLERANCE);
        assertThat(weights.timePriority()).isEqualTo(0.27, TOLERANCE);
        assertThat(sum(weights)).isEqualTo(1.0, TOLERANCE);
    }

    @Test
    @DisplayName("overspending clamps pressure at 1 instead of running away")
    void pressureIsClamped() {
        OptimizationWeights weights =
                resolver.resolve(new PreferenceContext(1L, 5_000L, 50_000L, ContextProfile.BALANCED));

        assertThat(weights.budgetPressure()).isEqualTo(1.0);
        assertThat(weights.costPriority()).isEqualTo(0.63, TOLERANCE);
    }

    @Test
    @DisplayName("a null context is treated as anonymous")
    void nullContext() {
        assertThat(resolver.resolve(null).source()).isEqualTo(WeightSource.DEFAULT);
    }

    @Test
    @DisplayName("budget pressure rises monotonically with spending")
    void monotonic() {
        double previous = -1;
        for (long spent = 0; spent <= 5_000; spent += 500) {
            double pressure = new PreferenceContext(1L, 5_000L, spent, ContextProfile.BALANCED).budgetPressure();
            assertThat(pressure).isGreaterThanOrEqualTo(previous);
            previous = pressure;
        }
    }

    private static double sum(OptimizationWeights weights) {
        return weights.costPriority() + weights.timePriority() + weights.transferPriority();
    }
}
