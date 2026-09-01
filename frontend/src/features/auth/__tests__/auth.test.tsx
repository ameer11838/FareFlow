import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../../App'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError, tokenStore } from '../../../api/client'
import {
  authConfig, demoConfig, emptyInsights, emptySpendingHistory, passRecommendation,
  profileOptions, travelProfile, user,
} from '../../../test/fixtures'

function stubAppData() {
  vi.spyOn(api.routesApi, 'locations').mockResolvedValue({
    origins: ['Newark'], destinations: ['Manhattan'], sources: ['database'],
  })
  vi.spyOn(api.recommendationsApi, 'profiles').mockResolvedValue([])
  vi.spyOn(api.insightsApi, 'get').mockResolvedValue(emptyInsights)
  // Both were unstubbed, so they fell through to a real request that 401s — and
  // a 401 anywhere drops the session. The old test clicked sign-out before the
  // rejection landed, which hid it; any extra await exposes it.
  vi.spyOn(api.insightsApi, 'history').mockResolvedValue(emptySpendingHistory)
  vi.spyOn(api.passesApi, 'recommendation').mockResolvedValue(passRecommendation)
  vi.spyOn(api.profileApi, 'get').mockResolvedValue(travelProfile)
  vi.spyOn(api.profileApi, 'options').mockResolvedValue(profileOptions)
}

describe('auth mode', () => {
  beforeEach(() => {
    tokenStore.clear()
    vi.spyOn(api.authApi, 'config').mockResolvedValue(authConfig)
    stubAppData()
  })

  it('sends a signed-out visitor to login', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))

    renderWithProviders(<App />, { route: '/insights' })

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
  })

  it('signs in and lands on the app', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    const login = vi.spyOn(api.authApi, 'login').mockResolvedValue({
      token: 'a-real-token', expiresInSeconds: 3600, user,
    })

    renderWithProviders(<App />, { route: '/login' })

    await userEvent.type(await screen.findByLabelText('Email'), 'ameer@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'the-right-password')
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }))

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith({ email: 'ameer@example.com', password: 'the-right-password' }))
    // The token is persisted so a refresh keeps the session.
    await waitFor(() => expect(tokenStore.get()).toBe('a-real-token'))
  })

  it('shows the server message when credentials are wrong', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    vi.spyOn(api.authApi, 'login').mockRejectedValue(
      new ApiError(401, { title: 'Authentication required', detail: 'Incorrect email or password' }))

    renderWithProviders(<App />, { route: '/login' })

    await userEvent.type(await screen.findByLabelText('Email'), 'ameer@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password')
    expect(tokenStore.get()).toBeNull()
  })

  it('lets the rider reveal and hide the password without changing it', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    renderWithProviders(<App />, { route: '/login' })

    const password = await screen.findByLabelText('Password')
    await userEvent.type(password, 'still-my-password')
    expect(password).toHaveAttribute('type', 'password')

    await userEvent.click(screen.getByRole('button', { name: /show password/i }))
    expect(password).toHaveAttribute('type', 'text')
    expect(password).toHaveValue('still-my-password')

    await userEvent.click(screen.getByRole('button', { name: /hide password/i }))
    expect(password).toHaveAttribute('type', 'password')
  })

  it('registers with just a name, email, and password', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    const register = vi.spyOn(api.authApi, 'register').mockResolvedValue({
      token: 'new-token', expiresInSeconds: 3600, user,
    })

    renderWithProviders(<App />, { route: '/register' })

    await userEvent.type(await screen.findByLabelText('Name'), 'Ameer Hassan')
    await userEvent.type(screen.getByLabelText('Email'), 'ameer@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-good-password')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'a-good-password')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    // No budget on the signup form: onboarding asks for it, with the reason.
    await waitFor(() => expect(register).toHaveBeenCalledWith({
      name: 'Ameer Hassan',
      email: 'ameer@example.com',
      password: 'a-good-password',
    }))
    expect(screen.queryByLabelText(/budget/i)).not.toBeInTheDocument()
  })

  it('catches a password mismatch before calling the API', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    const register = vi.spyOn(api.authApi, 'register')

    renderWithProviders(<App />, { route: '/register' })

    await userEvent.type(await screen.findByLabelText('Name'), 'Ameer')
    await userEvent.type(screen.getByLabelText('Email'), 'ameer@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-good-password')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'something-else')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument()
    expect(register).not.toHaveBeenCalled()
  })

  it('an authenticated user visiting /login is redirected into the app', async () => {
    tokenStore.set('a-real-token')
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)

    renderWithProviders(<App />, { route: '/login' })

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: /welcome back/i })).not.toBeInTheDocument())
  })

  it('signing out clears the token and returns to login', async () => {
    tokenStore.set('a-real-token')
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)

    renderWithProviders(<App />, { route: '/insights' })

    // Sign-out moved into the account menu with everything else that is about
    // the person rather than about travel. The assertion is unchanged; only the
    // path to the control is.
    await userEvent.click(await screen.findByRole('button', { name: /account:/i }))
    await userEvent.click(await screen.findByRole('menuitem', { name: /sign out/i }))

    expect(tokenStore.get()).toBeNull()
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
  })

  it('shows the value proposition alongside the form', async () => {
    vi.spyOn(api.authApi, 'me').mockRejectedValue(new ApiError(401, { title: 'Authentication required' }))
    renderWithProviders(<App />, { route: '/login' })

    expect(await screen.findByText(/travel smarter/i)).toBeInTheDocument()
    expect(screen.getByText(/spend better/i)).toBeInTheDocument()
    expect(screen.getByText(/based on your time, budget, and travel preferences/i))
      .toBeInTheDocument()
  })
})

describe('demo mode', () => {
  beforeEach(() => {
    tokenStore.clear()
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
    stubAppData()
  })

  it('opens straight into the app with no login', async () => {
    renderWithProviders(<App />, { route: '/insights' })

    expect(await screen.findByText(/demo mode/i)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /welcome back/i })).not.toBeInTheDocument()
  })

  it('redirects /login to the app rather than showing a form', async () => {
    renderWithProviders(<App />, { route: '/login' })

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: /welcome back/i })).not.toBeInTheDocument())
  })

  it('offers no sign-out, because there is no session to end', async () => {
    renderWithProviders(<App />, { route: '/insights' })

    await screen.findByText(/demo mode/i)
    await userEvent.click(await screen.findByRole('button', { name: /account:/i }))

    expect(screen.queryByRole('menuitem', { name: /sign out/i })).not.toBeInTheDocument()
    expect(await screen.findByText(/no session to sign out of/i)).toBeInTheDocument()
  })

  it('never sends a token', async () => {
    renderWithProviders(<App />, { route: '/insights' })
    await screen.findByText(/demo mode/i)

    expect(tokenStore.get()).toBeNull()
  })
})
