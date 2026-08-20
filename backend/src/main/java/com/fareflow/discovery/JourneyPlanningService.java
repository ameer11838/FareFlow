package com.fareflow.discovery;

import com.fareflow.fare.FareCalculation;
import com.fareflow.fare.FareEngine;
import com.fareflow.fare.UserFareContext;
import com.fareflow.journey.Journey;
import com.fareflow.location.LocationCandidate;
import com.fareflow.network.TransitGraphService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers journeys between two places and prices each one.
 *
 * <p>Providers are consulted in order and the first that returns anything wins, so
 * a future live-feed provider takes precedence simply by ordering ahead of the
 * curated network. Discovery and pricing stay separate: a provider produces
 * movement, the fare engine produces money.
 */
@Service
@Transactional(readOnly = true)
public class JourneyPlanningService {

    /** A journey with its price attached. */
    public record PricedJourney(Journey journey, FareCalculation fare) {
    }

    private final List<RouteDiscoveryProvider> providers;
    private final FareEngine fareEngine;
    private final TransitGraphService graphService;

    public JourneyPlanningService(List<RouteDiscoveryProvider> providers,
                                  FareEngine fareEngine,
                                  TransitGraphService graphService) {
        this.providers = List.copyOf(providers);
        this.fareEngine = fareEngine;
        this.graphService = graphService;
    }

    public List<PricedJourney> plan(LocationCandidate origin,
                                    LocationCandidate destination,
                                    UserFareContext fareContext) {

        List<Journey> journeys = discover(origin, destination);
        if (journeys.isEmpty()) {
            return List.of();
        }

        Map<String, String> policyByLine = graphService.policyByLineCode();

        // Keyed by the sequence of lines so two itineraries riding the same lines
        // collapse into one option rather than cluttering the results.
        Map<String, PricedJourney> priced = new LinkedHashMap<>();
        for (Journey journey : journeys) {
            FareCalculation fare = fareEngine.price(journey, fareContext, policyByLine);
            priced.putIfAbsent(journey.id(), new PricedJourney(journey, fare));
        }

        return List.copyOf(priced.values());
    }

    private List<Journey> discover(LocationCandidate origin, LocationCandidate destination) {
        for (RouteDiscoveryProvider provider : providers) {
            List<Journey> journeys = provider.discover(origin, destination);
            if (!journeys.isEmpty()) {
                return journeys;
            }
        }
        return List.of();
    }

    public List<String> providerNames() {
        return providers.stream().map(RouteDiscoveryProvider::sourceName).toList();
    }
}
