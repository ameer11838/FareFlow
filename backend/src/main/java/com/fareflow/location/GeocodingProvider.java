package com.fareflow.location;

import java.util.List;

/**
 * Turns typed text into places.
 *
 * <p>An interface because the geocoder is a swappable dependency and because unit
 * tests must never call a live third-party API. {@code StaticGeocodingProvider}
 * backs the tests; {@code TomTomGeocodingProvider} backs the running app.
 */
public interface GeocodingProvider {

    String sourceName();

    /** Ordered best-first. Empty when nothing matches — never null. */
    List<LocationCandidate> search(String query, int limit);
}
