import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { journeysApi, tripsApi } from '../../api'
import { ApiError } from '../../api/client'
import type { Page, PersistedJourneyDetail, Trip } from '../../api/types'
import { ClockIcon, ModeIcon, RouteIcon, TransferIcon } from '../../components/Icons'
import { PageHeader } from '../../components/PageHeader'
import { Card, Section, Skeleton } from '../../components/Surface'
import { EmptyState, ErrorState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useCurrentUser } from '../../hooks/useAuth'
import { formatCents, formatDateTime, formatMinutes, labelText } from '../../lib/format'

/**
 * Trips as journeys rather than as records.
 *
 * <p>The row a rider recognises is "Newark → Manhattan, NJ Transit then subway,
 * 40 minutes, $20.40" — a shape, not a set of fields. The card leads with that,
 * and everything an accountant would want (fare breakdown, savings comparison,
 * leg-by-leg timings) lives one click down rather than in a wall of columns.
 */
export function TripHistoryPage() {
  const user = useCurrentUser()
  const [searchParams] = useSearchParams()
  const focusedTripId = Number(searchParams.get('trip')) || null
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
        subtitle="Every trip records the fare, duration, and operator exactly as they were at the time of travel."
        actions={<Link className="btn btn-primary" to="/plan">Plan another trip</Link>}
      />

      {actionError && (
        <Section>
          <Card className="card-body"><ErrorState error={actionError} /></Card>
        </Section>
      )}

      {loading && (
        <div className="trip-list">
          {[0, 1, 2].map((index) => (
            <Card key={index} className="card-body"><Skeleton height={104} /></Card>
          ))}
        </div>
      )}

      {error && <Card className="card-body"><ErrorState error={error} onRetry={refetch} /></Card>}

      {data && data.content.length === 0 && (
        <Card>
          <EmptyState
            icon={<RouteIcon size={22} />}
            title="No trips yet"
            description="Plan your first FareFlow trip and we'll start building personalized transportation insights — what you spend, what you save, and which routes are worth taking."
            action={<Link className="btn btn-primary btn-lg" to="/plan">Plan a trip</Link>}
          />
        </Card>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="trip-list">
            {data.content.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                onCancel={cancel}
                cancelling={cancelling === trip.id}
                disabled={cancelling !== null}
                initiallyOpen={focusedTripId === trip.id}
              />
            ))}
          </div>

          {data.totalPages > 1 && (
            <div className="pager">
              <span className="stat-caption">
                Page {data.page + 1} of {data.totalPages} · {data.totalElements} trips
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
        </>
      )}
    </div>
  )
}

