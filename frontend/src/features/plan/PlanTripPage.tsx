import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { journeysApi, profileApi, recommendationsApi, transitApi, transitSessionsApi } from '../../api'
import { ApiError } from '../../api/client'
import { BottomNav, TopBar } from '../../components/AppShell'
import type {
  JourneyOption, JourneySearchResponse, LocationCandidate, PaymentIntent, PaymentRail,
  TransitSession, TransitStop, TravelProfile,
} from '../../api/types'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { useAssistant } from '../assistant/AssistantContext'
import { CommuteShortcut } from './CommuteShortcut'
import { PlannerCard } from './PlannerCard'
import { RouteDrawer } from './RouteDrawer'
import { TransitSessionSheet } from './TransitSessionSheet'
import { RouteMap } from './map/RouteMap'

/**
 * Map-first Plan Trip.
 *
 * The map owns the whole main area; the planner floats over its top-left corner and
 * results sit in a drawer along the bottom. Nothing stretches to full height, so the
 * map is never reduced to a strip beside a column.
 *
 * All financial decisions still come from the backend. This page renders a decision;
 * it never makes one.
 */
export function PlanTripPage() {
  const { user, loading: userLoading } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const assistant = useAssistant()

  // Empty rather than a hardcoded pair: the rider's own commute fills these in
  // once their profile loads, and a deep link overrides both.
  const [origin, setOrigin] = useState(searchParams.get('from') ?? '')
  const [destination, setDestination] = useState(searchParams.get('to') ?? '')
  const [originCandidate, setOriginCandidate] = useState<LocationCandidate | null>(null)
  const [destinationCandidate, setDestinationCandidate] = useState<LocationCandidate | null>(null)
  const [nearbyStops, setNearbyStops] = useState<TransitStop[]>([])
  const [profile, setProfile] = useState(searchParams.get('profile') ?? 'BALANCED')

  const [result, setResult] = useState<JourneySearchResponse | null>(null)
  const [selectedJourneyId, setSelectedJourneyId] = useState<string | null>(null)
  const [hoveredJourneyId, setHoveredJourneyId] = useState<string | null>(null)
  const [activeLegIndex, setActiveLegIndex] = useState<number | null>(null)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [choosingJourneyId, setChoosingJourneyId] = useState<string | null>(null)
  const [tripCandidate, setTripCandidate] = useState<JourneyOption | null>(null)
  const [session, setSession] = useState<TransitSession | null>(null)
  const [payment, setPayment] = useState<PaymentIntent | null>(null)
  const [paymentError, setPaymentError] = useState<string | null>(null)
  const applyingAssistantResult = useRef(false)

  const profiles = useAsync(() => recommendationsApi.profiles(), [])
  const travelProfile = useAsync<TravelProfile>(() => profileApi.get(), [])
  const commute = travelProfile.data?.hasTypicalCommute ? travelProfile.data : null

  // Resume an unfinished trip after refresh. The endpoint returns only real
  // server state; a paid or no-charge session is no longer considered open.
  useEffect(() => {
    if (!user) return
    void transitSessionsApi.active().then((active) => {
      if (!active) return
      setSession(active)
      setActiveLegIndex(active.activeLegIndex)
    }).catch(() => {
      // Planning still works if restoring a previous session fails. A later
      // explicit action will surface its own error instead of blocking the map.
    })
  }, [user])

  // Pre-fill from the saved commute, once, and only into fields the rider has not
  // touched. Deliberately does not search: framing the map and filling a form are
  // free, while running a routing request nobody asked for is not.
  const [prefilled, setPrefilled] = useState(false)
  useEffect(() => {
    if (prefilled || !travelProfile.data) return
    setPrefilled(true)

    // Start on the rider's saved stance unless this trip named one. Without this
    // the chips would say "Balanced" while the rider's default is "Save money",
    // and — because the page always sends the selected stance — the client would
    // quietly override the very preference onboarding collected.
    if (!searchParams.has('profile')) {
      setProfile(travelProfile.data.defaultContextProfile)
    }

    if (!commute) return
    if (searchParams.has('from') || searchParams.has('to')) return
    if (origin.trim() === '') setOrigin(commute.typicalOrigin!.name)
    if (destination.trim() === '') setDestination(commute.typicalDestination!.name)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [travelProfile.data, commute, prefilled])

  /** Plans the saved commute in the given direction, on an explicit tap. */
  const planCommute = (reverse: boolean) => {
    if (!commute) return
    const from = reverse ? commute.typicalDestination!.name : commute.typicalOrigin!.name
    const to = reverse ? commute.typicalOrigin!.name : commute.typicalDestination!.name
    setOrigin(from)
    setDestination(to)
    setOriginCandidate(null)
    setDestinationCandidate(null)
    void runSearchFor(from, to, profile)
  }

  const changeOrigin = (value: string) => {
    setOrigin(value)
    setOriginCandidate(null)
    setResult(null)
    setSelectedJourneyId(null)
  }

  const changeDestination = (value: string) => {
    setDestination(value)
    setDestinationCandidate(null)
    setResult(null)
    setSelectedJourneyId(null)
  }

  const runSearch = (profileId: string) => runSearchFor(origin, destination, profileId)

  const runSearchFor = async (from: string, to: string, profileId: string) => {
    if (!from.trim() || !to.trim()) return
    setSearching(true)
    setError(null)
    try {
      // Arbitrary origin and destination: neither has to be a seeded pair.
      const response = await journeysApi.search(from.trim(), to.trim(), profileId)
      setResult(response)
      setOriginCandidate(response.origin)
      setDestinationCandidate(response.destination)
      setSelectedJourneyId(response.options.find((option) => option.recommended)?.journeyId
        ?? response.options[0]?.journeyId
        ?? null)
      setActiveLegIndex(null)
    } catch (caught) {
      setError(toApiError(caught))
      setResult(null)
      setSelectedJourneyId(null)
    } finally {
      setSearching(false)
    }
  }

  // Deep links run on load, but only once the user has resolved -- otherwise the
  // request would go out without a userId and silently skip budget weighting.
  const [ranInitialSearch, setRanInitialSearch] = useState(false)
  useEffect(() => {
    if (userLoading || ranInitialSearch) return
    if (searchParams.has('from') || searchParams.has('to')) {
      setRanInitialSearch(true)
      void runSearch(profile)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userLoading, ranInitialSearch])

  // Switching stance re-scores immediately -- that immediacy is the whole point.
  useEffect(() => {
    if (applyingAssistantResult.current) {
      applyingAssistantResult.current = false
    } else if (result) {
      void runSearch(profile)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile])

  const selectedJourney = useMemo(
    () => result?.options.find((option) => option.journeyId === selectedJourneyId) ?? null,
    [result, selectedJourneyId],
  )

  const applyAssistantRoutes = (routes: JourneySearchResponse) => {
    setOrigin(routes.origin.displayName)
    setDestination(routes.destination.displayName)
    setOriginCandidate(routes.origin)
    setDestinationCandidate(routes.destination)
    if (routes.profile.id !== profile) applyingAssistantResult.current = true
    setProfile(routes.profile.id)
    setResult(routes)
    setError(null)
    setSelectedJourneyId(routes.options.find((option) => option.recommended)?.journeyId
      ?? routes.options[0]?.journeyId
      ?? null)
    setActiveLegIndex(null)
  }

  // Give the persistent assistant only navigation context. Route facts are
  // always regenerated server-side before Gemini sees or explains them.
  useEffect(() => {
    assistant.setActiveRouteContext({
      origin: result?.origin.displayName ?? origin,
      destination: result?.destination.displayName ?? destination,
      profile,
      selectedJourneyId,
    })
    return () => assistant.setActiveRouteContext(null)
  }, [
    origin, destination, profile, selectedJourneyId, result?.origin.displayName,
    result?.destination.displayName, assistant.setActiveRouteContext,
  ])

  // A planning action requested in the conversation drives the exact same map,
  // cards, and selected-route state as a manual search.
  const appliedAssistantRevision = useRef(0)
  useEffect(() => {
    if (!assistant.latestRoutes || assistant.routeRevision <= appliedAssistantRevision.current) return
    appliedAssistantRevision.current = assistant.routeRevision
    applyAssistantRoutes(assistant.latestRoutes)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assistant.latestRoutes, assistant.routeRevision])

  // One key per lifecycle action: network retries replay the same server record,
  // while a later trip on the same route gets a new key after local state clears.
  const idempotencyKeys = useRef(new Map<string, string>())
  const idempotencyKeyFor = (cacheKey: string) => {
    const existing = idempotencyKeys.current.get(cacheKey)
    if (existing) return existing
    const key = `${cacheKey}|${crypto.randomUUID()}`
    idempotencyKeys.current.set(cacheKey, key)
    return key
  }

  const chooseJourney = (option: JourneyOption) => {
    if (!user || !result) return
    setPayment(null)
    setPaymentError(null)
    setSelectedJourneyId(option.journeyId)
    setTripCandidate(option)
  }

  const startTrip = async () => {
    if (!user || !result || !tripCandidate) return
    setChoosingJourneyId(tripCandidate.journeyId)
    setPaymentError(null)
    try {
      const started = await transitSessionsApi.start(
        {
          from: result.origin.displayName,
          to: result.destination.displayName,
          journeyId: tripCandidate.journeyId,
          profile,
        },
        idempotencyKeyFor(`session:${result.origin.displayName}|${result.destination.displayName}|${tripCandidate.journeyId}`),
      )
      setSession(started)
      setActiveLegIndex(started.activeLegIndex)
    } catch (caught) {
      setPaymentError(toApiError(caught).message)
    } finally {
      setChoosingJourneyId(null)
    }
  }

  const updateSession = async (
    action: 'advance' | 'end',
    outcome: 'REACHED' | 'SKIPPED' | 'DIVERTED' = 'REACHED',
  ) => {
    if (!session) return
    setChoosingJourneyId(tripCandidate?.journeyId ?? session.id)
    setPaymentError(null)
    try {
      const updated = action === 'advance'
        ? await transitSessionsApi.advance(session.id, outcome)
        : await transitSessionsApi.end(session.id)
      setSession(updated)
      setActiveLegIndex(updated.activeLegIndex)
    } catch (caught) {
      setPaymentError(toApiError(caught).message)
    } finally {
      setChoosingJourneyId(null)
    }
  }

  const payForSession = async (method: PaymentRail) => {
    if (!session) return
    setChoosingJourneyId(tripCandidate?.journeyId ?? session.id)
    setPaymentError(null)
    try {
      const paid = await transitSessionsApi.pay(
        session.id, method, idempotencyKeyFor(`session-payment:${session.id}|${method}`))
      setPayment(paid)
      if (paid.status === 'SETTLED' && paid.trip) {
        navigate(`/trips?payment=${paid.id}`)
      }
    } catch (caught) {
      setPaymentError(toApiError(caught).message)
    } finally {
      setChoosingJourneyId(null)
    }
  }

  const mapJourneys = useMemo(() => {
    if (result?.options.length) return result.options
    return session ? [sessionAsJourney(session)] : []
  }, [result, session])
  const mapSelection = result?.options.length
    ? selectedJourneyId
    : session ? `session:${session.id}` : null
  const hasResults = !session && (result?.options.length ?? 0) > 0
  const selectedPlaces = useMemo(() =>
    [originCandidate, destinationCandidate].filter(
      (candidate): candidate is LocationCandidate => candidate !== null),
  [originCandidate, destinationCandidate])

  // A selected nationwide geocoder result moves the map immediately. If the
  // location is within an imported feed, add real GTFS stops around it; an empty
  // response stays empty rather than inventing station markers.
  useEffect(() => {
    if (result || session || selectedPlaces.length === 0) {
      setNearbyStops([])
      return
    }
    let cancelled = false
    Promise.all(selectedPlaces.map((place) =>
      transitApi.nearbyStops(place.latitude, place.longitude).catch(() => [])))
      .then((groups) => {
        if (cancelled) return
        const unique = new Map<string, TransitStop>()
        groups.flat().forEach((stop) => unique.set(stop.id, stop))
        setNearbyStops([...unique.values()])
      })
    return () => { cancelled = true }
  }, [result, session, selectedPlaces])

  const mapFocus = selectedPlaces.length > 0
    ? selectedPlaces.map((place) => ({ lng: place.longitude, lat: place.latitude }))
    : commute ? [
      { lng: commute.typicalOrigin!.longitude, lat: commute.typicalOrigin!.latitude },
      { lng: commute.typicalDestination!.longitude, lat: commute.typicalDestination!.latitude },
    ] : null

  return (
    <div className="plan-shell">
      <TopBar compact />

      <div className={`plan-body${hasResults || searching || error ? ' has-drawer' : ''}`}>
        <RouteMap
          journeys={mapJourneys}
          selectedJourneyId={mapSelection}
          highlightedJourneyId={hoveredJourneyId}
          activeLegIndex={session?.activeLegIndex ?? activeLegIndex}
          activeStopSequence={session?.progressUnitsCompleted ?? null}
          activeStopName={session?.currentStop ?? null}
          onSelectJourney={(journeyId) => {
            setSelectedJourneyId(journeyId)
            setActiveLegIndex(null)
          }}
          onSelectLeg={(journeyId, index) => {
            setSelectedJourneyId(journeyId)
            setActiveLegIndex(index)
          }}
          focus={mapFocus}
          focusLocations={selectedPlaces}
          nearbyStops={nearbyStops}
        />

        {!session && <div className="plan-overlay">
          <PlannerCard
            origin={origin}
            destination={destination}
            onOriginChange={changeOrigin}
            onDestinationChange={changeDestination}
            onOriginSelect={setOriginCandidate}
            onDestinationSelect={setDestinationCandidate}
            onSwap={() => {
              setOrigin(destination)
              setDestination(origin)
              setOriginCandidate(destinationCandidate)
              setDestinationCandidate(originCandidate)
            }}
            onSubmit={() => void runSearch(profile)}
            searching={searching}
            profiles={profiles.data ?? []}
            selectedProfile={profile}
            onProfileChange={setProfile}
          />

          {/* One tap to the trip this rider actually takes, in either direction. */}
          {commute && !result && !searching && (
            <CommuteShortcut
              originName={commute.typicalOrigin!.name}
              destinationName={commute.typicalDestination!.name}
              onPlan={() => planCommute(false)}
              onPlanReturn={() => planCommute(true)}
            />
          )}

        </div>}

        {/*
          The selected route's reasoning sits in the opposite corner from the
          planner. It used to stack under the planner, which put it on a collision
          course with the results drawer as soon as a search returned more than
          three options — the explanation ended up covering the option it was
          explaining.
        */}
        {selectedJourney && !session && (
          <div className="plan-note-slot">
            <SelectionSummary option={selectedJourney} />
          </div>
        )}

        {!session && <div className="plan-drawer-slot">
          <RouteDrawer
            result={result}
            selectedJourneyId={selectedJourneyId}
            onSelectJourney={(journeyId) => {
              setSelectedJourneyId(journeyId)
              setActiveLegIndex(null)
            }}
            onHoverJourney={setHoveredJourneyId}
            onChoose={chooseJourney}
            choosingJourneyId={choosingJourneyId}
            searching={searching}
            error={error}
            onRetry={() => void runSearch(profile)}
            activeLegIndex={activeLegIndex}
            onSelectLeg={setActiveLegIndex}
          />
        </div>}

        {(tripCandidate || session) && (
          <TransitSessionSheet
            option={tripCandidate}
            result={result}
            session={session}
            payment={payment}
            processing={choosingJourneyId !== null}
            error={paymentError}
            onClose={() => {
              if (choosingJourneyId === null) {
                setTripCandidate(null)
                if (session?.status === 'NO_CHARGE' || session?.status === 'PAID') {
                  setSession(null)
                }
                setPayment(null)
                setPaymentError(null)
              }
            }}
            onStart={() => void startTrip()}
            onAdvance={(outcome) => void updateSession('advance', outcome)}
            onEnd={() => void updateSession('end')}
            onPay={(method) => void payForSession(method)}
          />
        )}
      </div>

      <BottomNav />
    </div>
  )
}

function sessionAsJourney(session: TransitSession): JourneyOption {
  return {
    journeyId: `session:${session.id}`,
    summary: session.summary,
    totalMinutes: session.legs.reduce((sum, leg) => sum + leg.durationMinutes + leg.waitMinutes, 0),
    walkingMinutes: session.legs.filter((leg) => leg.mode === 'WALK')
      .reduce((sum, leg) => sum + leg.durationMinutes, 0),
    transfers: Math.max(0, session.legs.filter((leg) => leg.mode !== 'WALK').length - 1),
    fareCents: null,
    fareStatus: 'UNKNOWN',
    fareSource: 'FAREFLOW_USAGE_SIMULATION',
    fareBreakdown: session.fareBreakdown,
    labels: [],
    recommended: true,
    score: 0,
    explanation: 'Active FareFlow transit session',
    dataSource: session.dataSource,
    usageFareMinCents: session.estimatedFareMinCents,
    usageFareMaxCents: session.estimatedFareMaxCents,
    usagePricingVersion: session.pricingVersion,
    legs: session.legs,
  }
}

/**
 * One-line explanation for the selected route, over the map rather than inside the
 * card — the cards stay scannable, and the reasoning is still one glance away.
 */
function SelectionSummary({ option }: { option: JourneyOption }) {
  return (
    <div className="selection-note" data-testid="route-detail">
      <span className="selection-note-provider">{option.summary}</span>
      <span className="selection-note-text">{option.explanation}</span>
      <span className="selection-note-foot">
        Lines connect published station coordinates and are indicative, not surveyed track geometry.
      </span>
    </div>
  )
}

function toApiError(caught: unknown): ApiError {
  return caught instanceof ApiError ? caught : new ApiError(0, { title: String(caught) })
}
