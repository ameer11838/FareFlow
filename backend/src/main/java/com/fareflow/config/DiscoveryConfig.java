package com.fareflow.config;

import com.fareflow.discovery.NetworkRouteDiscoveryProvider;
import com.fareflow.discovery.RouteDiscoveryProvider;
import com.fareflow.location.GeocodingProvider;
import com.fareflow.location.StaticGeocodingProvider;
import com.fareflow.location.TomTomGeocodingProvider;
import com.fareflow.network.TransitGraphService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires geocoding and route discovery.
 *
 * <p>Beans are declared here rather than by annotating the classes, so
 * {@code location}, {@code discovery}, and {@code network} stay free of Spring and
 * remain constructible with {@code new} in unit tests.
 */
@Configuration
public class DiscoveryConfig {

    /**
     * The primary geocoder. Falls back to the static gazetteer when no TomTom key
     * is configured, so the app runs — and its tests pass — without one.
     */
    @Bean
    @Primary
    public GeocodingProvider primaryGeocodingProvider(
            @Value("${fareflow.tomtom.api-key:}") String apiKey,
            RestClient.Builder restClientBuilder) {

        if (apiKey == null || apiKey.isBlank()) {
            return new StaticGeocodingProvider();
        }

        RestClient client = restClientBuilder
                .baseUrl("https://api.tomtom.com")
                .requestFactory(timeoutFactory())
                .build();

        return new TomTomGeocodingProvider(client, apiKey.trim());
    }

    @Bean
    public StaticGeocodingProvider staticGeocodingProvider() {
        return new StaticGeocodingProvider();
    }

    @Bean
    public com.fareflow.location.LocationService locationService(
            GeocodingProvider primary, StaticGeocodingProvider fallback) {
        return new com.fareflow.location.LocationService(primary, fallback);
    }

    @Bean
    public RouteDiscoveryProvider networkRouteDiscoveryProvider(TransitGraphService graphService) {
        return new NetworkRouteDiscoveryProvider(graphService.graph());
    }

    /** Short timeouts: a slow geocoder must degrade to the fallback, not hang a request. */
    private static org.springframework.http.client.ClientHttpRequestFactory timeoutFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }
}
