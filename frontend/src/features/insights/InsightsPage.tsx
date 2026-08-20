import { useState } from 'react'
import { Link } from 'react-router-dom'
import { insightsApi, passesApi, usersApi } from '../../api'
import type { Insights, InsightsPersonalization, PassRecommendation } from '../../api/types'
import { BarChart, ComparisonBars } from '../../components/charts'
import { RouteIcon, SparkleIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Metric, Meter, Section, Skeleton } from '../../components/Surface'
import { EmptyState, ErrorState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { formatCents, formatMinutes, formatOptionalCents, formatPercent } from '../../lib/format'

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
        <Section>
          <Card>
            <EmptyState
              icon={<RouteIcon size={22} />}
              title="Your insights are getting ready"
              description="Complete a few trips and FareFlow will start identifying spending patterns, the operators you rely on, and opportunities to save. Nothing here is simulated — it fills in from real trips."
              action={<Link className="btn btn-primary btn-lg" to="/plan">Plan a trip</Link>}
            />
          </Card>
        </Section>
      ) : (
        <>
          <Section>
            <HeadlineCard data={data} />
          </Section>

          <Section title="Budget adherence" caption="Where this week sits against the budget you set.">
            <BudgetAdherence data={data} />
          </Section>

          <Section title="Where your money goes" caption="Completed trips this week, by operator.">
            <div className="split-grid">
              <Card className="card-body">
                <BarChart
                  data={data.spendingByProvider.map((row) => ({
                    id: row.provider,
                    label: row.providerName,
                    value: row.totalFareCents,
                    display: formatCents(row.totalFareCents),
                    meta: `${row.tripCount} trip${row.tripCount === 1 ? '' : 's'} · ${formatCents(row.averageFareCents)} avg · ${formatMinutes(row.averageDurationMinutes)} avg`,
                  }))}
                  total={data.spentCents}
                />
              </Card>

              <Card className="card-body">
                <ProviderVerdicts data={data} />
              </Card>
            </div>
          </Section>

          <Section title="How you travel" caption="Averages across this week's completed trips.">
            <div className="module-grid">
              <Card className="card-body">
                <Metric label="Average fare"
                        value={formatOptionalCents(data.averageFareCents, '—')}
                        tone={data.averageFareCents === null ? 'muted' : 'default'}
                        caption="Per completed trip" />
              </Card>
              <Card className="card-body">
                <Metric label="Average trip"
                        value={data.averageDurationMinutes === null ? '—' : formatMinutes(data.averageDurationMinutes)}
                        tone={data.averageDurationMinutes === null ? 'muted' : 'default'}
                        caption="Door to door" />
              </Card>
              <Card className="card-body">
                <Metric label="Time traded for savings"
                        value={data.minutesTradedForSavings === null ? '—' : formatMinutes(data.minutesTradedForSavings)}
                        tone={data.minutesTradedForSavings === null ? 'muted' : 'default'}
                        caption={data.minutesTradedForSavings === null
                          ? 'No comparable trips yet'
                          : 'Extra travel time versus the fastest routes'} />
              </Card>
              <Card className="card-body">
                <Metric label="Projected monthly"
                        value={formatOptionalCents(data.projectedMonthlyCents, '—')}
                        tone={data.projectedMonthlyCents === null ? 'muted' : 'default'}
                        caption={data.projectedMonthlyCents === null
                          ? 'Needs a week with spending'
                          : 'Straight-line from this week alone'} />
              </Card>
            </div>
          </Section>

          {passes.data && <PassModule recommendation={passes.data} />}
        </>
      )}
    </div>
  )
}

/**
 * The one thing worth knowing this week.
 *
 * <p>Savings leads because it is the only figure on the page that answers "was
 * FareFlow worth using". When it cannot be computed the card says so and leads
 * with spend instead, rather than printing a confident $0.00.
 */
function HeadlineCard({ data }: { data: Insights }) {
  const saved = data.savedVersusFastestCents
  const personal = data.personalization

  return (
    <Card tone="navy" className="headline">
      <div className="headline-main">
        <span className="headline-label">
          {saved !== null && saved > 0 ? 'Saved this week' : 'Spent this week'}
        </span>
        <span className="headline-value numeric">
          {saved !== null && saved > 0 ? formatCents(saved) : formatCents(data.spentCents)}
        </span>
        <span className="headline-sub">
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
            <li key={note}><span className="headline-dot" aria-hidden="true" />{note}</li>
          ))}
        </ul>
      )}

      {personal && <CommuteChip personal={personal} />}
    </Card>
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

