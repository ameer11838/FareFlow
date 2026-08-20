import type { ContextProfileOption } from '../../api/types'
import { formatPercent } from '../../lib/format'

/**
 * "What matters right now?" — the deterministic stand-in for natural language.
 *
 * The chips carry only a profile id. Weights are displayed for transparency but
 * never sent: the backend looks them up, so the client cannot skew a financial
 * trade-off. When AI arrives it will select one of these same stances (or a
 * sanitized custom vector) from a sentence, and nothing downstream changes.
 */
export function ProfileSelector({ profiles, selected, onSelect, disabled }: {
  profiles: ContextProfileOption[]
  selected: string
  onSelect: (id: string) => void
  disabled?: boolean
}) {
  if (profiles.length === 0) return null

  return (
    <div className="profile-grid" role="group" aria-label="What matters right now?">
      {profiles.map((profile) => {
        const isSelected = profile.id === selected
        return (
          <button
            key={profile.id}
            type="button"
            className="profile-chip"
            aria-pressed={isSelected}
            disabled={disabled}
            onClick={() => onSelect(profile.id)}
          >
            <span className="profile-chip-head">
              <span className="profile-chip-name">{profile.displayName}</span>
            </span>
            <span className="profile-chip-weights">
              {formatPercent(profile.costPriority)} cost · {formatPercent(profile.timePriority)} time
              {profile.transferPriority >= 0.25 && ` · ${formatPercent(profile.transferPriority)} transfers`}
            </span>
          </button>
        )
      })}
    </div>
  )
}
