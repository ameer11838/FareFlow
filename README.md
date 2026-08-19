# FareFlow

FareFlow is an intelligent transit and fintech platform that helps commuters choose
transportation based on travel time, fare cost, personal budget, and situational context.

The core idea: the *best* route is not always the cheapest or the fastest one.

---

## Stack

| Layer    | Technology                              |
| -------- | --------------------------------------- |
| Backend  | Java 21 (LTS), Spring Boot 3.5, Maven   |
| Database | PostgreSQL 17, schema managed by Flyway |
| Frontend | React + TypeScript *(not started yet)*  |

---

## Current phase

**Phase 1 — M0: Environment + Skeleton**

Phase 1 milestones:

| Milestone | Scope                                    | Status         |
| --------- | ---------------------------------------- | -------------- |
| M0        | Environment, skeleton, health endpoint   | in progress    |
| M1        | Transit routes + optimization engine     | next           |
| M2        | Users                                    | not started    |
| M3        | Trips + financial ledger                 | not started    |
| M4        | Weekly budget + dashboard                | not started    |
| M5–M8     | React frontend, polish                   | not started    |

---

## Running the backend

**Prerequisites:** Java 21, Maven, PostgreSQL 17 running locally.

1. Create your local environment file (it is gitignored):

   ```bash
   cp .env.example .env
   ```

   Then set `DB_PASSWORD` in `.env` to your local database password.

2. Load the environment and start the server:

   ```bash
   cd backend
   set -a && source ../.env && set +a
   mvn spring-boot:run
   ```

   `set -a` exports every variable defined by the following `source`, which is how
   the values in `.env` reach Spring Boot as environment variables. Spring Boot does
   not read `.env` files on its own, and we deliberately avoid adding a library to
   make it do so.

3. Verify:

   ```bash
   curl http://localhost:8080/api/health
   # {"status":"UP"}
   ```

Run the tests:

```bash
cd backend && mvn test
```

---

## Configuration

All database configuration is read from environment variables. No credentials are
committed. `application.yml` provides defaults for non-secret values only —
`DB_PASSWORD` has no default, so the application fails fast if it is not set.

Flyway owns the database schema. Hibernate runs with `ddl-auto: validate` and is
never permitted to create or alter tables.

---

## Known limitations

These are deliberate Phase 1 scope decisions, not oversights:

- **No authentication.** Endpoints take a user id directly and do not verify identity.
  Spring Security + JWT is planned for a later phase.
- **Mock transit data.** Routes are seeded fixtures, not live transit APIs.
- **No AI.** Route selection is entirely deterministic Java. Natural-language context
  ("I'm running late") is a later phase, and even then the model will only produce
  sanitized optimization weights — it will never compute fares or write ledger entries.
- **No payments.** Fares are recorded in an internal ledger; no money actually moves.
- **Single-node, no infrastructure.** No Docker, Kafka, Redis, or cloud deployment yet.
