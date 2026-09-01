import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InsightsPage } from '../InsightsPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import {
  demoConfig, emptyInsights, emptySpendingHistory, insights, noBudgetInsights, passRecommendation,
  spendingHistory, spendingHistoryThreeDays, user,
} from '../../../test/fixtures'

describe('InsightsPage', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
    vi.spyOn(api.passesApi, 'recommendation').mockResolvedValue(passRecommendation)
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(emptySpendingHistory)
  })

  it('leads with budget status, and states each figure once', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    // Budget status leads: spend on its own is a number, spend against a budget
    // is a verdict, and the verdict is the reason to open this page.
    const lede = await screen.findByTestId('budget-lede')
    expect(lede.querySelector('.lede-value')).toHaveTextContent('$24.65')
    expect(within(lede).getByText('of $50.00')).toBeInTheDocument()
    expect(within(lede).getByText(/\$25\.35 left this week/i)).toBeInTheDocument()
    expect(lede).toHaveClass('lede-under')

    // Savings is the second question, so it is the secondary figure.
    expect(within(lede).getByText(/saved vs the fastest route/i)).toBeInTheDocument()
    expect(lede.querySelector('.lede-aside-value')).toHaveTextContent('$13.40')

    // And each of those figures appears exactly once on the page. The old
    // layout printed spend in the lede and again in a figure row, and savings
    // in the lede and again under "Trade-offs".
    expect(screen.getAllByText('$24.65')).toHaveLength(1)
    expect(screen.getAllByText('$13.40')).toHaveLength(1)
  })

  it('breaks spending down by provider', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    // A table, not a chart: share, trip count, and average fare are all real
    // columns, where a bar chart would give one of them and need a legend.
    const path = await screen.findByTestId('operator-PATH')
    expect(within(path).getByText('PATH')).toBeInTheDocument()
    expect(within(path).getByText(/\$15\.00/)).toBeInTheDocument()
    expect(within(path).getByText('5')).toBeInTheDocument()
    expect(within(path).getByText('$3.00')).toBeInTheDocument()

    expect(screen.getByTestId('operator-NJ_TRANSIT')).toBeInTheDocument()
    expect(screen.getByTestId('operator-NYC_BUS')).toBeInTheDocument()
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

  it('explores only history periods backed by completed trips', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(spendingHistory)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByRole('heading', { name: /transportation analytics/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '7 days' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '30 days' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '3 months' })).not.toBeInTheDocument()

    // The period comparison survives as a plain sentence. The "PATH accounts
    // for 77%" line beside it does not: it was the "Why it matters" cell of a
    // self-narrating strip, and the operator breakdown already carries it.
    expect(screen.getByText(/spending is up \$2\.85/i)).toBeInTheDocument()
    expect(screen.queryByText(/why it matters/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/what you can do/i)).not.toBeInTheDocument()
  })

  it('shows a stat comparison, not chart chrome, for a two-day history', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(spendingHistory)
    renderWithProviders(<InsightsPage />)

    // Two active days. A gridded, axis-bearing line chart over two points
    // asserts a trend that two points cannot support, so the figures are shown
    // side by side instead.
    // Generous timeout: InsightsCharts is lazy-loaded, and under a full parallel
    // suite the Suspense boundary can take longer than the 1s default.
    expect(await screen.findAllByTestId('sparse-series', {}, { timeout: 10_000 }))
      .not.toHaveLength(0)
    expect(screen.queryByTestId('chart-spending')).not.toBeInTheDocument()
    expect(screen.queryByTestId('chart-average-duration')).not.toBeInTheDocument()

    // Two operators and two modes still divide, so those keep their donuts.
    expect(screen.getByTestId('chart-operators')).toBeInTheDocument()
    expect(screen.getByTestId('chart-modes')).toBeInTheDocument()
  }, 15_000)

  it('draws the full time series once there are three active days', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(spendingHistoryThreeDays)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByTestId('chart-spending', {}, { timeout: 10_000 }))
      .toHaveAccessibleName(/spending over 30 days/i)
    expect(screen.getByTestId('chart-trips')).toHaveAccessibleName(/trips over 30 days/i)
    expect(screen.getByTestId('chart-savings')).toBeInTheDocument()
    expect(screen.getByTestId('chart-average-fare')).toBeInTheDocument()
    expect(screen.getByTestId('chart-average-duration')).toBeInTheDocument()
    expect(screen.getByTestId('chart-budget')).toBeInTheDocument()
    expect(screen.getByTestId('chart-fare-duration')).toBeInTheDocument()
    expect(screen.queryByTestId('sparse-series')).not.toBeInTheDocument()
  }, 15_000)

  it('cross-filters all dashboard metrics by operator', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(spendingHistory)
    renderWithProviders(<InsightsPage />)

    await screen.findByRole('heading', { name: /transportation analytics/i })
    await userEvent.selectOptions(screen.getByLabelText(/filter by operator/i), 'NYC_BUS')

    // The dashboard's totals are one line now, not a row of four metric cards
    // repeating figures the lede and the charts already carry.
    const scope = document.querySelector('.analytics-filter-status') as HTMLElement
    expect(within(scope).getByText(/showing NYC Bus/i)).toBeInTheDocument()
    expect(within(scope).getByText(/\$3\.65 · 1 trip/)).toBeInTheDocument()
    expect(screen.getByTestId('history-sparse-state')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /clear filters/i }))
    expect(screen.getByText(/showing all completed trips/i)).toBeInTheDocument()
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
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(emptySpendingHistory)
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
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(spendingHistory)
    renderWithProviders(<InsightsPage />)

    // Actual, projection, and budget now share an Apache ECharts bullet comparison.
    expect(await screen.findByTestId('chart-budget')).toHaveAccessibleName(
      /weekly budget compared with actual and projected spending/i)
    expect(screen.getByText('$31.52 projected buffer')).toBeInTheDocument()
  })

  it('links the saved commute straight into Plan', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
    renderWithProviders(<InsightsPage />)

    const link = await screen.findByRole('link', { name: /Newark.*Manhattan/ })
    expect(link).toHaveAttribute('href', '/plan?from=Newark&to=Manhattan')
  })

  it('asks for a budget instead of reporting $0.00 when none is set', async () => {
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(noBudgetInsights)
    vi.spyOn(api.insightsApi, 'history').mockResolvedValue(spendingHistory)
    renderWithProviders(<InsightsPage />)

    expect(await screen.findByRole('button', { name: /set a weekly budget/i })).toBeInTheDocument()
    // The analytics comparison explains why it cannot draw a budget reference.
    expect(await screen.findByText(/no budget set/i)).toBeInTheDocument()
    expect(await screen.findByText(/set a weekly budget to unlock this comparison/i)).toBeInTheDocument()

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
