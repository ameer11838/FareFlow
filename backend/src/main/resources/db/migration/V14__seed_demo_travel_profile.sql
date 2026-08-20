-- A completed profile for the demo identity.
--
-- Demo mode exists so someone can open FareFlow and immediately see what it
-- does. A demo user stuck on step 1 of onboarding would show them the opposite,
-- so the seeded rider arrives with a realistic profile already built: a regular
-- Newark to Manhattan work commute, four days a week, $50 a week, paying per
-- ride. Every personalized surface -- the commute shortcut on Plan, the budget
-- buffer in Insights, the default stance used for scoring -- has real data.

INSERT INTO user_travel_profiles (user_id,
                                  default_context_profile,
                                  weekly_commute_frequency,
                                  commute_kind,
                                  typical_origin_name,
                                  typical_origin_lat,
                                  typical_origin_lon,
                                  typical_origin_place_id,
                                  typical_destination_name,
                                  typical_destination_lat,
                                  typical_destination_lon,
                                  typical_destination_place_id,
                                  pass_preference,
                                  onboarding_completed,
                                  onboarding_completed_at)
SELECT u.id,
       'BALANCED',
       'THREE_TO_FOUR_DAYS',
       'WORK',
       -- Exactly the names, coordinates, and ids the built-in gazetteer returns,
       -- so the saved commute plans as a real journey with no second geocode and
       -- no chance of resolving somewhere slightly different than it was saved.
       'Newark', 40.735657, -74.164306, 'static:newark',
       'Manhattan', 40.758000, -73.985500, 'static:manhattan',
       'PAY_PER_RIDE',
       TRUE,
       now()
FROM users u
WHERE u.is_demo
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_travel_profile_modes (profile_id, mode)
SELECT p.id, m.mode
FROM user_travel_profiles p
         JOIN users u ON u.id = p.user_id
         CROSS JOIN (VALUES ('TRAIN'), ('SUBWAY'), ('BUS')) AS m(mode)
WHERE u.is_demo
ON CONFLICT DO NOTHING;

-- The demo budget matches what V9 already seeded on the user row. It is stated
-- here only as a comment: duplicating the value into this migration would be
-- the second source of truth the schema is designed to avoid.
