-- Real, publicly documented station coordinates for the seeded routes.
--
-- Sources are public transit facilities; the coordinates identify actual stations.
-- The lines drawn between consecutive stops are schematic (see V6).

-- Route 1: NJ Transit rail, Newark -> Manhattan (22 min, $6.25)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Newark Penn Station', 40.735657, -74.164306),
            (1, 'Secaucus Junction', 40.761600, -74.075700),
            (2, 'New York Penn Station', 40.750568, -73.993519)) AS s(seq, name, lat, lng)
WHERE origin = 'Newark' AND destination = 'Manhattan' AND provider = 'NJ_TRANSIT';

-- Route 2: PATH, Newark -> Manhattan (38 min, $3.00)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Newark Penn Station', 40.735657, -74.164306),
            (1, 'Harrison', 40.739400, -74.155400),
            (2, 'Journal Square', 40.732900, -74.063500),
            (3, 'Grove Street', 40.719600, -74.043200),
            (4, 'Exchange Place', 40.716300, -74.033300),
            (5, 'World Trade Center', 40.712600, -74.011300)) AS s(seq, name, lat, lng)
WHERE origin = 'Newark' AND destination = 'Manhattan' AND provider = 'PATH';

-- Route 3: NYC Bus, Newark -> Manhattan (55 min, $2.90)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Newark Penn Station', 40.735657, -74.164306),
            (1, 'Route 1 & 9 / Jersey City', 40.748000, -74.110000),
            (2, 'Lincoln Tunnel Helix', 40.766000, -74.018000),
            (3, 'Port Authority Bus Terminal', 40.757000, -73.990300)) AS s(seq, name, lat, lng)
WHERE origin = 'Newark' AND destination = 'Manhattan' AND provider = 'NYC_BUS';

-- Route 4: PATH, Hoboken -> Manhattan (15 min, $3.00)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Hoboken Terminal', 40.735800, -74.027100),
            (1, 'Christopher Street', 40.733300, -74.007100),
            (2, '9th Street', 40.734300, -73.998700),
            (3, '14th Street', 40.737600, -73.996700),
            (4, '23rd Street', 40.742900, -73.992800),
            (5, '33rd Street', 40.748500, -73.988000)) AS s(seq, name, lat, lng)
WHERE origin = 'Hoboken' AND destination = 'Manhattan' AND provider = 'PATH';

-- Route 5: NY Waterway ferry, Hoboken -> Manhattan (12 min, $9.00)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Hoboken Terminal', 40.735800, -74.027100),
            (1, 'Midtown / W 39th St Ferry Terminal', 40.762000, -74.002100)) AS s(seq, name, lat, lng)
WHERE origin = 'Hoboken' AND destination = 'Manhattan' AND provider = 'NY_WATERWAY';

-- Route 6: NJ Transit bus, Hoboken -> Manhattan (30 min, $3.50, 1 transfer)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Hoboken Terminal', 40.735800, -74.027100),
            (1, 'Lincoln Tunnel Helix', 40.766000, -74.018000),
            (2, 'Port Authority Bus Terminal', 40.757000, -73.990300)) AS s(seq, name, lat, lng)
WHERE origin = 'Hoboken' AND destination = 'Manhattan' AND provider = 'NJ_TRANSIT';

-- Route 7: NJ Transit rail, Princeton -> Manhattan (85 min, $18.75, 1 transfer)
INSERT INTO transit_route_waypoints (transit_route_id, sequence, name, latitude, longitude)
SELECT id, s.seq, s.name, s.lat, s.lng FROM transit_routes,
    (VALUES (0, 'Princeton Junction', 40.316600, -74.622900),
            (1, 'Newark Penn Station', 40.735657, -74.164306),
            (2, 'New York Penn Station', 40.750568, -73.993519)) AS s(seq, name, lat, lng)
WHERE origin = 'Princeton' AND destination = 'Manhattan' AND provider = 'NJ_TRANSIT';
