/**
 * TypeScript mirrors of the backend DTOs.
 *
 * All monetary values are integer cents, matching the backend end to end.
 * Formatting to dollars happens only in `lib/format.ts`.
 */

export interface LocationCandidate {
  providerPlaceId: string | null
  displayName: string
  locality: string
  region: string
  country: string
  latitude: number
  longitude: number
  type: string
  /** TOMTOM, STATIC, or GTFS — shown so the source of a place is traceable. */
  source: string
}

/** A map marker backed by a stop in a successfully imported GTFS feed. */
export interface TransitStop {
  id: string
  name: string
  regionCode: string
  regionName: string
  publisherName: string
  latitude: number
  longitude: number
  modes: Array<'RAIL' | 'SUBWAY' | 'LIGHT_RAIL' | 'BUS' | 'FERRY'>
  operators: string[]
  lines: string[]
  distanceMetres: number
  realtimeAvailable: boolean
  source: 'GTFS'
}

export type FareStatus = 'EXACT' | 'ESTIMATED' | 'UNKNOWN'

export interface JourneyLeg {
  mode: 'WALK' | 'RAIL' | 'SUBWAY' | 'LIGHT_RAIL' | 'BUS' | 'FERRY'
  agency: string | null
  lineName: string
  fromName: string
  toName: string
  durationMinutes: number
  waitMinutes: number
  distanceMetres?: number
  waypoints: Waypoint[]
  /** Present only when a provider supplied an actual scheduled/live time. */
  departureTime?: string | null
  arrivalTime?: string | null
  realtime?: boolean
  stopCount?: number | null
}

export interface JourneyOption {
  journeyId: string
  summary: string
  totalMinutes: number
  walkingMinutes: number
  transfers: number
  /** Null when no published fare could be computed. Never zero as a stand-in. */
  fareCents: number | null
  fareStatus: FareStatus
  fareSource: string
  fareBreakdown: string[]
  labels: RecommendationLabel[]
  recommended: boolean
  score: number
  explanation: string
  dataSource: string
  /** FareFlow's proposed usage model, separate from any published agency fare. */
  usageFareMinCents: number
  usageFareMaxCents: number
  usagePricingVersion: string
  legs: JourneyLeg[]
}

export type TransitSessionStatus =
  | 'STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'NO_CHARGE' | 'PAID'

export interface TransitSessionLeg extends JourneyLeg {
  sequence: number
}

export interface StopFareProgress {
  /** Zero is the boarding point; positive values are completed-stop boundaries. */
  sequence: number
  /** Null only when the transit provider omitted an intermediate stop name. */
  stopName: string | null
  lineName: string
  mode: JourneyLeg['mode']
  state: 'CURRENT' | 'COMPLETED' | 'NEXT' | 'UPCOMING' | 'SKIPPED' | 'DIVERTED'
  fareIncrementCents: number
  cumulativeFareCents: number
  grossCents: number
  totalDiscountCents: number
  description: string
}

export interface TransitFareEvent {
  id: number
  sequence: number
  eventType: 'STOP_COMPLETED' | 'STOP_SKIPPED' | 'ROUTE_DIVERSION'
  stopName: string | null
  lineName: string
  mode: JourneyLeg['mode']
  agency: string | null
  grossCents: number
  transferDiscountCents: number
  concessionDiscountCents: number
  capDiscountCents: number
  amountCents: number
  cumulativeFareCents: number
  description: string
  occurredAt: string
}

