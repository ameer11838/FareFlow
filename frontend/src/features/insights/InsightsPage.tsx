import { useState } from 'react'
import { Link } from 'react-router-dom'
import { insightsApi, usersApi } from '../../api'
import type { Insights, InsightsPersonalization, ProviderBreakdown } from '../../api/types'
import { RouteIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { StatTile } from '../../components/StatTile'
import { EmptyState, ErrorState, LoadingState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { formatCents, formatMinutes, formatOptionalCents, formatPercent } from '../../lib/format'

/**
 * Transportation financial intelligence.
 *
 * Every figure is derived from real trips and ledger entries. Where the backend
 * returns null — no trips, no comparable baseline, only one provider used — the
 * tile renders a dash and says why. Nothing here is invented to fill a layout.
 */
export function InsightsPage() {
  const { user, refresh } = useAuth()
  const { data, loading, error, refetch } = useAsync<Insights>(() => insightsApi.get(), [])

  if (loading) return <div className="page"><div className="card"><LoadingState /></div></div>
  if (error) return <div className="page"><div className="card"><ErrorState error={error} onRetry={refetch} /></div></div>
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

      {data.personalization && <PersonalNotes personal={data.personalization} />}

      <section className="section">
        <div className="stat-grid">
          <StatTile label="Spent" value={formatCents(data.spentCents)}
                    caption="Derived from ledger entries" accent />
          <StatTile label="Weekly budget"
                    value={formatOptionalCents(data.weeklyBudgetCents)}
                    tone={data.weeklyBudgetCents === null ? 'muted' : 'default'}
                    caption={data.weeklyBudgetCents === null ? 'Set one to track against it' : undefined} />
          <StatTile label="Remaining"
                    value={formatOptionalCents(data.remainingCents)}
                    tone={data.remainingCents === null ? 'muted' : 'default'}
                    caption={data.remainingCents !== null && data.remainingCents < 0
                      ? 'Over budget'
                      : undefined} />
          <Metric label="Saved vs. fastest" cents={data.savedVersusFastestCents}
                  emptyCaption="Not enough route options to compare"
                  caption="By not always taking the fastest" positive />
          <StatTile label="Trips" value={String(data.tripCount)} caption="Completed this week" />
        </div>
      </section>

      {noTrips ? (
        <section className="section">
          <div className="card">
            <EmptyState
              icon={<RouteIcon size={20} />}
              title="No trips this week yet"
              description="Insights are computed from real trips. Take one and this page fills in — nothing here is simulated."
              action={<Link className="btn btn-primary" to="/plan">Plan a trip</Link>}
            />
          </div>
        </section>
      ) : (
        <>
          <section className="section">
            <div className="section-head">
              <div>
                <h2 className="section-title">Spending by provider</h2>
                <p className="section-sub">Completed trips this week</p>
              </div>
            </div>
            <ProviderChart rows={data.spendingByProvider} total={data.spentCents} />
          </section>

          <section className="section">
            <div className="section-head">
              <div>
                <h2 className="section-title">Travel patterns</h2>
                <p className="section-sub">Averages across this week's completed trips</p>
              </div>
            </div>

            <div className="stat-grid">
              <StatTile label="Average fare"
                        value={data.averageFareCents === null ? '—' : formatCents(data.averageFareCents)}
                        tone={data.averageFareCents === null ? 'muted' : 'default'} />
              <StatTile label="Average duration"
                        value={data.averageDurationMinutes === null ? '—' : formatMinutes(data.averageDurationMinutes)}
                        tone={data.averageDurationMinutes === null ? 'muted' : 'default'} />
              <StatTile label="Budget used"
                        value={data.budgetUtilization === null ? '—' : formatPercent(data.budgetUtilization)}
                        tone={data.budgetUtilization === null ? 'muted' : 'default'}
                        caption={data.budgetUtilization === null ? 'No budget set' : undefined} />
              <StatTile label="Time traded for savings"
                        value={data.minutesTradedForSavings === null
                          ? '—'
                          : formatMinutes(data.minutesTradedForSavings)}
                        tone={data.minutesTradedForSavings === null ? 'muted' : 'default'}
                        caption={data.minutesTradedForSavings === null
                          ? 'No comparable trips yet'
                          : 'Extra travel time versus the fastest routes'} />
            </div>
          </section>

          <section className="section">
            <div className="grid-2">
              <div className="card card-body">
                <span className="stat-label">Cheapest provider used</span>
                <div className="insight-value">
                  {data.cheapestProviderName ?? <span className="muted">—</span>}
                </div>
                <p className="stat-caption">
                  {data.cheapestProviderName
                    ? 'Lowest fare among the providers you travelled with'
                    : 'Needs trips on at least two providers to compare'}
                </p>
              </div>

              <div className="card card-body">
                <span className="stat-label">Fastest provider used</span>
                <div className="insight-value">
                  {data.fastestProviderName ?? <span className="muted">—</span>}
                </div>
                <p className="stat-caption">
                  {data.fastestProviderName
                    ? 'Shortest trip among the providers you travelled with'
                    : 'Needs trips on at least two providers to compare'}
                </p>
              </div>
            </div>
          </section>

          <section className="section">
            <div className="card card-body">
              <span className="stat-label">Projected monthly transit spend</span>
              <div className="insight-value numeric">
                {data.projectedMonthlyCents === null
                  ? <span className="muted">—</span>
                  : formatCents(data.projectedMonthlyCents)}
              </div>
              <p className="stat-caption">
                {data.projectedMonthlyCents === null
                  ? 'Needs a week with spending before a projection means anything.'
                  : 'Straight-line projection from this week alone. One week is not a trend — this becomes meaningful once several weeks of history exist.'}
              </p>
            </div>
          </section>
        </>
      )}
    </div>
  )
}

/**
 * The sentences the backend built from this rider's own profile and ledger.
 *
 * Every one arrives fully composed, including the assumption it rests on. The
 * client does not calculate, round, or rephrase a financial claim — it renders
 * what the deterministic engine said, or nothing.
 */
function PersonalNotes({ personal }: { personal: InsightsPersonalization }) {
  const hasCommute = personal.typicalOriginName && personal.typicalDestinationName

  if (personal.notes.length === 0 && !hasCommute) return null

  return (
    <section className="section">
      <div className="card card-body personal-card">
        <div className="personal-head">
          <span className="stat-label">For your travel</span>
          {hasCommute && (
            <Link
              className="personal-commute"
              to={`/plan?from=${encodeURIComponent(personal.typicalOriginName!)}`
                + `&to=${encodeURIComponent(personal.typicalDestinationName!)}`}
            >
              {personal.typicalOriginName} → {personal.typicalDestinationName}
            </Link>
          )}
        </div>

        <ul className="personal-notes">
          {personal.notes.map((note) => (
            <li key={note}>
              <span className="personal-dot" aria-hidden="true" />
              {note}
            </li>
          ))}
        </ul>

        {(personal.projectedWeeklySpendCents !== null || personal.budgetBufferCents !== null) && (
          <div className="personal-figures">
            {personal.projectedWeeklySpendCents !== null && (
              <div className="personal-figure">
                <span className="stat-label">Projected this week</span>
                <span className="personal-figure-value numeric">
                  {formatCents(personal.projectedWeeklySpendCents)}
                </span>
              </div>
            )}
            {personal.budgetBufferCents !== null && (
              <div className="personal-figure">
                <span className="stat-label">Budget buffer</span>
                <span className={`personal-figure-value numeric${personal.budgetBufferCents < 0 ? ' negative' : ''}`}>
                  {formatCents(personal.budgetBufferCents)}
                </span>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  )
}

function ProviderChart({ rows, total }: { rows: ProviderBreakdown[]; total: number }) {
  if (rows.length === 0) {
    return <div className="card"><EmptyState title="No completed trips this week" /></div>
  }

  const max = Math.max(...rows.map((row) => row.totalFareCents), 1)

  return (
    <div className="card">
      <div className="provider-chart">
        {rows.map((row) => (
          <div key={row.provider} className="provider-row" data-testid={`provider-${row.provider}`}>
            <div className="provider-head">
              <span className="provider-name">{row.providerName}</span>
              <span className="provider-total numeric">{formatCents(row.totalFareCents)}</span>
            </div>
            <div className="provider-bar">
              <div className="provider-bar-fill" style={{ width: `${(row.totalFareCents / max) * 100}%` }} />
            </div>
            <div className="provider-meta">
              <span>{row.tripCount} trip{row.tripCount === 1 ? '' : 's'}</span>
              <span className="option-sep" />
              <span>{formatCents(row.averageFareCents)} avg</span>
              <span className="option-sep" />
              <span>{formatMinutes(row.averageDurationMinutes)} avg</span>
              {total > 0 && (
                <>
                  <span className="option-sep" />
                  <span>{formatPercent(row.totalFareCents / total)} of spend</span>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function Metric({ label, cents, caption, emptyCaption, positive }: {
  label: string
  cents: number | null
  caption?: string
  emptyCaption: string
  positive?: boolean
}) {
  if (cents === null) {
    return <StatTile label={label} value="—" caption={emptyCaption} tone="muted" />
  }
  return (
    <StatTile
      label={label}
      value={formatCents(cents)}
      tone={positive && cents > 0 ? 'positive' : 'default'}
      caption={cents < 0 ? 'More than always taking the fastest' : caption}
    />
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

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
