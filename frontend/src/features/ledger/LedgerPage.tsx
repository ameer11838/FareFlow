import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ledgerApi } from '../../api'
import type { LedgerEntry, Page } from '../../api/types'
import { LedgerIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useCurrentUser } from '../../hooks/useAuth'
import { formatSignedCents, formatTime, ledgerTypeText } from '../../lib/format'

export function LedgerPage() {
  const user = useCurrentUser()
  const [page, setPage] = useState(0)

  const { data, loading, error, refetch } = useAsync<Page<LedgerEntry> | null>(
    () => ledgerApi.list(page),
    [page],
  )

  if (!user) return null

  const days = data ? groupByDay(data.content) : []

  return (
    <div className="page">
      <PageHeader
        eyebrow="Finance"
        title="Transportation Ledger"
        subtitle="Every charge, refund, and fare adjustment recorded by FareFlow. Entries are append-only — nothing is ever edited or deleted, and corrections are new entries."
      />

      <div className="card">
        {loading && <LoadingState />}
        {error && <ErrorState error={error} onRetry={refetch} />}

        {data && data.content.length === 0 && (
          <EmptyState
            icon={<LedgerIcon size={20} />}
            title="No ledger entries yet"
            description="Taking a trip writes a TRIP_CHARGE here. Cancelling one appends a REFUND alongside it, leaving the original charge intact."
            action={<Link className="btn btn-primary" to="/plan">Plan a trip</Link>}
          />
        )}

        {data && data.content.length > 0 && (
          <>
            {days.map(({ day, entries, netCents }) => (
              <section key={day}>
                <div className="ledger-day">
                  <span>{day}</span>
                  <span className="numeric">Net {formatSignedCents(netCents)}</span>
                </div>
                {entries.map((entry) => <LedgerRow key={entry.id} entry={entry} />)}
              </section>
            ))}

            {data.totalPages > 1 && (
              <div className="card-footer">
                <span className="stat-caption">
                  Page {data.page + 1} of {data.totalPages} · {data.totalElements} entries
                </span>
                <div className="row">
                  <button className="btn btn-sm" disabled={data.page === 0} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </button>
                  <button
                    className="btn btn-sm"
                    disabled={data.page >= data.totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <div className="card card-body" style={{ marginTop: 'var(--space-5)' }}>
        <span className="stat-label">Entry types</span>
        <ul style={{ marginTop: 'var(--space-3)', display: 'grid', gap: 'var(--space-2)' }}>
          <li className="secondary" style={{ fontSize: 'var(--text-sm)' }}>
            <code className="entry-type">TRIP_CHARGE</code> — a trip was taken. Always negative.
          </li>
          <li className="secondary" style={{ fontSize: 'var(--text-sm)' }}>
            <code className="entry-type">REFUND</code> — a trip was cancelled. Always positive, and
            the original charge is left untouched.
          </li>
          <li className="secondary" style={{ fontSize: 'var(--text-sm)' }}>
            <code className="entry-type">FARE_ADJUSTMENT</code> — a correction, surcharge, or promotion.
            Either sign, never zero.
          </li>
        </ul>
        <p className="muted" style={{ marginTop: 'var(--space-4)', fontSize: 'var(--text-xs)', maxWidth: '74ch' }}>
          Amounts are signed integer cents: negative is money out, positive is money in. Weekly
          spending on the dashboard is the sum of these rows — there is no stored total anywhere
          in the system.
        </p>
      </div>
    </div>
  )
}

function LedgerRow({ entry }: { entry: LedgerEntry }) {
  const isCredit = entry.amountCents > 0

  return (
    <div className="activity-row" data-testid={`ledger-${entry.id}`}>
      <span
        className="mode-icon"
        style={isCredit ? {
          background: 'var(--color-positive-soft)',
          borderColor: 'var(--color-positive-border)',
          color: 'var(--color-positive)',
        } : undefined}
        aria-hidden="true"
      >
        {isCredit ? '+' : '−'}
      </span>

      <div>
        <div className="activity-title">{entry.description}</div>
        <div className="activity-sub">
          {/* Technical terminology stays visible on purpose: this is a ledger. */}
          <span className="entry-type">{ledgerTypeText(entry.type)}</span>
          <span className="option-sep" />
          <span>{formatTime(entry.occurredAt)}</span>
          {entry.tripId !== null && (
            <>
              <span className="option-sep" />
              <span className="muted numeric">Trip #{entry.tripId}</span>
            </>
          )}
        </div>
      </div>

      <div className="activity-right">
        <span className={`activity-amount numeric ${isCredit ? 'amount-in' : 'amount-out'}`}>
          {formatSignedCents(entry.amountCents)}
        </span>
      </div>
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
      month: 'short',
      day: 'numeric',
      year: 'numeric',
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

