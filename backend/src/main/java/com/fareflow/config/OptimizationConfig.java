package com.fareflow.config;

import com.fareflow.recommendation.optimization.DefaultPreferenceResolver;
import com.fareflow.recommendation.optimization.ExplanationBuilder;
import com.fareflow.recommendation.optimization.NormalizedWeightedScorer;
import com.fareflow.recommendation.optimization.PreferenceResolver;
import com.fareflow.recommendation.optimization.RouteScorer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the optimization engine into the Spring context.
 *
 * <p>The beans are declared here, in the config package, rather than by annotating
 * the classes themselves. That is what keeps
 * {@code com.fareflow.recommendation.optimization} free of any Spring import, so
 * every scoring class can be constructed with {@code new} in a plain JUnit test.
 */
@Configuration
@EnableConfigurationProperties(OptimizationProperties.class)
public class OptimizationConfig {

    @Bean
    public RouteScorer routeScorer() {
        return new NormalizedWeightedScorer();
    }

    @Bean
    public ExplanationBuilder explanationBuilder() {
        return new ExplanationBuilder();
    }

    @Bean
    public PreferenceResolver preferenceResolver(OptimizationProperties properties) {
        return new DefaultPreferenceResolver(
                properties.defaultCostPriority(),
                properties.defaultTimePriority(),
                properties.defaultTransferPriority(),
                properties.budgetPressureBeta());
    }
}
