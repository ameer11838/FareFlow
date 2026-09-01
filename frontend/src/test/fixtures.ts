import type {
  ApiUser,
  JourneyOption,
  JourneySearchResponse,
  LocationCandidate,
  PassRecommendation,
  AuthConfig,
  Insights,
  Wallet,
  ContextProfileOption,
  Dashboard,
  LedgerEntry,
  Page,
  PersistedJourneyDetail,
  ProfileOptions,
  TravelProfile,
  RecommendationResponse,
  RecommendedRoute,
  Trip,
  SpendingHistory,
  PaymentIntent,
  TransitSession,
} from '../api/types'

/**
 * Fixtures mirror real backend responses for Newark -> Manhattan, including the
 * hand-verified scores. If the API shape drifts, these fail alongside the UI.
 */

export const user: ApiUser = {
  id: 1,
  name: 'Ameer Hassan',
  email: 'ameer@example.com',
  weeklyBudgetCents: 5000,
  timezone: 'America/New_York',
  onboardingCompleted: true,
}

/** Just registered: no budget, no answers, nothing to personalise from yet. */
export const newUser: ApiUser = {
  ...user,
  id: 2,
  name: 'New Rider',
  email: 'new@example.com',
  weeklyBudgetCents: null,
  onboardingCompleted: false,
}

export const profiles: ContextProfileOption[] = [
  { id: 'BALANCED', displayName: 'Balanced', rationale: 'weighing cost and travel time equally', costPriority: 0.45, timePriority: 0.45, transferPriority: 0.1 },
  { id: 'RUSH', displayName: "I'm in a rush", rationale: 'prioritizing travel time over cost', costPriority: 0.15, timePriority: 0.75, transferPriority: 0.1 },
  { id: 'SAVE_MONEY', displayName: 'Save me money', rationale: 'prioritizing fare over travel time', costPriority: 0.75, timePriority: 0.15, transferPriority: 0.1 },
  { id: 'FEWER_TRANSFERS', displayName: 'Fewer transfers', rationale: 'prioritizing direct routes over cost and time', costPriority: 0.25, timePriority: 0.25, transferPriority: 0.5 },
]

function route(partial: Partial<RecommendedRoute> & Pick<RecommendedRoute, 'routeId' | 'provider' | 'providerName' | 'durationMinutes' | 'fareCents'>): RecommendedRoute {
  return {
    mode: 'RAIL',
    transfers: 0,
    labels: [],
    recommended: false,
    score: 0,
    breakdown: {
      normalizedFare: 0, normalizedTime: 0, normalizedTransfers: 0,
      fareContribution: 0, timeContribution: 0, transferContribution: 0,
    },
    overBudget: false,
    explanation: '',
    vsFastest: null,
    vsBestValue: null,
    vsCheapest: null,
    geometry: { source: 'SCHEMATIC', waypoints: [
      { name: 'Newark Penn Station', latitude: 40.735657, longitude: -74.164306 },
      { name: 'World Trade Center', latitude: 40.7126, longitude: -74.0113 },
    ] },
    ...partial,
  }
}

export const balancedRecommendation: RecommendationResponse = {
  origin: 'Newark',
  destination: 'Manhattan',
  profile: profiles[0],
  weightsUsed: { costPriority: 0.45, timePriority: 0.45, transferPriority: 0.1, source: 'DEFAULT', budgetPressure: 0 },
  summary: 'PATH balances cost and travel time best for this trip.',
  contextNote: null,
  options: [
    route({
      routeId: 2, provider: 'PATH', providerName: 'PATH', mode: 'SUBWAY',
      durationMinutes: 38, fareCents: 300,
      labels: ['BEST_VALUE'], recommended: true, score: 0.2316,
      explanation: 'PATH saves you $3.25 versus NJ Transit while adding 16 minutes — about $0.20 per minute of time given up.',
      vsFastest: { referenceRouteId: 1, referenceProvider: 'NJ Transit', fareDeltaCents: -325, minutesDelta: 16, transfersDelta: 0 },
    }),
    route({
      routeId: 3, provider: 'NYC_BUS', providerName: 'NYC Bus', mode: 'BUS',
      durationMinutes: 55, fareCents: 290,
      labels: ['CHEAPEST'], score: 0.45,
      explanation: 'Cheapest option. NYC Bus saves you $0.10 versus PATH while adding 17 minutes.',
      vsFastest: { referenceRouteId: 1, referenceProvider: 'NJ Transit', fareDeltaCents: -335, minutesDelta: 33, transfersDelta: 0 },
      vsBestValue: { referenceRouteId: 2, referenceProvider: 'PATH', fareDeltaCents: -10, minutesDelta: 17, transfersDelta: 0 },
    }),
    route({
      routeId: 1, provider: 'NJ_TRANSIT', providerName: 'NJ Transit', mode: 'RAIL',
      durationMinutes: 22, fareCents: 625,
      labels: ['FASTEST'], score: 0.45,
      explanation: 'Fastest option. NJ Transit costs $3.25 more than PATH but saves 16 minutes.',
      vsBestValue: { referenceRouteId: 2, referenceProvider: 'PATH', fareDeltaCents: 325, minutesDelta: -16, transfersDelta: 0 },
    }),
  ],
}

