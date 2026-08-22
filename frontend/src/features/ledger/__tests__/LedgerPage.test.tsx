import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LedgerPage } from '../LedgerPage'
import { renderWithProviders } from '../../../test/renderWith'
import * as api from '../../../api'
import { ApiError } from '../../../api/client'
import { demoConfig, ledgerEntries, page, user } from '../../../test/fixtures'

/** Demo mode: the server supplies the user, so no token is involved. */
function stubAuth() {
  vi.spyOn(api.authApi, 'config').mockResolvedValue(demoConfig)
  vi.spyOn(api.authApi, 'me').mockResolvedValue(user)
}

describe('LedgerPage', () => {
  beforeEach(() => {
    stubAuth()
  })

  it('renders charges and refunds with opposite visual treatment', async () => {
    vi.spyOn(api.ledgerApi, 'list').mockResolvedValue(page(ledgerEntries))
    renderWithProviders(<LedgerPage />)

    const charge = await screen.findByTestId('ledger-1')
    expect(within(charge).getByText('PATH — Newark to Manhattan')).toBeInTheDocument()
    expect(within(charge).getByText('Trip charge')).toBeInTheDocument()
    expect(within(charge).getByText('-$3.00')).toHaveClass('amount-out')

    const refund = screen.getByTestId('ledger-2')
    expect(within(refund).getByText('Refund: cancelled PATH trip')).toBeInTheDocument()
    expect(within(refund).getByText('Refund')).toBeInTheDocument()
    expect(within(refund).getByText('+$3.00')).toHaveClass('amount-in')
  })

  it('uses rider-facing payment history language', async () => {
    vi.spyOn(api.ledgerApi, 'list').mockResolvedValue(page(ledgerEntries))
    renderWithProviders(<LedgerPage />)

    expect(await screen.findByRole('heading', { name: 'Payment history' })).toBeInTheDocument()
    expect(screen.getAllByText('Trip charge').length).toBeGreaterThan(0)
    expect(screen.queryByText(/transportation ledger/i)).not.toBeInTheDocument()
  })

  it('groups entries by day with a net total', async () => {
    vi.spyOn(api.ledgerApi, 'list').mockResolvedValue(page(ledgerEntries))
    renderWithProviders(<LedgerPage />)

    // A charge and its refund on the same day net to zero.
    // The day header carries the net, the entry count, and the date.
    await screen.findByTestId('ledger-1')
    const header = document.querySelector('.ledger-day') as HTMLElement
    expect(within(header).getByText('$0.00')).toBeInTheDocument()
    expect(within(header).getByText(/2 entries/i)).toBeInTheDocument()
  })

  it('links each entry back to its trip', async () => {
    vi.spyOn(api.ledgerApi, 'list').mockResolvedValue(page(ledgerEntries))
    renderWithProviders(<LedgerPage />)

    expect(await screen.findAllByText('View trip #1')).toHaveLength(2)
  })

  it('shows an empty state explaining what will appear', async () => {
    vi.spyOn(api.ledgerApi, 'list').mockResolvedValue(page([]))
    renderWithProviders(<LedgerPage />)

    expect(await screen.findByText(/no payments yet/i)).toBeInTheDocument()
    expect(screen.getByText(/refunds, and fare adjustments/i)).toBeInTheDocument()
  })

  it('shows an error state when the ledger fails to load', async () => {
    vi.spyOn(api.ledgerApi, 'list').mockRejectedValue(
      new ApiError(500, { title: 'Internal server error', detail: 'An unexpected error occurred' }))
    renderWithProviders(<LedgerPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal server error')
  })
})
