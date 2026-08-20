-- Transit route catalog.
--
-- Phase 1 uses mock data seeded by V2. Later phases replace this with real
-- transit feeds, at which point origin/destination become foreign keys to a
-- stations table rather than free text.
--
-- Money is stored as integer cents (BIGINT). Never floating point: binary
-- floats cannot represent 0.1 exactly, and summing them accumulates drift.

CREATE TABLE transit_routes
(
    id               BIGSERIAL PRIMARY KEY,
    origin           TEXT        NOT NULL,
    destination      TEXT        NOT NULL,
    provider         TEXT        NOT NULL,
    mode             TEXT        NOT NULL,
    duration_minutes INTEGER     NOT NULL,
    fare_cents       BIGINT      NOT NULL,
    transfers        INTEGER     NOT NULL DEFAULT 0,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A zero-duration route would be infinitely attractive and would corrupt
    -- normalization in the scorer. Enforced here so no code path can bypass it.
    CONSTRAINT chk_transit_routes_duration_positive
        CHECK (duration_minutes > 0),
    CONSTRAINT chk_transit_routes_fare_non_negative
        CHECK (fare_cents >= 0),
    CONSTRAINT chk_transit_routes_transfers_non_negative
        CHECK (transfers >= 0),
    CONSTRAINT chk_transit_routes_origin_not_blank
        CHECK (length(btrim(origin)) > 0),
    CONSTRAINT chk_transit_routes_destination_not_blank
        CHECK (length(btrim(destination)) > 0)
);

-- Case-insensitive origin/destination lookup. Partial index: retired routes
-- are never searched, so they stay out of the index.
CREATE INDEX idx_transit_routes_origin_destination
    ON transit_routes (lower(origin), lower(destination))
    WHERE active;
