package com.fareflow.route;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TransitRouteWaypointRepository extends JpaRepository<TransitRouteWaypoint, Long> {

    /** Batched by route id so rendering N routes costs one query, not N. */
    List<TransitRouteWaypoint> findByTransitRouteIdInOrderByTransitRouteIdAscSequenceAsc(
            Collection<Long> transitRouteIds);
}
