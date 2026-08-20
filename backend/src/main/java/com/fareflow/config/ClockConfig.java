package com.fareflow.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injecting a {@link Clock} rather than calling {@code Instant.now()} directly
 * makes time a dependency, which means tests can pin it and assert on week
 * boundaries deterministically.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