export interface TransitSession {
  id: string
  status: TransitSessionStatus
  journeyId: number
  origin: string
  destination: string
  summary: string
  dataSource: string
  scheduledDeparture: string | null
  scheduledArrival: string | null
  hasRealtimeData: boolean
  startedAt: string
  endedAt: string | null
  elapsedSeconds: number
  activeLegIndex: number
  currentLine: string
  currentAgency: string | null
  currentMode: JourneyLeg['mode']
  currentStop: string | null
  nextStop: string | null
  nextStopFareIncreaseCents: number
  transferToLine: string | null
  progressUnitsCompleted: number
  progressUnitsTotal: number
  completedStops: number
  plannedStops: number
  distanceTravelledMetres: number
  plannedDistanceMetres: number
  progressSource: 'RIDER_CONFIRMED' | 'LOCATION_VERIFIED' | 'AGENCY_VERIFIED'
  estimatedFareMinCents: number
  estimatedFareMaxCents: number
  /** Published/provider route fare captured when the journey was selected. */
  publishedFareCents?: number | null
  publishedFareStatus?: FareStatus
  publishedFareSource?: string
  /** Exact server-owned fare at the latest completed stop. */
  currentFareCents: number
  /** Compatibility alias retained for older clients. */
  currentEstimatedFareCents: number
  finalFareCents: number | null
  fareCategory: FareCategoryId
  fareCategoryName: string
  dailyCapCents: number
  weeklyCapCents: number
  dailyCapRemainingCents: number
  weeklyCapRemainingCents: number
  transferDiscountCents: number
  concessionDiscountCents: number
  capDiscountCents: number
  fareBreakdown: string[]
  fareEvents: TransitFareEvent[]
  stopFareProgress: StopFareProgress[]
  pricingVersion: string
  canAdvance: boolean
  canEnd: boolean
  canPay: boolean
  simulationNotice: string
  legs: TransitSessionLeg[]
}

export interface JourneySearchResponse {
  origin: LocationCandidate
  destination: LocationCandidate
  profile: ContextProfileOption
  weightsUsed: WeightsUsed
  budgetContext?: {
    weeklyBudgetCents: number
    spentThisWeekCents: number
  } | null
  summary: string
  contextNote: string | null
  options: JourneyOption[]
  /** Stated limitations — shown, not buried. */
  notices: string[]
}

export interface AssistantTurn {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistantConfig {
  available: boolean
  unavailableReason: string | null
  starters: string[]
}

export interface AssistantResponse {
  reply: string
  toolsUsed: string[]
  routes: JourneySearchResponse | null
  trips: Trip[]
  followUps: string[]
}

export interface AssistantPageContext {
  pagePath: string
  pageName: string
  activeRouteSearch: {
    origin: string
    destination: string
    profile: string
    selectedJourneyId: string | null
  } | null
}

export interface PersistedJourneyLeg {
  sequence: number
  mode: string
  agency: string | null
  lineName: string
  fromName: string
  toName: string
  durationMinutes: number
  waitMinutes: number
}

/** The itinerary as it was when the rider chose it — never re-derived. */
export interface PersistedJourneyDetail {
  id: number
  originDisplayName: string
  destinationDisplayName: string
  totalDurationMinutes: number
  walkingMinutes: number
  transfers: number
  totalFareCents: number | null
  fareStatus: FareStatus
  fareSource: string
  fareBreakdown: string[]
  summary: string
  legs: PersistedJourneyLeg[]
}

export interface PassOption {
  code: string
  name: string
  agency: string
  priceCents: number
  monthlyCostCents: number
  monthlySavingsCents: number
  worthwhile: boolean
}

export interface PassRecommendation {
  hasEnoughHistory: boolean
  weeksOfHistory: number
  observedWeeklySpendCents: number
  projectedMonthlySpendCents: number
  recommendedPassCode: string | null
  recommendedPassName: string | null
  recommendedPassPriceCents: number | null
  monthlySavingsCents: number | null
  verdict: string
  confidence: string
  options: PassOption[]
  assumptions: string[]
}

export interface AuthConfig {
  authEnabled: boolean
  demoMode: boolean
  demoUserName: string | null
}

export interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: ApiUser
}

export interface Wallet {
  /** Null when no budget is set: there is no balance to report, not a $0.00 one. */
  availableBalanceCents: number | null
  spentThisWeekCents: number
  weeklyBudgetCents: number | null
  budgetUtilization: number | null
  paymentMethods: PaymentMethod[]
  recentActivity: LedgerEntry[]
  recentPayments: PaymentIntent[]
  openTransitSession?: TransitSession | null
}

export interface PaymentMethod {
  id: string
  name: string
  description: string
  status: 'AVAILABLE' | 'COMING_SOON'
}