function TripCard({ trip, onCancel, cancelling, disabled, initiallyOpen }: {
  trip: Trip
  onCancel: (id: number) => void
  cancelling: boolean
  disabled: boolean
  initiallyOpen: boolean
}) {
  const [open, setOpen] = useState(initiallyOpen)
  const cancelled = trip.status === 'CANCELLED'

  useEffect(() => {
    if (!initiallyOpen) return
    setOpen(true)
    document.getElementById(`trip-record-${trip.id}`)
      ?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  }, [initiallyOpen, trip.id])

  return (
    <Card
      id={`trip-record-${trip.id}`}
      className={`trip${cancelled ? ' trip-cancelled' : ''}${initiallyOpen ? ' trip-focused' : ''}`}
      data-testid={`trip-${trip.id}`}
    >
      <div className="trip-body">
        <div className="trip-main">
          {/* The arrow is decorative, so the heading carries a spoken label:
              "Newark to Manhattan" rather than "Newark right-arrow Manhattan". */}
          <h3 className="trip-route" aria-label={`${trip.origin} to ${trip.destination}`}>
            <span>{trip.origin}</span>
            <span className="trip-arrow" aria-hidden="true">→</span>
            <span>{trip.destination}</span>
          </h3>

          {/* The operator strip: what a rider actually pictures when they
              remember a trip. */}
          <div className="trip-line">
            <span className="trip-line-node">
              <ModeIcon mode={trip.mode} size={15} />
            </span>
            <span className="trip-line-track" aria-hidden="true" />
            <span className="trip-line-name">{trip.providerName}</span>
          </div>

          <div className="trip-facts">
            <span className="trip-fact">
              <ClockIcon size={14} />{formatMinutes(trip.durationMinutes)}
            </span>
            <span className="trip-fact">
              <TransferIcon size={14} />
              {trip.transfers === 0 ? 'Direct' : `${trip.transfers} transfer${trip.transfers > 1 ? 's' : ''}`}
            </span>
            <span className="trip-fact trip-fact-muted">{formatDateTime(trip.takenAt)}</span>
            {trip.stopsTravelled !== null && trip.stopsTravelled !== undefined && (
              <span className="trip-fact">{trip.stopsTravelled} stop{trip.stopsTravelled === 1 ? '' : 's'}</span>
            )}
            {trip.distanceMetres !== null && trip.distanceMetres !== undefined && (
              <span className="trip-fact numeric">{(trip.distanceMetres / 1609.344).toFixed(1)} mi</span>
            )}
          </div>
        </div>

        <div className="trip-side">
          <span className="trip-fare numeric">{formatCents(trip.fareCents)}</span>
          {trip.fareModel === 'FAREFLOW_USAGE_V1' && (
            <span className="trip-fare-model">Simulated usage fare</span>
          )}
          <TripStatus trip={trip} cancelled={cancelled} />
        </div>
      </div>

      <div className="trip-foot">
        <Savings trip={trip} cancelled={cancelled} />

        <div className="trip-actions">
          {trip.journeyId !== null && (
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => setOpen((value) => !value)}
              aria-expanded={open}
              data-testid={`itinerary-toggle-${trip.journeyId}`}
            >
              {open ? 'Hide itinerary' : 'View itinerary'}
            </button>
          )}
          {!cancelled && (
            <button
              className="btn btn-sm btn-danger"
              onClick={() => onCancel(trip.id)}
              disabled={disabled}
            >
              {cancelling ? 'Cancelling…' : 'Cancel'}
            </button>
          )}
        </div>
      </div>

      {open && trip.journeyId !== null && (
        <Itinerary journeyId={trip.journeyId} trip={trip} />
      )}
    </Card>
  )
}

function TripStatus({ trip, cancelled }: { trip: Trip; cancelled: boolean }) {
  if (cancelled) {
    return <span className="trip-status trip-status-cancelled">Cancelled</span>
  }
  return (
    <span className="trip-status trip-status-done">
      Completed
      {trip.selectedLabel !== 'MANUAL' && (
        <span className="trip-status-label">{labelText(trip.selectedLabel)}</span>
      )}
    </span>
  )
}

/**
 * What the choice was worth.
 *
 * <p>Null means "not computable" — there was no alternative to compare against —
 * and says so rather than printing a zero, which would read as "FareFlow saved
 * you nothing".
 */
function Savings({ trip, cancelled }: { trip: Trip; cancelled: boolean }) {
  if (cancelled) {
    return (
      <span className="trip-savings muted">
        Refunded {formatCents(trip.fareCents)} to your payment history
      </span>
    )
  }
  if (trip.savedVersusFastestCents === null) {
    return <span className="trip-savings muted">No alternative route to compare against</span>
  }
  if (trip.savedVersusFastestCents > 0) {
    return (
      <span className="trip-savings trip-savings-good numeric">
        Saved {formatCents(trip.savedVersusFastestCents)} vs the fastest route
      </span>
    )
  }
  if (trip.savedVersusFastestCents === 0) {
    return <span className="trip-savings muted">Took the fastest route</span>
  }
  return (
    <span className="trip-savings muted numeric">
      {formatCents(-trip.savedVersusFastestCents)} more than the fastest route
    </span>
  )
}

