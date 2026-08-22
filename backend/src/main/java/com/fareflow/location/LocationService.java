package com.fareflow.location;

import com.fareflow.gtfs.GtfsStopService;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location search with a cache and a fallback chain.
 *
 * <p>Merges imported GTFS stops, a nationwide geocoder, and the small offline
 * gazetteer. GTFS results are the only candidates that imply FareFlow has a real
 * schedule feed; geocoded places remain useful map/search anchors without making
 * that coverage claim.
 *
 * <p>The cache is a bounded in-memory map with a TTL. Deliberately not Redis yet:
 * a single instance has no coherence problem to solve, and adding a dependency to
 * avoid a HashMap would be architecture theatre. The interface is small enough that
 * swapping it later is a contained change.
 */
public class LocationService {

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final int PROVIDER_CANDIDATE_LIMIT = 10;

    private record CacheEntry(List<LocationCandidate> results, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final GeocodingProvider primary;
    private final GeocodingProvider fallback;
    private final GtfsStopService gtfsStops;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LocationService(GeocodingProvider primary, GeocodingProvider fallback,
                           GtfsStopService gtfsStops) {
        this.primary = primary;
        this.fallback = fallback;
        this.gtfsStops = gtfsStops;
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

        int boundedLimit = Math.clamp(limit, 1, 10);

        // Curated places first, then imported transit stops, then the geocoder.
        //
        // This ordering is not a fallback, it is a correctness decision. A generic
        // POI search can resolve a city nickname to a same-named business. The
        // gazetteer protects familiar aliases while GTFS supplies authoritative
        // station identities and TomTom covers the nationwide long tail.
        // Pull enough candidates from every source before trimming. Otherwise a
        // broad built-in city match ("Chicago") can crowd out the precise station
        // or address the rider actually typed ("Chicago Union Station").
        List<LocationCandidate> curated = fallback.search(query, PROVIDER_CANDIDATE_LIMIT);
        List<LocationCandidate> transitStops =
                gtfsStops.searchLocations(query, PROVIDER_CANDIDATE_LIMIT);
        List<LocationCandidate> geocoded = primary.search(query, PROVIDER_CANDIDATE_LIMIT);

        List<LocationCandidate> results = new java.util.ArrayList<>(curated);
        merge(results, transitStops, Integer.MAX_VALUE);
        merge(results, geocoded, Integer.MAX_VALUE);
        String normalizedQuery = normalized(query);
        // List.sort is stable, so equally relevant candidates retain the source
        // authority above: curated, then GTFS, then general geocoding.
        results.sort(Comparator.comparingInt(candidate ->
                relevance(normalizedQuery, normalized(candidate.displayName()))));
        results = List.copyOf(results.subList(0, Math.min(boundedLimit, results.size())));

        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear(); // Crude but bounded; an LRU is not worth the code here.
        }
        cache.put(key, new CacheEntry(results, Instant.now().plus(CACHE_TTL)));
        return results;
    }

    private static void merge(List<LocationCandidate> results,
                              List<LocationCandidate> candidates, int limit) {
        for (LocationCandidate candidate : candidates) {
            boolean duplicate = results.stream().anyMatch(existing ->
                    existing.displayName().equalsIgnoreCase(candidate.displayName())
                            || (existing.source().equals(candidate.source())
                                && existing.distanceMetresTo(
                                    candidate.latitude(), candidate.longitude()) < 50));
            if (!duplicate && results.size() < limit) {
                results.add(candidate);
            }
        }
    }

    /** Best match for a free-text place, for callers that need exactly one. */
    public Optional<LocationCandidate> resolve(String query) {
        return search(query, PROVIDER_CANDIDATE_LIMIT).stream().findFirst();
    }

    private static int relevance(String query, String candidate) {
        if (candidate.equals(query)) {
            return 0;
        }
        if (query.contains(candidate)) {
            // Prefer the most specific contained name: "Chicago Union Station"
            // must outrank the generic "Chicago" geography.
            return 100 - Math.min(candidate.length(), 99);
        }
        if (candidate.contains(query)) {
            // For autocomplete, prefer the shortest completion of what was typed.
            return 200 + Math.min(candidate.length() - query.length(), 99);
        }
        return 400;
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
