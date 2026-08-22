import { describe, expect, it } from 'vitest'
import { spendingHistory } from '../../../test/fixtures'
import { buildAnalyticsView } from '../analytics'

describe('buildAnalyticsView', () => {
  it('cross-filters every aggregate from stored operator observations', () => {
    const view = buildAnalyticsView(spendingHistory, {
      operator: 'PATH', mode: null, bucketDate: null,
    })

    expect(view.totals).toMatchObject({
      spentCents: 1200, tripCount: 4, averageFareCents: 300, savedCents: 420,
    })
    expect(view.byOperator.map((operator) => operator.id)).toEqual(['PATH'])
    expect(view.byMode.map((mode) => mode.id)).toEqual(['RAIL'])
    expect(view.buckets.map((bucket) => bucket.spentCents)).toEqual([600, 600])
    expect(view.observations.every((trip) => trip.provider === 'PATH')).toBe(true)
  })

  it('keeps missing savings and distance null instead of inventing zeroes', () => {
    const view = buildAnalyticsView(spendingHistory, {
      operator: 'NYC_BUS', mode: null, bucketDate: null,
    })

    expect(view.totals.savedCents).toBeNull()
    expect(view.totals.costPerMileCents).toBeNull()
    expect(view.buckets[0].savedCents).toBeNull()
    expect(view.buckets[1].savedCents).toBeNull()
  })

  it('uses a selected chart bucket as a dashboard-wide filter', () => {
    const view = buildAnalyticsView(spendingHistory, {
      operator: null, mode: null, bucketDate: '2026-08-19',
    })

    expect(view.buckets).toHaveLength(1)
    expect(view.totals.tripCount).toBe(2)
    expect(view.totals.spentCents).toBe(600)
    expect(view.distinctTripDays).toBe(1)
  })
})
