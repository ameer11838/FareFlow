package com.fareflow.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One ordered stop along a transit route.
 *
 * <p>Coordinates are geographic, not monetary — the integer-cents rule does not
 * apply here. {@code NUMERIC(9,6)} is about angular precision (roughly 10cm),
 * mapped to {@link BigDecimal} and converted to {@code double} only at the DTO
 * boundary, which is what mapping libraries expect.
 */
@Entity
@Table(name = "transit_route_waypoints")
public class TransitRouteWaypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transit_route_id", nullable = false)
    private Long transitRouteId;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    protected TransitRouteWaypoint() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public Long getTransitRouteId() {
        return transitRouteId;
    }

    public int getSequence() {
        return sequence;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude.doubleValue();
    }

    public double getLongitude() {
        return longitude.doubleValue();
    }
}
