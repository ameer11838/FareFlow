# Design system

FareFlow's identity comes from one object: the app mark, whose plate runs cyan →
electric blue → violet → magenta on deep navy. Everything below is that mark
decomposed into a system.

![Login](screenshots/login.png)

---

## The two rules

Everything else follows from these.

**1. The gradient marks intent, never surface.** It appears on the app mark, the
primary action, and thin accents that indicate progress or selection — a 2px
hairline under the navigation, a 3px edge on a selected card, a progress tick, a
meter fill. Cards, pages, and panels are never filled with it.

A page-sized gradient behind a dollar figure makes the figure harder to read and
dates the product in about a year. Used sparingly, the gradient does real work:
the primary action is unmistakable on every screen without having to be large.

**2. Anything that must be read uses a flat colour.** Text, icons, borders, and
data marks have a known contrast ratio; a gradient has a different ratio at each
end, so it cannot be reasoned about for accessibility. `--ff-indigo-700` (7.1:1 on
white, AAA) is the solid colour the spectrum resolves to, sampled from the middle
of the run so the two never look unrelated.

Semantic colour — green for money returned, red for money out, amber for an
estimate — is reserved for meaning and never used decoratively. That is why a
refund is green on a page whose brand colour is violet.

---

## Tokens

All in `styles/tokens.css`. Nothing in the app hardcodes a colour, a duration, or
a spacing value.

| Group | Notes |
| --- | --- |
| **Brand** | `--ff-cyan/blue/violet/magenta`, plus `--ff-gradient` and two shorter sweeps. One definition each, so the angle and stops cannot drift between the logo, a button, and a meter. |
| **Indigo** | The solid accent, 50→900. |
| **Navy** | 500→950 for navigation and dark panels, hue-matched to the brand. Three inks for text on navy, because one grey on a dark panel flattens every hierarchy printed on it. |
| **Neutrals** | Carry a few degrees of brand hue, so a white card sits on the canvas as a deliberate pairing rather than as two unrelated greys. |
| **Semantic** | positive / negative / amber, each with a soft fill and a border. |
| **Data viz** | `--viz-1…6`. See below — this is *not* the brand gradient. |
| **Spacing** | 4px scale, `--space-1…10`. |
| **Type** | 11px→54px, with tracking that tightens as size grows. |
| **Radii** | 6/10/14/20/pill. Larger surfaces take larger radii so nothing looks scaled. |
| **Elevation** | Five levels plus a brand glow and an inset hairline. Tinted with the brand hue rather than neutral black, which is what stops a stack of white cards from looking cut out of another design. |
| **Motion** | One curve, three durations. Nothing slower than 320ms in an app people open to catch a train. |

---

## The chart palette is not the brand gradient

The obvious move is to sample the spectrum for chart series. It fails, and the
failure is measurable rather than a matter of taste.

Cyan → blue → violet → magenta is a narrow hue arc, so sampling it puts adjacent
series a few degrees apart. Run through the palette validator, that palette scored:

- **ΔE 1.7** between blue and violet under deuteranopia (target ≥ 8)
- **ΔE 7.3** between magenta and orchid for *normal* vision (floor 15)

Two series most people — and nearly all colourblind people — simply cannot tell
apart. The chart palette therefore leads with the brand blue and keeps violet and
magenta in the slots real data reaches, but separates every adjacent pair by hue
*and* lightness:

| Slot | Colour | |
| --- | --- | --- |
| 1 | `#4f6bf6` | brand blue |
| 2 | `#d95f28` | orange |
| 3 | `#8b3ff0` | brand violet |
| 4 | `#0f8f66` | green |
| 5 | `#e935d6` | brand magenta |
| 6 | `#b57d00` | amber |

Validated against a white surface: lightness band **PASS**, chroma floor **PASS**,
CVD separation **PASS** (worst adjacent ΔE 15.3 deutan / 11.3 tritan), normal
vision **PASS** (worst adjacent ΔE 32.5), contrast **PASS** (all ≥ 3:1).