function BudgetAdherence({ data }: { data: Insights }) {
  if (data.weeklyBudgetCents === null) {
    return (
      <Card className="card-body adherence-empty">
        <div>
          <h3 className="section-title">No weekly budget set</h3>
          <p className="section-sub">
            Set one and FareFlow will track this week against it, and lean toward cheaper
            routes as you approach it.
          </p>
        </div>
        <Link className="btn btn-primary" to="/settings">Set a budget</Link>
      </Card>
    )
  }

  const budget = data.weeklyBudgetCents
  const over = (data.remainingCents ?? 0) < 0
  const projected = data.personalization?.projectedWeeklySpendCents ?? null

  return (
    <Card className="card-body adherence">
      <div className="adherence-figures">
        <Metric label="Spent" value={formatCents(data.spentCents)} emphasis="hero" />
        <Metric label="Budget" value={formatCents(budget)} />
        <Metric label="Remaining"
                value={formatOptionalCents(data.remainingCents)}
                tone={over ? 'negative' : 'positive'} />
      </div>

      <div className="adherence-meter">
        <Meter value={data.spentCents} max={budget} over={over}
               label={`${formatCents(data.spentCents)} of ${formatCents(budget)} spent`} />
        <div className="adherence-scale">
          <span>{data.budgetUtilization === null ? '0%' : formatPercent(data.budgetUtilization)} used</span>
          <span>{formatCents(budget)}</span>
        </div>
      </div>

      {projected !== null && (
        <ComparisonBars rows={[
          { id: 'spent', label: 'Spent so far', value: data.spentCents,
            display: formatCents(data.spentCents), tone: 'brand' },
          { id: 'projected', label: 'Projected by week end', value: projected,
            display: formatCents(projected), tone: projected > budget ? 'muted' : 'positive' },
          { id: 'budget', label: 'Your budget', value: budget,
            display: formatCents(budget), tone: 'muted' },
        ]} />
      )}
    </Card>
  )
}

/**
 * Cheapest and fastest operator used.
 *
 * <p>Both are null until the rider has travelled on at least two operators, since
 * "cheapest of one" is not a comparison. The card says that instead of naming the
 * only operator available and implying a choice was made.
 */
function ProviderVerdicts({ data }: { data: Insights }) {
  const comparable = data.cheapestProviderName !== null || data.fastestProviderName !== null

  if (!comparable) {
    return (
      <div className="verdict-empty">
        <span className="stat-label">Operator comparison</span>
        <p className="section-sub">
          FareFlow compares operators once you have travelled on at least two of them.
          Ranking a single operator against itself would not tell you anything.
        </p>
      </div>
    )
  }

  return (
    <div className="verdicts">
      <div className="verdict-item">
        <span className="stat-label">Cheapest you used</span>
        <span className="verdict-name">{data.cheapestProviderName ?? '—'}</span>
        <span className="stat-caption">Lowest fare among the operators you travelled with</span>
      </div>
      <div className="verdict-item">
        <span className="stat-label">Fastest you used</span>
        <span className="verdict-name">{data.fastestProviderName ?? '—'}</span>
        <span className="stat-caption">Shortest trip among the operators you travelled with</span>
      </div>
    </div>
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
      <Section title="Pass recommendation">
        <Card className="card-body">
          <p className="section-sub">
            FareFlow needs at least a full week of trips before comparing passes against
            paying per ride. Averaging less than that would be noise, not a pattern.
          </p>
        </Card>
      </Section>
    )
  }

  const worthwhile = recommendation.recommendedPassCode !== null

  return (
    <Section title="Pass recommendation" caption={`Based on ${recommendation.weeksOfHistory} week${recommendation.weeksOfHistory === 1 ? '' : 's'} of travel.`}>
      <Card className="card-body pass-card">
        <div className="pass-verdict">
          <span className={`pass-icon${worthwhile ? ' pass-icon-good' : ''}`} aria-hidden="true">
            <SparkleIcon size={18} />
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
      </Card>
    </Section>
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
      <Card tone="navy" className="headline-skeleton"><Skeleton width="55%" height={110} /></Card>
      <div className="module-grid" style={{ marginTop: 'var(--space-7)' }}>
        {[0, 1, 2, 3].map((index) => (
          <Card key={index} className="card-body"><Skeleton height={62} /></Card>
        ))}
      </div>
    </div>
  )
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
