import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InsightsPage } from '../InsightsPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import {
  demoConfig, emptyInsights, insights, noBudgetInsights, passRecommendation, user,
} from '../../../test/fixtures'

describe('InsightsPage', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
    vi.spyOn(api.passesApi, 'recommendation').mockResolvedValue(passRecommendation)
  })

  it('shows the headline weekly figures', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    // Savings leads: it is the only figure that answers "was FareFlow worth it".
    expect(await screen.findByText(/saved this week/i)).toBeInTheDocument()
    const headline = document.querySelector('.headline') as HTMLElement
    expect(headline.querySelector('.headline-value')).toHaveTextContent('$13.40')
    expect(within(headline).getByText(/across 8 trips/i)).toBeInTheDocument()

    // Spend and budget are support, in the adherence module. Each figure appears
    // twice there by design — once as a metric, once as a bar on the shared
    // baseline that makes the three directly comparable.
    const figures = document.querySelector('.adherence-figures') as HTMLElement
    expect(within(figures).getByText('$24.65')).toBeInTheDocument()   // spent
    expect(within(figures).getByText('$50.00')).toBeInTheDocument()   // budget
    expect(within(figures).getByText('$25.35')).toBeInTheDocument()   // remaining
  })

  it('breaks spending down by provider', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    const path = await screen.findByTestId('chart-bar-PATH')
    expect(within(path).getByText('PATH')).toBeInTheDocument()
    expect(within(path).getByText('$15.00')).toBeInTheDocument()
    expect(within(path).getByText(/5 trips/)).toBeInTheDocument()

    expect(screen.getByTestId('chart-bar-NJ_TRANSIT')).toBeInTheDocument()
    expect(screen.getByTestId('chart-bar-NYC_BUS')).toBeInTheDocument()
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

    expect(await screen.findByText(/cheapest you used/i)).toBeInTheDocument()
    expect(screen.getByText(/fastest you used/i)).toBeInTheDocument()
  })

  it('labels the monthly figure as a naive projection', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText('$106.82')).toBeInTheDocument()
    expect(screen.getByText(/straight-line from this week alone/i)).toBeInTheDocument()
  })

  it('invents nothing when there are no trips yet', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(emptyInsights)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByText(/your insights are getting ready/i)).toBeInTheDocument()
    expect(screen.getByText(/nothing here is simulated/i)).toBeInTheDocument()
    // With nothing to derive from, the page shows the empty state instead of a
    // grid of dashes — and never a fabricated $0.00.
    expect(screen.queryByText(/projected monthly/i)).not.toBeInTheDocument()
    expect(screen.queryByText('$0.00')).not.toBeInTheDocument()
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
    vi.spyOn(api.passesApi, 'recommendation').mockResolvedValue(passRecommendation)
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

    // The projection appears as a bar on the same baseline as spend and budget,
    // so the three are directly comparable rather than three loose figures.
    expect(await screen.findByTestId('compare-projected')).toBeInTheDocument()
    expect(within(screen.getByTestId('compare-projected')).getByText('$18.48')).toBeInTheDocument()
    expect(within(screen.getByTestId('compare-spent')).getByText('$24.65')).toBeInTheDocument()
    expect(within(screen.getByTestId('compare-budget')).getByText('$50.00')).toBeInTheDocument()
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
    // And the adherence module asks for one rather than charting against nothing.
    expect(screen.getByText(/no weekly budget set/i)).toBeInTheDocument()

    // The budget and remaining tiles read "Not set". Spent is genuinely $0.00
    // and still says so — an absent budget is not an absent ledger.
    // "$0.00" would read as "you are out of money", which is not what is true.
    expect(screen.queryByText(/\$0\.00 remaining/i)).not.toBeInTheDocument()
  })

  it('renders nothing personal for a rider with no profile', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(emptyInsights)
    renderWithProviders(<InsightsPage />)

    await screen.findByText(/your insights are getting ready/i)
    // With no trips there is nothing to personalise from, so no headline card.
    expect(document.querySelector('.headline')).toBeNull()
  })
})
