package com.fareflow.payment;

/** The complete lifecycle of a FareFlow payment intent. */
public enum PaymentStatus {
    CREATED,
    AUTHORIZED,
    PROCESSING,
    SETTLED,
    FAILED,
    REFUNDED
}
