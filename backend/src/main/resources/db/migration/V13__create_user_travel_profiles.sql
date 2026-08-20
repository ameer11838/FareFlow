-- The travel + financial profile FareFlow builds during onboarding.
--
-- Deliberately its own table rather than more columns on `users`. `users` is an
-- identity record; this is a preferences record. They change for different
-- reasons, at different rates, and by different code paths -- an onboarding
-- rewrite should never be able to touch a password hash.
--
-- One exception, and it is a deliberate one: the weekly transportation budget
-- stays on `users`. It is not an onboarding answer that happens to be about
-- money, it is *the* budget the ledger, wallet, insights, and budget-pressure
-- weighting have always read. Copying it here would create a second truth that
-- could disagree with the first. Onboarding writes through to `users` instead.
-- What changes below is that the budget becomes NULLABLE, so "I'm not sure"
-- is representable as an absence rather than as a fictitious $0.00.

ALTER TABLE users
    ALTER COLUMN weekly_budget_cents DROP NOT NULL,
    ALTER COLUMN weekly_budget_cents DROP DEFAULT;

-- Existing rows keep whatever they had. A stored 0 means "this rider set zero",
-- which is different from NULL, "this rider has not told us yet". The check
-- constraint from V3 still holds: NULL passes a CHECK, negatives do not.

COMMENT ON COLUMN users.weekly_budget_cents IS
    'Canonical weekly transportation budget in integer cents. NULL means no budget set.';

CREATE TABLE user_travel_profiles
(
    id                        BIGSERIAL PRIMARY KEY,

    -- UNIQUE, not just a foreign key: a rider has exactly one travel profile.
    -- Enforced by the database so no code path can create a second one and make
    -- "the user's default preference" ambiguous.
    user_id                   BIGINT      NOT NULL UNIQUE
        REFERENCES users (id) ON DELETE CASCADE,

    -- The rider's standing optimization stance. Stored as the ContextProfile
    -- name; the weights themselves live in Java and are never persisted, so
    -- retuning a profile does not require a data migration.
    default_context_profile   TEXT        NOT NULL DEFAULT 'BALANCED',

    weekly_commute_frequency  TEXT        NULL,
    commute_kind              TEXT        NULL,

    -- A typical commute is stored as a resolved place, not free text: name plus
    -- coordinates plus the geocoder's id. "Newark" is a string; 40.7357,-74.1643
    -- is a place FareFlow can actually route from.
    typical_origin_name       TEXT        NULL,
    typical_origin_lat        DOUBLE PRECISION NULL,
    typical_origin_lon        DOUBLE PRECISION NULL,
    typical_origin_place_id   TEXT        NULL,

    typical_destination_name  TEXT        NULL,
    typical_destination_lat   DOUBLE PRECISION NULL,
    typical_destination_lon   DOUBLE PRECISION NULL,
    typical_destination_place_id TEXT     NULL,

    pass_preference           TEXT        NULL,

    onboarding_completed      BOOLEAN     NOT NULL DEFAULT FALSE,
    onboarding_completed_at   TIMESTAMPTZ NULL,

    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_profile_context CHECK (
        default_context_profile IN ('BALANCED', 'RUSH', 'SAVE_MONEY', 'FEWER_TRANSFERS')),

    CONSTRAINT chk_profile_frequency CHECK (
        weekly_commute_frequency IS NULL OR weekly_commute_frequency IN
        ('ONE_TO_TWO_DAYS', 'THREE_TO_FOUR_DAYS', 'FIVE_PLUS_DAYS', 'VARIES')),

    CONSTRAINT chk_profile_commute_kind CHECK (
        commute_kind IS NULL OR commute_kind IN ('WORK', 'SCHOOL', 'BOTH', 'NONE')),

    CONSTRAINT chk_profile_pass_preference CHECK (
        pass_preference IS NULL OR pass_preference IN
        ('PAY_PER_RIDE', 'WEEKLY_PASS', 'MONTHLY_PASS', 'NOT_SURE')),

    -- A place is all-or-nothing. Half a place -- a name with no coordinates --
    -- is exactly the ambiguous free text this table exists to avoid.
    CONSTRAINT chk_profile_origin_complete CHECK (
        (typical_origin_name IS NULL AND typical_origin_lat IS NULL AND typical_origin_lon IS NULL)
        OR (typical_origin_name IS NOT NULL AND typical_origin_lat IS NOT NULL
            AND typical_origin_lon IS NOT NULL)),

    CONSTRAINT chk_profile_destination_complete CHECK (
        (typical_destination_name IS NULL AND typical_destination_lat IS NULL AND typical_destination_lon IS NULL)
        OR (typical_destination_name IS NOT NULL AND typical_destination_lat IS NOT NULL
            AND typical_destination_lon IS NOT NULL)),

    CONSTRAINT chk_profile_origin_lat CHECK (
        typical_origin_lat IS NULL OR (typical_origin_lat BETWEEN -90 AND 90)),
    CONSTRAINT chk_profile_origin_lon CHECK (
        typical_origin_lon IS NULL OR (typical_origin_lon BETWEEN -180 AND 180)),
    CONSTRAINT chk_profile_destination_lat CHECK (
        typical_destination_lat IS NULL OR (typical_destination_lat BETWEEN -90 AND 90)),
    CONSTRAINT chk_profile_destination_lon CHECK (
        typical_destination_lon IS NULL OR (typical_destination_lon BETWEEN -180 AND 180)),

    -- The timestamp and the flag cannot disagree.
    CONSTRAINT chk_profile_completed_at CHECK (
        (onboarding_completed AND onboarding_completed_at IS NOT NULL)
        OR (NOT onboarding_completed AND onboarding_completed_at IS NULL))
);

-- Preferred modes are a set, so they get a table rather than a comma-joined
-- string or an array column. The primary key makes duplicates impossible and
-- the CHECK keeps the vocabulary closed -- both properties a delimited string
-- would have to re-implement in application code.
CREATE TABLE user_travel_profile_modes
(
    profile_id BIGINT NOT NULL REFERENCES user_travel_profiles (id) ON DELETE CASCADE,
    mode       TEXT   NOT NULL,

    PRIMARY KEY (profile_id, mode),
    CONSTRAINT chk_profile_mode CHECK (
        mode IN ('TRAIN', 'SUBWAY', 'BUS', 'FERRY', 'WALKING'))
);

CREATE INDEX idx_profile_modes_profile ON user_travel_profile_modes (profile_id);
