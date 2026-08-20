package com.fareflow.route;

import com.fareflow.recommendation.optimization.RouteCandidate;
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
 * A transit option between two places.
 *
 * <p>Reference data: rows here are the catalog, not financial records. Trips
 * snapshot the values they need at the moment of travel rather than reading
 * through to this table, so a later fare change never rewrites history.
 *
 * <p>Fare is a {@code long} of cents mapped to {@code BIGINT}. There is no
 * {@code double} anywhere in the money path.
 */
@Entity
@Table(name = "transit_routes")
public class TransitRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransitProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransitMode mode;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "fare_cents", nullable = false)
    private long fareCents;

    @Column(nullable = false)
    private int transfers;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * How trustworthy this route's geometry is: SCHEMATIC (straight lines between
     * real stops), SURVEYED (an actual agency shape), or NONE.
     */
    @Column(name = "geometry_source", nullable = false)
    private String geometrySource = "SCHEMATIC";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TransitRoute() {
        // required by JPA
    }

    /**
     * Converts this entity into the plain value type the optimization engine consumes.
     * This mapping is the boundary that keeps the scorer free of JPA.
     */
    public RouteCandidate toCandidate() {
        return new RouteCandidate(
                id,
                provider.name(),
                provider.displayName(),
                mode.name(),
                durationMinutes,
                fareCents,
                transfers);
    }

    public Long getId() {
        return id;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public TransitProvider getProvider() {
        return provider;
    }

    public TransitMode getMode() {
        return mode;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public long getFareCents() {
        return fareCents;
    }

    public int getTransfers() {
        return transfers;
    }

    public boolean isActive() {
        return active;
    }

    public String getGeometrySource() {
        return geometrySource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
