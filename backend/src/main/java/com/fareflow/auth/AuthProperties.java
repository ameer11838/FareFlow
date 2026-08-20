package com.fareflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether authentication is enforced, bound from {@code fareflow.auth.*}.
 *
 * <p>One codebase, two modes. With {@code enabled=false} the app runs as a demo:
 * every request resolves to the single seeded demo user. With {@code enabled=true}
 * identity comes from a JWT and nothing else.
 *
 * <p>A feature flag rather than a Spring profile because the difference is a single
 * runtime decision, not a different set of beans — profiles would scatter the same
 * choice across several configuration classes and make the demo path harder to test.
 */
@ConfigurationProperties(prefix = "fareflow.auth")
public record AuthProperties(boolean enabled, String demoUserEmail) {

    public AuthProperties {
        if (demoUserEmail == null || demoUserEmail.isBlank()) {
            demoUserEmail = "demo@fareflow.app";
        }
    }
}
