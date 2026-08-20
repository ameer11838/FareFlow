package com.fareflow.network;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the network into memory once at startup and serves it to the planner.
 *
 * <p>Cached because the network is small, read-only at runtime, and touched almost
 * entirely by every search. Reloading per request would turn one journey search
 * into dozens of queries for data that never changes between deploys.
 */
@Service
public class TransitGraphService {

    private static final Logger log = LoggerFactory.getLogger(TransitGraphService.class);

    private final TransitNetworkRepository stopRepository;
    private final TransitLineRepository lineRepository;
    private final TransitLineStopRepository lineStopRepository;

    private volatile TransitGraph graph;
    /** line code -> fare policy code, handed to the fare engine. */
    private volatile Map<String, String> policyByLineCode = Map.of();

    public TransitGraphService(TransitNetworkRepository stopRepository,
                               TransitLineRepository lineRepository,
                               TransitLineStopRepository lineStopRepository) {
        this.stopRepository = stopRepository;
        this.lineRepository = lineRepository;
        this.lineStopRepository = lineStopRepository;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        Map<Long, TransitStop> stopsById = stopRepository.findAll().stream()
                .collect(Collectors.toMap(TransitStop::getId, Function.identity()));

        Map<Long, TransitLine> linesById = lineRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(TransitLine::getId, Function.identity()));

        Map<Long, List<TransitLineStop>> stopsByLine = lineStopRepository
                .findAllByOrderByLineIdAscSequenceAsc().stream()
                .filter(entry -> linesById.containsKey(entry.getLineId()))
                .collect(Collectors.groupingBy(TransitLineStop::getLineId));

        List<TransitGraph.LineRoute> routes = new ArrayList<>();
        for (Map.Entry<Long, List<TransitLineStop>> entry : stopsByLine.entrySet()) {
            TransitLine line = linesById.get(entry.getKey());
            List<TransitGraph.LineRoute.Stop> stops = entry.getValue().stream()
                    .map(lineStop -> new TransitGraph.LineRoute.Stop(
                            stopsById.get(lineStop.getStopId()), lineStop.getMinutesFromStart()))
                    .filter(stop -> stop.stop() != null)
                    .toList();
            if (stops.size() >= 2) {
                routes.add(new TransitGraph.LineRoute(line, stops));
            }
        }

        this.graph = new TransitGraph(routes);
        this.policyByLineCode = linesById.values().stream()
                .collect(Collectors.toMap(TransitLine::getCode, TransitLine::getFarePolicy));

        log.info("Transit network loaded: {} stops, {} lines", stopsById.size(), routes.size());
    }

    public TransitGraph graph() {
        if (graph == null) {
            reload();
        }
        return graph;
    }

    public Map<String, String> policyByLineCode() {
        return policyByLineCode;
    }
}
