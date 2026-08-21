package com.fareflow.payment;

import com.fareflow.exception.InvalidStateException;
import com.fareflow.journey.PersistedJourney;
import com.fareflow.trip.SelectedLabel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A provider-neutral request to pay an authoritative FareFlow fare.
 *
 * <p>The amount is copied from {@code FareEngine} output. No constructor accepts
 * an amount supplied by an HTTP request, which keeps the trust boundary visible
 * in the type itself.
 */
@Entity
@Table(name = "payment_intents")
public class PaymentIntent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false, updatable = false)
    private PersistedJourney journey;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false)
    private String requestFingerprint;

    @Column(name = "baseline_fare_cents", updatable = false)
    private Long baselineFareCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_label", nullable = false, updatable = false)
    private SelectedLabel selectedLabel;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "authorized_at")
    private Instant authorizedAt;
    @Column(name = "processing_at")
    private Instant processingAt;
    @Column(name = "settled_at")
    private Instant settledAt;
    @Column(name = "failed_at")
    private Instant failedAt;
    @Column(name = "refunded_at")
    private Instant refundedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentIntent() {
    }

    public static PaymentIntent create(long userId,
                                       PersistedJourney journey,
                                       long authoritativeAmountCents,
                                       PaymentMethod paymentMethod,
                                       String idempotencyKey,
                                       String requestFingerprint,
                                       Long baselineFareCents,
                                       SelectedLabel selectedLabel,
                                       Instant now) {
        if (authoritativeAmountCents < 0) {
            throw new IllegalArgumentException("Payment amount cannot be negative");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("An idempotency key is required");
        }
        PaymentIntent intent = new PaymentIntent();
        intent.id = UUID.randomUUID();
        intent.userId = userId;
        intent.journey = journey;
        intent.amountCents = authoritativeAmountCents;
        intent.currency = "USD";
        intent.paymentMethod = paymentMethod;
        intent.status = PaymentStatus.CREATED;
        intent.idempotencyKey = idempotencyKey.trim();
        intent.requestFingerprint = requestFingerprint;
        intent.baselineFareCents = baselineFareCents;
        intent.selectedLabel = selectedLabel == null ? SelectedLabel.MANUAL : selectedLabel;
        intent.createdAt = now;
        intent.updatedAt = now;
        return intent;
    }

    public PaymentStatus authorize(String reference, Instant now) {
        requireState(PaymentStatus.CREATED, PaymentStatus.FAILED);
        PaymentStatus previous = status;
        status = PaymentStatus.AUTHORIZED;
        attemptCount++;
        providerReference = reference;
        failureCode = null;
        failureMessage = null;
        failedAt = null;
        authorizedAt = now;
        updatedAt = now;
        return previous;
    }

    public PaymentStatus startProcessing(Instant now) {
        requireState(PaymentStatus.AUTHORIZED);
        PaymentStatus previous = status;
        status = PaymentStatus.PROCESSING;
        processingAt = now;
        updatedAt = now;
        return previous;
    }

    public PaymentStatus settle(long tripId, Instant now) {
        requireState(PaymentStatus.PROCESSING);
        PaymentStatus previous = status;
        this.tripId = tripId;
        status = PaymentStatus.SETTLED;
        settledAt = now;
        updatedAt = now;
        return previous;
    }

    public PaymentStatus fail(String code, String message, Instant now) {
        requireState(PaymentStatus.CREATED, PaymentStatus.AUTHORIZED, PaymentStatus.PROCESSING,
                PaymentStatus.FAILED);
        PaymentStatus previous = status;
        status = PaymentStatus.FAILED;
        attemptCount++;
        failureCode = code;
        failureMessage = message;
        failedAt = now;
        updatedAt = now;
        return previous;
    }

    public PaymentStatus refund(Instant now) {
        requireState(PaymentStatus.SETTLED);
        PaymentStatus previous = status;
        status = PaymentStatus.REFUNDED;
        refundedAt = now;
        updatedAt = now;
        return previous;
    }

    private void requireState(PaymentStatus... allowed) {
        for (PaymentStatus value : allowed) {
            if (status == value) {
                return;
            }
        }
        throw new InvalidStateException(
                "Payment %s cannot transition from %s".formatted(id, status));
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public PersistedJourney getJourney() { return journey; }
    public Long getTripId() { return tripId; }
    public long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Long getBaselineFareCents() { return baselineFareCents; }
    public SelectedLabel getSelectedLabel() { return selectedLabel; }
    public int getAttemptCount() { return attemptCount; }
    public String getProviderReference() { return providerReference; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getAuthorizedAt() { return authorizedAt; }
    public Instant getProcessingAt() { return processingAt; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
