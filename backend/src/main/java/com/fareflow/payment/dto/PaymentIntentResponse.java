package com.fareflow.payment.dto;

import com.fareflow.payment.PaymentEvent;
import com.fareflow.payment.PaymentIntent;
import com.fareflow.trip.Trip;
import com.fareflow.trip.dto.TripResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID id,
        String status,
        String paymentMethod,
        long amountCents,
        String currency,
        String journeySummary,
        String origin,
        String destination,
        int attemptCount,
        String providerReference,
        String failureCode,
        String failureMessage,
        TripResponse trip,
        Instant authorizedAt,
        Instant processingAt,
        Instant settledAt,
        Instant failedAt,
        Instant refundedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentEventResponse> events
) {
    public static PaymentIntentResponse from(PaymentIntent intent,
                                             Trip trip,
                                             List<PaymentEvent> events) {
        return new PaymentIntentResponse(
                intent.getId(),
                intent.getStatus().name(),
                intent.getPaymentMethod().name(),
                intent.getAmountCents(),
                intent.getCurrency(),
                intent.getJourney().summary(),
                intent.getJourney().getOriginDisplayName(),
                intent.getJourney().getDestinationDisplayName(),
                intent.getAttemptCount(),
                intent.getProviderReference(),
                intent.getFailureCode(),
                intent.getFailureMessage(),
                trip == null ? null : TripResponse.from(trip),
                intent.getAuthorizedAt(),
                intent.getProcessingAt(),
                intent.getSettledAt(),
                intent.getFailedAt(),
                intent.getRefundedAt(),
                intent.getCreatedAt(),
                intent.getUpdatedAt(),
                events.stream().map(PaymentEventResponse::from).toList());
    }
}
