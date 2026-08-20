export function StatTile({ label, value, caption, tone = 'default', accent = false }: {
  label: string
  value: string
  caption?: string
  tone?: 'default' | 'muted' | 'positive'
  accent?: boolean
}) {
  return (
    <div className={`card stat${accent ? ' stat-accent' : ''}`}>
      <span className="stat-label">{label}</span>
      <span className={`stat-value numeric${tone === 'muted' ? ' muted' : tone === 'positive' ? ' positive' : ''}`}>
        {value}
      </span>
      {caption && <span className="stat-caption">{caption}</span>}
    </div>
  )
}
