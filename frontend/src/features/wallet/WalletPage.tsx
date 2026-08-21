import { Link } from 'react-router-dom'
import { insightsApi, walletApi } from '../../api'
import type { Insights, LedgerEntry, PaymentIntent, PaymentMethod, Wallet } from '../../api/types'
import { seriesColor } from '../../components/charts'
import { CheckIcon, LedgerIcon, WalletIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Metric, Meter, Skeleton } from '../../components/Surface'
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

      {/*
        The lead figure sits on the page, not in a container. A rider checking
        their wallet before a trip is asking "can I afford this", and 56px of
        tightly tracked type answers that better than a box does.
      */}
      {hasBudget
        ? <BudgetLede wallet={data} projectedCents={projected} pastUsualPace={pastUsualPace} />
        : <NoBudgetLede />}

      <section className="band">
        <div className="band-head">
          <h2 className="band-title">This week</h2>
          <span className="band-note">Derived from ledger entries, not a stored total</span>
        </div>

        {/* One row, divided by rules: the figures sit on a shared baseline and
            are directly comparable, which four separate tiles never are. */}
        <div className="figures-row">
          <Metric
            label="Spent"
            value={formatCents(data.spentThisWeekCents)}
            caption={`${chargeCount(data)} charge${chargeCount(data) === 1 ? '' : 's'}`}
          />
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
          <Metric
            label="Projected"
            value={formatOptionalCents(projected, '—')}
            tone={projected === null ? 'muted' : 'default'}
            caption={projected === null
              ? 'Needs a completed trip'
              : pastUsualPace ? 'Past your usual pace' : 'At your usual commute rate'}
          />
          <Metric
            label="Saved"
            value={formatOptionalCents(savedCents, '—')}
            tone={savedCents !== null && savedCents > 0 ? 'positive' : 'muted'}
            caption={savedCents === null ? 'No comparable route' : 'vs the fastest route'}
          />
        </div>
      </section>

      {insights.data && insights.data.spendingByProvider.length > 0 && (
        <section className="band">
          <div className="band-head">
            <h2 className="band-title">Where it went</h2>
            <span className="band-note">Completed trips this week, by operator</span>
          </div>
          <OperatorTable rows={insights.data.spendingByProvider} total={insights.data.spentCents} />
        </section>
      )}

      <section className="band">
        <div className="band-head">
          <h2 className="band-title">Payment methods</h2>
        </div>
        <Card>
          <div className="method-list">
            {data.paymentMethods.map((method) => (
              <PaymentMethodRow key={method.id} method={method} />
            ))}
          </div>
        </Card>
        <p className="band-note" style={{ marginTop: 'var(--space-3)' }}>
          The card rail is a simulation for exercising authorization, settlement,
          failure, retry, and refund flows. It never moves real money.
        </p>
      </section>

      {data.recentPayments.length > 0 && (
        <section className="band">
          <div className="band-head">
            <h2 className="band-title">Payment activity</h2>
            <span className="band-note">Intent status and authoritative settled fare</span>
          </div>
          <Card>
            <ul className="txn-list">
              {data.recentPayments.map((payment) => (
                <PaymentRow key={payment.id} payment={payment} />
              ))}
            </ul>
          </Card>
        </section>
      )}

      <section className="band">
        <div className="band-head">
          <h2 className="band-title">Recent activity</h2>
          <Link className="btn btn-sm" to="/ledger">View ledger</Link>
        </div>
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
      </section>
    </div>
  )
}

function chargeCount(wallet: Wallet): number {
  return wallet.recentActivity.filter((entry) => entry.type === 'TRIP_CHARGE').length
}

/**
 * Spend by operator as a table.
 *
 * <p>A table rather than a chart: with four columns of real numbers the reader
 * gets share *and* trip count *and* average fare, where a bar chart gives one
 * of them and needs a legend. The inline bar keeps the visual comparison
 * without spending a whole card on it.
 */
function OperatorTable({ rows, total }: {
  rows: Insights['spendingByProvider']
  total: number
}) {
  const max = Math.max(...rows.map((row) => row.totalFareCents), 1)
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Operator</th>
            <th className="col-num">Trips</th>
            <th className="col-num">Avg fare</th>
            <th style={{ width: '28%' }}>Share</th>
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
                  {total > 0 ? ` ${Math.round((row.totalFareCents / total) * 100)}%` : ''}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
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
function BudgetLede({ wallet, projectedCents, pastUsualPace }: {
  wallet: Wallet
  projectedCents: number | null
  pastUsualPace: boolean
}) {
  const budget = wallet.weeklyBudgetCents ?? 0
  const remaining = wallet.availableBalanceCents ?? 0
  const over = remaining < 0
  const projectedRemaining = projectedCents === null ? null : budget - projectedCents

  return (
    <section className="budget-lede">
      <div className="lede">
        <span className="lede-eyebrow">Weekly transportation</span>
        <div className="lede-figure">
          <span className="lede-value">{formatCents(remaining)}</span>
          <span className="lede-unit">{over ? 'over budget' : 'remaining'}</span>
        </div>
        <span className="lede-sub numeric">
          {formatCents(wallet.spentThisWeekCents)} spent of {formatCents(budget)}
          {projectedCents !== null && !pastUsualPace && (
            <> · {formatCents(projectedCents)} projected by week end</>
          )}
          {pastUsualPace && <> · already past your usual weekly pace</>}
        </span>
      </div>

      <div className="budget-lede-track">
        <Meter
          value={wallet.spentThisWeekCents}
          max={budget}
          over={over}
          label={`${formatCents(wallet.spentThisWeekCents)} spent of ${formatCents(budget)}`}
        />
        <BudgetVerdict
          spent={wallet.spentThisWeekCents}
          budget={budget}
          projected={projectedCents}
          utilization={wallet.budgetUtilization}
        />
      </div>

      {projectedRemaining !== null && projectedRemaining < 0 && (
        <p className="budget-lede-warn">
          At this pace the week ends {formatCents(-projectedRemaining)} over budget.
        </p>
      )}
    </section>
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

function NoBudgetLede() {
  return (
    <section className="budget-lede">
      <div className="lede">
        <span className="lede-eyebrow">Weekly transportation</span>
        <div className="lede-figure">
          <span className="lede-value lede-value-unset">Set a weekly budget</span>
        </div>
        <span className="lede-sub">
          FareFlow tracks spending against a weekly transportation budget, and leans
          toward cheaper routes as you approach it. Until you set one there is no
          balance to show.
        </span>
      </div>
      <Link className="btn btn-primary" to="/settings" style={{ justifySelf: 'start' }}>
        Set a budget
      </Link>
    </section>
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

function PaymentRow({ payment }: { payment: PaymentIntent }) {
  return (
    <li className="txn" data-testid={`wallet-payment-${payment.id}`}>
      <span className={`payment-state-dot state-${payment.status.toLowerCase()}`} aria-hidden="true" />
      <div className="txn-text">
        <span className="txn-title">{payment.origin} → {payment.destination}</span>
        <span className="txn-meta">
          {payment.paymentMethod === 'FAREFLOW_WALLET' ? 'FareFlow Wallet' : 'Simulated card'}
          {' · '}{payment.status.toLowerCase()}
          {' · '}{payment.attemptCount} attempt{payment.attemptCount === 1 ? '' : 's'}
        </span>
      </div>
      <span className="txn-amount numeric">{formatCents(payment.amountCents)}</span>
    </li>
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
      <Skeleton width="42%" height={56} />
      <div style={{ marginTop: 'var(--space-7)' }}><Skeleton height={92} /></div>
    </div>
  )
}
