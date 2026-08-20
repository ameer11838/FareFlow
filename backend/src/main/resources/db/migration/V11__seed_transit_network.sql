-- Real stations and real services across the Philadelphia <-> New York corridor.
--
-- Coordinates are published station locations. Durations are typical scheduled
-- running times, not a live timetable -- journeys are labelled accordingly.

INSERT INTO transit_stops (code, name, locality, latitude, longitude) VALUES
    -- Philadelphia
    ('PHL_30ST',   '30th Street Station',          'Philadelphia, PA', 39.955700, -75.182100),
    ('PHL_SUBURB', 'Suburban Station',             'Philadelphia, PA', 39.954200, -75.166500),
    -- New Jersey corridor
    ('NJ_TRENTON', 'Trenton Transit Center',       'Trenton, NJ',      40.218100, -74.754600),
    ('NJ_NWKPENN', 'Newark Penn Station',          'Newark, NJ',       40.735657, -74.164306),
    ('NJ_NWKBRD',  'Newark Broad Street',          'Newark, NJ',       40.747900, -74.172300),
    ('NJ_WARREN',  'Warren Street / NJIT',         'Newark, NJ',       40.742000, -74.178000),
    ('NJ_EWR',     'Newark Liberty Airport',       'Newark, NJ',       40.706100, -74.186600),
    ('NJ_SECAUC',  'Secaucus Junction',            'Secaucus, NJ',     40.761600, -74.075700),
    ('NJ_HARRISON','Harrison',                     'Harrison, NJ',     40.739400, -74.155400),
    ('NJ_JSQ',     'Journal Square',               'Jersey City, NJ',  40.732900, -74.063500),
    ('NJ_GROVE',   'Grove Street',                 'Jersey City, NJ',  40.719600, -74.043200),
    ('NJ_EXCHANGE','Exchange Place',               'Jersey City, NJ',  40.716300, -74.033300),
    ('NJ_HOBOKEN', 'Hoboken Terminal',             'Hoboken, NJ',      40.735800, -74.027100),
    -- Manhattan
    ('NY_PENN',    'New York Penn Station',        'Manhattan, NY',    40.750568, -73.993519),
    ('NY_WTC',     'World Trade Center',           'Manhattan, NY',    40.712600, -74.011300),
    ('NY_33ST',    '33rd Street',                  'Manhattan, NY',    40.748500, -73.988000),
    ('NY_14ST',    '14th Street',                  'Manhattan, NY',    40.737600, -73.996700),
    ('NY_TIMESSQ', 'Times Square - 42nd Street',   'Manhattan, NY',    40.755700, -73.987000),
    ('NY_PABT',    'Port Authority Bus Terminal',  'Manhattan, NY',    40.757000, -73.990300),
    ('NY_FULTON',  'Fulton Street',                'Manhattan, NY',    40.710400, -74.007700),
    -- Brooklyn
    ('BK_JAYST',   'Jay Street - MetroTech',       'Brooklyn, NY',     40.692300, -73.987300),
    ('BK_ATLANTIC','Atlantic Av - Barclays Center','Brooklyn, NY',     40.684400, -73.976500);

INSERT INTO transit_lines (code, name, agency, mode, fare_policy, headway_minutes) VALUES
    ('SEPTA_TRE',  'SEPTA Trenton Line',            'SEPTA',      'RAIL',       'SEPTA_REGIONAL_RAIL', 30),
    ('NJT_NEC',    'NJ Transit Northeast Corridor', 'NJ_TRANSIT', 'RAIL',       'NJT_ZONE_RAIL',       25),
    ('AMTRAK_NER', 'Amtrak Northeast Regional',     'AMTRAK',     'RAIL',       'AMTRAK_DYNAMIC',      60),
    ('PATH_NWK',   'PATH Newark - World Trade Center','PATH',     'SUBWAY',     'PATH_FLAT',            8),
    ('PATH_HOB33', 'PATH Hoboken - 33rd Street',    'PATH',       'SUBWAY',     'PATH_FLAT',           10),
    ('NYCT_1',     'NYC Subway 1 / 2 / 3',          'MTA',        'SUBWAY',     'MTA_FLAT',             5),
    ('NYCT_ACE',   'NYC Subway A / C / E',          'MTA',        'SUBWAY',     'MTA_FLAT',             5),
    ('NLR_BROAD',  'Newark Light Rail',             'NJ_TRANSIT', 'LIGHT_RAIL', 'NLR_FLAT',             8),
    ('NJT_EWR',    'NJ Transit / AirTrain EWR',     'NJ_TRANSIT', 'RAIL',       'NJT_AIRPORT',         15);

