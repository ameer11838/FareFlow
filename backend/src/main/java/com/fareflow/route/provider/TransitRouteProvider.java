package com.fareflow.route.provider;

import java.util.List;

/**
 * A source of transit routes.
 *
 * <p>The recommendation engine does not care where routes come from. Today the only
 * implementation reads the seeded {@code transit_routes} table; a later phase can add
 * one backed by a live transit feed without touching the scorer, the service, or the
 * controller.
 *
 * <p>Implementations must be side-effect free and safe to call on every request.
 */
public interface TransitRouteProvider {

    /** Stable identifier for this source, echoed into responses for traceability. */
    String sourceName();

    /**
     * Whether this provider claims to serve the given pair. Used by
     * {@link TransitRouteCatalog} to pick a provider without fetching first, so a
     * remote source is not called for a region it does not cover.
     */
    boolean supports(String origin, String destination);

    /** Active routes for the pair, or an empty list. Never null. */
    List<TransitRouteData> findRoutes(String origin, String destination);

    List<String> knownOrigins();

    List<String> knownDestinations();
}
