import type { EChartsCoreOption } from 'echarts/core'
import type { SpendingHistory } from '../../api/types'
import { EChart, type ChartClickEvent } from '../../components/EChart'
import { useTheme } from '../../hooks/useTheme'
import { formatCents, formatMinutes } from '../../lib/format'
import type { AnalyticsFilters, AnalyticsGroup, AnalyticsView } from './analytics'

interface ChartTheme {
  text: string
  muted: string
  grid: string
  surface: string
  line: string
  area: string
}

const LIGHT: ChartTheme = {
  text: '#1b1824', muted: '#817c90', grid: '#e9e7ef', surface: '#ffffff',
  line: '#315fdd', area: 'rgba(49, 95, 221, .09)',
}

const DARK: ChartTheme = {
  text: '#f5f3f8', muted: '#918b9d', grid: '#2b2736', surface: '#191722',
  line: '#80a3ff', area: 'rgba(128, 163, 255, .10)',
}

/** Explanation first, one time-series, then directly labelled breakdowns. */
export function InsightsCharts({
  history, view, filters, onOperator, onMode, onBucket,
}: {
  history: SpendingHistory
  view: AnalyticsView
  filters: AnalyticsFilters
  onOperator: (operator: string | null) => void
  onMode: (mode: string | null) => void
  onBucket: (date: string | null) => void
}) {
  const { resolved } = useTheme()
  const theme = resolved === 'dark' ? DARK : LIGHT

  if (view.observations.length === 0) {
    return (
      <div className="analytics-filter-empty" role="status">
        <strong>No completed trips match these filters</strong>
        <span>Clear one of the active filters to see your transportation summary.</span>
      </div>
    )
  }

  return (
    <div className="analytics-focus">
      <PeriodBrief history={history} view={view} filters={filters} />

      <section className="analytics-trend" aria-labelledby="spending-trend-title">
        <header className="analytics-panel-head">
          <div>
            <h3 id="spending-trend-title">Spending over time</h3>
            <p>Only completed trips are included. Select a point to focus on that period.</p>
          </div>
        </header>
        {view.distinctTripDays >= 3 ? (
          <EChart
            option={spendingOption(history, view, theme)}
            ariaLabel={`Spending over ${history.rangeName}`}
            testId="chart-spending"
            height={270}
            onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
          />
        ) : view.distinctTripDays === 2 ? (
          <SparseSpending view={view} />
        ) : (
          <div className="analytics-history-short" data-testid="history-sparse-state">
            <strong>One active travel day</strong>
            <p>
              {formatCents(view.totals.spentCents)} across {view.totals.tripCount} trip
              {view.totals.tripCount === 1 ? '' : 's'}. A trend appears after trips on three days.
            </p>
          </div>
        )}
      </section>

      <section className="analytics-breakdown" aria-labelledby="spend-breakdown-title">
        <header className="analytics-panel-head">
          <div>
            <h3 id="spend-breakdown-title">What shaped your spending</h3>
            <p>Direct labels replace separate charts and legends. Select a row to filter.</p>
          </div>
        </header>
        <div className="breakdown-columns">
          <BreakdownList title="Operators" rows={view.byOperator}
                         total={view.totals.spentCents} selected={filters.operator}
                         onSelect={onOperator} />
          <BreakdownList title="Transit modes" rows={view.byMode}
                         total={view.totals.spentCents} selected={filters.mode}
                         onSelect={onMode} />
        </div>
      </section>
    </div>
  )
}

function PeriodBrief({ history, view, filters }: {
  history: SpendingHistory
  view: AnalyticsView
  filters: AnalyticsFilters
}) {
  const topOperator = view.byOperator[0]
  const topMode = view.byMode[0]
  const topShare = topOperator && view.totals.spentCents > 0
    ? Math.round((topOperator.spentCents / view.totals.spentCents) * 100) : null
  const filtered = Boolean(filters.operator || filters.mode || filters.bucketDate)
  const comparison = !filtered ? history.comparison : null

  return (
    <section className="period-brief" aria-labelledby="period-brief-title">
      <div className="period-brief-lead">
        <span>Your period, summarized</span>
        <h3 id="period-brief-title">
          You spent {formatCents(view.totals.spentCents)} across {view.totals.tripCount} completed
          {' '}trip{view.totals.tripCount === 1 ? '' : 's'}.
        </h3>
        <p>
          {view.totals.averageFareCents === null
            ? 'An average fare will appear after another completed trip.'
            : `The average trip cost ${formatCents(view.totals.averageFareCents)}`}
          {view.totals.averageDurationMinutes === null
            ? '.' : ` and took ${formatMinutes(view.totals.averageDurationMinutes)}.`}
        </p>
      </div>
      <dl className="period-brief-facts">
        <div>
          <dt>Main cost driver</dt>
          <dd>{topOperator
            ? `${topOperator.name}${topShare === null ? '' : ` · ${topShare}% of spend`}`
            : 'Not enough data'}</dd>
        </div>
        <div>
          <dt>Most used mode</dt>
          <dd>{topMode
            ? `${topMode.name} · ${topMode.tripCount} trip${topMode.tripCount === 1 ? '' : 's'}`
            : 'Not enough data'}</dd>
        </div>
        <div>
          <dt>Compared with before</dt>
          <dd>{comparison ? comparisonSentence(comparison)
            : filtered ? 'Comparison paused while filters are active' : 'No earlier period to compare'}</dd>
        </div>
      </dl>
    </section>
  )
}

