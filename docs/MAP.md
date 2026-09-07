# Map architecture

The Plan Trip page is map-first. This document records what is real, what is
approximate, and why the boundaries sit where they do.

## Routing geometry sources

Google Maps is now the single mapping vendor: the Maps JavaScript API renders the
basemap, the Routes API supplies primary U.S. transit itineraries and step polylines
using `travelMode: TRANSIT`, and the Places API resolves typed place text. One
project, one key, one billing surface — and the place a rider searches for is
resolved by the same vendor that then routes from it.

Fallback geometry follows the original truth boundary:

1. Ask a road router for a *driving* route and draw it as if it were the train.
   **Rejected** — it would be a lie rendered at GPS precision.
2. Invent plausible-looking coordinates. **Rejected** — fabricated data.
3. Model fallback geometry from real published station coordinates and label the
   connecting lines as schematic. **Chosen for GTFS/curated fallback routes.**

## Four separate layers

| Layer | Owns | Where |
| --- | --- | --- |
| **Transit facts** | Google Routes, imported GTFS, curated fallback | `google/`, `gtfs/`, `discovery/` |
| **Comparison and usage fares** | provider quote when present; FareFlow session pricing | `fare/`, `session/` |
| **Recommendation logic** | scoring, labels, explanations | `recommendation/optimization/` (pure Java) |
| **Map rendering** | tiles, markers, lines, viewport | `features/plan/map/` (frontend only) |

The important invariant: **geometry never reaches the scorer.** `RouteCandidate` —
the type the optimization engine consumes — has no coordinate fields at all, so a
route's shape cannot influence whether it is recommended. `TransitRouteCatalogTest`
asserts this by reflection.

The map layer is equally narrow. `RouteMap` receives a list of routes and a selected
id; it draws coordinates and reports clicks. It does not know what a fare is.

## What is real vs. approximate

**Real:**
- Google Routes `TRANSIT` step polylines for Google-discovered journeys.
- Station coordinates. Newark Penn Station, Journal Square, Grove Street, Exchange
  Place, World Trade Center, Hoboken Terminal, Port Authority, Secaucus Junction,
  New York Penn Station, Princeton Junction, and the PATH 33rd Street line stops are
  actual published locations, accurate to roughly 10cm of stated precision.
- Google basemap tiles, when a key is configured.

**Approximate, and labelled as such:**
- Geometry between stops for curated and GTFS routes without a published shape.
  These routes connect real stop coordinates schematically and are rendered dashed.
- FareFlow's usage-based fare range. It is a product simulation and remains separate
  from an optional transit fare quote returned by Google.

**Provider-backed when returned:**
- Google route times, transit stops, line/operator facts, step geometry, and transit
  fare quotes. Missing fields remain missing; FareFlow does not infer them.

## Upgrading GTFS fallback geometry

`geometry_source` already allows `SURVEYED`. A `GtfsTransitRouteProvider` that loads
`shapes.txt` would populate the same `transit_route_waypoints` structure and set that
value. Nothing in the map layer, the DTOs, or the engine would change — the frontend
would simply stop rendering the "schematic corridor" footnote.

See [TRANSIT_DATA.md](TRANSIT_DATA.md) for what that costs.

## The Google Maps key

```bash
# frontend/.env  (gitignored)
VITE_GOOGLE_MAPS_API_KEY=your-key-here
VITE_GOOGLE_MAPS_MAP_ID=              # optional; DEMO_MAP_ID is used when blank
```

Create one in the Google Cloud console and enable **Maps JavaScript API**,
**Places API (New)**, and **Routes API** on it. The backend imports this same
file, so the one key also backs server-side place search.

Advanced markers are vector-only and need a map id; Google's `DEMO_MAP_ID` is used
when none is set. A styled id from the Cloud console themes the basemap without
touching any code.

**Without a key the page still works.** `isMapAvailable()` returns false and
`SchematicMap` renders instead: same real coordinates, same selection interaction,
same route cards, same recommendations — just no street basemap. A missing map key
degrades the view, not the product.

The SDK is loaded through Google's dynamic-library bootstrap, requested on demand,
so it stays out of the initial bundle for users who never open Plan Trip. Libraries
are imported individually (`maps`, `marker`) so a failure names the one that broke.

## What a key would add

| Capability | Needs a key? | Status |
| --- | --- | --- |
| Basemap tiles, zoom, pan | **Yes** | implemented, waiting on key |
| Origin / destination markers | Yes (rendered on the map) | implemented |
| Route polylines | Yes | implemented |
| Fit viewport to journey | Yes | implemented |
| Click a line to select a route | Yes | implemented |
| Schematic fallback | No | working now |
| Geocoding free-text places | Same key, server-side | implemented |

Google Places Text Search resolves arbitrary U.S. place text when
`GOOGLE_MAPS_API_KEY` is configured — text search rather than plain geocoding
because "NJIT" and "Times Square" are points of interest, not postal addresses,
and a rider types those far more often than a house number. Imported GTFS stops
and the built-in gazetteer remain the fallback search sources.
