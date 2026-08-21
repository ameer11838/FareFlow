package com.fareflow.gtfs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GtfsCoverageService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GtfsCoverageService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public CoverageResponse coverage() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, java.time.ZoneOffset.UTC);
        List<FeedCoverage> feeds = jdbc.query("""
                SELECT f.feed_key, f.region_code, f.region_name, f.publisher_name, f.status,
                       f.imported_at, f.feed_start_date, f.feed_end_date,
                       f.realtime_trip_updates_url IS NOT NULL AS realtime_configured,
                       f.realtime_updated_at, f.realtime_expires_at,
                       f.agency_count, f.stop_count, f.route_count, f.trip_count,
                       f.unsupported_route_count, f.last_error,
                       ARRAY(SELECT DISTINCT a.agency_name FROM gtfs_agencies a
                              WHERE a.feed_id = f.id ORDER BY a.agency_name) AS agencies,
                       ARRAY(SELECT DISTINCT r.transit_mode FROM gtfs_routes r
                              WHERE r.feed_id = f.id ORDER BY r.transit_mode) AS modes
                  FROM gtfs_feeds f WHERE f.enabled = TRUE ORDER BY f.region_name, f.feed_key
                """, (rs, row) -> map(rs, today, now));
        return new CoverageResponse(feeds.stream().filter(FeedCoverage::supported).count(), feeds);
    }

    private FeedCoverage map(ResultSet rs, LocalDate today, Instant now) throws SQLException {
        String status = rs.getString("status");
        LocalDate start = localDate(rs.getDate("feed_start_date"));
        LocalDate end = localDate(rs.getDate("feed_end_date"));
        boolean withinWindow = (start == null || !today.isBefore(start))
                && (end == null || !today.isAfter(end));
        var imported = rs.getTimestamp("imported_at");
        var realtimeUpdated = rs.getTimestamp("realtime_updated_at");
        var realtimeExpires = rs.getTimestamp("realtime_expires_at");
        boolean realtimeFresh = realtimeExpires != null && realtimeExpires.toInstant().isAfter(now);
        return new FeedCoverage(rs.getString("feed_key"), rs.getString("region_code"),
                rs.getString("region_name"), rs.getString("publisher_name"), status,
                "READY".equals(status) && withinWindow, stringArray(rs.getArray("agencies")),
                stringArray(rs.getArray("modes")), start, end,
                imported == null ? null : imported.toInstant(),
                rs.getBoolean("realtime_configured"), realtimeFresh,
                realtimeUpdated == null ? null : realtimeUpdated.toInstant(),
                rs.getInt("agency_count"), rs.getInt("stop_count"), rs.getInt("route_count"),
                rs.getInt("trip_count"), rs.getInt("unsupported_route_count"),
                rs.getString("last_error"));
    }

    private static List<String> stringArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(Object::toString).toList();
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    public record CoverageResponse(long supportedFeedCount, List<FeedCoverage> feeds) {
    }

    public record FeedCoverage(String feedKey, String regionCode, String regionName,
                               String publisherName, String status, boolean supported,
                               List<String> agencies, List<String> modes,
                               LocalDate serviceStart, LocalDate serviceEnd, Instant importedAt,
                               boolean realtimeConfigured, boolean realtimeAvailable,
                               Instant realtimeUpdatedAt, int agencyCount, int stopCount,
                               int routeCount, int tripCount, int unsupportedRouteCount,
                               String lastError) {
    }
}
