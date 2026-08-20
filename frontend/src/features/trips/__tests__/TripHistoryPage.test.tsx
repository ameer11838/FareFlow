import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TripHistoryPage } from '../TripHistoryPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import { cancelledTrip, demoConfig, journeyDetail, journeyTrip, page, trip, user } from '../../../test/fixtures'

/** Demo mode: the server supplies the user, so no token is involved. */
function stubAuth() {
  vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
  vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
}

describe('TripHistoryPage', () => {
  beforeEach(() => {
    stubAuth()
  })

  it('shows origin, destination, provider, duration, fare, and savings', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([trip]))
    renderWithProviders(<TripHistoryPage />)

    const row = await screen.findByTestId('trip-1')
    // The route is a heading built from three elements, so it is asserted by its
    // accessible name rather than as one text node.
    expect(within(row).getByRole('heading', { name: 'Newark to Manhattan' })).toBeInTheDocument()
    expect(within(row).getByText('PATH')).toBeInTheDocument()
    expect(within(row).getByText('38 min')).toBeInTheDocument()
    expect(within(row).getByText('$3.00')).toBeInTheDocument()
    expect(within(row).getByText(/Saved \$3\.25 vs the fastest route/)).toBeInTheDocument()
    expect(within(row).getByText('Completed')).toBeInTheDocument()
  })

  it('shows the refund state for a cancelled trip', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([cancelledTrip]))
    renderWithProviders(<TripHistoryPage />)

    const row = await screen.findByTestId('trip-2')
    expect(within(row).getByText('Cancelled')).toBeInTheDocument()
    expect(within(row).getByText(/refunded \$3\.00 to your ledger/i)).toBeInTheDocument()
    // A cancelled trip cannot be cancelled again.
    expect(within(row).queryByRole('button', { name: /cancel/i })).not.toBeInTheDocument()
  })

  it('says so honestly when no savings comparison exists', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(
      page([{ ...trip, baselineFareCents: null, savedVersusFastestCents: null }]))
    renderWithProviders(<TripHistoryPage />)

    expect(await screen.findByText(/no alternative route to compare against/i)).toBeInTheDocument()
  })

  it('reports zero savings for the fastest route rather than hiding it', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(
      page([{ ...trip, savedVersusFastestCents: 0 }]))
    renderWithProviders(<TripHistoryPage />)

    expect(await screen.findByText(/took the fastest route/i)).toBeInTheDocument()
  })

  it('cancels a trip and refetches', async () => {
    const list = vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([trip]))
    const cancel = vi.spyOn(api.tripsApi, 'cancel').mockResolvedValue(cancelledTrip)

    renderWithProviders(<TripHistoryPage />)
    await userEvent.click(await screen.findByRole('button', { name: /^cancel$/i }))

    await waitFor(() => expect(cancel).toHaveBeenCalledWith(1))
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
  })

  it('surfaces a 409 when a trip is already cancelled', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([trip]))
    vi.spyOn(api.tripsApi, 'cancel').mockRejectedValue(
      new ApiError(409, { title: 'Conflicting request', detail: 'Trip 1 is already cancelled' }))

    renderWithProviders(<TripHistoryPage />)
    await userEvent.click(await screen.findByRole('button', { name: /^cancel$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('already cancelled')
  })

  it('shows an empty state with a call to action', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([]))
    renderWithProviders(<TripHistoryPage />)

    expect(await screen.findByText(/no trips yet/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /plan a trip/i })).toBeInTheDocument()
  })

  it('shows an error state when loading fails', async () => {
    vi.spyOn(api.tripsApi, 'list').mockRejectedValue(
      new ApiError(404, { title: 'Resource not found', detail: 'User 1 was not found' }))
    renderWithProviders(<TripHistoryPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('User 1 was not found')
  })
})


describe('TripHistoryPage — multi-leg journeys', () => {
  beforeEach(() => { stubAuth() })

  it('shows a discovered journey as a single readable row', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([journeyTrip]))
    renderWithProviders(<TripHistoryPage />)

    const row = await screen.findByTestId('trip-3')
    expect(within(row).getByRole('heading', { name: 'Philadelphia, PA to Manhattan, NY' })).toBeInTheDocument()
    expect(within(row).getByText(/SEPTA Trenton Line → NJ Transit/)).toBeInTheDocument()
    expect(within(row).getByText('$27.60')).toBeInTheDocument()
  })

  it('keeps the itinerary collapsed until asked for', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([journeyTrip]))
    const detail = vi.spyOn(api.journeysApi, 'detail').mockResolvedValue(journeyDetail)

    renderWithProviders(<TripHistoryPage />)
    await screen.findByTestId('trip-3')

    // Not fetched up front: a page of trips must not fetch every itinerary.
    expect(detail).not.toHaveBeenCalled()
    expect(screen.queryByTestId('itinerary-9')).not.toBeInTheDocument()
  })

  it('expands into the stored leg timeline', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([journeyTrip]))
    const detail = vi.spyOn(api.journeysApi, 'detail').mockResolvedValue(journeyDetail)

    renderWithProviders(<TripHistoryPage />)
    await userEvent.click(await screen.findByTestId('itinerary-toggle-9'))

    await waitFor(() => expect(detail).toHaveBeenCalledWith(9))
    const itinerary = await screen.findByTestId('itinerary-9')
    // Stations and the lines between them are separate rungs of the timeline, so
    // each is asserted on its own rather than as one "Walk to X" sentence.
    expect(within(itinerary).getByText('SEPTA Trenton Line')).toBeInTheDocument()
    expect(within(itinerary).getByText('NJ Transit Northeast Corridor')).toBeInTheDocument()
    expect(within(itinerary).getAllByText(/Trenton Transit Center/).length).toBeGreaterThan(0)
    // The final arrival is rendered as a destination, closing the timeline.
    expect(within(itinerary).getByText('Manhattan, NY')).toBeInTheDocument()
    // And the figures a rider would check against the fare.
    expect(within(itinerary).getByText('Total duration')).toBeInTheDocument()
    expect(within(itinerary).getByText('2 hr 57 min')).toBeInTheDocument()
  })

  it('shows the fare breakdown that was frozen at selection time', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([journeyTrip]))
    vi.spyOn(api.journeysApi, 'detail').mockResolvedValue(journeyDetail)

    renderWithProviders(<TripHistoryPage />)
    await userEvent.click(await screen.findByTestId('itinerary-toggle-9'))

    const itinerary = await screen.findByTestId('itinerary-9')
    expect(within(itinerary).getByText(/SEPTA Regional Rail \(Zone 4/)).toBeInTheDocument()
  })

  it('offers no itinerary toggle for a seeded-route trip', async () => {
    vi.spyOn(api.tripsApi, 'list').mockResolvedValue(page([trip]))
    renderWithProviders(<TripHistoryPage />)

    await screen.findByTestId('trip-1')
    expect(screen.queryByText(/view itinerary/i)).not.toBeInTheDocument()
  })
})
