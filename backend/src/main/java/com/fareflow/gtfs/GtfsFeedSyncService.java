package com.fareflow.gtfs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Downloads registered official feeds and delegates all parsing to the importers. */
@Service
public class GtfsFeedSyncService implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final GtfsScheduleImporter scheduleImporter;
    private final GtfsRealtimeImporter realtimeImporter;
    private final HttpClient http;
    private final boolean scheduleEnabled;
    private final boolean realtimeEnabled;
    private final boolean importOnStartup;

    public GtfsFeedSyncService(JdbcTemplate jdbc, GtfsScheduleImporter scheduleImporter,
                               GtfsRealtimeImporter realtimeImporter,
                               @Value("${fareflow.gtfs.schedule-enabled:false}") boolean scheduleEnabled,
                               @Value("${fareflow.gtfs.realtime-enabled:false}") boolean realtimeEnabled,
                               @Value("${fareflow.gtfs.import-on-startup:false}") boolean importOnStartup) {
        this.jdbc = jdbc;
        this.scheduleImporter = scheduleImporter;
        this.realtimeImporter = realtimeImporter;
        this.scheduleEnabled = scheduleEnabled;
        this.realtimeEnabled = realtimeEnabled;
        this.importOnStartup = importOnStartup;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (scheduleEnabled && importOnStartup) {
            syncSchedules();
        }
    }

    @Scheduled(fixedDelayString = "${fareflow.gtfs.static-refresh-millis:86400000}",
            initialDelayString = "${fareflow.gtfs.static-initial-delay-millis:60000}")
    public void syncSchedules() {
        if (!scheduleEnabled) {
            return;
        }
        feedsWithStaticUrls().forEach(feed -> {
            try {
                syncSchedule(feed);
            } catch (Exception exception) {
                recordFailure(feed.id(), exception);
            }
        });
    }

    @Scheduled(fixedDelayString = "${fareflow.gtfs.realtime-refresh-millis:30000}",
            initialDelayString = "${fareflow.gtfs.realtime-initial-delay-millis:15000}")
    public void syncRealtime() {
        if (!realtimeEnabled) {
            return;
        }
        feedsWithRealtimeUrls().forEach(feed -> {
            try {
                HttpResponse<InputStream> response = send(feed.realtimeUrl());
                try (InputStream body = response.body()) {
                    realtimeImporter.importTripUpdates(feed.key(), body);
                }
            } catch (Exception exception) {
                // Keep the static feed READY. A failed live refresh means live data
                // becomes unavailable after expiry; it must not erase the timetable.
                recordRealtimeFailure(feed.id(), exception);
            }
        });
    }

    public GtfsImportResult syncSchedule(String feedKey) throws IOException, InterruptedException {
        Feed feed = feedsWithStaticUrls().stream().filter(item -> item.key().equals(feedKey))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown GTFS feed: " + feedKey));
        return syncSchedule(feed);
    }

    private GtfsImportResult syncSchedule(Feed feed) throws IOException, InterruptedException {
        Path temporary = Files.createTempFile("fareflow-gtfs-" + feed.key() + "-", ".zip");
        try {
            HttpRequest request = request(feed.staticUrl());
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            ensureSuccess(response.statusCode(), feed.staticUrl());
            return scheduleImporter.importFeed(feed.key(), temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private HttpResponse<InputStream> send(String url) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            ensureSuccess(response.statusCode(), url);
        }
        return response;
    }

    private static HttpRequest request(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("GTFS feed URLs must use HTTPS");
        }
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "FareFlow/1.0 GTFS importer")
                .GET().build();
    }

    private static void ensureSuccess(int status, String url) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("GTFS publisher returned HTTP " + status + " for " + url);
        }
    }

    private List<Feed> feedsWithStaticUrls() {
        return jdbc.query("""
                SELECT id, feed_key, static_url, realtime_trip_updates_url
                  FROM gtfs_feeds WHERE enabled = TRUE ORDER BY feed_key
                """, (rs, row) -> new Feed(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4)));
    }

    private List<Feed> feedsWithRealtimeUrls() {
        return jdbc.query("""
                SELECT id, feed_key, static_url, realtime_trip_updates_url
                  FROM gtfs_feeds
                 WHERE enabled = TRUE AND status = 'READY'
                   AND realtime_trip_updates_url IS NOT NULL
                 ORDER BY feed_key
                """, (rs, row) -> new Feed(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4)));
    }

    private void recordFailure(long feedId, Exception exception) {
        jdbc.update("""
                UPDATE gtfs_feeds
                   SET status = CASE WHEN imported_at IS NULL THEN 'FAILED' ELSE status END,
                       last_error = ?
                 WHERE id = ?
                """, safeMessage(exception), feedId);
    }

    private void recordRealtimeFailure(long feedId, Exception exception) {
        jdbc.update("UPDATE gtfs_feeds SET last_error = ? WHERE id = ?",
                "Realtime: " + safeMessage(exception), feedId);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 1_000));
    }

    private record Feed(long id, String key, String staticUrl, String realtimeUrl) {
    }
}