export const rushRecommendation: RecommendationResponse = {
  ...balancedRecommendation,
  profile: profiles[1],
  weightsUsed: { costPriority: 0.15, timePriority: 0.75, transferPriority: 0.1, source: 'PROFILE', budgetPressure: 0 },
  contextNote: 'You told FareFlow "I\'m in a rush", so it is prioritizing travel time over cost. NJ Transit costs $3.25 more than PATH but gets you there 16 minutes sooner.',
  options: [
    route({
      routeId: 1, provider: 'NJ_TRANSIT', providerName: 'NJ Transit', mode: 'RAIL',
      durationMinutes: 22, fareCents: 625,
      labels: ['BEST_VALUE', 'FASTEST'], recommended: true, score: 0.15,
      explanation: 'NJ Transit is the fastest option and still scores best on value.',
      vsCheapest: { referenceRouteId: 3, referenceProvider: 'NYC Bus', fareDeltaCents: 335, minutesDelta: -33, transfersDelta: 0 },
    }),
    route({
      routeId: 2, provider: 'PATH', providerName: 'PATH', mode: 'SUBWAY',
      durationMinutes: 38, fareCents: 300, score: 0.3681,
      explanation: 'PATH saves you $3.25 versus NJ Transit while adding 16 minutes.',
      vsFastest: { referenceRouteId: 1, referenceProvider: 'NJ Transit', fareDeltaCents: -325, minutesDelta: 16, transfersDelta: 0 },
    }),
    route({
      routeId: 3, provider: 'NYC_BUS', providerName: 'NYC Bus', mode: 'BUS',
      durationMinutes: 55, fareCents: 290,
      labels: ['CHEAPEST'], score: 0.75,
      explanation: 'Cheapest option.',
      vsFastest: { referenceRouteId: 1, referenceProvider: 'NJ Transit', fareDeltaCents: -335, minutesDelta: 33, transfersDelta: 0 },
    }),
  ],
}

export const emptyRecommendation: RecommendationResponse = {
  origin: 'Atlantis',
  destination: 'Manhattan',
  profile: profiles[0],
  weightsUsed: balancedRecommendation.weightsUsed,
  summary: 'No routes are available between Atlantis and Manhattan.',
  contextNote: null,
  options: [],
}

export const trip: Trip = {
  id: 1, userId: 1, routeId: 2, journeyId: null,
  origin: 'Newark', destination: 'Manhattan',
  provider: 'PATH', providerName: 'PATH', mode: 'SUBWAY',
  fareCents: 300, durationMinutes: 38, transfers: 0,
  selectedLabel: 'BEST_VALUE',
  baselineFareCents: 625, savedVersusFastestCents: 325,
  status: 'COMPLETED',
  takenAt: '2026-08-19T12:32:00Z',
}

export const cancelledTrip: Trip = {
  ...trip, id: 2, status: 'CANCELLED', savedVersusFastestCents: 325,
}

export const dashboard: Dashboard = {
  user,
  week: {
    startDate: '2026-08-17',
    startsAt: '2026-08-17T04:00:00Z',
    endsAt: '2026-08-24T04:00:00Z',
    timezone: 'America/New_York',
  },
  spentCents: 2840,
  remainingCents: 2160,
  budgetUtilization: 0.568,
  tripCount: 9,
  savedVersusFastestCents: 1420,
  recentTrips: [trip],
}

export const ledgerEntries: LedgerEntry[] = [
  {
    id: 2, userId: 1, tripId: 1, paymentIntentId: null, type: 'REFUND', amountCents: 300,
    description: 'Refund: cancelled PATH trip',
    occurredAt: '2026-08-19T14:00:00Z', createdAt: '2026-08-19T14:00:00Z',
  },
  {
    id: 1, userId: 1, tripId: 1, paymentIntentId: null, type: 'TRIP_CHARGE', amountCents: -300,
    description: 'PATH — Newark to Manhattan',
    occurredAt: '2026-08-19T12:32:00Z', createdAt: '2026-08-19T12:32:00Z',
  },
]

export const demoConfig: AuthConfig = {
  authEnabled: false,
  demoMode: true,
  demoUserName: 'Ameer Demo',
}

/**
 * A completed profile: the Newark to Manhattan commute the demo rider has.
 *
 * The Plan page fills its origin and destination from this, which is why the
 * planner suite stubs it — the fields are no longer hardcoded to a pair, they
 * come from whoever is signed in.
 */
