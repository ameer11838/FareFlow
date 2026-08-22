import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { ErrorState, LoadingState } from './components/states'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { InsightsPage } from './features/insights/InsightsPage'
import { LedgerPage } from './features/ledger/LedgerPage'
import { OnboardingPage } from './features/onboarding/OnboardingPage'
import { PlanTripPage } from './features/plan/PlanTripPage'
import { SettingsPage } from './features/settings/SettingsPage'
import { TripHistoryPage } from './features/trips/TripHistoryPage'
import { WalletPage } from './features/wallet/WalletPage'
import { useAuth } from './hooks/useAuth'

export function App() {
  const { loading, error, refresh } = useAuth()

  if (loading) {
    return (
      <div className="boot">
        <LoadingState label="Connecting to FareFlow" />
      </div>
    )
  }

  // A backend that is down is not an auth problem; say so rather than showing login.
  if (error) {
    return (
      <div className="boot">
        <div className="card" style={{ maxWidth: 480 }}>
          <ErrorState error={error} onRetry={refresh} />
        </div>
      </div>
    )
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Signed in, but before the app proper: it owns the full viewport. */}
      <Route element={<RequireUser />}>
        <Route path="/onboarding" element={<OnboardingPage />} />
      </Route>

      <Route element={<RequireOnboardedUser />}>
        {/* Plan owns the full viewport: it carries its own chrome. */}
        <Route path="/plan" element={<PlanTripPage />} />

        <Route element={<AppShell />}>
          <Route path="/wallet" element={<WalletPage />} />
          <Route path="/trips" element={<TripHistoryPage />} />
          <Route path="/insights" element={<InsightsPage />} />
          <Route path="/payments" element={<LedgerPage />} />
          <Route path="/ledger" element={<Navigate to="/payments" replace />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/plan" replace />} />
    </Routes>
  )
}

/**
 * Guard for everything private.
 *
 * In demo mode the server supplies a user with no token, so this passes through
 * without a login screen ever rendering. In auth mode a signed-out visitor is sent
 * to /login with the URL they wanted, so they land back on it after signing in.
 */
function RequireUser() {
  const { user, demoMode } = useAuth()
  const location = useLocation()

  if (!user && !demoMode) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/**
 * Guard for the app itself: signed in <em>and</em> finished onboarding.
 *
 * A rider who has not answered anything yet would see a Plan screen with no
 * commute shortcut, a wallet with no budget, and insights with nothing to
 * personalise — so they are sent to onboarding first, once. Demo mode arrives
 * with a completed profile and never sees this redirect.
 */
function RequireOnboardedUser() {
  const { user, demoMode, needsOnboarding } = useAuth()
  const location = useLocation()

  if (!user && !demoMode) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (needsOnboarding) {
    return <Navigate to="/onboarding" replace />
  }
  return <Outlet />
}
