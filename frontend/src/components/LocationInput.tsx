import { useEffect, useRef, useState } from 'react'
import { locationsApi } from '../api'
import type { LocationCandidate } from '../api/types'

/**
 * Type-ahead place search.
 *
 * <p>Debounced so a keystroke does not become a request, and results are cached per
 * query for the life of the component — typing "Phil", deleting, and retyping hits
 * the network once. The backend caches too; this just avoids the round trip.
 *
 * <p>Free text is still accepted on the planner: the backend resolves whatever is
 * typed, so a user who ignores the dropdown is not blocked.
 *
 * <p>Callers that need a <em>resolved</em> place — saving a commute to the profile,
 * where a bare string would be re-geocoded to somewhere slightly different later —
 * pass {@code onSelectCandidate} and get the full candidate, coordinates included.
 */
export function LocationInput({
  id, label, value, onChange, placeholder, onSelectCandidate, variant = 'planner', hint,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  /** Fires only when a suggestion is chosen, never for typed-through text. */
  onSelectCandidate?: (candidate: LocationCandidate) => void
  variant?: 'planner' | 'field'
  hint?: React.ReactNode
}) {
  const [candidates, setCandidates] = useState<LocationCandidate[]>([])
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [loading, setLoading] = useState(false)

  const cache = useRef(new Map<string, LocationCandidate[]>())
  const containerRef = useRef<HTMLDivElement>(null)
  // Guards against a slow early request overwriting a newer one's results.
  const requestId = useRef(0)

  useEffect(() => {
    const query = value.trim()
    if (query.length < 2) {
      setCandidates([])
      return
    }

    const cached = cache.current.get(query.toLowerCase())
    if (cached) {
      setCandidates(cached)
      return
    }

    const id = ++requestId.current
    const timer = setTimeout(() => {
      setLoading(true)
      locationsApi
        .search(query)
        .then((results) => {
          if (id !== requestId.current) return
          cache.current.set(query.toLowerCase(), results)
          setCandidates(results)
        })
        .catch(() => {
          if (id === requestId.current) setCandidates([])
        })
        .finally(() => {
          if (id === requestId.current) setLoading(false)
        })
    }, 220)

    return () => clearTimeout(timer)
  }, [value])

  // Close when focus leaves the whole control, not just the input.
  useEffect(() => {
    const onDocumentClick = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDocumentClick)
    return () => document.removeEventListener('mousedown', onDocumentClick)
  }, [])

  const select = (candidate: LocationCandidate) => {
    onChange(candidate.displayName)
    onSelectCandidate?.(candidate)
    setOpen(false)
    setActiveIndex(-1)
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (!open || candidates.length === 0) return

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((index) => (index + 1) % candidates.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => (index <= 0 ? candidates.length - 1 : index - 1))
    } else if (event.key === 'Enter' && activeIndex >= 0) {
      event.preventDefault()
      select(candidates[activeIndex])
    } else if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  const fieldClass = variant === 'field' ? 'field' : 'planner-field'
  const labelClass = variant === 'field' ? 'label' : 'planner-label'
  const inputClass = variant === 'field' ? 'input' : 'planner-input'

  return (
    <div className={`location-input${variant === 'field' ? ' location-input-field' : ''}`}
         ref={containerRef}>
      <label className={fieldClass} htmlFor={id}>
        <span className={labelClass}>{label}</span>
        <input
          id={id}
          className={inputClass}
          value={value}
          onChange={(event) => { onChange(event.target.value); setOpen(true); setActiveIndex(-1) }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          autoComplete="off"
          role="combobox"
          aria-expanded={open && candidates.length > 0}
          aria-controls={`${id}-listbox`}
          aria-autocomplete="list"
        />
      </label>

      {open && candidates.length > 0 && (
        <ul className="location-menu" id={`${id}-listbox`} role="listbox">
          {candidates.map((candidate, index) => (
            <li key={`${candidate.providerPlaceId ?? candidate.displayName}-${index}`}>
              <button
                type="button"
                role="option"
                aria-selected={index === activeIndex}
                className={`location-option${index === activeIndex ? ' active' : ''}`}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => select(candidate)}
              >
                <span className="location-option-head">
                  <span className={`location-kind${candidate.source === 'GTFS' ? ' is-transit' : ''}`}
                        aria-hidden="true" />
                  <span className="location-name">{candidate.displayName}</span>
                </span>
                {(candidate.locality || candidate.region) && (
                  <span className="location-meta">
                    {[candidate.locality, candidate.region].filter(Boolean).join(', ')}
                    {candidate.source === 'GTFS' ? ' · Imported schedule' : ''}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}

      {loading && value.trim().length >= 2 && candidates.length === 0 && (
        <span className="location-loading">Searching…</span>
      )}

      {hint}
    </div>
  )
}
