-- The one identity demo mode uses.
--
-- No password hash, so it cannot be logged into even if auth is enabled and
-- someone guesses the email. Demo mode resolves to this row server-side.

INSERT INTO users (name, email, weekly_budget_cents, timezone, role, is_demo)
VALUES ('Ameer Demo', 'demo@fareflow.app', 5000, 'America/New_York', 'USER', TRUE)
ON CONFLICT DO NOTHING;