Assigned in fixed order and never cycled, so an operator keeps its colour as other
operators come and go.

Other chart rules: bars are rounded only at the data end and square at the
baseline, so lengths stay honestly comparable; stacked segments carry a 2px
surface gap so two adjacent shares never read as one; values and labels wear text
ink, never the series colour; and there is **no trend line anywhere**, because the
API returns a single week and a trend drawn from one point is a decoration
pretending to be data.

---

## Components

`components/Surface.tsx` and `components/charts.tsx`. Before these existed, each
page hand-rolled its own card, which is how an app ends up with four card paddings
and three header layouts.

- **`Card`** — three tones. `plain` is the default; `quiet` recedes for supporting
  material; `navy` is the dark panel for the one figure a page exists to show.
  "Everything is a white rectangle" is the single biggest reason an interface reads
  as an admin template.
- **`Metric`** — `hero` or `default`. That is the entire hierarchy control: without
  two sizes, every number on a page is equally loud, which is the same as saying
  none of them matter.
- **`Meter`** — takes the real value and max, not a percentage, so it can report
  figures to assistive technology as well as draw them. Over-budget re-colours
  rather than merely filling, so a full bar and an overrun never look identical.
- **`Skeleton`** — sized by the caller to match what is coming, so the page does not
  reflow when data lands. Skeletons deliberately do *not* wear the class of the
  content they stand in for, or a query cannot tell "loading" from "loaded".
- **`BarChart` / `ShareBar` / `ComparisonBars`** — the three forms Insights needs.

---

## Interaction

| Element | Rest → hover → press |
| --- | --- |
| Primary button | gradient → cross-fade to the full spectrum, lift 1px, brand shadow → settle |
| Secondary button | surface → sunken, border darkens → 1px down |
| Card (interactive) | border → shadow + 2px lift → settle |
| Choice card | border → shadow, brand edge wipes in on select → 0.5% scale down |
| Route tile | border → shadow + lift; selected takes a navy ring and a brand action |
| Metric | shadow only — it is read, not clicked, so it must not invite a press |
| Nav item | muted → white; active takes a cyan rule that scales in under the label |

The primary button's hover is a second gradient layered underneath and cross-faded
via opacity, because `background-image` cannot be transitioned. Both layers sit
behind the label, so the text never moves.

Buttons and cards take a **ring** on `:focus-visible` rather than an outline — an
outline outside an element that already has a border reads as two borders.

Every transition is decorative: none convey information the static state does not,
so `prefers-reduced-motion` switches all of them off wholesale, transforms
included.

---

## Page anatomy

| Page | Leads with |
| --- | --- |
| **Plan** | The map. The planner floats over it; results sit in a scroll-snapped rail along the bottom. Route cards open with a transit-line strip — modes drawn as connected nodes — because a rider recognises a route as a shape before they read a word of it. |
| **Wallet** | Remaining, in a navy hero. A rider checking their wallet before a trip is asking "can I afford this", not "what have I spent". Everything below explains that number. |
| **Trips** | The journey shape: two places, a line, a price. The accounting — fare breakdown, leg timings, the comparison against the fastest route — lives behind *View itinerary*. |
| **Insights** | Savings, because it is the only figure that answers "was FareFlow worth using". Where a figure is not derivable the module says why rather than printing a zero. |
| **Ledger** | Density, on purpose. This is the page you open when you do not believe a number somewhere else, so the technical vocabulary stays visible and every figure is tabular. |

---

## Responsive

Verified at 1512, 1280, 834, and 390px. The top navigation hands over to a
six-across bottom bar below 900px; the map becomes a banner with the planner
beneath it rather than overlays fighting for a small screen; module grids collapse
by `auto-fit`; the wallet hero stacks its projections under a rule instead of
beside one; and charts keep their labels because they are bars with text beside
them, not a legend keyed to colour.
