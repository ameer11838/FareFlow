package com.fareflow.gtfs;

import com.fareflow.journey.TransitMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomically replaces one normalized GTFS Schedule dataset.
 *
 * <p>The archive is validated before existing data is touched. Unsupported modes
 * are counted and excluded; missing times are skipped rather than interpolated.
 */
@Service
public class GtfsScheduleImporter {

    private static final int BATCH_SIZE = 2_000;
    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;

    public GtfsScheduleImporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public GtfsImportResult importFeed(String feedKey, Path archivePath) throws IOException {
        Objects.requireNonNull(feedKey, "feedKey");
        Objects.requireNonNull(archivePath, "archivePath");
        Long feedId = jdbc.query(
                "SELECT id FROM gtfs_feeds WHERE feed_key = ? AND enabled = TRUE",
                result -> result.next() ? result.getLong(1) : null, feedKey);
        if (feedId == null) {
            throw new IllegalArgumentException("Unknown or disabled GTFS feed: " + feedKey);
        }

        try (GtfsArchive archive = new GtfsArchive(archivePath)) {
            archive.require("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt");

            List<AgencyRow> agencies = readAgencies(archive);
            if (agencies.isEmpty()) {
                throw new IllegalArgumentException("agency.txt contains no usable agencies");
            }
            agencies.forEach(agency -> ZoneId.of(agency.timezone()));
            String defaultAgency = agencies.size() == 1 ? agencies.getFirst().id() : null;

            Map<String, StopRow> stops = readStops(archive);
            if (stops.isEmpty()) {
                throw new IllegalArgumentException("stops.txt contains no usable located stops");
            }

            RouteImport routes = readRoutes(archive, defaultAgency, agencies);
            if (routes.rows().isEmpty()) {
                throw new IllegalArgumentException(
                        "routes.txt contains no supported Train, Subway, Bus, or Ferry routes");
            }

            ServiceImport services = readServices(archive);
            if (services.rows().isEmpty()) {
                throw new IllegalArgumentException("GTFS feed contains no service calendars");
            }

            Map<String, TripRow> trips = readTrips(archive, routes.ids(), services.ids());
            if (trips.isEmpty()) {
                throw new IllegalArgumentException("trips.txt contains no trips on supported routes");
            }

            jdbc.update("UPDATE gtfs_feeds SET status = 'IMPORTING', last_error = NULL WHERE id = ?", feedId);
            clearFeed(feedId);
            insertAgencies(feedId, agencies);
            insertStops(feedId, stops.values());
            insertRoutes(feedId, routes.rows());
            insertServices(feedId, services.rows());
            insertExceptions(feedId, services.exceptions());
            insertTrips(feedId, trips.values());
            int stopTimeCount = insertStopTimes(archive, feedId, trips.keySet(), stops.keySet());

            // A routable trip needs two explicit timepoints. Removing incomplete
            // trips is safer than inventing/interpolating times.
            jdbc.update("""
                    DELETE FROM gtfs_trips t
                    WHERE t.feed_id = ?
                      AND (SELECT COUNT(*) FROM gtfs_stop_times st
                           WHERE st.feed_id = t.feed_id AND st.trip_id = t.trip_id) < 2
                    """, feedId);
            int retainedTrips = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM gtfs_trips WHERE feed_id = ?", Integer.class, feedId);
            int retainedStopTimes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM gtfs_stop_times WHERE feed_id = ?", Integer.class, feedId);
            int transferCount = insertTransfers(archive, feedId, stops.keySet());
            String digest = sha256(archivePath);

            jdbc.update("""
                    UPDATE gtfs_feeds
                       SET status = 'READY', imported_at = CURRENT_TIMESTAMP,
                           agency_timezone = ?, feed_start_date = ?, feed_end_date = ?,
                           content_sha256 = ?, agency_count = ?, stop_count = ?,
                           route_count = ?, trip_count = ?, unsupported_route_count = ?,
                           last_error = NULL
                     WHERE id = ?
                    """,
                    agencies.getFirst().timezone(), sqlDate(services.start()), sqlDate(services.end()),
                    digest, agencies.size(), stops.size(), routes.rows().size(), retainedTrips,
                    routes.unsupported(), feedId);

            return new GtfsImportResult(feedKey, agencies.size(), stops.size(), routes.rows().size(),
                    retainedTrips, retainedStopTimes, transferCount, routes.unsupported(),
                    services.start(), services.end(), digest);
        }
    }

