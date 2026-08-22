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
  createdPayment,
  settledPayment,
  profiles,
  rushJourneySearch,
  emptyTravelProfile,
  septaNjtJourney,
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
  // Origin and destination are no longer hardcoded: they are pre-filled from the
  // signed-in rider's saved commute, which is Newark to Manhattan here.
  vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
  vi.spyOn(api.paymentsApi, 'create').mockResolvedValue(createdPayment)
  vi.spyOn(api.paymentsApi, 'confirm').mockResolvedValue(settledPayment)
  vi.spyOn(api.paymentsApi, 'retry').mockResolvedValue(settledPayment)
  vi.spyOn(api.journeysApi, 'take').mockResolvedValue(settledPayment.trip!)
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

  it('creates and settles a server-priced payment without sending a fare', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const create = vi.spyOn(api.paymentsApi, 'create').mockResolvedValue(createdPayment)
    const confirmPayment = vi.spyOn(api.paymentsApi, 'confirm').mockResolvedValue(settledPayment)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    await userEvent.click(await screen.findByRole('button', { name: /pay \$27\.60/i }))

    await waitFor(() => expect(create).toHaveBeenCalled())
    const [body] = create.mock.calls[0]
    expect(body).toEqual({
      from: 'Philadelphia, PA',
      to: 'Manhattan, NY',
      journeyId: septaNjtJourney.journeyId,
      profile: 'BALANCED',
      confirmUnknownFare: false,
      paymentMethod: 'FAREFLOW_WALLET',
    })
    // The amount is the server's business; the client must not be able to state one.
    expect(JSON.stringify(body)).not.toContain('2760')
    expect(confirmPayment).toHaveBeenCalledWith(createdPayment.id)
    expect(navigate).toHaveBeenCalledWith(`/trips?payment=${settledPayment.id}`)
  })

  it('sends an idempotency key so a double click cannot charge twice', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const create = vi.spyOn(api.paymentsApi, 'create').mockResolvedValue(createdPayment)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    const choose = within(card).getByRole('button', { name: /choose/i })
    await userEvent.click(choose)
    const pay = await screen.findByRole('button', { name: /pay \$27\.60/i })
    await userEvent.click(pay)
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))

    await userEvent.click(pay)
    await waitFor(() => expect(create).toHaveBeenCalledTimes(2))

    // The same journey reuses its key, so the server dedupes the retry.
    expect(create.mock.calls[0][1]).toBe(create.mock.calls[1][1])
    expect(create.mock.calls[0][1]).toBeTruthy()
  })

  it('asks before recording a journey with no computable fare', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const take = vi.spyOn(api.journeysApi, 'take').mockResolvedValue(settledPayment.trip!)
    const create = vi.spyOn(api.paymentsApi, 'create')

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${amtrakJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    await userEvent.click(await screen.findByRole('button', { name: /record trip without charge/i }))

    await waitFor(() => expect(confirm).toHaveBeenCalled())
    await waitFor(() => expect(take).toHaveBeenCalledTimes(1))
    expect(take.mock.calls[0][0].confirmUnknownFare).toBe(true)
    expect(create).not.toHaveBeenCalled()
    expect(navigate).toHaveBeenCalledWith('/trips')
  })

  it('records nothing when the rider declines an unknown fare', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    const create = vi.spyOn(api.paymentsApi, 'create')
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${amtrakJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))

    expect(create).not.toHaveBeenCalled()
    expect(navigate).not.toHaveBeenCalled()
  })

  it('surfaces a server error rather than navigating away', async () => {
    vi.spyOn(api.journeysApi, 'search').mockResolvedValue(journeySearch)
    vi.spyOn(api.paymentsApi, 'create').mockRejectedValue(
      new ApiError(404, { title: 'Resource not found', detail: 'That journey is no longer available.' }))

    renderWithProviders(<PlanTripPage />)
    await search()

    const card = await screen.findByTestId(`journey-card-${septaNjtJourney.journeyId}`)
    await userEvent.click(within(card).getByRole('button', { name: /choose/i }))
    await userEvent.click(await screen.findByRole('button', { name: /pay \$27\.60/i }))

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
