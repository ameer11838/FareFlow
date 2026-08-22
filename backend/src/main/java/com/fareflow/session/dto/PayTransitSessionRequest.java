package com.fareflow.session.dto;

import com.fareflow.payment.PaymentMethod;

public record PayTransitSessionRequest(
        PaymentMethod paymentMethod,
        String simulatedCardToken
) {
    public PaymentMethod paymentMethodOrWallet() {
        return paymentMethod == null ? PaymentMethod.FAREFLOW_WALLET : paymentMethod;
    }
}
