-- Persisted journey snapshots.
--
-- WHY A SNAPSHOT, NOT A REFERENCE
-- A trip must remain accurate forever. Schedules shift, fares rise, lines get
-- renamed, and the discovery algorithm itself will change. If a historical trip
-- pointed at live network data, every one of those changes would silently rewrite
-- what the user was told they bought. So selecting a journey copies the facts.
--
-- WHY RELATIONAL, NOT A JSON BLOB
-- Legs are queried, aggregated, and displayed independently -- "how many minutes
-- did I spend on PATH this month" is a normal question. A blob would make that a
-- full scan and a parse, and would let malformed itineraries in unnoticed.

CREATE TABLE journeys
(
    id                    BIGSERIAL PRIMARY KEY,
    -- The deterministic line-sequence key discovery produced, e.g. SEPTA_TRE>NJT_NEC.
    -- Kept for traceability, not as an identity: two riders may take the same shape.
    discovery_key         TEXT          NOT NULL,

    origin_display_name   TEXT          NOT NULL,
    destination_display_name TEXT       NOT NULL,
    origin_latitude       NUMERIC(9, 6) NOT NULL,
    origin_longitude      NUMERIC(9, 6) NOT NULL,
    destination_latitude  NUMERIC(9, 6) NOT NULL,
    destination_longitude NUMERIC(9, 6) NOT NULL,

    total_duration_minutes INTEGER      NOT NULL,
    walking_minutes        INTEGER      NOT NULL DEFAULT 0,
    transfers              INTEGER      NOT NULL DEFAULT 0,

    -- Null when the journey could not be priced. Never 0 as a stand-in: a journey
    -- with an unknown fare is a different fact from a free one.
    total_fare_cents      BIGINT        NULL,
    fare_status           TEXT          NOT NULL,
    fare_source           TEXT          NOT NULL,
    -- The receipt lines that justified the total, frozen at selection time.
    fare_breakdown        TEXT          NULL,

    data_source           TEXT          NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_journey_duration_positive CHECK (total_duration_minutes > 0),
    CONSTRAINT chk_journey_transfers CHECK (transfers >= 0),
    CONSTRAINT chk_journey_walking CHECK (walking_minutes >= 0),
    CONSTRAINT chk_journey_fare CHECK (total_fare_cents IS NULL OR total_fare_cents >= 0),
    CONSTRAINT chk_journey_fare_status CHECK (fare_status IN ('EXACT', 'ESTIMATED', 'UNKNOWN')),
    -- The invariant that keeps an unknown fare from becoming zero further down.
    CONSTRAINT chk_journey_unknown_has_no_total
        CHECK ((fare_status = 'UNKNOWN') = (total_fare_cents IS NULL))
);

CREATE TABLE journey_legs
(
    id                BIGSERIAL PRIMARY KEY,
    journey_id        BIGINT  NOT NULL REFERENCES journeys (id) ON DELETE CASCADE,
    sequence          INTEGER NOT NULL,
    mode              TEXT    NOT NULL,
    agency            TEXT    NULL,
    line_name         TEXT    NOT NULL,
    from_name         TEXT    NOT NULL,
    to_name           TEXT    NOT NULL,
    duration_minutes  INTEGER NOT NULL,
    wait_minutes      INTEGER NOT NULL DEFAULT 0,
    -- Ordered "lat,lon;lat,lon" pairs. A compact encoding beats a child table for
    -- data that is only ever read back whole to draw a line.
    waypoints         TEXT    NULL,

    CONSTRAINT uq_journey_leg_sequence UNIQUE (journey_id, sequence),
    CONSTRAINT chk_leg_sequence CHECK (sequence >= 0),
    CONSTRAINT chk_leg_duration CHECK (duration_minutes >= 0),
    CONSTRAINT chk_leg_wait CHECK (wait_minutes >= 0),
    CONSTRAINT chk_leg_mode CHECK (mode IN ('WALK', 'RAIL', 'SUBWAY', 'LIGHT_RAIL', 'BUS', 'FERRY'))
);

CREATE INDEX idx_journey_legs_journey ON journey_legs (journey_id, sequence);

-- A trip now comes from EITHER a seeded route or a discovered journey.
ALTER TABLE trips ADD COLUMN journey_id BIGINT NULL REFERENCES journeys (id);

-- transit_route_id was mandatory because journeys did not exist. It becomes
-- optional so a discovered itinerary can back a trip without a catalog row.
ALTER TABLE trips ALTER COLUMN transit_route_id DROP NOT NULL;

-- Exactly one origin for a trip. Both, or neither, would make the snapshot ambiguous.
ALTER TABLE trips ADD CONSTRAINT chk_trip_has_one_source
    CHECK ((transit_route_id IS NULL) <> (journey_id IS NULL));

-- Idempotency for trip creation. A double-submitted Choose must not charge twice;
-- the unique index is what makes that a database guarantee rather than a race.
ALTER TABLE trips ADD COLUMN idempotency_key TEXT NULL;
CREATE UNIQUE INDEX uq_trips_idempotency ON trips (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_trips_journey ON trips (journey_id) WHERE journey_id IS NOT NULL;