export const travelProfile: TravelProfile = {
  onboardingCompleted: true,
  onboardingCompletedAt: '2026-08-18T14:00:00Z',
  defaultContextProfile: 'BALANCED',
  defaultContextProfileName: 'Balanced',
  weeklyCommuteFrequency: 'THREE_TO_FOUR_DAYS',
  weeklyCommuteFrequencyName: '3–4 days a week',
  estimatedCommuteDaysPerWeek: 3,
  weeklyBudgetCents: 5000,
  commuteKind: 'WORK',
  commuteKindName: 'Work',
  typicalOrigin: {
    name: 'Newark', latitude: 40.735657, longitude: -74.164306,
    providerPlaceId: 'static:newark',
  },
  typicalDestination: {
    name: 'Manhattan', latitude: 40.758, longitude: -73.9855,
    providerPlaceId: 'static:manhattan',
  },
  hasTypicalCommute: true,
  passPreference: 'PAY_PER_RIDE',
  passPreferenceName: 'Pay per ride',
  fareCategory: 'REGULAR',
  fareCategoryName: 'Regular fare',
  preferredModes: [
    { id: 'TRAIN', displayName: 'Train' },
    { id: 'SUBWAY', displayName: 'Subway' },
  ],
}

/** A rider who has registered but answered nothing yet. */
export const emptyTravelProfile: TravelProfile = {
  onboardingCompleted: false,
  onboardingCompletedAt: null,
  defaultContextProfile: 'BALANCED',
  defaultContextProfileName: 'Balanced',
  weeklyCommuteFrequency: null,
  weeklyCommuteFrequencyName: null,
  estimatedCommuteDaysPerWeek: null,
  weeklyBudgetCents: null,
  commuteKind: null,
  commuteKindName: null,
  typicalOrigin: null,
  typicalDestination: null,
  hasTypicalCommute: false,
  passPreference: null,
  passPreferenceName: null,
  fareCategory: 'REGULAR',
  fareCategoryName: 'Regular fare',
  preferredModes: [],
}

/** The vocabulary catalogue, exactly as the backend serves it. */
export const profileOptions: ProfileOptions = {
  contextProfiles: profiles,
  commuteFrequencies: [
    { id: 'ONE_TO_TWO_DAYS', displayName: '1–2 days a week', detail: 'About 1 commuting days a week' },
    { id: 'THREE_TO_FOUR_DAYS', displayName: '3–4 days a week', detail: 'About 3 commuting days a week' },
    { id: 'FIVE_PLUS_DAYS', displayName: '5+ days a week', detail: 'About 5 commuting days a week' },
    { id: 'VARIES', displayName: 'It varies', detail: 'FareFlow will estimate from your actual trips' },
  ],
  commuteKinds: [
    { id: 'WORK', displayName: 'Work', detail: null },
    { id: 'SCHOOL', displayName: 'School', detail: null },
    { id: 'BOTH', displayName: 'Work and school', detail: null },
    { id: 'NONE', displayName: 'No regular commute', detail: null },
  ],
  passPreferences: [
    { id: 'PAY_PER_RIDE', displayName: 'Pay per ride', detail: null },
    { id: 'WEEKLY_PASS', displayName: 'Weekly pass', detail: null },
    { id: 'MONTHLY_PASS', displayName: 'Monthly pass', detail: null },
    { id: 'NOT_SURE', displayName: 'Not sure', detail: null },
  ],
  fareCategories: [
    { id: 'REGULAR', displayName: 'Regular fare', detail: 'Standard stop-based pricing' },
    { id: 'STUDENT', displayName: 'Student', detail: 'Discounted stop and boarding charges' },
    { id: 'SENIOR', displayName: 'Senior', detail: 'Reduced-fare stop and boarding charges' },
    { id: 'REDUCED', displayName: 'Reduced fare', detail: 'Reduced pricing for eligible riders' },
  ],
  travelModes: [
    { id: 'TRAIN', displayName: 'Train' },
    { id: 'SUBWAY', displayName: 'Subway' },
    { id: 'BUS', displayName: 'Bus' },
    { id: 'FERRY', displayName: 'Ferry' },
  ],
}

export const authConfig: AuthConfig = {
  authEnabled: true,
  demoMode: false,
  demoUserName: null,
}

export const wallet: Wallet = {
  availableBalanceCents: 2160,
  spentThisWeekCents: 2840,
  weeklyBudgetCents: 5000,
  budgetUtilization: 0.568,
  paymentMethods: [
    {
      id: 'FAREFLOW_BALANCE',
      name: 'FareFlow Balance',
      description: 'Fares are charged against your weekly transportation budget',
      status: 'AVAILABLE',
    },
    {
      id: 'SIMULATED_CARD',
      name: 'Simulated card',
      description: 'Exercises authorization and settlement without moving real money',
      status: 'AVAILABLE',
    },
  ],
  recentActivity: ledgerEntries,
  recentPayments: [],
}

export const emptySpendingHistory: SpendingHistory = {
  range: '30d',
  rangeName: '30 days',
  granularity: 'DAY',
  startDate: '2026-07-22',
  endDate: '2026-08-20',
  hasData: false,
  firstTripDate: null,
  rangesWithData: [],
  weeklyBudgetCents: 5000,
  totals: {
    spentCents: 0,
    tripCount: 0,
    averageFareCents: null,
    averageDurationMinutes: null,
    savedCents: null,
    totalMinutes: 0,
    totalDistanceMetres: null,
    costPerMileCents: null,
    usagePricedTripCount: 0,
  },
  comparison: null,
  buckets: [],
  byOperator: [],
  byMode: [],
  mostUsedRoutes: [],
  observations: [],
}

