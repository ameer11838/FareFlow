package com.fareflow.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fareflow.discovery.RouteDiscoveryProvider;
import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.TransitStopGeometry;
import com.fareflow.journey.TransitMode;
import com.fareflow.location.LocationCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Nationwide public-transit discovery backed by Google Maps Routes API.
 *
 * <p>Google supplies movement facts only: route alternatives, step geometry,
 * stops, schedules, lines, operators, and an estimated published fare when it can
 * determine one. FareFlow still scores the alternatives and its usage fare engine
 * remains the only authority for a transit-session charge.
 *
 * <p>The provider accepts only FareFlow's public-transit scope. Walking may connect
 * transit steps; a route containing a car, bicycle, share taxi, cable car, gondola,
 * or other unsupported vehicle is discarded rather than relabelled.
 */
@Order(0)
public final class GoogleRoutesTransitProvider implements RouteDiscoveryProvider {

    public static final String SOURCE = "GOOGLE_ROUTES";
    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesTransitProvider.class);
    private static final String ENDPOINT = "/directions/v2:computeRoutes";
    private static final String FIELD_MASK = String.join(",",
            "routes.duration",
            "routes.distanceMeters",
            "routes.travelAdvisory.transitFare",
            "routes.legs.steps.distanceMeters",
            "routes.legs.steps.staticDuration",
            "routes.legs.steps.polyline.geoJsonLinestring",
            "routes.legs.steps.startLocation",
            "routes.legs.steps.endLocation",
            "routes.legs.steps.navigationInstruction.instructions",
            "routes.legs.steps.travelMode",
            "routes.legs.steps.transitDetails.stopDetails",
            "routes.legs.steps.transitDetails.headsign",
            "routes.legs.steps.transitDetails.transitLine.agencies.name",
            "routes.legs.steps.transitDetails.transitLine.name",
            "routes.legs.steps.transitDetails.transitLine.nameShort",
            "routes.legs.steps.transitDetails.transitLine.vehicle.name",
            "routes.legs.steps.transitDetails.transitLine.vehicle.type",
            "routes.legs.steps.transitDetails.stopCount",
            "routes.legs.steps.transitDetails.tripShortText");

    private final RestClient restClient;
    private final String apiKey;
    private final Clock clock;

    public GoogleRoutesTransitProvider(RestClient restClient, String apiKey, Clock clock) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.clock = clock;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public List<Journey> discover(LocationCandidate origin, LocationCandidate destination) {
        if (apiKey.isBlank()) {
            return List.of();
        }

        Instant requestedDeparture = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> request = Map.of(
                "origin", waypoint(origin),
                "destination", waypoint(destination),
                "travelMode", "TRANSIT",
                "departureTime", requestedDeparture.toString(),
                "computeAlternativeRoutes", true,
                "polylineQuality", "HIGH_QUALITY",
                "polylineEncoding", "GEO_JSON_LINESTRING",
                "languageCode", "en-US",
                "regionCode", "US",
                "units", "IMPERIAL");

        try {
            JsonNode response = restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response, origin, destination, requestedDeparture);
        } catch (Exception exception) {
            // Discovery falls through to imported GTFS and then the curated graph.
            // Never turn a provider error into made-up route data.
            log.warn("Google transit routing failed from '{}' to '{}': {}",
                    origin.displayName(), destination.displayName(), exception.toString());
            return List.of();
        }
    }

    /** Package-visible for deterministic response-contract tests. */
    List<Journey> parseResponse(JsonNode response, LocationCandidate origin,
                                LocationCandidate destination, Instant requestedDeparture) {
        if (response == null || !response.path("routes").isArray()) {
            return List.of();
        }

        Map<String, Journey> unique = new LinkedHashMap<>();
        for (JsonNode route : response.path("routes")) {
            parseRoute(route, origin, destination, requestedDeparture)
                    .ifPresent(journey -> unique.merge(journey.id(), journey,
                            (existing, candidate) -> candidate.totalMinutes() < existing.totalMinutes()
                                    ? candidate : existing));
        }
        return List.copyOf(unique.values());
    }

    private Optional<Journey> parseRoute(JsonNode route, LocationCandidate origin,
                                         LocationCandidate destination, Instant requestedDeparture) {
        List<JsonNode> steps = new ArrayList<>();
        route.path("legs").forEach(leg -> leg.path("steps").forEach(steps::add));
        if (steps.isEmpty()) {
            return Optional.empty();
        }

        List<JourneyLeg> legs = new ArrayList<>();
        String currentName = origin.displayName();
        Instant cursor = requestedDeparture;
        int index = 0;
        while (index < steps.size()) {
            JsonNode step = steps.get(index);
            String travelMode = step.path("travelMode").asText("");

            if ("WALK".equals(travelMode)) {
                int end = index;
                long seconds = 0;
                double distance = 0;
                List<JourneyLeg.Waypoint> geometry = new ArrayList<>();
                while (end < steps.size() && "WALK".equals(
                        steps.get(end).path("travelMode").asText(""))) {
                    JsonNode walk = steps.get(end);
                    seconds = Math.addExact(seconds, durationSeconds(walk.path("staticDuration")));
                    distance += nonNegativeDistance(walk);
                    appendDistinct(geometry, geometry(walk,
                            locationWaypoint(walk.path("startLocation")),
                            locationWaypoint(walk.path("endLocation"))));
                    end++;
                }

                String endName = end < steps.size() && "TRANSIT".equals(
                        steps.get(end).path("travelMode").asText(""))
                        ? stopName(steps.get(end).path("transitDetails")
                                .path("stopDetails").path("departureStop"))
                        : destination.displayName();
                if (endName == null || endName.isBlank()) {
                    return Optional.empty();
                }
                labelEndpoints(geometry, currentName, endName);
                if (seconds > 0 || distance > 0 || geometry.size() > 1) {
                    legs.add(JourneyLeg.walk(currentName, endName, ceilMinutes(seconds), distance,
                            List.copyOf(geometry)));
                }
                cursor = cursor.plusSeconds(seconds);
                currentName = endName;
                index = end;
                continue;
            }

            if (!"TRANSIT".equals(travelMode)) {
                // FareFlow has no car/bike/other-routing mode to reinterpret this as.
                return Optional.empty();
            }

            JsonNode details = step.path("transitDetails");
            Optional<TransitMode> mode = transitMode(
                    details.path("transitLine").path("vehicle").path("type").asText(""));
            if (mode.isEmpty()) {
                return Optional.empty();
            }

            Stop departure = stop(details.path("stopDetails").path("departureStop"));
            Stop arrival = stop(details.path("stopDetails").path("arrivalStop"));
            int stopCount = details.path("stopCount").asInt(0);
            // Stop-based FareFlow sessions cannot honestly price a route whose
            // provider omitted the number of completed-stop boundaries.
            if (departure == null || arrival == null || stopCount <= 0) {
                return Optional.empty();
            }

            Instant departureTime = instant(details.path("stopDetails").path("departureTime"));
            Instant arrivalTime = instant(details.path("stopDetails").path("arrivalTime"));
            long waitSeconds = departureTime == null
                    ? 0 : Math.max(0, Duration.between(cursor, departureTime).toSeconds());
            long rideSeconds = departureTime != null && arrivalTime != null
                    && !arrivalTime.isBefore(departureTime)
                    ? Duration.between(departureTime, arrivalTime).toSeconds()
                    : durationSeconds(step.path("staticDuration"));

            JsonNode line = details.path("transitLine");
            String agency = joinedAgencyNames(line.path("agencies"));
            String baseLineName = firstNonBlank(
                    text(line, "nameShort"), text(line, "name"), text(details, "tripShortText"),
                    line.path("vehicle").path("name").path("text").asText(null),
                    displayMode(mode.get()));
            String headsign = text(details, "headsign");
            String lineName = headsign == null || headsign.isBlank()
                    ? baseLineName : baseLineName + " toward " + headsign;
            String lineCode = "GOOGLE:" + normalized(agency) + ":" + normalized(baseLineName);

            List<JourneyLeg.Waypoint> geometry = new ArrayList<>(geometry(
                    step, departure.waypoint(), arrival.waypoint()));
            geometry = new ArrayList<>(TransitStopGeometry.ensureStopBoundaries(
                    geometry, departure.name(), arrival.name(), baseLineName, stopCount));
            double distance = nonNegativeDistance(step);
            if (distance == 0) {
                distance = pathDistance(geometry);
            }

            legs.add(new JourneyLeg(mode.get(), agency, lineCode, lineName,
                    stopCode(departure), departure.name(), stopCode(arrival), arrival.name(),
                    ceilMinutes(rideSeconds), ceilMinutes(waitSeconds), distance,
                    List.copyOf(geometry), departureTime, arrivalTime, false, stopCount));
            cursor = arrivalTime != null ? arrivalTime : cursor.plusSeconds(waitSeconds + rideSeconds);
            currentName = arrival.name();
            index++;
        }

        if (legs.stream().noneMatch(leg -> leg.mode().isTransit())) {
            return Optional.empty();
        }

        Journey.ProviderFare fare = providerFare(route).orElse(null);
        String signature = signature(legs);
        return Optional.of(new Journey(signature, origin.displayName(), destination.displayName(),
                legs, Journey.DataSource.GOOGLE_ROUTES, fare));
    }

    private static Map<String, Object> waypoint(LocationCandidate candidate) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", candidate.latitude(), "longitude", candidate.longitude())));
    }

    private static Optional<Journey.ProviderFare> providerFare(JsonNode route) {
        JsonNode money = route.path("travelAdvisory").path("transitFare");
        String currency = money.path("currencyCode").asText("");
        if (!"USD".equalsIgnoreCase(currency) || money.isMissingNode()) {
            return Optional.empty();
        }
        try {
            BigDecimal units = new BigDecimal(money.path("units").asText("0"));
            BigDecimal nanos = BigDecimal.valueOf(money.path("nanos").asLong(0), 9);
            long cents = units.add(nanos).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            return cents < 0 ? Optional.empty()
                    : Optional.of(new Journey.ProviderFare(cents, "USD", "Google Maps"));
        } catch (ArithmeticException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<TransitMode> transitMode(String vehicleType) {
        return switch (vehicleType) {
            case "BUS", "INTERCITY_BUS", "TROLLEYBUS" -> Optional.of(TransitMode.BUS);
            case "SUBWAY" -> Optional.of(TransitMode.SUBWAY);
            case "TRAM", "METRO_RAIL" -> Optional.of(TransitMode.LIGHT_RAIL);
            case "COMMUTER_TRAIN", "HEAVY_RAIL", "HIGH_SPEED_TRAIN",
                    "LONG_DISTANCE_TRAIN", "RAIL", "MONORAIL" -> Optional.of(TransitMode.RAIL);
            case "FERRY" -> Optional.of(TransitMode.FERRY);
            default -> Optional.empty();
        };
    }

    private static Stop stop(JsonNode node) {
        String name = stopName(node);
        JsonNode latLng = node.path("location").path("latLng");
        if (name == null || name.isBlank() || !latLng.hasNonNull("latitude")
                || !latLng.hasNonNull("longitude")) {
            return null;
        }
        double latitude = latLng.path("latitude").asDouble(Double.NaN);
        double longitude = latLng.path("longitude").asDouble(Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return null;
        }
        return new Stop(name, latitude, longitude);
    }

    private static String stopName(JsonNode node) {
        return text(node, "name");
    }

    private static List<JourneyLeg.Waypoint> geometry(JsonNode step,
                                                      JourneyLeg.Waypoint fallbackStart,
                                                      JourneyLeg.Waypoint fallbackEnd) {
        List<JourneyLeg.Waypoint> points = new ArrayList<>();
        JsonNode coordinates = step.path("polyline").path("geoJsonLinestring").path("coordinates");
        if (coordinates.isArray()) {
            for (JsonNode coordinate : coordinates) {
                if (!coordinate.isArray() || coordinate.size() < 2) {
                    continue;
                }
                double longitude = coordinate.get(0).asDouble(Double.NaN);
                double latitude = coordinate.get(1).asDouble(Double.NaN);
                if (Double.isFinite(latitude) && Double.isFinite(longitude)
                        && latitude >= -90 && latitude <= 90
                        && longitude >= -180 && longitude <= 180) {
                    points.add(new JourneyLeg.Waypoint("", latitude, longitude));
                }
            }
        }
        if (points.isEmpty()) {
            if (fallbackStart != null) points.add(fallbackStart);
            if (fallbackEnd != null) appendDistinct(points, List.of(fallbackEnd));
        }
        return List.copyOf(points);
    }

    private static JourneyLeg.Waypoint locationWaypoint(JsonNode location) {
        JsonNode latLng = location.path("latLng");
        if (!latLng.hasNonNull("latitude") || !latLng.hasNonNull("longitude")) return null;
        double latitude = latLng.path("latitude").asDouble(Double.NaN);
        double longitude = latLng.path("longitude").asDouble(Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return null;
        }
        return new JourneyLeg.Waypoint("", latitude, longitude);
    }

    private static void appendDistinct(List<JourneyLeg.Waypoint> target,
                                       List<JourneyLeg.Waypoint> additions) {
        for (JourneyLeg.Waypoint point : additions) {
            if (target.isEmpty() || !sameCoordinate(target.getLast(), point)) {
                target.add(point);
            }
        }
    }

    private static void labelEndpoints(List<JourneyLeg.Waypoint> points,
                                       String startName, String endName) {
        if (points.isEmpty()) return;
        JourneyLeg.Waypoint first = points.getFirst();
        points.set(0, new JourneyLeg.Waypoint(startName, first.latitude(), first.longitude()));
        JourneyLeg.Waypoint last = points.getLast();
        points.set(points.size() - 1,
                new JourneyLeg.Waypoint(endName, last.latitude(), last.longitude()));
    }

    private static boolean sameCoordinate(JourneyLeg.Waypoint left, JourneyLeg.Waypoint right) {
        return Double.compare(left.latitude(), right.latitude()) == 0
                && Double.compare(left.longitude(), right.longitude()) == 0;
    }

    private static double nonNegativeDistance(JsonNode step) {
        return Math.max(0, step.path("distanceMeters").asDouble(0));
    }

    private static double pathDistance(List<JourneyLeg.Waypoint> points) {
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            JourneyLeg.Waypoint previous = points.get(index - 1);
            JourneyLeg.Waypoint current = points.get(index);
            distance += LocationCandidate.haversineMetres(previous.latitude(), previous.longitude(),
                    current.latitude(), current.longitude());
        }
        return distance;
    }

    private static long durationSeconds(JsonNode value) {
        String raw = value.asText("0s").trim();
        if (!raw.endsWith("s")) return 0;
        try {
            return Math.max(0, new BigDecimal(raw.substring(0, raw.length() - 1))
                    .setScale(0, RoundingMode.CEILING).longValueExact());
        } catch (ArithmeticException | NumberFormatException exception) {
            return 0;
        }
    }

    private static int ceilMinutes(long seconds) {
        return (int) Math.max(0, Math.ceil(seconds / 60.0));
    }

    private static Instant instant(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        try {
            return Instant.parse(value.asText());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String joinedAgencyNames(JsonNode agencies) {
        if (!agencies.isArray()) return null;
        List<String> names = new ArrayList<>();
        agencies.forEach(agency -> {
            String name = text(agency, "name");
            if (name != null && !name.isBlank() && !names.contains(name)) names.add(name);
        });
        return names.isEmpty() ? null : String.join(" + ", names);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.hasNonNull(field)) return null;
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        throw new IllegalArgumentException("Google transit step has no usable line name");
    }

    private static String displayMode(TransitMode mode) {
        return switch (mode) {
            case BUS -> "Bus";
            case SUBWAY -> "Subway";
            case LIGHT_RAIL -> "Light rail";
            case RAIL -> "Train";
            case FERRY -> "Ferry";
            case WALK -> "Walk";
        };
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String stopCode(Stop stop) {
        return "GOOGLE_STOP:" + normalized(stop.name()) + ":"
                + String.format(Locale.ROOT, "%.5f:%.5f", stop.latitude(), stop.longitude());
    }

    private static String signature(List<JourneyLeg> legs) {
        String facts = legs.stream().filter(leg -> leg.mode().isTransit())
                .map(leg -> String.join("|", leg.lineCode(), leg.fromStopCode(), leg.toStopCode()))
                .reduce((left, right) -> left + ">" + right).orElseThrow();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(facts.getBytes(StandardCharsets.UTF_8));
            return "GOOGLE:" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Stop(String name, double latitude, double longitude) {
        JourneyLeg.Waypoint waypoint() {
            return new JourneyLeg.Waypoint(name, latitude, longitude);
        }
    }
}
