package com.fareflow.location;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Geocoding backed by the Google Places API (New) Text Search endpoint.
 *
 * <p>Text Search is used rather than the Geocoding API because it resolves points
 * of interest as well as street addresses — "NJIT" and "Times Square" are POIs, not
 * postal addresses, and a rider types those far more often than a house number.
 *
 * <p>Sharing one Google Maps Platform key with route discovery is deliberate: the
 * rider's typed origin resolves through the same vendor that later returns the
 * transit itinerary, so a place Google can find is a place Google can route from.
 *
 * <p>Never called from unit tests: {@code StaticGeocodingProvider} backs those, so
 * the suite has no third-party dependency and no network flakiness.
 */
public final class GoogleGeocodingProvider implements GeocodingProvider {

    public static final String SOURCE = "GOOGLE";
    private static final Logger log = LoggerFactory.getLogger(GoogleGeocodingProvider.class);

    /**
     * Only the fields FareFlow actually reads. Places charges by field mask, so
     * requesting the whole place object would cost more for data we discard.
     */
    private static final String FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.shortFormattedAddress",
            "places.location",
            "places.primaryType",
            "places.addressComponents");

    private final RestClient restClient;
    private final String apiKey;

    public GoogleGeocodingProvider(RestClient restClient, String apiKey) {
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
            JsonNode response = restClient.post()
                    .uri("/v1/places:searchText")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(Map.of(
                            "textQuery", query.trim(),
                            // FareFlow's product scope is U.S. public transit, but
                            // search is nationwide rather than pinned to one
                            // corridor. Imported GTFS candidates disambiguate
                            // actual stops before this general place result is used.
                            "regionCode", "US",
                            "languageCode", "en",
                            "maxResultCount", Math.clamp(limit, 1, 10)))
                    .retrieve()
                    .body(JsonNode.class);

            return parse(response);
        } catch (Exception exception) {
            // A geocoder outage must not take the page down: the caller falls back
            // to the static provider, and the user still gets known locations.
            log.warn("Google place search failed for '{}': {}", query, exception.toString());
            return List.of();
        }
    }

    private List<LocationCandidate> parse(JsonNode response) {
        if (response == null || !response.has("places")) {
            return List.of();
        }

        List<LocationCandidate> places = new ArrayList<>();
        List<LocationCandidate> businesses = new ArrayList<>();
        for (JsonNode place : response.get("places")) {
            JsonNode location = place.path("location");
            if (!location.hasNonNull("latitude") || !location.hasNonNull("longitude")) {
                continue;
            }

            String displayName = place.path("displayName").path("text").asText("");
            if (displayName.isBlank()) {
                displayName = place.path("formattedAddress").asText("");
            }
            if (displayName.isBlank()) {
                continue;
            }

            String primaryType = place.path("primaryType").asText("");
            LocationCandidate candidate = new LocationCandidate(
                    place.path("id").asText(null),
                    displayName,
                    component(place, "locality"),
                    component(place, "administrative_area_level_1"),
                    componentOrDefault(place, "country", "US"),
                    location.get("latitude").asDouble(),
                    location.get("longitude").asDouble(),
                    primaryType.isBlank() ? "geography" : primaryType,
                    SOURCE);

            // Geographies and transit stations ahead of businesses: someone typing
            // a place name wants the place, not a shop named after it.
            if (isPlaceLike(primaryType)) {
                places.add(candidate);
            } else {
                businesses.add(candidate);
            }
        }

        places.addAll(businesses);
        return List.copyOf(places);
    }

    /**
     * Google's {@code primaryType} is a long taxonomy. Rather than enumerate it,
     * treat anything administrative, geographic, or transit-related as place-like:
     * those are the categories a transit rider means when they type a name.
     */
    private static boolean isPlaceLike(String primaryType) {
        if (primaryType == null || primaryType.isBlank()) {
            return true;
        }
        String type = primaryType.toLowerCase(java.util.Locale.ROOT);
        return type.startsWith("administrative_area")
                || type.startsWith("sublocality")
                || type.contains("locality")
                || type.contains("neighborhood")
                || type.contains("postal_code")
                || type.contains("premise")
                || type.contains("street_address")
                || type.contains("route")
                || type.contains("airport")
                || type.contains("transit")
                || type.contains("station")
                || type.contains("subway")
                || type.contains("train")
                || type.contains("light_rail")
                || type.contains("bus")
                || type.contains("ferry")
                || type.contains("university")
                || type.contains("school");
    }

    /** First address component carrying {@code type}, by short name. */
    private static String component(JsonNode place, String type) {
        return componentOrDefault(place, type, "");
    }

    private static String componentOrDefault(JsonNode place, String type, String fallback) {
        for (JsonNode component : place.path("addressComponents")) {
            for (JsonNode componentType : component.path("types")) {
                if (type.equals(componentType.asText())) {
                    String shortName = component.path("shortText").asText("");
                    if (!shortName.isBlank()) {
                        return shortName;
                    }
                    String longName = component.path("longText").asText("");
                    if (!longName.isBlank()) {
                        return longName;
                    }
                }
            }
        }
        return fallback;
    }
}
