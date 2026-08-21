import type { JourneyLeg } from '../../api/types'
import { ModeIcon } from '../../components/Icons'
import { formatMinutes } from '../../lib/format'

/** Detailed, selectable directions using only fields supplied by the route API. */
export function JourneyLegs({ legs, activeLegIndex = null, onSelectLeg }: {
  legs: JourneyLeg[]
  activeLegIndex?: number | null
  onSelectLeg?: (index: number) => void
}) {
  if (legs.length === 0) return null

  return (
    <ol className="legs">
      <li className="leg leg-place">
        <span className="leg-rail" aria-hidden="true">
          <span className="leg-node place" />
          <span className="leg-line" />
        </span>
        <span className="leg-body">
          <span className="leg-kicker">Start</span>
          <span className="leg-title">Leave {legs[0].fromName}</span>
        </span>
      </li>

      {legs.map((leg, index) => {
        const walking = leg.mode === 'WALK'
        const selected = activeLegIndex === index
        return (
          <li key={index} className={`leg${walking ? ' leg-walk' : ''}${selected ? ' active' : ''}`}>
            <button
              type="button"
              className="leg-select"
              onClick={() => onSelectLeg?.(index)}
              aria-pressed={selected}
              aria-label={`Highlight ${walking ? 'walking' : leg.lineName} segment on map`}
            >
              <span className="leg-rail" aria-hidden="true">
                <span className={`leg-node${walking ? ' walk' : ''}`}>
                  {!walking && <ModeIcon mode={leg.mode} size={12} />}
                </span>
                <span className="leg-line" />
              </span>

              <span className="leg-body">
                {leg.departureTime && (
                  <span className="leg-kicker">
                    {formatTransitTime(leg.departureTime)}{leg.realtime ? ' · Live update' : ' · Scheduled'}
                  </span>
                )}
                {leg.waitMinutes > 0 && (
                  <span className="leg-wait-event">Wait {formatMinutes(leg.waitMinutes)}</span>
                )}
                <span className="leg-kicker">{walking ? 'Walk' : `Board · ${modeName(leg.mode)}`}</span>
                <span className="leg-title">
                  {walking ? `Walk to ${leg.toName}` : leg.lineName}
                </span>
                <span className="leg-detail">
                  {walking ? (
                    <>{formatMinutes(leg.durationMinutes)}{distanceText(leg.distanceMetres)}</>
                  ) : (
                    <>
                      {leg.agency && <>{leg.agency} · </>}
                      {leg.fromName} → {leg.toName}
                    </>
                  )}
                </span>
                {!walking && (
                  <>
                    <span className="leg-ride">
                      Ride {formatMinutes(leg.durationMinutes)}{stopCount(leg)}
                      {leg.arrivalTime && <> · Arrive {formatTransitTime(leg.arrivalTime)}</>}
                    </span>
                    {leg.waypoints.length > 2 && (
                      <span className="leg-stops">
                        Via {leg.waypoints.slice(1, -1).map((stop) => stop.name).join(' · ')}
                      </span>
                    )}
                  </>
                )}
              </span>
            </button>
          </li>
        )
      })}

      <li className="leg leg-place leg-arrive">
        <span className="leg-rail" aria-hidden="true"><span className="leg-node place end" /></span>
        <span className="leg-body">
          <span className="leg-kicker">Arrive</span>
          <span className="leg-title">{legs[legs.length - 1].toName}</span>
        </span>
      </li>
    </ol>
  )
}

function modeName(mode: JourneyLeg['mode']): string {
  return mode.toLowerCase().replace('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
}

function distanceText(metres: number | undefined): string {
  if (metres === undefined || metres <= 0) return ''
  const miles = metres / 1609.344
  return ` · ${miles < 0.1 ? miles.toFixed(2) : miles.toFixed(1)} mi`
}

function stopCount(leg: JourneyLeg): string {
  const count = leg.stopCount ?? Math.max(0, leg.waypoints.length - 1)
  return count > 0 ? ` · ${count} stop${count === 1 ? '' : 's'}` : ''
}

function formatTransitTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}