export const spendingHistory: SpendingHistory = {
  ...emptySpendingHistory,
  hasData: true,
  firstTripDate: '2026-08-01',
  rangesWithData: ['7d', '30d'],
  totals: {
    spentCents: 1565,
    tripCount: 5,
    averageFareCents: 313,
    averageDurationMinutes: 38,
    savedCents: 420,
    totalMinutes: 190,
    totalDistanceMetres: 18_000,
    costPerMileCents: 140,
    usagePricedTripCount: 3,
  },
  comparison: {
    startDate: '2026-06-22',
    endDate: '2026-07-21',
    spentCents: 1280,
    tripCount: 4,
    averageFareCents: 320,
    spentChangeCents: 285,
    spentChangePercent: .2227,
  },
  buckets: [
    {
      date: '2026-08-19', label: 'Aug 19', spentCents: 600, tripCount: 2,
      averageFareCents: 300, averageDurationMinutes: 35, savedCents: 180,
      cumulativeSpentCents: 600,
    },
    {
      date: '2026-08-20', label: 'Aug 20', spentCents: 965, tripCount: 3,
      averageFareCents: 322, averageDurationMinutes: 40, savedCents: 240,
      cumulativeSpentCents: 1565,
    },
  ],
  byOperator: [
    {
      provider: 'PATH', providerName: 'PATH', tripCount: 4, spentCents: 1200,
      averageFareCents: 300, shareOfSpend: .7668,
    },
    {
      provider: 'NYC_BUS', providerName: 'NYC Bus', tripCount: 1, spentCents: 365,
      averageFareCents: 365, shareOfSpend: .2332,
    },
  ],
  byMode: [
    { mode: 'RAIL', modeName: 'Rail', tripCount: 4, spentCents: 1200, shareOfSpend: .7668 },
    { mode: 'BUS', modeName: 'Bus', tripCount: 1, spentCents: 365, shareOfSpend: .2332 },
  ],
  mostUsedRoutes: [
    {
      origin: 'Newark', destination: 'Manhattan', provider: 'PATH',
      tripCount: 4, totalFareCents: 1200, averageFareCents: 300,
    },
  ],
  observations: [
    {
      tripId: 101, takenAt: '2026-08-19T12:00:00Z', tripDate: '2026-08-19',
      bucketDate: '2026-08-19', provider: 'PATH', providerName: 'PATH',
      mode: 'RAIL', modeName: 'Rail', origin: 'Newark', destination: 'Manhattan',
      fareCents: 300, durationMinutes: 34, savedCents: 90, distanceMetres: 3600,
    },
    {
      tripId: 102, takenAt: '2026-08-19T21:00:00Z', tripDate: '2026-08-19',
      bucketDate: '2026-08-19', provider: 'PATH', providerName: 'PATH',
      mode: 'RAIL', modeName: 'Rail', origin: 'Manhattan', destination: 'Newark',
      fareCents: 300, durationMinutes: 36, savedCents: 90, distanceMetres: 3600,
    },
    {
      tripId: 103, takenAt: '2026-08-20T12:00:00Z', tripDate: '2026-08-20',
      bucketDate: '2026-08-20', provider: 'PATH', providerName: 'PATH',
      mode: 'RAIL', modeName: 'Rail', origin: 'Newark', destination: 'Manhattan',
      fareCents: 300, durationMinutes: 39, savedCents: 120, distanceMetres: 3600,
    },
    {
      tripId: 104, takenAt: '2026-08-20T16:00:00Z', tripDate: '2026-08-20',
      bucketDate: '2026-08-20', provider: 'PATH', providerName: 'PATH',
      mode: 'RAIL', modeName: 'Rail', origin: 'Manhattan', destination: 'Newark',
      fareCents: 300, durationMinutes: 41, savedCents: 120, distanceMetres: 3600,
    },
    {
      tripId: 105, takenAt: '2026-08-20T19:00:00Z', tripDate: '2026-08-20',
      bucketDate: '2026-08-20', provider: 'NYC_BUS', providerName: 'NYC Bus',
      mode: 'BUS', modeName: 'Bus', origin: 'Port Authority', destination: 'Chelsea',
      fareCents: 365, durationMinutes: 40, savedCents: null, distanceMetres: null,
    },
  ],
}

/**
 * A history long enough to earn chart chrome.
 *
 * <p>`spendingHistory` has trips on two days, which the dashboard deliberately
 * renders as a stat comparison rather than as five gridded time series. This
 * variant adds a third active day so the charting branch stays covered.
 */
export const spendingHistoryThreeDays: SpendingHistory = {
  ...spendingHistory,
  totals: { ...spendingHistory.totals, spentCents: 1965, tripCount: 6, usagePricedTripCount: 4 },
  buckets: [
    ...spendingHistory.buckets,
    {
      date: '2026-08-21', label: 'Aug 21', spentCents: 400, tripCount: 1,
      averageFareCents: 400, averageDurationMinutes: 42, savedCents: 0,
      cumulativeSpentCents: 1965,
    },
  ],
  observations: [
    ...spendingHistory.observations,
    {
      tripId: 106, takenAt: '2026-08-21T12:00:00Z', tripDate: '2026-08-21',
      bucketDate: '2026-08-21', provider: 'PATH', providerName: 'PATH',
      mode: 'RAIL', modeName: 'Rail', origin: 'Newark', destination: 'Manhattan',
      fareCents: 400, durationMinutes: 42, savedCents: 0, distanceMetres: 3600,
    },
  ],
}


