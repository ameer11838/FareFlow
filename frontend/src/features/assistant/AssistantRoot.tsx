import { useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { AssistantPanel } from './AssistantPanel'
import { AssistantProvider } from './AssistantContext'

/** Keeps the conversation mounted while the router swaps pages. */
export function AssistantRoot({ children }: { children: React.ReactNode }) {
  return (
    <AssistantProvider>
      {children}
      <AssistantVisibility />
    </AssistantProvider>
  )
}

function AssistantVisibility() {
  const { user, loading, needsOnboarding } = useAuth()
  const location = useLocation()
  if (loading || !user || needsOnboarding) return null
  // The shortcut is a way *to* the assistant. On the assistant's own page it
  // would be a button that goes where you already are, sitting on top of the
  // composer it duplicates.
  if (location.pathname.startsWith('/assistant')) return null
  return <AssistantPanel />
}
