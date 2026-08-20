package com.fareflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing configuration, bound from {@code fareflow.jwt.*}.
 *
 * <p>{@code secret} has no default. When authentication is enabled and the secret
 * is missing or too short, the application refuses to start rather than signing
 * tokens with a guessable key — see {@link JwtService}.
 *
 * <p>Demo mode never constructs this, so {@code JWT_SECRET} is not required to run
 * the demo.
 */
@ConfigurationProperties(prefix = "fareflow.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
