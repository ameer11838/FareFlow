import { Link } from 'react-router-dom'
import { insightsApi, walletApi } from '../../api'
import type { Insights, LedgerEntry, PaymentMethod, Wallet } from '../../api/types'
import { BarChart } from '../../components/charts'
import { CheckIcon, LedgerIcon, WalletIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Metric, Meter, Section, Skeleton } from '../../components/Surface'
import { EmptyState, ErrorState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import {
  formatCents, formatOptionalCents, formatPercent, formatSignedCents, formatTime, ledgerTypeText,
} from '../../lib/format'

/**
 * The wallet is a read-only projection of the ledger.
 *
 * <p>There is no wallet balance stored anywhere — "remaining" is the weekly budget
 * minus the sum of the entries shown below it. A second stored balance could drift
 * from the entries that justify it.
 *
 * <p>The page is built around one figure. Everything else on it exists to explain
 * that figure: what has been spent, what is projected, and what each of those is
 * derived from.
 */
export function WalletPage() {
  const wallet = useAsync<Wallet>(() => walletApi.get(), [])
  // Projections live on the insights endpoint because they are derived from the
  // rider's stated commute rate, not from the ledger alone.
  const insights = useAsync<Insights>(() => insightsApi.get(), [])

  if (wallet.loading) return <WalletSkeleton />
  if (wallet.error) {
    return (
      <div className="page">
        <Card className="card-body"><ErrorState error={wallet.error} onRetry={wallet.refetch} /></Card>
      </div>
    )
  }
  if (!wallet.data) return null

  const data = wallet.data
  const projected = insights.data?.personalization?.projectedWeeklySpendCents ?? null
  /*
   * The projection is floored at actual spend, so once a rider passes their usual
   * pace the two numbers converge. Printing the same figure twice under different
   * labels reads as a bug, so the page says what the equality actually means
   * instead of repeating itself.
   */
  const pastUsualPace = projected !== null && projected === data.spentThisWeekCents
  const savedCents = insights.data?.savedVersusFastestCents ?? null
  const hasBudget = data.weeklyBudgetCents !== null

  return (
    <div className="page">
      <PageHeader
        eyebrow="Wallet"
        title="FareFlow Wallet"
        subtitle="Fares are charged against your weekly transportation budget. Every movement is recorded in the ledger."
      />

      <Section>
        {hasBudget
          ? <BudgetCard wallet={data} projectedCents={projected} pastUsualPace={pastUsualPace} />
          : <NoBudgetCard />}
      </Section>

      <Section title="This week" caption="Derived from ledger entries, not a stored total.">
        <div className="module-grid">
          <Card className="card-body">
            <Metric
              label="Spent"
              value={formatCents(data.spentThisWeekCents)}
              caption={`${data.recentActivity.length > 0 ? 'Across ' : ''}${
                data.recentActivity.filter((entry) => entry.type === 'TRIP_CHARGE').length
              } charge${data.recentActivity.filter((e) => e.type === 'TRIP_CHARGE').length === 1 ? '' : 's'}`}
            />
          </Card>

          <Card className="card-body">
            <Metric
              label="Remaining"
              value={formatOptionalCents(data.availableBalanceCents)}
              tone={data.availableBalanceCents === null
                ? 'muted'
                : data.availableBalanceCents < 0 ? 'negative' : 'positive'}
              caption={data.availableBalanceCents === null
                ? 'No budget set'
                : data.availableBalanceCents < 0 ? 'Over budget' : 'Left to spend'}
            />
          </Card>

          <Card className="card-body">
            <Metric
              label="Projected spend"
              value={formatOptionalCents(projected, '—')}
              tone={projected === null ? 'muted' : 'default'}
              caption={projected === null
                ? 'Needs a commute frequency and a completed trip'
                : pastUsualPace
                  ? 'You have already passed your usual weekly pace'
                  : 'At your usual commute rate'}
            />
          </Card>

          <Card className="card-body">
            <Metric
              label="Saved"
              value={formatOptionalCents(savedCents, '—')}
              tone={savedCents !== null && savedCents > 0 ? 'positive' : 'muted'}
              caption={savedCents === null
                ? 'Needs a comparable alternative route'
                : 'By not always taking the fastest route'}
            />
          </Card>
        </div>
      </Section>

      {insights.data && insights.data.spendingByProvider.length > 0 && (
        <Section title="Where it went" caption="Completed trips this week, by operator.">
          <Card className="card-body">
            <BarChart
              data={insights.data.spendingByProvider.map((row) => ({
                id: row.provider,
                label: row.providerName,
                value: row.totalFareCents,
                display: formatCents(row.totalFareCents),
                meta: `${row.tripCount} trip${row.tripCount === 1 ? '' : 's'} · ${formatCents(row.averageFareCents)} avg`,
              }))}
              total={insights.data.spentCents}
            />
          </Card>
        </Section>
      )}

      <Section title="Payment methods">
        <Card>
          <div className="method-list">
            {data.paymentMethods.map((method) => (
              <PaymentMethodRow key={method.id} method={method} />
            ))}
          </div>
          <div className="card-footer">
            <span className="stat-caption">
              Card and stablecoin rails are declared so the checkout flow has a shape to
              grow into. Neither moves real money today.
            </span>
          </div>
        </Card>
      </Section>

      <Section
        title="Recent activity"
        caption="Straight from the ledger."
        action={<Link className="btn btn-sm" to="/ledger">View ledger</Link>}
      >
        <Card>
          {data.recentActivity.length === 0 ? (
            <EmptyState
              icon={<LedgerIcon size={22} />}
              title="No activity yet"
              description="Taking a trip records a charge here, and cancelling one records the refund beside it."
              action={<Link className="btn btn-primary" to="/plan">Plan a trip</Link>}
            />
          ) : (
            <ul className="txn-list">
              {data.recentActivity.map((entry) => (
                <TransactionRow key={entry.id} entry={entry} />
              ))}
            </ul>
          )}
        </Card>
      </Section>
    </div>
  )
}

/**
 * The one figure the page exists to show.
 *
 * <p>Remaining leads because it is the number that changes a decision: a rider
 * checking their wallet before a trip is asking "can I afford this", not "what
 * have I spent". Spent, the meter, and the projection are all support for it.
 */
function BudgetCard({ wallet, projectedCents, pastUsualPace }: {
  wallet: Wallet
  projectedCents: number | null
  pastUsualPace: boolean
}) {
  const budget = wallet.weeklyBudgetCents ?? 0
  const remaining = wallet.availableBalanceCents ?? 0
  const over = remaining < 0
  const projectedRemaining = projectedCents === null ? null : budget - projectedCents

  return (
    <Card tone="navy" className="budget-hero">
      <div className="budget-hero-main">
        <span className="budget-hero-label">Weekly transportation</span>
        <div className="budget-hero-figure">
          <span className="budget-hero-value numeric">{formatCents(remaining)}</span>
          <span className="budget-hero-unit">{over ? 'over budget' : 'remaining'}</span>
        </div>
        <span className="budget-hero-sub numeric">
          {formatCents(wallet.spentThisWeekCents)} spent of {formatCents(budget)}
        </span>

        <div className="budget-hero-meter">
          <Meter
            value={wallet.spentThisWeekCents}
            max={budget}
            over={over}
            label={`${formatCents(wallet.spentThisWeekCents)} spent of ${formatCents(budget)}`}
          />
        </div>

        <BudgetVerdict
          spent={wallet.spentThisWeekCents}
          budget={budget}
          projected={projectedCents}
          utilization={wallet.budgetUtilization}
        />
      </div>

      {/* Projections are separated by a rule rather than a second card: they are
          the same story told forward, not a different subject. */}
      <div className="budget-hero-side">
        {pastUsualPace ? (
          /* Repeating "remaining" under a "projected" label would look broken.
             What is true and useful here is that the week is already ahead of
             the rider's usual pattern. */
          <Metric
            label="This week so far"
            value={formatCents(wallet.spentThisWeekCents)}
            caption="Already past your usual weekly pace, so there is nothing left to project."
          />
        ) : (
          <>
            <Metric
              label="Projected spend"
              value={formatOptionalCents(projectedCents, '—')}
              tone={projectedCents === null ? 'muted' : 'default'}
              caption={projectedCents === null ? 'Needs a trip to project from' : undefined}
            />
            <Metric
              label="Projected remaining"
              value={formatOptionalCents(projectedRemaining, '—')}
              tone={projectedRemaining !== null && projectedRemaining < 0 ? 'negative' : 'default'}
              caption={projectedRemaining !== null && projectedRemaining < 0
                ? 'This pace exceeds your budget'
                : undefined}
            />
          </>
        )}
      </div>
    </Card>
  )
}

/**
 * A one-word read on the week.
 *
 * <p>Deliberately conservative: it says "on track" only when the *projection*
 * fits the budget, not merely when today's spend does. A verdict that flips to
 * "over" on the last day of the week would have been useless all week.
 */
function BudgetVerdict({ spent, budget, projected, utilization }: {
  spent: number
  budget: number
  projected: number | null
  utilization: number | null
}) {
  if (budget <= 0) return null

  const reference = projected ?? spent
  const over = reference > budget
  const tight = !over && reference > budget * 0.9

  const tone = over ? 'negative' : tight ? 'neutral' : 'positive'
  const text = over ? 'Over budget' : tight ? 'Running close' : 'On track'

  return (
    <div className="budget-verdict">
      <span className={`verdict verdict-${tone}`}>
        {!over && !tight && <CheckIcon size={13} />}
        {text}
      </span>
      {utilization !== null && (
        <span className="budget-verdict-note numeric">
          {formatPercent(utilization)} of budget used
          {projected !== null && ` · projected ${formatPercent(projected / budget)}`}
        </span>
      )}
    </div>
  )
}

function NoBudgetCard() {
  return (
    <Card tone="navy" className="budget-hero budget-hero-empty">
      <div className="budget-hero-main">
        <span className="budget-hero-label">Weekly transportation</span>
        <div className="budget-hero-figure">
          <span className="budget-hero-value budget-hero-value-unset">Set a weekly budget</span>
        </div>
        <span className="budget-hero-sub">
          FareFlow tracks spending against a weekly transportation budget, and leans
          toward cheaper routes as you approach it. Until you set one there is no
          balance to show.
        </span>
        <Link className="btn btn-primary budget-hero-cta" to="/settings">Set a budget</Link>
      </div>
    </Card>
  )
}

function PaymentMethodRow({ method }: { method: PaymentMethod }) {
  const available = method.status === 'AVAILABLE'
  return (
    <div className={`method${available ? ' method-available' : ''}`} data-testid={`payment-${method.id}`}>
      <span className="method-icon"><WalletIcon size={18} /></span>
      <div className="method-text">
        <span className="method-name">{method.name}</span>
        <span className="method-desc">{method.description}</span>
      </div>
      <span className={`method-status${available ? ' active' : ''}`}>
        {available ? 'Active' : 'Coming later'}
      </span>
    </div>
  )
}

/**
 * A transaction as a financial row rather than a database record.
 *
 * <p>Sign is carried by three things at once — the glyph, the colour, and the
 * leading + or − on the amount — so the direction of money survives greyscale,
 * colourblindness, and a quick glance.
 */
function TransactionRow({ entry }: { entry: LedgerEntry }) {
  const credit = entry.amountCents > 0
  return (
    <li className="txn" data-testid={`wallet-entry-${entry.id}`}>
      <span className={`txn-icon${credit ? ' txn-icon-in' : ''}`} aria-hidden="true">
        {credit ? '↓' : '↑'}
      </span>
      <div className="txn-text">
        <span className="txn-title">{entry.description}</span>
        <span className="txn-meta">
          {ledgerTypeText(entry.type)} · {formatTime(entry.occurredAt)}
        </span>
      </div>
      <span className={`txn-amount numeric ${credit ? 'amount-in' : 'amount-out'}`}>
        {formatSignedCents(entry.amountCents)}
      </span>
    </li>
  )
}

function WalletSkeleton() {
  return (
    <div className="page">
      <div className="page-header">
        <Skeleton width={90} height={12} />
        <div style={{ marginTop: 12 }}><Skeleton width={260} height={30} /></div>
      </div>
      <Card tone="navy" className="budget-hero-skeleton"><Skeleton width="60%" height={120} /></Card>
      <div className="module-grid" style={{ marginTop: 'var(--space-7)' }}>
        {[0, 1, 2, 3].map((index) => (
          <Card key={index} className="card-body"><Skeleton height={62} /></Card>
        ))}
      </div>
    </div>
  )
}
