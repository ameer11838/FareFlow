package com.fareflow.route.provider;

import com.fareflow.route.TransitRoute;
import com.fareflow.route.TransitRouteRepository;
import com.fareflow.route.TransitRouteWaypoint;
import com.fareflow.route.TransitRouteWaypointRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serves routes from the {@code transit_routes} table seeded by Flyway, together
 * with their published station coordinates.
 *
 * <p>This remains the default source for development and tests even after live data
 * arrives: deterministic fixtures are what make the integration tests meaningful.
 */
@Component
@Order(100)
@Transactional(readOnly = true)
public class DatabaseTransitRouteProvider implements TransitRouteProvider {

    public static final String SOURCE_NAME = "database";

    private final TransitRouteRepository routeRepository;
    private final TransitRouteWaypointRepository waypointRepository;

    public DatabaseTransitRouteProvider(TransitRouteRepository routeRepository,
                                        TransitRouteWaypointRepository waypointRepository) {
        this.routeRepository = routeRepository;
        this.waypointRepository = waypointRepository;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean supports(String origin, String destination) {
        return !routeRepository.findActiveByOriginAndDestination(origin, destination).isEmpty();
    }

    @Override
    public List<TransitRouteData> findRoutes(String origin, String destination) {
        List<TransitRoute> routes = routeRepository.findActiveByOriginAndDestination(origin, destination);
        if (routes.isEmpty()) {
            return List.of();
        }

        // One batched query for every route's waypoints rather than N lazy loads.
        Map<Long, List<TransitRouteWaypoint>> waypointsByRoute =
                waypointRepository.findByTransitRouteIdInOrderByTransitRouteIdAscSequenceAsc(
                                routes.stream().map(TransitRoute::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(TransitRouteWaypoint::getTransitRouteId));

        return routes.stream()
                .map(route -> toData(route, waypointsByRoute.getOrDefault(route.getId(), List.of())))
                .toList();
    }

    @Override
    public List<String> knownOrigins() {
        return routeRepository.findDistinctOrigins();
    }

    @Override
    public List<String> knownDestinations() {
        return routeRepository.findDistinctDestinations();
    }

    private static TransitRouteData toData(TransitRoute route, List<TransitRouteWaypoint> waypoints) {
        List<TransitRouteData.Waypoint> points = waypoints.stream()
                .map(waypoint -> new TransitRouteData.Waypoint(
                        waypoint.getName(), waypoint.getLatitude(), waypoint.getLongitude()))
                .toList();

        // A route with no stored stops has no geometry, whatever the column says.
        String geometrySource = points.isEmpty()
                ? TransitRouteData.GeometrySource.NONE
                : route.getGeometrySource();

        return new TransitRouteData(
                route.getId(),
                route.getOrigin(),
                route.getDestination(),
                route.getProvider().name(),
                route.getProvider().displayName(),
                route.getMode().name(),
                route.getDurationMinutes(),
                route.getFareCents(),
                route.getTransfers(),
                SOURCE_NAME,
                points,
                geometrySource);
    }
}
