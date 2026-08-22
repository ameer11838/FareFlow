package com.fareflow.gtfs;

import com.fareflow.journey.TransitMode;
import com.fareflow.location.LocationCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Read model used by the timetable router. All identifiers remain feed-scoped. */
@Repository
public class GtfsScheduleRepository {

    private final JdbcTemplate jdbc;

    public GtfsScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Stop> stopsNear(double latitude, double longitude, double radiusMetres, int limit) {
        double latitudeRange = radiusMetres / 111_320.0;
        double longitudeRange = radiusMetres
                / Math.max(1.0, 111_320.0 * Math.cos(Math.toRadians(latitude)));
        return jdbc.query("""
                SELECT f.id, f.feed_key, f.agency_timezone, s.stop_id, s.stop_name,
                       s.stop_latitude, s.stop_longitude,
                       f.realtime_expires_at > CURRENT_TIMESTAMP AS realtime_fresh
                  FROM gtfs_stops s
                  JOIN gtfs_feeds f ON f.id = s.feed_id
                 WHERE f.status = 'READY' AND f.enabled = TRUE
                   AND s.location_type IN (0, 1)
                   AND s.stop_latitude BETWEEN ? AND ?
                   AND s.stop_longitude BETWEEN ? AND ?
                """, (rs, row) -> stop(rs), latitude - latitudeRange, latitude + latitudeRange,
                longitude - longitudeRange, longitude + longitudeRange).stream()
                .filter(stop -> LocationCandidate.haversineMetres(latitude, longitude,
                        stop.latitude(), stop.longitude()) <= radiusMetres)
                .sorted(java.util.Comparator.comparingDouble(stop ->
                        LocationCandidate.haversineMetres(latitude, longitude,
                                stop.latitude(), stop.longitude())))
                .limit(limit)
                .toList();
    }

    /**
     * Searches only stops from successfully imported, currently enabled feeds.
     *
     * <p>This is deliberately separate from web geocoding. A geocoder can tell us
     * that a place called "Union Station" exists; this query proves that FareFlow
     * has an actual GTFS stop identity and published service data for it.
     */
    public List<StopLocation> searchStops(String query, String regionHint, int limit) {
        String contains = "%" + escapeLike(query.trim().toLowerCase(java.util.Locale.ROOT)) + "%";
        String prefix = escapeLike(query.trim().toLowerCase(java.util.Locale.ROOT)) + "%";
        String region = regionHint == null || regionHint.isBlank()
                ? null : "%" + escapeLike(regionHint.trim().toLowerCase(java.util.Locale.ROOT)) + "%";

        return jdbc.query("""
                WITH matches AS (
                    SELECT f.id AS feed_id, f.feed_key, f.region_code, f.region_name,
                           f.publisher_name, f.realtime_expires_at > CURRENT_TIMESTAMP AS realtime_fresh,
                           s.stop_id, s.stop_name, s.stop_latitude, s.stop_longitude,
                           s.location_type,
                           CASE WHEN LOWER(s.stop_name) = ? THEN 0
                                WHEN LOWER(s.stop_name) LIKE ? ESCAPE '\\' THEN 1 ELSE 2 END AS match_rank
                      FROM gtfs_stops s
                      JOIN gtfs_feeds f ON f.id = s.feed_id
                     WHERE f.status = 'READY' AND f.enabled = TRUE
                       AND s.location_type IN (0, 1)
                       AND LOWER(s.stop_name) LIKE ? ESCAPE '\\'
                       AND (CAST(? AS TEXT) IS NULL OR LOWER(f.region_name) LIKE ? ESCAPE '\\'
                            OR LOWER(f.region_code) LIKE ? ESCAPE '\\')
                     ORDER BY match_rank, s.location_type DESC, s.stop_name, f.region_name
                     LIMIT ?
                )
                SELECT m.*,
                       COALESCE((SELECT STRING_AGG(DISTINCT r.transit_mode, ',' ORDER BY r.transit_mode)
                                   FROM gtfs_stop_times st
                                   JOIN gtfs_trips t ON t.feed_id = st.feed_id AND t.trip_id = st.trip_id
                                   JOIN gtfs_routes r ON r.feed_id = t.feed_id AND r.route_id = t.route_id
                                  WHERE st.feed_id = m.feed_id AND st.stop_id = m.stop_id), '') AS modes,
                       COALESCE((SELECT STRING_AGG(DISTINCT a.agency_name, '|' ORDER BY a.agency_name)
                                   FROM gtfs_stop_times st
                                   JOIN gtfs_trips t ON t.feed_id = st.feed_id AND t.trip_id = st.trip_id
                                   JOIN gtfs_routes r ON r.feed_id = t.feed_id AND r.route_id = t.route_id
                                   JOIN gtfs_agencies a ON a.feed_id = r.feed_id AND a.agency_id = r.agency_id
                                  WHERE st.feed_id = m.feed_id AND st.stop_id = m.stop_id), '') AS operators,
                       COALESCE((SELECT STRING_AGG(DISTINCT COALESCE(NULLIF(r.route_short_name, ''), r.route_long_name),
                                                   '|' ORDER BY COALESCE(NULLIF(r.route_short_name, ''), r.route_long_name))
                                   FROM gtfs_stop_times st
                                   JOIN gtfs_trips t ON t.feed_id = st.feed_id AND t.trip_id = st.trip_id
                                   JOIN gtfs_routes r ON r.feed_id = t.feed_id AND r.route_id = t.route_id
                                  WHERE st.feed_id = m.feed_id AND st.stop_id = m.stop_id), '') AS lines
                  FROM matches m
                 ORDER BY m.match_rank, m.location_type DESC, m.stop_name, m.region_name
                """, (rs, row) -> stopLocation(rs), query.trim().toLowerCase(java.util.Locale.ROOT),
                prefix, contains, region, region, region, Math.max(limit * 4, 20));
    }

