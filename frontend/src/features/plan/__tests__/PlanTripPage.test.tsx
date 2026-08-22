import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PlanTripPage } from '../PlanTripPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import {
  amtrakJourney,
  demoConfig,
  emptyJourneySearch,
  journeySearch,
  completedTransitSession,
  noChargeTransitSession,
  profiles,
  progressedTransitSession,
  rushJourneySearch,
  emptyTravelProfile,
  septaNjtJourney,
  settledSessionPayment,
  startedTransitSession,
  travelProfile,
  user,
} from '../../../test/fixtures'

// Map availability is stubbed explicitly so the suite does not depend on whether a
// TomTom key happens to exist in the local .env.
vi.mock('../map/tomtom', async () => {
  const actual = await vi.importActual<typeof import('../map/tomtom')>('../map/tomtom')
  return { ...actual, isMapAvailable: () => mapAvailable }
})
let mapAvailable = false

const navigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => navigate }
})

function stubApis() {
  vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
  vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
  vi.spyOn(api.recommendationsApi, 'profiles').mockResolvedValue(profiles)
  vi.spyOn(api.locationsApi, 'search').mockResolvedValue([])
  vi.spyOn(api.transitApi, 'nearbyStops').mockResolvedValue([])
  // Origin and destination are no longer hardcoded: they are pre-filled from the
  // signed-in rider's saved commute, which is Newark to Manhattan here.
  vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
  vi.spyOn(api.transitSessionsApi, 'active').mockResolvedValue(null)
  vi.spyOn(api.transitSessionsApi, 'start').mockResolvedValue(startedTransitSession)
  vi.spyOn(api.transitSessionsApi, 'advance').mockResolvedValue(progressedTransitSession)
  vi.spyOn(api.transitSessionsApi, 'end').mockResolvedValue(completedTransitSession)
  vi.spyOn(api.transitSessionsApi, 'pay').mockResolvedValue(settledSessionPayment)
}

async function search() {
  await userEvent.click(await screen.findByRole('button', { name: /find routes/i }))
}

