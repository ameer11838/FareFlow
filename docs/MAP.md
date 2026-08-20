# Map architecture

The Plan Trip page is map-first. This document records what is real, what is
approximate, and why the boundaries sit where they do.

## The constraint that shaped everything

**TomTom's Routing API has no public-transit mode.** It supports car, truck,
pedestrian, and bicycle. It cannot return the shape of a PATH train or an NJ Transit
rail journey.

That leaves three options:

1. Ask TomTom for a *driving* route and draw it as if it were the train. **Rejected** —
   it would be a lie rendered at GPS precision.
2. Invent plausible-looking coordinates. **Rejected** — fabricated data.
3. Model geometry as transit data we own, from real published station coordinates,
   and label the connecting lines as schematic. **Chosen.**

## Four separate layers

| Layer | Owns | Where |
| --- | --- | --- |
| **Transit & fare data** | routes, fares, durations, transfers | `route/`, `route/provider/` |
| **Route geometry** | ordered station coordinates | `transit_route_waypoints`, `TransitRouteData.waypoints` |
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
- Station coordinates. Newark Penn Station, Journal Square, Grove Street, Exchange
  Place, World Trade Center, Hoboken Terminal, Port Authority, Secaucus Junction,
  New York Penn Station, Princeton Junction, and the PATH 33rd Street line stops are
  actual published locations, accurate to roughly 10cm of stated precision.
- TomTom basemap tiles, when a key is configured.

**Approximate, and labelled as such:**
- The lines *between* stations. These are straight segments, not track geometry.
  Every route carries `geometry.source = "SCHEMATIC"`, the UI renders unselected
  routes dashed, and the detail panel says so in words.

**Mocked FareFlow data (unchanged from Phase 1):**
- Fares, durations, and transfer counts. Seeded fixtures, not live agency feeds.

## Upgrading to real geometry

`geometry_source` already allows `SURVEYED`. A `GtfsTransitRouteProvider` that loads
`shapes.txt` would populate the same `transit_route_waypoints` structure and set that
value. Nothing in the map layer, the DTOs, or the engine would change — the frontend
would simply stop rendering the "schematic corridor" footnote.

See [TRANSIT_DATA.md](TRANSIT_DATA.md) for what that costs.

## The TomTom key

```bash
# frontend/.env  (gitignored)
VITE_TOMTOM_API_KEY=your-key-here
```

Get one free at <https://developer.tomtom.com/> — the **Maps SDK for Web** product.
The free tier covers development use comfortably.

**Without a key the page still works.** `isMapAvailable()` returns false and
`SchematicMap` renders instead: same real coordinates, same selection interaction,
same route cards, same recommendations — just no street basemap. A missing map key
degrades the view, not the product.

The SDK is loaded with a dynamic `import()` so its ~800KB stays out of the initial
bundle for users who never open Plan Trip.

## What a key would add

| Capability | Needs a key? | Status |
| --- | --- | --- |
| Basemap tiles, zoom, pan | **Yes** | implemented, waiting on key |
| Origin / destination markers | Yes (rendered on the map) | implemented |
| Route polylines | Yes | implemented |
| Fit viewport to journey | Yes | implemented |
| Click a line to select a route | Yes | implemented |
| Schematic fallback | No | working now |
| Geocoding free-text places | Yes, **and not implemented** | see below |

**Not implemented, and it needs the key to build meaningfully:** TomTom Search /
Geocoding to turn arbitrary typed text ("Grand Central") into coordinates. Today the
origin and destination pickers are constrained to the seeded location list, so
geocoding would add nothing until the catalog covers more places. It is the natural
next step once real transit data lands.
