package com.fareflow.payment.dto;

/**
 * Confirmation data for the simulated card rail.
 *
 * <p>{@code tok_simulated_decline} exercises the FAILED path. Any blank or other
 * token authorizes in the local simulation. Real provider tokens can replace this
 * later without changing the payment intent API.
 */
public record ConfirmPaymentRequest(String simulatedCardToken) {
}
