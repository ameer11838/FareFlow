import type { RecommendedRoute } from '../../api/types'
import { LabelBadge } from '../../components/Badge'
import { ClockIcon, ModeIcon, TransferIcon } from '../../components/Icons'
import { formatCents, formatMinutes } from '../../lib/format'

/**
 * One route option.
 *
 * The trade-off strip is built from the backend's integer deltas rather than by
 * parsing the prose explanation, so the numbers shown are exactly the ones the
 * engine scored on.
 */
export function RouteOptionCard({ route, onChoose, choosing, disabled }: {
  route: RecommendedRoute
  onChoose: (route: RecommendedRoute) => void
  choosing: boolean
  disabled: boolean
}) {
  const tradeoffs = buildTradeoffs(route)

  return (
    <article className={`option${route.recommended ? ' recommended' : ''}`} data-testid={`option-${route.provider}`}>
      {route.recommended && (
        <div className="option-ribbon">
          <span>Recommended for you</span>
        </div>
      )}

      <div className="option-main">
        <div>
          <div className="option-labels">
            {route.labels.map((label) => <LabelBadge key={label} label={label} />)}
          </div>

          <h3 className="option-provider">
            <span className="mode-icon" style={{ width: 34, height: 34 }}>
              <ModeIcon mode={route.mode} size={17} />
            </span>
            {route.providerName}
          </h3>

          <div className="option-facts numeric">
            <span className="option-fact">
              <ClockIcon size={15} />
              {formatMinutes(route.durationMinutes)}
            </span>
            <span className="option-sep" />
            <span className="option-fact">
              <TransferIcon size={15} />
              {route.transfers === 0
                ? 'Direct'
                : `${route.transfers} transfer${route.transfers > 1 ? 's' : ''}`}
            </span>
          </div>
        </div>

        <div className="option-price">
          <div className="option-fare numeric">{formatCents(route.fareCents)}</div>
          <div className="option-fare-note">per ride</div>
        </div>
      </div>

      {tradeoffs.length > 0 && (
        <div className="option-tradeoffs">
          {tradeoffs.map((item) => (
            <span key={item.text} className={`tradeoff ${item.good ? 'tradeoff-good' : 'tradeoff-bad'}`}>
              {item.text}
            </span>
          ))}
        </div>
      )}

      <div className="option-actions">
        <p className="option-explanation">{route.explanation}</p>
        <button
          className={`btn${route.recommended ? ' btn-primary' : ''}`}
          onClick={() => onChoose(route)}
          disabled={disabled}
        >
          {choosing ? 'Starting…' : 'Choose this route'}
        </button>
      </div>

      {route.overBudget && (
        <div className="option-tradeoffs" style={{ color: 'var(--color-amber)' }}>
          <span className="tradeoff">This fare exceeds your remaining weekly budget.</span>
        </div>
      )}
    </article>
  )
}

interface Tradeoff {
  text: string
  good: boolean
}

/**
 * Renders the two comparison lines from the mock-up: money first, then time.
 * Anchors on the fastest route, falling back to the best-value route for the
 * fastest option itself.
 */
function buildTradeoffs(route: RecommendedRoute): Tradeoff[] {
  // Anchor on the fastest route; a route that IS the fastest falls back to the
  // best-value option, and one that is both falls back to the cheapest. Only a
  // sole candidate ends up with no anchor at all.
  const reference = route.vsFastest ?? route.vsBestValue ?? route.vsCheapest
  if (!reference) return []

  const items: Tradeoff[] = []
  const { fareDeltaCents, minutesDelta, referenceProvider } = reference

  if (fareDeltaCents < 0) {
    items.push({ text: `Saves ${formatCents(-fareDeltaCents)} vs ${referenceProvider}`, good: true })
  } else if (fareDeltaCents > 0) {
    items.push({ text: `Costs ${formatCents(fareDeltaCents)} more than ${referenceProvider}`, good: false })
  } else {
    items.push({ text: `Same fare as ${referenceProvider}`, good: false })
  }

  if (minutesDelta > 0) {
    items.push({ text: `${minutesDelta} min slower`, good: false })
  } else if (minutesDelta < 0) {
    items.push({ text: `Arrives ${-minutesDelta} min sooner`, good: true })
  } else {
    items.push({ text: 'Same travel time', good: false })
  }

  if (reference.transfersDelta < 0) {
    items.push({ text: `${-reference.transfersDelta} fewer transfer(s)`, good: true })
  } else if (reference.transfersDelta > 0) {
    items.push({ text: `${reference.transfersDelta} more transfer(s)`, good: false })
  }

  return items
}
