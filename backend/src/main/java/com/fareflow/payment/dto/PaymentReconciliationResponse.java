package com.fareflow.payment.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A read-only consistency check across intents, trips, and append-only ledger rows. */
public record PaymentReconciliationResponse(
        Instant checkedAt,
        Map<String, Long> countsByStatus,
        long settledCents,
        int issueCount,
        List<Issue> issues
) {
    public record Issue(UUID paymentIntentId, String status, String detail) {
    }
}
