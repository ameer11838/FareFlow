-- Mock transit data for Phase 1.
--
-- Fares are integer cents: $6.25 -> 625, $3.00 -> 300, $2.90 -> 290.
--
-- Three origin/destination pairs, chosen to exercise different code paths:
--   Newark    -> Manhattan : three routes, the canonical demo case
--   Hoboken   -> Manhattan : three routes including a transfer and a ferry
--   Princeton -> Manhattan : ONE route only, which exercises the rule that
--                            "saved vs. fastest route" is NULL when the user
--                            had no alternative to choose from.

INSERT INTO transit_routes (origin, destination, provider, mode, duration_minutes, fare_cents, transfers)
VALUES
    -- Newark -> Manhattan
    ('Newark', 'Manhattan', 'NJ_TRANSIT', 'RAIL', 22, 625, 0),
    ('Newark', 'Manhattan', 'PATH', 'SUBWAY', 38, 300, 0),
    ('Newark', 'Manhattan', 'NYC_BUS', 'BUS', 55, 290, 0),

    -- Hoboken -> Manhattan
    ('Hoboken', 'Manhattan', 'PATH', 'SUBWAY', 15, 300, 0),
    ('Hoboken', 'Manhattan', 'NY_WATERWAY', 'FERRY', 12, 900, 0),
    ('Hoboken', 'Manhattan', 'NJ_TRANSIT', 'BUS', 30, 350, 1),

    -- Princeton -> Manhattan (single option)
    ('Princeton', 'Manhattan', 'NJ_TRANSIT', 'RAIL', 85, 1875, 1);
