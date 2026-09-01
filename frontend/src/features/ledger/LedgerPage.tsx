import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ledgerApi, paymentsApi } from '../../api'
import type { LedgerEntry, LedgerEntryType, Page, PaymentIntent } from '../../api/types'
import { ChevronDownIcon, SearchIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Section, Skeleton } from '../../components/Surface'
import { Tile } from '../../components/Tile'
import type { TileName } from '../../components/tileNames'
import { EmptyState, ErrorState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useCurrentUser } from '../../hooks/useAuth'
import { formatCents, formatDateTime, formatSignedCents, formatTime, ledgerTypeText } from '../../lib/format'

/**
 * The payment history, and deliberately the densest screen in the app.
 *
 * <p>Everywhere else FareFlow rounds off the accounting and shows a rider what
 * they need. Here the technical vocabulary stays visible — TRIP_CHARGE, signed
 * cents, append-only — because this is the page you open when you do not believe
 * one of the other numbers, and it has to be checkable.
 *
 * <p>Green means money returned; red means money out. Neither is a brand colour,
 * so their appearance always carries information rather than decoration.
 */
const FILTERS: { id: 'ALL' | LedgerEntryType; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'TRIP_CHARGE', label: 'Charges' },
  { id: 'REFUND', label: 'Refunds' },
  { id: 'FARE_ADJUSTMENT', label: 'Adjustments' },
]

const LEDGER_TILES: Record<LedgerEntryType, TileName> = {
  TRIP_CHARGE: 'payments-wallet/receipt',
  REFUND: 'notifications/success',
  FARE_ADJUSTMENT: 'trip-states/fare-update',
}

