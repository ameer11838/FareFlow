# Replacing mock routes with real transit data

FareFlow reads routes through `TransitRouteProvider`, so the recommendation engine
does not know or care where a route came from. This document records what is already
built and exactly what is still needed.

## What exists today

```
com.fareflow.route.provider
├── TransitRouteProvider          interface: sourceName, supports, findRoutes, knownOrigins/Destinations
├── TransitRouteData              plain value type — no JPA, so any source can produce it
├── DatabaseTransitRouteProvider  @Order(100), reads the Flyway-seeded transit_routes table
└── TransitRouteCatalog           consults providers in @Order sequence, first match wins
```

`TransitRouteCatalog` falls through when a provider claims support but returns nothing,
which is the realistic failure mode for a live feed that is up but has no data for a
given pair. That behaviour is covered by `TransitRouteCatalogTest`.

**Adding a live provider requires no change to the engine, the service, or the
controller.** Implement the interface, annotate `@Component` with an `@Order` below
100, and it takes precedence automatically. The database provider stays in place as
the development and test source.

## What is NOT built, and why

No live integration is implemented, because every option for the NY/NJ region either
needs credentials I do not have or needs a data pipeline well beyond a provider class.

### Option A — GTFS static feeds (no per-request credentials)

| Agency | Feed | Access |
| --- | --- | --- |
| MTA (subway/bus) | `gtfs_subway.zip`, borough bus feeds | public download, no key |
| NJ Transit (rail/bus) | GTFS rail + bus | **free developer account required** to download |
| PATH | GTFS via PANYNJ | public download |

GTFS static gives scheduled times and route structure. It does **not** give fares in a
usable form for our model: `fare_attributes.txt` / `fare_rules.txt` are optional, and
PATH/NJ Transit zone fares would need to be modelled by hand.

The real work here is not the HTTP call — it is:

1. Downloading and unzipping multi-hundred-megabyte feeds on a schedule.
2. Loading `stops`, `routes`, `trips`, `stop_times`, `calendar` into Postgres.
3. Implementing a **journey planner** (RAPTOR or CSA) over that data to turn
   "Newark → Manhattan" into concrete itineraries with transfer counts.
4. Mapping each itinerary to a fare using hand-modelled agency rules.

That is a multi-week project and a phase of its own, not a provider implementation.

### Option B — a routing API (credentials required)

| Service | What it gives | Blocker |
| --- | --- | --- |
| Google Routes API (TRANSIT mode) | itineraries, durations, transfers, some fares | API key + billing account |
| Transitland / Transitous | GTFS aggregation, routing | key for production rate limits |
| OpenTripPlanner (self-hosted) | full journey planning | needs a server and a GTFS load |

**To integrate any of these, I need from you:** an API key, and confirmation of which
service you want to pay for. None of them work anonymously at a usable rate limit.

### Option C — real-time arrivals only

MTA GTFS-Realtime feeds are now open without a key. These give live arrival
predictions but no origin-to-destination planning and no fares, so they would enrich
an existing itinerary rather than produce one. A reasonable later addition; not a
replacement for the catalog.

## Recommended sequence

1. **Fares first, still seeded.** Model real NJ Transit / PATH / MTA fare rules
   (zones, peak/off-peak, transfers, weekly caps) against the existing seeded routes.
   This is the highest-value work for a fintech portfolio and needs no external data.
2. **`GtfsTransitRouteProvider`** reading a locally loaded MTA + PATH GTFS feed, with
   a simple direct-route planner before attempting full RAPTOR.
3. **`RoutingApiTransitRouteProvider`** behind a feature flag, once a key exists.

## One schema note

`trips.transit_route_id` is a foreign key into `transit_routes`. A route that came
from a live feed has no row there, so taking such a trip must first persist it as a
cached catalog entry. The snapshot columns on `trips` already hold everything needed
for history and the ledger, so this is a small addition to `TripService` and one
migration — but it is not done yet, and taking a trip currently requires a database route.
