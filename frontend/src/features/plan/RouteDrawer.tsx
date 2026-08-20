import { useState } from 'react'
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
  choosingJourneyId, searching, error, onRetry,
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

function JourneyTile({ option, selected, dimmed, onSelect, onHover, onChoose, choosing, disabled }: {
  option: JourneyOption
  selected: boolean
  dimmed: boolean
  onSelect: () => void
  onHover: (journeyId: string | null) => void
  onChoose: () => void
  choosing: boolean
  disabled: boolean
}) {
  const [showLegs, setShowLegs] = useState(false)
  const primaryLabel = option.labels[0]
  const rides = option.legs.filter((leg) => leg.mode !== 'WALK')

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
      {primaryLabel && (
        <span className={`route-flag flag-${primaryLabel.toLowerCase()}`}>
          {labelText(primaryLabel)}
        </span>
      )}

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
          <JourneyLegs legs={option.legs} />
          {option.fareBreakdown.length > 0 && (
            <details className="fare-breakdown">
              <summary>Fare breakdown</summary>
              <ul>
                {option.fareBreakdown.map((line) => <li key={line}><span>{line}</span></li>)}
              </ul>
            </details>
          )}
        </div>
      )}
    </div>
  )
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
