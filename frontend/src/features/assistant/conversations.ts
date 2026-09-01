import { useCallback, useEffect, useState } from 'react'
import type { AssistantTurn } from '../../api/types'

/**
 * Saved conversations.
 *
 * <p>The assistant endpoint is stateless — <code>ask(question, history, context)</code>
 * — so the thread has always lived on the client. That means multiple named
 * conversations need no new backend and invent no server state: this is simply
 * the history the client was already holding, kept instead of discarded when a
 * rider starts a new one.
 *
 * <p>Deliberately local. A conversation about someone's spending is not
 * something to sync anywhere it was not asked to go.
 */
export interface Conversation {
  id: string
  /** Derived from the opening question — riders recognise their own words. */
  title: string
  updatedAt: number
  turns: AssistantTurn[]
}

const KEY = 'fareflow.assistant.conversations'
const LIMIT = 40

function read(): Conversation[] {
  try {
    const raw = localStorage.getItem(KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? (parsed as Conversation[]) : []
  } catch {
    // A corrupt or unavailable store must not take the assistant down with it.
    return []
  }
}

function write(items: Conversation[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(items.slice(0, LIMIT)))
  } catch {
    // Private browsing and quota errors are not worth failing a send over.
  }
}

/** First question, trimmed to something that fits a sidebar row. */
export function titleFor(turns: AssistantTurn[]): string {
  const first = turns.find((turn) => turn.role === 'user')?.content.trim()
  if (!first) return 'New conversation'
  return first.length > 52 ? `${first.slice(0, 51)}…` : first
}

export function useConversations() {
  const [items, setItems] = useState<Conversation[]>(() => read())

  useEffect(() => { write(items) }, [items])

  /** Creates or updates in place, keeping the list newest-first. */
  const save = useCallback((id: string, turns: AssistantTurn[]) => {
    if (turns.length === 0) return
    setItems((current) => {
      const rest = current.filter((item) => item.id !== id)
      return [{ id, title: titleFor(turns), updatedAt: Date.now(), turns }, ...rest]
    })
  }, [])

  const remove = useCallback((id: string) => {
    setItems((current) => current.filter((item) => item.id !== id))
  }, [])

  return { conversations: items, save, remove }
}
