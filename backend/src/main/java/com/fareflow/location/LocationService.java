package com.fareflow.location;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location search with a cache and a fallback chain.
 *
 * <p>Tries the primary geocoder (TomTom) and falls back to the built-in gazetteer
 * when it returns nothing — an outage, a missing key, or a query outside coverage.
 * The user always gets an answer for places FareFlow can actually route.
 *
 * <p>The cache is a bounded in-memory map with a TTL. Deliberately not Redis yet:
 * a single instance has no coherence problem to solve, and adding a dependency to
 * avoid a HashMap would be architecture theatre. The interface is small enough that
 * swapping it later is a contained change.
 */
@Service
public class LocationService {

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_CACHE_ENTRIES = 500;

    private record CacheEntry(List<LocationCandidate> results, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final GeocodingProvider primary;
    private final GeocodingProvider fallback;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LocationService(GeocodingProvider primary, GeocodingProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public List<LocationCandidate> search(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        String key = query.trim().toLowerCase() + "#" + limit;
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.results();
        }

        // Curated places first, then the geocoder for everything else.
        //
        // This ordering is not a fallback, it is a correctness decision. A generic
        // POI search resolves "Philly" to a pretzel shop and "Brooklyn" to Brooklyn,
        // Maryland. For the corridor FareFlow actually serves, the curated gazetteer
        // is simply more accurate, so it wins; TomTom covers the long tail.
        List<LocationCandidate> curated = fallback.search(query, limit);
        List<LocationCandidate> geocoded = primary.search(query, limit);

        List<LocationCandidate> results = new java.util.ArrayList<>(curated);
        for (LocationCandidate candidate : geocoded) {
            boolean duplicate = results.stream().anyMatch(existing ->
                    existing.displayName().equalsIgnoreCase(candidate.displayName())
                            || existing.distanceMetresTo(candidate.latitude(), candidate.longitude()) < 500);
            if (!duplicate && results.size() < limit) {
                results.add(candidate);
            }
        }
        results = List.copyOf(results);

        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear(); // Crude but bounded; an LRU is not worth the code here.
        }
        cache.put(key, new CacheEntry(results, Instant.now().plus(CACHE_TTL)));
        return results;
    }

    /** Best match for a free-text place, for callers that need exactly one. */
    public Optional<LocationCandidate> resolve(String query) {
        return search(query, 1).stream().findFirst();
    }
}
