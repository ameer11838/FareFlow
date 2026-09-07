-- Stop-based charging asks the rider to confirm each boundary. This records what
-- their device said at that moment, so a trip's charges carry evidence rather than
-- assertion alone.
--
-- Verification never gates the fare. A rider underground, out of battery, or
-- refusing the permission is not a fare evader, and stranding them mid-trip with
-- an unpayable session would be a worse product than charging an unverified stop.
-- What this buys is an auditable difference between a trip confirmed on the
-- platform and one confirmed from a kilometre away.

ALTER TABLE transit_fare_events
    ADD COLUMN verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED_NO_FIX',
    ADD COLUMN verification_distance_metres DOUBLE PRECISION NULL,
    ADD COLUMN reported_accuracy_metres DOUBLE PRECISION NULL,
    -- Raw coordinates are kept only where a rider may need to dispute a flag.
    -- Storing every rider's exact position at every stop, forever, would be a
    -- surveillance record the product has no use for; the distance is what the
    -- ledger actually reasons about.
    ADD COLUMN reported_latitude DOUBLE PRECISION NULL,
    ADD COLUMN reported_longitude DOUBLE PRECISION NULL,
    ADD COLUMN verification_note TEXT NULL;

ALTER TABLE transit_fare_events
    ADD CONSTRAINT chk_transit_fare_event_verification CHECK
        (verification_status IN ('VERIFIED', 'UNVERIFIED_NO_FIX', 'UNVERIFIED_IMPRECISE',
                                 'UNVERIFIABLE_STOP', 'UNVERIFIED_TOO_FAR')),
    ADD CONSTRAINT chk_transit_fare_event_verification_distance CHECK
        (verification_distance_metres IS NULL OR verification_distance_metres >= 0),
    ADD CONSTRAINT chk_transit_fare_event_reported_accuracy CHECK
        (reported_accuracy_metres IS NULL OR reported_accuracy_metres >= 0),
    ADD CONSTRAINT chk_transit_fare_event_reported_point CHECK
        ((reported_latitude IS NULL) = (reported_longitude IS NULL)),
    ADD CONSTRAINT chk_transit_fare_event_reported_range CHECK
        (reported_latitude IS NULL
            OR (reported_latitude BETWEEN -90 AND 90
                AND reported_longitude BETWEEN -180 AND 180)),
    -- A stop cannot be verified without something to have measured.
    ADD CONSTRAINT chk_transit_fare_event_verified_has_distance CHECK
        (verification_status <> 'VERIFIED' OR verification_distance_metres IS NOT NULL);

-- Running tallies so a trip's integrity is readable without replaying its events.
ALTER TABLE transit_sessions
    ADD COLUMN verified_stop_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN contradicted_stop_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE transit_sessions
    ADD CONSTRAINT chk_transit_session_verification_counts CHECK
        (verified_stop_count >= 0
            AND contradicted_stop_count >= 0
            AND verified_stop_count + contradicted_stop_count <= progress_units_completed);

CREATE INDEX idx_transit_fare_events_unverified
    ON transit_fare_events (transit_session_id)
    WHERE verification_status = 'UNVERIFIED_TOO_FAR';
