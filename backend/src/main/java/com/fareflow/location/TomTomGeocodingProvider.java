package com.fareflow.location;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Geocoding backed by TomTom's Search API.
 *
 * <p>TomTom's fuzzy search is used rather than plain geocoding because it resolves
 * points of interest as well as addresses — "NJIT" and "Times Square" are POIs, not
 * street addresses, and a rider types those far more often than a postal address.
 *
 * <p>Never called from unit tests: {@code StaticGeocodingProvider} backs those, so
 * the suite has no third-party dependency and no network flakiness.
 */
public final class TomTomGeocodingProvider implements GeocodingProvider {

    public static final String SOURCE = "TOMTOM";
    private static final Logger log = LoggerFactory.getLogger(TomTomGeocodingProvider.class);

    private final RestClient restClient;
    private final String apiKey;

    public TomTomGeocodingProvider(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public List<LocationCandidate> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            JsonNode response = restClient.get()
                    .uri(builder -> builder
                            .path("/search/2/search/{query}.json")
                            .queryParam("key", apiKey)
                            .queryParam("limit", Math.clamp(limit, 1, 10))
                            // FareFlow's current product scope is U.S. public transit,
                            // but search is nationwide rather than pinned to one
                            // corridor. Imported GTFS candidates disambiguate actual
                            // stops before this general place-search result is used.
                            .queryParam("countrySet", "US")
                            // Ask for places and addresses ahead of businesses.
                            .queryParam("idxSet", "Geo,PAD,Str,Xstr,POI")
                            .build(query.trim()))
                    .retrieve()
                    .body(JsonNode.class);

            return parse(response);
        } catch (Exception exception) {
            // A geocoder outage must not take the page down: the caller falls back
            // to the static provider, and the user still gets known locations.
            log.warn("TomTom geocoding failed for '{}': {}", query, exception.toString());
            return List.of();
        }
    }

    private List<LocationCandidate> parse(JsonNode response) {
        if (response == null || !response.has("results")) {
            return List.of();
        }

        List<LocationCandidate> candidates = new ArrayList<>();
        List<LocationCandidate> places = new ArrayList<>();
        for (JsonNode result : response.get("results")) {
            JsonNode position = result.path("position");
            if (!position.hasNonNull("lat") || !position.hasNonNull("lon")) {
                continue;
            }
            JsonNode address = result.path("address");
            String poiName = result.path("poi").path("name").asText(null);
            String freeform = address.path("freeformAddress").asText("");

            String type = result.path("type").asText("Geography");
            LocationCandidate candidate = new LocationCandidate(
                    result.path("id").asText(null),
                    poiName != null && !poiName.isBlank() ? poiName : freeform,
                    address.path("municipality").asText(""),
                    address.path("countrySubdivision").asText(""),
                    address.path("countryCode").asText("US"),
                    position.get("lat").asDouble(),
                    position.get("lon").asDouble(),
                    type,
                    SOURCE);

            // Geographies (cities, neighbourhoods) ahead of businesses: someone
            // typing a place name wants the place, not a shop named after it.
            if ("Geography".equalsIgnoreCase(type)) {
                places.add(candidate);
            } else {
                candidates.add(candidate);
            }
        }

        places.addAll(candidates);
        return List.copyOf(places);
    }
}
