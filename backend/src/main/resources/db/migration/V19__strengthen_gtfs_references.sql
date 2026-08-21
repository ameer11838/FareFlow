ALTER TABLE gtfs_routes
    ADD CONSTRAINT fk_gtfs_route_agency
    FOREIGN KEY (feed_id, agency_id)
    REFERENCES gtfs_agencies (feed_id, agency_id);

ALTER TABLE gtfs_service_exceptions
    ADD CONSTRAINT fk_gtfs_exception_service
    FOREIGN KEY (feed_id, service_id)
    REFERENCES gtfs_services (feed_id, service_id);

ALTER TABLE gtfs_transfers
    ADD CONSTRAINT fk_gtfs_transfer_from_stop
    FOREIGN KEY (feed_id, from_stop_id)
    REFERENCES gtfs_stops (feed_id, stop_id);

ALTER TABLE gtfs_transfers
    ADD CONSTRAINT fk_gtfs_transfer_to_stop
    FOREIGN KEY (feed_id, to_stop_id)
    REFERENCES gtfs_stops (feed_id, stop_id);
