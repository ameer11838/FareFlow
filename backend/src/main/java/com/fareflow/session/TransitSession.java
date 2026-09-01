package com.fareflow.session;

import com.fareflow.exception.InvalidStateException;
import com.fareflow.journey.PersistedJourney;
import com.fareflow.profile.FareCategory;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Server-owned state for one usage-priced transit journey. */
@Entity
@Table(name = "transit_sessions")
public class TransitSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false, updatable = false)
    private PersistedJourney journey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransitSessionStatus status;

    @Column(name = "progress_units_total", nullable = false, updatable = false)
    private int progressUnitsTotal;
    @Column(name = "progress_units_completed", nullable = false)
    private int progressUnitsCompleted;
    @Column(name = "planned_stop_count", updatable = false)
    private Integer plannedStopCount;
    @Column(name = "completed_stop_count", nullable = false)
    private int completedStopCount;
    @Column(name = "planned_distance_metres", nullable = false, updatable = false)
    private long plannedDistanceMetres;
    @Column(name = "distance_travelled_metres", nullable = false)
    private long distanceTravelledMetres;

    @Column(name = "estimated_fare_min_cents", nullable = false, updatable = false)
    private long estimatedFareMinCents;
    @Column(name = "estimated_fare_max_cents", nullable = false, updatable = false)
    private long estimatedFareMaxCents;
    @Column(name = "current_fare_cents", nullable = false)
    private long currentFareCents;
    @Column(name = "final_fare_cents")
    private Long finalFareCents;
    @Column(name = "base_fare_cents")
    private Long baseFareCents;
    @Column(name = "distance_fare_cents")
    private Long distanceFareCents;
    @Column(name = "stop_fare_cents")
    private Long stopFareCents;
    @Column(name = "transfer_discount_cents", nullable = false)
    private long transferDiscountCents;
    @Column(name = "concession_discount_cents", nullable = false)
    private long concessionDiscountCents;
    @Column(name = "cap_discount_cents", nullable = false)
    private long capDiscountCents;
    @Enumerated(EnumType.STRING)
    @Column(name = "fare_category", nullable = false, updatable = false)
    private FareCategory fareCategory;
    @Column(name = "spent_today_before_cents", nullable = false, updatable = false)
    private long spentTodayBeforeCents;
    @Column(name = "spent_week_before_cents", nullable = false, updatable = false)
    private long spentWeekBeforeCents;
    @Column(name = "pricing_version", nullable = false, updatable = false)
    private String pricingVersion;
    @Column(name = "progress_source", nullable = false, updatable = false)
    private String progressSource;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, updatable = false)
    private String requestFingerprint;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TransitSession() {
    }

    public static TransitSession start(long userId,
                                       PersistedJourney journey,
                                       int totalUnits,
                                       long plannedDistanceMetres,
                                       long minimumFareCents,
                                       long maximumFareCents,
                                       String pricingVersion,
                                       UsageFareContext fareContext,
                                       String idempotencyKey,
                                       String requestFingerprint,
                                       Instant now) {
        if (totalUnits <= 0) {
            throw new IllegalArgumentException("A transit session needs public-transit progress");
        }
        TransitSession session = new TransitSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.journey = journey;
        session.status = TransitSessionStatus.STARTED;
        session.progressUnitsTotal = totalUnits;
        session.plannedStopCount = totalUnits;
        session.plannedDistanceMetres = plannedDistanceMetres;
        session.estimatedFareMinCents = minimumFareCents;
        session.estimatedFareMaxCents = maximumFareCents;
        session.pricingVersion = pricingVersion;
        session.fareCategory = fareContext.fareCategory();
        session.spentTodayBeforeCents = fareContext.spentTodayCents();
        session.spentWeekBeforeCents = fareContext.spentThisWeekCents();
        session.baseFareCents = 0L;
        session.distanceFareCents = 0L;
        session.stopFareCents = 0L;
        session.progressSource = "RIDER_CONFIRMED";
        session.idempotencyKey = idempotencyKey;
        session.requestFingerprint = requestFingerprint;
        session.startedAt = now;
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    public void advance(UsageFareEngine.StopFarePoint fare,
                        TransitProgressOutcome outcome, Instant now) {
        requireActive();
        if (progressUnitsCompleted >= progressUnitsTotal) {
            throw new InvalidStateException("Every recorded stop on this route is already complete");
        }
        progressUnitsCompleted++;
        if (outcome == TransitProgressOutcome.REACHED) completedStopCount++;
        distanceTravelledMetres = Math.min(plannedDistanceMetres,
                Math.addExact(distanceTravelledMetres, fare.distanceMetres()));
        currentFareCents = fare.cumulativeFareCents();
        baseFareCents = Math.addExact(baseFareCents, fare.baseCents());
        distanceFareCents = Math.addExact(distanceFareCents, fare.distanceCents());
        stopFareCents = Math.addExact(stopFareCents, fare.stopCents());
        transferDiscountCents = Math.addExact(
                transferDiscountCents, fare.transferDiscountCents());
        concessionDiscountCents = Math.addExact(
                concessionDiscountCents, fare.concessionDiscountCents());
        capDiscountCents = Math.addExact(capDiscountCents, fare.capDiscountCents());
        status = TransitSessionStatus.IN_PROGRESS;
        updatedAt = now;
    }

    public void end(Instant now) {
        requireActive();
        finalFareCents = currentFareCents;
        endedAt = now;
        status = currentFareCents == 0
                ? TransitSessionStatus.NO_CHARGE : TransitSessionStatus.COMPLETED;
        updatedAt = now;
    }

    public void markPaid(Instant now) {
        if (status == TransitSessionStatus.PAID) {
            return;
        }
        if (status != TransitSessionStatus.COMPLETED) {
            throw new InvalidStateException("Only a completed transit session can be paid");
        }
        status = TransitSessionStatus.PAID;
        paidAt = now;
        updatedAt = now;
    }

    private void requireActive() {
        if (!status.isActive()) {
            throw new InvalidStateException(
                    "Transit session %s is already %s".formatted(id, status));
        }
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public PersistedJourney getJourney() { return journey; }
    public TransitSessionStatus getStatus() { return status; }
    public int getProgressUnitsTotal() { return progressUnitsTotal; }
    public int getProgressUnitsCompleted() { return progressUnitsCompleted; }
    public Integer getPlannedStopCount() { return plannedStopCount; }
    public int getCompletedStopCount() { return completedStopCount; }
    public long getPlannedDistanceMetres() { return plannedDistanceMetres; }
    public long getDistanceTravelledMetres() { return distanceTravelledMetres; }
    public long getEstimatedFareMinCents() { return estimatedFareMinCents; }
    public long getEstimatedFareMaxCents() { return estimatedFareMaxCents; }
    public long getCurrentFareCents() { return currentFareCents; }
    public Long getFinalFareCents() { return finalFareCents; }
    public Long getBaseFareCents() { return baseFareCents; }
    public Long getDistanceFareCents() { return distanceFareCents; }
    public Long getStopFareCents() { return stopFareCents; }
    public long getTransferDiscountCents() { return transferDiscountCents; }
    public long getConcessionDiscountCents() { return concessionDiscountCents; }
    public long getCapDiscountCents() { return capDiscountCents; }
    public FareCategory getFareCategory() { return fareCategory; }
    public long getSpentTodayBeforeCents() { return spentTodayBeforeCents; }
    public long getSpentWeekBeforeCents() { return spentWeekBeforeCents; }
    public String getPricingVersion() { return pricingVersion; }
    public String getProgressSource() { return progressSource; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
