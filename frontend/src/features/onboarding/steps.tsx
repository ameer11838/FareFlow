import { useState } from 'react'
import { LocationInput } from '../../components/LocationInput'
import type {
  ContextProfileOption,
  LocationCandidate,
  ModeOption,
  ProfileOption,
  TravelModeId,
  TypicalPlace,
} from '../../api/types'
import { formatCents } from '../../lib/format'
import { ChoiceCard } from './OnboardingLayout'
import { BusIcon, FerryIcon, RailIcon, SubwayIcon } from '../../components/Icons'

/* ------------------------------------------------------------------ *
 * Step 1 — how often do you commute?
 * ------------------------------------------------------------------ */

export function FrequencyStep({ options, value, onChange }: {
  options: ProfileOption[]
  value: string | null
  onChange: (id: string) => void
}) {
  return (
    <div className="choice-list">
      {options.map((option) => (
        <ChoiceCard
          key={option.id}
          selected={value === option.id}
          onSelect={() => onChange(option.id)}
          title={option.displayName}
          detail={option.detail}
        />
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ *
 * Step 2 — what matters most?
 * ------------------------------------------------------------------ */

/**
 * The stances come from the backend, weights included. They are shown here so the
 * rider can see what they are choosing, and never sent back: the client posts an
 * id, and the server owns what that id means.
 */
export function PriorityStep({ profiles, value, onChange }: {
  profiles: ContextProfileOption[]
  value: string
  onChange: (id: string) => void
}) {
  return (
    <div className="choice-list">
      {profiles.map((profile) => (
        <ChoiceCard
          key={profile.id}
          selected={value === profile.id}
          onSelect={() => onChange(profile.id)}
          title={profile.displayName}
          detail={capitalize(profile.rationale)}
        />
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ *
 * Step 3 — weekly budget
 * ------------------------------------------------------------------ */

const BUDGET_PRESETS = [2500, 5000, 7500, 10000]

/**
 * "I'm not sure" is a real answer, not a skip.
 *
 * Choosing it stores no budget at all rather than $0.00. Everything downstream
 * treats that absence as an absence: the wallet asks for a budget instead of
 * reporting an empty one, and route scoring applies no budget pressure.
 */
export function BudgetStep({ cents, onChange }: {
  cents: number | null
  onChange: (cents: number | null) => void
}) {
  const [custom, setCustom] = useState(
    cents !== null && !BUDGET_PRESETS.includes(cents) ? (cents / 100).toFixed(2) : '')
  const [error, setError] = useState<string | null>(null)

  const applyCustom = (raw: string) => {
    setCustom(raw)
    if (raw.trim() === '') {
      setError(null)
      onChange(null)
      return
    }
    const parsed = Number(raw)
    if (!Number.isFinite(parsed) || parsed < 0) {
      setError('Enter an amount like 50')
      return
    }
    if (parsed > 2000) {
      setError('That looks high for a week of transit — enter dollars, not cents')
      return
    }
    setError(null)
    // Converted to integer cents at the boundary; money is never a float.
    onChange(Math.round(parsed * 100))
  }

  return (
    <div className="budget-step">
      <div className="budget-presets">
        {BUDGET_PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            className="budget-chip"
            aria-pressed={cents === preset}
            onClick={() => { setCustom(''); setError(null); onChange(preset) }}
          >
            {formatCents(preset)}
          </button>
        ))}
      </div>

      <div className="field budget-custom">
        <label className="label" htmlFor="custom-budget">Or enter your own</label>
        <div className="budget-input">
          <span className="budget-currency" aria-hidden="true">$</span>
          <input
            id="custom-budget"
            className="input numeric"
            inputMode="decimal"
            placeholder="50.00"
            value={custom}
            onChange={(event) => applyCustom(event.target.value)}
          />
        </div>
        {error
          ? <span className="field-error">{error}</span>
          : <span className="field-hint">Per week, across every agency you ride.</span>}
      </div>

      <button
        type="button"
        className="budget-unsure"
        aria-pressed={cents === null && custom.trim() === ''}
        onClick={() => { setCustom(''); setError(null); onChange(null) }}
      >
        I&apos;m not sure yet
        <span className="budget-unsure-detail">
          FareFlow will skip budget tracking until you set one. You can add it any time.
        </span>
      </button>
    </div>
  )
}

/* ------------------------------------------------------------------ *
 * Step 4 — typical commute
 * ------------------------------------------------------------------ */

/**
 * Both ends run through the same geocoder the planner uses, and only a chosen
 * suggestion counts. Typing "Newark" and moving on would save a string that could
 * resolve to Newark, Delaware next week; saving the candidate saves the place.
 */
export function CommuteStep({
  kinds, kind, onKindChange, origin, destination, onOriginChange, onDestinationChange,
}: {
  kinds: ProfileOption[]
  kind: string | null
  onKindChange: (id: string) => void
  origin: TypicalPlace | null
  destination: TypicalPlace | null
  onOriginChange: (place: TypicalPlace | null) => void
  onDestinationChange: (place: TypicalPlace | null) => void
}) {
  const [originText, setOriginText] = useState(origin?.name ?? '')
  const [destinationText, setDestinationText] = useState(destination?.name ?? '')

  const wantsCommute = kind !== null && kind !== 'NONE'

  return (
    <div className="commute-step">
      <div className="choice-list compact">
        {kinds.map((option) => (
          <ChoiceCard
            key={option.id}
            selected={kind === option.id}
            onSelect={() => onKindChange(option.id)}
            title={option.displayName}
          />
        ))}
      </div>

      {wantsCommute && (
        <div className="commute-fields">
          <LocationInput
            id="commute-origin"
            variant="field"
            label="Home or starting point"
            placeholder="Newark"
            value={originText}
            onChange={(text) => {
              setOriginText(text)
              // Editing the text invalidates the resolved place behind it: the
              // profile must never hold a name that no longer matches its point.
              if (text !== origin?.name) onOriginChange(null)
            }}
            onSelectCandidate={(candidate) => onOriginChange(toPlace(candidate))}
            hint={<PlaceHint place={origin} text={originText} />}
          />

          <LocationInput
            id="commute-destination"
            variant="field"
            label="Where you usually go"
            placeholder="Manhattan"
            value={destinationText}
            onChange={(text) => {
              setDestinationText(text)
              if (text !== destination?.name) onDestinationChange(null)
            }}
            onSelectCandidate={(candidate) => onDestinationChange(toPlace(candidate))}
            hint={<PlaceHint place={destination} text={destinationText} />}
          />

          <p className="commute-examples">
            Newark → Manhattan · NJIT → Penn Station · Philadelphia → Manhattan
          </p>
        </div>
      )}
    </div>
  )
}

function PlaceHint({ place, text }: { place: TypicalPlace | null; text: string }) {
  if (place) {
    return <span className="field-hint resolved">Resolved to {place.name}</span>
  }
  if (text.trim().length >= 2) {
    return <span className="field-hint">Pick a suggestion so FareFlow can plan from it</span>
  }
  return null
}

function toPlace(candidate: LocationCandidate): TypicalPlace {
  return {
    name: candidate.displayName,
    latitude: candidate.latitude,
    longitude: candidate.longitude,
    providerPlaceId: candidate.providerPlaceId,
  }
}

/* ------------------------------------------------------------------ *
 * Step 5 — habits
 * ------------------------------------------------------------------ */

const MODE_ICONS: Record<string, React.ReactNode> = {
  TRAIN: <RailIcon size={17} />,
  SUBWAY: <SubwayIcon size={17} />,
  BUS: <BusIcon size={17} />,
  FERRY: <FerryIcon size={17} />,
}

export function HabitsStep({
  modes, selectedModes, onToggleMode, passOptions, passPreference, onPassChange,
}: {
  modes: ModeOption[]
  selectedModes: TravelModeId[]
  onToggleMode: (id: TravelModeId) => void
  passOptions: ProfileOption[]
  passPreference: string | null
  onPassChange: (id: string) => void
}) {
  return (
    <div className="habits-step">
      <div className="mode-grid" role="group" aria-label="How do you usually travel?">
        {modes.map((mode) => (
          <button
            key={mode.id}
            type="button"
            className="mode-card"
            aria-pressed={selectedModes.includes(mode.id)}
            onClick={() => onToggleMode(mode.id)}
          >
            <span className="mode-card-icon" aria-hidden="true">{MODE_ICONS[mode.id]}</span>
            <span className="mode-card-name">{mode.displayName}</span>
          </button>
        ))}
      </div>

      <div className="habits-pass">
        <span className="habits-pass-label">How do you usually pay?</span>
        <div className="pass-row" role="group" aria-label="How do you usually pay?">
          {passOptions.map((option) => (
            <button
              key={option.id}
              type="button"
              className="pass-chip"
              aria-pressed={passPreference === option.id}
              onClick={() => onPassChange(option.id)}
            >
              {option.displayName}
            </button>
          ))}
        </div>
        <p className="habits-note">
          FareFlow never asks for a card, an account, or anything a bank would recognise.
        </p>
      </div>
    </div>
  )
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
