import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WalletPage } from '../WalletPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import { demoConfig, insights, user, wallet } from '../../../test/fixtures'

describe('WalletPage', () => {
  beforeEach(() => {
    vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
    vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
    // The wallet reads projections from insights: the ledger knows what was
    // spent, only the travel profile knows what the week is heading toward.
    vi.spyOn(api.insightsApi, 'get').mockResolvedValue(insights)
  })

  it('leads with what is left, not with what was spent', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue(wallet)
    renderWithProviders(<WalletPage />)

    // Remaining is the figure that changes a decision, so it is the hero.
    // It appears twice: the hero panel and the "Remaining" module beneath it.
    expect(await screen.findAllByText('$21.60')).toHaveLength(2)
    // Scoped to the hero: "weekly transportation" also appears in the page
    // subtitle, and matching both would prove nothing about the hierarchy.
    const hero = document.querySelector('.budget-hero') as HTMLElement
    expect(within(hero).getByText('Weekly transportation')).toBeInTheDocument()
    expect(within(hero).getByText('remaining')).toBeInTheDocument()
    // Spent is present, but as support rather than as the headline.
    expect(screen.getByText(/\$28\.40 spent of \$50\.00/)).toBeInTheDocument()
  })

  it('gives a verdict on the week rather than only figures', async () => {
    vi.spyOn(api.walletApi, 'get').mockResolvedValue(wallet)
    renderWithProviders(<WalletPage />)

    // insights fixture projects $18.48 against a $50.00 budget.
    expect(await screen.findByText('On track')).toBeInTheDocument()
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
    expect(screen.getByText('Not set')).toBeInTheDocument()
    // And no verdict is offered about a budget that does not exist.
    expect(screen.queryByText(/on track|over budget/i)).not.toBeInTheDocument()
  })
})