export const insights: Insights = {
  spentCents: 2465,
  weeklyBudgetCents: 5000,
  remainingCents: 2535,
  budgetUtilization: 0.493,
  tripCount: 8,
  savedVersusFastestCents: 1340,
  averageFareCents: 308,
  averageDurationMinutes: 37,
  cheapestProvider: 'NYC_BUS',
  cheapestProviderName: 'NYC Bus',
  fastestProvider: 'NJ_TRANSIT',
  fastestProviderName: 'NJ Transit',
  minutesTradedForSavings: 64,
  projectedMonthlyCents: 10682,
  personalization: {
    commuteFrequency: 'THREE_TO_FOUR_DAYS',
    commuteFrequencyName: '3–4 days a week',
    commuteDaysPerWeek: 3,
    typicalOriginName: 'Newark',
    typicalDestinationName: 'Manhattan',
    projectedWeeklySpendCents: 1848,
    budgetBufferCents: 3152,
    suggestedPassCode: null,
    suggestedPassName: null,
    suggestedPassSavingsCents: null,
    notes: [
      'You told FareFlow you commute 3–4 days a week.',
      'At $3.08 a trip across about 3 commuting days, this week is tracking toward $18.48.',
      'Your $50.00 weekly budget leaves about $31.52 of buffer.',
    ],
  },
  spendingByProvider: [
    {
      provider: 'PATH', providerName: 'PATH', tripCount: 5,
      totalFareCents: 1500, averageFareCents: 300, averageDurationMinutes: 38,
    },
    {
      provider: 'NJ_TRANSIT', providerName: 'NJ Transit', tripCount: 1,
      totalFareCents: 625, averageFareCents: 625, averageDurationMinutes: 22,
    },
    {
      provider: 'NYC_BUS', providerName: 'NYC Bus', tripCount: 2,
      totalFareCents: 580, averageFareCents: 290, averageDurationMinutes: 55,
    },
  ],
}

/** Empty week: every derived figure is null, nothing is invented. */
export const emptyInsights: Insights = {
  spentCents: 0,
  weeklyBudgetCents: 5000,
  remainingCents: 5000,
  budgetUtilization: 0,
  tripCount: 0,
  savedVersusFastestCents: null,
  averageFareCents: null,
  averageDurationMinutes: null,
  cheapestProvider: null,
  cheapestProviderName: null,
  fastestProvider: null,
  fastestProviderName: null,
  minutesTradedForSavings: null,
  projectedMonthlyCents: null,
  spendingByProvider: [],
  personalization: null,
}

/**
 * A rider who chose "I'm not sure" at the budget step, and has travelled since.
 * Trips matter here: with none, the page shows its empty state rather than the
 * budget module this fixture exists to exercise.
 */
export const noBudgetInsights: Insights = {
  ...emptyInsights,
  spentCents: 900,
  tripCount: 3,
  averageFareCents: 300,
  averageDurationMinutes: 38,
  weeklyBudgetCents: null,
  remainingCents: null,
  budgetUtilization: null,
  personalization: {
    commuteFrequency: 'VARIES',
    commuteFrequencyName: 'It varies',
    commuteDaysPerWeek: 3,
    typicalOriginName: null,
    typicalDestinationName: null,
    projectedWeeklySpendCents: null,
    budgetBufferCents: null,
    suggestedPassCode: null,
    suggestedPassName: null,
    suggestedPassSavingsCents: null,
    notes: ['Set a weekly budget and FareFlow can tell you whether this pace fits it.'],
  },
}

export function page<T>(content: T[]): Page<T> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 }
}


/* ---------------- journeys (arbitrary origin/destination) ---------------- */

export const philadelphia: LocationCandidate = {
  providerPlaceId: 'static:philadelphia', displayName: 'Philadelphia, PA',
  locality: 'Philadelphia', region: 'PA', country: 'US',
  latitude: 39.9526, longitude: -75.1652, type: 'Geography', source: 'STATIC',
}

export const manhattan: LocationCandidate = {
  providerPlaceId: 'static:manhattan', displayName: 'Manhattan, NY',
  locality: 'New York', region: 'NY', country: 'US',
  latitude: 40.758, longitude: -73.9855, type: 'Geography', source: 'STATIC',
}

function leg(
  mode: JourneyOption['legs'][number]['mode'], lineName: string,
  fromName: string, toName: string, durationMinutes: number, waitMinutes = 0,
): JourneyOption['legs'][number] {
  return {
    mode, agency: mode === 'WALK' ? null : 'AGENCY', lineName,
    fromName, toName, durationMinutes, waitMinutes,
    waypoints: [
      { name: fromName, latitude: 39.95, longitude: -75.18 },
      { name: toName, latitude: 40.75, longitude: -73.99 },
    ],
  }
}

