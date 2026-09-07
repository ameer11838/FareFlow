package com.fareflow.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geofence rules for checking a rider against the stop they confirmed.
 *
 * <p>Configuration rather than constants because the honest radius depends on the
 * network: a suburban bus flag stop and a multi-block interchange are not the same
 * target, and a deployment should be able to say so without a redeploy.
 *
 * @param radiusMetres        how close counts as "at the stop"
 * @param maxAccuracyMetres   fixes worse than this are treated as unusable rather
 *                            than as evidence against the rider
 * @param accuracySlackMetres how much of a fix's own error radius is added to the
 *                            geofence before calling a rider contradicted
 */
@ConfigurationProperties(prefix = "fareflow.stop-verification")
public record StopVerificationProperties(
        double radiusMetres,
        double maxAccuracyMetres,
        double accuracySlackMetres
) {

    public StopVerificationProperties {
        if (radiusMetres <= 0) {
            throw new IllegalArgumentException("Stop geofence radius must be positive");
        }
        if (maxAccuracyMetres <= 0) {
            throw new IllegalArgumentException("Maximum usable accuracy must be positive");
        }
        if (accuracySlackMetres < 0) {
            throw new IllegalArgumentException("Accuracy slack must not be negative");
        }
    }
}
