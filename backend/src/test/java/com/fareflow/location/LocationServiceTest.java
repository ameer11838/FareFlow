package com.fareflow.location;

import com.fareflow.gtfs.GtfsStopService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationServiceTest {

    @Test
    void preciseStationOutranksAContainedCityName() {
        LocationCandidate city = place("static:chicago", "Chicago", 41.8781, -87.6298, "STATIC");
        LocationCandidate station = place("tomtom:union", "Chicago Union Station",
                41.878846, -87.639487, "TOMTOM");
        LocationService service = service(List.of(station), List.of(city));

        assertThat(service.search("Chicago Union Station, Chicago IL", 6))
                .extracting(LocationCandidate::displayName)
                .startsWith("Chicago Union Station", "Chicago");
        assertThat(service.resolve("Chicago Union Station, Chicago IL").orElseThrow())
                .isEqualTo(station);
    }

    @Test
    void exactCuratedMatchKeepsAuthorityOverAnEquivalentProviderResult() {
        LocationCandidate curated = place("static:philadelphia", "Philadelphia",
                39.9526, -75.1652, "STATIC");
        LocationCandidate provider = place("tomtom:philadelphia", "Philadelphia",
                39.9527, -75.1651, "TOMTOM");
        LocationService service = service(List.of(provider), List.of(curated));

        assertThat(service.resolve("Philadelphia").orElseThrow()).isEqualTo(curated);
    }

    private static LocationService service(List<LocationCandidate> primaryResults,
                                           List<LocationCandidate> fallbackResults) {
        GeocodingProvider primary = provider("PRIMARY", primaryResults);
        GeocodingProvider fallback = provider("FALLBACK", fallbackResults);
        GtfsStopService gtfsStops = mock(GtfsStopService.class);
        when(gtfsStops.searchLocations(anyString(), anyInt())).thenReturn(List.of());
        return new LocationService(primary, fallback, gtfsStops);
    }

    private static GeocodingProvider provider(String name, List<LocationCandidate> results) {
        return new GeocodingProvider() {
            @Override
            public String sourceName() {
                return name;
            }

            @Override
            public List<LocationCandidate> search(String query, int limit) {
                return results.stream().limit(limit).toList();
            }
        };
    }

    private static LocationCandidate place(String id, String name,
                                           double latitude, double longitude, String source) {
        return new LocationCandidate(id, name, "Chicago", "IL", "US",
                latitude, longitude, "PLACE", source);
    }
}
