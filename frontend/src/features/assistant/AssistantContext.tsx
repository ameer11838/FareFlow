import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react'
import { useLocation } from 'react-router-dom'
import { assistantApi } from '../../api'
import { ApiError } from '../../api/client'
import type {
  AssistantConfig, AssistantPageContext, AssistantTurn, JourneySearchResponse, Trip,
} from '../../api/types'
import { useAuth } from '../../hooks/useAuth'

export interface ActiveRouteContext {
  origin: string
  destination: string
  profile: string
  selectedJourneyId: string | null
}

interface AssistantValue {
  open: boolean
  config: AssistantConfig | null
  loadingConfig: boolean
  turns: AssistantTurn[]
  followUps: string[]
  asking: boolean
  error: string | null
  latestRoutes: JourneySearchResponse | null
  routeRevision: number
  suggestedTrips: Trip[]
  pageName: string
  openAssistant: () => void
  closeAssistant: () => void
  toggleAssistant: () => void
  ask: (question: string) => Promise<void>
  clearConversation: () => void
  /** Loads a saved conversation back into the live thread. */
  restoreConversation: (turns: AssistantTurn[]) => void
  setActiveRouteContext: (context: ActiveRouteContext | null) => void
}

const AssistantContext = createContext<AssistantValue | null>(null)

const PAGE_NAMES: Record<string, string> = {
  '/plan': 'Plan',
  '/wallet': 'Wallet',
  '/trips': 'Trips',
  '/insights': 'Insights',
  '/payments': 'Payment history',
  '/ledger': 'Payment history',
  '/settings': 'Settings',
}

/**
 * One assistant state for the entire signed-in app. The thread survives page
 * navigation and is restored for this browser session, but is not stored as a
 * financial or trip record on the server.
 */
export function AssistantProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [config, setConfig] = useState<AssistantConfig | null>(null)
  const [loadingConfig, setLoadingConfig] = useState(false)
  const [turns, setTurns] = useState<AssistantTurn[]>([])
  const [followUps, setFollowUps] = useState<string[]>([])
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeRouteContext, setActiveRouteContext] = useState<ActiveRouteContext | null>(null)
  const [latestRoutes, setLatestRoutes] = useState<JourneySearchResponse | null>(null)
  const [routeRevision, setRouteRevision] = useState(0)
  const [suggestedTrips, setSuggestedTrips] = useState<Trip[]>([])

  const pageName = PAGE_NAMES[location.pathname] ?? 'FareFlow'
  const storageKey = user ? `fareflow:assistant:${user.id}` : null

  useEffect(() => {
    if (!storageKey) {
      setTurns([])
      return
    }
    try {
      const stored = sessionStorage.getItem(storageKey)
      const parsed = stored ? JSON.parse(stored) as AssistantTurn[] : []
      setTurns(Array.isArray(parsed) ? parsed.filter(isTurn).slice(-40) : [])
    } catch {
      setTurns([])
    }
  }, [storageKey])

  useEffect(() => {
    if (!storageKey) return
    sessionStorage.setItem(storageKey, JSON.stringify(turns.slice(-40)))
  }, [storageKey, turns])

  const loadConfig = useCallback(async () => {
    if (config || loadingConfig || !user) return
    setLoadingConfig(true)
    setError(null)
    try {
      setConfig(await assistantApi.config())
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setLoadingConfig(false)
    }
  }, [config, loadingConfig, user])

  const openAssistant = useCallback(() => {
    setOpen(true)
    void loadConfig()
  }, [loadConfig])

  const closeAssistant = useCallback(() => setOpen(false), [])

  const ask = useCallback(async (question: string) => {
    const trimmed = question.trim()
    if (!trimmed || asking || config?.available === false) return

    const history = turns
    const context: AssistantPageContext = {
      pagePath: location.pathname,
      pageName,
      activeRouteSearch: activeRouteContext,
    }
    setTurns((current) => [...current, { role: 'user', content: trimmed }])
    setAsking(true)
    setError(null)
    try {
      const response = await assistantApi.ask(trimmed, history, context)
      setTurns((current) => [...current, { role: 'assistant', content: response.reply }])
      setFollowUps(response.followUps)
      setSuggestedTrips(response.trips ?? [])
      if (response.routes) {
        setLatestRoutes(response.routes)
        setRouteRevision((revision) => revision + 1)
      }
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setAsking(false)
    }
  }, [activeRouteContext, asking, config?.available, location.pathname, pageName, turns])

  const clearConversation = useCallback(() => {
    setTurns([])
    setFollowUps([])
    setSuggestedTrips([])
    setError(null)
    if (storageKey) sessionStorage.removeItem(storageKey)
  }, [storageKey])

  // Revisiting a saved conversation replaces the thread wholesale. Follow-ups and
  // suggested trips are cleared rather than restored: they were generated against
  // a page and a moment that have both moved on, and offering a stale "book this
  // route" action is worse than offering none.
  const restoreConversation = useCallback((restored: AssistantTurn[]) => {
    setTurns(restored)
    setFollowUps([])
    setSuggestedTrips([])
    setError(null)
  }, [])

  const value = useMemo<AssistantValue>(() => ({
    open,
    config,
    loadingConfig,
    turns,
    followUps,
    asking,
    error,
    latestRoutes,
    routeRevision,
    suggestedTrips,
    pageName,
    openAssistant,
    closeAssistant,
    toggleAssistant: open ? closeAssistant : openAssistant,
    ask,
    clearConversation,
    restoreConversation,
    setActiveRouteContext,
  }), [
    open, config, loadingConfig, turns, followUps, asking, error, latestRoutes,
    routeRevision, suggestedTrips, pageName, openAssistant, closeAssistant, ask,
    clearConversation, restoreConversation,
  ])

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>
}

export function useAssistant(): AssistantValue {
  const context = useContext(AssistantContext)
  if (!context) throw new Error('useAssistant must be used inside AssistantProvider')
  return context
}

function isTurn(value: unknown): value is AssistantTurn {
  if (!value || typeof value !== 'object') return false
  const turn = value as Partial<AssistantTurn>
  return (turn.role === 'user' || turn.role === 'assistant') && typeof turn.content === 'string'
}

function messageOf(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : 'Ask FareFlow could not answer just now.'
}
