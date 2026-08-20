import type { JourneyLeg } from '../../api/types'
import { ModeIcon } from '../../components/Icons'
import { formatMinutes } from '../../lib/format'

/**
 * Vertical timeline of a multi-leg journey.
 *
 * <p>Walking legs are drawn as dashed connectors and transit legs as solid nodes,
 * so the shape of the trip reads at a glance: where you board, where you change,
 * how long each piece takes.
 */
export function JourneyLegs({ legs }: { legs: JourneyLeg[] }) {
  return (
    <ol className="legs">
      {legs.map((leg, index) => {
        const walking = leg.mode === 'WALK'
        return (
          <li key={index} className={`leg${walking ? ' leg-walk' : ''}`}>
            <span className="leg-rail" aria-hidden="true">
              <span className={`leg-node${walking ? ' walk' : ''}`}>
                {!walking && <ModeIcon mode={leg.mode} size={12} />}
              </span>
              {index < legs.length - 1 && <span className="leg-line" />}
            </span>

            <span className="leg-body">
              <span className="leg-title">
                {walking ? `Walk to ${leg.toName}` : leg.lineName}
              </span>
              <span className="leg-detail">
                {walking
                  ? formatMinutes(leg.durationMinutes)
                  : `${leg.fromName} → ${leg.toName} · ${formatMinutes(leg.durationMinutes)}`}
                {leg.waitMinutes > 0 && (
                  <span className="leg-wait"> · {leg.waitMinutes} min wait</span>
                )}
              </span>
            </span>
          </li>
        )
      })}
    </ol>
  )
}
