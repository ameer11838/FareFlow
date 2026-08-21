import { useEffect, useState } from 'react'
import type { ApiError } from '../../api/client'
import type { JourneyOption, JourneySearchResponse } from '../../api/types'
import { InfoIcon, ModeIcon, TransferIcon } from '../../components/Icons'
import { formatCents, formatMinutes, labelText } from '../../lib/format'
import { JourneyLegs } from './JourneyLegs'

/**
 * Horizontal results drawer along the bottom of the map.
 *
 * <p>Cards stay concise: mode, time, fare, one trade-off line. The full leg
 * timeline and fare breakdown are one click away rather than crowding the row.
 */
export function RouteDrawer({
  result, selectedJourneyId, onSelectJourney, onHoverJourney, onChoose,
  choosingJourneyId, searching, error, onRetry, activeLegIndex, onSelectLeg,
}: {
  result: JourneySearchResponse | null
  selectedJourneyId: string | null
  onSelectJourney: (journeyId: string) => void
  onHoverJourney: (journeyId: string | null) => void
  onChoose: (option: JourneyOption) => void
  choosingJourneyId: string | null
  searching: boolean
  error: ApiError | null
  onRetry: () => void
  activeLegIndex: number | null
  onSelectLeg: (index: number | null) => void
}) {
  if (error) {
    return (
      <div className="drawer drawer-message" role="alert">
        <div>
          <p className="drawer-message-title">{error.problem.title ?? 'Something went wrong'}</p>
          <p className="drawer-message-body">{error.message}</p>
        </div>
        <button className="btn btn-sm" onClick={onRetry}>Try again</button>
      </div>
    )
  }

  if (searching && !result) {
    return (
      <div className="drawer">
        <div className="drawer-rail">
          {[0, 1, 2].map((index) => (
            <div key={index} className="route-tile skeleton" style={{ height: 150 }} />
          ))}
        </div>
      </div>
    )
  }

  if (!result) return null

  if (result.options.length === 0) {
    return (
      <div className="drawer drawer-message">
        <div>
          <p className="drawer-message-title">No journeys found</p>
          <p className="drawer-message-body">
            {result.notices[0] ?? 'FareFlow has no transit coverage between these places yet.'}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="drawer" data-testid="route-drawer">
      <div className="drawer-head">
        <span className="drawer-route">
          {result.origin.displayName} → {result.destination.displayName}
        </span>
        <span className="drawer-count">{result.options.length} options</span>
        {result.contextNote && (
          <span className="drawer-note" data-testid="context-note">
            <InfoIcon size={14} />
            {result.contextNote}
          </span>
        )}
      </div>

      <div className="drawer-rail">
        {result.options.map((option) => (
          <JourneyTile
            key={option.journeyId}
            option={option}
            selected={option.journeyId === selectedJourneyId}
            dimmed={selectedJourneyId !== null && option.journeyId !== selectedJourneyId}
            onSelect={() => onSelectJourney(option.journeyId)}
            onHover={onHoverJourney}
            onChoose={() => onChoose(option)}
            choosing={choosingJourneyId === option.journeyId}
            disabled={choosingJourneyId !== null}
            allOptions={result.options}
            budgetContext={result.budgetContext ?? null}
            activeLegIndex={option.journeyId === selectedJourneyId ? activeLegIndex : null}
            onSelectLeg={(index) => {
              onSelectJourney(option.journeyId)
              onSelectLeg(index)
            }}
          />
        ))}
      </div>

      {result.notices.length > 0 && (
        <div className="notices">
          {result.notices.map((notice) => (
            <p key={notice} className="notice">{notice}</p>
          ))}
        </div>
      )}
    </div>
  )
}

function JourneyTile({
  option, selected, dimmed, onSelect, onHover, onChoose, choosing, disabled,
  allOptions, budgetContext, activeLegIndex, onSelectLeg,
}: {
  option: JourneyOption
  selected: boolean
  dimmed: boolean
  onSelect: () => void
  onHover: (journeyId: string | null) => void
  onChoose: () => void
  choosing: boolean
  disabled: boolean
  allOptions: JourneyOption[]
  budgetContext: JourneySearchResponse['budgetContext']
  activeLegIndex: number | null
  onSelectLeg: (index: number) => void
}) {
  const [showLegs, setShowLegs] = useState(false)
  useEffect(() => {
    if (activeLegIndex !== null) setShowLegs(true)
  }, [activeLegIndex])
  const rides = option.legs.filter((leg) => leg.mode !== 'WALK')
  const labels = labelsFor(option, allOptions)
  const operators = [...new Set(rides.map((leg) => leg.agency).filter(Boolean))]
  const comparison = fareComparison(option, allOptions)
  const remainingAfter = option.fareCents !== null && budgetContext
    ? budgetContext.weeklyBudgetCents - budgetContext.spentThisWeekCents - option.fareCents
    : null

  return (
    <div
      className={`route-tile${selected ? ' selected' : ''}${dimmed ? ' dimmed' : ''}`}
      data-testid={`journey-card-${option.journeyId}`}
      aria-pressed={selected}
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onMouseEnter={() => onHover(option.journeyId)}
      onMouseLeave={() => onHover(null)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onSelect()
        }
      }}
    >
      <div className="route-flags">
        {labels.map((label) => (
          <span key={label.text} className={`route-flag ${label.className}`}>{label.text}</span>
        ))}
      </div>

      {/*
        The transit-line strip. A rider recognises a route as a sequence of modes
        before they read a word of it, so the modes are drawn as connected nodes
        rather than described in a sentence.
      */}
      <div className="line-strip" aria-hidden="true">
        {rides.slice(0, 4).map((leg, index) => (
          <span key={index} className="line-strip-item">
            {index > 0 && <span className="line-strip-link" />}
            <span className={`line-strip-node mode-${leg.mode.toLowerCase()}`}>
              <ModeIcon mode={leg.mode} size={13} />
            </span>
          </span>
        ))}
        {rides.length > 4 && <span className="line-strip-more">+{rides.length - 4}</span>}
      </div>

      <div className="route-tile-provider">{option.summary}</div>

      <div className="route-tile-figures">
        <span className="route-tile-time numeric">{formatMinutes(option.totalMinutes)}</span>
        <Fare option={option} />
      </div>

      <div className="route-tile-transfers">
        <TransferIcon size={13} />
        {option.transfers === 0 ? 'Direct' : `${option.transfers} transfer${option.transfers > 1 ? 's' : ''}`}
        {option.walkingMinutes > 0 && <> · {option.walkingMinutes} min walk</>}
      </div>

      {operators.length > 0 && (
        <div className="route-tile-operators">{operators.join(' + ')}</div>
      )}

      {(comparison || remainingAfter !== null) && (
        <div className="route-impact">
          {comparison && <span>{comparison}</span>}
          {remainingAfter !== null && (
            <span className={remainingAfter < 0 ? 'impact-over' : ''}>
              {remainingAfter < 0
                ? `${formatCents(-remainingAfter)} over weekly budget`
                : `${formatCents(remainingAfter)} budget left after trip`}
            </span>
          )}
        </div>
      )}

      {/* The one line that says why this option might be the right one. */}
      {option.explanation && (
        <p className="route-tile-why">{option.explanation}</p>
      )}

      <div className="route-tile-actions">
        <button
          type="button"
          className="route-tile-detail"
          onClick={(event) => { event.stopPropagation(); setShowLegs((value) => !value) }}
          aria-expanded={showLegs}
        >
          {showLegs ? 'Hide details' : 'View details'}
        </button>
        <button
          className="route-tile-choose"
          onClick={(event) => { event.stopPropagation(); onChoose() }}
          disabled={disabled}
        >
          {choosing ? 'Starting…' : 'Choose route'}
        </button>
      </div>

      {showLegs && (
        <div className="route-tile-legs" onClick={(event) => event.stopPropagation()}>
          <div className="itinerary-head">
            <strong>Step-by-step directions</strong>
            <span>Scheduled clock times are not available from this route source.</span>
          </div>
          <JourneyLegs
            legs={option.legs}
            activeLegIndex={activeLegIndex}
            onSelectLeg={onSelectLeg}
          />
          {option.fareBreakdown.length > 0 && (
            <section className="fare-breakdown fare-events" aria-label="Fare events">
              <strong>Fare events</strong>
              <ul>
                {option.fareBreakdown.map((line) => <li key={line}><span>{line}</span></li>)}
              </ul>
            </section>
          )}
        </div>
      )}
    </div>
  )
}

