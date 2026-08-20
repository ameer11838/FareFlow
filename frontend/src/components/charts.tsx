import { useId, useState } from 'react'

/**
 * FareFlow's charts.
 *
 * <p>Two forms, deliberately. Everything Insights has to say is either a magnitude
 * comparison across a handful of categories or a share of one total, and a third
 * chart type would be decoration. There is no time series here because the API
 * returns a single week — inventing a trend line from one data point would be
 * exactly the kind of fabricated figure the backend refuses to produce.
 *
 * <p>Series colours come from the validated categorical palette
 * (`--viz-1…6`), assigned in fixed order and never cycled, so a provider keeps
 * its colour as other providers come and go. Values and labels are set in text
 * ink, never in the series colour: the swatch carries identity, the text carries
 * the number.
 */

export interface Datum {
  /** Stable identity. Drives colour assignment, so it must not be the rank. */
  id: string
  label: string
  value: number
  /** Pre-formatted for display — the chart never formats money itself. */
  display: string
  meta?: string
}

const SERIES = ['var(--viz-1)', 'var(--viz-2)', 'var(--viz-3)',
                'var(--viz-4)', 'var(--viz-5)', 'var(--viz-6)'] as const

/** Slot 6 is the quiet "everything else" bucket; a 7th series folds into it. */
export function seriesColor(index: number): string {
  return SERIES[Math.min(index, SERIES.length - 1)]
}

/**
 * Horizontal bars: the right form when categories have names worth reading and
 * the comparison is magnitude. Horizontal rather than vertical so a long provider
 * name sits on a baseline instead of being rotated 45°.
 *
 * <p>Every bar is directly labelled, so this needs no legend and no tooltip to be
 * readable — the hover layer adds the share, which would clutter the row if it
 * were always printed.
 */
export function BarChart({ data, total, emptyLabel = 'Nothing to show yet' }: {
  data: Datum[]
  /** The denominator for share. Omitted, share is computed from the bars. */
  total?: number
  emptyLabel?: string
}) {
  const [hovered, setHovered] = useState<string | null>(null)

  if (data.length === 0) {
    return <p className="chart-empty">{emptyLabel}</p>
  }

  const max = Math.max(...data.map((d) => d.value), 1)
  const denominator = total ?? data.reduce((sum, d) => sum + d.value, 0)

  return (
    <div className="chart-bars">
      {data.map((datum, index) => {
        const share = denominator > 0 ? datum.value / denominator : 0
        return (
          <div
            key={datum.id}
            className={`chart-bar-row${hovered === datum.id ? ' hovered' : ''}`}
            data-testid={`chart-bar-${datum.id}`}
            onMouseEnter={() => setHovered(datum.id)}
            onMouseLeave={() => setHovered(null)}
          >
            <div className="chart-bar-head">
              <span className="chart-bar-label">
                <span
                  className="chart-swatch"
                  style={{ background: seriesColor(index) }}
                  aria-hidden="true"
                />
                {datum.label}
              </span>
              <span className="chart-bar-value numeric">{datum.display}</span>
            </div>

            <div className="chart-track">
              <div
                className="chart-fill"
                style={{
                  width: `${(datum.value / max) * 100}%`,
                  background: seriesColor(index),
                }}
              />
            </div>

            <div className="chart-bar-meta">
              {datum.meta && <span>{datum.meta}</span>}
              {/* The share appears on hover rather than always: printed on every
                  row it competes with the value, which is the primary figure. */}
              <span className={`chart-bar-share${hovered === datum.id ? ' visible' : ''}`}>
                {Math.round(share * 100)}% of total
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )
}

/**
 * One bar split into shares of a single total.
 *
 * <p>A donut would need a legend, a centre label, and about 200px of height to say
 * the same thing this says in 10px. Segments are separated by a 2px surface gap so
 * two adjacent shares never read as one, and each is labelled beneath.
 */
export function ShareBar({ data, caption }: { data: Datum[]; caption?: string }) {
  const id = useId()
  const total = data.reduce((sum, d) => sum + d.value, 0)
  if (total <= 0) return null

  return (
    <div className="share">
      <div className="share-bar" role="img"
           aria-labelledby={`${id}-desc`}>
        {data.map((datum, index) => (
          <span
            key={datum.id}
            className="share-seg"
            style={{
              width: `${(datum.value / total) * 100}%`,
              background: seriesColor(index),
            }}
          />
        ))}
      </div>

      <p id={`${id}-desc`} className="visually-hidden">
        {data.map((d) => `${d.label} ${d.display}`).join(', ')}
      </p>

      <ul className="share-legend">
        {data.map((datum, index) => (
          <li key={datum.id}>
            <span className="chart-swatch" style={{ background: seriesColor(index) }}
                  aria-hidden="true" />
            <span className="share-legend-label">{datum.label}</span>
            <span className="share-legend-value numeric">{datum.display}</span>
          </li>
        ))}
      </ul>

      {caption && <p className="chart-caption">{caption}</p>}
    </div>
  )
}

/**
 * A comparison of two figures on one baseline — what was spent against what it
 * would have cost on the fastest routes.
 *
 * <p>Two bars on a shared scale rather than two axes: the whole point is that the
 * lengths are directly comparable, and a second scale would destroy that.
 */
export function ComparisonBars({ rows }: {
  rows: { id: string; label: string; value: number; display: string; tone: 'brand' | 'muted' | 'positive' }[]
}) {
  const max = Math.max(...rows.map((r) => r.value), 1)
  return (
    <div className="compare">
      {rows.map((row) => (
        <div key={row.id} className="compare-row" data-testid={`compare-${row.id}`}>
          <span className="compare-label">{row.label}</span>
          <div className="chart-track">
            <div className={`chart-fill fill-${row.tone}`} style={{ width: `${(row.value / max) * 100}%` }} />
          </div>
          <span className="compare-value numeric">{row.display}</span>
        </div>
      ))}
    </div>
  )
}
