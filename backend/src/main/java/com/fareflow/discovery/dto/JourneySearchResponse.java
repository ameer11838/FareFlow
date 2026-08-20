package com.fareflow.discovery.dto;

import com.fareflow.location.LocationCandidate;
import com.fareflow.recommendation.dto.ProfileDto;
import com.fareflow.recommendation.dto.WeightsDto;

import java.util.List;

/**
 * Result of an arbitrary origin-to-destination search.
 *
 * <p>Carries the resolved places, so a client can show what "Philly" was understood
 * to mean, and the weights used, so any recommendation stays reproducible.
 */
public record JourneySearchResponse(
        LocationCandidate origin,
        LocationCandidate destination,
        ProfileDto profile,
        WeightsDto weightsUsed,
        String summary,
        String contextNote,
        List<JourneyOptionDto> options,
        List<String> notices
) {
}
