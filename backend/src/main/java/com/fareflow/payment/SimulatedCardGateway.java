package com.fareflow.payment;

import org.springframework.stereotype.Component;

/**
 * Deterministic local card rail. It never contacts a network or stores card data.
 * A real sandbox adapter can replace this component behind the same result type.
 */
@Component
public class SimulatedCardGateway {

    public static final String DECLINE_TOKEN = "tok_simulated_decline";

    public Authorization authorize(PaymentIntent intent, String token) {
        if (DECLINE_TOKEN.equals(token)) {
            return new Authorization(false, null, "CARD_DECLINED",
                    "The simulated card was declined. No trip was created and nothing was charged.");
        }
        return new Authorization(true,
                "sim_card_%s_%d".formatted(intent.getId(), intent.getAttemptCount() + 1),
                null, null);
    }

    public record Authorization(
            boolean approved,
            String providerReference,
            String failureCode,
            String failureMessage
    ) {
    }
}
