-- Authentication columns.
--
-- password_hash is NULLABLE on purpose: users seeded for demo mode have no
-- password and can never be logged into. A null hash is not "no password" --
-- it means this account cannot authenticate at all, which is exactly what a
-- demo identity should be.
--
-- Passwords are stored only as BCrypt hashes. There is no column, log, or DTO
-- anywhere in the codebase that holds a plaintext password.

ALTER TABLE users
    ADD COLUMN password_hash TEXT NULL,
    ADD COLUMN role TEXT NOT NULL DEFAULT 'USER',
    -- Marks the single account demo mode resolves to. The backend owns this;
    -- the browser never chooses which user it is.
    ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

-- A BCrypt hash is always 60 characters; reject anything that clearly is not one.
ALTER TABLE users
    ADD CONSTRAINT chk_users_password_hash
        CHECK (password_hash IS NULL OR length(password_hash) >= 55);

-- At most one demo user. Enforced by the database so no code path can create a
-- second one and make "the demo identity" ambiguous.
CREATE UNIQUE INDEX uq_users_single_demo ON users (is_demo) WHERE is_demo;

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
