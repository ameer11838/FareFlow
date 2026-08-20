import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InsightsPage } from '../InsightsPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import {
  demoConfig, emptyInsights, insights, noBudgetInsights, user,
} from '../../../test/fixtures'

describe('InsightsPage', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
  })

  it('shows the headline weekly figures', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText('$24.65')).toBeInTheDocument()   // spent
    expect(screen.getByText('$25.35')).toBeInTheDocument()          // remaining
    expect(screen.getByText('$13.40')).toBeInTheDocument()          // saved vs fastest
    expect(screen.getByText('8')).toBeInTheDocument()               // trips
  })

  it('breaks spending down by provider', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    const path = await screen.findByTestId('provider-PATH')
    expect(within(path).getByText('PATH')).toBeInTheDocument()
    expect(within(path).getByText('$15.00')).toBeInTheDocument()
    expect(within(path).getByText(/5 trips/)).toBeInTheDocument()

    expect(screen.getByTestId('provider-NJ_TRANSIT')).toBeInTheDocument()
    expect(screen.getByTestId('provider-NYC_BUS')).toBeInTheDocument()
  })

  it('shows travel-pattern averages', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText('$3.08')).toBeInTheDocument()    // average fare
    expect(screen.getByText('37 min')).toBeInTheDocument()          // average duration
    expect(screen.getByText('1 hr 4 min')).toBeInTheDocument()      // time traded
  })

  it('names the cheapest and fastest providers used', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText(/cheapest provider used/i)).toBeInTheDocument()
    expect(screen.getByText(/fastest provider used/i)).toBeInTheDocument()
  })

  it('labels the monthly figure as a naive projection', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText('$106.82')).toBeInTheDocument()
    expect(screen.getByText(/one week is not a trend/i)).toBeInTheDocument()
  })

  it('invents nothing when there are no trips yet', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(emptyInsights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText(/no trips this week yet/i)).toBeInTheDocument()
    expect(screen.getByText(/nothing here is simulated/i)).toBeInTheDocument()
    // The savings tile shows a dash, never $0.00.
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.queryByText(/projected monthly/i)).not.toBeInTheDocument()
  })

  it('updates the weekly budget in integer cents', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    const update = vi.spyOn(api.usersApi, 'updateBudget')
      .mockResolvedValue({ ...user, weeklyBudgetCents: 7500 })

    renderWithProviders(<InsightsPage />)
    await userEvent.click(await screen.findByRole('button', { name: /edit budget/i }))

    const input = screen.getByLabelText(/weekly budget in dollars/i)
    await userEvent.clear(input)
    await userEvent.type(input, '75')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(update).toHaveBeenCalledWith(7500))
  })

  it('shows an error state when insights fail to load', async () => {
    vi.spyOn(api.insightsApi, 'get').mockRejectedValue(
      new ApiError(500, { title: 'Internal server error', detail: 'Broke' }))
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal server error')
  })
})

describe('InsightsPage — personalized from the profile', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
  })

  it('renders the sentences the backend derived, verbatim', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText(/you told fareflow you commute 3–4 days a week/i))
      .toBeInTheDocument()
    expect(screen.getByText(/tracking toward \$18\.48/i)).toBeInTheDocument()
    expect(screen.getByText(/\$50\.00 weekly budget leaves about \$31\.52 of buffer/i))
      .toBeInTheDocument()
  })

  it('shows the projection and buffer as figures, not just prose', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText(/projected this week/i)).toBeInTheDocument()
    expect(screen.getByText('$18.48')).toBeInTheDocument()
    expect(screen.getByText(/budget buffer/i)).toBeInTheDocument()
    expect(screen.getByText('$31.52')).toBeInTheDocument()
  })

  it('links the saved commute straight into Plan', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    const link = await screen.findByRole('link', { name: /Newark.*Manhattan/ })
    expect(link).toHaveAttribute('href', '/plan?from=Newark&to=Manhattan')
  })

  it('asks for a budget instead of reporting $0.00 when none is set', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(noBudgetInsights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByRole('button', { name: /set a weekly budget/i })).toBeInTheDocument()
    expect(screen.getByText(/set a weekly budget and fareflow can tell you/i)).toBeInTheDocument()

    // The budget and remaining tiles read "Not set". Spent is genuinely $0.00
    // and still says so — an absent budget is not an absent ledger.
    const tile = (label: string) =>
      screen.getByText(label).closest('.stat') as HTMLElement
    expect(within(tile('Weekly budget')).getByText('Not set')).toBeInTheDocument()
    expect(within(tile('Remaining')).getByText('Not set')).toBeInTheDocument()
    expect(within(tile('Spent')).getByText('$0.00')).toBeInTheDocument()
  })

  it('renders nothing personal for a rider with no profile', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(emptyInsights)
    renderWithProviders(<InsightsPage />)

    await screen.findByText(/no trips this week yet/i)
    expect(screen.queryByText(/for your travel/i)).not.toBeInTheDocument()
  })
})
