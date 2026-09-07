# FareFlow

FareFlow is a full-stack transit and financial planning platform that helps riders
find public-transit routes, compare time and cost trade-offs, track a trip stop by
stop, and understand their transportation spending.

Unlike ticket-based systems, FareFlow uses a configurable **stop-based fare
engine**. Charges are calculated from confirmed travel progress, service type,
transfers, rider eligibility, and fare caps. Waiting time and delays never increase
the fare.

Each confirmed stop is checked against the rider's device location and graded
**verified**, **unverified**, or **flagged** — but the grade never changes the
fare. A rider underground, out of battery, or declining the permission is not a
fare evader, and refusing to advance them would strand them in a trip they could
not end or pay for. What verification buys is an auditable difference between a
trip confirmed on the platform and one confirmed from a kilometre away.

Fares settle through the FareFlow wallet, a simulated card, or a **demo
RLUSD-coded asset on the XRP Ledger** — the one rail whose receipt a rider can
verify without trusting FareFlow at all.

> FareFlow pricing and payments are product simulations, not official agency fares
> or claims of agency acceptance.

![FareFlow trip planning and route comparison](docs/screenshots/plan-map.png)

## Features

- Address-to-address transit planning through Google Routes, imported GTFS data,
  and a curated offline network with 22 stops across 9 lines.
- Context-aware route ranking for balanced, fastest, cheapest, and fewer-transfer
  preferences.
- Interactive maps with route lines, stop markers, current-stop progress, and
  provider-backed stop names.
- Stop-based pricing for buses, provider-labeled express buses, rail, subway,
  light rail, and ferries.
- Boarding charges, per-stop charges, operator transfer credits, student/senior/
  reduced fares, and daily or weekly caps.
- Skipped-stop, route-diversion, transfer, and early-trip-ending support.
- Location verification of each confirmed stop, graded and recorded per fare
  event, which never blocks a rider or changes what they are charged.
- Demo RLUSD settlement on the XRP Ledger, with custodial testnet accounts,
  encrypted key material, and a public transaction hash on every receipt.
- Secure multi-user registration and JWT authentication with isolated profiles,
  trips, budgets, wallets, and payment histories.
- Append-only fare events and ledger entries for auditable trip charges.
- Personalized spending insights and an optional Gemini-powered assistant.

## Screenshots

<table>
  <tr>
    <td><img src="docs/screenshots/login.png" alt="FareFlow login page"></td>
    <td><img src="docs/screenshots/insights-personalized.png" alt="FareFlow spending insights"></td>
  </tr>
  <tr>
    <td align="center"><strong>Multi-user authentication</strong></td>
    <td align="center"><strong>Personalized spending insights</strong></td>
  </tr>
</table>

## Technology

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, ECharts, Google Maps JavaScript API |
| Backend | Java 21, Spring Boot 3.5, Spring Security, JWT, Maven |
| Settlement | XRP Ledger via xrpl4j (server-side signing), xrpl.js (browser verification) |
| Data | PostgreSQL 17, Spring Data JPA, Flyway |
| Routing | Google Routes API, Google Places API, GTFS Schedule/Realtime, curated fallback graph |
| Testing | JUnit, Spring Boot Test, Vitest, Testing Library |

## Architecture

```text
React client
    |
    v
Spring Boot REST API
    |-- authentication and user profiles
    |-- route discovery and recommendation scoring
    |-- stop-based fare and transit-session lifecycle
    |-- payments, append-only ledger, wallet, and insights
    v
PostgreSQL + Flyway migrations
```

Financial values use integer cents end to end. Route providers supply transit
facts, while FareFlow owns recommendation scoring, stop-based pricing, payments,
history, and personalization.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+
- PostgreSQL 17

On macOS:

```bash
brew install openjdk@21 maven postgresql@17
brew services start postgresql@17
```

## Local setup

### 1. Create the databases

```bash
psql postgres -c "CREATE ROLE fareflow WITH LOGIN PASSWORD 'fareflow_dev_password';"
psql postgres -c "CREATE DATABASE fareflow OWNER fareflow;"
psql postgres -c "CREATE DATABASE fareflow_test OWNER fareflow;"
```

Flyway creates and validates the schema when the backend starts.

### 2. Configure the backend

Create a gitignored `.env` in the repository root:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/fareflow
DB_USERNAME=fareflow
DB_PASSWORD=fareflow_dev_password
SERVER_PORT=8080

FAREFLOW_AUTH_ENABLED=true
JWT_SECRET=paste-a-random-secret-with-at-least-32-bytes-here
JWT_EXPIRATION=86400

# Optional integrations
# One Google Maps Platform key backs tiles, place search, and transit routing.
# Enable Maps JavaScript API, Places API (New), and Routes API on it.
GOOGLE_MAPS_API_KEY=
GEMINI_API_KEY=

