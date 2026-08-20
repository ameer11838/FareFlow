import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api'
import { ApiError, setUnauthorizedHandler, tokenStore } from '../api/client'
import type { ApiUser, AuthConfig } from '../api/types'

interface AuthValue {
  /** Null while loading, or when signed out in auth mode. */
  user: ApiUser | null
  config: AuthConfig | null
  loading: boolean
  error: ApiError | null
  demoMode: boolean
  signIn: (email: string, password: string) => Promise<void>
  signUp: (input: { name: string; email: string; password: string }) => Promise<void>
  signOut: () => void
  refresh: () => void
  /** Applies a fresh user record after onboarding or a settings change. */
  setUser: (user: ApiUser) => void
  /**
   * Whether this rider still has onboarding to do.
   *
   * False in demo mode and while signed out — neither is a state onboarding
   * should interrupt.
   */
  needsOnboarding: boolean
}

const AuthContext = createContext<AuthValue | null>(null)

/**
 * One provider covering both modes.
 *
 * <p>The server's `/api/auth/config` is authoritative: the client's own
 * `VITE_AUTH_ENABLED` is only a hint for the initial render. If the two disagree
 * — a frontend built for auth pointed at a demo backend, say — the server wins and
 * the app still works.
 *
 * In demo mode `/api/auth/me` returns the seeded demo user with no token, so the
 * rest of the app never branches on which mode it is in.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<ApiUser | null>(null)
  const [config, setConfig] = useState<AuthConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [nonce, setNonce] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const serverConfig = await authApi.config()
      setConfig(serverConfig)

      if (!serverConfig.authEnabled) {
        // Demo mode: identity comes from the server, no token involved.
        setUser(await authApi.me())
        return
      }

      if (!tokenStore.get()) {
        setUser(null)
        return
      }

      try {
        setUser(await authApi.me())
      } catch {
        // A stale or rejected token just means "signed out".
        tokenStore.clear()
        setUser(null)
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, { title: String(caught) }))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, nonce])

  // A 401 from any request anywhere drops the session.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await authApi.login({ email, password })
    tokenStore.set(response.token)
    setUser(response.user)
  }, [])

  const signUp = useCallback(async (input: {
    name: string
    email: string
    password: string
  }) => {
    const response = await authApi.register(input)
    tokenStore.set(response.token)
    setUser(response.user)
  }, [])

  const signOut = useCallback(() => {
    // The token is stateless, so signing out is discarding it.
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo<AuthValue>(() => {
    const demoMode = config?.demoMode ?? false
    return {
      user,
      config,
      loading,
      error,
      demoMode,
      signIn,
      signUp,
      signOut,
      setUser,
      refresh: () => setNonce((n) => n + 1),
      // Demo mode arrives with a finished profile, so it never sees onboarding.
      needsOnboarding: !demoMode && user !== null && !user.onboardingCompleted,
    }
  }, [user, config, loading, error, signIn, signUp, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

/** Convenience for pages that only render once a user exists. */
export function useCurrentUser(): ApiUser | null {
  return useAuth().user
}
