# FareFlow product scope

FareFlow is a public-transit transportation-finance product. It plans, compares,
pays for, and tracks journeys using:

- Train, including commuter and light rail
- Subway
- Bus
- Ferry
- Walking only as access, egress, or a transfer connection to public transit

Cars, taxis, rideshare, flights, bicycle routing, micromobility, and unrelated
financial products are outside the product boundary.

## Product loop

`FIND ROUTE → SELECT TRANSIT → START TRIP → TRACK → END TRIP → CALCULATE FARE → PAY → HISTORY`

Each stage should make the next stage more reliable:

1. Routing providers return sourced public-transit itineraries. Missing schedules,
   fares, stops, or real-time fields remain unavailable; FareFlow does not infer them.
2. Starting a selected route creates an idempotent transit session at $0. Live
   progress is used only when a provider supplies it; otherwise progress is
   explicitly rider-confirmed.
3. Each confirmed completed stop asks a deterministic Java fare engine to update
   the simulated usage fare from configured base, distance, stop, and mode rules.
   Distance is recognized at stop boundaries, not continuously. Ending the trip
   freezes that last stop-based amount. Time is recorded only as trip duration;
   waiting and delays never increase the charge. No recorded boarding or transit
   progress means no charge.
4. The completed session creates an idempotent payment intent. Settlement creates
   the trip and append-only charge in the same transaction.
5. Trips and payment activity power Wallet, analytics, and personalization.
6. Ask FareFlow interprets intent and context but never calculates money or
   initiates a financial transaction.

## Coverage rule

Google Routes `TRANSIT` is the primary U.S. discovery provider wherever Google
returns a public-transit itinerary. Validated GTFS/GTFS-Realtime feeds and the
curated network remain fallback/enrichment layers. Every result states its source;
FareFlow does not synthesize schedules or widen coverage with guessed service.

## Payment boundary

The browser identifies a route, confirms trip progress, and selects a payment
method; it never supplies the fare. FareEngine calculates the authoritative amount
from the server-owned transit session before a payment intent exists. Usage pricing
is a proposed FareFlow simulation and must never be described as an agency tariff,
agency acceptance, or an existing agency capability.
Payment states are `CREATED`, `AUTHORIZED`, `PROCESSING`, `SETTLED`, `FAILED`, and
`REFUNDED`. Ledger entries remain immutable, signed integer cents.
