import { useState } from 'react'
import type { ContextProfileOption } from '../../api/types'
import { SparkleIcon } from '../../components/Icons'

/**
 * "Ask FareFlow" — the natural-language entry point, without a language model.
 *
 * <p>Today it maps a fixed set of phrases to existing profile ids through the table
 * below. That is deliberately not AI: it is the same interaction surface an LLM will
 * sit behind, so the contract can be proven before a model is involved.
 *
 * <p><strong>The rule this preserves:</strong> it only ever selects a profile id. It
 * never computes a fare, compares routes, or decides anything financial — the Java
 * engine does all of that, exactly as when a stance button is clicked directly.
 */
const PHRASES: { text: string; profile: string }[] = [
  { text: "I'm running late", profile: 'RUSH' },
  { text: 'I want to save money', profile: 'SAVE_MONEY' },
  { text: "I don't care about time", profile: 'SAVE_MONEY' },
  { text: "I don't want transfers", profile: 'FEWER_TRANSFERS' },
  { text: 'Just pick something sensible', profile: 'BALANCED' },
]

export function AssistantPanel({ profiles, selectedProfile, onSelectProfile }: {
  profiles: ContextProfileOption[]
  selectedProfile: string
  onSelectProfile: (id: string) => void
}) {
  const [open, setOpen] = useState(false)

  const profileName = (id: string) =>
    profiles.find((profile) => profile.id === id)?.displayName ?? id

  return (
    <div className="ask">
      <button
        type="button"
        className="ask-trigger"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        <SparkleIcon size={15} />
        <span>Ask FareFlow</span>
        <span className="ask-hint">Preview</span>
      </button>

      {open && (
        <div className="ask-sheet" data-testid="assistant">
          <p className="ask-lede">
            Say what matters. FareFlow picks a stance — the routing engine still decides.
          </p>

          <ul className="ask-list">
            {PHRASES.map((phrase) => (
              <li key={phrase.text}>
                <button
                  type="button"
                  className={`ask-phrase${phrase.profile === selectedProfile ? ' active' : ''}`}
                  onClick={() => { onSelectProfile(phrase.profile); setOpen(false) }}
                >
                  <span>&ldquo;{phrase.text}&rdquo;</span>
                  <span className="ask-maps">{profileName(phrase.profile)}</span>
                </button>
              </li>
            ))}
          </ul>

          <p className="ask-note">
            No language model yet — these map to stances through a fixed table. When one
            arrives it will produce the same thing: a stance, never a fare or a route.
          </p>
        </div>
      )}
    </div>
  )
}
