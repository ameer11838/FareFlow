import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { formatCents } from '../lib/format'
import { DashboardIcon, LedgerIcon, RouteIcon, SettingsIcon, TripsIcon, WalletIcon } from './Icons'
import { Logo } from './Logo'

export const NAV_ITEMS = [
  { to: '/plan', label: 'Plan', Icon: RouteIcon, primary: true },
  { to: '/wallet', label: 'Wallet', Icon: WalletIcon },
  { to: '/trips', label: 'Trips', Icon: TripsIcon },
  { to: '/insights', label: 'Insights', Icon: DashboardIcon },
  { to: '/ledger', label: 'Ledger', Icon: LedgerIcon },
  { to: '/settings', label: 'Settings', Icon: SettingsIcon },
]

/**
 * Top navigation rather than a sidebar.
 *
 * A 56px bar costs the map 56px of height; a 248px sidebar cost it 248px of width.
 * Since the map is the product, the bar wins. The same chrome is used on every
 * page so navigation does not shift between sections.
 */
export function AppShell() {
  return (
    <div className="app-shell">
      <TopBar />
      <main className="app-main">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  )
}

export function TopBar({ compact = false }: { compact?: boolean }) {
  const { user, demoMode, signOut, config } = useAuth()

  return (
    <header className={`topbar${compact ? ' topbar-compact' : ''}`}>
      <NavLink to="/plan" className="topbar-brand">
        <Logo size={30} />
        <span className="topbar-name">FareFlow</span>
      </NavLink>

      <nav className="topbar-nav" aria-label="Main">
        {NAV_ITEMS.map(({ to, label, Icon, primary }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `topbar-link${isActive ? ' active' : ''}${primary ? ' primary' : ''}`}
          >
            <Icon size={16} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="topbar-right">
        {demoMode && (
          <span className="demo-chip" title={`Demo identity: ${config?.demoUserName ?? 'demo user'}`}>
            Demo mode
          </span>
        )}

        {user && (
          <>
            {user.weeklyBudgetCents === null ? (
              <NavLink className="topbar-budget-unset" to="/settings"
                       title="Set a weekly transportation budget">
                Set a<span className="long"> weekly</span> budget
              </NavLink>
            ) : (
              <span className="topbar-budget numeric" title="Weekly transportation budget">
                {formatCents(user.weeklyBudgetCents)}<span className="muted"> / wk</span>
              </span>
            )}
            <span className="avatar" aria-hidden="true">{initials(user.name)}</span>
            {!demoMode && (
              <button className="btn btn-sm btn-ghost topbar-signout" onClick={signOut}>
                Sign out
              </button>
            )}
          </>
        )}
      </div>
    </header>
  )
}

/** Mobile only; hidden at desktop widths by CSS. */
export function BottomNav() {
  return (
    <nav className="bottom-nav" aria-label="Sections">
      {NAV_ITEMS.map(({ to, label, Icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `bottom-nav-link${isActive ? ' active' : ''}`}
        >
          <Icon size={19} />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

function initials(name: string): string {
  return name.split(/\s+/).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('')
}
