# Design system

FareFlow's identity comes from its app mark: a spectrum running cyan → indigo →
magenta, on deep ink navigation and a pale canvas.

![Login](screenshots/login.png)

---

## The gradient is a brand device, not a background

This is the rule the whole system rests on. The spectrum appears on:

- the **app mark**,
- the **primary action** — the one button on a screen that is the point of it,
- **thin accents** that mark progress or selection: a 2px hairline under the
  navigation, a 3px edge on the selected card, a 4px progress tick, a meter fill.

It appears nowhere else. A page-sized gradient behind a dollar figure makes the
figure harder to read and dates the product in about a year. A gradient used as an
accent reads as identity — and because it is scarce, the primary action is
unmistakable on every screen without needing to be large.

```css
--ff-gradient: linear-gradient(135deg, #22d3ee 0%, #4f6bf6 38%, #8b3ff0 72%, #e935d6 100%);
```

Defined once in `tokens.css`, so the angle and stops cannot drift between the logo,
a button, and a progress bar. `--ff-gradient-compact` is the two-stop version for
small elements, where four stops turn to mush.

### Where a gradient is the wrong answer

- **Text, icons, borders.** They need one flat colour with a known contrast ratio;
  a gradient has a different ratio at each end. `--ff-indigo-700` (7.1:1 on white,
  AAA for body text) is the solid colour the spectrum resolves to, picked from the
  middle of the run so the two never look unrelated.
- **Data bars.** Several bars in a rainbow each look like a different measurement,
  when length is the only variable that means anything. Provider bars use the
  compact sweep so all bars share one colour.
- **Unvisited progress.** A step not yet reached is plain grey. Painting it in a
  faded brand colour made it look partly complete — the one thing a progress
  indicator must never say.

---

## The mark

`components/Logo.tsx`, drawn as SVG rather than imported as a raster so it stays
sharp from a 22px favicon to a 96px auth panel, needs no icon-library dependency,
and inherits nothing from the network.

An F whose lower arm sweeps into a rail corridor, with a train emerging from the
curve. The train is a cab, a window, and two lamps — any more detail turns to mud
below 24px, which is most of the sizes it is actually used at.

Two details worth knowing:

- The gradient's `<defs>` id is generated per instance with `useId`. Two logos on
  one page would otherwise share one id, and whichever unmounted first would take
  the fill with it — a genuinely confusing bug to chase.
- `<Wordmark>` defines the lockup — mark size, gap, weight, tracking — once, so it
  cannot be re-approximated in the top bar, the auth screens, and the onboarding
  header, which is how a wordmark quietly ends up existing in three sizes.

---

## Interaction

One easing curve and two durations for the whole app, so nothing animates at a
speed nothing else uses:

```css
--ease: cubic-bezier(0.32, 0.72, 0.28, 1);
--dur-fast: 120ms;   /* hover, press */
--dur: 200ms;        /* selection, cross-fade */
```

| Element | Rest → hover → press |
| --- | --- |
| Primary button | gradient → cross-fade to the full spectrum, lift 1px, brand shadow → settle to 0 |
| Secondary button | surface → sunken, border darkens → 1px down |
| Choice card | border → shadow appears, brand edge wipes in on select → 0.5% scale down |
| Mode card | border → lift 2px → settle |
| Statistic | shadow only — it is read, not clicked, so it must not invite a press |

The primary button's hover is a second gradient layered underneath and cross-faded
via opacity, because `background-image` cannot be transitioned. Both layers sit
behind the label, so the text never moves.

Buttons and cards take a **ring** on `:focus-visible` rather than an outline —
an outline outside an element that already has a border reads as two borders.

Every transition in the app is decorative: none convey information the static
state does not, so `prefers-reduced-motion` switches all of them off wholesale,
transforms included.

---

## Colour

| Token | Use |
| --- | --- |
| `--ff-grad-1…4` | the four stops of the mark, in order |
| `--ff-indigo-600` / `-700` | the solid accent: links, icons, selected borders |
| `--ff-ink-900…600` | navigation and dark panels, hue-matched to the brand |
| `--color-canvas` / `-surface` | neutrals carrying a few degrees of brand hue, so white cards sit on the canvas as a deliberate pairing rather than as two unrelated greys |
| `--color-positive` / `-negative` / `-amber` | money in/out and budget pressure — **semantic only**, never decorative |

Shadows are tinted with the brand hue rather than neutral black, which is what
stops a stack of white cards from looking like it was cut out of a different
design.

Dark panels get a single soft brand glow from two corners rather than a gradient
wash: a gradient across a whole panel fights the figures printed on it, and one
light source behind the content does not.