function labelsFor(option: JourneyOption, all: JourneyOption[]): Array<{ text: string; className: string }> {
  const labels: Array<{ text: string; className: string }> = []
  if (option.recommended) labels.push({ text: 'Best for you', className: 'flag-best_for_you' })
  option.labels.forEach((label) => labels.push({
    text: labelText(label),
    className: `flag-${label.toLowerCase()}`,
  }))
  const leastWalking = Math.min(...all.map((candidate) => candidate.walkingMinutes))
  const fewestTransfers = Math.min(...all.map((candidate) => candidate.transfers))
  if (option.walkingMinutes === leastWalking) {
    labels.push({ text: 'Least walking', className: 'flag-secondary' })
  }
  if (option.transfers === fewestTransfers) {
    labels.push({ text: 'Fewest transfers', className: 'flag-secondary' })
  }
  return labels.filter((label, index) => labels.findIndex((item) => item.text === label.text) === index)
}

function fareComparison(option: JourneyOption, all: JourneyOption[]): string | null {
  if (option.fareCents === null) return null
  const priced = all.filter((candidate) => candidate.fareCents !== null)
  if (priced.length < 2) return null
  const cheapest = Math.min(...priced.map((candidate) => candidate.fareCents!))
  if (option.fareCents > cheapest) {
    return `${formatCents(option.fareCents - cheapest)} more than cheapest`
  }
  const mostExpensive = Math.max(...priced.map((candidate) => candidate.fareCents!))
  return mostExpensive > cheapest
    ? `Saves up to ${formatCents(mostExpensive - cheapest)}`
    : 'Same fare as other priced options'
}

/**
 * An unpriceable fare is shown as unavailable, never as $0.00 — the whole point of
 * carrying fare status through from the engine.
 */
function Fare({ option }: { option: JourneyOption }) {
  if (option.fareCents === null) {
    return (
      <span className="fare-unavailable" title="No published fare FareFlow can compute">
        Fare varies
      </span>
    )
  }
  return (
    <span className="route-tile-fare numeric">
      {formatCents(option.fareCents)}
      {option.fareStatus === 'ESTIMATED' && (
        <span className="fare-status fare-estimated" style={{ marginLeft: 6 }}>est</span>
      )}
    </span>
  )
}
