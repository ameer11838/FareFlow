package com.fareflow.discovery.dto;

import java.util.List;

/**
 * One scored, priced journey.
 *
 * @param fareCents      null when the journey could not be priced. Never zero as a
 *                       stand-in — the client renders "Fare unavailable"
 * @param fareStatus     EXACT, ESTIMATED, or UNKNOWN
 * @param fareBreakdown  receipt-style lines summing to the total
 */
public record JourneyOptionDto(
        String journeyId,
        String summary,
        int totalMinutes,
        int walkingMinutes,
        int transfers,
        Long fareCents,
        String fareStatus,
        String fareSource,
        List<String> fareBreakdown,
        List<String> labels,
        boolean recommended,
        double score,
        String explanation,
        String dataSource,
        List<LegDto> legs
) {

    public record LegDto(
            String mode,
            String agency,
            String lineName,
            String fromName,
            String toName,
            int durationMinutes,
            int waitMinutes,
            List<WaypointDto> waypoints
    ) {
    }

    public record WaypointDto(String name, double latitude, double longitude) {
    }
}