describe('PlanTripPage — journey search', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('searches arbitrary origins and destinations, not seeded pairs', async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    const from = await screen.findByLabelText('From')
    await userEvent.clear(from)
    await userEvent.type(from, 'Philadelphia')
    await search()

    await waitFor(() =>
      expect(call).toHaveBeenCalledWith('Philadelphia', 'Manhattan', 'BALANCED'))
  })

  it('lists journeys with duration, fare, and labels', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const cheapest = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    // The operator names the card and also appears in its one-line reason, so
    // this asserts the title specifically rather than either occurrence.
    expect(cheapest.querySelector('.route-tile-provider'))
      .toHaveTextContent('SEPTA Trenton Line')
    expect(within(cheapest).getByText('$27.60')).toBeInTheDocument()
    expect(within(cheapest).getByText('2 hr 57 min')).toBeInTheDocument()
    expect(within(cheapest).getByText('Best value')).toBeInTheDocument()
    expect(within(cheapest).getByText(/1 transfer/)).toBeInTheDocument()
  })

  it('marks an estimated fare as estimated rather than exact', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const cheapest = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    expect(within(cheapest).getByText('est')).toBeInTheDocument()
  })

  it('shows an unpriceable fare as unavailable, never as $0.00', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const amtrak = await screen.findByTestId(`journey-card-${amtrakJourney.journeyId}`)
    expect(within(amtrak).getByText(/fare varies/i)).toBeInTheDocument()
    expect(within(amtrak).queryByText('$0.00')).not.toBeInTheDocument()
  })

  it('surfaces the honest notices rather than hiding them', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    expect(await screen.findByText(/not live departures/i)).toBeInTheDocument()
    expect(screen.getByText(/without a fare rather than with a guess/i)).toBeInTheDocument()
  })

  it('selects the recommended journey by default', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const cheapest = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    expect(cheapest).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('route-detail')).toHaveTextContent('SEPTA Trenton Line')
  })

  it('dims unselected journeys so the selection is unmistakable', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const cheapest = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    expect(cheapest.className).toContain('selected')
    expect(screen.getByTestId(`journey-card-${amtrakJourney.journeyId}`).className).toContain('dimmed')
  })

  it('switches selection and detail when another journey is clicked', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    await userEvent.click(await screen.findByTestId(`journey-card-${amtrakJourney.journeyId}`))

    expect(screen.getByTestId(`journey-card-${amtrakJourney.journeyId}`))
      .toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('route-detail')).toHaveTextContent('Amtrak')
  })

  it('lets Ask FareFlow update the same map and route comparison results', async () => {
    vi.spyOn(api.assistantApi, 'config').mockResolvedValue({
      available: true,
      unavailableReason: null,
      starters: ['Find my cheapest commute'],
    })
    const ask = vi.spyOn(api.assistantApi, 'ask').mockResolvedValue({
      reply: 'The SEPTA and NJ Transit option is the best fit for this request.',
      toolsUsed: ['get_travel_profile', 'plan_journey'],
      routes: journeySearch,
      trips: [],
      followUps: ['Why did you recommend that one?'],
    })
    renderWithProviders(<PlanTripPage />, { route: '/plan' })

    await userEvent.click(await screen.findByRole('button', { name: /ask fareflow/i }))
    const input = await screen.findByLabelText('Ask FareFlow')
    await userEvent.type(input, "What's the cheapest way to class?")
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => expect(ask).toHaveBeenCalledWith(
      "What's the cheapest way to class?", [], expect.objectContaining({
        pagePath: '/plan',
        pageName: 'Plan',
      })))
    expect(await screen.findByText(/best fit for this request/i)).toBeInTheDocument()
    expect(screen.getByTestId(`journey-card-${septaNjtJourney.journeyId}`)).toBeInTheDocument()
    expect(screen.getByTestId('route-drawer')).toHaveTextContent('Philadelphia, PA → Manhattan, NY')
  })
})

describe('PlanTripPage — multi-leg journeys', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('reveals the leg timeline on demand rather than crowding the card', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    // Concise by default.
    expect(within(card).queryByText(/Trenton Transit Center/)).not.toBeInTheDocument()

    await userEvent.click(within(card).getByRole('button', { name: /view details/i }))

    // The line name now appears twice on purpose: the card title and the timeline.
    expect(within(card).getAllByText(/SEPTA Trenton Line/).length).toBeGreaterThan(1)
    expect(within(card).getByText(/Walk to Suburban Station/)).toBeInTheDocument()
    expect(within(card).getAllByText(/Trenton Transit Center/).length).toBeGreaterThan(0)
  })

  it('shows the fare breakdown for a priced journey', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /view details/i }))

    expect(within(card).getByText(/SEPTA Regional Rail \(Zone 4/)).toBeInTheDocument()
    expect(within(card).getByText(/NJ Transit rail \(Trenton/)).toBeInTheDocument()
  })

  it('shows Google schedule provenance and does not render polyline points as stops', async () => {
    const googleJourney = {
      ...septaNjtJourney,
      journeyId: 'GOOGLE:route-1',
      summary: '62 toward Newark Penn Station',
      totalMinutes: 18,
      walkingMinutes: 0,
      transfers: 0,
      dataSource: 'GOOGLE_ROUTES',
      fareCents: 250,
      fareSource: 'PROVIDER',
      fareBreakdown: ['Google Maps estimated transit fare  $2.50'],
      legs: [{
        ...septaNjtJourney.legs[1],
        mode: 'BUS' as const,
        agency: 'NJ TRANSIT',
        lineName: '62 toward Newark Penn Station',
        fromName: 'NJIT',
        toName: 'Newark Penn Station',
        departureTime: '2026-08-22T12:07:00Z',
        arrivalTime: '2026-08-22T12:22:00Z',
        stopCount: 5,
        waypoints: [
          { name: 'NJIT', latitude: 40.741, longitude: -74.176 },
          { name: '', latitude: 40.738, longitude: -74.170 },
          { name: 'Newark Penn Station', latitude: 40.7347, longitude: -74.1642 },
        ],
      }],
    }
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue({
      ...journeySearch,
      options: [googleJourney],
      notices: ['Routes and times come from Google Maps.'],
    })
    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId('journey-card-GOOGLE:route-1')
    await userEvent.click(within(card).getByRole('button', { name: /view details/i }))

    expect(within(card).getByText(/Google-provided transit times and geometry/i))
      .toBeInTheDocument()
    expect(within(card).getAllByText('62 toward Newark Penn Station')).toHaveLength(2)
    expect(within(card).queryByText(/^Via /)).not.toBeInTheDocument()
    expect(within(card).getByText(/5 stops/)).toBeInTheDocument()
  })

  it('reports walking time as part of the journey', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    expect(within(card).getByText(/15 min walk/)).toBeInTheDocument()
  })
})

