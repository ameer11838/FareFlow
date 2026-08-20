package com.fareflow.trip;

import com.fareflow.route.TransitRoute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

/**
 * A trip a user took.
 *
 * <p>The route details here are a snapshot taken when the trip was created, not a
 * live join to {@code transit_routes}. Fares change; history must not.
 */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Set for seeded-route trips; null for discovered journeys. */
    @Column(name = "transit_route_id", updatable = false)
    private Long transitRouteId;

    /** Set for discovered journeys; null for seeded-route trips. */
    @Column(name = "journey_id", updatable = false)
    private Long journeyId;

    /**
     * Deduplicates a double-submitted selection. A partial unique index on
     * (user_id, idempotency_key) makes "charged twice" impossible at the database
     * level rather than merely unlikely.
     */
    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String origin;

    @Column(nullable = false, updatable = false)
    private String destination;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(nullable = false, updatable = false)
    private String mode;

    @Column(name = "fare_cents", nullable = false, updatable = false)
    private long fareCents;

    @Column(name = "duration_minutes", nullable = false, updatable = false)
    private int durationMinutes;

    @Column(nullable = false, updatable = false)
    private int transfers;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_label", nullable = false, updatable = false)
    private SelectedLabel selectedLabel;

    /**
     * Fare of the fastest alternative at decision time. Null when fewer than two
     * routes existed — no choice was made, so no honest savings figure exists.
     */
    @Column(name = "baseline_fare_cents", updatable = false)
    private Long baselineFareCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    // updatable so the test-only backdate query can build multi-week histories.
    // No production code path writes it after construction.
    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Trip() {
        // required by JPA
    }

    /**
     * A trip taken from a discovered multi-leg journey.
     *
     * <p>Route details are copied from the journey snapshot, so this trip reads
     * correctly forever even after schedules, fares, or discovery change.
     *
     * @param fareCents the authoritative fare, recomputed server-side
     */
    public Trip(long userId, com.fareflow.journey.PersistedJourney journey,
                long fareCents, SelectedLabel selectedLabel, Long baselineFareCents,
                Instant takenAt, String idempotencyKey) {
        this.userId = userId;
        this.journeyId = journey.getId();
        this.origin = journey.getOriginDisplayName();
        this.destination = journey.getDestinationDisplayName();
        this.provider = journey.summary();
        this.mode = journey.getLegs().stream()
                .filter(leg -> !"WALK".equals(leg.getMode()))
                .map(com.fareflow.journey.PersistedJourneyLeg::getMode)
                .findFirst()
                .orElse("WALK");
        this.fareCents = fareCents;
        this.durationMinutes = journey.getTotalDurationMinutes();
        this.transfers = journey.getTransfers();
        this.selectedLabel = selectedLabel;
        this.baselineFareCents = baselineFareCents;
        this.status = TripStatus.COMPLETED;
        this.takenAt = takenAt;
        this.idempotencyKey = idempotencyKey;
    }

    public Trip(long userId, TransitRoute route, SelectedLabel selectedLabel,
                Long baselineFareCents, Instant takenAt) {
        this.userId = userId;
        this.transitRouteId = route.getId();
        this.origin = route.getOrigin();
        this.destination = route.getDestination();
        this.provider = route.getProvider().name();
        this.mode = route.getMode().name();
        this.fareCents = route.getFareCents();
        this.durationMinutes = route.getDurationMinutes();
        this.transfers = route.getTransfers();
        this.selectedLabel = selectedLabel;
        this.baselineFareCents = baselineFareCents;
        this.status = TripStatus.COMPLETED;
        this.takenAt = takenAt;
    }

    public void markCancelled() {
        this.status = TripStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return status == TripStatus.CANCELLED;
    }

    /**
     * Money not spent by choosing this route over the fastest one.
     *
     * <p>Empty when no baseline was recorded. May be zero (the user took the
     * fastest route) or negative (they took something pricier than the fastest) —
     * both are reported honestly rather than clamped.
     */
    public Optional<Long> savedVersusFastestCents() {
        if (baselineFareCents == null) {
            return Optional.empty();
        }
        return Optional.of(baselineFareCents - fareCents);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTransitRouteId() {
        return transitRouteId;
    }

    public Long getJourneyId() {
        return journeyId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public boolean isFromDiscoveredJourney() {
        return journeyId != null;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public String getProvider() {
        return provider;
    }

    public String getMode() {
        return mode;
    }

    public long getFareCents() {
        return fareCents;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getTransfers() {
        return transfers;
    }

    public SelectedLabel getSelectedLabel() {
        return selectedLabel;
    }

    public Long getBaselineFareCents() {
        return baselineFareCents;
    }

    public TripStatus getStatus() {
        return status;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
