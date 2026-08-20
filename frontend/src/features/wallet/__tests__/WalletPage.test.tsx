import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WalletPage } from '../WalletPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import { demoConfig, user, wallet } from '../../../test/fixtures'

describe('WalletPage', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
  })

  it('shows the balance derived from the weekly budget', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue(wallet)
    renderWithProviders(<WalletPage />)

    // $21.60 appears twice on purpose: the balance card and the "Remaining" tile.
    expect(await screen.findAllByText('$21.60')).toHaveLength(2)
    expect(screen.getByText(/available balance/i)).toBeInTheDocument()
    expect(screen.getByText('$28.40')).toBeInTheDocument()
  })

  it('marks only the budget-backed method as active', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue(wallet)
    renderWithProviders(<WalletPage />)

    const balance = await screen.findByTestId('payment-FAREFLOW_BALANCE')
    expect(within(balance).getByText('Active')).toBeInTheDocument()

    // Nothing that cannot actually move money may look selectable.
    expect(within(screen.getByTestId('payment-CARD')).getByText(/coming later/i)).toBeInTheDocument()
    expect(within(screen.getByTestId('payment-STABLECOIN')).getByText(/coming later/i)).toBeInTheDocument()
  })

  it('renders recent activity straight from the ledger', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue(wallet)
    renderWithProviders(<WalletPage />)

    const charge = await screen.findByTestId('wallet-entry-1')
    expect(within(charge).getByText('PATH — Newark to Manhattan')).toBeInTheDocument()
    expect(within(charge).getByText('-$3.00')).toHaveClass('amount-out')

    const refund = screen.getByTestId('wallet-entry-2')
    expect(within(refund).getByText('+$3.00')).toHaveClass('amount-in')
  })

  it('says the other rails do not move real money', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue(wallet)
    renderWithProviders(<WalletPage />)

    expect(await screen.findByText(/neither moves real money today/i)).toBeInTheDocument()
  })

  it('shows an empty state when nothing has happened yet', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue({ ...wallet, recentActivity: [] })
    renderWithProviders(<WalletPage />)

    expect(await screen.findByText(/no activity yet/i)).toBeInTheDocument()
  })

  it('shows an error state when the wallet fails to load', async () => {
    vi.spyOn(api.walletApi, 'get').mockRejectedValue(
      new ApiError(500, { title: 'Internal server error', detail: 'Broke' }))
    renderWithProviders(<WalletPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal server error')
  })
})

describe('WalletPage — no budget set', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
  })

  it('asks for a budget rather than showing an empty balance', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue({
      ...wallet,
      availableBalanceCents: null,
      weeklyBudgetCents: null,
      budgetUtilization: null,
    })
    renderWithProviders(<WalletPage />)

    expect(await screen.findByText('Set a weekly budget')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /set a budget/i })).toHaveAttribute('href', '/settings')
    // "$0.00" would read as "you are out of money", which is not what is true.
    expect(screen.queryByText('$0.00')).not.toBeInTheDocument()
    expect(screen.getAllByText('Not set').length).toBeGreaterThanOrEqual(2)
  })
})
