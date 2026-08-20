-- Ordered geographic waypoints for each transit route.
--
-- WHY THIS LIVES IN OUR DATA LAYER AND NOT IN THE MAP PROVIDER:
-- TomTom's Routing API supports car, truck, pedestrian, and bicycle modes. It has
-- no public-transit routing, so it cannot return the shape of a PATH or NJ Transit
-- journey. Rather than draw a driving route and mislabel it as transit, route
-- geometry is modelled here as data owned by the transit layer.
--
-- The waypoints seeded in V7 are REAL, publicly documented station coordinates.
-- The straight segments a client draws between them are SCHEMATIC -- an indicative
-- corridor, not surveyed track geometry. Clients are told which via the
-- geometry_source column so nothing is presented as more precise than it is.
--
-- Replacing this with true shapes means loading GTFS `shapes.txt`, at which point
-- a GtfsTransitRouteProvider populates the same structure and geometry_source
-- becomes SURVEYED. See docs/TRANSIT_DATA.md.

CREATE TABLE transit_route_waypoints
(
    id               BIGSERIAL PRIMARY KEY,
    transit_route_id BIGINT       NOT NULL REFERENCES transit_routes (id) ON DELETE CASCADE,
    sequence         INTEGER      NOT NULL,
    name             TEXT         NOT NULL,
    -- Geographic coordinates, not money: NUMERIC here is about angular precision,
    -- and the integer-cents rule does not apply.
    latitude         NUMERIC(9, 6) NOT NULL,
    longitude        NUMERIC(9, 6) NOT NULL,

    CONSTRAINT uq_waypoint_sequence UNIQUE (transit_route_id, sequence),
    CONSTRAINT chk_waypoint_sequence_non_negative CHECK (sequence >= 0),
    CONSTRAINT chk_waypoint_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_waypoint_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_waypoint_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE INDEX idx_waypoints_route ON transit_route_waypoints (transit_route_id, sequence);

-- How trustworthy a route's geometry is. SCHEMATIC = straight lines between real
-- stops. SURVEYED would mean an actual agency shape from GTFS.
ALTER TABLE transit_routes
    ADD COLUMN geometry_source TEXT NOT NULL DEFAULT 'SCHEMATIC';

ALTER TABLE transit_routes
    ADD CONSTRAINT chk_transit_routes_geometry_source
        CHECK (geometry_source IN ('SCHEMATIC', 'SURVEYED', 'NONE'));
