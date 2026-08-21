# Design system

FareFlow's identity comes from one object: the app mark, whose plate runs cyan →
electric blue → violet → magenta on deep navy. Everything below is that mark
decomposed into a system.

![Login](screenshots/login.png)

---

## The two rules

Everything else follows from these.

**1. The gradient belongs to the logo.** The mark is the most expressive element
in the product, and it earns that by being the only place the full spectrum
appears. Buttons, navigation, meters, selection states, and charts all use **solid
purple** — `--color-accent`, which is the brand colour throughout.

This is a correction of an earlier version that put the gradient on the primary
button, the nav indicator, the progress ticks, the meters and the avatars. Each
one looked fine alone; together they made the logo ordinary and the product look
generated. One gradient, in one place, is what makes it read as a brand.

Cyan is a secondary accent used sparingly — an eyebrow, one indicator — never as a
second brand colour competing with the purple. Green, red and amber are reserved
for financial and status meaning and never used decoratively.

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
| **Elevation** | Structure comes from **1px borders**; shadow is reserved for things genuinely floating above the page — the planner over the map, a drawer. A resting card has a border and no shadow. There is no coloured glow anywhere. |
| **Radii** | Deliberately tight (4/6/8/12). Large radii read as consumer-app friendliness; a product that shows people their money reads better slightly squarer. |
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

## Light and dark

Three states, because "System" is a real choice rather than the absence of one: a
rider whose laptop switches at sunset should follow it unless they have said
otherwise. `useTheme` persists the preference, writes `data-theme` on `<html>` for
an explicit choice, and writes **no attribute** for "system" so the stylesheet's
`prefers-color-scheme` query takes over. The `:not([data-theme="light"])` guard on
that query lets an explicit light choice beat a dark OS, so the toggle wins in both
directions.

The theme is applied by a small inline script in `index.html`, before first paint.
Waiting for React would show one frame of the light theme to every dark-mode user —
the white flash every themed app gets judged on. That script is kept in sync with
the hook: same storage key, same three states, same rule about the attribute.

**Dark mode is selected, not inverted.** Every role is re-stepped against a navy
ground:

- Surfaces get **lighter** as they rise. In light mode they get darker — flipping
  the values would give grey-on-grey cards.
- The accent moves two steps up the ramp (`#5b3ce8` → `#8b7cff`), because a mid
  indigo disappears on navy.
- Semantic colours are re-picked, not lightened: `#0f7a4f` → `#3ddc9a` for
  positive, so a refund still reads as money returned at the same glance value.
- Shadows change job. A black shadow does nothing on a dark ground, so depth comes
  from a light top edge plus a deeper ambient shadow.
- The chart palette has its own validated dark steps — same six hues, re-stepped
  for the `#141227` surface: worst adjacent ΔE **10.4** deutan / **9.0** tritan,
  normal vision **27.1**, all ≥ 3:1 contrast.

The navigation and the hero panels stay dark in both themes, but not the *same*
dark: `--ff-nav-surface` and `--ff-panel-surface` drop onto the canvas in light
mode and rise off it in dark.

The control lives in Settings as a three-way segmented control, and in the navbar
as a compact button that cycles light → dark → system.

---

## Icons

`public/favicon.svg` is the primary tab icon — the mark, no text — so it stays
sharp at any density. The PNG siblings (16, 32, 180 apple-touch, 192, 512) are
rendered from that same SVG, so they cannot drift from the vector, and cover Safari
and the platforms that will not take one. `site.webmanifest` completes the
installable-app metadata, and paired `theme-color` meta tags keep mobile browser
chrome from sitting as a white band above a dark app.

---

## Not everything is a card

Putting every piece of information inside a rounded box is the single clearest
tell of a template. Most of a page is built from three patterns that are not
cards:

- **`.band`** — an open section: a hairline above it, an uppercase label, generous
  space, and the content sitting directly on the page.
- **`.figures-row`** — a row of figures divided by rules. The numbers share one
  baseline and are directly comparable, which four separate tiles never are.
- **`.data-table`** — a real financial table: tabular numerals, right-aligned
  money, a quiet header, an inline share bar in a cell. On Insights and Wallet
  this replaced a bar chart, because a table gives share *and* trip count *and*
  average fare where the chart gave one of them and needed a legend.

And **`.lede`** — the figure a page exists to show, set at 56px on the page rather
than inside a panel. Prominence comes from typography, not from a container.

Whitespace is generous *between* sections and tight *within* them: `--space-7`
above a band, `--space-3` between rows inside it. Even spacing everywhere is what
makes a dashboard feel airless.

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
