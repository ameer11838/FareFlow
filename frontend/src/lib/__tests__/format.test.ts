import { describe, expect, it } from 'vitest'
import { formatCents, formatMinutes, formatPercent, formatSignedCents, labelText, ledgerTypeText } from '../format'

describe('money formatting', () => {
  it('renders integer cents as currency', () => {
    expect(formatCents(625)).toBe('$6.25')
    expect(formatCents(300)).toBe('$3.00')
    expect(formatCents(0)).toBe('$0.00')
    expect(formatCents(5)).toBe('$0.05')
  })

  it('keeps the sign outside the currency symbol', () => {
    expect(formatCents(-325)).toBe('-$3.25')
  })

  it('renders ledger amounts with an explicit direction', () => {
    expect(formatSignedCents(-300)).toBe('-$3.00')
    expect(formatSignedCents(300)).toBe('+$3.00')
    expect(formatSignedCents(0)).toBe('$0.00')
  })
})

describe('duration formatting', () => {
  it('shows minutes below an hour and hours above', () => {
    expect(formatMinutes(38)).toBe('38 min')
    expect(formatMinutes(60)).toBe('1 hr')
    expect(formatMinutes(85)).toBe('1 hr 25 min')
  })
})

describe('label formatting', () => {
  it('humanizes engine labels', () => {
    expect(labelText('BEST_VALUE')).toBe('Best value')
    expect(labelText('CHEAPEST')).toBe('Cheapest')
    expect(labelText('FASTEST')).toBe('Fastest')
  })

  it('keeps ledger terminology recognizable', () => {
    expect(ledgerTypeText('TRIP_CHARGE')).toBe('Trip charge')
    expect(ledgerTypeText('FARE_ADJUSTMENT')).toBe('Fare adjustment')
  })

  it('rounds percentages', () => {
    expect(formatPercent(0.568)).toBe('57%')
    expect(formatPercent(0.45)).toBe('45%')
  })
})
