import { useEffect, useState } from 'react'
import type {
  JourneyOption, JourneySearchResponse, PaymentIntent, PaymentRail, TransitSession,
} from '../../api/types'
import {
  CheckIcon, ClockIcon, CloseIcon, InfoIcon, WalletIcon,
} from '../../components/Icons'
import { ModeTile } from '../../components/Tile'
import { formatCents, formatMinutes } from '../../lib/format'

/**
 * The operational surface between route selection and a saved trip.
 *
 * It uses the route provider's actual facts and labels rider-confirmed progress
 * plainly. No local timer, distance, or stop value is allowed to become money;
 * every displayed fare comes back from the session API.
 */
export function TransitSessionSheet({
  option, result, session, payment, processing, error,
  onClose, onStart, onAdvance, onEnd, onPay,
}: {
  option: JourneyOption | null
  result: JourneySearchResponse | null
  session: TransitSession | null
  payment: PaymentIntent | null
  processing: boolean
  error: string | null
  onClose: () => void
  onStart: () => void
  onAdvance: (outcome?: 'REACHED' | 'SKIPPED' | 'DIVERTED') => void
  onEnd: () => void
  onPay: (method: PaymentRail) => void
}) {
  const [method, setMethod] = useState<PaymentRail>('FAREFLOW_WALLET')
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    if (!session || !session.canEnd) return
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [session])

  const elapsedSeconds = session
    ? session.endedAt
      ? session.elapsedSeconds
      : Math.max(session.elapsedSeconds,
          Math.floor((now - new Date(session.startedAt).getTime()) / 1_000))
    : 0

  const titleId = 'transit-session-title'
  const phase = !session ? 'ready'
    : session.status === 'STARTED' || session.status === 'IN_PROGRESS' ? 'active'
      : session.status === 'NO_CHARGE' ? 'no-charge'
        : 'checkout'

  return (
    <div className={`checkout-backdrop session-backdrop session-backdrop-${phase}`}
         role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !processing && phase !== 'active') onClose()
    }}>
      <section className="checkout-sheet session-sheet" role="dialog" aria-modal={phase !== 'active'}
               aria-labelledby={titleId} data-testid="transit-session-sheet">
        <header className="checkout-head session-head">
          <div>
            <span className="checkout-eyebrow">{phaseLabel(phase)}</span>
            <h2 id={titleId}>{phaseTitle(phase)}</h2>
          </div>
          {phase !== 'active' && (
            <button className="icon-btn" type="button" onClick={onClose}
                    disabled={processing} aria-label="Close trip panel">
              <CloseIcon />
            </button>
          )}
        </header>

        {phase === 'ready' && option && result && (
          <ReadyToRide option={option} result={result} processing={processing} onStart={onStart} />
        )}

        {phase === 'active' && session && (
          <ActiveTrip session={session} elapsedSeconds={elapsedSeconds} processing={processing}
                      onAdvance={onAdvance} onEnd={onEnd} />
        )}

        {phase === 'no-charge' && session && (
          <NoCharge session={session} onClose={onClose} />
        )}

        {phase === 'checkout' && session && (
          <TripCheckout session={session} elapsedSeconds={elapsedSeconds} payment={payment}
                        method={method} onMethod={setMethod} processing={processing}
                        onPay={onPay} />
        )}

        {(error || payment?.failureMessage) && (
          <div className="checkout-error" role="alert">
            <strong>{payment?.status === 'FAILED' ? 'Payment failed' : 'Trip update failed'}</strong>
            <span>{payment?.failureMessage ?? error}</span>
          </div>
        )}
      </section>
    </div>
  )
}

