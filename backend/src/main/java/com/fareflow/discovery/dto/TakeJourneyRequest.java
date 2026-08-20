package com.fareflow.discovery.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A request to take a discovered journey.
 *
 * <p><strong>There is no fare field, and there never will be.</strong> The client
 * identifies which journey it selected; the server re-discovers it, re-prices it,
 * and charges its own number. A fare posted from a browser is a fare an attacker
 * controls.
 *
 * @param journeyId the deterministic discovery key from the search response
 * @param confirmUnknownFare set when the rider has accepted that a journey has no
 *                           computable fare. Without it, selecting such a journey
 *                           is refused rather than silently charged as zero.
 */
public record TakeJourneyRequest(
        @NotBlank(message = "from is required") String from,
        @NotBlank(message = "to is required") String to,
        @NotBlank(message = "journeyId is required") String journeyId,
        String profile,
        boolean confirmUnknownFare
) {
}
