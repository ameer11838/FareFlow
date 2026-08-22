import type {
  SpendingHistory, SpendingHistoryBucket, SpendingHistoryObservation,
} from '../../api/types'

export interface AnalyticsFilters {
  operator: string | null
  mode: string | null
  bucketDate: string | null
}

export interface AnalyticsGroup {
  id: string
  name: string
  tripCount: number
  spentCents: number
  averageFareCents: number
}

export interface AnalyticsRoute {
  origin: string
  destination: string
  provider: string
  tripCount: number
  averageFareCents: number
}

export interface AnalyticsView {
  observations: SpendingHistoryObservation[]
  buckets: SpendingHistoryBucket[]
  totals: {
    spentCents: number
    tripCount: number
    averageFareCents: number | null
    averageDurationMinutes: number | null
    savedCents: number | null
    costPerMileCents: number | null
  }
  byOperator: AnalyticsGroup[]
  byMode: AnalyticsGroup[]
  routes: AnalyticsRoute[]
  distinctTripDays: number
}

/**
 * Re-aggregates only stored trip observations. Zero-trip buckets remain zero for
 * spend/trip counts and null for averages/savings, preserving the backend's
 * missing-data semantics after every cross-filter.
 */
export function buildAnalyticsView(
  history: SpendingHistory,
  filters: AnalyticsFilters,
): AnalyticsView {
  const observations = history.observations.filter((trip) =>
    (!filters.operator || trip.provider === filters.operator)
    && (!filters.mode || trip.mode === filters.mode)
    && (!filters.bucketDate || trip.bucketDate === filters.bucketDate))

  const sourceBuckets = filters.bucketDate
    ? history.buckets.filter((bucket) => bucket.date === filters.bucketDate)
    : history.buckets
  let cumulativeSpentCents = 0
  const buckets = sourceBuckets.map((bucket) => {
    const trips = observations.filter((trip) => trip.bucketDate === bucket.date)
    const spentCents = sum(trips.map((trip) => trip.fareCents))
    const comparableSavings = trips.flatMap((trip) =>
      trip.savedCents === null ? [] : [trip.savedCents])
    cumulativeSpentCents += spentCents
    return {
      ...bucket,
      spentCents,
      tripCount: trips.length,
      averageFareCents: average(trips.map((trip) => trip.fareCents)),
      averageDurationMinutes: average(trips.map((trip) => trip.durationMinutes)),
      savedCents: comparableSavings.length === 0 ? null : sum(comparableSavings),
      cumulativeSpentCents,
    }
  })

  const comparableSavings = observations.flatMap((trip) =>
    trip.savedCents === null ? [] : [trip.savedCents])
  const distanceTrips = observations.filter((trip) =>
    trip.distanceMetres !== null && trip.distanceMetres > 0)
  const distanceMetres = sum(distanceTrips.map((trip) => trip.distanceMetres ?? 0))
  const distanceSpend = sum(distanceTrips.map((trip) => trip.fareCents))

  return {
    observations,
    buckets,
    totals: {
      spentCents: sum(observations.map((trip) => trip.fareCents)),
      tripCount: observations.length,
      averageFareCents: average(observations.map((trip) => trip.fareCents)),
      averageDurationMinutes: average(observations.map((trip) => trip.durationMinutes)),
      savedCents: comparableSavings.length === 0 ? null : sum(comparableSavings),
      costPerMileCents: distanceMetres === 0
        ? null : Math.round(distanceSpend / (distanceMetres / 1_609.344)),
    },
    byOperator: group(observations, (trip) => trip.provider, (trip) => trip.providerName),
    byMode: group(observations, (trip) => trip.mode, (trip) => trip.modeName),
    routes: routes(observations),
    distinctTripDays: new Set(observations.map((trip) => trip.tripDate)).size,
  }
}

function group(
  observations: SpendingHistoryObservation[],
  idOf: (trip: SpendingHistoryObservation) => string,
  nameOf: (trip: SpendingHistoryObservation) => string,
): AnalyticsGroup[] {
  const grouped = new Map<string, SpendingHistoryObservation[]>()
  observations.forEach((trip) => {
    const id = idOf(trip)
    grouped.set(id, [...(grouped.get(id) ?? []), trip])
  })
  return [...grouped.entries()].map(([id, trips]) => ({
    id,
    name: nameOf(trips[0]),
    tripCount: trips.length,
    spentCents: sum(trips.map((trip) => trip.fareCents)),
    averageFareCents: average(trips.map((trip) => trip.fareCents)) ?? 0,
  })).sort((left, right) => right.spentCents - left.spentCents)
}

function routes(observations: SpendingHistoryObservation[]): AnalyticsRoute[] {
  const grouped = new Map<string, SpendingHistoryObservation[]>()
  observations.forEach((trip) => {
    const key = `${trip.origin}\u0000${trip.destination}\u0000${trip.providerName}`
    grouped.set(key, [...(grouped.get(key) ?? []), trip])
  })
  return [...grouped.values()].map((trips) => ({
    origin: trips[0].origin,
    destination: trips[0].destination,
    provider: trips[0].providerName,
    tripCount: trips.length,
    averageFareCents: average(trips.map((trip) => trip.fareCents)) ?? 0,
  })).sort((left, right) => right.tripCount - left.tripCount
    || left.origin.localeCompare(right.origin)).slice(0, 5)
}

function sum(values: number[]): number {
  return values.reduce((total, value) => total + value, 0)
}

function average(values: number[]): number | null {
  return values.length === 0 ? null : Math.round(sum(values) / values.length)
}
