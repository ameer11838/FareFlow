package com.fareflow.discovery;

import com.fareflow.journey.Journey;
import com.fareflow.location.LocationCandidate;

import java.util.List;

/**
 * Produces candidate journeys between two resolved places.
 *
 * <p>The seam that keeps the optimization engine ignorant of data sources. Google
 * Routes, imported GTFS, and the curated fallback all implement this contract, so
 * everything downstream—usage fares, scoring, payments, explanations, and the
 * map—remains FareFlow-owned.
 */
public interface RouteDiscoveryProvider {

    String sourceName();

    /**
     * @return candidate journeys, best-effort and unranked. Empty when this provider
     *         cannot serve the pair — never a fabricated itinerary.
     */
    List<Journey> discover(LocationCandidate origin, LocationCandidate destination);
}
