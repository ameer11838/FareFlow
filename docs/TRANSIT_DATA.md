# Transit data infrastructure

Google Maps Routes API is FareFlow's primary U.S. route-discovery provider. The
backend calls `directions/v2:computeRoutes` with `travelMode: TRANSIT`, requests
alternatives, and normalizes public-transit and connecting-walk steps into the same
`Journey` model used everywhere else. Configure it with:

```dotenv
GOOGLE_MAPS_ROUTES_API_KEY=your-server-side-maps-key
```

The key must have the Google Maps Platform Routes API enabled. It is deliberately
separate from the Gemini key. Google route responses can supply transit endpoints,
times, headsigns, lines, operators, stop counts, detailed polylines, and an estimated
fare. Missing fields remain missing. FareFlow does not label provider times as live
because Routes API does not expose a per-step real-time provenance flag.

Google's fare, when present in USD, is comparison data only. FareFlow's simulated
stop-based pricing and all payment amounts remain deterministic server calculations.

GTFS remains the provider-neutral fallback and enrichment layer described below.

FareFlow has a provider-neutral GTFS Schedule and GTFS-Realtime layer for public
transit only: train, subway/light rail, bus, and ferry. Walking exists solely as
access, egress, or a transfer between transit legs.

## Truth and coverage model

A feed in `gtfs_feeds` is only **configured** until its archive has passed validation
and imported atomically. `GET /api/transit/coverage` exposes the distinction, the
service-date window, agencies, modes, record counts, import time, and whether a live
overlay is currently fresh. The app never treats an advertised URL as proof of
coverage.

Location search and map coverage use the same rule. `GET /api/locations` merges
nationwide U.S. geocoding with stops from `READY` feeds. Geocoded places can move
the map anywhere, but only candidates marked `source: GTFS` assert that FareFlow has
an imported schedule identity. `GET /api/transit/stops/nearby` returns map markers
only from ready feeds, including their stored modes, operators, lines, and whether
the live overlay is fresh. An unsupported area therefore shows a real basemap and
place location with no invented stops or timetable.

The initial official publisher registry contains:

| Region | Feed | Schedule | Trip updates |
| --- | --- | --- | --- |
| Greater Boston | MBTA | `cdn.mbta.com/MBTA_GTFS.zip` | MBTA GTFS-Realtime |
| Chicago | CTA | CTA `google_transit.zip` | not configured |
| San Francisco Bay Area | BART | BART `google_transit.zip` | BART GTFS-Realtime |

These rows begin as `CONFIGURED`; they become `READY` only after an actual import.

## Data flow

```text
Google Routes API (TRANSIT)
  → strict bus/train/subway/light-rail/ferry scope filter
  → normalized Journey alternatives and provider geometry
  → FareFlow fare comparison / optimizer / personalization

official ZIP
  → validate required GTFS tables and calendars
  → strict route_type allow-list
  → atomic normalized import
  → time-dependent multi-agency router
  → existing FareEngine / optimizer / personalization

official TripUpdates protobuf
  → atomic, expiring overlay
  → cancellation / stop update / propagated delay
  → scheduled route with clearly marked live facts
```

Identifiers are always scoped by `feed_id`; GTFS only promises uniqueness inside
one dataset. Normalized tables cover agencies, stops, routes, calendars and date
exceptions, trips, stop times, and transfers. Cross-feed transfer links can be
reviewed explicitly. The router also permits a conservative inferred walking link
when official stops from different ready feeds have the same normalized name and
are within 150 metres. That link supplies no schedule or fare.

Stop times with no explicit arrival or departure are not interpolated. Trips with
fewer than two explicit timepoints are excluded from routing. Missing real-time data
means “no live fact,” never “on time.” Live records expire automatically.

## Enabling synchronization

All downloading is opt-in so tests and local startup never depend on the network:

```dotenv
FAREFLOW_GTFS_SCHEDULE_ENABLED=true
FAREFLOW_GTFS_IMPORT_ON_STARTUP=true
FAREFLOW_GTFS_REALTIME_ENABLED=true
```

Static feeds refresh daily and live feeds every 30 seconds by default. Both periods
and the live freshness window are configurable in `application.yml`. A failed refresh
does not destroy the last successful schedule; a partial archive cannot commit.

## Adding another agency

Add one registry row with a stable `feed_key`, region metadata, the publisher's
official HTTPS Schedule URL, and optionally an official TripUpdates URL. No routing,
optimization, payment, ledger, AI, or personalization code changes are required.

Before enabling a feed, verify its license, service calendar, route types, and stop
coordinates. If separate feeds share a complex interchange, add reviewed rows to
`gtfs_inter_feed_transfers` rather than relying on a loose geographic guess.

No frontend map change is needed for a newly imported region. Its stops become
searchable and appear as nearby markers as soon as the feed reaches `READY`.

## Fares and payments

Neither Google nor GTFS turns an absent published fare into zero. A route can still
start a FareFlow usage-priced session because its eventual charge is calculated from
server-owned, confirmed stop progress—not from a frontend fare or missing provider
quote. Schedule expansion cannot bypass FareFlow's financial controls.

## Geometry boundary

Google journeys use the step polylines returned by Routes API. Every GTFS transit
waypoint is an official stop coordinate, but FareFlow does not yet ingest
`shapes.txt`; GTFS fallback routes therefore connect those stops schematically and
do not claim track-accurate geometry.
