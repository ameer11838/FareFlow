import { RouteIcon, SwapIcon } from '../../components/Icons'

/**
 * The rider's saved commute, one tap from being planned.
 *
 * <p>This is the payoff for onboarding: someone who told FareFlow they go Newark
 * to Manhattan should not have to type it again every morning. The return
 * direction gets its own button rather than a swap-then-search, because the
 * evening trip is a different trip, not an edit of the morning one.
 *
 * <p>Nothing here runs until it is pressed. The shortcut is rendered from data
 * already loaded with the profile; no route is fetched to display it.
 */
export function CommuteShortcut({ originName, destinationName, onPlan, onPlanReturn }: {
  originName: string
  destinationName: string
  onPlan: () => void
  onPlanReturn: () => void
}) {
  return (
    <div className="commute-shortcut">
      <div className="commute-shortcut-head">
        <span className="commute-shortcut-icon" aria-hidden="true"><RouteIcon size={15} /></span>
        <div>
          <span className="commute-shortcut-label">Your usual commute</span>
          <span className="commute-shortcut-route">
            {originName} <span className="commute-arrow" aria-hidden="true">→</span> {destinationName}
          </span>
        </div>
      </div>

      <div className="commute-shortcut-actions">
        <button type="button" className="btn btn-primary btn-sm" onClick={onPlan}>
          Plan commute
        </button>
        <button
          type="button"
          className="btn btn-sm btn-ghost commute-return"
          onClick={onPlanReturn}
          title={`Plan ${destinationName} to ${originName}`}
        >
          <SwapIcon size={13} />
          Return trip
        </button>
      </div>
    </div>
  )
}