describe('PlanTripPage — context profiles', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('switching to Rush re-scores and explains the change', async () => {
    const call = vi.spyOn(api.journeysApi, 'search')
      .mockResolvedValueOnce(journeySearch)
      .mockResolvedValueOnce(rushJourneySearch)

    renderWithProviders(<PlanTripPage />)
    await search()
    await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)

    await userEvent.click(screen.getByRole('button', { name: 'Fastest' }))

    await waitFor(() =>
      expect(call).toHaveBeenLastCalledWith('Newark', 'Manhattan', 'RUSH'))
    expect(await screen.findByTestId('context-note')).toHaveTextContent(/in a rush/i)
  })

  it('marks the active stance as pressed', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    const balanced = await screen.findByRole('button', { name: 'Balanced' })
    expect(balanced).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Cheapest' }))
    expect(screen.getByRole('button', { name: 'Cheapest' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('shows no context note when the stance changed nothing', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    expect(screen.queryByTestId('context-note')).not.toBeInTheDocument()
  })
})

describe('PlanTripPage — states and map', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('shows an empty state when no journeys exist', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(emptyJourneySearch)
    renderWithProviders(<PlanTripPage />)
    await search()

    // Appears as both the drawer heading and the notice text.
    expect(await screen.findAllByText(/no journeys found/i)).not.toHaveLength(0)
  })

  it('shows an error state and can retry', async () => {
    const call = vi.spyOn(api.journeysApi, 'search')
      .mockRejectedValueOnce(new ApiError(500, { title: 'Internal server error', detail: 'Broke' }))
      .mockResolvedValueOnce(journeySearch)

    renderWithProviders(<PlanTripPage />)
    await search()

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal server error')
    await userEvent.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => expect(call).toHaveBeenCalledTimes(2))
  })

  it('surfaces a 404 when a place cannot be resolved', async () => {
    vi.spyOn(api.journeysApi, 'search').mockRejectedValue(
      new ApiError(404, { title: 'Resource not found', detail: "Could not find a place matching 'Atlantis'" }))

    renderWithProviders(<PlanTripPage />)
    await search()

    expect(await screen.findByRole('alert')).toHaveTextContent(/Could not find a place/)
  })

  it('renders the schematic map when no TomTom key is configured', async () => {
    mapAvailable = false
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    expect(await screen.findByTestId('schematic-map')).toBeInTheDocument()
  })

  it('mounts the TomTom map when a key is configured', async () => {
    mapAvailable = true
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    expect(await screen.findByTestId('tomtom-map')).toBeInTheDocument()
    expect(screen.queryByTestId('schematic-map')).not.toBeInTheDocument()
    mapAvailable = false
  })

  it('runs the search immediately from a deep link', async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />,
      { route: '/plan?from=Philadelphia&to=Manhattan&profile=RUSH' })

    await waitFor(() =>
      expect(call).toHaveBeenCalledWith('Philadelphia', 'Manhattan', 'RUSH'))
  })

  it('keeps navigation to the other sections available', async () => {
    renderWithProviders(<PlanTripPage />)
    expect(await screen.findAllByRole('link', { name: /wallet/i })).not.toHaveLength(0)
    expect(screen.getAllByRole('link', { name: /insights/i })).not.toHaveLength(0)
  })
})

