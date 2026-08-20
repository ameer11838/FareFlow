package com.fareflow.route;

import com.fareflow.route.dto.TransitRouteResponse;
import com.fareflow.route.provider.TransitRouteCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Raw catalog access — no scoring. Useful for the search form and for debugging. */
@RestController
@RequestMapping("/api/transit-routes")
public class TransitRouteController {

    private final TransitRouteService routeService;
    private final TransitRouteCatalog routeCatalog;

    public TransitRouteController(TransitRouteService routeService, TransitRouteCatalog routeCatalog) {
        this.routeService = routeService;
        this.routeCatalog = routeCatalog;
    }

    @GetMapping
    public List<TransitRouteResponse> search(@RequestParam String origin,
                                             @RequestParam String destination) {
        return routeService.findActiveRoutes(origin.trim(), destination.trim()).stream()
                .map(TransitRouteResponse::from)
                .toList();
    }

    /** Populates the origin/destination pickers in the search UI. */
    @GetMapping("/locations")
    public Map<String, Object> locations() {
        return Map.of(
                "origins", routeCatalog.knownOrigins(),
                "destinations", routeCatalog.knownDestinations(),
                "sources", routeCatalog.sourceNames());
    }
}