export function LedgerPage() {
  const user = useCurrentUser()
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<'ALL' | LedgerEntryType>('ALL')
  const [query, setQuery] = useState('')

  const { data, loading, error, refetch } = useAsync<Page<LedgerEntry> | null>(
    () => ledgerApi.list(page),
    [page],
  )
  const {
    data: payments,
    loading: paymentsLoading,
    error: paymentsError,
    refetch: refetchPayments,
  } = useAsync<Page<PaymentIntent> | null>(() => paymentsApi.list(page), [page])

  /*
   * Filtering happens over the loaded page, not over the whole ledger: the API
   * paginates server-side and has no search parameter. Pretending otherwise
   * would quietly show a rider "no results" for an entry that exists two pages
   * back, so the caption below says exactly what is being searched.
   */
  const entries = useMemo(() => {
    if (!data) return []
    const needle = query.trim().toLowerCase()
    return data.content.filter((entry) => {
      if (filter !== 'ALL' && entry.type !== filter) return false
      if (needle && !entry.description.toLowerCase().includes(needle)) return false
      return true
    })
  }, [data, filter, query])

  const days = useMemo(() => groupByDay(entries), [entries])
  const filtering = filter !== 'ALL' || query.trim() !== ''

  if (!user) return null

  return (
    <div className="page">
      <PageHeader
        tile="financial-analytics/history"
        title="Payment history"
        subtitle="Review every transit charge, refund, and fare adjustment in one place. Corrections appear as new activity, so your history always remains complete."
        actions={(
          <div className="page-actions">
            <Link className="btn" to="/wallet">Back to wallet</Link>
            <Link className="btn btn-primary" to="/plan">Plan a trip</Link>
          </div>
        )}
      />

      {/* Receipts are the supporting half of this page: most visits are looking
          for a charge in the activity list below, not for a provider reference. */}
      <Section title="Receipts">
        <Card className="receipt-list payment-history-panel">
          {paymentsLoading && <div className="card-body"><Skeleton height={120} /></div>}
          {paymentsError && (
            <div className="card-body"><ErrorState error={paymentsError} onRetry={refetchPayments} /></div>
          )}
          {payments && payments.content.length === 0 && (
            <div className="receipt-empty">
              Receipts appear after a payment is created for a completed trip.
            </div>
          )}
          {payments?.content.map((payment) => (
            <PaymentReceipt key={payment.id} payment={payment} />
          ))}
        </Card>
      </Section>

      <Section title="Account activity" caption="Append-only charges, refunds, and corrections.">
        <div className="ledger-controls">
          <div className="filter-row" role="group" aria-label="Filter by entry type">
            {FILTERS.map((option) => (
              <button
                key={option.id}
                type="button"
                className="filter-chip"
                aria-pressed={filter === option.id}
                onClick={() => setFilter(option.id)}
              >
                {option.label}
              </button>
            ))}
          </div>

          <label className="ledger-search">
            <SearchIcon size={15} />
            <input
              className="ledger-search-input"
              type="search"
              placeholder="Search descriptions"
              aria-label="Search this page of entries"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
        </div>

        <Card className="ledger payment-history-panel">
        {loading && <div className="card-body"><Skeleton height={220} /></div>}
        {error && <div className="card-body"><ErrorState error={error} onRetry={refetch} /></div>}

        {data && data.content.length === 0 && (
          <EmptyState
            tile="financial-analytics/history"
            title="No payments yet"
            description="Completed trips, refunds, and fare adjustments will appear here as soon as they happen."
            action={<Link className="btn btn-primary" to="/plan">Plan a trip</Link>}
          />
        )}

        {data && data.content.length > 0 && entries.length === 0 && (
          <EmptyState
            tile="actions-ui/filter"
            title="Nothing on this page matches"
            description="This searches the payments currently loaded. Try another page, or clear the filter."
            action={
              <button className="btn" onClick={() => { setFilter('ALL'); setQuery('') }}>
                Clear filters
              </button>
            }
          />
        )}

        {entries.length > 0 && days.map(({ day, entries: dayEntries, netCents }) => (
          <section key={day} className="ledger-day-group">
            <header className="ledger-day">
              <span className="ledger-day-label">{day}</span>
              <span className="ledger-day-meta">
                {dayEntries.length} entr{dayEntries.length === 1 ? 'y' : 'ies'}
              </span>
              <span className={`ledger-day-net numeric${netCents >= 0 ? ' in' : ''}`}>
                {formatSignedCents(netCents)}
              </span>
            </header>
            {dayEntries.map((entry) => <LedgerRow key={entry.id} entry={entry} />)}
          </section>
        ))}

        {data && data.totalPages > 1 && (
          <div className="card-footer">
            <span className="stat-caption">
              Page {data.page + 1} of {data.totalPages} · {data.totalElements} entries
              {filtering && ` · showing ${entries.length} on this page`}
            </span>
            <div className="row">
              <button className="btn btn-sm" disabled={data.page === 0}
                      onClick={() => setPage((p) => p - 1)}>
                Previous
              </button>
              <button className="btn btn-sm" disabled={data.page >= data.totalPages - 1}
                      onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          </div>
        )}
        </Card>
      </Section>

      {/* The legend is reference material, not content, and stays last on the page. */}
      <Section title="About your payment history">
        <Card className="card-body legend payment-history-panel">
          <dl className="legend-list">
            <div>
              <dt>
                <span className="tile-plate legend-tile"><Tile name={LEDGER_TILES.TRIP_CHARGE} size={30} /></span>
                <span className="entry-type">Trip charge</span>
              </dt>
              <dd>A public-transit trip was paid for. Shown as money out.</dd>
            </div>
            <div>
              <dt>
                <span className="tile-plate legend-tile"><Tile name={LEDGER_TILES.REFUND} size={30} /></span>
                <span className="entry-type">Refund</span>
              </dt>
              <dd>Money was returned after a cancellation. Shown as money in.</dd>
            </div>
            <div>
              <dt>
                <span className="tile-plate legend-tile"><Tile name={LEDGER_TILES.FARE_ADJUSTMENT} size={30} /></span>
                <span className="entry-type">Fare adjustment</span>
              </dt>
              <dd>A fare correction, surcharge, or promotion was recorded.</dd>
            </div>
          </dl>
          <p className="legend-note">
            A minus sign means money out; a plus sign means money returned. Weekly
            spending elsewhere in FareFlow is calculated directly from this activity.
          </p>
        </Card>
      </Section>
    </div>
  )
}