describe('PlanTripPage — taking a journey', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('runs select, start, track, end, and server-priced payment without sending a fare', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const start = vi.spyOn(api.transitSessionsApi, 'start').mockResolvedValue(startedTransitSession)
    const advance = vi.spyOn(api.transitSessionsApi, 'advance').mockResolvedValue(progressedTransitSession)
    const end = vi.spyOn(api.transitSessionsApi, 'end').mockResolvedValue(completedTransitSession)
    const pay = vi.spyOn(api.transitSessionsApi, 'pay').mockResolvedValue(settledSessionPayment)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    expect(await screen.findByText(/FareFlow’s proposed usage-pricing simulation/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^start trip$/i }))
    await screen.findByText(/session active/i)
    expect(screen.getAllByText(/trip duration/i)).not.toHaveLength(0)
    expect(screen.getByRole('region', { name: /current fare/i }))
      .toHaveTextContent('$0.00')
    const stopFares = screen.getByRole('region', { name: /fare by stop/i })
    expect(stopFares).toHaveTextContent('Trenton Transit Center')
    expect(stopFares).toHaveTextContent('+$1.30')
    expect(screen.getByText(/\+\$1\.30 when reached/i)).toBeInTheDocument()
    expect(screen.getByText(/waiting time and delays never increase/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /complete next stop/i }))
    await waitFor(() => expect(advance).toHaveBeenCalledWith(startedTransitSession.id))
    expect(screen.getByRole('region', { name: /current fare/i }))
      .toHaveTextContent('$1.30')
    expect(screen.getByText(/\+\$3\.45 when reached/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /end trip/i }))
    await screen.findByRole('heading', { name: /review and pay/i })
    await userEvent.click(screen.getByRole('button', { name: /pay \$1\.30 with FareFlow/i }))

    await waitFor(() => expect(start).toHaveBeenCalled())
    const [body] = start.mock.calls[0]
    expect(body).toEqual({
      from: 'Philadelphia, PA',
      to: 'Manhattan, NY',
      journeyId: septaNjtJourney.journeyId,
      profile: 'BALANCED',
    })
    // The amount is the server's business; the client must not be able to state one.
    expect(JSON.stringify(body)).not.toMatch(/fareCents|amountCents/)
    expect(end).toHaveBeenCalledWith(startedTransitSession.id)
    expect(pay).toHaveBeenCalledWith(
      startedTransitSession.id, 'FAREFLOW_WALLET', expect.any(String))
    expect(navigate).toHaveBeenCalledWith(`/trips?payment=${settledSessionPayment.id}`)
  })

  it('sends an idempotency key when starting a session', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const start = vi.spyOn(api.transitSessionsApi, 'start').mockResolvedValue(startedTransitSession)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    await userEvent.click(await screen.findByRole('button', { name: /^start trip$/i }))
    await waitFor(() => expect(start).toHaveBeenCalledTimes(1))
    expect(start.mock.calls[0][1]).toBeTruthy()
  })

  it('can start usage tracking even when the agency published fare is unavailable', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const start = vi.spyOn(api.transitSessionsApi, 'start').mockResolvedValue(startedTransitSession)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${amtrakJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    expect((await screen.findAllByText('$1.30–$3.10')).length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: /^start trip$/i }))
    await waitFor(() => expect(start).toHaveBeenCalledTimes(1))
    expect(start.mock.calls[0][0].journeyId).toBe(amtrakJourney.journeyId)
  })

  it('ends an unboarded session with no payment or charge', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const end = vi.spyOn(api.transitSessionsApi, 'end').mockResolvedValue(noChargeTransitSession)
    const pay = vi.spyOn(api.transitSessionsApi, 'pay')

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    await userEvent.click(await screen.findByRole('button', { name: /^start trip$/i }))
    await screen.findByText(/session active/i)
    await userEvent.click(screen.getByRole('button', { name: /end trip/i }))
    expect(await screen.findByText(/no fare charged/i)).toBeInTheDocument()
    expect(end).toHaveBeenCalledTimes(1)
    expect(pay).not.toHaveBeenCalled()
  })

  it('surfaces a server error rather than navigating away', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    vi.spyOn(api.transitSessionsApi, 'start').mockRejectedValue(
      new ApiError(404, { title: 'Resource not found', detail: 'That journey is no longer available.' }))

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    await userEvent.click(await screen.findByRole('button', { name: /^start trip$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer available/)
    expect(navigate).not.toHaveBeenCalled()
  })
})

