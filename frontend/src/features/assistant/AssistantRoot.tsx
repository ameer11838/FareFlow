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
  if (loading || !user || needsOnboarding) return null
  return <AssistantPanel />
}