/** Nullable fields mean "not derivable yet", never zero. */
export interface Insights {
  spentCents: number
  weeklyBudgetCents: number | null
  remainingCents: number | null
  budgetUtilization: number | null
  tripCount: number
  savedVersusFastestCents: number | null
  averageFareCents: number | null
  averageDurationMinutes: number | null
  cheapestProvider: string | null
  cheapestProviderName: string | null
  fastestProvider: string | null
  fastestProviderName: string | null
  minutesTradedForSavings: number | null
  projectedMonthlyCents: number | null
  spendingByProvider: ProviderBreakdown[]
  /** Null until the rider has a travel profile to personalise from. */
  personalization: InsightsPersonalization | null
}

/**
 * Figures that exist only because the rider answered onboarding.
 *
 * Every one is null unless the backend had every input it needed. `notes` carries
 * the sentences the backend built, including the assumption behind any projection
 * — the client never composes a financial claim of its own.
 */
export interface InsightsPersonalization {
  commuteFrequency: string | null
  commuteFrequencyName: string | null
  commuteDaysPerWeek: number | null
  typicalOriginName: string | null
  typicalDestinationName: string | null
  projectedWeeklySpendCents: number | null
  budgetBufferCents: number | null
  suggestedPassCode: string | null
  suggestedPassName: string | null
  suggestedPassSavingsCents: number | null
  notes: string[]
}

export interface ProviderBreakdown {
  provider: string
  providerName: string
  tripCount: number
  totalFareCents: number
  averageFareCents: number
  averageDurationMinutes: number
}

export type HistoryRange = '7d' | '30d' | '3m' | '1y'

export interface SpendingHistoryBucket {
  date: string
  label: string
  spentCents: number
  tripCount: number
  averageFareCents: number | null
  averageDurationMinutes: number | null
  savedCents: number | null
  cumulativeSpentCents: number
}

/** One stored completed trip, exposed for dashboard cross-filtering. */
export interface SpendingHistoryObservation {
  tripId: number
  takenAt: string
  /** Rider-local date, calculated by the backend from the user's timezone. */
  tripDate: string
  /** Rider-local day/week/month bucket matching the response granularity. */
  bucketDate: string
  provider: string
  providerName: string
  mode: string
  modeName: string
  origin: string
  destination: string
  fareCents: number
  durationMinutes: number
  /** Null when the selected trip had no comparable fastest alternative. */
  savedCents: number | null
  /** Null for trips completed before distance tracking was available. */
  distanceMetres: number | null
}

export interface SpendingHistory {
  range: HistoryRange
  rangeName: string
  granularity: 'DAY' | 'WEEK' | 'MONTH'
  startDate: string
  endDate: string
  hasData: boolean
  firstTripDate: string | null
  rangesWithData: HistoryRange[]
  weeklyBudgetCents: number | null
  totals: {
    spentCents: number
    tripCount: number
    averageFareCents: number | null
    averageDurationMinutes: number | null
    savedCents: number | null
    totalMinutes: number
    totalDistanceMetres: number | null
    costPerMileCents: number | null
    usagePricedTripCount: number
  }
  comparison: {
    startDate: string
    endDate: string
    spentCents: number
    tripCount: number
    averageFareCents: number | null
    spentChangeCents: number
    spentChangePercent: number | null
  } | null
  buckets: SpendingHistoryBucket[]
  byOperator: Array<{
    provider: string
    providerName: string
    tripCount: number
    spentCents: number
    averageFareCents: number
    shareOfSpend: number
  }>
  byMode: Array<{
    mode: string
    modeName: string
    tripCount: number
    spentCents: number
    shareOfSpend: number
  }>
  mostUsedRoutes: Array<{
    origin: string
    destination: string
    provider: string
    tripCount: number
    totalFareCents: number
    averageFareCents: number
  }>
  observations: SpendingHistoryObservation[]
}

export interface ApiUser {
  id: number
  name: string
  email: string
  /** Null when no budget is set. The UI prompts rather than showing $0.00. */
  weeklyBudgetCents: number | null
  timezone: string
  /** Drives the redirect to /onboarding without a second round trip. */
  onboardingCompleted: boolean
}

/** A place saved on the profile: resolved to coordinates, not left as free text. */
export interface TypicalPlace {
  name: string
  latitude: number
  longitude: number
  providerPlaceId: string | null
}