    /** Enriches a nearby routing stop with only facts present in imported GTFS. */
    public StopLocation describeStop(Stop stop) {
        List<StopLocation> rows = jdbc.query("""
                SELECT f.id AS feed_id, f.feed_key, f.region_code, f.region_name,
                       f.publisher_name, f.realtime_expires_at > CURRENT_TIMESTAMP AS realtime_fresh,
                       s.stop_id, s.stop_name, s.stop_latitude, s.stop_longitude,
                       s.location_type, 0 AS match_rank,
                       COALESCE(STRING_AGG(DISTINCT r.transit_mode, ',' ORDER BY r.transit_mode)
                                FILTER (WHERE r.transit_mode IS NOT NULL), '') AS modes,
                       COALESCE(STRING_AGG(DISTINCT a.agency_name, '|' ORDER BY a.agency_name)
                                FILTER (WHERE a.agency_name IS NOT NULL), '') AS operators,
                       COALESCE(STRING_AGG(DISTINCT COALESCE(NULLIF(r.route_short_name, ''), r.route_long_name),
                                           '|' ORDER BY COALESCE(NULLIF(r.route_short_name, ''), r.route_long_name))
                                FILTER (WHERE r.route_id IS NOT NULL), '') AS lines
                  FROM gtfs_stops s
                  JOIN gtfs_feeds f ON f.id = s.feed_id
                  LEFT JOIN gtfs_stop_times st ON st.feed_id = s.feed_id AND st.stop_id = s.stop_id
                  LEFT JOIN gtfs_trips t ON t.feed_id = st.feed_id AND t.trip_id = st.trip_id
                  LEFT JOIN gtfs_routes r ON r.feed_id = t.feed_id AND r.route_id = t.route_id
                  LEFT JOIN gtfs_agencies a ON a.feed_id = r.feed_id AND a.agency_id = r.agency_id
                 WHERE s.feed_id = ? AND s.stop_id = ? AND f.status = 'READY' AND f.enabled = TRUE
                 GROUP BY f.id, f.feed_key, f.region_code, f.region_name, f.publisher_name,
                          f.realtime_expires_at, s.stop_id, s.stop_name, s.stop_latitude,
                          s.stop_longitude, s.location_type
                """, (rs, row) -> stopLocation(rs), stop.feedId(), stop.stopId());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Boarding> boardings(Stop stop, Instant earliest, Instant latest, int limit) {
        ZoneId zone = ZoneId.of(stop.timezone());
        LocalDate localDate = earliest.atZone(zone).toLocalDate();
        List<Boarding> candidates = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate serviceDate = localDate.plusDays(offset);
            List<String> services = activeServices(stop.feedId(), serviceDate);
            if (services.isEmpty()) {
                continue;
            }
            ZonedDateTime serviceStart = serviceDate.atStartOfDay(zone);
            long lower = Duration.between(serviceStart.toInstant(), earliest).getSeconds();
            long upper = Duration.between(serviceStart.toInstant(), latest).getSeconds();
            if (upper < 0) {
                continue;
            }
            candidates.addAll(boardingsForServiceDay(stop, serviceDate,
                    Math.max(0, lower), upper, services, Math.max(limit * 3, 30)));
        }
        return candidates.stream()
                .filter(boarding -> !boarding.cancelled())
                .filter(boarding -> !boarding.departure().isBefore(earliest)
                        && !boarding.departure().isAfter(latest))
                .sorted(java.util.Comparator.comparing(Boarding::departure))
                .limit(limit)
                .toList();
    }