/** Cheapest and best value: SEPTA + NJ Transit, priced but estimated. */
export const septaNjtJourney: JourneyOption = {
  journeyId: 'SEPTA_TRE>NJT_NEC',
  summary: 'SEPTA Trenton Line → NJ Transit Northeast Corridor',
  totalMinutes: 177, walkingMinutes: 15, transfers: 1,
  fareCents: 2760, fareStatus: 'ESTIMATED', fareSource: 'FARE_RULE_ENGINE',
  fareBreakdown: ['SEPTA Regional Rail (Zone 4 / Trenton)  $10.00',
                  'NJ Transit rail (Trenton - New York)  $17.60'],
  labels: ['BEST_VALUE', 'CHEAPEST'], recommended: true, score: 0.21,
  explanation: 'SEPTA Trenton Line → NJ Transit saves you $12.40 while adding 32 minutes.',
  dataSource: 'CURATED_NETWORK',
  usageFareMinCents: 130, usageFareMaxCents: 475,
  usagePricingVersion: 'FAREFLOW_USAGE_V2',
  legs: [
    leg('WALK', 'Walk', 'Philadelphia, PA', 'Suburban Station', 4),
    leg('RAIL', 'SEPTA Trenton Line', 'Suburban Station', 'Trenton Transit Center', 65, 15),
    leg('RAIL', 'NJ Transit Northeast Corridor', 'Trenton Transit Center', 'New York Penn Station', 70, 12),
    leg('WALK', 'Walk', 'New York Penn Station', 'Manhattan, NY', 11),
  ],
}

/** Fastest, but Amtrak is dynamically priced so the fare is genuinely unknown. */
export const amtrakJourney: JourneyOption = {
  journeyId: 'AMTRAK_NER',
  summary: 'Amtrak Northeast Regional',
  totalMinutes: 145, walkingMinutes: 30, transfers: 0,
  fareCents: null, fareStatus: 'UNKNOWN', fareSource: 'UNKNOWN',
  fareBreakdown: ['Amtrak Northeast Regional — fare varies by demand and booking date  not priced'],
  labels: ['FASTEST'], recommended: false, score: 0.42,
  explanation: 'This option has no published fare FareFlow can compute, so it is not compared on cost.',
  dataSource: 'CURATED_NETWORK',
  usageFareMinCents: 130, usageFareMaxCents: 310,
  usagePricingVersion: 'FAREFLOW_USAGE_V2',
  legs: [
    leg('WALK', 'Walk', 'Philadelphia, PA', '30th Street Station', 19),
    leg('RAIL', 'Amtrak Northeast Regional', '30th Street Station', 'New York Penn Station', 85, 30),
    leg('WALK', 'Walk', 'New York Penn Station', 'Manhattan, NY', 11),
  ],
}

export const journeySearch: JourneySearchResponse = {
  origin: philadelphia,
  destination: manhattan,
  profile: profiles[0],
  weightsUsed: { costPriority: 0.45, timePriority: 0.45, transferPriority: 0.1, source: 'DEFAULT', budgetPressure: 0 },
  summary: 'SEPTA Trenton Line → NJ Transit Northeast Corridor is the cheapest option and still scores best on value.',
  contextNote: null,
  options: [septaNjtJourney, amtrakJourney],
  notices: [
    'Some options have no published fare FareFlow can compute (for example Amtrak, which is priced dynamically). Those are shown without a fare rather than with a guess.',
    'Journey times are typical scheduled durations, not live departures.',
  ],
}

export const rushJourneySearch: JourneySearchResponse = {
  ...journeySearch,
  profile: profiles[1],
  weightsUsed: { costPriority: 0.15, timePriority: 0.75, transferPriority: 0.1, source: 'PROFILE', budgetPressure: 0 },
  contextNote: 'You told FareFlow "I\'m in a rush", so it is prioritizing travel time over cost.',
  options: [
    { ...amtrakJourney, labels: ['BEST_VALUE', 'FASTEST'], recommended: true },
    { ...septaNjtJourney, labels: ['CHEAPEST'], recommended: false },
  ],
}

export const emptyJourneySearch: JourneySearchResponse = {
  ...journeySearch,
  summary: 'No public-transit route was returned between these places.',
  options: [],
  notices: ["Google Routes, imported GTFS, and FareFlow's curated fallback returned no public-transit itinerary. No schedule was invented."],
}

export const passRecommendation: PassRecommendation = {
  hasEnoughHistory: true, weeksOfHistory: 3,
  observedWeeklySpendCents: 6000, projectedMonthlySpendCents: 26000,
  recommendedPassCode: 'PATH_30DAY', recommendedPassName: 'PATH 30-Day SmartLink pass',
  recommendedPassPriceCents: 14200, monthlySavingsCents: 11800,
  verdict: 'Based on the last 21 days, a PATH 30-Day SmartLink pass would save about $118.00 a month.',
  confidence: 'HIGH',
  options: [{
    code: 'PATH_30DAY', name: 'PATH 30-Day SmartLink pass', agency: 'PATH',
    priceCents: 14200, monthlyCostCents: 14200, monthlySavingsCents: 11800, worthwhile: true,
  }],
  assumptions: ['Assumes your recent travel pattern continues unchanged.'],
}


