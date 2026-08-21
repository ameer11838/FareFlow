package com.fareflow.payment.dto;

import com.fareflow.payment.PaymentEvent;

import java.time.Instant;

public record PaymentEventResponse(
        long id,
        String fromStatus,
        String toStatus,
        String reason,
        Instant occurredAt
) {
    public static PaymentEventResponse from(PaymentEvent event) {
        return new PaymentEventResponse(
                event.getId(),
                event.getFromStatus() == null ? null : event.getFromStatus().name(),
                event.getToStatus().name(),
                event.getReason(),
                event.getOccurredAt());
    }
}
