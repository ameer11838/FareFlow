package com.fareflow.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verifier decides how much FareFlow trusts a confirmed stop. It must never
 * decide whether the rider is charged — that separation is what keeps a rider
 * whose phone failed underground from being stranded in an unpayable session.
 */
class StopVerifierTest {

    // Newark Penn Station, and points measured from it.
    private static final double STOP_LAT = 40.734583;
    private static final double STOP_LON = -74.164306;

    private final StopVerifier verifier = new StopVerifier(
            new StopVerificationProperties(250, 400, 150));

    @Test
    @DisplayName("a fix on the platform verifies the stop")
    void onPlatformVerifies() {
        StopVerification result = verifier.verify(
                stopAt(STOP_LAT, STOP_LON), new RiderPosition(40.734700, -74.164200, 12.0));

        assertThat(result.status()).isEqualTo(StopVerificationStatus.VERIFIED);
        assertThat(result.distanceMetres()).isLessThan(50);
        assertThat(result.explanation()).contains("from the stop");
    }

    @Test
    @DisplayName("a good fix far from the stop is contradicted, not blocked")
    void farAwayIsContradicted() {
        // Roughly 4 km south-west of the stop, with a tight error radius.
        StopVerification result = verifier.verify(
                stopAt(STOP_LAT, STOP_LON), new RiderPosition(40.700000, -74.180000, 10.0));

        assertThat(result.status()).isEqualTo(StopVerificationStatus.UNVERIFIED_TOO_FAR);
        assertThat(result.status().isContradicted()).isTrue();
        assertThat(result.distanceMetres()).isGreaterThan(3_000);
    }

    @Test
    @DisplayName("no position recorded means unverified, never contradicted")
    void noPositionIsNotEvidence() {
        StopVerification result = verifier.verify(stopAt(STOP_LAT, STOP_LON), null);

        assertThat(result.status()).isEqualTo(StopVerificationStatus.UNVERIFIED_NO_FIX);
        assertThat(result.status().isContradicted()).isFalse();
        assertThat(result.distanceMetres()).isNull();
        assertThat(result.reportedLatitude()).isNull();
    }

    @Test
    @DisplayName("a fix too vague to place anyone is unusable, not evidence against the rider")
    void impreciseFixIsNotEvidence() {
        // 4 km away, but the device admits it could be a kilometre out.
        StopVerification result = verifier.verify(
                stopAt(STOP_LAT, STOP_LON), new RiderPosition(40.700000, -74.180000, 1_000.0));

        assertThat(result.status()).isEqualTo(StopVerificationStatus.UNVERIFIED_IMPRECISE);
        assertThat(result.status().isContradicted()).isFalse();
    }

    @Test
    @DisplayName("a stop the provider never placed cannot be verified either way")
    void unplacedStopIsUnverifiable() {
        StopVerification result = verifier.verify(
                stopWithoutCoordinates(), new RiderPosition(40.734700, -74.164200, 12.0));

        assertThat(result.status()).isEqualTo(StopVerificationStatus.UNVERIFIABLE_STOP);
        assertThat(result.status().isContradicted()).isFalse();
    }

    @Test
    @DisplayName("a rider just outside the fence is verified via their own error radius")
    void accuracySlackIsAllowed() {
        // ~350 m away: outside the 250 m fence, inside 250 m + 150 m of slack.
        StopVerification result = verifier.verify(
                stopAt(STOP_LAT, STOP_LON), new RiderPosition(40.737730, -74.164306, 140.0));

        assertThat(result.distanceMetres()).isBetween(250.0, 400.0);
        assertThat(result.status()).isEqualTo(StopVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("slack is capped, so a device cannot claim its way to verified")
    void accuracySlackIsCapped() {
        // Same 350 m, but the device claims a 399 m radius. Slack caps at 150 m,
        // so the allowance stays 400 m -- and a wilder claim would not extend it.
        StopVerification generous = verifier.verify(
                stopAt(STOP_LAT, STOP_LON), new RiderPosition(40.741000, -74.164306, 399.0));

        assertThat(generous.distanceMetres()).isGreaterThan(400);
        assertThat(generous.status()).isEqualTo(StopVerificationStatus.UNVERIFIED_TOO_FAR);
    }

    private static UsageFareEngine.StopFarePoint stopAt(double latitude, double longitude) {
        return point(latitude, longitude);
    }

    private static UsageFareEngine.StopFarePoint stopWithoutCoordinates() {
        return point(null, null);
    }

    private static UsageFareEngine.StopFarePoint point(Double latitude, Double longitude) {
        return new UsageFareEngine.StopFarePoint(
                1, "Newark Penn Station", "Northeast Corridor", "RAIL", "NJ TRANSIT",
                120, 10, 5, 135, 0, 0, 0, 135, 135, 800,
                "Northeast Corridor reached Newark Penn Station", latitude, longitude);
    }
}
