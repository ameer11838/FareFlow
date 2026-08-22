import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ledgerApi } from '../../api'
import type { LedgerEntry, LedgerEntryType, Page } from '../../api/types'
import { PaymentHistoryIcon, SearchIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Section, Skeleton } from '../../components/Surface'
import { EmptyState, ErrorState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useCurrentUser } from '../../hooks/useAuth'
import { formatSignedCents, formatTime, ledgerTypeText } from '../../lib/format'

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

export function LedgerPage() {
  const user = useCurrentUser()
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<'ALL' | LedgerEntryType>('ALL')
  const [query, setQuery] = useState('')

  const { data, loading, error, refetch } = useAsync<Page<LedgerEntry> | null>(
    () => ledgerApi.list(page),
    [page],
  )

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
    <div className="page page-narrow">
      <PageHeader
        eyebrow="Money"
        title="Payment history"
        subtitle="Review every transit charge, refund, and fare adjustment in one place. Corrections appear as new activity, so your history always remains complete."
        actions={(
          <div className="page-actions">
            <Link className="btn" to="/wallet">Back to wallet</Link>
            <Link className="btn btn-primary" to="/plan">Plan a trip</Link>
          </div>
        )}
      />

      <Section>
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
      </Section>

      <Card className="ledger">
        {loading && <div className="card-body"><Skeleton height={220} /></div>}
        {error && <div className="card-body"><ErrorState error={error} onRetry={refetch} /></div>}

        {data && data.content.length === 0 && (
          <EmptyState
            icon={<PaymentHistoryIcon size={22} />}
            title="No payments yet"
            description="Completed trips, refunds, and fare adjustments will appear here as soon as they happen."
            action={<Link className="btn btn-primary" to="/plan">Plan a trip</Link>}
          />
        )}

        {data && data.content.length > 0 && entries.length === 0 && (
          <EmptyState
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

      <Section title="About your payment history">
        <Card className="card-body legend">
          <dl className="legend-list">
            <div>
              <dt><span className="entry-type">Trip charge</span></dt>
              <dd>A public-transit trip was paid for. Shown as money out.</dd>
            </div>
            <div>
              <dt><span className="entry-type">Refund</span></dt>
              <dd>Money was returned after a cancellation. Shown as money in.</dd>
            </div>
            <div>
              <dt><span className="entry-type">Fare adjustment</span></dt>
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

function LedgerRow({ entry }: { entry: LedgerEntry }) {
  const credit = entry.amountCents > 0

  return (
    <div className="ledger-row" data-testid={`ledger-${entry.id}`}>
      <span className={`ledger-glyph${credit ? ' ledger-glyph-in' : ''}`} aria-hidden="true">
        {credit ? '↓' : '↑'}
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