function comparisonSentence(comparison: NonNullable<SpendingHistory['comparison']>): string {
  if (comparison.spentChangeCents === 0) return 'Spending was unchanged'
  return `${formatCents(Math.abs(comparison.spentChangeCents))} ${
    comparison.spentChangeCents > 0 ? 'more' : 'less'} spending`
}

function BreakdownList({ title, rows, total, selected, onSelect }: {
  title: string
  rows: AnalyticsGroup[]
  total: number
  selected: string | null
  onSelect: (id: string | null) => void
}) {
  return (
    <div className="breakdown-list">
      <h4>{title}</h4>
      <ol>
        {rows.slice(0, 5).map((row) => {
          const share = total > 0 ? Math.round((row.spentCents / total) * 100) : 0
          const active = selected === row.id
          return (
            <li key={row.id}>
              <button type="button" aria-pressed={active}
                      onClick={() => onSelect(active ? null : row.id)}>
                <span className="breakdown-label">
                  <strong>{row.name}</strong>
                  <small>{row.tripCount} trip{row.tripCount === 1 ? '' : 's'} · {share}%</small>
                </span>
                <span className="breakdown-value numeric">{formatCents(row.spentCents)}</span>
                <span className="breakdown-track" aria-hidden="true">
                  <span style={{ width: `${share}%` }} />
                </span>
              </button>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

function SparseSpending({ view }: { view: AnalyticsView }) {
  const points = view.buckets.filter((bucket) => bucket.tripCount > 0)
  const first = points[0]
  const last = points[points.length - 1]
  return (
    <div className="sparse-series" data-testid="sparse-series">
      <ol className="sparse-points">
        {points.map((point) => (
          <li key={point.date}>
            <span className="sparse-point-label">{point.label}</span>
            <span className="sparse-point-value numeric">{formatCents(point.spentCents)}</span>
          </li>
        ))}
      </ol>
      {first && last && first.spentCents !== last.spentCents && (
        <p className={`sparse-delta${last.spentCents > first.spentCents ? ' is-up' : ' is-down'}`}>
          {formatCents(Math.abs(last.spentCents - first.spentCents))}{' '}
          {last.spentCents > first.spentCents ? 'more' : 'less'} on the later day
        </p>
      )}
    </div>
  )
}

function spendingOption(
  history: SpendingHistory,
  view: AnalyticsView,
  theme: ChartTheme,
): EChartsCoreOption {
  return {
    animationDuration: 220,
    color: [theme.line],
    grid: { left: 56, right: 16, top: 14, bottom: 36 },
    tooltip: {
      trigger: 'axis', backgroundColor: theme.surface, borderColor: theme.grid,
      textStyle: { color: theme.text, fontSize: 12 },
      valueFormatter: (value: unknown) => formatCents(Number(value)),
    },
    xAxis: {
      type: 'category', data: view.buckets.map((bucket) => bucket.label), boundaryGap: false,
      axisLine: { lineStyle: { color: theme.grid } }, axisTick: { show: false },
      axisLabel: { color: theme.muted, fontSize: 10, hideOverlap: true },
    },
    yAxis: {
      type: 'value', min: 0, splitLine: { lineStyle: { color: theme.grid } },
      axisLabel: { color: theme.muted, fontSize: 10, formatter: (value: number) => `$${value / 100}` },
    },
    series: [{
      name: 'Spending', type: 'line', smooth: 0.22,
      data: view.buckets.map((bucket) => ({ value: bucket.spentCents, filterId: bucket.date })),
      showSymbol: history.buckets.length <= 14, symbol: 'circle', symbolSize: 6,
      lineStyle: { width: 2.5, color: theme.line },
      itemStyle: { color: theme.line, borderColor: theme.surface, borderWidth: 2 },
      areaStyle: { color: theme.area }, emphasis: { focus: 'series' },
    }],
  }
}

function selectBucket(
  event: ChartClickEvent,
  current: string | null,
  onSelect: (date: string | null) => void,
) {
  const data = event.data as { filterId?: unknown } | undefined
  const id = typeof data?.filterId === 'string' ? data.filterId : null
  if (id) onSelect(current === id ? null : id)
}
