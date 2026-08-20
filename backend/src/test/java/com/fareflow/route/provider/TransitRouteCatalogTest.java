package com.fareflow.route.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog is the seam that lets a live transit feed replace seeded data without
 * the recommendation engine noticing. Plain JUnit — no Spring, no database.
 */
class TransitRouteCatalogTest {

    private static TransitRouteData route(long id, String source) {
        return new TransitRouteData(id, "Newark", "Manhattan", "PATH", "PATH", "SUBWAY",
                38, 300, 0, source,
                List.of(new TransitRouteData.Waypoint("Newark Penn Station", 40.735657, -74.164306),
                        new TransitRouteData.Waypoint("World Trade Center", 40.712600, -74.011300)),
                TransitRouteData.GeometrySource.SCHEMATIC);
    }

    /** Minimal stub standing in for a future live-feed provider. */
    private record StubProvider(String sourceName, boolean supports,
                                List<TransitRouteData> routes) implements TransitRouteProvider {
        @Override
        public boolean supports(String origin, String destination) {
            return supports;
        }

        @Override
        public List<TransitRouteData> findRoutes(String origin, String destination) {
            return routes;
        }

        @Override
        public List<String> knownOrigins() {
            return List.of("Newark");
        }

        @Override
        public List<String> knownDestinations() {
            return List.of("Manhattan");
        }
    }

    @Test
    @DisplayName("the first provider that supports the pair wins")
    void firstSupportingProviderWins() {
        TransitRouteCatalog catalog = new TransitRouteCatalog(List.of(
                new StubProvider("live", true, List.of(route(1L, "live"))),
                new StubProvider("database", true, List.of(route(2L, "database")))));

        assertThat(catalog.findRoutes("Newark", "Manhattan"))
                .singleElement()
                .extracting(TransitRouteData::sourceName)
                .isEqualTo("live");
    }

    @Test
    @DisplayName("a provider that does not support the pair is skipped")
    void unsupportedProviderIsSkipped() {
        TransitRouteCatalog catalog = new TransitRouteCatalog(List.of(
                new StubProvider("live", false, List.of(route(1L, "live"))),
                new StubProvider("database", true, List.of(route(2L, "database")))));

        assertThat(catalog.findRoutes("Newark", "Manhattan"))
                .singleElement()
                .extracting(TransitRouteData::sourceName)
                .isEqualTo("database");
    }

    @Test
    @DisplayName("a provider that claims support but returns nothing falls through")
    void emptyResultFallsThrough() {
        // Guards the realistic failure mode where a live feed is up but has no data
        // for this pair. Falling back to the database beats returning nothing.
        TransitRouteCatalog catalog = new TransitRouteCatalog(List.of(
                new StubProvider("live", true, List.of()),
                new StubProvider("database", true, List.of(route(2L, "database")))));

        assertThat(catalog.findRoutes("Newark", "Manhattan"))
                .singleElement()
                .extracting(TransitRouteData::sourceName)
                .isEqualTo("database");
    }

    @Test
    @DisplayName("no provider serving the pair yields an empty list, not an error")
    void noProviderYieldsEmpty() {
        TransitRouteCatalog catalog = new TransitRouteCatalog(List.of(
                new StubProvider("live", false, List.of())));

        assertThat(catalog.findRoutes("Atlantis", "Manhattan")).isEmpty();
    }

    @Test
    @DisplayName("locations are the de-duplicated union across providers")
    void locationsAreUnioned() {
        TransitRouteCatalog catalog = new TransitRouteCatalog(List.of(
                new StubProvider("live", true, List.of()),
                new StubProvider("database", true, List.of())));

        assertThat(catalog.knownOrigins()).containsExactly("Newark");
        assertThat(catalog.sourceNames()).containsExactly("live", "database");
    }

    @Test
    @DisplayName("geometry defaults are safe when a source provides none")
    void geometryDefaults() {
        TransitRouteData bare = TransitRouteData.withoutGeometry(
                1L, "Newark", "Manhattan", "PATH", "PATH", "SUBWAY", 38, 300, 0, "stub");

        assertThat(bare.waypoints()).isEmpty();
        assertThat(bare.geometrySource()).isEqualTo(TransitRouteData.GeometrySource.NONE);
    }

    @Test
    @DisplayName("geometry travels with the route but never reaches the scorer")
    void geometryIsSeparateFromScoring() {
        TransitRouteData data = route(1L, "database");

        assertThat(data.waypoints()).hasSize(2);
        assertThat(data.geometrySource()).isEqualTo("SCHEMATIC");
        // RouteCandidate -- what the optimization engine actually consumes -- has
        // no geometry field at all, so shape cannot influence a recommendation.
        assertThat(data.toCandidate().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("waypoints", "geometry", "latitude", "longitude");
    }

    @Test
    @DisplayName("route data converts to the engine's candidate type without loss")
    void convertsToCandidate() {
        var candidate = route(7L, "database").toCandidate();

        assertThat(candidate.routeId()).isEqualTo(7L);
        assertThat(candidate.fareCents()).isEqualTo(300);
        assertThat(candidate.durationMinutes()).isEqualTo(38);
        assertThat(candidate.providerDisplayName()).isEqualTo("PATH");
    }
}
