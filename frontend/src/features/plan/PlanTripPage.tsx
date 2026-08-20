import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { journeysApi, profileApi, recommendationsApi } from '../../api'
import { ApiError } from '../../api/client'
import { BottomNav, TopBar } from '../../components/AppShell'
import type { JourneyOption, JourneySearchResponse, TravelProfile } from '../../api/types'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { AssistantPanel } from './AssistantPanel'
import { CommuteShortcut } from './CommuteShortcut'
import { PlannerCard } from './PlannerCard'
import { RouteDrawer } from './RouteDrawer'
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

  // Empty rather than a hardcoded pair: the rider's own commute fills these in
  // once their profile loads, and a deep link overrides both.
  const [origin, setOrigin] = useState(searchParams.get('from') ?? '')
  const [destination, setDestination] = useState(searchParams.get('to') ?? '')
  const [profile, setProfile] = useState(searchParams.get('profile') ?? 'BALANCED')

  const [result, setResult] = useState<JourneySearchResponse | null>(null)
  const [selectedJourneyId, setSelectedJourneyId] = useState<string | null>(null)
  const [hoveredJourneyId, setHoveredJourneyId] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [choosingJourneyId, setChoosingJourneyId] = useState<string | null>(null)

  const profiles = useAsync(() => recommendationsApi.profiles(), [])
  const travelProfile = useAsync<TravelProfile>(() => profileApi.get(), [])
  const commute = travelProfile.data?.hasTypicalCommute ? travelProfile.data : null

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
    void runSearchFor(from, to, profile)
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
      setSelectedJourneyId(response.options.find((option) => option.recommended)?.journeyId
        ?? response.options[0]?.journeyId
        ?? null)
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
    if (result) void runSearch(profile)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile])

  const selectedJourney = useMemo(
    () => result?.options.find((option) => option.journeyId === selectedJourneyId) ?? null,
    [result, selectedJourneyId],
  )

  /**
   * Takes the selected journey.
   *
   * <p>Only the journey id is sent — the server re-prices it and charges its own
   * figure. The idempotency key is generated once per attempt so a double click
   * returns the first trip instead of charging twice.
   */
  // One key per (journey, search) so a retry of the same click dedupes, while a
  // deliberate second trip on the same route does not.
  const idempotencyKeys = useRef(new Map<string, string>())
  const idempotencyKeyFor = (option: JourneyOption) => {
    const cacheKey = `${result?.origin.displayName}|${result?.destination.displayName}|${option.journeyId}`
    const existing = idempotencyKeys.current.get(cacheKey)
    if (existing) return existing
    const key = `${cacheKey}|${crypto.randomUUID()}`
    idempotencyKeys.current.set(cacheKey, key)
    return key
  }

  const chooseJourney = async (option: JourneyOption, confirmUnknownFare = false) => {
    if (!user || !result) return
    setChoosingJourneyId(option.journeyId)
    setError(null)
    try {
      await journeysApi.take(
        {
          from: result.origin.displayName,
          to: result.destination.displayName,
          journeyId: option.journeyId,
          confirmUnknownFare,
        },
        idempotencyKeyFor(option),
      )
      navigate('/trips')
    } catch (caught) {
      const apiError = toApiError(caught)
      // The server refuses to charge a journey it cannot price until the rider
      // explicitly accepts that. Ask, then retry with the confirmation.
      if (apiError.problem.code === 'FARE_CONFIRMATION_REQUIRED' && !confirmUnknownFare) {
        const accepted = window.confirm(
          `${apiError.message}\n\nRecord this trip with no charge?`)
        if (accepted) {
          setChoosingJourneyId(null)
          return chooseJourney(option, true)
        }
        setChoosingJourneyId(null)
        return
      }
      setError(apiError)
    } finally {
      setChoosingJourneyId(null)
    }
  }

  const hasResults = (result?.options.length ?? 0) > 0

  return (
    <div className="plan-shell">
      <TopBar compact />

      <div className={`plan-body${hasResults || searching || error ? ' has-drawer' : ''}`}>
        <RouteMap
          journeys={result?.options ?? []}
          selectedJourneyId={selectedJourneyId}
          highlightedJourneyId={hoveredJourneyId}
          onSelectJourney={setSelectedJourneyId}
          focus={commute ? [
            { lng: commute.typicalOrigin!.longitude, lat: commute.typicalOrigin!.latitude },
            { lng: commute.typicalDestination!.longitude, lat: commute.typicalDestination!.latitude },
          ] : null}
        />

        <div className="plan-overlay">
          <PlannerCard
            origin={origin}
            destination={destination}
            onOriginChange={setOrigin}
            onDestinationChange={setDestination}
            onSwap={() => { setOrigin(destination); setDestination(origin) }}
            onSubmit={() => void runSearch(profile)}
            searching={searching}
            profiles={profiles.data ?? []}
            selectedProfile={profile}
            onProfileChange={setProfile}
          >
            <AssistantPanel
              profiles={profiles.data ?? []}
              selectedProfile={profile}
              onSelectProfile={setProfile}
            />
          </PlannerCard>

          {/* One tap to the trip this rider actually takes, in either direction. */}
          {commute && !result && !searching && (
            <CommuteShortcut
              originName={commute.typicalOrigin!.name}
              destinationName={commute.typicalDestination!.name}
              onPlan={() => planCommute(false)}
              onPlanReturn={() => planCommute(true)}
            />
          )}

        </div>

        {/*
          The selected route's reasoning sits in the opposite corner from the
          planner. It used to stack under the planner, which put it on a collision
          course with the results drawer as soon as a search returned more than
          three options — the explanation ended up covering the option it was
          explaining.
        */}
        {selectedJourney && (
          <div className="plan-note-slot">
            <SelectionSummary option={selectedJourney} />
          </div>
        )}

        <div className="plan-drawer-slot">
          <RouteDrawer
            result={result}
            selectedJourneyId={selectedJourneyId}
            onSelectJourney={setSelectedJourneyId}
            onHoverJourney={setHoveredJourneyId}
            onChoose={chooseJourney}
            choosingJourneyId={choosingJourneyId}
            searching={searching}
            error={error}
            onRetry={() => void runSearch(profile)}
          />
        </div>
      </div>

      <BottomNav />
    </div>
  )
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