    public List<TripStop> remainingTripStops(Boarding boarding) {
        if (boarding.cancelled()) {
            return List.of();
        }
        RealtimeIndex realtime = boarding.realtimeFeedFresh()
                ? realtimeStops(boarding) : RealtimeIndex.empty();
        Integer[] propagatedDelay = {null};
        return jdbc.query("""
                SELECT st.stop_sequence, st.stop_id, s.stop_name, s.stop_latitude,
                       s.stop_longitude, st.arrival_seconds, st.departure_seconds,
                       st.pickup_type, st.drop_off_type
                  FROM gtfs_stop_times st
                  JOIN gtfs_stops s ON s.feed_id = st.feed_id AND s.stop_id = st.stop_id
                 WHERE st.feed_id = ? AND st.trip_id = ? AND st.stop_sequence >= ?
                 ORDER BY st.stop_sequence
                """, (rs, row) -> {
            int sequence = rs.getInt("stop_sequence");
            Instant scheduledArrival = scheduled(boarding, rs.getInt("arrival_seconds"));
            Instant scheduledDeparture = scheduled(boarding, rs.getInt("departure_seconds"));
            String stopId = rs.getString("stop_id");
            RealtimeStop live = realtime.bySequence().get(sequence);
            if (live == null) {
                live = realtime.byStopId().get(stopId);
            }
            if (live != null && "NO_DATA".equals(live.relationship())) {
                propagatedDelay[0] = null;
                live = null;
            }
            Instant arrival = adjusted(scheduledArrival, live == null ? null : live.arrivalTime(),
                    live == null ? propagatedDelay[0] : live.arrivalDelay());
            Instant departure = adjusted(scheduledDeparture, live == null ? null : live.departureTime(),
                    live == null ? propagatedDelay[0] : live.departureDelay());
            if (live != null) {
                Integer explicitDelay = live.departureDelay() != null
                        ? live.departureDelay() : live.arrivalDelay();
                if (explicitDelay == null && live.departureTime() != null) {
                    explicitDelay = Math.toIntExact(Duration.between(
                            scheduledDeparture, live.departureTime()).getSeconds());
                }
                if (explicitDelay == null && live.arrivalTime() != null) {
                    explicitDelay = Math.toIntExact(Duration.between(
                            scheduledArrival, live.arrivalTime()).getSeconds());
                }
                if (explicitDelay != null) {
                    propagatedDelay[0] = explicitDelay;
                }
            }
            boolean skipped = live != null && "SKIPPED".equals(live.relationship());
            boolean hasLiveFact = live != null || propagatedDelay[0] != null;
            return new TripStop(new Stop(boarding.feedId(), boarding.feedKey(), boarding.timezone(),
                    stopId, rs.getString("stop_name"),
                    rs.getDouble("stop_latitude"), rs.getDouble("stop_longitude"),
                    boarding.realtimeFeedFresh()),
                    sequence, arrival, departure, skipped ? 1 : rs.getInt("pickup_type"),
                    skipped ? 1 : rs.getInt("drop_off_type"), hasLiveFact,
                    live == null ? "SCHEDULED" : live.relationship());
        }, boarding.feedId(), boarding.tripId(), boarding.stopSequence());
    }

