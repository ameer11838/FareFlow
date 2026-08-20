import type { ApiError } from '../api/client'
import { AlertIcon, SearchIcon } from './Icons'

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite" aria-busy="true">
      <div className="stack" style={{ maxWidth: 380, margin: '0 auto', gap: 10 }}>
        <div className="skeleton" style={{ height: 12, width: '70%' }} />
        <div className="skeleton" style={{ height: 12, width: '90%' }} />
        <div className="skeleton" style={{ height: 12, width: '55%' }} />
      </div>
      <p className="muted" style={{ marginTop: 20, fontSize: 13 }}>{label}</p>
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <span className="state-icon"><AlertIcon size={20} /></span>
      <p className="state-title">{error.problem.title ?? 'Something went wrong'}</p>
      <p className="state-body">{error.message}</p>
      {onRetry && (
        <button className="btn" style={{ marginTop: 20 }} onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, description, action, icon }: {
  title: string
  description?: string
  action?: React.ReactNode
  icon?: React.ReactNode
}) {
  return (
    <div className="state">
      <span className="state-icon">{icon ?? <SearchIcon size={20} />}</span>
      <p className="state-title">{title}</p>
      {description && <p className="state-body">{description}</p>}
      {action && <div style={{ marginTop: 20 }}>{action}</div>}
    </div>
  )
}
