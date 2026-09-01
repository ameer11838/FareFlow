import { useEffect, useRef, useState } from 'react'
import { PlusIcon, SendIcon, TripsIcon } from '../../components/Icons'
import { Tile } from '../../components/Tile'
import { useAssistant } from './AssistantContext'
import { useConversations } from './conversations'

/**
 * Ask FareFlow, as a workspace rather than a widget.
 *
 * <p>Three regions, and each earns its place: a rail of past conversations
 * (money questions recur — "how am I doing on my budget" is asked weekly, and
 * losing last week's answer wastes the reasoning), a wide reading column, and a
 * composer that stays put.
 *
 * <p>The thread column is capped at a reading measure instead of stretching to
 * the viewport. Full-width prose on a 1500px display is unreadable, and the
 * assistant's answers are prose.
 */
export function AssistantPage() {
  const assistant = useAssistant()
  const { conversations, save, remove } = useConversations()
  const [activeId, setActiveId] = useState<string>(() => crypto.randomUUID())
  const [question, setQuestion] = useState('')
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const endRef = useRef<HTMLDivElement>(null)

  // Persist after every exchange, so a conversation survives a reload or a jump
  // to Plan mid-thread without the rider having to do anything.
  useEffect(() => {
    if (!assistant.asking) save(activeId, assistant.turns)
  }, [assistant.turns, assistant.asking, activeId, save])

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' })
  }, [assistant.turns, assistant.asking])

  const submit = (text: string) => {
    const trimmed = text.trim()
    if (!trimmed || assistant.asking) return
    setQuestion('')
    void assistant.ask(trimmed)
  }

  const startNew = () => {
    setActiveId(crypto.randomUUID())
    assistant.clearConversation()
    inputRef.current?.focus()
  }

  const openConversation = (id: string) => {
    const found = conversations.find((item) => item.id === id)
    if (!found) return
    setActiveId(id)
    assistant.restoreConversation(found.turns)
  }

  const starters = assistant.config?.starters?.length
    ? assistant.config.starters
    : ['How am I doing on my budget?', 'Find me a cheaper route to work',
       'What did I spend on transit last week?', 'Is a weekly pass worth it for me?']

  return (
    <div className="ask">
      <aside className="ask-rail" aria-label="Conversations">
        <div className="ask-rail-head">
          <span className="ask-rail-title">Conversations</span>
          <button type="button" className="btn btn-sm" onClick={startNew}>
            <PlusIcon size={15} /> New
          </button>
        </div>

        {conversations.length === 0 ? (
          <p className="ask-rail-empty">
            Your past questions will be listed here.
          </p>
        ) : (
          <ul className="ask-rail-list">
            {conversations.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={`ask-rail-item${item.id === activeId ? ' is-active' : ''}`}
                  onClick={() => openConversation(item.id)}
                >
                  <span className="ask-rail-item-title">{item.title}</span>
                  <span className="ask-rail-item-meta">
                    {item.turns.filter((turn) => turn.role === 'user').length} question
                    {item.turns.filter((turn) => turn.role === 'user').length === 1 ? '' : 's'}
                  </span>
                </button>
                <button type="button" className="ask-rail-remove"
                        aria-label={`Delete conversation: ${item.title}`}
                        onClick={() => remove(item.id)}>×</button>
              </li>
            ))}
          </ul>
        )}
      </aside>

      <section className="ask-main" aria-label="Conversation">
        <header className="ask-head">
          <div>
            <h1 className="ask-title">Ask FareFlow</h1>
            <p className="ask-sub">
              Answers come from your own routes, trips, spending and budget.
            </p>
          </div>
        </header>

        <div className="ask-thread">
          {assistant.turns.length === 0 ? (
            <div className="ask-intro">
              <span className="ask-intro-mark" aria-hidden="true">
                <Tile name="misc-branding-top-right/ai-assistant" size={44} />
              </span>
              <h2>What would you like to know?</h2>
              {/* No paragraph here. The header above already says answers come
                  from your own routes, trips, spending and budget, and the
                  starters below demonstrate the scope far better than a
                  sentence describing it does. */}
              <ul className="ask-starters">
                {starters.slice(0, 4).map((prompt) => (
                  <li key={prompt}>
                    <button type="button" onClick={() => submit(prompt)}>{prompt}</button>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <ol className="ask-turns">
              {assistant.turns.map((turn, index) => (
                <li key={index} className={`ask-turn ask-turn-${turn.role}`}>
                  <span className="ask-turn-who">
                    {turn.role === 'user' ? 'You' : 'FareFlow'}
                  </span>
                  <div className="ask-turn-body">{turn.content}</div>
                </li>
              ))}
              {assistant.asking && (
                <li className="ask-turn ask-turn-assistant">
                  <span className="ask-turn-who">FareFlow</span>
                  <div className="ask-turn-body ask-thinking">Thinking…</div>
                </li>
              )}
            </ol>
          )}

          {assistant.error && <p className="ask-error" role="alert">{assistant.error}</p>}

          {assistant.suggestedTrips.length > 0 && (
            <div className="ask-suggested">
              <span className="ask-suggested-label"><TripsIcon size={15} /> Related trips</span>
              {assistant.suggestedTrips.slice(0, 3).map((trip) => (
                <span key={trip.id} className="ask-suggested-item">
                  {trip.origin} → {trip.destination}
                </span>
              ))}
            </div>
          )}
          <div ref={endRef} />
        </div>

        <footer className="ask-composer">
          {assistant.followUps.length > 0 && (
            <div className="ask-followups">
              {assistant.followUps.slice(0, 3).map((prompt) => (
                <button key={prompt} type="button" onClick={() => submit(prompt)}>{prompt}</button>
              ))}
            </div>
          )}
          <div className="ask-input">
            <textarea
              ref={inputRef}
              rows={1}
              value={question}
              placeholder="Ask about a route, a charge, or your budget…"
              aria-label="Ask FareFlow a question"
              onChange={(event) => setQuestion(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  submit(question)
                }
              }}
            />
            <button type="button" className="btn btn-primary" disabled={!question.trim() || assistant.asking}
                    onClick={() => submit(question)}>
              <SendIcon size={16} /> Send
            </button>
          </div>
          {assistant.config && !assistant.config.available && (
            <p className="ask-unavailable">
              {assistant.config.unavailableReason
                ?? 'Ask FareFlow is not configured on this server.'}
            </p>
          )}
        </footer>
      </section>
    </div>
  )
}
