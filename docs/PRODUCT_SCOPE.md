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

`PLAN → COMPARE → CHOOSE → PAY → TRAVEL → TRACK → PERSONALIZE`

Each stage should make the next stage more reliable:

1. Routing providers return sourced public-transit itineraries. Missing schedules,
   fares, stops, or real-time fields remain unavailable; FareFlow does not infer them.
2. Deterministic Java services calculate fares, route scores, budget impact, and
   payment amounts.
3. A route choice creates an idempotent payment intent. Settlement creates the
   trip and append-only charge in the same transaction.
4. Trips and ledger movements power Wallet, analytics, and personalization.
5. Ask FareFlow interprets intent and context but never calculates money or
   initiates a financial transaction.

## Coverage rule

Coverage expands only through a reliable transit provider, such as validated GTFS
and GTFS-Realtime feeds or a transit-routing API with an explicit data source.
FareFlow does not synthesize schedules or widen coverage with guessed service.

## Payment boundary

The browser identifies a route and a payment method; it never supplies the fare.
FareEngine recalculates the authoritative amount before a payment intent exists.
Payment states are `CREATED`, `AUTHORIZED`, `PROCESSING`, `SETTLED`, `FAILED`, and
`REFUNDED`. Ledger entries remain immutable, signed integer cents.
