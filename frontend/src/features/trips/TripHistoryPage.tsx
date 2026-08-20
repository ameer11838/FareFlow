import { useState } from 'react'
import { Link } from 'react-router-dom'
import { journeysApi, tripsApi } from '../../api'
import { ApiError } from '../../api/client'
import type { Page, PersistedJourneyDetail, Trip } from '../../api/types'
import { Badge } from '../../components/Badge'
import { ClockIcon, ModeIcon, RouteIcon, TransferIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useCurrentUser } from '../../hooks/useAuth'
import { formatCents, formatDateTime, formatMinutes, labelText } from '../../lib/format'
import { JourneyLegs } from '../plan/JourneyLegs'

export function TripHistoryPage() {
  const user = useCurrentUser()
  const [page, setPage] = useState(0)
  const [cancelling, setCancelling] = useState<number | null>(null)
  const [actionError, setActionError] = useState<ApiError | null>(null)

  const { data, loading, error, refetch } = useAsync<Page<Trip> | null>(
    () => tripsApi.list(page),
    [page],
  )

  const cancel = async (tripId: number) => {
    setCancelling(tripId)
    setActionError(null)
    try {
      await tripsApi.cancel(tripId)
      refetch()
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught : new ApiError(0, { title: String(caught) }))
    } finally {
      setCancelling(null)
    }
  }

  if (!user) return null

  return (
    <div className="page">
      <PageHeader
        eyebrow="Activity"
        title="Trips"
        subtitle="Every trip records the fare, duration, and provider exactly as they were at the time of travel."
      />

      {actionError && (
        <section className="section">
          <div className="card"><ErrorState error={actionError} /></div>
        </section>
      )}

      <div className="card">
        {loading && <LoadingState />}
        {error && <ErrorState error={error} onRetry={refetch} />}

        {data && data.content.length === 0 && (
          <EmptyState
            icon={<RouteIcon size={20} />}
            title="No trips yet"
            description="Choose a route from the Plan trip page and it will appear here with its fare and savings."
            action={<Link className="btn btn-primary" to="/plan">Plan a trip</Link>}
          />
        )}

        {data && data.content.length > 0 && (
          <>
            <div className="activity">
              {data.content.map((trip) => (
                <TripRow
                  key={trip.id}
                  trip={trip}
                  onCancel={cancel}
                  cancelling={cancelling === trip.id}
                  disabled={cancelling !== null}
                />
              ))}
            </div>

            {data.totalPages > 1 && (
              <div className="card-footer">
                <span className="stat-caption">
                  Page {data.page + 1} of {data.totalPages} · {data.totalElements} trips
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
    </div>
  )
}

function TripRow({ trip, onCancel, cancelling, disabled }: {
  trip: Trip
  onCancel: (id: number) => void
  cancelling: boolean
  disabled: boolean
}) {
  const cancelled = trip.status === 'CANCELLED'

  return (
    <div className={`activity-row${cancelled ? ' cancelled' : ''}`} data-testid={`trip-${trip.id}`}>
      <span className="mode-icon"><ModeIcon mode={trip.mode} /></span>

      <div>
        <div className="activity-title">{trip.origin} → {trip.destination}</div>
        <div className="activity-sub">
          <span>{trip.providerName}</span>
          <span className="option-sep" />
          <span>{formatDateTime(trip.takenAt)}</span>
          <span className="option-sep" />
          <span className="row" style={{ gap: 4 }}>
            <ClockIcon size={13} /> {formatMinutes(trip.durationMinutes)}
          </span>
          {trip.transfers > 0 && (
            <>
              <span className="option-sep" />
              <span className="row" style={{ gap: 4 }}>
                <TransferIcon size={13} /> {trip.transfers}
              </span>
            </>
          )}
        </div>
        <div className="activity-sub" style={{ marginTop: 6 }}>
          {cancelled ? (
            <>
              <Badge tone="danger">Cancelled</Badge>
              <span className="muted">Refunded {formatCents(trip.fareCents)} to your ledger</span>
            </>
          ) : (
            <>
              <Badge tone="positive">Completed</Badge>
              <Badge>{labelText(trip.selectedLabel)}</Badge>
            </>
          )}
        </div>
      </div>

      <div className="activity-right">
        <span className="activity-amount numeric">{formatCents(trip.fareCents)}</span>
        {trip.journeyId !== null && <JourneyTimeline journeyId={trip.journeyId} />}
        {!cancelled && (
          <span className="activity-note numeric">
            {trip.savedVersusFastestCents === null ? (
              <span className="muted" title="No alternative route existed, so there is no honest comparison">
                No comparison available
              </span>
            ) : trip.savedVersusFastestCents > 0 ? (
              `Saved ${formatCents(trip.savedVersusFastestCents)} vs fastest`
            ) : trip.savedVersusFastestCents === 0 ? (
              <span className="muted">Took the fastest route</span>
            ) : (
              <span className="muted">
                {formatCents(-trip.savedVersusFastestCents)} over the fastest
              </span>
            )}
          </span>
        )}
        {!cancelled && (
          <button
            className="btn btn-sm btn-danger"
            style={{ marginTop: 4 }}
            onClick={() => onCancel(trip.id)}
            disabled={disabled}
          >
            {cancelling ? 'Cancelling…' : 'Cancel'}
          </button>
        )}
      </div>
    </div>
  )
}


/**
 * Expandable itinerary for a trip taken from a discovered journey.
 *
 * <p>Collapsed by default so the list stays scannable, and loaded on demand so a
 * page of trips does not fetch every itinerary up front. The legs come from the
 * stored snapshot, so they show what was actually chosen.
 */
function JourneyTimeline({ journeyId }: { journeyId: number }) {
  const [open, setOpen] = useState(false)
  const [detail, setDetail] = useState<PersistedJourneyDetail | null>(null)
  const [loading, setLoading] = useState(false)

  const toggle = async () => {
    const next = !open
    setOpen(next)
    if (next && !detail) {
      setLoading(true)
      try {
        setDetail(await journeysApi.detail(journeyId))
      } catch {
        // Leave it closed rather than showing a broken timeline.
        setOpen(false)
      } finally {
        setLoading(false)
      }
    }
  }

  return (
    <>
      <button
        type="button"
        className="route-tile-legs-toggle"
        onClick={toggle}
        aria-expanded={open}
        data-testid={`itinerary-toggle-${journeyId}`}
      >
        {open ? 'Hide itinerary' : 'View itinerary'}
      </button>

      {open && (
        <div className="trip-itinerary" data-testid={`itinerary-${journeyId}`}>
          {loading && <span className="muted">Loading…</span>}
          {detail && (
            <>
              <JourneyLegs
                legs={detail.legs.map((leg) => ({
                  mode: leg.mode as never,
                  agency: leg.agency,
                  lineName: leg.lineName,
                  fromName: leg.fromName,
                  toName: leg.toName,
                  durationMinutes: leg.durationMinutes,
                  waitMinutes: leg.waitMinutes,
                  waypoints: [],
                }))}
              />
              {detail.fareBreakdown.length > 0 && (
                <details className="fare-breakdown">
                  <summary>Fare breakdown</summary>
                  <ul>
                    {detail.fareBreakdown.map((line) => <li key={line}><span>{line}</span></li>)}
                  </ul>
                </details>
              )}
            </>
          )}
        </div>
      )}
    </>
  )
}
