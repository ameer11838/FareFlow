package com.fareflow.route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransitRouteRepository extends JpaRepository<TransitRoute, Long> {

    /**
     * Active routes for an origin/destination pair, matched case-insensitively and
     * ignoring surrounding whitespace so "  newark " finds "Newark".
     *
     * <p>Ordered by id to give the scorer a stable input sequence — the ranking is
     * fully deterministic on its own, but a stable input keeps test failures readable.
     */
    @Query("""
            SELECT r FROM TransitRoute r
            WHERE r.active = true
              AND LOWER(TRIM(r.origin)) = LOWER(TRIM(:origin))
              AND LOWER(TRIM(r.destination)) = LOWER(TRIM(:destination))
            ORDER BY r.id
            """)
    List<TransitRoute> findActiveByOriginAndDestination(@Param("origin") String origin,
                                                        @Param("destination") String destination);

    @Query("SELECT DISTINCT r.origin FROM TransitRoute r WHERE r.active = true ORDER BY r.origin")
    List<String> findDistinctOrigins();

    @Query("SELECT DISTINCT r.destination FROM TransitRoute r WHERE r.active = true ORDER BY r.destination")
    List<String> findDistinctDestinations();
}
