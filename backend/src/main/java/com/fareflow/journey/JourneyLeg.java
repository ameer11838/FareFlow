package com.fareflow.journey;

import java.time.Instant;
import java.util.List;

/**
 * One continuous movement within a journey: a walk, or a ride on one line.
 *
 * <p>A leg carries no fare. Pricing is a property of the whole journey — transfer
 * credits and fare caps span legs — so the fare engine consumes the journey and the
 * legs stay purely about movement.
 *
 * @param lineCode  null for walking legs
 * @param waypoints ordered coordinates for drawing; may be empty
 */
public record JourneyLeg(
        TransitMode mode,
        String agency,
        String lineCode,
        String lineName,
        String fromStopCode,
        String fromStopName,
        String toStopCode,
        String toStopName,
        int durationMinutes,
        int waitMinutes,
        double distanceMetres,
        List<Waypoint> waypoints,
        Instant departureTime,
        Instant arrivalTime,
        boolean realtime,
        Integer stopCount
) {

    public JourneyLeg {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes must not be negative");
        }
        if (waitMinutes < 0) {
            throw new IllegalArgumentException("waitMinutes must not be negative");
        }
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
    }

    /** Compatibility constructor for providers that only have typical durations. */
    public JourneyLeg(TransitMode mode, String agency, String lineCode, String lineName,
                      String fromStopCode, String fromStopName, String toStopCode,
                      String toStopName, int durationMinutes, int waitMinutes,
                      double distanceMetres, List<Waypoint> waypoints) {
        this(mode, agency, lineCode, lineName, fromStopCode, fromStopName, toStopCode,
                toStopName, durationMinutes, waitMinutes, distanceMetres, waypoints,
                null, null, false, null);
    }

    /** In-vehicle time plus the wait before boarding. */
    public int totalMinutes() {
        return durationMinutes + waitMinutes;
    }

    public record Waypoint(String name, double latitude, double longitude) {
    }

    public static JourneyLeg walk(String fromName, String toName, int minutes, double metres,
                                  List<Waypoint> waypoints) {
        return new JourneyLeg(TransitMode.WALK, null, null, "Walk",
                null, fromName, null, toName, minutes, 0, metres, waypoints);
    }
}