    private List<AgencyRow> readAgencies(GtfsArchive archive) throws IOException {
        List<AgencyRow> rows = new ArrayList<>();
        archive.rows("agency.txt", row -> {
            String name = required(row, "agency_name");
            String timezone = required(row, "agency_timezone");
            String id = value(row, "agency_id");
            rows.add(new AgencyRow(id.isBlank() ? "default" : id, name,
                    nullable(row, "agency_url"), timezone));
        });
        Set<String> ids = new HashSet<>();
        if (rows.stream().anyMatch(row -> !ids.add(row.id()))) {
            throw new IllegalArgumentException("agency.txt contains duplicate agency_id values");
        }
        return rows;
    }

    private Map<String, StopRow> readStops(GtfsArchive archive) throws IOException {
        Map<String, RawStop> raw = new LinkedHashMap<>();
        archive.rows("stops.txt", row -> {
            String id = required(row, "stop_id");
            raw.put(id, new RawStop(id, nullable(row, "stop_code"), required(row, "stop_name"),
                    optionalDouble(row, "stop_lat"), optionalDouble(row, "stop_lon"),
                    optionalInt(row, "location_type", 0), nullable(row, "parent_station"),
                    nullable(row, "platform_code"), optionalInteger(row, "wheelchair_boarding")));
        });

        Map<String, StopRow> located = new LinkedHashMap<>();
        for (RawStop stop : raw.values()) {
            Coordinates coordinates = resolveCoordinates(stop, raw, new HashSet<>());
            if (coordinates != null && stop.locationType() >= 0 && stop.locationType() <= 4) {
                located.put(stop.id(), new StopRow(stop.id(), stop.code(), stop.name(),
                        coordinates.latitude(), coordinates.longitude(), stop.locationType(),
                        stop.parent(), stop.platform(), stop.wheelchair()));
            }
        }
        return located;
    }

    private Coordinates resolveCoordinates(RawStop stop, Map<String, RawStop> raw, Set<String> seen) {
        if (stop.latitude() != null && stop.longitude() != null) {
            validateCoordinates(stop.latitude(), stop.longitude());
            return new Coordinates(stop.latitude(), stop.longitude());
        }
        if (stop.parent() == null || !seen.add(stop.id())) {
            return null;
        }
        RawStop parent = raw.get(stop.parent());
        return parent == null ? null : resolveCoordinates(parent, raw, seen);
    }

    private RouteImport readRoutes(GtfsArchive archive, String defaultAgency,
                                   List<AgencyRow> agencies) throws IOException {
        Set<String> agencyIds = agencies.stream().map(AgencyRow::id).collect(java.util.stream.Collectors.toSet());
        List<RouteRow> rows = new ArrayList<>();
        int[] unsupported = {0};
        archive.rows("routes.txt", row -> {
            int type = integer(row, "route_type");
            var mode = GtfsTransitMode.fromRouteType(type);
            if (mode.isEmpty()) {
                unsupported[0]++;
                return;
            }
            String agency = value(row, "agency_id");
            if (agency.isBlank()) {
                if (defaultAgency == null) {
                    throw new IllegalArgumentException("route agency_id is required in a multi-agency feed");
                }
                agency = defaultAgency;
            }
            if (!agencyIds.contains(agency)) {
                throw new IllegalArgumentException("Route references unknown agency_id " + agency);
            }
            rows.add(new RouteRow(required(row, "route_id"), agency,
                    nullable(row, "route_short_name"), nullable(row, "route_long_name"), type,
                    mode.orElseThrow(), nullable(row, "route_color"), nullable(row, "route_text_color")));
        });
        Set<String> ids = rows.stream().map(RouteRow::id).collect(java.util.stream.Collectors.toSet());
        return new RouteImport(rows, ids, unsupported[0]);
    }

