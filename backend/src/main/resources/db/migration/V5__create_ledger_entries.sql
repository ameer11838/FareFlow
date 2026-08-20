-- Append-only financial ledger.
--
-- Nothing in this table is ever UPDATEd or DELETEd. A mistaken charge is
-- corrected by appending a FARE_ADJUSTMENT; a cancelled trip is corrected by
-- appending a REFUND. That immutability is the whole point: it is what lets you
-- answer "what did we charge, and when did we learn it was wrong?"
--
-- amount_cents is SIGNED: negative = money out, positive = money in. A single
-- signed column makes weekly spend one SUM() instead of SUM(debit) - SUM(credit)
-- plus a rule about which column may be populated.

CREATE TABLE ledger_entries
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    -- Nullable: FARE_ADJUSTMENT entries need not belong to a trip.
    trip_id         BIGINT      NULL REFERENCES trips (id),
    type            TEXT        NOT NULL,
    amount_cents    BIGINT      NOT NULL,
    description     TEXT        NOT NULL,

    -- BUSINESS time: when the money movement happened. Drives the weekly window.
    occurred_at     TIMESTAMPTZ NOT NULL,
    -- SYSTEM time: when we recorded the row. These differ once events can arrive late.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Unused in Phase 1. Added now because attaching a UNIQUE column to a hot
    -- table later is a migration; adding it now is free. This is the column that
    -- makes "process each transit tap exactly once" a one-line ON CONFLICT.
    idempotency_key TEXT        NULL UNIQUE,

    CONSTRAINT chk_ledger_type
        CHECK (type IN ('TRIP_CHARGE', 'REFUND', 'FARE_ADJUSTMENT')),

    -- The sign is an invariant, so the database enforces it. A positive
    -- TRIP_CHARGE is not a bug to catch in review; it is a row that cannot exist.
    CONSTRAINT chk_ledger_sign_matches_type CHECK (
        (type = 'TRIP_CHARGE' AND amount_cents < 0) OR
        (type = 'REFUND' AND amount_cents > 0) OR
        (type = 'FARE_ADJUSTMENT' AND amount_cents <> 0)
    ),

    CONSTRAINT chk_ledger_trip_required
        CHECK (type = 'FARE_ADJUSTMENT' OR trip_id IS NOT NULL),

    CONSTRAINT chk_ledger_description_not_blank
        CHECK (length(btrim(description)) > 0)
);

-- Supports both the weekly SUM and the paginated ledger view.
CREATE INDEX idx_ledger_user_occurred_at ON ledger_entries (user_id, occurred_at DESC);
CREATE INDEX idx_ledger_trip ON ledger_entries (trip_id) WHERE trip_id IS NOT NULL;
