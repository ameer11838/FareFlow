-- Provider-neutral GTFS Schedule + GTFS-Realtime storage.
-- External identifiers are namespaced by feed_id because GTFS only guarantees
-- uniqueness within one dataset. Every imported fact retains its source feed.

CREATE TABLE gtfs_feeds
(
    id                         BIGSERIAL PRIMARY KEY,
    feed_key                   TEXT        NOT NULL UNIQUE,
    region_code                TEXT        NOT NULL,
    region_name                TEXT        NOT NULL,
    publisher_name             TEXT        NOT NULL,
    static_url                 TEXT        NOT NULL,
    realtime_trip_updates_url  TEXT        NULL,
    agency_timezone            TEXT        NULL,
    status                     TEXT        NOT NULL DEFAULT 'CONFIGURED',
    imported_at                TIMESTAMPTZ NULL,
    realtime_updated_at        TIMESTAMPTZ NULL,
    realtime_expires_at        TIMESTAMPTZ NULL,
    feed_start_date            DATE        NULL,
    feed_end_date              DATE        NULL,
    content_sha256             TEXT        NULL,
    agency_count               INTEGER     NOT NULL DEFAULT 0,
    stop_count                 INTEGER     NOT NULL DEFAULT 0,
    route_count                INTEGER     NOT NULL DEFAULT 0,
    trip_count                 INTEGER     NOT NULL DEFAULT 0,
    unsupported_route_count    INTEGER     NOT NULL DEFAULT 0,
    last_error                 TEXT        NULL,
    enabled                    BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_gtfs_feed_status CHECK
        (status IN ('CONFIGURED', 'IMPORTING', 'READY', 'FAILED')),
    CONSTRAINT chk_gtfs_feed_counts CHECK
        (agency_count >= 0 AND stop_count >= 0 AND route_count >= 0
         AND trip_count >= 0 AND unsupported_route_count >= 0)
);

CREATE TABLE gtfs_agencies
(
    feed_id          BIGINT NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    agency_id        TEXT   NOT NULL,
    agency_name      TEXT   NOT NULL,
    agency_url       TEXT   NULL,
    agency_timezone  TEXT   NOT NULL,
    PRIMARY KEY (feed_id, agency_id)
);

