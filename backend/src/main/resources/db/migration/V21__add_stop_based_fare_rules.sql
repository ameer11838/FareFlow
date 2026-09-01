-- Stop-based fares are an append-only stream. Every reached, skipped, or diverted
-- boundary is retained with its discounts and cumulative total for auditability.

ALTER TABLE user_travel_profiles
    ADD COLUMN fare_category TEXT NOT NULL DEFAULT 'REGULAR',
    ADD CONSTRAINT chk_user_fare_category
        CHECK (fare_category IN ('REGULAR', 'STUDENT', 'SENIOR', 'REDUCED'));

ALTER TABLE transit_sessions
    ADD COLUMN fare_category TEXT NOT NULL DEFAULT 'REGULAR',
    ADD COLUMN spent_today_before_cents BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN spent_week_before_cents BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN transfer_discount_cents BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN concession_discount_cents BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cap_discount_cents BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_transit_session_fare_category
        CHECK (fare_category IN ('REGULAR', 'STUDENT', 'SENIOR', 'REDUCED')),
    ADD CONSTRAINT chk_transit_session_cap_snapshots
        CHECK (spent_today_before_cents >= 0 AND spent_week_before_cents >= 0),
    ADD CONSTRAINT chk_transit_session_discounts
        CHECK (transfer_discount_cents >= 0 AND concession_discount_cents >= 0
            AND cap_discount_cents >= 0);

CREATE TABLE transit_fare_events
(
    id                          BIGSERIAL PRIMARY KEY,
    transit_session_id          UUID        NOT NULL REFERENCES transit_sessions (id),
    sequence                    INTEGER     NOT NULL,
    event_type                  TEXT        NOT NULL,
    stop_name                   TEXT        NULL,
    line_name                   TEXT        NOT NULL,
    mode                        TEXT        NOT NULL,
    agency                      TEXT        NULL,
    gross_cents                 BIGINT      NOT NULL,
    transfer_discount_cents     BIGINT      NOT NULL DEFAULT 0,
    concession_discount_cents   BIGINT      NOT NULL DEFAULT 0,
    cap_discount_cents          BIGINT      NOT NULL DEFAULT 0,
    amount_cents                BIGINT      NOT NULL,
    cumulative_fare_cents       BIGINT      NOT NULL,
    description                 TEXT        NOT NULL,
    occurred_at                 TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_transit_fare_event_sequence UNIQUE (transit_session_id, sequence),
    CONSTRAINT chk_transit_fare_event_sequence CHECK (sequence > 0),
    CONSTRAINT chk_transit_fare_event_type CHECK
        (event_type IN ('STOP_COMPLETED', 'STOP_SKIPPED', 'ROUTE_DIVERSION')),
    CONSTRAINT chk_transit_fare_event_amounts CHECK
        (gross_cents >= 0 AND transfer_discount_cents >= 0
            AND concession_discount_cents >= 0 AND cap_discount_cents >= 0
            AND amount_cents >= 0 AND cumulative_fare_cents >= 0),
    CONSTRAINT chk_transit_fare_event_net CHECK
        (amount_cents = gross_cents - transfer_discount_cents
            - concession_discount_cents - cap_discount_cents),
    CONSTRAINT chk_transit_fare_event_no_service_charge CHECK
        (event_type = 'STOP_COMPLETED'
            OR (gross_cents = 0 AND amount_cents = 0))
);

CREATE INDEX idx_transit_fare_events_session
    ON transit_fare_events (transit_session_id, sequence);

CREATE OR REPLACE FUNCTION reject_transit_fare_event_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'transit fare events are append-only';
END;
$$;

CREATE TRIGGER transit_fare_events_no_update
    BEFORE UPDATE OR DELETE ON transit_fare_events
    FOR EACH ROW EXECUTE FUNCTION reject_transit_fare_event_mutation();
