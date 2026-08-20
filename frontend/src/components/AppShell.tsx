import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { formatCents } from '../lib/format'
import {
  DashboardIcon, LedgerIcon, RouteIcon, SettingsIcon, TripsIcon, WalletIcon,
} from './Icons'
import { Logo } from './Logo'

export const NAV_ITEMS = [
  { to: '/plan', label: 'Plan', Icon: RouteIcon },
  { to: '/wallet', label: 'Wallet', Icon: WalletIcon },
  { to: '/trips', label: 'Trips', Icon: TripsIcon },
  { to: '/insights', label: 'Insights', Icon: DashboardIcon },
  { to: '/ledger', label: 'Ledger', Icon: LedgerIcon },
  { to: '/settings', label: 'Settings', Icon: SettingsIcon },
]

/**
 * Top navigation rather than a sidebar.
 *
 * <p>A 64px bar costs the map 64px of height; a 248px sidebar cost it 248px of
 * width. Since the map is the product, the bar wins. The same chrome appears on
 * every page, so navigation never shifts between sections.
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
      <NavLink to="/plan" className="topbar-brand" aria-label="FareFlow home">
        <Logo size={32} />
        <span className="topbar-name">FareFlow</span>
      </NavLink>

      <nav className="topbar-nav" aria-label="Main">
        {NAV_ITEMS.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => `topbar-link${isActive ? ' active' : ''}`}
          >
            {/*
              The active marker is a rule under the label rather than a filled
              pill. Six filled pills in a row is a toolbar; one lit underline is
              navigation, and it leaves the labels at one weight so the bar stays
              quiet behind the page.
            */}
            <Icon size={17} />
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
          <div className="topbar-user">
            {/* The budget is the number a rider glances up at, so it is set as a
                figure with a unit rather than as another label. */}
            {user.weeklyBudgetCents === null ? (
              <NavLink className="topbar-budget-unset" to="/settings"
                       title="Set a weekly transportation budget">
                Set a<span className="long"> weekly</span> budget
              </NavLink>
            ) : (
              <NavLink to="/wallet" className="topbar-budget" title="Weekly transportation budget">
                <span className="topbar-budget-value numeric">
                  {formatCents(user.weeklyBudgetCents)}
                </span>
                <span className="topbar-budget-unit">/ week</span>
              </NavLink>
            )}

            <span className="topbar-divider" aria-hidden="true" />

            <NavLink to="/settings" className="topbar-account" title={user.email}>
              <span className="avatar" aria-hidden="true">{initials(user.name)}</span>
              <span className="topbar-account-name">{firstName(user.name)}</span>
            </NavLink>

            {!demoMode && (
              <button className="topbar-signout" onClick={signOut} title="Sign out">
                Sign out
              </button>
            )}
          </div>
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
          <span className="bottom-nav-icon"><Icon size={19} /></span>
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

function initials(name: string): string {
  return name.split(/\s+/).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('')
}

function firstName(name: string): string {
  return name.split(/\s+/)[0]
}