export type CommuteFrequencyId =
  | 'ONE_TO_TWO_DAYS' | 'THREE_TO_FOUR_DAYS' | 'FIVE_PLUS_DAYS' | 'VARIES'
export type CommuteKindId = 'WORK' | 'SCHOOL' | 'BOTH' | 'NONE'
export type PassPreferenceId = 'PAY_PER_RIDE' | 'WEEKLY_PASS' | 'MONTHLY_PASS' | 'NOT_SURE'
export type TravelModeId = 'TRAIN' | 'SUBWAY' | 'BUS' | 'FERRY'
export type FareCategoryId = 'REGULAR' | 'STUDENT' | 'SENIOR' | 'REDUCED'

export interface ModeOption {
  id: TravelModeId
  displayName: string
}

/**
 * The rider's travel profile.
 *
 * Enums arrive as an id plus a label. The client sends the id back and renders the
 * label, so the wording of every option lives in exactly one place: the backend.
 */
export interface TravelProfile {
  onboardingCompleted: boolean
  onboardingCompletedAt: string | null
  defaultContextProfile: ContextProfileOption['id']
  defaultContextProfileName: string
  weeklyCommuteFrequency: CommuteFrequencyId | null
  weeklyCommuteFrequencyName: string | null
  estimatedCommuteDaysPerWeek: number | null
  weeklyBudgetCents: number | null
  commuteKind: CommuteKindId | null
  commuteKindName: string | null
  typicalOrigin: TypicalPlace | null
  typicalDestination: TypicalPlace | null
  hasTypicalCommute: boolean
  passPreference: PassPreferenceId | null
  passPreferenceName: string | null
  fareCategory: FareCategoryId
  fareCategoryName: string
  preferredModes: ModeOption[]
}

/** What onboarding submits. A full replace: every screen renders every field. */
export interface TravelProfileInput {
  defaultContextProfile: ContextProfileOption['id']
  weeklyCommuteFrequency: CommuteFrequencyId | null
  /** Null is a real answer — "I'm not sure" — and clears the budget. */
  weeklyBudgetCents: number | null
  commuteKind: CommuteKindId | null
  typicalOrigin: TypicalPlace | null
  typicalDestination: TypicalPlace | null
  passPreference: PassPreferenceId | null
  fareCategory: FareCategoryId
  preferredModes: TravelModeId[]
}

export interface ProfileOption {
  id: string
  displayName: string
  detail: string | null
}

/** The vocabularies onboarding may offer, served so the client invents none. */
export interface ProfileOptions {
  contextProfiles: ContextProfileOption[]
  commuteFrequencies: ProfileOption[]
  commuteKinds: ProfileOption[]
  passPreferences: ProfileOption[]
  fareCategories: ProfileOption[]
  travelModes: ModeOption[]
}

export type RecommendationLabel = 'CHEAPEST' | 'FASTEST' | 'BEST_VALUE'

export interface ScoreBreakdown {
  normalizedFare: number
  normalizedTime: number
  normalizedTransfers: number
  fareContribution: number
  timeContribution: number
  transferContribution: number
}

export interface WeightsUsed {
  costPriority: number
  timePriority: number
  transferPriority: number
  source: 'DEFAULT' | 'PROFILE' | 'BUDGET_ADJUSTED' | 'AI_DERIVED'
  budgetPressure: number
}

/** A route's difference from a reference route, in the engine's own integer units. */
export interface RouteComparison {
  referenceRouteId: number
  referenceProvider: string
  /** Positive when this route costs more than the reference. */
  fareDeltaCents: number
  /** Positive when this route is slower than the reference. */
  minutesDelta: number
  transfersDelta: number
}

export interface RecommendedRoute {
  routeId: number
  provider: string
  providerName: string
  mode: string
  durationMinutes: number
  fareCents: number
  transfers: number
  labels: RecommendationLabel[]
  recommended: boolean
  score: number
  breakdown: ScoreBreakdown
  overBudget: boolean
  explanation: string
  vsFastest: RouteComparison | null
  vsBestValue: RouteComparison | null
  vsCheapest: RouteComparison | null
  geometry: RouteGeometry | null
}