describe('PlanTripPage — location autocomplete', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('offers geocoded suggestions as the user types', async () => {
    const search = vi.spyOn(api.locationsApi, 'search').mockResolvedValue([
      { providerPlaceId: 'p1', displayName: 'Philadelphia, PA', locality: 'Philadelphia',
        region: 'PA', country: 'US', latitude: 39.95, longitude: -75.16,
        type: 'Geography', source: 'TOMTOM' },
    ])

    renderWithProviders(<PlanTripPage />)
    const from = await screen.findByLabelText('From')
    await userEvent.clear(from)
    await userEvent.type(from, 'Phil')

    await waitFor(() => expect(search).toHaveBeenCalled(), { timeout: 2000 })
    expect(await screen.findByRole('option', { name: /Philadelphia/ })).toBeInTheDocument()
  })

  it('fills the field when a suggestion is chosen', async () => {
    vi.spyOn(api.locationsApi, 'search').mockResolvedValue([
      { providerPlaceId: 'p1', displayName: 'Philadelphia, PA', locality: 'Philadelphia',
        region: 'PA', country: 'US', latitude: 39.95, longitude: -75.16,
        type: 'Geography', source: 'TOMTOM' },
    ])

    renderWithProviders(<PlanTripPage />)
    const from = await screen.findByLabelText('From')
    await userEvent.clear(from)
    await userEvent.type(from, 'Phil')

    await userEvent.click(await screen.findByRole('option', { name: /Philadelphia/ }))
    expect(screen.getByLabelText('From')).toHaveValue('Philadelphia, PA')
  })

  it('loads real nearby GTFS stops and focuses the map after choosing any place', async () => {
    vi.spyOn(api.locationsApi, 'search').mockResolvedValue([
      { providerPlaceId: 'denver-union', displayName: 'Denver Union Station',
        locality: 'Denver', region: 'CO', country: 'US', latitude: 39.7527,
        longitude: -105.0002, type: 'POI', source: 'TOMTOM' },
    ])
    const nearby = vi.spyOn(api.transitApi, 'nearbyStops').mockResolvedValue([
      { id: 'gtfs:rtd:union', name: 'Union Station', regionCode: 'DEN',
        regionName: 'Denver', publisherName: 'Regional Transportation District',
        latitude: 39.7527, longitude: -105.0002, modes: ['RAIL', 'BUS'],
        operators: ['Regional Transportation District'], lines: ['A', 'B'],
        distanceMetres: 0, realtimeAvailable: false, source: 'GTFS' },
    ])

    renderWithProviders(<PlanTripPage />)
    const from = await screen.findByLabelText('From')
    await userEvent.clear(from)
    await userEvent.type(from, 'Denver Union')
    await userEvent.click(await screen.findByRole('option', { name: /Denver Union Station/ }))

    await waitFor(() => expect(nearby).toHaveBeenCalledWith(39.7527, -105.0002))
    expect(await screen.findByText('Denver Union Station')).toBeInTheDocument()
  })

  it('does not search for a single character', async () => {
    const search = vi.spyOn(api.locationsApi, 'search').mockResolvedValue([])
    renderWithProviders(<PlanTripPage />)

    const from = await screen.findByLabelText('From')
    await userEvent.clear(from)
    await userEvent.type(from, 'P')

    await new Promise((resolve) => setTimeout(resolve, 400))
    // The prefilled destination legitimately searches; a one-character query must not.
    expect(search.mock.calls.map((call) => call[0])).not.toContain('P')
  })
})

