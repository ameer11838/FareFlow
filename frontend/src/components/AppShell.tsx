import { useEffect, useId, useRef, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { formatCents } from '../lib/format'
import {
  DashboardIcon, FareFlowGuideIcon, LogoutIcon, PaymentHistoryIcon, RouteIcon,
  SettingsIcon, TripsIcon, WalletIcon,
} from './Icons'
import { Logo } from './Logo'
import { ThemeButton } from './ThemeToggle'

/**
 * Five destinations, and Ask FareFlow is one of them.
 *
 * <p>The assistant used to be a floating button parked over the bottom-right
 * corner of every screen. That placement says "chat widget" — the thing a SaaS
 * site bolts on for support — rather than "part of the product". It is the only
 * surface that can answer a question spanning routes, spending and budget at
 * once, so it belongs in the navigation with everything else.
 *
 * <p>Wallet and Settings are deliberately <em>not</em> peers here. A wallet
 * balance is a figure you glance at, so it is rendered as a figure in the bar;
 * settings is account admin, so it lives behind the avatar. Six flat nav items
 * with no hierarchy is how the old bar became unreadable.
 */
export const NAV_ITEMS = [
  { to: '/plan', label: 'Plan', mobileLabel: 'Plan', Icon: RouteIcon },
  { to: '/trips', label: 'Trips', mobileLabel: 'Trips', Icon: TripsIcon },
  { to: '/payments', label: 'Payments', mobileLabel: 'Payments', Icon: PaymentHistoryIcon },
  { to: '/insights', label: 'Insights', mobileLabel: 'Insights', Icon: DashboardIcon },
  { to: '/assistant', label: 'Ask FareFlow', mobileLabel: 'Ask', Icon: FareFlowGuideIcon },
]

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
  const { user, demoMode } = useAuth()

  return (
    <header className={`topbar${compact ? ' topbar-compact' : ''}`}>
      <NavLink to="/plan" className="topbar-brand" aria-label="FareFlow home">
        <Logo size={26} />
        <span className="topbar-name">FareFlow</span>
      </NavLink>

      <nav className="topbar-nav" aria-label="Main">
        {NAV_ITEMS.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => `navtab${isActive ? ' is-active' : ''}`}
          >
            {({ isActive }) => (
              <>
                {/* Selection is carried by stroke weight as well as colour, so
                    it survives being read in greyscale. */}
                <Icon size={17} strokeWidth={isActive ? 2 : 1.5} />
                <span>{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="topbar-right">
        {demoMode && <span className="demo-chip">Demo mode</span>}

        {/* The wallet is a figure, not a nav item: "what have I got to spend" is
            a question riders ask while doing something else. */}
        {user && (
          <NavLink to="/wallet" className="wallet-chip" aria-label="Wallet and weekly budget">
            <WalletIcon size={15} />
            <span className="numeric">
              {user.weeklyBudgetCents === null
                ? 'Set budget' : formatCents(user.weeklyBudgetCents)}
            </span>
          </NavLink>
        )}

        {user && <AccountMenu />}
      </div>
    </header>
  )
}

/**
 * The account menu: identity, appearance, settings, and the way out.
 *
 * <p>Closes on outside-click, on Escape and on navigation. A menu still hanging
 * open over the next screen is the kind of bug people blame the whole app for.
 */
function AccountMenu() {
  const { user, demoMode, signOut } = useAuth()
  const [open, setOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const navigate = useNavigate()

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!wrapRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  if (!user) return null

  const go = (to: string) => { setOpen(false); navigate(to) }

  return (
    <div className="account" ref={wrapRef}>
      <button
        type="button"
        className={`account-trigger${open ? ' is-open' : ''}`}
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-controls={menuId}
        aria-label={`Account: ${user.name}`}
      >
        <span className="avatar" aria-hidden="true">{initials(user.name)}</span>
      </button>

      {open && (
        <div className="menu" id={menuId} role="menu">
          <div className="menu-identity">
            <strong>{user.name}</strong>
            <small>{user.email}</small>
          </div>

          <div className="menu-row">
            <span>Appearance</span>
            <ThemeButton />
          </div>

          <div className="menu-group">
            <button type="button" role="menuitem" className="menu-item"
                    onClick={() => go('/wallet')}>
              <WalletIcon size={16} /><span>Wallet</span>
            </button>
            <button type="button" role="menuitem" className="menu-item"
                    onClick={() => go('/settings')}>
              <SettingsIcon size={16} /><span>Settings</span>
            </button>
            {demoMode ? (
              <p className="menu-note">Demo identity — no session to sign out of.</p>
            ) : (
              <button type="button" role="menuitem" className="menu-item menu-item-danger"
                      onClick={signOut}>
                <LogoutIcon size={16} /><span>Sign out</span>
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

/** Mobile only; hidden at desktop widths by CSS. */
export function BottomNav() {
  return (
    <nav className="bottom-nav" aria-label="Sections">
      {NAV_ITEMS.map(({ to, mobileLabel, Icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `bottom-nav-link${isActive ? ' active' : ''}`}
        >
          {({ isActive }) => (
            <>
              <Icon size={20} strokeWidth={isActive ? 2 : 1.5} />
              {/* No aria-label here: the visible word is the accessible name, and
                  duplicating the full label collided with the composer's. */}
              <span>{mobileLabel}</span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}

function initials(name: string): string {
  return name.split(/\s+/).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('')
}