    private ServiceImport readServices(GtfsArchive archive) throws IOException {
        Map<String, ServiceRow> services = new LinkedHashMap<>();
        if (archive.has("calendar.txt")) {
            archive.rows("calendar.txt", row -> {
                ServiceRow service = new ServiceRow(required(row, "service_id"),
                        bool(row, "monday"), bool(row, "tuesday"), bool(row, "wednesday"),
                        bool(row, "thursday"), bool(row, "friday"), bool(row, "saturday"),
                        bool(row, "sunday"), date(row, "start_date"), date(row, "end_date"));
                services.put(service.id(), service);
            });
        }

        List<ExceptionRow> exceptions = new ArrayList<>();
        if (archive.has("calendar_dates.txt")) {
            archive.rows("calendar_dates.txt", row -> {
                String serviceId = required(row, "service_id");
                LocalDate serviceDate = date(row, "date");
                int type = integer(row, "exception_type");
                if (type != 1 && type != 2) {
                    throw new IllegalArgumentException("Invalid calendar exception_type " + type);
                }
                exceptions.add(new ExceptionRow(serviceId, serviceDate, type));
                services.putIfAbsent(serviceId, ServiceRow.exceptionsOnly(serviceId));
            });
        }

        LocalDate start = services.values().stream().map(ServiceRow::start).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate end = services.values().stream().map(ServiceRow::end).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        for (ExceptionRow exception : exceptions) {
            start = start == null || exception.date().isBefore(start) ? exception.date() : start;
            end = end == null || exception.date().isAfter(end) ? exception.date() : end;
        }
        return new ServiceImport(new ArrayList<>(services.values()), services.keySet(), exceptions, start, end);
    }

    private Map<String, TripRow> readTrips(GtfsArchive archive, Set<String> routes,
                                           Set<String> services) throws IOException {
        Map<String, TripRow> trips = new LinkedHashMap<>();
        archive.rows("trips.txt", row -> {
            String route = required(row, "route_id");
            if (!routes.contains(route)) {
                return;
            }
            String service = required(row, "service_id");
            if (!services.contains(service)) {
                throw new IllegalArgumentException("Trip references unknown service_id " + service);
            }
            TripRow trip = new TripRow(required(row, "trip_id"), route, service,
                    nullable(row, "trip_headsign"), optionalInteger(row, "direction_id"),
                    nullable(row, "shape_id"), optionalInteger(row, "wheelchair_accessible"));
            trips.put(trip.id(), trip);
        });
        return trips;
    }

    private int insertStopTimes(GtfsArchive archive, long feedId, Set<String> trips,
                                Set<String> stops) throws IOException {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int[] inserted = {0};
        archive.rows("stop_times.txt", row -> {
            String trip = required(row, "trip_id");
            String stop = required(row, "stop_id");
            if (!trips.contains(trip) || !stops.contains(stop)) {
                return;
            }
            String arrivalText = value(row, "arrival_time");
            String departureText = value(row, "departure_time");
            if (arrivalText.isBlank() && departureText.isBlank()) {
                return;
            }
            int arrival = parseTime(arrivalText.isBlank() ? departureText : arrivalText);
            int departure = parseTime(departureText.isBlank() ? arrivalText : departureText);
            if (departure < arrival) {
                throw new IllegalArgumentException("Departure precedes arrival for trip " + trip);
            }
            batch.add(new Object[]{feedId, trip, stop, integer(row, "stop_sequence"), arrival,
                    departure, optionalInt(row, "pickup_type", 0),
                    optionalInt(row, "drop_off_type", 0), optionalInteger(row, "timepoint")});
            if (batch.size() == BATCH_SIZE) {
                insertStopTimeBatch(batch);
                inserted[0] += batch.size();
                batch.clear();
            }
        });
        insertStopTimeBatch(batch);
        inserted[0] += batch.size();
        return inserted[0];
    }

