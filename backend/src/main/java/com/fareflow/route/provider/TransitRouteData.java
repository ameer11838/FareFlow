package com.fareflow.route.provider;

import com.fareflow.recommendation.optimization.RouteCandidate;

import java.util.List;

/**
 * A transit option as returned by a {@link TransitRouteProvider}.
 *
 * <p>Deliberately not the JPA entity. A provider backed by a live transit API has no
 * database row to hand back, so the contract is expressed in a plain value type that
 * any source can produce.
 *
 * <p>Geometry travels with the transit data, not with the map SDK. The map layer only
 * knows how to draw a list of coordinates; it has no opinion about what a route is.
 *
 * @param routeId        identifier within the providing source
 * @param fareCents      integer cents — never a floating point amount
 * @param waypoints      ordered stops; may be empty when a source has no geometry
 * @param geometrySource SCHEMATIC, SURVEYED, or NONE — see {@link GeometrySource}
 */
public record TransitRouteData(
        long routeId,
        String origin,
        String destination,
        String provider,
        String providerDisplayName,
        String mode,
        int durationMinutes,
        long fareCents,
        int transfers,
        String sourceName,
        List<Waypoint> waypoints,
        String geometrySource
) {

    public TransitRouteData {
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        geometrySource = geometrySource == null ? GeometrySource.NONE : geometrySource;
    }

    /** Convenience for sources that carry no geometry at all. */
    public static TransitRouteData withoutGeometry(long routeId, String origin, String destination,
                                                   String provider, String providerDisplayName,
                                                   String mode, int durationMinutes, long fareCents,
                                                   int transfers, String sourceName) {
        return new TransitRouteData(routeId, origin, destination, provider, providerDisplayName,
                mode, durationMinutes, fareCents, transfers, sourceName,
                List.of(), GeometrySource.NONE);
    }

    /** Converts to the value type the pure optimization engine consumes. */
    public RouteCandidate toCandidate() {
        return new RouteCandidate(routeId, provider, providerDisplayName, mode,
                durationMinutes, fareCents, transfers);
    }

    /** One ordered stop. Coordinates are geographic, so doubles are appropriate. */
    public record Waypoint(String name, double latitude, double longitude) {
    }

    /**
     * How much to trust a route's shape.
     *
     * <p>This distinction exists because TomTom's Routing API has no public-transit
     * mode. Drawing a driving route and calling it a train line would be a lie, so
     * clients are told exactly what they are rendering.
     */
    public static final class GeometrySource {
        /** Straight segments between real, published station coordinates. */
        public static final String SCHEMATIC = "SCHEMATIC";
        /** An actual agency shape, e.g. from GTFS shapes.txt. Not yet available. */
        public static final String SURVEYED = "SURVEYED";
        /** No geometry at all — the client should not draw a line. */
        public static final String NONE = "NONE";

        private GeometrySource() {
        }
    }
}
