import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { CloseIcon, SparkleIcon } from '../../components/Icons'
import { Logo } from '../../components/Logo'
import { formatCents, formatDateTime } from '../../lib/format'
import { useAssistant } from './AssistantContext'

const SUGGESTED_PROMPTS = [
  'Find me a cheaper route',
  "I'm in a rush",
  'Stay under $5',
  'Why this route?',
  'How am I doing on my budget?',
  'Show my spending this month',
]

/** Persistent, contextual assistant surface shared by every FareFlow page. */
export function AssistantPanel() {
  const assistant = useAssistant()
  const location = useLocation()
  const navigate = useNavigate()
  const [question, setQuestion] = useState('')
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const threadEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!assistant.open) return
    inputRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') assistant.closeAssistant()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [assistant.open, assistant.closeAssistant])

  useEffect(() => {
    if (assistant.open) threadEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [assistant.open, assistant.turns, assistant.asking])

  const submit = (text: string) => {
    if (!text.trim()) return
    setQuestion('')
    void assistant.ask(text)
  }

  const prompts = assistant.turns.length === 0 && assistant.config?.starters.length
    ? [...SUGGESTED_PROMPTS.slice(0, 3), ...assistant.config.starters].slice(0, 6)
    : assistant.followUps.length > 0 ? assistant.followUps : SUGGESTED_PROMPTS

  return (
    <>
      <button
        type="button"
        className={`assistant-fab${assistant.open ? ' is-open' : ''}`}
        onClick={assistant.toggleAssistant}
        aria-label={assistant.open ? 'Close Ask FareFlow' : 'Open Ask FareFlow'}
        aria-expanded={assistant.open}
      >
        <span className="assistant-fab-icon"><SparkleIcon size={19} /></span>
        <span>Ask FareFlow</span>
      </button>

      {assistant.open && (
        <button
          type="button"
          className="assistant-backdrop"
          aria-label="Close assistant"
          onClick={assistant.closeAssistant}
        />
      )}

      <aside
        className={`assistant-panel${assistant.open ? ' is-open' : ''}`}
        aria-hidden={!assistant.open}
        aria-label="Ask FareFlow assistant"
        data-testid="assistant"
      >
        <header className="assistant-header">
          <div className="assistant-identity">
            <span className="assistant-avatar"><Logo size={29} /></span>
            <span>
              <strong>Ask FareFlow</strong>
              <small><span className="assistant-live-dot" /> Context: {assistant.pageName}</small>
            </span>
          </div>
          <div className="assistant-header-actions">
            <button type="button" onClick={assistant.clearConversation}>New chat</button>
            <button type="button" className="icon-button" onClick={assistant.closeAssistant}
                    aria-label="Close Ask FareFlow">
              <CloseIcon size={18} />
            </button>
          </div>
        </header>

        <div className="assistant-thread" aria-live="polite">
          {assistant.turns.length === 0 && (
            <div className="assistant-welcome">
              <span className="assistant-welcome-mark"><SparkleIcon size={18} /></span>
              <h2>Where can FareFlow help?</h2>
              <p>
                I can work with the route on your screen, your transit preferences,
                completed trips, and transportation budget.
              </p>
            </div>
          )}

          {assistant.turns.map((turn, index) => (
            <div key={`${turn.role}-${index}`} className={`assistant-turn assistant-turn-${turn.role}`}>
              {turn.role === 'assistant' && <span className="assistant-mini-avatar"><Logo size={20} /></span>}
              <div>
                <span className="assistant-turn-label">{turn.role === 'user' ? 'You' : 'FareFlow'}</span>
                <p>{turn.content}</p>
              </div>
            </div>
          ))}

          {assistant.asking && (
            <div className="assistant-turn assistant-turn-assistant">
              <span className="assistant-mini-avatar"><Logo size={20} /></span>
              <div>
                <span className="assistant-turn-label">FareFlow</span>
                <div className="assistant-typing" aria-label="FareFlow is checking your data">
                  <span /><span /><span />
                </div>
              </div>
            </div>
          )}

          {assistant.latestRoutes && assistant.routeRevision > 0 && (
            <div className="assistant-action-result">
              <span>
                <strong>{location.pathname === '/plan' ? 'Route results updated' : 'Route options ready'}</strong>
                <small>{assistant.latestRoutes.origin.displayName} → {assistant.latestRoutes.destination.displayName}</small>
              </span>
              {location.pathname !== '/plan' && (
                <button type="button" onClick={() => navigate('/plan')}>View routes</button>
              )}
            </div>
          )}

          {assistant.suggestedTrips.length > 0 && (
            <div className="assistant-trip-results">
              <span className="assistant-section-label">Your trip records</span>
              {assistant.suggestedTrips.map((trip) => (
                <button key={trip.id} type="button" onClick={() => navigate(`/trips?trip=${trip.id}`)}>
                  <span><strong>{trip.origin} → {trip.destination}</strong><small>{trip.providerName} · {formatDateTime(trip.takenAt)}</small></span>
                  <b>{formatCents(trip.fareCents)}</b>
                </button>
              ))}
            </div>
          )}

          {assistant.error && <p className="assistant-error" role="alert">{assistant.error}</p>}
          {assistant.config?.available === false && (
            <p className="assistant-unavailable">{assistant.config.unavailableReason}</p>
          )}
          <div ref={threadEndRef} />
        </div>

        <div className="assistant-footer">
          {!assistant.asking && assistant.config?.available !== false && (
            <div className="assistant-prompts" aria-label="Suggested prompts">
              {prompts.map((prompt) => (
                <button key={prompt} type="button" onClick={() => submit(prompt)}>{prompt}</button>
              ))}
            </div>
          )}

          <div className="assistant-composer">
            <textarea
              ref={inputRef}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask about this route or your transit spending…"
              aria-label="Ask FareFlow"
              maxLength={1000}
              rows={1}
              disabled={assistant.asking || assistant.loadingConfig || assistant.config?.available === false}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  submit(question)
                }
              }}
            />
            <button type="button" onClick={() => submit(question)} aria-label="Send"
                    disabled={!question.trim() || assistant.asking || assistant.loadingConfig}>
              <span aria-hidden="true">↑</span>
            </button>
          </div>
          <p className="assistant-safety">FareFlow uses verified route and account data. Payments always require your confirmation.</p>
        </div>
      </aside>
    </>
  )
}
