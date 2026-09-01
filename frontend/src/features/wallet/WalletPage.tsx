import { Link } from 'react-router-dom'
import { insightsApi, walletApi } from '../../api'
import type { Insights, LedgerEntry, PaymentIntent, PaymentMethod, Wallet } from '../../api/types'
import { seriesColor } from '../../components/charts'
import { ModeIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { BudgetLede, NoBudgetLede } from '../../components/BudgetLede'
import { Card, Skeleton } from '../../components/Surface'
import { EmptyState, ErrorState } from '../../components/states'
import { Tile } from '../../components/Tile'
import type { TileName } from '../../components/tileNames'
import { useAsync } from '../../hooks/useAsync'
import { formatCents, formatOptionalCents, formatSignedCents, formatTime, ledgerTypeText } from '../../lib/format'

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
  const projectedRemaining = projected === null || data.weeklyBudgetCents === null
    ? null : data.weeklyBudgetCents - projected

  return (
    <div className="page">
      <PageHeader
        tile="payments-wallet/wallet"
        title="FareFlow Wallet"
        subtitle="See what is available for transit this week, where it went, and what your current pace means for the days ahead."
        actions={(
          <div className="page-actions">
            <Link className="btn" to="/settings">Add weekly funds</Link>
            <Link className="btn" to="/payments">Payment history</Link>
            <Link className="btn btn-primary" to="/plan">Plan a trip</Link>
          </div>
        )}
      />

      {/*
        The lead figure sits on the page, not in a container. A rider checking
        their wallet before a trip is asking "can I afford this", and 56px of
        tightly tracked type answers that better than a box does.
      */}
      {hasBudget ? (
        <BudgetLede
          lead="remaining"
          spentCents={data.spentThisWeekCents}
          budgetCents={data.weeklyBudgetCents}
          remainingCents={data.availableBalanceCents}
          projectedCents={projected}
          detail={projected === null ? undefined : pastUsualPace
            ? 'Already past your usual weekly pace.'
            : `${formatCents(projected)} projected by week end at your usual commute rate.`}
          aside={(
            <>
              <span className="lede-aside-label">Saved vs the fastest route</span>
              <span className={`lede-aside-value numeric${savedCents !== null && savedCents > 0 ? ' is-positive' : ''}`}>
                {formatOptionalCents(savedCents, '—')}
              </span>
              <span className="lede-aside-note">
                {savedCents === null ? 'No comparable route this week' : 'This week'}
              </span>
            </>
          )}
          footer={projectedRemaining !== null && projectedRemaining < 0 ? (
            <p className="lede-warn">
              At this pace the week ends {formatCents(-projectedRemaining)} over budget.
            </p>
          ) : undefined}
        />
      ) : (
        <NoBudgetLede action={(
          <Link className="btn btn-primary" to="/settings" style={{ justifySelf: 'start' }}>
            Set a budget
          </Link>
        )} />
      )}

      {data.openTransitSession && (
        <section className="band wallet-pending" data-testid="pending-transit-session">
          <div className="band-head">
            <h2 className="band-title">Pending trip</h2>
            <span className="band-note">Not included in spending until payment settles</span>
          </div>
          <Link className="wallet-pending-row" to="/plan">
            <span className="activity-icon"><ModeIcon mode={data.openTransitSession.currentMode} /></span>
            <span>
              <strong>{data.openTransitSession.origin} → {data.openTransitSession.destination}</strong>
              <small>{data.openTransitSession.summary} · {data.openTransitSession.progressUnitsCompleted}
                {' '}of {data.openTransitSession.progressUnitsTotal} route stops recorded</small>
            </span>
            <span className="wallet-pending-amount numeric">
              {data.openTransitSession.finalFareCents === null
                ? `${formatCents(data.openTransitSession.currentEstimatedFareCents)} est.`
                : `${formatCents(data.openTransitSession.finalFareCents)} due`}
            </span>
          </Link>
        </section>
      )}

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
            <h2 className="band-title">Payment status</h2>
            <span className="band-note">Authorization, settlement, and retry status</span>
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
          <h2 className="band-title">Latest transactions</h2>
          <Link className="btn btn-sm" to="/payments">View payment history</Link>
        </div>
        <Card>
          {data.recentActivity.length === 0 ? (
            <EmptyState
              tile="payments-wallet/wallet"
              title="No payments yet"
              description="A completed trip records its charge here. Refunds appear beside the original payment."
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
 * The plate for a funding source, by id.
 *
 * <p>Falls back to the generic card rather than to the wallet: an unknown method
 * is much more likely to be a card FareFlow has not been taught about than a
 * second FareFlow balance, and showing the balance plate for it would be a
 * small lie about where the money comes from.
 */
const METHOD_TILE: Record<string, TileName> = {
  FAREFLOW_BALANCE: 'payments-wallet/wallet',
  FAREFLOW_WALLET: 'payments-wallet/wallet',
  SIMULATED_CARD: 'payments-wallet/credit-card',
  APPLE_PAY: 'payments-wallet/apple-pay',
  GOOGLE_PAY: 'payments-wallet/google-pay',
}

function PaymentMethodRow({ method }: { method: PaymentMethod }) {
  const available = method.status === 'AVAILABLE'
  return (
    <div className={`method${available ? ' method-available' : ''}`} data-testid={`payment-${method.id}`}>
      <span className="tile-plate method-plate">
        <Tile name={METHOD_TILE[method.id] ?? 'payments-wallet/credit-card'} size={34} />
      </span>
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
