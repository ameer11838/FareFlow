import { useRef, useState } from 'react'
import { assistantApi } from '../../api'
import { ApiError } from '../../api/client'
import type { AssistantConfig, AssistantTurn, JourneySearchResponse } from '../../api/types'
import { SparkleIcon } from '../../components/Icons'

const FALLBACK_STARTERS = [
  "What's the cheapest way to my usual destination?",
  'Can I afford my commute for the rest of the week?',
  'How has my commute changed this month?',
]

/**
 * Conversational route and transportation-finance control surface.
 * Route objects and financial figures come from FareFlow services on the server;
 * a planning answer hands that same result to the map and comparison drawer.
 */
export function AssistantPanel({ onRoutes }: {
  onRoutes: (routes: JourneySearchResponse) => void
}) {
  const [open, setOpen] = useState(false)
  const [config, setConfig] = useState<AssistantConfig | null>(null)
  const [loadingConfig, setLoadingConfig] = useState(false)
  const [turns, setTurns] = useState<AssistantTurn[]>([])
  const [followUps, setFollowUps] = useState<string[]>([])
  const [question, setQuestion] = useState('')
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const toggle = async () => {
    const next = !open
    setOpen(next)
    if (!next) return

    window.setTimeout(() => inputRef.current?.focus(), 0)
    if (config || loadingConfig) return
    setLoadingConfig(true)
    try {
      setConfig(await assistantApi.config())
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setLoadingConfig(false)
    }
  }

  const ask = async (text: string) => {
    const trimmed = text.trim()
    if (!trimmed || asking || config?.available === false) return

    const history = turns
    setTurns((current) => [...current, { role: 'user', content: trimmed }])
    setQuestion('')
    setAsking(true)
    setError(null)
    try {
      const response = await assistantApi.ask(trimmed, history)
      setTurns((current) => [...current, { role: 'assistant', content: response.reply }])
      setFollowUps(response.followUps)
      if (response.routes) onRoutes(response.routes)
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setAsking(false)
    }
  }

  const starters = config?.starters.length ? config.starters : FALLBACK_STARTERS

  return (
    <div className={`ask${open ? ' ask-open' : ''}`}>
      <button type="button" className="ask-trigger" onClick={() => void toggle()} aria-expanded={open}>
        <SparkleIcon size={16} />
        <span>Ask FareFlow</span>
        <span className="ask-hint">AI transit + budget</span>
      </button>

      {open && (
        <div className="ask-sheet" data-testid="assistant">
          <p className="ask-lede">
            Ask about a route, your commute, or what you can afford. Route answers update the map.
          </p>

          {loadingConfig && <p className="ask-status">Connecting to your travel data…</p>}

          {config?.available === false ? (
            <p className="ask-unavailable">{config.unavailableReason}</p>
          ) : (
            <>
              {turns.length > 0 && (
                <div className="ask-thread" aria-live="polite">
                  {turns.map((turn, index) => (
                    <div key={index} className={`ask-turn ask-turn-${turn.role}`}>
                      <span className="ask-turn-label">{turn.role === 'user' ? 'You' : 'FareFlow'}</span>
                      <p>{turn.content}</p>
                    </div>
                  ))}
                  {asking && (
                    <div className="ask-turn ask-turn-assistant">
                      <span className="ask-turn-label">FareFlow</span>
                      <p className="ask-thinking">Checking your routes and spending…</p>
                    </div>
                  )}
                </div>
              )}

              {turns.length === 0 && !loadingConfig && (
                <div className="ask-starters">
                  {starters.slice(0, 4).map((starter) => (
                    <button key={starter} type="button" onClick={() => void ask(starter)}>{starter}</button>
                  ))}
                </div>
              )}

              {followUps.length > 0 && !asking && (
                <div className="ask-followups">
                  {followUps.map((item) => (
                    <button key={item} type="button" onClick={() => void ask(item)}>{item}</button>
                  ))}
                </div>
              )}

              <div className="ask-composer">
                <input
                  ref={inputRef}
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  placeholder="Ask about a route or your spending…"
                  aria-label="Ask FareFlow"
                  maxLength={1000}
                  disabled={asking || loadingConfig}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault()
                      void ask(question)
                    }
                  }}
                />
                <button type="button" onClick={() => void ask(question)}
                        disabled={!question.trim() || asking || loadingConfig}>
                  {asking ? 'Asking…' : 'Send'}
                </button>
              </div>
            </>
          )}

          {error && <p className="ask-error" role="alert">{error}</p>}
          <p className="ask-note">Answers use your FareFlow profile, completed trips, and priced route data.</p>
        </div>
      )}
    </div>
  )
}

function messageOf(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : 'Ask FareFlow could not answer just now.'
}
