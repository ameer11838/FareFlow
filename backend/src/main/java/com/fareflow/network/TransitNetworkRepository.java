package com.fareflow.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransitNetworkRepository extends JpaRepository<TransitStop, Long> {

    /**
     * Stops inside a latitude/longitude box.
     *
     * <p>A box rather than a radius on purpose: it is index-friendly, and the caller
     * refines with a true haversine distance afterwards. Doing the cheap filter in
     * SQL and the exact one in Java avoids either a PostGIS dependency or a full scan.
     */
    List<TransitStop> findByLatitudeBetweenAndLongitudeBetween(
            java.math.BigDecimal minLatitude, java.math.BigDecimal maxLatitude,
            java.math.BigDecimal minLongitude, java.math.BigDecimal maxLongitude);
}