    public List<Transfer> transfersFrom(Stop stop) {
        List<Transfer> transfers = new ArrayList<>(jdbc.query("""
                SELECT f.id, f.feed_key, f.agency_timezone, s.stop_id, s.stop_name,
                       s.stop_latitude, s.stop_longitude,
                       f.realtime_expires_at > CURRENT_TIMESTAMP AS realtime_fresh,
                       COALESCE(t.min_transfer_seconds, 120) AS transfer_seconds
                  FROM gtfs_transfers t
                  JOIN gtfs_stops s ON s.feed_id = t.feed_id AND s.stop_id = t.to_stop_id
                  JOIN gtfs_feeds f ON f.id = s.feed_id
                 WHERE t.feed_id = ? AND t.from_stop_id = ? AND t.transfer_type IN (0, 1, 2)
                """, (rs, row) -> new Transfer(stop(rs), rs.getInt("transfer_seconds"), true),
                stop.feedId(), stop.stopId()));
        transfers.addAll(jdbc.query("""
                SELECT f.id, f.feed_key, f.agency_timezone, s.stop_id, s.stop_name,
                       s.stop_latitude, s.stop_longitude,
                       f.realtime_expires_at > CURRENT_TIMESTAMP AS realtime_fresh,
                       x.min_transfer_seconds AS transfer_seconds
                  FROM gtfs_inter_feed_transfers x
                  JOIN gtfs_stops s ON s.feed_id = x.to_feed_id AND s.stop_id = x.to_stop_id
                  JOIN gtfs_feeds f ON f.id = s.feed_id AND f.status = 'READY'
                 WHERE x.from_feed_id = ? AND x.from_stop_id = ?
                """, (rs, row) -> new Transfer(stop(rs), rs.getInt("transfer_seconds"), true),
                stop.feedId(), stop.stopId()));

        // Conservative cross-feed inference: official stop names must match and
        // coordinates must be within 150m. This creates only a walking link and
        // never invents a schedule.
        String normalizedName = normalize(stop.name());
        stopsNear(stop.latitude(), stop.longitude(), 150, 20).stream()
                .filter(candidate -> candidate.feedId() != stop.feedId())
                .filter(candidate -> normalize(candidate.name()).equals(normalizedName))
                .filter(candidate -> transfers.stream().noneMatch(existing ->
                        existing.to().key().equals(candidate.key())))
                .forEach(candidate -> {
                    double metres = LocationCandidate.haversineMetres(stop.latitude(), stop.longitude(),
                            candidate.latitude(), candidate.longitude());
                    int seconds = Math.max(120, (int) Math.ceil(metres / 1.3));
                    transfers.add(new Transfer(candidate, seconds, false));
                });
        return transfers;
    }

    private List<String> activeServices(long feedId, LocalDate date) {
        String weekday = switch (date.getDayOfWeek()) {
            case MONDAY -> "monday";
            case TUESDAY -> "tuesday";
            case WEDNESDAY -> "wednesday";
            case THURSDAY -> "thursday";
            case FRIDAY -> "friday";
            case SATURDAY -> "saturday";
            case SUNDAY -> "sunday";
        };
        return jdbc.queryForList("""
                SELECT s.service_id
                  FROM gtfs_services s
                 WHERE s.feed_id = ?
                   AND (((s.start_date IS NOT NULL AND ? BETWEEN s.start_date AND s.end_date
                           AND s.%s = TRUE)
                         OR EXISTS (SELECT 1 FROM gtfs_service_exceptions e
                                     WHERE e.feed_id = s.feed_id AND e.service_id = s.service_id
                                       AND e.service_date = ? AND e.exception_type = 1))
                        AND NOT EXISTS (SELECT 1 FROM gtfs_service_exceptions e
                                        WHERE e.feed_id = s.feed_id AND e.service_id = s.service_id
                                          AND e.service_date = ? AND e.exception_type = 2))
                """.formatted(weekday), String.class, feedId, Date.valueOf(date),
                Date.valueOf(date), Date.valueOf(date));
    }

