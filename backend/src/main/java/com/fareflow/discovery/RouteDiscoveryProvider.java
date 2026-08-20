package com.fareflow.discovery;

import com.fareflow.journey.Journey;
import com.fareflow.location.LocationCandidate;

import java.util.List;

/**
 * Produces candidate journeys between two resolved places.
 *
 * <p>The seam that keeps the optimization engine ignorant of data sources. A GTFS
 * loader or a third-party transit API would implement this and everything
 * downstream — fares, scoring, explanations, the map — would be unchanged.
 */
public interface RouteDiscoveryProvider {

    String sourceName();

    /**
     * @return candidate journeys, best-effort and unranked. Empty when this provider
     *         cannot serve the pair — never a fabricated itinerary.
     */
    List<Journey> discover(LocationCandidate origin, LocationCandidate destination);
}
