package com.fareflow.route;

import com.fareflow.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read access to the transit catalog. No scoring happens here — that is the
 * recommendation feature's job.
 */
@Service
@Transactional(readOnly = true)
public class TransitRouteService {

    private final TransitRouteRepository routeRepository;

    public TransitRouteService(TransitRouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public List<TransitRoute> findActiveRoutes(String origin, String destination) {
        return routeRepository.findActiveByOriginAndDestination(origin, destination);
    }

    public TransitRoute getById(long routeId) {
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Transit route %d was not found".formatted(routeId)));
    }

    public List<String> findOrigins() {
        return routeRepository.findDistinctOrigins();
    }

    public List<String> findDestinations() {
        return routeRepository.findDistinctDestinations();
    }
}