# Optional: RLUSD settlement on the XRP Ledger.
# All five are required together; with any missing the rail reports itself
# unavailable and wallet and card payments are unaffected.
XRPL_NETWORK=TESTNET
XRPL_RLUSD_ISSUER=
XRPL_ISSUER_SEED=
XRPL_TREASURY_ADDRESS=
XRPL_CUSTODY_KEY=
```

Generate a local JWT secret with `openssl rand -base64 48`. Authentication mode
supports separate user accounts; do not set `FAREFLOW_AUTH_ENABLED=false` when
testing multi-user behavior.

`GOOGLE_MAPS_ROUTES_API_KEY` still works if you prefer a separate key for the
Routes API; without it, routing falls back to `GOOGLE_MAPS_API_KEY`.

### 3. Configure the frontend

```bash
cd frontend
cp .env.example .env
```

Add `VITE_GOOGLE_MAPS_API_KEY` to `frontend/.env` for map tiles. Without a key,
FareFlow uses a schematic route map and everything else keeps working. The backend
imports this file too, so the same key backs server-side place search.

### 4. Optional — enable demo RLUSD settlement

The XRP Ledger rail is off unless all five `XRPL_*` values are set. To turn it on:

1. Generate a custody key: `openssl rand -base64 32` → `XRPL_CUSTODY_KEY`.
   This encrypts each rider's seed entropy at rest, so a leaked database row is
   not by itself enough to move funds.
2. Run `cd scripts/xrpl-bootstrap && npm install && npm start`. This `xrpl.js`
   utility creates a testnet-only demo issuer and treasury, enables issuer
   rippling, and establishes the treasury trustline. The issued asset uses the
   RLUSD currency code but is not Ripple-issued RLUSD and has no value.
3. Copy its `XRPL_RLUSD_ISSUER`, `XRPL_ISSUER_SEED`, and
   `XRPL_TREASURY_ADDRESS` output into `.env`. The issuer seed is a testnet
   signing key and must never be committed.

Rider accounts are created lazily, the first time someone picks the rail, and
funded from the public faucet. A rider who never chooses it never has a key held
on their behalf.

**Testnet only, deliberately.** `XRPL_NETWORK` accepts `TESTNET` or `DEVNET` and
nothing else, enforced in both configuration and the database. This holds riders'
signing keys, and custodial settlement of a real stablecoin is a regulated
activity that a product documented as a simulation must not perform.

### 5. Run FareFlow

Backend — <http://localhost:8080>:

```bash
cd backend
mvn spring-boot:run
```

Spring imports the gitignored repository `.env` automatically in local
development, so XRPL and other optional integrations do not depend on how the
process was launched. Deployed environments should continue to use real
environment variables or a secrets manager.

Frontend — <http://localhost:5173>:

```bash
cd frontend
npm install
npm run dev
```

Verify the backend:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/auth/config
```

## Main API endpoints

Protected endpoints require `Authorization: Bearer <token>`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Application health |
| `GET` | `/api/auth/config` | Authentication mode |
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Sign in and receive a JWT |
| `GET` | `/api/auth/me` | Current authenticated user |
| `GET` | `/api/locations?q=` | Address and place autocomplete |
| `GET` | `/api/journeys?from=&to=&profile=` | Find and rank transit journeys |
| `POST` | `/api/transit-sessions` | Start a stop-based trip session |
| `POST` | `/api/transit-sessions/{id}/advance` | Record a reached, skipped, or diverted stop, optionally with the rider's position |
| `POST` | `/api/transit-sessions/{id}/end` | End the trip at its actual stop |
| `POST` | `/api/transit-sessions/{id}/pay` | Pay the final simulated fare |
| `GET/PUT` | `/api/profile` | Read or update travel preferences |
| `GET` | `/api/trips` | Paginated trip history |
| `GET` | `/api/wallet` | Budget, balance, and recent activity |
| `GET` | `/api/ledger` | Append-only charge and refund history |
| `GET` | `/api/insights` | Personalized spending analytics |
| `GET` | `/api/payments/rails` | Which payment rails this deployment can complete |
| `POST` | `/api/assistant/ask` | Ask the optional FareFlow assistant |

API errors use RFC 9457 Problem Details. Financial commands use server-owned
pricing and idempotency keys where duplicate submission could move money.

## Tests

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run typecheck
cd frontend && npm run build
```

Integration tests use `fareflow_test`; they do not depend on production or local
application data.

## Additional documentation

- [Authentication](docs/AUTH.md)
- [Map and route geometry](docs/MAP.md)
- [Transit data and GTFS](docs/TRANSIT_DATA.md)
- [Product scope](docs/PRODUCT_SCOPE.md)
