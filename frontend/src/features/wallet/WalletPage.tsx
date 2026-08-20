import { Link } from 'react-router-dom'
import { walletApi } from '../../api'
import type { PaymentMethod, Wallet } from '../../api/types'
import { Badge } from '../../components/Badge'
import { LedgerIcon, WalletIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { StatTile } from '../../components/StatTile'
import { EmptyState, ErrorState, LoadingState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { formatCents, formatOptionalCents, formatPercent, formatSignedCents, formatTime, ledgerTypeText } from '../../lib/format'

/**
 * The wallet is a read-only projection of the ledger.
 *
 * There is no wallet balance stored anywhere — "available" is the remaining weekly
 * budget, derived from the same entries shown below it. A second stored balance
 * could drift from the entries that justify it.
 */
export function WalletPage() {
  const { data, loading, error, refetch } = useAsync<Wallet>(() => walletApi.get(), [])

  if (loading) return <div className="page"><div className="card"><LoadingState /></div></div>
  if (error) return <div className="page"><div className="card"><ErrorState error={error} onRetry={refetch} /></div></div>
  if (!data) return null

  return (
    <div className="page">
      <PageHeader
        eyebrow="Wallet"
        title="FareFlow Wallet"
        subtitle="Fares are charged against your weekly transportation budget. Every movement is recorded in the ledger."
      />

      <section className="section">
        <div className="balance-card">
          {/*
            No budget means no balance to report. Rendering $0.00 here would read
            as "you are out of money", so the card asks for a budget instead.
          */}
          {data.weeklyBudgetCents === null ? (
            <>
              <span className="balance-label">Weekly budget</span>
              <span className="balance-value balance-value-unset">Set a weekly budget</span>
              <span className="balance-caption">
                FareFlow tracks spending against a weekly transportation budget. Until
                you set one there is no balance to show.
              </span>
              <Link className="btn btn-primary balance-cta" to="/settings">
                Set a budget
              </Link>
            </>
          ) : (
            <>
              <span className="balance-label">Available balance</span>
              <span className="balance-value numeric">
                {formatCents(data.availableBalanceCents ?? 0)}
              </span>
              <span className="balance-caption">
                {data.budgetUtilization === null
                  ? 'Your weekly budget is zero, so nothing is tracked against it'
                  : `${formatPercent(data.budgetUtilization)} of this week's budget used`}
              </span>

              {data.weeklyBudgetCents > 0 && (
                <div className="balance-meter">
                  <div
                    className="balance-meter-fill"
                    style={{
                      width: `${Math.min(Math.max(data.spentThisWeekCents / data.weeklyBudgetCents, 0), 1) * 100}%`,
                    }}
                  />
                </div>
              )}
            </>
          )}
        </div>
      </section>

      <section className="section">
        <div className="stat-grid">
          <StatTile label="Spent this week" value={formatCents(data.spentThisWeekCents)}
                    caption="Derived from ledger entries" />
          <StatTile label="Weekly budget"
                    value={formatOptionalCents(data.weeklyBudgetCents)}
                    tone={data.weeklyBudgetCents === null ? 'muted' : 'default'}
                    caption="Change it from Settings" />
          <StatTile label="Remaining"
                    value={formatOptionalCents(data.availableBalanceCents)}
                    tone={data.availableBalanceCents === null
                      ? 'muted'
                      : data.availableBalanceCents < 0 ? 'default' : 'positive'}
                    caption={data.availableBalanceCents === null
                      ? 'No budget set'
                      : data.availableBalanceCents < 0 ? 'Over budget' : 'Left to spend'} />
        </div>
      </section>

      <section className="section">
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">Payment methods</h2>
          </div>
          <div className="payment-list">
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
        </div>
      </section>

      <section className="section">
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">Recent activity</h2>
            <span className="stat-caption">From the ledger</span>
          </div>

          {data.recentActivity.length === 0 ? (
            <EmptyState
              icon={<LedgerIcon size={20} />}
              title="No activity yet"
              description="Taking a trip records a charge here."
            />
          ) : (
            <div className="activity">
              {data.recentActivity.map((entry) => (
                <div key={entry.id} className="activity-row" data-testid={`wallet-entry-${entry.id}`}>
                  <span
                    className="mode-icon"
                    style={entry.amountCents > 0 ? {
                      background: 'var(--color-positive-soft)',
                      borderColor: 'var(--color-positive-border)',
                      color: 'var(--color-positive)',
                    } : undefined}
                    aria-hidden="true"
                  >
                    {entry.amountCents > 0 ? '+' : '−'}
                  </span>
                  <div>
                    <div className="activity-title">{entry.description}</div>
                    <div className="activity-sub">
                      <span className="entry-type">{ledgerTypeText(entry.type)}</span>
                      <span className="option-sep" />
                      <span>{formatTime(entry.occurredAt)}</span>
                    </div>
                  </div>
                  <div className="activity-right">
                    <span className={`activity-amount numeric ${entry.amountCents < 0 ? 'amount-out' : 'amount-in'}`}>
                      {formatSignedCents(entry.amountCents)}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}

function PaymentMethodRow({ method }: { method: PaymentMethod }) {
  const available = method.status === 'AVAILABLE'
  return (
    <div className={`payment-row${available ? ' available' : ''}`} data-testid={`payment-${method.id}`}>
      <span className="payment-icon"><WalletIcon size={18} /></span>
      <div className="payment-main">
        <div className="payment-name">
          {method.name}
          {available
            ? <Badge tone="positive">Active</Badge>
            : <Badge>Coming later</Badge>}
        </div>
        <div className="payment-desc">{method.description}</div>
      </div>
    </div>
  )
}
