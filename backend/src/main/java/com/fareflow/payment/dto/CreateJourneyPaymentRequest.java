package com.fareflow.payment.dto;

import com.fareflow.payment.PaymentMethod;
import jakarta.validation.constraints.NotBlank;

/**
 * Creates a payment intent for a discovered journey.
 *
 * <p>There is intentionally no amount field. The server re-plans the journey and
 * asks FareEngine for the amount before the intent can exist.
 */
public record CreateJourneyPaymentRequest(
        @NotBlank(message = "from is required") String from,
        @NotBlank(message = "to is required") String to,
        @NotBlank(message = "journeyId is required") String journeyId,
        String profile,
        boolean confirmUnknownFare,
        PaymentMethod paymentMethod
) {
    public PaymentMethod paymentMethodOrWallet() {
        return paymentMethod == null ? PaymentMethod.FAREFLOW_WALLET : paymentMethod;
    }
}
