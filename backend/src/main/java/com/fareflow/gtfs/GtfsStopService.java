package com.fareflow.gtfs;

import com.fareflow.location.LocationCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Search/read model for real stops contained in successfully imported GTFS feeds. */
@Service
@Transactional(readOnly = true)
public class GtfsStopService {

    public static final String SOURCE = "GTFS";
    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int MAX_NEARBY_LIMIT = 80;

    private final GtfsScheduleRepository repository;

    public GtfsStopService(GtfsScheduleRepository repository) {
        this.repository = repository;
    }

    public List<LocationCandidate> searchLocations(String rawQuery, int requestedLimit) {
        if (rawQuery == null || rawQuery.trim().length() < 2) {
            return List.of();
        }
        int limit = Math.clamp(requestedLimit, 1, MAX_SEARCH_LIMIT);
        SearchParts parts = parseSearchLabel(rawQuery.trim());
        LinkedHashMap<String, LocationCandidate> unique = new LinkedHashMap<>();
        for (GtfsScheduleRepository.StopLocation stop :
                repository.searchStops(parts.stopName(), parts.regionHint(), limit)) {
            String key = normalized(stop.name()) + "|" + stop.regionCode();
            unique.putIfAbsent(key, toLocation(stop));
            if (unique.size() >= limit) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    public List<TransitStop> nearby(double latitude, double longitude,
                                    double requestedRadiusMetres, int requestedLimit) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("latitude/longitude are out of range");
        }
        double radius = Math.clamp(requestedRadiusMetres, 100, 10_000);
        int limit = Math.clamp(requestedLimit, 1, MAX_NEARBY_LIMIT);
        LinkedHashMap<String, TransitStop> unique = new LinkedHashMap<>();
        // Fetch extra candidates because a parent station and its platforms often
        // share a name and coordinates. The map should show one useful marker.
        for (GtfsScheduleRepository.Stop stop :
                repository.stopsNear(latitude, longitude, radius, Math.min(limit * 3, 240))) {
            GtfsScheduleRepository.StopLocation described = repository.describeStop(stop);
            if (described == null || described.modes().isEmpty()) {
                continue;
            }
            String key = normalized(described.name()) + "|" + described.regionCode();
            unique.putIfAbsent(key, toTransitStop(described,
                    Math.round(LocationCandidate.haversineMetres(latitude, longitude,
                            described.latitude(), described.longitude()))));
            if (unique.size() >= limit) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private static LocationCandidate toLocation(GtfsScheduleRepository.StopLocation stop) {
        String displayName = stop.name() + " — " + stop.regionName();
        String type = stop.modes().stream().anyMatch(mode ->
                mode.equals("RAIL") || mode.equals("SUBWAY") || mode.equals("LIGHT_RAIL"))
                ? "TRANSIT_STATION" : "TRANSIT_STOP";
        return new LocationCandidate("gtfs:" + stop.key(), displayName,
                stop.publisherName(), stop.regionName(), "US", stop.latitude(), stop.longitude(),
                type, SOURCE);
    }

    private static TransitStop toTransitStop(GtfsScheduleRepository.StopLocation stop,
                                             long distanceMetres) {
        return new TransitStop("gtfs:" + stop.key(), stop.name(), stop.regionCode(),
                stop.regionName(), stop.publisherName(), stop.latitude(), stop.longitude(),
                stop.modes(), stop.operators(), stop.lines(), distanceMetres,
                stop.realtimeAvailable(), SOURCE);
    }

    private static SearchParts parseSearchLabel(String query) {
        String[] parts = query.split("\\s+[—–]\\s+", 2);
        return new SearchParts(parts[0].trim(), parts.length > 1 ? parts[1].trim() : null);
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record SearchParts(String stopName, String regionHint) {
    }

    public record TransitStop(String id, String name, String regionCode, String regionName,
                              String publisherName, double latitude, double longitude,
                              List<String> modes, List<String> operators, List<String> lines,
                              long distanceMetres, boolean realtimeAvailable, String source) {
    }
}
