# Authentication and demo mode

FareFlow ships one codebase that runs in two modes, selected by a single flag.

| | Auth mode | Demo mode |
| --- | --- | --- |
| Backend | `FAREFLOW_AUTH_ENABLED=true` | `FAREFLOW_AUTH_ENABLED=false` |
| Frontend | `VITE_AUTH_ENABLED=true` | `VITE_AUTH_ENABLED=false` |
| `JWT_SECRET` | **required**, ≥32 bytes | not needed at all |
| Identity from | a verified JWT | the seeded demo row |
| Login screen | yes | never shown |

A flag rather than Spring profiles: the difference is one runtime decision, not a
different set of beans. Profiles would scatter the same choice across several
configuration classes and make the demo path harder to test.

## The security property that matters

**No endpoint accepts a user id from the browser for anything private.**

`CurrentUserService` is the only place the application decides who is asking. In
auth mode it reads the id from a verified token; in demo mode it loads the single
row with `is_demo = true`. Trips, ledger, wallet, insights, dashboard, and budget
all resolve identity through it.

That is why demo mode is safe to leave running: there is no `?userId=` to change,
no id in a request body, and no header that selects an account.
`DemoModeIntegrationTest.cannotImpersonate` proves it by creating a second user and
then trying a query parameter, a custom header, and a forged bearer token.

Endpoints changed shape to make this structural rather than a convention:

| Before | Now |
| --- | --- |
| `GET /api/users/{id}/trips` | `GET /api/trips` |
| `GET /api/users/{id}/ledger` | `GET /api/ledger` |
| `GET /api/users/{id}/dashboard` | `GET /api/dashboard` |
| `PATCH /api/users/{id}/budget` | `PATCH /api/users/me/budget` |
| `POST /api/trips` with `userId` in the body | `POST /api/trips`, body has no userId |
| `GET /api/users`, `GET /api/users/{id}` | **removed** — they leaked every account |

## Passwords

BCrypt, via Spring Security's `PasswordEncoder`. There is no column, DTO, log line,
or response shape anywhere that carries a plaintext password.
`AuthEnabledIntegrationTest` asserts the stored value starts with `$2`, does not
contain the password, and that the registration response body does not either.

Login verifies a hash even when the email does not exist, so a wrong address and a
wrong password take comparable time and return byte-identical responses. There is a
test asserting the two bodies are equal.

## The demo identity

Seeded by `V9__seed_demo_user.sql` as **Ameer Demo** with a $50 weekly budget and a
`NULL` password hash. A null hash means the account cannot authenticate *at all* —
not "no password". Even with auth enabled and the address known, logging in as the
demo user is impossible.

A partial unique index (`uq_users_single_demo`) enforces at most one demo row, so
"the demo identity" can never become ambiguous.

## Tokens

Stateless HS256 JWTs, subject = user id, default lifetime 24h. Logout is
client-side: the token is discarded. Server-side revocation needs a shared store
(Redis), which is out of scope for this phase.

The token is kept in `localStorage`, which is XSS-readable. That is the accepted
trade-off for a stateless API with no cookie/CSRF machinery; an httpOnly refresh
cookie is the correct next step.

A missing or short `JWT_SECRET` fails startup rather than signing with a guessable
key — `JwtService` validates it on construction, and the bean is only created when
auth is enabled.

## Running each mode

```bash
# Auth mode
cd backend
set -a && source ../.env && set +a          # needs JWT_SECRET
mvn spring-boot:run

cd frontend && VITE_AUTH_ENABLED=true npm run dev
```

```bash
# Demo mode -- no secret required
cd backend
set -a && source ../.env && set +a
FAREFLOW_AUTH_ENABLED=false mvn spring-boot:run

cd frontend && VITE_AUTH_ENABLED=false npm run dev
```

The frontend also calls `GET /api/auth/config` and trusts the server's answer over
its own build flag, so a mismatched pair degrades gracefully instead of breaking.
