package com.fareflow.ledger.dto;

import com.fareflow.ledger.LedgerEntry;

import java.time.Instant;
import java.util.UUID;

/**
 * @param amountCents signed — negative is money out, positive is money in.
 *        The client colours by sign rather than by type.
 */
public record LedgerEntryResponse(
        long id,
        long userId,
        Long tripId,
        UUID paymentIntentId,
        String type,
        long amountCents,
        String description,
        Instant occurredAt,
        Instant createdAt
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getUserId(),
                entry.getTripId(),
                entry.getPaymentIntentId(),
                entry.getType().name(),
                entry.getAmountCents(),
                entry.getDescription(),
                entry.getOccurredAt(),
                entry.getCreatedAt());
    }
}
