package com.fareflow.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies JSON Web Tokens.
 *
 * <p>The subject is the user id, so every protected request derives its identity
 * from a signed token rather than from anything the browser supplies. Nothing in
 * this class trusts a request parameter.
 *
 * <p>Plain Java: the clock and configuration arrive through the constructor, so
 * expiry can be tested deterministically without a Spring context.
 */
public class JwtService {

    /** HS256 requires at least 256 bits of key material. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final Clock clock;

    public JwtService(String secret, long expirationSeconds, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "fareflow.jwt.secret must be at least " + MINIMUM_SECRET_BYTES
                            + " bytes when authentication is enabled. Set the JWT_SECRET "
                            + "environment variable, or run with FAREFLOW_AUTH_ENABLED=false.");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("fareflow.jwt.expiration-seconds must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    public String issueToken(long userId, String email) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the user id carried by a valid, unexpired token.
     *
     * <p>Empty for anything else — bad signature, expired, malformed, or a subject
     * that is not a number. Callers cannot distinguish the cases, which is
     * deliberate: telling a client <em>why</em> a token failed helps an attacker.
     */
    public Optional<Long> extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException exception) {
            // NumberFormatException is an IllegalArgumentException, so it is covered:
            // a token whose subject is not numeric is just as invalid as a bad signature.
            return Optional.empty();
        }
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }
}