    private List<Boarding> boardingsForServiceDay(Stop stop, LocalDate serviceDate,
                                                   long lowerSeconds, long upperSeconds,
                                                   List<String> services, int limit) {
        String placeholders = String.join(",", java.util.Collections.nCopies(services.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(stop.feedId());
        parameters.add(stop.stopId());
        parameters.add(lowerSeconds);
        parameters.add(upperSeconds);
        parameters.addAll(services);
        parameters.add(limit);
        Map<String, TripStatus> statuses = stop.realtimeFresh()
                ? realtimeTripStatuses(stop.feedId(), serviceDate) : Map.of();
        return jdbc.query("""
                SELECT st.trip_id, st.stop_sequence, st.departure_seconds,
                       t.trip_headsign, r.route_id, r.route_short_name, r.route_long_name,
                       r.transit_mode, a.agency_name
                  FROM gtfs_stop_times st
                  JOIN gtfs_trips t ON t.feed_id = st.feed_id AND t.trip_id = st.trip_id
                  JOIN gtfs_routes r ON r.feed_id = t.feed_id AND r.route_id = t.route_id
                  JOIN gtfs_agencies a ON a.feed_id = r.feed_id AND a.agency_id = r.agency_id
                 WHERE st.feed_id = ? AND st.stop_id = ? AND st.pickup_type = 0
                   AND st.departure_seconds BETWEEN ? AND ?
                   AND t.service_id IN (%s)
                 ORDER BY st.departure_seconds
                 LIMIT ?
                """.formatted(placeholders), (rs, row) -> {
            String tripId = rs.getString("trip_id");
            int sequence = rs.getInt("stop_sequence");
            Instant scheduled = serviceDate.atStartOfDay(ZoneId.of(stop.timezone()))
                    .plusSeconds(rs.getInt("departure_seconds")).toInstant();
            RealtimeStop update = stop.realtimeFresh()
                    ? realtimeStop(stop.feedId(), tripId, serviceDate, sequence, stop.stopId())
                    : null;
            if (update != null && "NO_DATA".equals(update.relationship())) {
                update = null;
            }
            Instant departure = adjusted(scheduled,
                    update == null ? null : update.departureTime(),
                    update == null ? null : update.departureDelay());
            TripStatus status = statuses.get(tripId);
            return new Boarding(stop.feedId(), stop.feedKey(), stop.timezone(), tripId, serviceDate,
                    sequence, rs.getString("route_id"), rs.getString("route_short_name"),
                    rs.getString("route_long_name"), rs.getString("trip_headsign"),
                    rs.getString("agency_name"), TransitMode.valueOf(rs.getString("transit_mode")),
                    scheduled, departure, update != null,
                    status != null && "CANCELED".equals(status.relationship()),
                    stop.realtimeFresh());
        }, parameters.toArray());
    }

    private Map<String, TripStatus> realtimeTripStatuses(long feedId, LocalDate date) {
        Map<String, TripStatus> statuses = new HashMap<>();
        jdbc.query("""
                SELECT trip_id, schedule_relationship
                  FROM gtfs_realtime_trip_status
                 WHERE feed_id = ? AND (start_date = ? OR start_date IS NULL)
                   AND expires_at > CURRENT_TIMESTAMP
                """, (RowCallbackHandler) rs ->
                        statuses.put(rs.getString(1), new TripStatus(rs.getString(2))),
                feedId, Date.valueOf(date));
        return statuses;
    }

    private RealtimeStop realtimeStop(long feedId, String tripId, LocalDate date, int sequence,
                                      String stopId) {
        List<RealtimeStop> rows = jdbc.query("""
                SELECT arrival_delay_seconds, departure_delay_seconds, arrival_time,
                       departure_time, schedule_relationship
                  FROM gtfs_realtime_stop_updates
                 WHERE feed_id = ? AND trip_id = ?
                   AND (start_date = ? OR start_date IS NULL)
                   AND (stop_sequence = ? OR (stop_sequence IS NULL AND stop_id = ?))
                   AND expires_at > CURRENT_TIMESTAMP
                 ORDER BY observed_at DESC LIMIT 1
                """, (rs, row) -> realtime(rs), feedId, tripId, Date.valueOf(date), sequence, stopId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private RealtimeIndex realtimeStops(Boarding boarding) {
        Map<Integer, RealtimeStop> bySequence = new HashMap<>();
        Map<String, RealtimeStop> byStopId = new HashMap<>();
        jdbc.query("""
                SELECT stop_sequence, stop_id, arrival_delay_seconds, departure_delay_seconds,
                       arrival_time, departure_time, schedule_relationship
                  FROM gtfs_realtime_stop_updates
                 WHERE feed_id = ? AND trip_id = ?
                   AND (start_date = ? OR start_date IS NULL)
                   AND expires_at > CURRENT_TIMESTAMP
                 ORDER BY observed_at
                """, (RowCallbackHandler) rs -> {
                    RealtimeStop update = realtime(rs);
                    Integer sequence = (Integer) rs.getObject("stop_sequence");
                    if (sequence != null) {
                        bySequence.put(sequence, update);
                    }
                    String stopId = rs.getString("stop_id");
                    if (stopId != null) {
                        byStopId.put(stopId, update);
                    }
                },
                boarding.feedId(), boarding.tripId(), Date.valueOf(boarding.serviceDate()));
        return new RealtimeIndex(bySequence, byStopId);
    }

    private Instant scheduled(Boarding boarding, int seconds) {
        return boarding.serviceDate().atStartOfDay(ZoneId.of(boarding.timezone()))
                .plusSeconds(seconds).toInstant();
    }

    private static Instant adjusted(Instant scheduled, Instant absolute, Integer delay) {
        if (absolute != null) {
            return absolute;
        }
        return delay == null ? scheduled : scheduled.plusSeconds(delay);
    }

    private static RealtimeStop realtime(ResultSet rs) throws SQLException {
        Integer arrivalDelay = (Integer) rs.getObject("arrival_delay_seconds");
        Integer departureDelay = (Integer) rs.getObject("departure_delay_seconds");
        var arrival = rs.getTimestamp("arrival_time");
        var departure = rs.getTimestamp("departure_time");
        return new RealtimeStop(arrivalDelay, departureDelay,
                arrival == null ? null : arrival.toInstant(),
                departure == null ? null : departure.toInstant(),
                rs.getString("schedule_relationship"));
    }

    private static Stop stop(ResultSet rs) throws SQLException {
        return new Stop(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getDouble(6), rs.getDouble(7),
                rs.getBoolean(8));
    }

    private static StopLocation stopLocation(ResultSet rs) throws SQLException {
        return new StopLocation(rs.getLong("feed_id"), rs.getString("feed_key"),
                rs.getString("region_code"), rs.getString("region_name"),
                rs.getString("publisher_name"), rs.getString("stop_id"),
                rs.getString("stop_name"), rs.getDouble("stop_latitude"),
                rs.getDouble("stop_longitude"), rs.getInt("location_type"),
                split(rs.getString("modes"), ","), split(rs.getString("operators"), "\\|"),
                split(rs.getString("lines"), "\\|"), rs.getBoolean("realtime_fresh"));
    }

    private static List<String> split(String value, String delimiter) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(List.of(value.split(delimiter))));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String normalize(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record Stop(long feedId, String feedKey, String timezone, String stopId,
                       String name, double latitude, double longitude, boolean realtimeFresh) {
        public String key() {
            return feedId + ":" + stopId;
        }
    }

    public record StopLocation(long feedId, String feedKey, String regionCode, String regionName,
                               String publisherName, String stopId, String name,
                               double latitude, double longitude, int locationType,
                               List<String> modes, List<String> operators, List<String> lines,
                               boolean realtimeAvailable) {
        public String key() {
            return feedKey + ":" + stopId;
        }
    }

    public record Boarding(long feedId, String feedKey, String timezone, String tripId,
                           LocalDate serviceDate, int stopSequence, String routeId,
                           String shortName, String longName, String headsign, String agency,
                           TransitMode mode, Instant scheduledDeparture, Instant departure,
                           boolean realtime, boolean cancelled, boolean realtimeFeedFresh) {
    }

    public record TripStop(Stop stop, int sequence, Instant arrival, Instant departure,
                           int pickupType, int dropOffType, boolean realtime,
                           String scheduleRelationship) {
    }

    public record Transfer(Stop to, int seconds, boolean explicit) {
    }

    private record RealtimeStop(Integer arrivalDelay, Integer departureDelay,
                                Instant arrivalTime, Instant departureTime, String relationship) {
    }

    private record RealtimeIndex(Map<Integer, RealtimeStop> bySequence,
                                 Map<String, RealtimeStop> byStopId) {
        static RealtimeIndex empty() {
            return new RealtimeIndex(Map.of(), Map.of());
        }
    }

    private record TripStatus(String relationship) {
    }
}
