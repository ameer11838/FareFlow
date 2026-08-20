-- Trips taken by users.
--
-- The origin/destination/provider/fare/duration/transfers columns are a SNAPSHOT
-- of the route as it was at the moment of travel, not a join to transit_routes.
-- If PATH raises its fare next month, this trip must still read $3.00 -- the same
-- reason an invoice line item stores the price rather than pointing at a catalog.

CREATE TABLE trips
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id),
    transit_route_id    BIGINT      NOT NULL REFERENCES transit_routes (id),

    -- snapshot of the route
    origin              TEXT        NOT NULL,
    destination         TEXT        NOT NULL,
    provider            TEXT        NOT NULL,
    mode                TEXT        NOT NULL,
    fare_cents          BIGINT      NOT NULL,
    duration_minutes    INTEGER     NOT NULL,
    transfers           INTEGER     NOT NULL,

    -- snapshot of the decision context
    selected_label      TEXT        NOT NULL,

    -- Fare of the FASTEST route for this origin/destination at decision time.
    -- NULLABLE ON PURPOSE: when fewer than two routes existed the user made no
    -- choice, so there is no honest "saved vs. fastest route" figure. NULL means
    -- "not computable", which is a different fact from 0 meaning "computed, and
    -- it was zero". A NOT NULL DEFAULT 0 here would invent data.
    baseline_fare_cents BIGINT      NULL,

    status              TEXT        NOT NULL DEFAULT 'COMPLETED',
    taken_at            TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trips_fare_non_negative CHECK (fare_cents >= 0),
    CONSTRAINT chk_trips_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_trips_transfers_non_negative CHECK (transfers >= 0),
    CONSTRAINT chk_trips_baseline_non_negative CHECK (baseline_fare_cents IS NULL OR baseline_fare_cents >= 0),
    CONSTRAINT chk_trips_status CHECK (status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_trips_selected_label
        CHECK (selected_label IN ('CHEAPEST', 'FASTEST', 'BEST_VALUE', 'MANUAL'))
);

-- Every trip query is "this user's trips, newest first".
CREATE INDEX idx_trips_user_taken_at ON trips (user_id, taken_at DESC);
