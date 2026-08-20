import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import { SettingsPage } from '../SettingsPage'
import { renderWithProviders } from '../../../test/renderWith'
import { demoConfig, emptyTravelProfile, profileOptions, travelProfile, user } from '../../../test/fixtures'

function stub() {
  vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
  vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
  vi.spyOn(api.locationsApi, 'search').mockResolvedValue([])
  vi.spyOn(api.profileApi, 'options').mockResolvedValue(profileOptions)
}

describe('SettingsPage', () => {
  beforeEach(stub)

  it('loads the saved profile into every control', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByRole('heading', { name: /your travel profile/i })).toBeInTheDocument()

    expect(screen.getByRole('button', { name: /^balanced/i })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /3–4 days a week/i })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /pay per ride/i })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /^work$/i })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /^train$/i })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '$50.00' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByDisplayValue('Newark')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Manhattan')).toBeInTheDocument()
  })

  it('saves an edit through the profile endpoint, not the onboarding one', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
    const update = vi.spyOn(api.profileApi, 'update').mockResolvedValue({
      ...travelProfile, defaultContextProfile: 'RUSH', weeklyBudgetCents: 7500,
    })
    const complete = vi.spyOn(api.profileApi, 'completeOnboarding')

    renderWithProviders(<SettingsPage />)

    await userEvent.click(await screen.findByRole('button', { name: /i'm in a rush/i }))
    await userEvent.click(screen.getByRole('button', { name: '$75.00' }))
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => expect(update).toHaveBeenCalledWith(expect.objectContaining({
      defaultContextProfile: 'RUSH',
      weeklyBudgetCents: 7500,
    })))
    // Editing settings must never make someone repeat onboarding.
    expect(complete).not.toHaveBeenCalled()
    expect(await screen.findByText('Saved')).toBeInTheDocument()
  })

  it('can clear a budget back to unset without it becoming zero', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
    const update = vi.spyOn(api.profileApi, 'update')
      .mockResolvedValue({ ...travelProfile, weeklyBudgetCents: null })

    renderWithProviders(<SettingsPage />)

    await userEvent.click(await screen.findByRole('button', { name: /i'm not sure yet/i }))
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => expect(update).toHaveBeenCalledWith(
      expect.objectContaining({ weeklyBudgetCents: null })))
  })

  it('drops the saved commute when the rider says they have none', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
    const update = vi.spyOn(api.profileApi, 'update').mockResolvedValue(travelProfile)

    renderWithProviders(<SettingsPage />)

    await userEvent.click(await screen.findByRole('button', { name: /no regular commute/i }))
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => expect(update).toHaveBeenCalledWith(expect.objectContaining({
      commuteKind: 'NONE',
      typicalOrigin: null,
      typicalDestination: null,
    })))
  })

  it('works for a rider who has no profile yet', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(emptyTravelProfile)
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByRole('heading', { name: /your travel profile/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /i'm not sure yet/i }))
      .toHaveAttribute('aria-pressed', 'true')
  })

  it('reports a failed save rather than showing Saved', async () => {
    vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
    vi.spyOn(api.profileApi, 'update').mockRejectedValue(
      new ApiError(400, { title: 'Validation failed', detail: 'Weekly budget must be $2,000.00 or less' }))

    renderWithProviders(<SettingsPage />)

    await userEvent.click(await screen.findByRole('button', { name: /save changes/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/\$2,000.00 or less/)
    expect(screen.queryByText('Saved')).not.toBeInTheDocument()
  })
})
