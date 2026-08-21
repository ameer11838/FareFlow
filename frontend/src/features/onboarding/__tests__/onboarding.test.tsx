import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../../App'
import * as api from '../../../api'
import { ApiError, tokenStore } from '../../../api/client'
import { renderWithProviders } from '../../../test/renderWith'
import {
  authConfig,
  demoConfig,
  emptyInsights,
  emptySpendingHistory,
  emptyTravelProfile,
  newUser,
  passRecommendation,
  philadelphia,
  profileOptions,
  profiles,
  travelProfile,
  user,
} from '../../../test/fixtures'

/** Everything the app touches once it is past the auth screens. */
function stubAppData() {
  vi.spyOn(api.recommendationsApi, 'profiles').mockResolvedValue(profiles)
  vi.spyOn(api.insightsApi, 'get').mockResolvedValue(emptyInsights)
  vi.spyOn(api.insightsApi, 'history').mockResolvedValue(emptySpendingHistory)
  vi.spyOn(api.locationsApi, 'search').mockResolvedValue([])
  vi.spyOn(api.profileApi, 'options').mockResolvedValue(profileOptions)
  vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
  // Insights asks the pass service whether a pass beats paying per ride. Left
  // unstubbed it reaches the network, and a pending request outlives the test.
  vi.spyOn(api.passesApi, 'recommendation').mockResolvedValue(passRecommendation)
}

const welcome = /let's make fareflow work for you/i

describe('onboarding — where a rider lands', () => {
  beforeEach(() => {
    tokenStore.clear()
    vi.spyOn(api.authApi, 'config').mockResolvedValue(authConfig)
    stubAppData()
  })

  it('sends a newly registered rider to onboarding, not to Plan', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    vi.spyOn(api.authApi, 'register').mockResolvedValue({
      token: 'new-token', expiresInSeconds: 3600, user: newUser,
    })
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(emptyTravelProfile)

    renderWithProviders(<App />, { route: '/register' })

    await userEvent.type(await screen.findByLabelText('Name'), 'New Rider')
    await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-good-password')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'a-good-password')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('heading', { name: welcome })).toBeInTheDocument()
  })

  it('sends a signing-in rider who never finished onboarding back to it', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    vi.spyOn(api.authApi, 'login').mockResolvedValue({
      token: 'a-real-token', expiresInSeconds: 3600, user: newUser,
    })
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(emptyTravelProfile)

    renderWithProviders(<App />, { route: '/login' })

    await userEvent.type(await screen.findByLabelText('Email'), 'new@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-good-password')
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByRole('heading', { name: welcome })).toBeInTheDocument()
  })

  it('lets a rider who already finished it straight into the app', async () => {
    tokenStore.set('a-real-token')
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)

    renderWithProviders(<App />, { route: '/insights' })

    expect(await screen.findByRole('heading', { name: /insights|good (morning|afternoon|evening)/i }))
      .toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: welcome })).not.toBeInTheDocument()
  })

  it('refuses to re-run onboarding for someone who has finished it', async () => {
    tokenStore.set('a-real-token')
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)

    renderWithProviders(<App />, { route: '/onboarding' })

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: welcome })).not.toBeInTheDocument())
  })

  it('bounces an unfinished rider out of the app and into onboarding', async () => {
    tokenStore.set('a-real-token')
    vi.spyOn(api.authApi, 'me').mockResolvedValue(newUser)
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(emptyTravelProfile)

    renderWithProviders(<App />, { route: '/wallet' })

    expect(await screen.findByRole('heading', { name: welcome })).toBeInTheDocument()
  })
})

describe('onboarding — demo mode', () => {
  beforeEach(() => {
    tokenStore.clear()
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
    stubAppData()
  })

  it('never interrupts the demo rider, who arrives with a finished profile', async () => {
    renderWithProviders(<App />, { route: '/onboarding' })

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: welcome })).not.toBeInTheDocument())
  })

  it('opens the app directly, with no onboarding in the way', async () => {
    renderWithProviders(<App />, { route: '/insights' })

    expect(await screen.findByText(/demo mode/i)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: welcome })).not.toBeInTheDocument()
  })
})

