package com.fareflow.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One immutable payment state transition. */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_intent_id", nullable = false, updatable = false)
    private UUID paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private PaymentStatus toStatus;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(UUID paymentIntentId, PaymentStatus fromStatus,
                        PaymentStatus toStatus, String reason, Instant occurredAt) {
        this.paymentIntentId = paymentIntentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public UUID getPaymentIntentId() { return paymentIntentId; }
    public PaymentStatus getFromStatus() { return fromStatus; }
    public PaymentStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
