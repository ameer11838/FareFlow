-- Walking is a connection constraint, not a preferred transportation mode.
-- Remove the old option so the profile vocabulary matches the product boundary.
DELETE FROM user_travel_profile_modes WHERE mode = 'WALKING';

ALTER TABLE user_travel_profile_modes DROP CONSTRAINT chk_profile_mode;
ALTER TABLE user_travel_profile_modes ADD CONSTRAINT chk_profile_mode
    CHECK (mode IN ('TRAIN', 'SUBWAY', 'BUS', 'FERRY'));