describe('onboarding — the flow', () => {
  beforeEach(() => {
    tokenStore.set('a-real-token')
    vi.spyOn(api.authApi, 'config').mockResolvedValue(authConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(newUser)
    stubAppData()
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(emptyTravelProfile)
  })

  const start = async () => {
    renderWithProviders(<App />, { route: '/onboarding' })
    await userEvent.click(await screen.findByRole('button', { name: /get started/i }))
  }

  it('counts the steps, and does not count the welcome screen as one', async () => {
    renderWithProviders(<App />, { route: '/onboarding' })

    await screen.findByRole('heading', { name: welcome })
    expect(screen.queryByText(/step \d of/i)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /get started/i }))
    expect(await screen.findByText('Step 1 of 6')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    expect(await screen.findByText('Step 2 of 6')).toBeInTheDocument()
  })

  it('moves forward and back without losing an answer', async () => {
    await start()

    await userEvent.click(await screen.findByRole('button', { name: /5\+ days a week/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByText('Step 2 of 6')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^back$/i }))

    const chosen = await screen.findByRole('button', { name: /5\+ days a week/i })
    expect(chosen).toHaveAttribute('aria-pressed', 'true')
  })

  it('offers the stances the backend serves, and sends back only the id', async () => {
    await start()
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))

    // Every stance from /api/profile/options, with the server's own wording.
    expect(await screen.findByRole('button', { name: /save me money/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /i'm in a rush/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /fewer transfers/i })).toBeInTheDocument()
  })

  it('treats "I\'m not sure" about the budget as an absence, not as $0.00', async () => {
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding')
      .mockResolvedValue({ ...travelProfile, weeklyBudgetCents: null })

    await start()
    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // frequency
    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // priority

    expect(await screen.findByText('Step 3 of 6')).toBeInTheDocument()

    // Pick a preset first, then change your mind: the preset must not linger.
    await userEvent.click(screen.getByRole('button', { name: '$50.00' }))
    await userEvent.click(screen.getByRole('button', { name: /i'm not sure yet/i }))

    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // commute
    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // habits
    await userEvent.click(screen.getByRole('button', { name: /review/i }))

    const summary = await screen.findByRole('heading', { name: /you're ready/i })
    expect(summary).toBeInTheDocument()
    // Not "$0.00" anywhere on the summary.
    expect(screen.queryByText('$0.00')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /start planning/i }))

    await waitFor(() => expect(complete).toHaveBeenCalledWith(
      expect.objectContaining({ weeklyBudgetCents: null })))
  })

  it('converts a chosen budget to integer cents', async () => {
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding').mockResolvedValue(travelProfile)

    await start()
    await userEvent.click(screen.getByRole('button', { name: /3–4 days a week/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /save me money/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))

    await userEvent.type(await screen.findByLabelText(/or enter your own/i), '37.50')
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /review/i }))
    await userEvent.click(await screen.findByRole('button', { name: /start planning/i }))

    await waitFor(() => expect(complete).toHaveBeenCalledWith(expect.objectContaining({
      weeklyBudgetCents: 3750,
      weeklyCommuteFrequency: 'THREE_TO_FOUR_DAYS',
      defaultContextProfile: 'SAVE_MONEY',
    })))
  })

  it('saves a commute only once it is resolved to real coordinates', async () => {
    vi.spyOn(api.locationsApi, 'search').mockResolvedValue([philadelphia])
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding').mockResolvedValue(travelProfile)

    await start()
    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // frequency
    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // priority
    await userEvent.click(screen.getByRole('button', { name: /continue/i })) // budget

    expect(await screen.findByText('Step 4 of 6')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^work$/i }))

    const from = await screen.findByLabelText(/home or starting point/i)
    await userEvent.type(from, 'Phil')

    // Typed text alone is not a place: only choosing a suggestion resolves it.
    expect(await screen.findByText(/pick a suggestion/i)).toBeInTheDocument()

    const option = await screen.findByRole('option', { name: /philadelphia/i })
    await userEvent.click(option)
    expect(await screen.findByText(/resolved to philadelphia/i)).toBeInTheDocument()

    // Only one end resolved, so no commute is sent rather than half of one.
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /review/i }))
    await userEvent.click(await screen.findByRole('button', { name: /start planning/i }))

    await waitFor(() => expect(complete).toHaveBeenCalledWith(expect.objectContaining({
      typicalOrigin: null,
      typicalDestination: null,
    })))
  })

  it('sends both ends of the commute with their coordinates', async () => {
    vi.spyOn(api.locationsApi, 'search').mockResolvedValue([philadelphia])
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding').mockResolvedValue(travelProfile)

    await start()
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(await screen.findByRole('button', { name: /^work$/i }))

    await userEvent.type(await screen.findByLabelText(/home or starting point/i), 'Phil')
    await userEvent.click(await screen.findByRole('option', { name: /philadelphia/i }))

    await userEvent.type(await screen.findByLabelText(/where you usually go/i), 'Phil')
    const menus = await screen.findAllByRole('option', { name: /philadelphia/i })
    await userEvent.click(menus[menus.length - 1])

    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /review/i }))
    await userEvent.click(await screen.findByRole('button', { name: /start planning/i }))

    await waitFor(() => expect(complete).toHaveBeenCalledWith(expect.objectContaining({
      typicalOrigin: expect.objectContaining({
        name: 'Philadelphia, PA', latitude: 39.9526, longitude: -75.1652,
      }),
      typicalDestination: expect.objectContaining({ name: 'Philadelphia, PA' }),
    })))
  })

  it('multi-selects travel modes and records the payment style', async () => {
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding').mockResolvedValue(travelProfile)

    await start()
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByText('Step 5 of 6')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^train$/i }))
    await userEvent.click(screen.getByRole('button', { name: /^ferry$/i }))
    await userEvent.click(screen.getByRole('button', { name: /^bus$/i }))
    // Tapping again deselects: it is a multi-select, not a one-way switch.
    await userEvent.click(screen.getByRole('button', { name: /^bus$/i }))
    await userEvent.click(screen.getByRole('button', { name: /weekly pass/i }))

    await userEvent.click(screen.getByRole('button', { name: /review/i }))
    await userEvent.click(await screen.findByRole('button', { name: /start planning/i }))

    await waitFor(() => expect(complete).toHaveBeenCalledWith(expect.objectContaining({
      preferredModes: ['TRAIN', 'FERRY'],
      passPreference: 'WEEKLY_PASS',
    })))
  })

  it('summarises every answer before anything is saved', async () => {
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding').mockResolvedValue(travelProfile)

    await start()
    await userEvent.click(screen.getByRole('button', { name: /3–4 days a week/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /save me money/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: '$50.00' }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(await screen.findByRole('button', { name: /^work$/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /^subway$/i }))
    await userEvent.click(screen.getByRole('button', { name: /pay per ride/i }))
    await userEvent.click(screen.getByRole('button', { name: /review/i }))

    expect(await screen.findByRole('heading', { name: /you're ready/i })).toBeInTheDocument()

    const rowFor = (label: string) =>
      screen.getByText(label).closest('.summary-row') as HTMLElement

    expect(within(rowFor('Travel priority')).getByText('Save me money')).toBeInTheDocument()
    expect(within(rowFor('Weekly budget')).getByText('$50.00')).toBeInTheDocument()
    expect(within(rowFor('Commute frequency')).getByText('3–4 days a week')).toBeInTheDocument()
    expect(within(rowFor('Payment style')).getByText('Pay per ride')).toBeInTheDocument()
    expect(within(rowFor('Usual modes')).getByText('Subway')).toBeInTheDocument()

    // Nothing has been written yet: the summary is a review, not a receipt.
    expect(complete).not.toHaveBeenCalled()
  })

  it('surfaces a save failure instead of pretending onboarding finished', async () => {
    vi.spyOn(api.profileApi, 'completeOnboarding').mockRejectedValue(
      new ApiError(400, { title: 'Validation failed', detail: 'Weekly budget must be $2,000.00 or less' }))

    await start()
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await userEvent.click(screen.getByRole('button', { name: /review/i }))
    await userEvent.click(await screen.findByRole('button', { name: /start planning/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/\$2,000.00 or less/)
    expect(screen.getByRole('heading', { name: /you're ready/i })).toBeInTheDocument()
  })
})
