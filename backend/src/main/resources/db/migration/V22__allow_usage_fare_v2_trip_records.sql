ALTER TABLE trips DROP CONSTRAINT chk_trip_fare_model;
ALTER TABLE trips ADD CONSTRAINT chk_trip_fare_model
    CHECK (fare_model IN ('FIXED', 'FAREFLOW_USAGE_V1', 'FAREFLOW_USAGE_V2'));
