import { lazy, Suspense, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { insightsApi, passesApi, usersApi } from '../../api'
import type {
  HistoryRange, Insights, InsightsPersonalization, PassRecommendation, SpendingHistory,
} from '../../api/types'
import { seriesColor } from '../../components/charts'
import { RouteIcon, WalletIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Metric, Skeleton } from '../../components/Surface'
import { EmptyState, ErrorState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { formatCents, formatMinutes, formatOptionalCents, formatPercent } from '../../lib/format'
import { buildAnalyticsView, type AnalyticsFilters } from './analytics'

const InsightsCharts = lazy(() => import('./InsightsCharts').then((module) => ({
  default: module.InsightsCharts,
})))

/**
 * Transportation financial intelligence.
 *
 * <p>Every figure is derived from real trips and ledger entries. Where the backend
 * returns null — no trips, no comparable baseline, only one operator used — the
 * module says why rather than rendering a zero. Nothing here is invented to fill
 * a layout, which is also why there is no trend line: the API returns one week,
 * and a trend drawn from one point would be a decoration pretending to be data.
 *
 * <p>The page answers four questions in order: how am I travelling, what am I
 * spending, where is it going, and how could FareFlow save me money.
 */
export function InsightsPage() {
  const { user, refresh } = useAuth()
  const { data, loading, error, refetch } = useAsync<Insights>(() => insightsApi.get(), [])
  const passes = useAsync<PassRecommendation>(() => passesApi.recommendation(), [])

  if (loading) return <InsightsSkeleton />
  if (error) {
    return (
      <div className="page">
        <Card className="card-body"><ErrorState error={error} onRetry={refetch} /></Card>
      </div>
    )
  }
  if (!data) return null

  const noTrips = data.tripCount === 0

  return (
    <div className="page">
      <PageHeader
        eyebrow="Insights"
        title={user ? `${greeting()}, ${user.name.split(' ')[0]}` : 'Insights'}
        subtitle="How your transportation spending is tracking this week."
        actions={<BudgetEditor current={data.weeklyBudgetCents} onSaved={() => { refresh(); refetch() }} />}
      />

      {noTrips ? (
        <>
          <Card>
            <EmptyState
              icon={<RouteIcon size={26} />}
              title="Your insights are getting ready"
              description="Complete a few trips and FareFlow will start identifying spending patterns, the operators you rely on, and opportunities to save. Nothing here is simulated — it fills in from real trips."
              action={<Link className="btn btn-primary btn-lg" to="/plan">Plan a trip</Link>}
            />
          </Card>
        </>
      ) : (
        <>
          <Headline data={data} />

          <HistoryModule weekly={data} />

          <section className="band">
            <div className="band-head">
              <h2 className="band-title">This week</h2>
              <span className="band-note">Calculated from completed trips and payments</span>
            </div>
            <div className="figures-row">
              <Metric label="Spent" value={formatCents(data.spentCents)}
                      caption={`${data.tripCount} completed trip${data.tripCount === 1 ? '' : 's'}`} />
              <Metric label="Budget"
                      value={formatOptionalCents(data.weeklyBudgetCents)}
                      tone={data.weeklyBudgetCents === null ? 'muted' : 'default'}
                      caption={data.budgetUtilization === null
                        ? 'Not set' : `${formatPercent(data.budgetUtilization)} used`} />
              <Metric label="Average fare"
                      value={formatOptionalCents(data.averageFareCents, '—')}
                      tone={data.averageFareCents === null ? 'muted' : 'default'}
                      caption="Per trip" />
              <Metric label="Average trip"
                      value={data.averageDurationMinutes === null
                        ? '—' : formatMinutes(data.averageDurationMinutes)}
                      tone={data.averageDurationMinutes === null ? 'muted' : 'default'}
                      caption="Door to door" />
            </div>
          </section>

          <section className="band">
            <div className="band-head">
              <h2 className="band-title">Operators</h2>
              <span className="band-note">Where this week's fares went</span>
            </div>
            <OperatorTable data={data} />
          </section>

          <section className="band">
            <div className="band-head">
              <h2 className="band-title">Trade-offs</h2>
              <span className="band-note">What choosing value instead of speed cost and saved</span>
            </div>
            <div className="figures-open">
              <Metric
                label="Saved vs the fastest route"
                value={formatOptionalCents(data.savedVersusFastestCents, '—')}
                tone={data.savedVersusFastestCents !== null && data.savedVersusFastestCents > 0
                  ? 'positive' : 'muted'}
                caption={data.savedVersusFastestCents === null
                  ? 'Needs a comparable alternative route'
                  : 'Across this week\u2019s completed trips'}
              />
              <Metric
                label="Extra time spent"
                value={data.minutesTradedForSavings === null
                  ? '—' : formatMinutes(data.minutesTradedForSavings)}
                tone={data.minutesTradedForSavings === null ? 'muted' : 'default'}
                caption="The time cost of those savings"
              />
              <Metric
                label="Projected monthly"
                value={formatOptionalCents(data.projectedMonthlyCents, '—')}
                tone={data.projectedMonthlyCents === null ? 'muted' : 'default'}
                caption={data.projectedMonthlyCents === null
                  ? 'Needs a week with spending'
                  : 'Straight-line from this week alone'}
              />
            </div>
          </section>

          {passes.data && <PassModule recommendation={passes.data} />}
        </>
      )}
    </div>
  )
}

const RANGE_NAMES: Record<HistoryRange, string> = {
  '7d': '7 days', '30d': '30 days', '3m': '3 months', '1y': '1 year',
}

const EMPTY_FILTERS: AnalyticsFilters = { operator: null, mode: null, bucketDate: null }

function HistoryModule({ weekly }: { weekly: Insights }) {
  const [range, setRange] = useState<HistoryRange>('30d')
  const [filters, setFilters] = useState<AnalyticsFilters>(EMPTY_FILTERS)
  const history = useAsync<SpendingHistory>(() => insightsApi.history(range), [range])

  useEffect(() => {
    const available = history.data?.rangesWithData ?? []
    if (available.length > 0 && !available.includes(range)) {
      setRange(available.includes('30d') ? '30d' : available[0])
    }
  }, [history.data, range])

  if (history.loading && !history.data) {
    return <section className="band"><Skeleton height={250} /></section>
  }
  if (history.error) {
    return (
      <section className="band">
        <ErrorState error={history.error} onRetry={history.refetch} />
      </section>
    )
  }
  const data = history.data
  if (!data || (!data.hasData && data.rangesWithData.length === 0)) return null

  const periods = data.rangesWithData
  const view = buildAnalyticsView(data, filters)
  const activeFilters = [
    filters.operator
      ? data.byOperator.find((operator) => operator.provider === filters.operator)?.providerName
        ?? filters.operator : null,
    filters.mode
      ? data.byMode.find((mode) => mode.mode === filters.mode)?.modeName ?? filters.mode : null,
    filters.bucketDate
      ? data.buckets.find((bucket) => bucket.date === filters.bucketDate)?.label
        ?? filters.bucketDate : null,
  ].filter((value): value is string => Boolean(value))

  const setOperator = (operator: string | null) =>
    setFilters((current) => ({ ...current, operator }))
  const setMode = (mode: string | null) =>
    setFilters((current) => ({ ...current, mode }))
  const setBucket = (bucketDate: string | null) =>
    setFilters((current) => ({ ...current, bucketDate }))

  return (
    <section className="band history-module" data-testid="history-module">
      <div className="band-head history-head">
        <div>
          <h2 className="band-title">Transportation analytics</h2>
          <span className="band-note">
            Completed trips only · {data.startDate} to {data.endDate} · click chart data to cross-filter
          </span>
        </div>
        <div className="range-tabs" aria-label="History period">
          {periods.map((period) => (
            <button key={period} type="button" aria-pressed={range === period}
                    onClick={() => {
                      setRange(period)
                      setFilters((current) => ({ ...current, bucketDate: null }))
                    }}>{RANGE_NAMES[period]}</button>
          ))}
        </div>
      </div>

      <div className="analytics-toolbar" aria-label="Analytics filters">
        <label>
          <span>Operator</span>
          <select aria-label="Filter by operator" value={filters.operator ?? ''}
                  onChange={(event) => setOperator(event.target.value || null)}>
            <option value="">All operators</option>
            {data.byOperator.map((operator) => (
              <option key={operator.provider} value={operator.provider}>{operator.providerName}</option>
            ))}
          </select>
        </label>
        <label>
          <span>Mode</span>
          <select aria-label="Filter by transit mode" value={filters.mode ?? ''}
                  onChange={(event) => setMode(event.target.value || null)}>
            <option value="">All public transit</option>
            {data.byMode.map((mode) => (
              <option key={mode.mode} value={mode.mode}>{mode.modeName}</option>
            ))}
          </select>
        </label>
        <div className="analytics-filter-status" aria-live="polite">
          {activeFilters.length > 0 ? (
            <>
              <span>Showing {activeFilters.join(' · ')}</span>
              <button type="button" onClick={() => setFilters(EMPTY_FILTERS)}>Clear filters</button>
            </>
          ) : <span>Showing all completed trips in this period</span>}
        </div>
      </div>

      {data.hasData ? (
        <>
          <div className="figures-row analytics-summary">
            <Metric label="Filtered spend" value={formatCents(view.totals.spentCents)}
                    caption={`${view.totals.tripCount} completed trip${view.totals.tripCount === 1 ? '' : 's'}`} />
            <Metric label="Average fare" value={formatOptionalCents(view.totals.averageFareCents, '—')}
                    caption="Across the current dashboard view" />
            <Metric label="Cost per mile" value={formatOptionalCents(view.totals.costPerMileCents, '—')}
                    caption={view.totals.costPerMileCents === null
                      ? 'Needs trips with recorded distance' : 'Usage-priced trips with distance'} />
            <Metric label="Savings" value={formatOptionalCents(view.totals.savedCents, '—')}
                    caption="Only where a comparison existed" />
          </div>
          <HistoryDecision data={data} view={view} filtered={activeFilters.length > 0} />
          <Suspense fallback={<Skeleton height={420} />}>
            <InsightsCharts
              history={data}
              weekly={weekly}
              view={view}
              filters={filters}
              onOperator={setOperator}
              onMode={setMode}
              onBucket={setBucket}
            />
          </Suspense>

          {view.routes.length > 0 && (
            <div className="history-routes">
              <h3>Most-used routes in this view</h3>
              <div className="table-wrap">
                <table className="data-table">
                  <thead><tr><th>Route</th><th>Operator</th><th className="col-num">Trips</th><th className="col-total">Average fare</th></tr></thead>
                  <tbody>{view.routes.map((route) => (
                    <tr key={`${route.origin}|${route.destination}|${route.provider}`}>
                      <td className="col-name">{route.origin} → {route.destination}</td>
                      <td>{route.provider}</td>
                      <td className="col-num">{route.tripCount}</td>
                      <td className="col-total">{formatCents(route.averageFareCents)}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </div>
          )}
        </>
      ) : (
        <p className="chart-empty">There are no completed trips in this period.</p>
      )}
    </section>
  )
}

function HistoryDecision({ data, view, filtered }: {
  data: SpendingHistory
  view: ReturnType<typeof buildAnalyticsView>
  filtered: boolean
}) {
  const comparison = data.comparison
  const topOperator = view.byOperator[0]
  return (
    <div className="history-decision">
      <div>
        <span>What changed</span>
        <strong>{!filtered && comparison
          ? changeSentence(comparison)
          : filtered
            ? `The dashboard is cross-filtered to ${view.totals.tripCount} completed trip${view.totals.tripCount === 1 ? '' : 's'}.`
            : 'There is not enough earlier history for a fair comparison.'}</strong>
      </div>
      <div>
        <span>Why it matters</span>
        <strong>{topOperator
          ? `${topOperator.name} accounts for ${view.totals.spentCents === 0 ? 0 : Math.round((topOperator.spentCents / view.totals.spentCents) * 100)}% of spending in this view.`
          : 'No operator accounts for recorded spending in this period.'}</strong>
      </div>
      <div>
        <span>What you can do</span>
        <strong><Link to="/plan?profile=SAVE_MONEY">Compare a cheaper route for your next trip →</Link></strong>
      </div>
    </div>
  )
}

function changeSentence(comparison: NonNullable<SpendingHistory['comparison']>): string {
  const change = comparison.spentChangeCents
  const direction = change === 0 ? 'unchanged' : change > 0 ? 'up' : 'down'
  const amount = change === 0 ? '' : ` ${formatCents(Math.abs(change))}`
  return `Spending is ${direction}${amount}; the previous period had ${comparison.tripCount} trip${comparison.tripCount === 1 ? '' : 's'}.`
}

/**
 * The one thing worth knowing this week.
 *
 * <p>Savings leads because it is the only figure on the page that answers "was
 * FareFlow worth using". When it cannot be computed the card says so and leads
 * with spend instead, rather than printing a confident $0.00.
 */
function Headline({ data }: { data: Insights }) {
  const saved = data.savedVersusFastestCents
  const personal = data.personalization

  return (
    <section className="headline">
      <div className="lede">
        <span className="lede-eyebrow">
          {saved !== null && saved > 0 ? 'Saved this week' : 'Spent this week'}
        </span>
        <div className="lede-figure">
          <span className="lede-value">
            {saved !== null && saved > 0 ? formatCents(saved) : formatCents(data.spentCents)}
          </span>
          {personal && <CommuteChip personal={personal} />}
        </div>
        <span className="lede-sub">
          {saved !== null && saved > 0
            ? `By not always taking the fastest route, across ${data.tripCount} trip${data.tripCount === 1 ? '' : 's'}.`
            : saved === null
              ? `Across ${data.tripCount} trip${data.tripCount === 1 ? '' : 's'}. Savings need at least one comparable alternative route.`
              : `Across ${data.tripCount} trip${data.tripCount === 1 ? '' : 's'}. You took the fastest route each time.`}
        </span>
      </div>

      {personal && personal.notes.length > 0 && (
        <ul className="headline-notes">
          {personal.notes.slice(0, 3).map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
    </section>
  )
}

function CommuteChip({ personal }: { personal: InsightsPersonalization }) {
  if (!personal.typicalOriginName || !personal.typicalDestinationName) return null
  return (
    <Link
      className="headline-commute"
      to={`/plan?from=${encodeURIComponent(personal.typicalOriginName)}`
        + `&to=${encodeURIComponent(personal.typicalDestinationName)}`}
    >
      {personal.typicalOriginName} → {personal.typicalDestinationName}
    </Link>
  )
}

/**
 * Cheapest and fastest operator used.
 *
 * <p>Both are null until the rider has travelled on at least two operators, since
 * "cheapest of one" is not a comparison. The card says that instead of naming the
 * only operator available and implying a choice was made.
 */
function OperatorTable({ data }: { data: Insights }) {
  const rows = data.spendingByProvider
  const max = Math.max(...rows.map((row) => row.totalFareCents), 1)

  return (
    <>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Operator</th>
              <th className="col-num">Trips</th>
              <th className="col-num">Avg fare</th>
              <th className="col-num">Avg time</th>
              <th style={{ width: '22%' }}>Share</th>
              <th className="col-total">Total</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={row.provider} data-testid={`operator-${row.provider}`}>
                <td className="col-name">
                  <span className="table-swatch" style={{ background: seriesColor(index) }}
                        aria-hidden="true" />
                  {row.providerName}
                </td>
                <td className="col-num">{row.tripCount}</td>
                <td className="col-num">{formatCents(row.averageFareCents)}</td>
                <td className="col-num">{formatMinutes(row.averageDurationMinutes)}</td>
                <td>
                  <span className="cell-bar">
                    <span style={{
                      width: `${(row.totalFareCents / max) * 100}%`,
                      background: seriesColor(index),
                    }} />
                  </span>
                </td>
                <td className="col-total">
                  {formatCents(row.totalFareCents)}
                  <span className="cell-share">
                    {data.spentCents > 0
                      ? ` ${Math.round((row.totalFareCents / data.spentCents) * 100)}%`
                      : ''}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ProviderVerdicts data={data} />
    </>
  )
}

function ProviderVerdicts({ data }: { data: Insights }) {
  const comparable = data.cheapestProviderName !== null || data.fastestProviderName !== null

  if (!comparable) {
    return (
      <p className="band-note verdict-empty">
        FareFlow compares operators once you have travelled on at least two of them.
        Ranking a single operator against itself would not tell you anything.
      </p>
    )
  }

  return (
    <p className="verdicts">
      <span>Cheapest you used <strong>{data.cheapestProviderName}</strong></span>
      <span className="verdict-sep" aria-hidden="true">·</span>
      <span>Fastest you used <strong>{data.fastestProviderName}</strong></span>
    </p>
  )
}

/**
 * Whether a pass beats paying per ride.
 *
 * <p>Rendered straight from the pass service's verdict, including when the verdict
 * is "keep paying per ride" — a recommender that only ever says "buy the pass" is
 * a sales tool, not an advisor.
 */
function PassModule({ recommendation }: { recommendation: PassRecommendation }) {
  if (!recommendation.hasEnoughHistory) {
    return (
      <section className="band">
        <div className="band-head">
          <h2 className="band-title">Pass recommendation</h2>
        </div>
        <p className="band-note" style={{ maxWidth: '72ch' }}>
          FareFlow needs at least a full week of trips before comparing passes against
          paying per ride. Averaging less than that would be noise, not a pattern.
        </p>
      </section>
    )
  }

  const worthwhile = recommendation.recommendedPassCode !== null

  return (
    <section className="band">
      <div className="band-head">
        <h2 className="band-title">Pass recommendation</h2>
        <span className="band-note">
          Based on {recommendation.weeksOfHistory} week
          {recommendation.weeksOfHistory === 1 ? '' : 's'} of travel
        </span>
      </div>
      <div className="pass-card">
        <div className="pass-verdict">
          <span className={`pass-icon${worthwhile ? ' pass-icon-good' : ''}`} aria-hidden="true">
            <WalletIcon size={18} />
          </span>
          <div>
            <p className="pass-text">{recommendation.verdict}</p>
            <p className="stat-caption">
              Observed {formatCents(recommendation.observedWeeklySpendCents)} a week ·
              projected {formatCents(recommendation.projectedMonthlySpendCents)} a month ·
              confidence {recommendation.confidence.toLowerCase()}
            </p>
          </div>
        </div>

        {recommendation.options.length > 0 && (
          <ul className="pass-options">
            {recommendation.options.map((option) => (
              <li key={option.code} className={option.worthwhile ? 'worthwhile' : undefined}>
                <span className="pass-option-name">{option.name}</span>
                <span className="pass-option-price numeric">{formatCents(option.priceCents)}</span>
                <span className={`pass-option-delta numeric${option.worthwhile ? ' good' : ''}`}>
                  {option.monthlySavingsCents > 0
                    ? `saves ${formatCents(option.monthlySavingsCents)}/mo`
                    : `costs ${formatCents(-option.monthlySavingsCents)}/mo more`}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function BudgetEditor({ current, onSaved }: { current: number | null; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [dollars, setDollars] = useState(current === null ? '' : (current / 100).toFixed(2))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!editing) {
    return (
      <button className={`btn${current === null ? ' btn-primary' : ''}`}
              onClick={() => setEditing(true)}>
        {current === null ? 'Set a weekly budget' : 'Edit budget'}
      </button>
    )
  }

  const save = async () => {
    const parsed = Number(dollars)
    if (!Number.isFinite(parsed) || parsed < 0) {
      setError('Enter a non-negative amount')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await usersApi.updateBudget(Math.round(parsed * 100))
      setEditing(false)
      onSaved()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not save')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="row" style={{ alignItems: 'flex-start' }}>
      <div className="field" style={{ width: 130 }}>
        <input className="input numeric" value={dollars} inputMode="decimal"
               aria-label="Weekly budget in dollars"
               onChange={(event) => setDollars(event.target.value)} />
        {error && <span className="field-error">{error}</span>}
      </div>
      <button className="btn btn-primary" onClick={save} disabled={saving}>
        {saving ? 'Saving…' : 'Save'}
      </button>
      <button className="btn btn-ghost" onClick={() => setEditing(false)} disabled={saving}>Cancel</button>
    </div>
  )
}

function InsightsSkeleton() {
  return (
    <div className="page">
      <div className="page-header">
        <Skeleton width={80} height={12} />
        <div style={{ marginTop: 12 }}><Skeleton width={300} height={30} /></div>
      </div>
      <Skeleton width="38%" height={56} />
      <div style={{ marginTop: 'var(--space-7)' }}><Skeleton height={92} /></div>
    </div>
  )
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