/** A trip taken from a discovered multi-leg journey, not a seeded route. */
export const journeyTrip: Trip = {
  id: 3, userId: 1, routeId: null, journeyId: 9,
  origin: 'Philadelphia, PA', destination: 'Manhattan, NY',
  provider: 'SEPTA Trenton Line → NJ Transit Northeast Corridor',
  providerName: 'SEPTA Trenton Line → NJ Transit Northeast Corridor',
  mode: 'RAIL',
  fareCents: 2760, durationMinutes: 177, transfers: 1,
  selectedLabel: 'MANUAL',
  baselineFareCents: 4000, savedVersusFastestCents: 1240,
  status: 'COMPLETED', takenAt: '2026-08-20T12:32:00Z',
}

export const createdPayment: PaymentIntent = {
  id: '9d624735-9f97-4d8c-b0a1-c6451b7a6e73',
  status: 'CREATED',
  paymentMethod: 'FAREFLOW_WALLET',
  amountCents: 2760,
  currency: 'USD',
  journeySummary: 'SEPTA Trenton Line → NJ Transit Northeast Corridor',
  origin: 'Philadelphia, PA',
  destination: 'Manhattan, NY',
  attemptCount: 0,
  providerReference: null,
  failureCode: null,
  failureMessage: null,
  trip: null,
  authorizedAt: null,
  processingAt: null,
  settledAt: null,
  failedAt: null,
  refundedAt: null,
  createdAt: '2026-08-20T12:31:59Z',
  updatedAt: '2026-08-20T12:31:59Z',
  events: [{
    id: 1, fromStatus: null, toStatus: 'CREATED',
    reason: 'Authoritative fare calculated and payment intent created',
    occurredAt: '2026-08-20T12:31:59Z',
  }],
}

export const settledPayment: PaymentIntent = {
  ...createdPayment,
  status: 'SETTLED',
  attemptCount: 1,
  providerReference: 'fareflow_wallet_test_1',
  trip: journeyTrip,
  authorizedAt: '2026-08-20T12:32:00Z',
  processingAt: '2026-08-20T12:32:00Z',
  settledAt: '2026-08-20T12:32:00Z',
  updatedAt: '2026-08-20T12:32:00Z',
  events: [
    ...createdPayment.events,
    { id: 2, fromStatus: 'CREATED', toStatus: 'AUTHORIZED', reason: 'Payment authorized', occurredAt: '2026-08-20T12:32:00Z' },
    { id: 3, fromStatus: 'AUTHORIZED', toStatus: 'PROCESSING', reason: 'Settlement started', occurredAt: '2026-08-20T12:32:00Z' },
    { id: 4, fromStatus: 'PROCESSING', toStatus: 'SETTLED', reason: 'Payment settled', occurredAt: '2026-08-20T12:32:00Z' },
  ],
}

export const startedTransitSession: TransitSession = {
  id: 'c6bfac66-6fe4-4b60-a3e9-4bea7a366962',
  status: 'STARTED',
  journeyId: 12,
  origin: 'Philadelphia, PA',
  destination: 'Manhattan, NY',
  summary: septaNjtJourney.summary,
  dataSource: 'CURATED_NETWORK',
  scheduledDeparture: null,
  scheduledArrival: null,
  hasRealtimeData: false,
  startedAt: '2026-08-22T12:00:00Z',
  endedAt: null,
  elapsedSeconds: 0,
  activeLegIndex: 1,
  currentLine: 'SEPTA Trenton Line',
  currentAgency: 'AGENCY',
  currentMode: 'RAIL',
  currentStop: 'Suburban Station',
  nextStop: 'Trenton Transit Center',
  nextStopFareIncreaseCents: 130,
  transferToLine: 'NJ Transit Northeast Corridor',
  progressUnitsCompleted: 0,
  progressUnitsTotal: 2,
  completedStops: 0,
  plannedStops: 2,
  distanceTravelledMetres: 0,
  plannedDistanceMetres: 129_000,
  progressSource: 'RIDER_CONFIRMED',
  estimatedFareMinCents: 130,
  estimatedFareMaxCents: 475,
  currentFareCents: 0,
  currentEstimatedFareCents: 0,
  finalFareCents: null,
  fareCategory: 'REGULAR',
  fareCategoryName: 'Regular fare',
  dailyCapCents: 1200,
  weeklyCapCents: 6000,
  dailyCapRemainingCents: 1200,
  weeklyCapRemainingCents: 6000,
  transferDiscountCents: 0,
  concessionDiscountCents: 0,
  capDiscountCents: 0,
  fareBreakdown: ['No transit progress recorded · no charge'],
  fareEvents: [],
  stopFareProgress: [
    { sequence: 0, stopName: 'Suburban Station', lineName: 'SEPTA Trenton Line',
      mode: 'RAIL', state: 'CURRENT', fareIncrementCents: 0, cumulativeFareCents: 0,
      grossCents: 0, totalDiscountCents: 0, description: 'Boarding point' },
    { sequence: 1, stopName: 'Trenton Transit Center', lineName: 'SEPTA Trenton Line',
      mode: 'RAIL', state: 'NEXT', fareIncrementCents: 130, cumulativeFareCents: 130,
      grossCents: 130, totalDiscountCents: 0, description: 'Stop charge' },
    { sequence: 2, stopName: 'New York Penn Station', lineName: 'NJ Transit Northeast Corridor',
      mode: 'RAIL', state: 'UPCOMING', fareIncrementCents: 345, cumulativeFareCents: 475,
      grossCents: 345, totalDiscountCents: 0, description: 'Stop charge' },
  ],
  pricingVersion: 'FAREFLOW_USAGE_V2',
  canAdvance: true,
  canEnd: true,
  canPay: false,
  simulationNotice: 'FareFlow usage pricing is a simulation. No transit agency partnership or acceptance is implied.',
  legs: septaNjtJourney.legs.map((item, sequence) => ({ ...item, sequence })),
}

