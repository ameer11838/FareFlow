# GTFS transit infrastructure

FareFlow has a provider-neutral GTFS Schedule and GTFS-Realtime layer for public
transit only: train, subway/light rail, bus, and ferry. Walking exists solely as
access, egress, or a transfer between transit legs.

## Truth and coverage model

A feed in `gtfs_feeds` is only **configured** until its archive has passed validation
and imported atomically. `GET /api/transit/coverage` exposes the distinction, the
service-date window, agencies, modes, record counts, import time, and whether a live
overlay is currently fresh. The app never treats an advertised URL as proof of
coverage.

The initial official publisher registry contains:

| Region | Feed | Schedule | Trip updates |
| --- | --- | --- | --- |
| Greater Boston | MBTA | `cdn.mbta.com/MBTA_GTFS.zip` | MBTA GTFS-Realtime |
| Chicago | CTA | CTA `google_transit.zip` | not configured |
| San Francisco Bay Area | BART | BART `google_transit.zip` | BART GTFS-Realtime |

These rows begin as `CONFIGURED`; they become `READY` only after an actual import.

## Data flow

```text
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

## Fares and payments

GTFS routing does not turn an absent fare into zero. Until an agency has an
authoritative FareEngine policy, its result carries `UNKNOWN` fare status and cannot
create a paid PaymentIntent. Schedule expansion therefore cannot bypass FareFlow's
server-side pricing or financial controls.

## Geometry boundary

Every transit waypoint is an official GTFS stop coordinate. FareFlow does not yet
ingest `shapes.txt`, so the map connects those waypoints schematically and does not
claim track-accurate geometry.
