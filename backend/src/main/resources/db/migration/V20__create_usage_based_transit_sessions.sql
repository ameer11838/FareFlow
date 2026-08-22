-- A transit session is the operational record between choosing a route and paying.
-- It snapshots a real discovered journey, records rider-confirmed progress, and
-- produces an authoritative fare under FareFlow's explicitly simulated pricing
-- model. It never claims an agency accepted or calculated this fare.

ALTER TABLE journey_legs
    ADD COLUMN distance_metres DOUBLE PRECISION NULL,
    ADD COLUMN departure_time TIMESTAMPTZ NULL,
    ADD COLUMN arrival_time TIMESTAMPTZ NULL,
    ADD COLUMN realtime BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stop_count INTEGER NULL;

ALTER TABLE journey_legs
    ADD CONSTRAINT chk_journey_leg_distance
        CHECK (distance_metres IS NULL OR distance_metres >= 0),
    ADD CONSTRAINT chk_journey_leg_stop_count
        CHECK (stop_count IS NULL OR stop_count > 0),
    ADD CONSTRAINT chk_journey_leg_times
        CHECK (arrival_time IS NULL OR departure_time IS NULL OR arrival_time >= departure_time);

CREATE TABLE transit_sessions
(
    id                       UUID PRIMARY KEY,
    user_id                  BIGINT      NOT NULL REFERENCES users (id),
    journey_id               BIGINT      NOT NULL REFERENCES journeys (id),
    status                   TEXT        NOT NULL,

    progress_units_total     INTEGER     NOT NULL,
    progress_units_completed INTEGER     NOT NULL DEFAULT 0,
    planned_stop_count       INTEGER     NULL,
    completed_stop_count     INTEGER     NOT NULL DEFAULT 0,
    planned_distance_metres  BIGINT      NOT NULL,
    distance_travelled_metres BIGINT     NOT NULL DEFAULT 0,

    estimated_fare_min_cents BIGINT      NOT NULL,
    estimated_fare_max_cents BIGINT      NOT NULL,
    current_fare_cents       BIGINT      NOT NULL DEFAULT 0,
    final_fare_cents         BIGINT      NULL,
    base_fare_cents          BIGINT      NULL,
    distance_fare_cents      BIGINT      NULL,
    stop_fare_cents          BIGINT      NULL,
    pricing_version          TEXT        NOT NULL,
    progress_source          TEXT        NOT NULL DEFAULT 'RIDER_CONFIRMED',

    idempotency_key          TEXT        NOT NULL,
    request_fingerprint      TEXT        NOT NULL,
    started_at               TIMESTAMPTZ NOT NULL,
    ended_at                 TIMESTAMPTZ NULL,
    paid_at                  TIMESTAMPTZ NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uq_transit_session_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_transit_session_status CHECK
        (status IN ('STARTED', 'IN_PROGRESS', 'COMPLETED', 'NO_CHARGE', 'PAID')),
    CONSTRAINT chk_transit_session_progress CHECK
        (progress_units_total > 0 AND progress_units_completed >= 0
            AND progress_units_completed <= progress_units_total),
    CONSTRAINT chk_transit_session_stops CHECK
        (planned_stop_count IS NULL OR (planned_stop_count > 0
            AND completed_stop_count >= 0 AND completed_stop_count <= planned_stop_count)),
    CONSTRAINT chk_transit_session_distance CHECK
        (planned_distance_metres >= 0 AND distance_travelled_metres >= 0
            AND distance_travelled_metres <= planned_distance_metres),
    CONSTRAINT chk_transit_session_estimate CHECK
        (estimated_fare_min_cents >= 0 AND estimated_fare_max_cents >= estimated_fare_min_cents),
    CONSTRAINT chk_transit_session_current_fare CHECK (current_fare_cents >= 0),
    CONSTRAINT chk_transit_session_final_fare CHECK
        (final_fare_cents IS NULL OR final_fare_cents >= 0),
    CONSTRAINT chk_transit_session_pricing_parts CHECK
        ((base_fare_cents IS NULL AND distance_fare_cents IS NULL AND stop_fare_cents IS NULL)
         OR (base_fare_cents >= 0 AND distance_fare_cents >= 0 AND stop_fare_cents >= 0)),
    CONSTRAINT chk_transit_session_source CHECK
        (progress_source IN ('RIDER_CONFIRMED', 'LOCATION_VERIFIED', 'AGENCY_VERIFIED')),
    CONSTRAINT chk_transit_session_key CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT chk_transit_session_fingerprint CHECK (length(btrim(request_fingerprint)) > 0),
    CONSTRAINT chk_transit_session_end_state CHECK
        ((status IN ('STARTED', 'IN_PROGRESS') AND ended_at IS NULL AND final_fare_cents IS NULL)
         OR (status IN ('COMPLETED', 'NO_CHARGE', 'PAID') AND ended_at IS NOT NULL
             AND final_fare_cents IS NOT NULL)),
    CONSTRAINT chk_transit_session_paid_state CHECK
        ((status = 'PAID') = (paid_at IS NOT NULL)),
    CONSTRAINT chk_transit_session_no_charge CHECK
        (status <> 'NO_CHARGE' OR final_fare_cents = 0)
);

CREATE UNIQUE INDEX uq_transit_sessions_one_active_per_user
    ON transit_sessions (user_id)
    WHERE status IN ('STARTED', 'IN_PROGRESS', 'COMPLETED');
CREATE INDEX idx_transit_sessions_user_started
    ON transit_sessions (user_id, started_at DESC);

ALTER TABLE payment_intents
    ADD COLUMN transit_session_id UUID NULL UNIQUE REFERENCES transit_sessions (id);

ALTER TABLE trips
    ADD COLUMN transit_session_id UUID NULL UNIQUE REFERENCES transit_sessions (id),
    ADD COLUMN distance_metres BIGINT NULL,
    ADD COLUMN stops_travelled INTEGER NULL,
    ADD COLUMN fare_model TEXT NOT NULL DEFAULT 'FIXED';

ALTER TABLE trips
    ADD CONSTRAINT chk_trip_distance CHECK (distance_metres IS NULL OR distance_metres >= 0),
    ADD CONSTRAINT chk_trip_stops CHECK (stops_travelled IS NULL OR stops_travelled >= 0),
    ADD CONSTRAINT chk_trip_fare_model CHECK (fare_model IN ('FIXED', 'FAREFLOW_USAGE_V1'));

CREATE INDEX idx_payment_transit_session ON payment_intents (transit_session_id)
    WHERE transit_session_id IS NOT NULL;
