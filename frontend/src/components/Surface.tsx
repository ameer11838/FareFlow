/**
 * The surfaces every page is built from.
 *
 * <p>Before this existed each page hand-rolled its own `<div className="card">`,
 * which is how an app ends up with four card paddings and three header layouts.
 * A page now picks a level of prominence and the system supplies the geometry.
 */

/**
 * @param tone     'plain' is the default white card. 'navy' is a dark panel for
 *                 the one figure on a page that matters most. 'quiet' recedes,
 *                 for supporting material that should not compete.
 * @param interactive adds hover elevation. Only for surfaces that actually do
 *                 something when clicked — a card that lifts but cannot be
 *                 pressed is a promise the UI does not keep.
 */
export function Card({
  tone = 'plain', interactive = false, className = '', children, ...rest
}: {
  tone?: 'plain' | 'navy' | 'quiet'
  interactive?: boolean
  className?: string
  children: React.ReactNode
} & Omit<React.HTMLAttributes<HTMLDivElement>, 'className' | 'children'>) {
  return (
    <div
      className={`surface surface-${tone}${interactive ? ' surface-interactive' : ''} ${className}`.trim()}
      {...rest}
    >
      {children}
    </div>
  )
}

/** A titled block. The eyebrow/title/action arrangement is defined once here. */
export function Section({ title, caption, action, children, id }: {
  title?: string
  caption?: string
  action?: React.ReactNode
  children: React.ReactNode
  id?: string
}) {
  return (
    <section className="section" id={id}>
      {(title || action) && (
        <header className="section-head">
          <div className="section-head-text">
            {title && <h2 className="section-title">{title}</h2>}
            {caption && <p className="section-sub">{caption}</p>}
          </div>
          {action && <div className="section-action">{action}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

/**
 * A labelled figure.
 *
 * <p>`emphasis` is the hierarchy control: 'hero' for the number a page exists to
 * show, 'default' for supporting figures. Without it every metric on a page is
 * set at the same size and the page says nothing about what matters.
 */
export function Metric({
  label, value, caption, tone = 'default', emphasis = 'default', icon, trend,
}: {
  label: string
  value: string
  caption?: React.ReactNode
  tone?: 'default' | 'muted' | 'positive' | 'negative'
  emphasis?: 'hero' | 'default'
  icon?: React.ReactNode
  trend?: React.ReactNode
}) {
  return (
    <div className={`metric metric-${emphasis}`}>
      <div className="metric-head">
        {icon && <span className="metric-icon" aria-hidden="true">{icon}</span>}
        <span className="metric-label">{label}</span>
      </div>
      <span className={`metric-value numeric tone-${tone}`}>{value}</span>
      {trend}
      {caption && <span className="metric-caption">{caption}</span>}
    </div>
  )
}

/**
 * A horizontal meter.
 *
 * <p>`value` and `max` are the real numbers, not a pre-computed percentage, so the
 * component can report the figures to assistive technology as well as draw them.
 * Over-budget is clamped visually but never silently: `over` re-colours the fill
 * so a full bar and an overrun do not look identical.
 */
export function Meter({ value, max, label, over = false, tone = 'brand' }: {
  value: number
  max: number
  label: string
  over?: boolean
  tone?: 'brand' | 'positive' | 'negative'
}) {
  const ratio = max > 0 ? Math.min(Math.max(value / max, 0), 1) : 0
  return (
    <div
      className={`meter meter-${tone}${over ? ' meter-over' : ''}`}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      aria-label={label}
    >
      <div className="meter-fill" style={{ width: `${ratio * 100}%` }} />
    </div>
  )
}

/**
 * A block that stands in for content while it loads.
 *
 * <p>Sized by the caller to match what is coming, so the page does not reflow when
 * the data lands. A spinner in the same place would tell the user less and still
 * cost them the layout shift.
 */
export function Skeleton({ width = '100%', height = 14, radius = 'var(--radius-sm)' }: {
  width?: number | string
  height?: number | string
  radius?: string
}) {
  return (
    <span
      className="skeleton-block"
      style={{ width, height, borderRadius: radius }}
      aria-hidden="true"
    />
  )
}
