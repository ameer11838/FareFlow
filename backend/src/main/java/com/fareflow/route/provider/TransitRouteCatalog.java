package com.fareflow.route.provider;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Front door to every {@link TransitRouteProvider}.
 *
 * <p>Providers are consulted in {@code @Order} sequence and the first one that serves
 * the pair wins. Spring injects them ordered, so adding a live-feed provider with a
 * lower order value makes it take precedence over the seeded database automatically —
 * no change here, and none in the recommendation engine.
 */
@Service
public class TransitRouteCatalog {

    private final List<TransitRouteProvider> providers;

    public TransitRouteCatalog(List<TransitRouteProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<TransitRouteData> findRoutes(String origin, String destination) {
        for (TransitRouteProvider provider : providers) {
            if (provider.supports(origin, destination)) {
                List<TransitRouteData> routes = provider.findRoutes(origin, destination);
                if (!routes.isEmpty()) {
                    return routes;
                }
            }
        }
        return List.of();
    }

    /** Union across providers, de-duplicated, for the search form's suggestions. */
    public List<String> knownOrigins() {
        return union(TransitRouteProvider::knownOrigins);
    }

    public List<String> knownDestinations() {
        return union(TransitRouteProvider::knownDestinations);
    }

    public List<String> sourceNames() {
        return providers.stream().map(TransitRouteProvider::sourceName).toList();
    }

    private List<String> union(java.util.function.Function<TransitRouteProvider, List<String>> extractor) {
        Set<String> merged = new LinkedHashSet<>();
        providers.forEach(provider -> merged.addAll(extractor.apply(provider)));
        return List.copyOf(merged);
    }
}
