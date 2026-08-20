/**
 * The only place `fetch` is called.
 *
 * Backend errors arrive as RFC 9457 Problem Details, so there is exactly one
 * error shape to parse and one error type for the UI to render.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const TOKEN_KEY = 'fareflow.token'

/**
 * The token lives in localStorage. That is XSS-readable, which is the accepted
 * trade-off for a stateless JWT API with no cookie/CSRF machinery; the real fix is
 * an httpOnly refresh cookie, which is a later phase.
 */
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/** Notified on a 401 so the app can drop to the login screen from anywhere. */
let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

export interface ProblemDetail {
  /** Machine-readable signal, e.g. FARE_CONFIRMATION_REQUIRED. */
  code?: string
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** Field-level validation messages, when the backend supplied them. */
  get fieldErrors(): Record<string, string> {
    return this.problem.errors ?? {}
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    const token = tokenStore.get()
    response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init?.headers ?? {}),
      },
    })
  } catch {
    // Network-level failure: the server is unreachable, not returning an error.
    throw new ApiError(0, {
      title: 'Cannot reach the server',
      detail: 'The FareFlow backend is not responding. Is it running on ' + BASE_URL + '?',
    })
  }

  if (!response.ok) {
    // An expired or rejected token invalidates the whole session, wherever we are.
    if (response.status === 401) {
      tokenStore.clear()
      onUnauthorized?.()
    }

    let problem: ProblemDetail = { title: 'Request failed', status: response.status }
    try {
      problem = { ...problem, ...(await response.json()) }
    } catch {
      // Non-JSON error body; keep the generic problem.
    }
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
      headers,
    }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
}
