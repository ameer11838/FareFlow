package com.fareflow.discovery;

import com.fareflow.discovery.dto.TakeJourneyRequest;
import com.fareflow.payment.PaymentService;
import com.fareflow.trip.Trip;
import com.fareflow.user.User;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for the original one-call journey purchase endpoint.
 *
 * <p>The actual workflow now lives in {@link PaymentService}: FareEngine quote,
 * payment intent, authorization, settlement, trip, ledger, and budget projection.
 * Keeping this facade preserves existing clients while new clients expose the
 * payment lifecycle directly.
 */
@Service
public class TakeJourneyService {

    private final PaymentService paymentService;

    public TakeJourneyService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public Trip take(User user, TakeJourneyRequest request, String idempotencyKey) {
        return paymentService.purchaseJourney(user, request, idempotencyKey);
    }
}
