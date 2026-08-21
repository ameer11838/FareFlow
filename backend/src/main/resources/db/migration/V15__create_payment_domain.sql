-- Payment intents sit between an authoritative fare quote and a completed trip.
-- They are deliberately provider-neutral: today FareFlow Wallet and a simulated
-- card gateway use them; a real sandbox gateway can implement the same lifecycle.

CREATE TABLE payment_intents
(
    id                   UUID PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users (id),
    journey_id           BIGINT      NOT NULL REFERENCES journeys (id),
    trip_id              BIGINT      NULL UNIQUE REFERENCES trips (id),

    amount_cents         BIGINT      NOT NULL,
    currency             TEXT        NOT NULL DEFAULT 'USD',
    payment_method       TEXT        NOT NULL,
    status               TEXT        NOT NULL,

    -- Same key + same fingerprint is a replay. Same key + a different
    -- fingerprint is a conflict, never a second purchase.
    idempotency_key      TEXT        NOT NULL,
    request_fingerprint  TEXT        NOT NULL,

    baseline_fare_cents  BIGINT      NULL,
    selected_label       TEXT        NOT NULL DEFAULT 'MANUAL',
    attempt_count        INTEGER     NOT NULL DEFAULT 0,
    provider_reference   TEXT        NULL,
    failure_code         TEXT        NULL,
    failure_message      TEXT        NULL,

    authorized_at        TIMESTAMPTZ NULL,
    processing_at        TIMESTAMPTZ NULL,
    settled_at           TIMESTAMPTZ NULL,
    failed_at            TIMESTAMPTZ NULL,
    refunded_at          TIMESTAMPTZ NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uq_payment_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_payment_amount CHECK (amount_cents >= 0),
    CONSTRAINT chk_payment_currency CHECK (currency = 'USD'),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('FAREFLOW_WALLET', 'SIMULATED_CARD')),
    CONSTRAINT chk_payment_status CHECK (status IN
        ('CREATED', 'AUTHORIZED', 'PROCESSING', 'SETTLED', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payment_attempts CHECK (attempt_count >= 0),
    CONSTRAINT chk_payment_baseline CHECK
        (baseline_fare_cents IS NULL OR baseline_fare_cents >= 0),
    CONSTRAINT chk_payment_selected_label CHECK
        (selected_label IN ('CHEAPEST', 'FASTEST', 'BEST_VALUE', 'MANUAL')),
    CONSTRAINT chk_payment_key_not_blank CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT chk_payment_fingerprint_not_blank CHECK (length(btrim(request_fingerprint)) > 0),
    CONSTRAINT chk_payment_settled_has_trip CHECK
        (status NOT IN ('SETTLED', 'REFUNDED') OR trip_id IS NOT NULL)
);

CREATE INDEX idx_payment_user_created ON payment_intents (user_id, created_at DESC);
CREATE INDEX idx_payment_status_updated ON payment_intents (status, updated_at);

-- State changes are an append-only audit trail. The mutable intent is the current
-- projection; this table answers how it reached that state.
CREATE TABLE payment_events
(
    id                 BIGSERIAL PRIMARY KEY,
    payment_intent_id  UUID        NOT NULL REFERENCES payment_intents (id),
    from_status        TEXT        NULL,
    to_status          TEXT        NOT NULL,
    reason             TEXT        NOT NULL,
    occurred_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_payment_event_from CHECK
        (from_status IS NULL OR from_status IN
            ('CREATED', 'AUTHORIZED', 'PROCESSING', 'SETTLED', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payment_event_to CHECK
        (to_status IN ('CREATED', 'AUTHORIZED', 'PROCESSING', 'SETTLED', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payment_event_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX idx_payment_events_intent ON payment_events (payment_intent_id, id);

-- Tie every financial movement back to the payment that caused it. Legacy rows
-- remain valid with NULL; new payment-backed charges and refunds always set it.
ALTER TABLE ledger_entries
    ADD COLUMN payment_intent_id UUID NULL REFERENCES payment_intents (id);

CREATE INDEX idx_ledger_payment_intent ON ledger_entries (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;