export const progressedTransitSession: TransitSession = {
  ...startedTransitSession,
  status: 'IN_PROGRESS',
  progressUnitsCompleted: 1,
  completedStops: 1,
  distanceTravelledMetres: 65_000,
  currentFareCents: 130,
  currentEstimatedFareCents: 130,
  activeLegIndex: 2,
  currentLine: 'NJ Transit Northeast Corridor',
  currentStop: 'Trenton Transit Center',
  nextStop: 'New York Penn Station',
  transferToLine: null,
  nextStopFareIncreaseCents: 345,
  stopFareProgress: startedTransitSession.stopFareProgress.map((stop) => ({
    ...stop,
    state: stop.sequence < 1 ? 'COMPLETED'
      : stop.sequence === 1 ? 'CURRENT' : stop.sequence === 2 ? 'NEXT' : 'UPCOMING',
  })),
}

export const completedTransitSession: TransitSession = {
  ...progressedTransitSession,
  status: 'COMPLETED',
  endedAt: '2026-08-22T12:12:00Z',
  elapsedSeconds: 720,
  finalFareCents: 130,
  nextStopFareIncreaseCents: 0,
  stopFareProgress: progressedTransitSession.stopFareProgress.map((stop) => ({
    ...stop,
    state: stop.sequence <= 1 ? 'COMPLETED' : 'UPCOMING',
  })),
  fareBreakdown: ['SEPTA Trenton Line · base 120¢ + distance 5¢ + 1 stop 5¢'],
  canAdvance: false,
  canEnd: false,
  canPay: true,
}

export const noChargeTransitSession: TransitSession = {
  ...startedTransitSession,
  status: 'NO_CHARGE',
  endedAt: '2026-08-22T12:02:00Z',
  elapsedSeconds: 120,
  finalFareCents: 0,
  nextStopFareIncreaseCents: 0,
  stopFareProgress: startedTransitSession.stopFareProgress.map((stop) => ({
    ...stop, state: stop.sequence === 0 ? 'CURRENT' : 'UPCOMING',
  })),
  canAdvance: false,
  canEnd: false,
  canPay: false,
}

export const settledSessionPayment: PaymentIntent = {
  ...settledPayment,
  transitSessionId: startedTransitSession.id,
  amountCents: 130,
  trip: {
    ...journeyTrip,
    transitSessionId: startedTransitSession.id,
    fareCents: 130,
    durationMinutes: 12,
    distanceMetres: 65_000,
    stopsTravelled: 1,
    fareModel: 'FAREFLOW_USAGE_V2',
  },
}

export const journeyDetail: PersistedJourneyDetail = {
  id: 9,
  originDisplayName: 'Philadelphia, PA',
  destinationDisplayName: 'Manhattan, NY',
  totalDurationMinutes: 177, walkingMinutes: 15, transfers: 1,
  totalFareCents: 2760, fareStatus: 'ESTIMATED', fareSource: 'FARE_RULE_ENGINE',
  fareBreakdown: ['SEPTA Regional Rail (Zone 4 / Trenton)  $10.00',
                  'NJ Transit rail (Trenton - New York)  $17.60'],
  summary: 'SEPTA Trenton Line → NJ Transit Northeast Corridor',
  legs: [
    { sequence: 0, mode: 'WALK', agency: null, lineName: 'Walk',
      fromName: 'Philadelphia, PA', toName: 'Suburban Station', durationMinutes: 4, waitMinutes: 0 },
    { sequence: 1, mode: 'RAIL', agency: 'SEPTA', lineName: 'SEPTA Trenton Line',
      fromName: 'Suburban Station', toName: 'Trenton Transit Center', durationMinutes: 65, waitMinutes: 15 },
    { sequence: 2, mode: 'RAIL', agency: 'NJ_TRANSIT', lineName: 'NJ Transit Northeast Corridor',
      fromName: 'Trenton Transit Center', toName: 'New York Penn Station', durationMinutes: 70, waitMinutes: 12 },
    { sequence: 3, mode: 'WALK', agency: null, lineName: 'Walk',
      fromName: 'New York Penn Station', toName: 'Manhattan, NY', durationMinutes: 11, waitMinutes: 0 },
  ],
}
