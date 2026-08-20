package com.fareflow.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One immutable row in the financial ledger.
 *
 * <p>There are no setters. Once written, an entry never changes — corrections are
 * new entries. {@code updatable = false} on every column means Hibernate cannot
 * emit an UPDATE for this table even if someone later tries.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "trip_id", updatable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LedgerEntryType type;

    /** Signed: negative is money out, positive is money in. Integer cents, never a double. */
    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    protected LedgerEntry() {
        // required by JPA
    }

    private LedgerEntry(Long userId, Long tripId, LedgerEntryType type, long amountCents,
                        String description, Instant occurredAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        validateSign(type, amountCents);
        if (type != LedgerEntryType.FARE_ADJUSTMENT && tripId == null) {
            throw new IllegalArgumentException(type + " entries must reference a trip");
        }

        this.userId = userId;
        this.tripId = tripId;
        this.type = type;
        this.amountCents = amountCents;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    /** A charge for taking a trip. Pass the positive fare; the entry stores it negative. */
    public static LedgerEntry tripCharge(long userId, long tripId, long fareCents,
                                         String description, Instant occurredAt) {
        if (fareCents <= 0) {
            throw new IllegalArgumentException("A trip charge requires a positive fare");
        }
        return new LedgerEntry(userId, tripId, LedgerEntryType.TRIP_CHARGE, -fareCents,
                description, occurredAt);
    }

    /** A refund for a cancelled trip. Pass the positive amount to return. */
    public static LedgerEntry refund(long userId, long tripId, long amountCents,
                                     String description, Instant occurredAt) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("A refund requires a positive amount");
        }
        return new LedgerEntry(userId, tripId, LedgerEntryType.REFUND, amountCents,
                description, occurredAt);
    }

    /** A correction. Signed: negative charges more, positive gives money back. */
    public static LedgerEntry fareAdjustment(long userId, Long tripId, long signedAmountCents,
                                             String description, Instant occurredAt) {
        if (signedAmountCents == 0) {
            throw new IllegalArgumentException("A fare adjustment must be non-zero");
        }
        return new LedgerEntry(userId, tripId, LedgerEntryType.FARE_ADJUSTMENT, signedAmountCents,
                description, occurredAt);
    }

    private static void validateSign(LedgerEntryType type, long amountCents) {
        switch (type) {
            case TRIP_CHARGE -> {
                if (amountCents >= 0) {
                    throw new IllegalArgumentException("TRIP_CHARGE must be negative");
                }
            }
            case REFUND -> {
                if (amountCents <= 0) {
                    throw new IllegalArgumentException("REFUND must be positive");
                }
            }
            case FARE_ADJUSTMENT -> {
                if (amountCents == 0) {
                    throw new IllegalArgumentException("FARE_ADJUSTMENT must be non-zero");
                }
            }
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTripId() {
        return tripId;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
