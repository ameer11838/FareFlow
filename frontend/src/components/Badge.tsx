import type { RecommendationLabel } from '../api/types'
import { labelText } from '../lib/format'

const CLASS_BY_LABEL: Record<RecommendationLabel, string> = {
  BEST_VALUE: 'badge-best',
  CHEAPEST: 'badge-cheap',
  FASTEST: 'badge-fast',
}

export function LabelBadge({ label }: { label: RecommendationLabel }) {
  return <span className={`badge ${CLASS_BY_LABEL[label]}`}>{labelText(label)}</span>
}

export function Badge({ children, tone = 'neutral' }: {
  children: React.ReactNode
  tone?: 'neutral' | 'danger' | 'positive' | 'solid'
}) {
  const cls =
    tone === 'danger' ? 'badge-danger'
    : tone === 'positive' ? 'badge-cheap'
    : tone === 'solid' ? 'badge-solid'
    : 'badge-neutral'
  return <span className={`badge ${cls}`}>{children}</span>
}