function ReadyToRide({ option, result, processing, onStart }: {
  option: JourneyOption
  result: JourneySearchResponse
  processing: boolean
  onStart: () => void
}) {
  const rides = option.legs.filter((leg) => leg.mode !== 'WALK')
  const first = rides[0]
  const scheduledArrival = [...rides].reverse().find((leg) => leg.arrivalTime)?.arrivalTime
  return (
    <>
      <RouteIdentity origin={result.origin.displayName} destination={result.destination.displayName}
                     summary={option.summary} mode={first?.mode ?? 'BUS'} />

      <div className="session-facts">
        <Fact label="Expected arrival"
              value={scheduledArrival ? formatClock(scheduledArrival) : 'Not provided'} />
        <Fact label={option.fareSource === 'PROVIDER' ? 'Provider fare estimate' : 'Published fare'}
              value={option.fareCents === null ? 'Unavailable' : formatCents(option.fareCents)} />
        <Fact label="Charged now" value="$0.00" tone="positive" />
      </div>

      <p className="session-usage-preview">
        FareFlow usage simulation: {formatFareRange(option.usageFareMinCents, option.usageFareMaxCents)}
      </p>

      <div className="session-source-note">
        <InfoIcon size={16} />
        <span>
          This is FareFlow’s proposed usage-pricing simulation, not an agency ticket.
          Start only when you are ready to board. Ending with no recorded transit progress costs $0.
        </span>
      </div>

      <button className="btn btn-primary checkout-submit" type="button"
              onClick={onStart} disabled={processing}>
        {processing ? 'Starting trip…' : 'Start trip'}
      </button>
      <p className="checkout-fineprint">
        FareFlow stores this selected route server-side. The browser cannot send a fare.
      </p>
    </>
  )
}

function ActiveTrip({ session, elapsedSeconds, processing, onAdvance, onEnd }: {
  session: TransitSession
  elapsedSeconds: number
  processing: boolean
  onAdvance: (outcome?: 'REACHED' | 'SKIPPED' | 'DIVERTED') => void
  onEnd: () => void
}) {
  const progress = session.progressUnitsTotal > 0
    ? session.progressUnitsCompleted / session.progressUnitsTotal : 0
  return (
    <>
      <div className="session-live-line">
        <span className="session-live-status"><span className="session-live-dot" />Session active</span>
        <span className="session-trip-duration">
          <span><ClockIcon size={13} /> Trip duration</span>
          <strong className="numeric">{formatTimer(elapsedSeconds)}</strong>
        </span>
      </div>

      <RouteIdentity origin={session.origin} destination={session.destination}
                     summary={session.currentLine} mode={session.currentMode} />

      <section className="session-current-fare" aria-label="Current fare — FareFlow usage simulation">
        <div>
          <span>FareFlow usage simulation</span>
          <small>Locked to {session.completedStops} completed stop{session.completedStops === 1 ? '' : 's'}</small>
        </div>
        <strong className="numeric">{formatCents(session.currentFareCents)}</strong>
        <p>
          Waiting time and delays never increase this amount.
          {session.publishedFareCents != null
            ? ` Published route ${session.publishedFareStatus === 'EXACT' ? 'fare' : 'estimate'}: ${formatCents(session.publishedFareCents)}.`
            : ' The route provider did not return a complete published fare.'}
        </p>
        <div className="session-fare-policy">
          <span>{session.fareCategoryName}</span>
          <span>{formatCents(session.dailyCapRemainingCents)} until today’s cap</span>
          {(session.transferDiscountCents + session.concessionDiscountCents
            + session.capDiscountCents) > 0 && (
            <span>{formatCents(session.transferDiscountCents + session.concessionDiscountCents
              + session.capDiscountCents)} saved by fare rules</span>
          )}
        </div>
      </section>

      <div className="session-stop-board">
        <span className="session-stop-kicker">Current stop</span>
        <strong>{stopDisplayName(session.currentStop, session.progressUnitsCompleted, session.plannedStops)}</strong>
        <div className="session-next-stop">
          <span>Next</span>
          <span>
            <b>{session.nextStop
              ?? (session.canAdvance
                ? stopDisplayName(null, session.progressUnitsCompleted + 1, session.plannedStops)
                : session.destination)}</b>
            {session.canAdvance && (
              <small>+{formatCents(session.nextStopFareIncreaseCents)} when reached</small>
            )}
          </span>
        </div>
      </div>

      <div className="session-progress" aria-label={`${Math.round(progress * 100)}% trip progress`}>
        <div className="session-progress-head">
          <span>Trip progress</span>
          <b>{session.progressUnitsCompleted} of {session.progressUnitsTotal} route stops</b>
        </div>
        <span className="session-progress-track"><span style={{ width: `${progress * 100}%` }} /></span>
      </div>

      <StopFareTimeline session={session} />

      <div className="session-facts session-facts-active">
        <Fact label="Distance" value={formatDistance(session.distanceTravelledMetres)} />
        <Fact label="Completed" value={`${session.completedStops} stop${session.completedStops === 1 ? '' : 's'}`} />
        <Fact label="Transfer" value={session.transferToLine ?? 'None ahead'} />
      </div>

      <div className="session-source-note neutral">
        <InfoIcon size={16} />
        <span>
          Fare updates only after a stop is completed. The timer measures trip duration only.
          {session.hasRealtimeData
            ? ' Agency real-time updates are available for at least one scheduled leg.'
            : ' No live vehicle feed is claimed; progress is rider-confirmed.'}
        </span>
      </div>

      <div className="session-actions">
        <button className="btn btn-primary" type="button" onClick={() => onAdvance('REACHED')}
                disabled={processing || !session.canAdvance}>
          {processing ? 'Updating…' : session.canAdvance ? 'Complete next stop' : 'Route completed'}
        </button>
        <button className="btn session-end" type="button" onClick={onEnd} disabled={processing}>
          End trip
        </button>
      </div>
      {session.canAdvance && (
        <details className="session-service-exception">
          <summary>Did the vehicle skip this stop or divert?</summary>
          <p>Service exceptions advance the route without adding a stop charge.</p>
          <div>
            <button className="btn btn-ghost" type="button" disabled={processing}
                    onClick={() => onAdvance('SKIPPED')}>Stop was skipped</button>
            <button className="btn btn-ghost" type="button" disabled={processing}
                    onClick={() => onAdvance('DIVERTED')}>Route diverted</button>
          </div>
        </details>
      )}
    </>
  )
}