CREATE TABLE gtfs_stops
(
    feed_id           BIGINT         NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    stop_id           TEXT           NOT NULL,
    stop_code         TEXT           NULL,
    stop_name         TEXT           NOT NULL,
    stop_latitude     NUMERIC(9, 6)  NOT NULL,
    stop_longitude    NUMERIC(9, 6)  NOT NULL,
    location_type     INTEGER        NOT NULL DEFAULT 0,
    parent_station_id TEXT           NULL,
    platform_code     TEXT           NULL,
    wheelchair_boarding INTEGER      NULL,
    PRIMARY KEY (feed_id, stop_id),
    CONSTRAINT chk_gtfs_stop_location_type CHECK (location_type BETWEEN 0 AND 4),
    CONSTRAINT chk_gtfs_stop_lat CHECK (stop_latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_gtfs_stop_lon CHECK (stop_longitude BETWEEN -180 AND 180)
);
CREATE INDEX idx_gtfs_stops_coordinates ON gtfs_stops (stop_latitude, stop_longitude);

CREATE TABLE gtfs_routes
(
    feed_id          BIGINT NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    route_id         TEXT   NOT NULL,
    agency_id        TEXT   NOT NULL,
    route_short_name TEXT   NULL,
    route_long_name  TEXT   NULL,
    route_type       INTEGER NOT NULL,
    transit_mode     TEXT   NOT NULL,
    route_color      TEXT   NULL,
    route_text_color TEXT   NULL,
    PRIMARY KEY (feed_id, route_id),
    CONSTRAINT chk_gtfs_transit_mode CHECK
        (transit_mode IN ('RAIL', 'SUBWAY', 'LIGHT_RAIL', 'BUS', 'FERRY'))
);

CREATE TABLE gtfs_services
(
    feed_id    BIGINT  NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    service_id TEXT    NOT NULL,
    monday     BOOLEAN NOT NULL DEFAULT FALSE,
    tuesday    BOOLEAN NOT NULL DEFAULT FALSE,
    wednesday  BOOLEAN NOT NULL DEFAULT FALSE,
    thursday   BOOLEAN NOT NULL DEFAULT FALSE,
    friday     BOOLEAN NOT NULL DEFAULT FALSE,
    saturday   BOOLEAN NOT NULL DEFAULT FALSE,
    sunday     BOOLEAN NOT NULL DEFAULT FALSE,
    start_date DATE    NULL,
    end_date   DATE    NULL,
    PRIMARY KEY (feed_id, service_id),
    CONSTRAINT chk_gtfs_service_dates CHECK
        ((start_date IS NULL AND end_date IS NULL)
         OR (start_date IS NOT NULL AND end_date IS NOT NULL AND start_date <= end_date))
);

CREATE TABLE gtfs_service_exceptions
(
    feed_id       BIGINT  NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    service_id    TEXT    NOT NULL,
    service_date  DATE    NOT NULL,
    exception_type INTEGER NOT NULL,
    PRIMARY KEY (feed_id, service_id, service_date),
    CONSTRAINT chk_gtfs_exception_type CHECK (exception_type IN (1, 2))
);
CREATE INDEX idx_gtfs_service_exceptions_date
    ON gtfs_service_exceptions (feed_id, service_date);

CREATE TABLE gtfs_trips
(
    feed_id      BIGINT NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    trip_id      TEXT   NOT NULL,
    route_id     TEXT   NOT NULL,
    service_id   TEXT   NOT NULL,
    trip_headsign TEXT  NULL,
    direction_id INTEGER NULL,
    shape_id      TEXT  NULL,
    wheelchair_accessible INTEGER NULL,
    PRIMARY KEY (feed_id, trip_id),
    FOREIGN KEY (feed_id, route_id) REFERENCES gtfs_routes (feed_id, route_id),
    FOREIGN KEY (feed_id, service_id) REFERENCES gtfs_services (feed_id, service_id)
);
CREATE INDEX idx_gtfs_trips_service ON gtfs_trips (feed_id, service_id);
CREATE INDEX idx_gtfs_trips_route ON gtfs_trips (feed_id, route_id);

CREATE TABLE gtfs_stop_times
(
    feed_id          BIGINT  NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    trip_id          TEXT    NOT NULL,
    stop_id          TEXT    NOT NULL,
    stop_sequence    INTEGER NOT NULL,
    arrival_seconds  INTEGER NOT NULL,
    departure_seconds INTEGER NOT NULL,
    pickup_type      INTEGER NOT NULL DEFAULT 0,
    drop_off_type    INTEGER NOT NULL DEFAULT 0,
    timepoint        INTEGER NULL,
    PRIMARY KEY (feed_id, trip_id, stop_sequence),
    FOREIGN KEY (feed_id, trip_id) REFERENCES gtfs_trips (feed_id, trip_id) ON DELETE CASCADE,
    FOREIGN KEY (feed_id, stop_id) REFERENCES gtfs_stops (feed_id, stop_id),
    CONSTRAINT chk_gtfs_stop_sequence CHECK (stop_sequence >= 0),
    CONSTRAINT chk_gtfs_stop_times CHECK
        (arrival_seconds >= 0 AND departure_seconds >= arrival_seconds),
    CONSTRAINT chk_gtfs_pickup CHECK (pickup_type BETWEEN 0 AND 3),
    CONSTRAINT chk_gtfs_dropoff CHECK (drop_off_type BETWEEN 0 AND 3)
);
CREATE INDEX idx_gtfs_stop_times_departures
    ON gtfs_stop_times (feed_id, stop_id, departure_seconds);
CREATE INDEX idx_gtfs_stop_times_trip
    ON gtfs_stop_times (feed_id, trip_id, stop_sequence);

CREATE TABLE gtfs_transfers
(
    id                  BIGSERIAL PRIMARY KEY,
    feed_id             BIGINT  NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    from_stop_id        TEXT    NOT NULL,
    to_stop_id          TEXT    NOT NULL,
    transfer_type       INTEGER NOT NULL DEFAULT 0,
    min_transfer_seconds INTEGER NULL,
    from_route_id       TEXT    NULL,
    to_route_id         TEXT    NULL,
    from_trip_id        TEXT    NULL,
    to_trip_id          TEXT    NULL,
    CONSTRAINT chk_gtfs_transfer_type CHECK (transfer_type BETWEEN 0 AND 5),
    CONSTRAINT chk_gtfs_transfer_duration CHECK
        (min_transfer_seconds IS NULL OR min_transfer_seconds >= 0)
);
CREATE INDEX idx_gtfs_transfers_from ON gtfs_transfers (feed_id, from_stop_id);

-- Explicit, reviewed links between separate feeds. Routing may also infer a
-- walking connection only when official stops have the same normalized name and
-- are within a conservative distance; that inference never supplies a schedule.
CREATE TABLE gtfs_inter_feed_transfers
(
    id                   BIGSERIAL PRIMARY KEY,
    from_feed_id         BIGINT  NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    from_stop_id         TEXT    NOT NULL,
    to_feed_id           BIGINT  NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    to_stop_id           TEXT    NOT NULL,
    min_transfer_seconds INTEGER NOT NULL,
    source               TEXT    NOT NULL DEFAULT 'VERIFIED',
    UNIQUE (from_feed_id, from_stop_id, to_feed_id, to_stop_id),
    CONSTRAINT chk_inter_feed_transfer_seconds CHECK (min_transfer_seconds >= 0),
    CONSTRAINT chk_inter_feed_transfer_source CHECK (source IN ('VERIFIED'))
);

CREATE TABLE gtfs_realtime_stop_updates
(
    id                     BIGSERIAL PRIMARY KEY,
    feed_id                BIGINT      NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    trip_id                TEXT        NOT NULL,
    start_date             DATE        NULL,
    stop_id                TEXT        NULL,
    stop_sequence          INTEGER     NULL,
    arrival_delay_seconds  INTEGER     NULL,
    departure_delay_seconds INTEGER    NULL,
    arrival_time           TIMESTAMPTZ NULL,
    departure_time         TIMESTAMPTZ NULL,
    schedule_relationship  TEXT        NOT NULL DEFAULT 'SCHEDULED',
    observed_at            TIMESTAMPTZ NOT NULL,
    expires_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_gtfs_rt_stop_reference CHECK
        (stop_id IS NOT NULL OR stop_sequence IS NOT NULL),
    CONSTRAINT chk_gtfs_rt_relationship CHECK
        (schedule_relationship IN ('SCHEDULED', 'SKIPPED', 'NO_DATA', 'UNSCHEDULED'))
);
CREATE INDEX idx_gtfs_rt_lookup
    ON gtfs_realtime_stop_updates (feed_id, trip_id, start_date, stop_sequence);
CREATE INDEX idx_gtfs_rt_expiry ON gtfs_realtime_stop_updates (expires_at);

-- Trip-level state is separate because a canceled trip often has no stop update.
-- An absent row never means "on time"; it means FareFlow has no live fact.
CREATE TABLE gtfs_realtime_trip_status
(
    id                    BIGSERIAL PRIMARY KEY,
    feed_id               BIGINT      NOT NULL REFERENCES gtfs_feeds (id) ON DELETE CASCADE,
    trip_id               TEXT        NOT NULL,
    start_date            DATE        NULL,
    schedule_relationship TEXT        NOT NULL,
    observed_at           TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_gtfs_rt_trip_relationship CHECK
        (schedule_relationship IN ('SCHEDULED', 'CANCELED', 'ADDED', 'UNSCHEDULED',
                                   'DUPLICATED', 'DELETED'))
);
CREATE INDEX idx_gtfs_rt_trip_expiry ON gtfs_realtime_trip_status (expires_at);

-- Initial agency registry. A row is CONFIGURED, not supported, until an import
-- succeeds. The coverage API exposes that distinction explicitly.
INSERT INTO gtfs_feeds
    (feed_key, region_code, region_name, publisher_name, static_url,
     realtime_trip_updates_url)
VALUES
    ('mbta', 'BOS', 'Greater Boston', 'Massachusetts Bay Transportation Authority',
     'https://cdn.mbta.com/MBTA_GTFS.zip',
     'https://cdn.mbta.com/realtime/TripUpdates.pb'),
    ('cta', 'CHI', 'Chicago', 'Chicago Transit Authority',
     'https://www.transitchicago.com/downloads/sch_data/google_transit.zip', NULL),
    ('bart', 'SFBAY', 'San Francisco Bay Area', 'Bay Area Rapid Transit',
     'https://www.bart.gov/dev/schedules/google_transit.zip',
     'https://api.bart.gov/gtfsrt/tripupdate.aspx');