/**
 * The itinerary as it was when the rider chose it.
 *
 * <p>Loaded on demand: a page of trips must not fetch every itinerary up front.
 * The legs come from the stored snapshot, so this shows what was actually taken
 * even after schedules and fares have moved on.
 */
function Itinerary({ journeyId, trip }: { journeyId: number; trip: Trip }) {
  const { data, loading, error } = useAsync<PersistedJourneyDetail>(
    () => journeysApi.detail(journeyId),
    [journeyId],
  )

  if (loading) {
    return (
      <div className="itinerary" data-testid={`itinerary-${journeyId}`}>
        <Skeleton height={140} />
      </div>
    )
  }
  if (error || !data) {
    return (
      <div className="itinerary" data-testid={`itinerary-${journeyId}`}>
        <p className="muted">This itinerary could not be loaded.</p>
      </div>
    )
  }

  return (
    <div className="itinerary" data-testid={`itinerary-${journeyId}`}>
      <ol className="timeline">
        {data.legs.map((leg, index) => (
          <li key={leg.sequence} className={`timeline-leg mode-${leg.mode.toLowerCase()}`}>
            <div className="timeline-rail" aria-hidden="true">
              <span className="timeline-dot" />
              {index < data.legs.length - 1 && <span className="timeline-line" />}
            </div>

            <div className="timeline-body">
              <span className="timeline-station">{leg.fromName}</span>
              <div className="timeline-ride">
                <span className="timeline-mode" aria-hidden="true">
                  <ModeIcon mode={leg.mode} size={14} />
                </span>
                <span className="timeline-line-name">{leg.lineName}</span>
                <span className="timeline-duration numeric">
                  {formatMinutes(leg.durationMinutes)}
                  {leg.waitMinutes > 0 && (
                    <span className="timeline-wait"> · {leg.waitMinutes} min wait</span>
                  )}
                </span>
              </div>
            </div>
          </li>
        ))}

        {/* The final arrival is a destination, not another departure. */}
        <li className="timeline-leg timeline-end">
          <div className="timeline-rail" aria-hidden="true">
            <span className="timeline-dot timeline-dot-end" />
          </div>
          <div className="timeline-body">
            <span className="timeline-station">
              {data.legs[data.legs.length - 1]?.toName ?? trip.destination}
            </span>
          </div>
        </li>
      </ol>

      <div className="itinerary-summary">
        <div className="itinerary-figures">
          <Figure label="Total duration" value={formatMinutes(data.totalDurationMinutes)} />
          <Figure label="Transfers" value={data.transfers === 0 ? 'Direct' : String(data.transfers)} />
          <Figure label="Walking" value={formatMinutes(data.walkingMinutes)} />
          <Figure
            label="Fare"
            value={data.totalFareCents === null ? 'Varies' : formatCents(data.totalFareCents)}
            note={data.fareStatus === 'ESTIMATED' ? 'Estimated' : undefined}
          />
        </div>

        {data.fareBreakdown.length > 0 && (
          <div className="fare-lines">
            <span className="stat-label">Fare breakdown</span>
            <ul>
              {data.fareBreakdown.map((line) => <li key={line}>{line}</li>)}
            </ul>
          </div>
        )}

        {trip.baselineFareCents !== null && (
          <div className="fare-lines">
            <span className="stat-label">Versus the fastest route</span>
            <ul>
              <li>Fastest route fare · {formatCents(trip.baselineFareCents)}</li>
              <li>You paid · {formatCents(trip.fareCents)}</li>
              {trip.savedVersusFastestCents !== null && (
                <li className={trip.savedVersusFastestCents > 0 ? 'positive' : undefined}>
                  Difference · {formatCents(trip.savedVersusFastestCents)}
                </li>
              )}
            </ul>
          </div>
        )}
      </div>
    </div>
  )
}

function Figure({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="figure">
      <span className="figure-label">{label}</span>
      <span className="figure-value numeric">
        {value}
        {note && <span className="figure-note">{note}</span>}
      </span>
    </div>
  )
}
