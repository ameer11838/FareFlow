package com.fareflow.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain JUnit: the clock is injected, so expiry is deterministic. */
class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-definitely-long-enough-32";
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(SECRET, 3600, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a freshly issued token round-trips to the same user id")
    void roundTrip() {
        JwtService service = serviceAt(NOW);
        String token = service.issueToken(42L, "ameer@example.com");

        assertThat(service.extractUserId(token)).contains(42L);
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void rejectsForeignSignature() {
        String token = serviceAt(NOW).issueToken(42L, "ameer@example.com");
        JwtService other = new JwtService(
                "a-completely-different-secret-also-long-enough!!", 3600, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(other.extractUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpired() {
        String token = serviceAt(NOW).issueToken(42L, "ameer@example.com");

        // Same secret, but the clock has moved past the expiry.
        JwtService later = new JwtService(SECRET, 3600,
                Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThat(later.extractUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("a token still inside its window is accepted")
    void acceptsBeforeExpiry() {
        String token = serviceAt(NOW).issueToken(42L, "ameer@example.com");
        JwtService later = new JwtService(SECRET, 3600,
                Clock.fixed(NOW.plus(Duration.ofMinutes(59)), ZoneOffset.UTC));

        assertThat(later.extractUserId(token)).contains(42L);
    }

    @Test
    @DisplayName("garbage and tampered tokens are rejected without throwing")
    void rejectsMalformed() {
        JwtService service = serviceAt(NOW);

        assertThat(service.extractUserId("not-a-token")).isEmpty();
        assertThat(service.extractUserId("")).isEmpty();
        assertThat(service.extractUserId("a.b.c")).isEmpty();

        // Flip a character in the payload: the signature no longer matches.
        String token = service.issueToken(42L, "ameer@example.com");
        String tampered = token.substring(0, 20) + "X" + token.substring(21);
        assertThat(service.extractUserId(tampered)).isEmpty();
    }

    @Test
    @DisplayName("a short secret fails fast rather than signing weakly")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 3600, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");

        assertThatThrownBy(() -> new JwtService(null, 3600, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a non-positive expiry is rejected")
    void rejectsBadExpiry() {
        assertThatThrownBy(() -> new JwtService(SECRET, 0, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
