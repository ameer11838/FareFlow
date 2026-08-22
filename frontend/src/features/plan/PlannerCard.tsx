import type { ContextProfileOption, LocationCandidate } from '../../api/types'
import { SearchIcon, SwapIcon } from '../../components/Icons'
import { LocationInput } from '../../components/LocationInput'

/*
 * Short chip labels. The backend still owns what a profile *means* — the weights
 * and the full display names come from /api/recommendations/profiles — but a chip
 * has room for one or two words, and "Fastest" is what a rider is actually asking
 * for when they pick RUSH.
 */
const STANCE_LABELS: Record<string, string> = {
  BALANCED: 'Balanced',
  RUSH: 'Fastest',
  SAVE_MONEY: 'Cheapest',
  FEWER_TRANSFERS: 'Fewer transfers',
}

/**
 * Compact floating planner.
 *
 * Sized to its content rather than the viewport: it is a control that sits on the
 * map, not a column that competes with it.
 *
 * The stance buttons emit only a profile id — the weights shown in the tooltip come
 * from the backend and are display-only. A client cannot send raw weights.
 */
export function PlannerCard({
  origin, destination, onOriginChange, onDestinationChange, onSwap, onSubmit,
  onOriginSelect, onDestinationSelect, searching, profiles, selectedProfile,
  onProfileChange, children,
}: {
  origin: string
  destination: string
  onOriginChange: (value: string) => void
  onDestinationChange: (value: string) => void
  onOriginSelect?: (candidate: LocationCandidate) => void
  onDestinationSelect?: (candidate: LocationCandidate) => void
  onSwap: () => void
  onSubmit: () => void
  searching: boolean
  profiles: ContextProfileOption[]
  selectedProfile: string
  onProfileChange: (id: string) => void
  children?: React.ReactNode
}) {
  return (
    <form
      className="planner"
      onSubmit={(event) => { event.preventDefault(); onSubmit() }}
    >
      <div className="planner-head">
        <h1 className="planner-title">Plan your trip</h1>
      </div>

      <div className="planner-journey">
        <span className="planner-rail" aria-hidden="true">
          <span className="planner-dot" />
          <span className="planner-line" />
          <span className="planner-dot end" />
        </span>

        <div className="planner-inputs">
          {/* Type-ahead over the geocoder: any place, not just seeded names. */}
          <LocationInput
            id="origin"
            label="From"
            value={origin}
            onChange={onOriginChange}
            onSelectCandidate={onOriginSelect}
            placeholder="Station, stop, or address"
          />
          <span className="planner-divider" />
          <LocationInput
            id="destination"
            label="To"
            value={destination}
            onChange={onDestinationChange}
            onSelectCandidate={onDestinationSelect}
            placeholder="Station, stop, or destination"
          />
        </div>

        <button
          type="button"
          className="planner-swap"
          onClick={onSwap}
          aria-label="Swap origin and destination"
          title="Swap"
        >
          <SwapIcon size={14} />
        </button>
      </div>

      <div className="planner-stance">
        <span className="planner-stance-label">What matters right now?</span>
        <div className="planner-stance-row" role="group" aria-label="What matters right now?">
          {profiles.map((profile) => (
            <button
              key={profile.id}
              type="button"
              className="stance"
              aria-pressed={profile.id === selectedProfile}
              disabled={searching}
              onClick={() => onProfileChange(profile.id)}
              title={`${Math.round(profile.costPriority * 100)}% cost · ${Math.round(profile.timePriority * 100)}% time · ${Math.round(profile.transferPriority * 100)}% transfers`}
            >
              {STANCE_LABELS[profile.id] ?? profile.displayName}
            </button>
          ))}
        </div>
      </div>

      <button className="planner-submit" type="submit" disabled={searching}>
        <SearchIcon size={16} />
        {searching ? 'Finding routes…' : 'Find routes'}
      </button>

      {children}
    </form>
  )
}
