-- Transit network: stops, lines, and the ordered stops on each line.
--
-- WHY THIS EXISTS
-- TomTom's Routing API has no public-transit mode -- `travelMode=publicTransit`
-- returns 400 "Invalid travel mode", and there is no transit endpoint on the key
-- we have. TomTom therefore handles geocoding, search, and map rendering; journey
-- planning runs over this network.
--
-- Everything seeded here describes REAL services between REAL stations, with
-- published coordinates and published fares. What is modelled rather than live:
-- typical durations (not a timetable) and headways. Nothing is invented -- where a
-- fare is genuinely dynamic (Amtrak), it is recorded as UNKNOWN rather than guessed.
--
-- This is the structure a GTFS loader would populate: stops <- stop_times <- trips.
-- Replacing curated data with a real feed means filling these same tables.

CREATE TABLE transit_stops
(
    id         BIGSERIAL PRIMARY KEY,
    code       TEXT          NOT NULL UNIQUE,
    name       TEXT          NOT NULL,
    locality   TEXT          NOT NULL,
    latitude   NUMERIC(9, 6) NOT NULL,
    longitude  NUMERIC(9, 6) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_stop_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_stop_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_stop_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- Bounding-box prefilter for "stops near this coordinate".
CREATE INDEX idx_stops_position ON transit_stops (latitude, longitude);

CREATE TABLE transit_lines
(
    id           BIGSERIAL PRIMARY KEY,
    code         TEXT        NOT NULL UNIQUE,
    name         TEXT        NOT NULL,
    agency       TEXT        NOT NULL,
    mode         TEXT        NOT NULL,
    -- Which fare policy prices a ride on this line. Resolved by the fare engine.
    fare_policy  TEXT        NOT NULL,
    -- Typical wait before boarding, used as a deterministic stand-in for a
    -- timetable. Explicitly an average, never presented as a departure time.
    headway_minutes INTEGER  NOT NULL DEFAULT 10,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_line_mode CHECK (mode IN ('RAIL', 'SUBWAY', 'BUS', 'FERRY', 'LIGHT_RAIL')),
    CONSTRAINT chk_line_headway CHECK (headway_minutes >= 0)
);

CREATE TABLE transit_line_stops
(
    id                 BIGSERIAL PRIMARY KEY,
    line_id            BIGINT  NOT NULL REFERENCES transit_lines (id) ON DELETE CASCADE,
    stop_id            BIGINT  NOT NULL REFERENCES transit_stops (id),
    sequence           INTEGER NOT NULL,
    -- Cumulative minutes from the line's first stop. Differences between two stops
    -- give the in-vehicle time for that segment.
    minutes_from_start INTEGER NOT NULL,

    CONSTRAINT uq_line_stop_sequence UNIQUE (line_id, sequence),
    CONSTRAINT chk_line_stop_sequence CHECK (sequence >= 0),
    CONSTRAINT chk_line_stop_minutes CHECK (minutes_from_start >= 0)
);

CREATE INDEX idx_line_stops_line ON transit_line_stops (line_id, sequence);
CREATE INDEX idx_line_stops_stop ON transit_line_stops (stop_id);