    private int insertTransfers(GtfsArchive archive, long feedId, Set<String> stops) throws IOException {
        if (!archive.has("transfers.txt")) {
            return 0;
        }
        List<Object[]> batch = new ArrayList<>();
        archive.rows("transfers.txt", row -> {
            String from = required(row, "from_stop_id");
            String to = required(row, "to_stop_id");
            if (stops.contains(from) && stops.contains(to)) {
                batch.add(new Object[]{feedId, from, to, optionalInt(row, "transfer_type", 0),
                        optionalInteger(row, "min_transfer_time"), nullable(row, "from_route_id"),
                        nullable(row, "to_route_id"), nullable(row, "from_trip_id"),
                        nullable(row, "to_trip_id")});
            }
        });
        jdbc.batchUpdate("""
                INSERT INTO gtfs_transfers
                    (feed_id, from_stop_id, to_stop_id, transfer_type, min_transfer_seconds,
                     from_route_id, to_route_id, from_trip_id, to_trip_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
        return batch.size();
    }

    private void clearFeed(long feedId) {
        jdbc.update("DELETE FROM gtfs_realtime_stop_updates WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_realtime_trip_status WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_transfers WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_stop_times WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_trips WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_service_exceptions WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_services WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_routes WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_stops WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_agencies WHERE feed_id = ?", feedId);
    }

    private void insertAgencies(long feedId, List<AgencyRow> rows) {
        jdbc.batchUpdate("INSERT INTO gtfs_agencies VALUES (?, ?, ?, ?, ?)", rows.stream()
                .map(row -> new Object[]{feedId, row.id(), row.name(), row.url(), row.timezone()}).toList());
    }

    private void insertStops(long feedId, java.util.Collection<StopRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO gtfs_stops
                    (feed_id, stop_id, stop_code, stop_name, stop_latitude, stop_longitude,
                     location_type, parent_station_id, platform_code, wheelchair_boarding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows.stream().map(row -> new Object[]{feedId, row.id(), row.code(), row.name(),
                row.latitude(), row.longitude(), row.locationType(), row.parent(), row.platform(),
                row.wheelchair()}).toList());
    }

    private void insertRoutes(long feedId, List<RouteRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO gtfs_routes
                    (feed_id, route_id, agency_id, route_short_name, route_long_name,
                     route_type, transit_mode, route_color, route_text_color)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows.stream().map(row -> new Object[]{feedId, row.id(), row.agency(), row.shortName(),
                row.longName(), row.type(), row.mode().name(), row.color(), row.textColor()}).toList());
    }

    private void insertServices(long feedId, List<ServiceRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO gtfs_services
                    (feed_id, service_id, monday, tuesday, wednesday, thursday, friday,
                     saturday, sunday, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows.stream().map(row -> new Object[]{feedId, row.id(), row.monday(), row.tuesday(),
                row.wednesday(), row.thursday(), row.friday(), row.saturday(), row.sunday(),
                sqlDate(row.start()), sqlDate(row.end())}).toList());
    }

    private void insertExceptions(long feedId, List<ExceptionRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO gtfs_service_exceptions (feed_id, service_id, service_date, exception_type)
                VALUES (?, ?, ?, ?)
                """, rows.stream().map(row -> new Object[]{feedId, row.serviceId(), Date.valueOf(row.date()),
                row.type()}).toList());
    }