export interface Waypoint {
  name: string
  latitude: number
  longitude: number
}

/**
 * A route's shape, for map rendering only.
 *
 * `source` matters: SCHEMATIC means straight segments between real published
 * station coordinates, not surveyed track geometry. Detailed transit journeys can
 * now carry Google Routes step polylines; this legacy catalog shape remains honest
 * about whether its own points are schematic or surveyed.
 */
export interface RouteGeometry {
  source: 'SCHEMATIC' | 'SURVEYED' | 'NONE'
  waypoints: Waypoint[]
}

/**
 * A selectable optimization stance. Weights are shown for transparency but never
 * sent back — the client posts only the `id`, and the backend owns the numbers.
 */
export interface ContextProfileOption {
  id: 'BALANCED' | 'RUSH' | 'SAVE_MONEY' | 'FEWER_TRANSFERS'
  displayName: string
  rationale: string
  costPriority: number
  timePriority: number
  transferPriority: number
}

export interface RecommendationResponse {
  origin: string
  destination: string
  profile: ContextProfileOption
  weightsUsed: WeightsUsed
  summary: string
  /** Non-null only when the chosen profile changed which route won. */
  contextNote: string | null
  options: RecommendedRoute[]
}

export interface Trip {
  id: number
  userId: number
  /** Null for trips taken from a discovered journey. */
  routeId: number | null
  /** Null for trips taken from a seeded route. */
  journeyId: number | null
  transitSessionId?: string | null
  origin: string
  destination: string
  provider: string
  providerName: string
  mode: string
  fareCents: number
  durationMinutes: number
  transfers: number
  distanceMetres?: number | null
  stopsTravelled?: number | null
  fareModel?: 'FIXED' | 'FAREFLOW_USAGE_V1' | 'FAREFLOW_USAGE_V2'
  selectedLabel: string
  baselineFareCents: number | null
  /** Null means "not computable", which is different from a computed zero. */
  savedVersusFastestCents: number | null
  status: 'COMPLETED' | 'CANCELLED'
  takenAt: string
}

export type LedgerEntryType = 'TRIP_CHARGE' | 'REFUND' | 'FARE_ADJUSTMENT'

export interface LedgerEntry {
  id: number
  userId: number
  tripId: number | null
  paymentIntentId: string | null
  type: LedgerEntryType
  /** Signed: negative is money out, positive is money in. */
  amountCents: number
  description: string
  occurredAt: string
  createdAt: string
}

export type PaymentStatus =
  | 'CREATED'
  | 'AUTHORIZED'
  | 'PROCESSING'
  | 'SETTLED'
  | 'FAILED'
  | 'REFUNDED'

export type PaymentRail = 'FAREFLOW_WALLET' | 'SIMULATED_CARD'

export interface PaymentEvent {
  id: number
  fromStatus: PaymentStatus | null
  toStatus: PaymentStatus
  reason: string
  occurredAt: string
}

export interface PaymentIntent {
  id: string
  transitSessionId?: string | null
  status: PaymentStatus
  paymentMethod: PaymentRail
  amountCents: number
  currency: 'USD'
  journeySummary: string
  origin: string
  destination: string
  attemptCount: number
  providerReference: string | null
  failureCode: string | null
  failureMessage: string | null
  trip: Trip | null
  authorizedAt: string | null
  processingAt: string | null
  settledAt: string | null
  failedAt: string | null
  refundedAt: string | null
  createdAt: string
  updatedAt: string
  events: PaymentEvent[]
}

export interface PaymentReconciliation {
  checkedAt: string
  countsByStatus: Record<PaymentStatus, number>
  settledCents: number
  issueCount: number
  issues: Array<{ paymentIntentId: string; status: PaymentStatus; detail: string }>
}

export interface Dashboard {
  user: ApiUser
  week: { startDate: string; startsAt: string; endsAt: string; timezone: string }
  spentCents: number
  remainingCents: number | null
  budgetUtilization: number | null
  tripCount: number
  savedVersusFastestCents: number | null
  recentTrips: Trip[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Locations {
  origins: string[]
  destinations: string[]
  sources: string[]
}
