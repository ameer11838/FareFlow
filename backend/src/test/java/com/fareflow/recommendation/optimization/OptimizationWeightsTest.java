package com.fareflow.recommendation.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This type is the AI boundary. Its validation is what guarantees that a future
 * model can only ever hand the engine three bounded, finite numbers.
 */
class OptimizationWeightsTest {

    @Test
    @DisplayName("defaults are 0.45 / 0.45 / 0.10 and sum to 1")
    void defaults() {
        OptimizationWeights weights = OptimizationWeights.defaults();

        assertThat(weights.costPriority()).isEqualTo(0.45);
        assertThat(weights.timePriority()).isEqualTo(0.45);
        assertThat(weights.transferPriority()).isEqualTo(0.10);
        assertThat(weights.source()).isEqualTo(WeightSource.DEFAULT);
        assertThat(sum(weights)).isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("raw proportions are normalised to sum to 1")
    void normalises() {
        OptimizationWeights weights = OptimizationWeights.of(0.2, 0.2, 0.1);

        assertThat(sum(weights)).isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(weights.costPriority()).isEqualTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("NaN and infinity are rejected")
    void rejectsNonFinite() {
        assertThatThrownBy(() -> OptimizationWeights.of(Double.NaN, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> OptimizationWeights.of(Double.POSITIVE_INFINITY, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("values outside [0,1] are rejected")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> OptimizationWeights.of(-0.1, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OptimizationWeights.of(1.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an all-zero weight vector is rejected rather than dividing by zero")
    void rejectsAllZero() {
        assertThatThrownBy(() -> OptimizationWeights.of(0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to more than zero");
    }

    @Test
    @DisplayName("budget pressure must be a finite value in [0,1]")
    void validatesBudgetPressure() {
        assertThatThrownBy(() ->
                new OptimizationWeights(0.5, 0.5, 0.0, WeightSource.DEFAULT, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new OptimizationWeights(0.5, 0.5, 0.0, WeightSource.DEFAULT, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("source is required")
    void requiresSource() {
        assertThatThrownBy(() -> new OptimizationWeights(0.5, 0.5, 0.0, null, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the type carries no monetary or route fields")
    void carriesNoMoney() {
        // Guards the AI boundary structurally: if someone adds a fare or route id
        // to this record, this test fails and the review conversation happens.
        assertThat(OptimizationWeights.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("costPriority", "timePriority", "transferPriority",
                        "source", "budgetPressure");
    }

    private static double sum(OptimizationWeights weights) {
        return weights.costPriority() + weights.timePriority() + weights.transferPriority();
    }
}