    private void insertTrips(long feedId, java.util.Collection<TripRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO gtfs_trips
                    (feed_id, trip_id, route_id, service_id, trip_headsign, direction_id,
                     shape_id, wheelchair_accessible)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, rows.stream().map(row -> new Object[]{feedId, row.id(), row.route(), row.service(),
                row.headsign(), row.direction(), row.shape(), row.wheelchair()}).toList());
    }

    private void insertStopTimeBatch(List<Object[]> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO gtfs_stop_times
                        (feed_id, trip_id, stop_id, stop_sequence, arrival_seconds,
                         departure_seconds, pickup_type, drop_off_type, timepoint)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, rows);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16_384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }

    private static int parseTime(String value) {
        String[] pieces = value.split(":", -1);
        if (pieces.length != 3) {
            throw new IllegalArgumentException("Invalid GTFS time: " + value);
        }
        try {
            int hours = Integer.parseInt(pieces[0]);
            int minutes = Integer.parseInt(pieces[1]);
            int seconds = Integer.parseInt(pieces[2]);
            if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
                throw new IllegalArgumentException("Invalid GTFS time: " + value);
            }
            return hours * 3_600 + minutes * 60 + seconds;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid GTFS time: " + value, exception);
        }
    }

    private static LocalDate date(Map<String, String> row, String column) {
        String value = required(row, column);
        try {
            return LocalDate.parse(value, GTFS_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid GTFS date in " + column + ": " + value, exception);
        }
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static boolean bool(Map<String, String> row, String column) {
        return integer(row, column) == 1;
    }

    private static int integer(Map<String, String> row, String column) {
        String value = required(row, column);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer in " + column + ": " + value, exception);
        }
    }

    private static int optionalInt(Map<String, String> row, String column, int fallback) {
        Integer value = optionalInteger(row, column);
        return value == null ? fallback : value;
    }

    private static Integer optionalInteger(Map<String, String> row, String column) {
        String value = value(row, column);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer in " + column + ": " + value, exception);
        }
    }

    private static Double optionalDouble(Map<String, String> row, String column) {
        String value = value(row, column);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal in " + column + ": " + value, exception);
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("GTFS stop coordinates are outside the valid range");
        }
    }

    private static String required(Map<String, String> row, String column) {
        String value = value(row, column);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Required GTFS field is blank: " + column);
        }
        return value;
    }

    private static String value(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").trim();
    }

    private static String nullable(Map<String, String> row, String column) {
        String value = value(row, column);
        return value.isBlank() ? null : value;
    }

    private record AgencyRow(String id, String name, String url, String timezone) {
    }

    private record RawStop(String id, String code, String name, Double latitude, Double longitude,
                           int locationType, String parent, String platform, Integer wheelchair) {
    }

    private record StopRow(String id, String code, String name, double latitude, double longitude,
                           int locationType, String parent, String platform, Integer wheelchair) {
    }

    private record Coordinates(double latitude, double longitude) {
    }

    private record RouteRow(String id, String agency, String shortName, String longName, int type,
                            TransitMode mode, String color, String textColor) {
    }

    private record RouteImport(List<RouteRow> rows, Set<String> ids, int unsupported) {
    }

    private record ServiceRow(String id, boolean monday, boolean tuesday, boolean wednesday,
                              boolean thursday, boolean friday, boolean saturday, boolean sunday,
                              LocalDate start, LocalDate end) {
        static ServiceRow exceptionsOnly(String id) {
            return new ServiceRow(id, false, false, false, false, false, false, false, null, null);
        }
    }

    private record ExceptionRow(String serviceId, LocalDate date, int type) {
    }

    private record ServiceImport(List<ServiceRow> rows, Set<String> ids,
                                 List<ExceptionRow> exceptions, LocalDate start, LocalDate end) {
    }

    private record TripRow(String id, String route, String service, String headsign,
                           Integer direction, String shape, Integer wheelchair) {
    }
}
