package com.fareflow.location;

import java.util.List;
import java.util.Locale;

/**
 * A small built-in gazetteer of places FareFlow's network actually serves.
 *
 * <p>Two jobs. It backs every unit and integration test, so the suite never touches
 * a third-party API. And it is the fallback when TomTom is unreachable or no key is
 * configured, which keeps the product usable rather than dead.
 *
 * <p>Coordinates are real published locations.
 */
public final class StaticGeocodingProvider implements GeocodingProvider {

    public static final String SOURCE = "STATIC";

    private record Entry(String name, String locality, String region,
                         double latitude, double longitude, String... aliases) {
    }

    private static final List<Entry> PLACES = List.of(
            new Entry("Philadelphia", "Philadelphia", "PA", 39.952600, -75.165200,
                    "philly", "philadelphia pa", "center city"),
            new Entry("30th Street Station", "Philadelphia", "PA", 39.955700, -75.182100,
                    "30th st", "30th street", "septa 30th"),
            new Entry("Trenton", "Trenton", "NJ", 40.218100, -74.754600, "trenton nj"),
            new Entry("Newark", "Newark", "NJ", 40.735657, -74.164306,
                    "newark nj", "newark penn", "newark penn station"),
            new Entry("New Jersey Institute of Technology", "Newark", "NJ", 40.742000, -74.178000,
                    "njit", "nj institute of technology"),
            new Entry("Newark Liberty International Airport", "Newark", "NJ", 40.706100, -74.186600,
                    "ewr", "newark airport"),
            new Entry("Jersey City", "Jersey City", "NJ", 40.728200, -74.077600, "jersey city nj"),
            new Entry("Hoboken", "Hoboken", "NJ", 40.735800, -74.027100, "hoboken nj"),
            new Entry("Manhattan", "New York", "NY", 40.758000, -73.985500,
                    "manhattan ny", "nyc", "new york", "midtown"),
            new Entry("Penn Station New York", "New York", "NY", 40.750568, -73.993519,
                    "penn station", "ny penn", "nyp"),
            new Entry("Times Square", "New York", "NY", 40.755700, -73.987000, "times sq"),
            new Entry("World Trade Center", "New York", "NY", 40.712600, -74.011300, "wtc"),
            new Entry("Brooklyn", "Brooklyn", "NY", 40.684400, -73.976500, "brooklyn ny"),
            new Entry("Princeton", "Princeton", "NJ", 40.348700, -74.659100, "princeton nj"),
            new Entry("Suburban Station", "Philadelphia", "PA", 39.954200, -75.166500,
                    "suburban station", "septa suburban"),
            new Entry("Journal Square", "Jersey City", "NJ", 40.732900, -74.063500, "journal sq"),
            new Entry("Secaucus Junction", "Secaucus", "NJ", 40.761600, -74.075700, "secaucus"),
            new Entry("Port Authority Bus Terminal", "New York", "NY", 40.757000, -73.990300,
                    "port authority", "pabt"),
            new Entry("Boston", "Boston", "MA", 42.360100, -71.058900, "boston ma"),
            new Entry("South Station", "Boston", "MA", 42.352300, -71.055200,
                    "boston south station"),
            new Entry("Harvard Square", "Cambridge", "MA", 42.373400, -71.118900,
                    "harvard", "harvard station"),
            new Entry("Chicago", "Chicago", "IL", 41.878100, -87.629800, "chicago il"),
            new Entry("Chicago Union Station", "Chicago", "IL", 41.878600, -87.640500,
                    "union station chicago"),
            new Entry("O'Hare International Airport", "Chicago", "IL", 41.974200, -87.907300,
                    "ohare", "ord", "chicago airport"),
            new Entry("San Francisco", "San Francisco", "CA", 37.774900, -122.419400,
                    "san francisco ca", "sf"),
            new Entry("Embarcadero Station", "San Francisco", "CA", 37.792900, -122.397100,
                    "embarcadero"),
            new Entry("Downtown Oakland", "Oakland", "CA", 37.804400, -122.271200,
                    "oakland", "oakland ca")
    );

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public List<LocationCandidate> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);

        return PLACES.stream()
                .filter(entry -> matches(entry, needle))
                // Prefix matches first: typing "new" should surface Newark before
                // New Jersey Institute of Technology.
                .sorted(java.util.Comparator.comparingInt(entry ->
                        entry.name().toLowerCase(Locale.ROOT).startsWith(needle) ? 0 : 1))
                .limit(Math.clamp(limit, 1, 10))
                .map(StaticGeocodingProvider::toCandidate)
                .toList();
    }

    private static boolean matches(Entry entry, String needle) {
        if (entry.name().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (String alias : entry.aliases()) {
            if (alias.contains(needle) || needle.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static LocationCandidate toCandidate(Entry entry) {
        return new LocationCandidate(
                "static:" + entry.name().toLowerCase(Locale.ROOT).replace(' ', '-'),
                entry.name(), entry.locality(), entry.region(), "US",
                entry.latitude(), entry.longitude(), "Geography", SOURCE);
    }
}