function StopFareTimeline({ session }: { session: TransitSession }) {
  return (
    <section className="session-stop-fares" aria-labelledby="stop-fare-title">
      <div className="session-stop-fares-head">
        <div>
          <strong id="stop-fare-title">Simulated fare by stop</strong>
          <span>FareFlow usage charges post at completed stops</span>
        </div>
        <span>{session.progressUnitsCompleted}/{session.progressUnitsTotal}</span>
      </div>
      <ol className="session-stop-list">
        {session.stopFareProgress.map((stop) => (
          <li key={stop.sequence} className={`session-stop-row is-${stop.state.toLowerCase()}`}
              aria-current={stop.state === 'CURRENT' ? 'step' : undefined}>
            <span className="session-stop-rail" aria-hidden="true"><span /></span>
            <span className="session-stop-copy">
              <b>{stopDisplayName(stop.stopName, stop.sequence, session.plannedStops)}</b>
              <small>{stop.sequence === 0
                ? 'Boarding point · no charge'
                : `${stop.lineName} · ${stopStateLabel(stop.state)}`}</small>
            </span>
            <span className="session-stop-charge numeric">
              <b>{stop.sequence === 0 ? '$0.00'
                : stop.state === 'SKIPPED' || stop.state === 'DIVERTED' ? '$0.00'
                  : `+${formatCents(stop.fareIncrementCents)}`}</b>
              <small>{stop.sequence === 0 ? 'No charge'
                : stop.totalDiscountCents > 0
                  ? `${formatCents(stop.totalDiscountCents)} discount`
                  : `${formatCents(stop.cumulativeFareCents)} total`}</small>
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}

function NoCharge({ session, onClose }: { session: TransitSession; onClose: () => void }) {
  return (
    <>
      <div className="session-result-icon"><CheckIcon size={24} /></div>
      <div className="session-result-copy">
        <strong>No fare charged</strong>
        <span>No transit progress was recorded for {session.summary}, so this simulated session costs $0.</span>
      </div>
      <div className="session-total"><span>Total</span><strong className="numeric">$0.00</strong></div>
      <button className="btn btn-primary checkout-submit" type="button" onClick={onClose}>Done</button>
    </>
  )
}

function TripCheckout({ session, elapsedSeconds, payment, method, onMethod, processing, onPay }: {
  session: TransitSession
  elapsedSeconds: number
  payment: PaymentIntent | null
  method: PaymentRail
  onMethod: (method: PaymentRail) => void
  processing: boolean
  onPay: (method: PaymentRail) => void
}) {
  const fare = session.finalFareCents ?? 0
  return (
    <>
      <RouteIdentity origin={session.origin} destination={session.destination}
                     summary={session.summary} mode={session.currentMode} />
      <div className="session-complete-summary">
        <span><ClockIcon size={15} /> {formatMinutes(Math.max(1, Math.ceil(elapsedSeconds / 60)))}</span>
        <span>{session.completedStops} stops</span>
        <span>{formatDistance(session.distanceTravelledMetres)}</span>
      </div>

      <div className="session-total">
        <span>Fare</span>
        <strong className="numeric">{formatCents(fare)}</strong>
      </div>

      {session.fareBreakdown.length > 0 && (
        <details className="session-fare-details">
          <summary>How this fare was calculated</summary>
          <ul>{session.fareBreakdown.map((line) => <li key={line}>{line}</li>)}</ul>
          <p>Trip time is not part of this calculation.</p>
        </details>
      )}

      <fieldset className="checkout-methods" disabled={processing}>
        <legend>Payment method</legend>
        <PaymentMethodChoice method="FAREFLOW_WALLET" selected={method} onSelect={onMethod}
                             title="FareFlow Wallet" detail="Applies to your weekly transit budget" />
        <PaymentMethodChoice method="SIMULATED_CARD" selected={method} onSelect={onMethod}
                             title="Simulated card" detail="No real card or money movement" />
      </fieldset>

      <button className="btn btn-primary checkout-submit" type="button"
              onClick={() => onPay(method)} disabled={processing || payment?.status === 'SETTLED'}>
        {processing ? 'Authorizing payment…'
          : payment?.status === 'FAILED' ? 'Retry payment'
            : payment?.status === 'SETTLED' ? 'Payment complete'
              : `Pay ${formatCents(fare)} with FareFlow`}
      </button>
      <p className="checkout-fineprint">{session.simulationNotice}</p>
    </>
  )
}

function PaymentMethodChoice({ method, selected, onSelect, title, detail }: {
  method: PaymentRail
  selected: PaymentRail
  onSelect: (method: PaymentRail) => void
  title: string
  detail: string
}) {
  return (
    <label className={`checkout-method${selected === method ? ' selected' : ''}`}>
      <input type="radio" name="session-payment-method" value={method}
             checked={selected === method} onChange={() => onSelect(method)} />
      <span className="checkout-method-icon">
        {method === 'FAREFLOW_WALLET' ? <WalletIcon /> : <span className="numeric">••••</span>}
      </span>
      <span><strong>{title}</strong><small>{detail}</small></span>
      {selected === method && <CheckIcon />}
    </label>
  )
}

function RouteIdentity({ origin, destination, summary, mode }: {
  origin: string; destination: string; summary: string; mode: string
}) {
  return (
    <div className="session-route-identity">
      <span className="session-mode"><ModeTile mode={mode.toLowerCase()} size={42} /></span>
      <div><strong>{origin} → {destination}</strong><span>{summary}</span></div>
    </div>
  )
}

function Fact({ label, value, tone }: { label: string; value: string; tone?: 'positive' }) {
  return <div className={`session-fact${tone ? ` ${tone}` : ''}`}><span>{label}</span><strong>{value}</strong></div>
}

function phaseLabel(phase: string): string {
  if (phase === 'ready') return 'Selected transit'
  if (phase === 'active') return 'Track trip'
  if (phase === 'no-charge') return 'Session ended'
  return 'Trip complete'
}

function phaseTitle(phase: string): string {
  if (phase === 'ready') return 'Ready to ride?'
  if (phase === 'active') return 'Your trip is in progress'
  if (phase === 'no-charge') return 'You were not charged'
  return 'Review and pay'
}

function formatFareRange(min: number, max: number): string {
  return min === max ? formatCents(max) : `${formatCents(min)}–${formatCents(max)}`
}

function stopDisplayName(name: string | null, sequence: number, total: number): string {
  if (name?.trim()) return name
  if (sequence <= 0) return 'Boarding point'
  return `Route stop ${sequence} of ${total}`
}

function formatDistance(metres: number): string {
  return `${(metres / 1_609.344).toFixed(metres < 1_609 ? 2 : 1)} mi`
}

function formatTimer(seconds: number): string {
  const hours = Math.floor(seconds / 3_600)
  const minutes = Math.floor((seconds % 3_600) / 60)
  const rest = seconds % 60
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

function formatClock(value: string): string {
  return new Date(value).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

function stopStateLabel(state: TransitSession['stopFareProgress'][number]['state']): string {
  if (state === 'COMPLETED') return 'charged'
  if (state === 'CURRENT') return 'current · charged'
  if (state === 'NEXT') return 'next stop'
  if (state === 'SKIPPED') return 'skipped · no charge'
  if (state === 'DIVERTED') return 'diverted · no charge'
  return 'upcoming'
}
