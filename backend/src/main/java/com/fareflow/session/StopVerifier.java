package com.fareflow.session;

import com.fareflow.location.LocationCandidate;
import org.springframework.stereotype.Service;

/**
 * Checks a reported rider position against the stop being confirmed.
 *
 * <p>This grades evidence; it never decides money. {@link TransitSessionService}
 * charges the same fare whatever this returns, because the alternative — refusing
 * to advance a rider whose phone lost its fix in a tunnel — strands them mid-trip
 * with an unpayable session. What the verdict does buy is an auditable record:
 * a trip where every stop was confirmed from a kilometre away looks different, in
 * the ledger, from one confirmed on the platform.
 *
 * <p>Distance is straight-line. A rider is either near the stop or nowhere near it;
 * street-network distance would add a routing call per stop to sharpen a boundary
 * that is already deliberately generous.
 */
@Service
public class StopVerifier {

    private final StopVerificationProperties properties;

    public StopVerifier(StopVerificationProperties properties) {
        this.properties = properties;
    }

    /**
     * @param position null when the rider confirmed without sharing a location
     */
    public StopVerification verify(UsageFareEngine.StopFarePoint stop, RiderPosition position) {
        if (position == null) {
            return StopVerification.noFix();
        }
        if (!stop.hasCoordinates()) {
            return StopVerification.unverifiableStop(position);
        }

        double distance = LocationCandidate.haversineMetres(
                position.latitude(), position.longitude(),
                stop.stopLatitude(), stop.stopLongitude());

        // A fix this vague cannot place the rider at a specific stop in either
        // direction. Calling it a contradiction would punish bad reception.
        if (position.hasAccuracy() && position.accuracyMetres() > properties.maxAccuracyMetres()) {
            return new StopVerification(StopVerificationStatus.UNVERIFIED_IMPRECISE,
                    distance, position.accuracyMetres(),
                    position.latitude(), position.longitude(),
                    "Location accurate only to %s · too imprecise to confirm this stop"
                            .formatted(metres(position.accuracyMetres())));
        }

        // The rider gets the benefit of their own error radius, capped so a device
        // cannot claim its way to a verified stop by reporting a huge uncertainty.
        double slack = Math.min(position.accuracyOrZero(), properties.accuracySlackMetres());
        double allowed = properties.radiusMetres() + slack;

        if (distance <= allowed) {
            return new StopVerification(StopVerificationStatus.VERIFIED,
                    distance, position.accuracyMetres(),
                    position.latitude(), position.longitude(),
                    "Confirmed %s from the stop".formatted(metres(distance)));
        }

        return new StopVerification(StopVerificationStatus.UNVERIFIED_TOO_FAR,
                distance, position.accuracyMetres(),
                position.latitude(), position.longitude(),
                "Reported %s from the stop · charged, flagged for review"
                        .formatted(metres(distance)));
    }

    private static String metres(double value) {
        return value >= 1_000
                ? "%.1f km".formatted(value / 1_000)
                : "%d m".formatted(Math.round(value));
    }
}