-- SEPTA Trenton Line: Philadelphia -> Trenton (~1h05)
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('PHL_SUBURB', 0, 0), ('PHL_30ST', 1, 4), ('NJ_TRENTON', 2, 65)) AS v(code, seq, mins)
WHERE l.code = 'SEPTA_TRE' AND s.code = v.code;

-- NJ Transit Northeast Corridor: Trenton -> New York Penn (~1h10)
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NJ_TRENTON', 0, 0), ('NJ_NWKPENN', 1, 47), ('NJ_SECAUC', 2, 57), ('NY_PENN', 3, 70))
    AS v(code, seq, mins)
WHERE l.code = 'NJT_NEC' AND s.code = v.code;

-- Amtrak Northeast Regional: Philadelphia -> New York (~1h25)
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('PHL_30ST', 0, 0), ('NJ_TRENTON', 1, 35), ('NJ_NWKPENN', 2, 68), ('NY_PENN', 3, 85))
    AS v(code, seq, mins)
WHERE l.code = 'AMTRAK_NER' AND s.code = v.code;

-- PATH Newark -> World Trade Center (~25 min)
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NJ_NWKPENN', 0, 0), ('NJ_HARRISON', 1, 4), ('NJ_JSQ', 2, 13),
            ('NJ_GROVE', 3, 17), ('NJ_EXCHANGE', 4, 20), ('NY_WTC', 5, 25)) AS v(code, seq, mins)
WHERE l.code = 'PATH_NWK' AND s.code = v.code;

-- PATH Hoboken -> 33rd Street (~15 min)
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NJ_HOBOKEN', 0, 0), ('NY_14ST', 1, 8), ('NY_33ST', 2, 15)) AS v(code, seq, mins)
WHERE l.code = 'PATH_HOB33' AND s.code = v.code;

-- NYC Subway 1/2/3: Penn Station <-> Times Square <-> Downtown <-> Brooklyn
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NY_TIMESSQ', 0, 0), ('NY_PENN', 1, 2), ('NY_14ST', 2, 7),
            ('NY_FULTON', 3, 16), ('BK_ATLANTIC', 4, 27)) AS v(code, seq, mins)
WHERE l.code = 'NYCT_1' AND s.code = v.code;

-- NYC Subway A/C/E: Port Authority <-> WTC <-> Brooklyn
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NY_PABT', 0, 0), ('NY_14ST', 1, 6), ('NY_WTC', 2, 14),
            ('NY_FULTON', 3, 16), ('BK_JAYST', 4, 21)) AS v(code, seq, mins)
WHERE l.code = 'NYCT_ACE' AND s.code = v.code;

-- Newark Light Rail: Newark Penn <-> Warren Street (NJIT) <-> Broad Street
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NJ_NWKPENN', 0, 0), ('NJ_WARREN', 1, 6), ('NJ_NWKBRD', 2, 9)) AS v(code, seq, mins)
WHERE l.code = 'NLR_BROAD' AND s.code = v.code;

-- Newark Airport <-> Newark Penn <-> New York Penn
INSERT INTO transit_line_stops (line_id, stop_id, sequence, minutes_from_start)
SELECT l.id, s.id, v.seq, v.mins FROM transit_lines l, transit_stops s,
    (VALUES ('NJ_EWR', 0, 0), ('NJ_NWKPENN', 1, 9), ('NY_PENN', 2, 34)) AS v(code, seq, mins)
WHERE l.code = 'NJT_EWR' AND s.code = v.code;
