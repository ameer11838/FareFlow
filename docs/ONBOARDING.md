# Onboarding and personalization

FareFlow asks a rider five short questions after they register, and then uses every
answer. That second half is the point: onboarding that collects data it never
consults is a survey, and riders can tell.

```
REGISTER / LOGIN
      ↓
SHORT PERSONALIZED ONBOARDING        five questions + a summary
      ↓
TRAVEL + FINANCIAL PROFILE           user_travel_profiles + users.weekly_budget_cents
      ↓
PLAN TRIP                            commute shortcut, pre-filled fields, framed map
      ↓
RECOMMENDATIONS USE THAT PROFILE     default stance, budget pressure, projections
```

---

## The data model

Two tables, and one column that deliberately stayed where it was.

### `user_travel_profiles`

One row per rider, enforced by a `UNIQUE` constraint on `user_id` rather than by
convention — "the rider's default preference" must not be able to become ambiguous.

| Column | Notes |
| --- | --- |
| `default_context_profile` | `BALANCED` / `RUSH` / `SAVE_MONEY` / `FEWER_TRANSFERS`. Stored by **name**; the weights live in `ContextProfile` and are never persisted, so retuning a profile needs no data migration. |
| `weekly_commute_frequency` | A band, not a number: `ONE_TO_TWO_DAYS`, `THREE_TO_FOUR_DAYS`, `FIVE_PLUS_DAYS`, `VARIES`. |
| `commute_kind` | `WORK` / `SCHOOL` / `BOTH` / `NONE`. |
| `typical_origin_{name,lat,lon,place_id}` | A resolved place, not free text. |
| `typical_destination_{…}` | Same. |
| `pass_preference` | `PAY_PER_RIDE` / `WEEKLY_PASS` / `MONTHLY_PASS` / `NOT_SURE`. |
| `onboarding_completed` + `_at` | A `CHECK` keeps the flag and the timestamp from disagreeing. |

Every answer is nullable, because every question can be skipped. An unanswered
question stays unanswered — nothing is back-filled with a plausible default, since
a fabricated commute would drive real recommendations.

### `user_travel_profile_modes`

Preferred modes are a **set**, so they get a table rather than a comma-joined
string or an array column. The composite primary key makes duplicates impossible
and a `CHECK` keeps the vocabulary closed — two properties a delimited string
would have to re-implement in application code.

### Where the budget lives

**`users.weekly_budget_cents`, exactly where it always was.** The travel profile
has no budget column.

That is the one field onboarding collects which already had an owner: the ledger,
wallet, insights, and budget-pressure weighting have all read it from `users`
since before onboarding existed. Copying it into the profile would have created a
second number that could disagree with the one money actually flows against.
Onboarding writes through to `users` instead.

What did change is that the column became **nullable**, so "I'm not sure" is
representable:

- `NULL` — no budget set. No remaining balance, no utilization, no budget
  pressure. Every surface asks for one.
- `0` — a budget of zero, which is a real (if unusual) answer.

The two are never conflated. `WeeklySummary.remainingCents` is `Long`, not `long`,
for exactly this reason: there is nothing to remain from.

### A place is all-or-nothing

A typical commute is stored as coordinates plus the geocoder's own id, not as the
string the rider typed. `"Newark"` is ambiguous — Newark NJ and Newark DE are both
real, and a geocoder can change its mind between sessions. Saving the candidate
saves the place.

Both the database (`chk_profile_origin_complete`) and the domain
(`TypicalPlace.of`) refuse a half-resolved place, and `setTypicalCommute` refuses
one end of a commute without the other.

---

## The API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/profile/options` | the vocabularies onboarding may offer (public; static) |
| `GET` | `/api/profile` | the caller's profile |
| `PUT` | `/api/profile` | settings edit — never re-opens onboarding |
| `PUT` | `/api/onboarding` | the onboarding submission — marks it complete |

**There is no `userId` anywhere in this contract.** Identity comes from the JWT via
`CurrentUserService`, exactly as it does for trips and the ledger, so "update
someone else's profile" is not a request the API can express — it is not a check
that can be forgotten. `OnboardingProfileIntegrationTest.profilesAreSelfScoped`
proves it by naming another rider in a body field, a header, and a query parameter
at once.

Two write endpoints for one operation is deliberate. They take the same document
and run the same validation; they differ only in meaning, which is what two
resources are for. Neither is a `PATCH`: onboarding and settings both render every
field, so a full replace has no ambiguity to resolve about omitted values.

The vocabularies are **served, not hardcoded in the client**, for the same reason
the scoring weights are: the backend owns what a choice means. A frontend that
invented its own "4 days/week" option would be inventing an input to a financial
projection.

---

## Precedence: current context beats stated habit

`TravelProfileService.resolveContextProfile` is the whole rule, in one method:

```
current request  >  onboarding default  >  BALANCED
```

A rider whose default is `SAVE_MONEY` gets cost-leaning results all week. The
moment they say "I'm in a rush" for one trip, that wins — a habit should never
outrank a situation.

```
GET /api/journeys?from=Philadelphia&to=Manhattan
    → profile SAVE_MONEY, costPriority 0.75      (their default)

GET /api/journeys?from=Philadelphia&to=Manhattan&profile=RUSH
    → profile RUSH, timePriority 0.75            (this trip only)
```

Nothing downstream changed: the weights, the scorer, and the ledger are the same
code they were. Only the stance *selection* moved.

The Plan page starts on the rider's saved stance rather than on `BALANCED`.
Without that, the client would send `BALANCED` on every search and quietly
override the very preference onboarding collected — the personalization would have
been real in the backend and invisible in the product.

---

## What the profile actually buys the rider

| Surface | Uses |
| --- | --- |
| **Plan** | Origin/destination pre-filled from the saved commute; a one-tap "Plan commute" shortcut with a return-trip direction; the map framed on the corridor before any search. |
| **Scoring** | The default stance feeds `PreferenceContext`, then budget pressure adjusts it as the week's spend rises. |
| **Insights** | Commute frequency × the rider's own average fare produces a weekly projection and a budget buffer, each shipped with the assumption behind it. |
| **Passes** | A pass is suggested only when it beats paying per ride at the rider's stated commute rate, and never to someone who already holds one. |
| **Wallet** | "Set a weekly budget" instead of `$0.00` when no budget exists. |

### The projection is arithmetic, not a guess

```
projected = max(spent so far, average fare × commuting days × 2 trips a day)
buffer    = weekly budget − projected
```

Both inputs are the rider's own numbers: their stated frequency and their observed
average fare from the ledger. The projection is floored at actual spend, because a
projection that comes in under the actual is not a projection, it is a
contradiction. Each figure is null unless every input exists, and the sentences the
UI renders are built server-side so the client never composes a financial claim.

The commuting-days number is the **low end** of each band, on purpose: every
projection built on it understates rather than overstates, so a pass suggestion
errs toward "keep paying per ride" rather than toward a sale.

---

## Demo mode

`V14__seed_demo_travel_profile.sql` gives the demo rider a finished profile:
Newark → Manhattan, work, 3–4 days a week, $50 a week, paying per ride, on
train/subway/bus.

A demo whose user is stuck on step one of onboarding demonstrates the onboarding,
not the product. Because the profile is complete, `needsOnboarding` is false and
demo mode never sees the flow — every personalized surface has real data from the
first page load.

The seeded place names and coordinates match the built-in gazetteer exactly
(`Newark`, `40.735657, -74.164306`, `static:newark`), so the saved commute plans as
a real journey with no second geocode and no chance of resolving somewhere
slightly different than it was saved.