describe('PlanTripPage — the saved commute', () => {
  beforeEach(() => { navigate.mockReset(); mapAvailable = false; stubApis() })

  it('pre-fills the planner from the rider\'s own commute instead of a hardcoded pair', async () => {
    renderWithProviders(<PlanTripPage />)

    await waitFor(() => expect(screen.getByLabelText('From')).toHaveValue('Newark'))
    expect(screen.getByLabelText('To')).toHaveValue('Manhattan')
  })

  it('does not search on load — filling a form is free, routing is not', async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    await waitFor(() => expect(screen.getByLabelText('From')).toHaveValue('Newark'))
    expect(call).not.toHaveBeenCalled()
  })

  it('offers the commute as a one-tap shortcut', async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    const shortcut = await screen.findByText(/your usual commute/i)
    expect(shortcut).toBeInTheDocument()
    expect(screen.getByText(/Newark.*Manhattan/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /plan commute/i }))

    await waitFor(() =>
      expect(call).toHaveBeenCalledWith('Newark', 'Manhattan', 'BALANCED'))
  })

  it('plans the return leg in the other direction', async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    await userEvent.click(await screen.findByRole('button', { name: /return trip/i }))

    await waitFor(() =>
      expect(call).toHaveBeenCalledWith('Manhattan', 'Newark', 'BALANCED'))
    expect(screen.getByLabelText('From')).toHaveValue('Manhattan')
  })

  it('hides the shortcut once results are on screen', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />)

    await screen.findByText(/your usual commute/i)
    await userEvent.click(screen.getByRole('button', { name: /plan commute/i }))

    await waitFor(() =>
      expect(screen.queryByText(/your usual commute/i)).not.toBeInTheDocument())
  })

  it('shows no shortcut for a rider who saved no commute', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(emptyTravelProfile)
    renderWithProviders(<PlanTripPage />)

    await screen.findByRole('button', { name: /find routes/i })
    expect(screen.queryByText(/your usual commute/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText('From')).toHaveValue('')
  })

  it("starts on the rider's saved stance rather than overriding it with BALANCED", async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    vi.spyOn(api.profileApi, 'get').mockResolvedValue({
      ...travelProfile, defaultContextProfile: 'SAVE_MONEY',
    })
    renderWithProviders(<PlanTripPage />)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Cheapest' }))
      .toHaveAttribute('aria-pressed', 'true'))

    await userEvent.click(screen.getByRole('button', { name: /plan commute/i }))
    await waitFor(() =>
      expect(call).toHaveBeenCalledWith('Newark', 'Manhattan', 'SAVE_MONEY'))
  })

  it('still lets the rider override their default for one trip', async () => {
    const call = vi.spyOn(api.journeysApi, 'search').mockResolvedValue(rushJourneySearch)
    vi.spyOn(api.profileApi, 'get').mockResolvedValue({
      ...travelProfile, defaultContextProfile: 'SAVE_MONEY',
    })
    renderWithProviders(<PlanTripPage />)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Cheapest' }))
      .toHaveAttribute('aria-pressed', 'true'))

    await userEvent.click(screen.getByRole('button', { name: 'Fastest' }))
    await userEvent.click(screen.getByRole('button', { name: /find routes/i }))

    await waitFor(() => expect(call).toHaveBeenCalledWith('Newark', 'Manhattan', 'RUSH'))
  })

  it('lets a deep link win over the saved commute', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    renderWithProviders(<PlanTripPage />, { route: '/plan?from=Hoboken&to=Brooklyn' })

    await waitFor(() => expect(screen.getByLabelText('From')).toHaveValue('Hoboken'))
    expect(screen.getByLabelText('To')).toHaveValue('Brooklyn')
  })
})
