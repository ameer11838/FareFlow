import { api } from './client'
import type {
  ApiUser,
  PersistedJourneyDetail,
  JourneySearchResponse,
  LocationCandidate,
  PassRecommendation,
  AuthConfig,
  AuthResponse,
  ContextProfileOption,
  Dashboard,
  ProfileOptions,
  TravelProfile,
  TravelProfileInput,
  Insights,
  LedgerEntry,
  Locations,
  Page,
  RecommendationResponse,
  Trip,
  Wallet,
} from './types'

export const authApi = {
  /** The server's mode. Authoritative over the client's own build flag. */
  config: () => api.get<AuthConfig>('/api/auth/config'),
  /**
   * Registration takes a name, an email, and a password. Nothing else.
   *
   * The budget and every travel preference are collected during onboarding,
   * where there is room to explain why FareFlow is asking.
   */
  register: (body: { name: string; email: string; password: string }) =>
    api.post<AuthResponse>('/api/auth/register', body),
  login: (body: { email: string; password: string }) =>
    api.post<AuthResponse>('/api/auth/login', body),
  me: () => api.get<ApiUser>('/api/auth/me'),
}

export const usersApi = {
  me: () => api.get<ApiUser>('/api/users/me'),
  updateBudget: (weeklyBudgetCents: number) =>
    api.patch<ApiUser>('/api/users/me/budget', { weeklyBudgetCents }),
}

/**
 * The rider's travel profile.
 *
 * No userId anywhere: the backend derives identity from the token, so there is no
 * shape in which this client could ask for someone else's profile.
 *
 * `completeOnboarding` and `update` send the same document to different resources.
 * The first finishes onboarding; the second is the ongoing settings edit and
 * leaves that flag alone, so nobody is ever made to repeat onboarding.
 */
export const profileApi = {
  options: () => api.get<ProfileOptions>('/api/profile/options'),
  get: () => api.get<TravelProfile>('/api/profile'),
  update: (body: TravelProfileInput) => api.put<TravelProfile>('/api/profile', body),
  completeOnboarding: (body: TravelProfileInput) =>
    api.put<TravelProfile>('/api/onboarding', body),
}

export const routesApi = {
  locations: () => api.get<Locations>('/api/transit-routes/locations'),
}

export const recommendationsApi = {
  profiles: () => api.get<ContextProfileOption[]>('/api/recommendations/profiles'),

  search: (origin: string, destination: string, profile?: string, userId?: number) => {
    const params = new URLSearchParams({ origin, destination })
    if (profile) params.set('profile', profile)
    // Only used to apply budget pressure; it grants no access to private data.
    if (userId !== undefined) params.set('userId', String(userId))
    return api.get<RecommendationResponse>(`/api/recommendations?${params}`)
  },
}

export const locationsApi = {
  search: (query: string, limit = 6) =>
    api.get<LocationCandidate[]>(
      `/api/locations?q=${encodeURIComponent(query)}&limit=${limit}`),
}

export const journeysApi = {
  /** Arbitrary origin to destination — neither has to be a seeded pair. */
  search: (from: string, to: string, profile?: string) => {
    const params = new URLSearchParams({ from, to })
    if (profile) params.set('profile', profile)
    return api.get<JourneySearchResponse>(`/api/journeys?${params}`)
  },

  /**
   * Takes a discovered journey.
   *
   * No fare is sent: the server re-prices the journey and charges its own number.
   * The idempotency key makes a double-submitted Choose safe.
   */
  take: (
    body: { from: string; to: string; journeyId: string; confirmUnknownFare?: boolean },
    idempotencyKey: string,
  ) => api.post<Trip>('/api/journeys/take', body, { 'Idempotency-Key': idempotencyKey }),

  detail: (journeyId: number) =>
    api.get<PersistedJourneyDetail>(`/api/journeys/${journeyId}`),
}

export const passesApi = {
  recommendation: () => api.get<PassRecommendation>('/api/passes/recommendation'),
}

export const tripsApi = {
  take: (body: { routeId: number; selectedLabel?: string }) =>
    api.post<Trip>('/api/trips', body),
  cancel: (tripId: number) => api.post<Trip>(`/api/trips/${tripId}/cancel`),
  list: (page = 0, size = 20) =>
    api.get<Page<Trip>>(`/api/trips?page=${page}&size=${size}`),
}

export const ledgerApi = {
  list: (page = 0, size = 25) =>
    api.get<Page<LedgerEntry>>(`/api/ledger?page=${page}&size=${size}`),
}

export const dashboardApi = {
  get: () => api.get<Dashboard>('/api/dashboard'),
}

export const insightsApi = {
  get: () => api.get<Insights>('/api/insights'),
}

export const walletApi = {
  get: () => api.get<Wallet>('/api/wallet'),
}
