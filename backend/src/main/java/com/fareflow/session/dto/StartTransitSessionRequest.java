package com.fareflow.session.dto;

import jakarta.validation.constraints.NotBlank;

/** No distance, stop count, or fare is accepted from the browser. */
public record StartTransitSessionRequest(
        @NotBlank(message = "from is required") String from,
        @NotBlank(message = "to is required") String to,
        @NotBlank(message = "journeyId is required") String journeyId,
        String profile
) {
}
