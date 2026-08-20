-- Application users.
--
-- Note what is absent: there is no `total_spent` or `weekly_spend` column.
-- Weekly spending is always derived by summing ledger_entries. A mutable total
-- can drift, cannot be audited, cannot be corrected, and races under concurrency.
-- Store facts, derive views.

CREATE TABLE users
(
    id                  BIGSERIAL PRIMARY KEY,
    name                TEXT        NOT NULL,
    email               TEXT        NOT NULL UNIQUE,
    weekly_budget_cents BIGINT      NOT NULL DEFAULT 0,
    -- "This week" is meaningless without a timezone: Monday 00:00 in New York is
    -- a different instant than Monday 00:00 in London.
    timezone            TEXT        NOT NULL DEFAULT 'America/New_York',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_users_budget_non_negative CHECK (weekly_budget_cents >= 0),
    CONSTRAINT chk_users_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_users_email_not_blank CHECK (length(btrim(email)) > 0)
);
