package com.fareflow.recommendation.dto;

import com.fareflow.route.provider.TransitRouteData;

import java.util.List;

/**
 * The shape of a route, for drawing on a map.
 *
 * <p>Kept separate from the fare and timing fields on purpose: geometry is a
 * rendering concern, fares are a financial one, and the recommendation engine
 * consumes neither. A client can ignore this block entirely and still get a
 * complete recommendation.
 *
 * @param source SCHEMATIC means straight segments between real published station
 *               coordinates — an indicative corridor, not surveyed track geometry.
 *               Clients should label it as such rather than implying GPS precision.
 */
public record RouteGeometryDto(
        String source,
        List<WaypointDto> waypoints
) {

    public record WaypointDto(String name, double latitude, double longitude) {
    }

    public static RouteGeometryDto from(TransitRouteData route) {
        return new RouteGeometryDto(
                route.geometrySource(),
                route.waypoints().stream()
                        .map(point -> new WaypointDto(point.name(), point.latitude(), point.longitude()))
                        .toList());
    }
}