function PaymentReceipt({ payment }: { payment: PaymentIntent }) {
  const date = payment.settledAt ?? payment.refundedAt ?? payment.failedAt ?? payment.createdAt
  const method = payment.paymentMethod === 'FAREFLOW_WALLET'
    ? 'FareFlow Wallet'
    : 'Simulated card'
  const operator = payment.trip?.providerName ?? payment.journeySummary
  const refunded = payment.status === 'REFUNDED'

  return (
    <details className="payment-receipt" data-testid={`receipt-${payment.id}`}>
      <summary>
        <span className="tile-plate receipt-icon" aria-hidden="true">
          <Tile name={paymentTile(payment)} size={38} />
          <span className={`payment-state-dot state-${payment.status.toLowerCase()}`} />
        </span>
        <span className="receipt-trip">
          <strong>{payment.origin} → {payment.destination}</strong>
          <small>{operator} · {formatDateTime(date)}</small>
        </span>
        <span className="receipt-method">{method}</span>
        <span className={`receipt-status status-${payment.status.toLowerCase()}`}>
          {refunded ? 'Refunded' : payment.status.toLowerCase()}
        </span>
        <strong className="receipt-fare numeric">{formatCents(payment.amountCents)}</strong>
        <ChevronDownIcon className="receipt-chevron" size={17} />
      </summary>
      <div className="receipt-detail">
        <dl>
          <div><dt>Payment ID</dt><dd className="numeric">{payment.id}</dd></div>
          <div><dt>Payment method</dt><dd>{method}</dd></div>
          <div><dt>Fare</dt><dd className="numeric">{formatCents(payment.amountCents)}</dd></div>
          <div><dt>Refund status</dt><dd>{refunded ? `Refunded ${formatDateTime(payment.refundedAt!)}` : 'Not refunded'}</dd></div>
          {payment.providerReference && (
            <div><dt>Reference</dt><dd className="numeric">{payment.providerReference}</dd></div>
          )}
        </dl>
        {payment.trip && (
          <Link className="btn btn-sm" to={`/trips?trip=${payment.trip.id}`}>
            Open trip
          </Link>
        )}
        {payment.events.length > 0 && (
          <ol className="receipt-events" aria-label="Payment status timeline">
            {payment.events.map((event) => (
              <li key={event.id}>
                <span className={`payment-state-dot state-${event.toStatus.toLowerCase()}`} aria-hidden="true" />
                <span><strong>{event.toStatus.toLowerCase()}</strong><small>{event.reason}</small></span>
                <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time>
              </li>
            ))}
          </ol>
        )}
      </div>
    </details>
  )
}

function LedgerRow({ entry }: { entry: LedgerEntry }) {
  const credit = entry.amountCents > 0

  return (
    <div className="ledger-row" data-testid={`ledger-${entry.id}`}>
      <span className={`tile-plate ledger-glyph ledger-glyph-${entry.type.toLowerCase()}`} aria-hidden="true">
        <Tile name={LEDGER_TILES[entry.type]} size={34} />
      </span>

      <div className="ledger-main">
        <span className="ledger-desc">{entry.description}</span>
        <span className="ledger-meta">
          <span className="entry-type">{ledgerTypeText(entry.type)}</span>
          <span className="ledger-sep" aria-hidden="true">·</span>
          <span className="numeric">{formatTime(entry.occurredAt)}</span>
          {entry.tripId !== null && (
            <>
              <span className="ledger-sep" aria-hidden="true">·</span>
              <Link className="ledger-trip-link numeric" to={`/trips?trip=${entry.tripId}`}>
                View trip #{entry.tripId}
              </Link>
            </>
          )}
        </span>
      </div>

      <span className={`ledger-amount numeric ${credit ? 'amount-in' : 'amount-out'}`}>
        {formatSignedCents(entry.amountCents)}
      </span>
    </div>
  )
}

function paymentTile(payment: PaymentIntent): TileName {
  if (payment.status === 'FAILED') return 'notifications/error'
  if (payment.status === 'REFUNDED') return 'notifications/success'
  return payment.paymentMethod === 'FAREFLOW_WALLET'
    ? 'payments-wallet/wallet'
    : 'payments-wallet/credit-card'
}

interface LedgerDay {
  day: string
  entries: LedgerEntry[]
  netCents: number
}

/** Groups entries by calendar day, preserving the server's newest-first ordering. */
function groupByDay(entries: LedgerEntry[]): LedgerDay[] {
  const days: LedgerDay[] = []

  for (const entry of entries) {
    const day = new Date(entry.occurredAt).toLocaleDateString(undefined, {
      weekday: 'short', month: 'short', day: 'numeric', year: 'numeric',
    })
    const existing = days.find((candidate) => candidate.day === day)
    if (existing) {
      existing.entries.push(entry)
      existing.netCents += entry.amountCents
    } else {
      days.push({ day, entries: [entry], netCents: entry.amountCents })
    }
  }

  return days
}
