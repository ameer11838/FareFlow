import { Wordmark } from '../../components/Logo'

/**
 * Split-screen shell for sign in and sign up.
 *
 * The left panel is a transit-inspired schematic drawn in SVG rather than a stock
 * photo or a live map: it needs no API key, no network, and no tiles, so the auth
 * screens render instantly and identically for everyone.
 */
export function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <div className="auth-brand-top">
          <Wordmark size={38} tone="light" />
        </div>

        <div className="auth-brand-copy">
          <h1 className="auth-headline">
            Plan smarter.<br />Spend less.<br />Get there on time.
          </h1>
          <p className="auth-sub">
            FareFlow scores every transit option on fare, travel time, and transfers —
            then tells you exactly what the trade-off costs.
          </p>

          <ul className="auth-points">
            <li><span className="auth-point-dot" />Cheapest, fastest, and best value on every route</li>
            <li><span className="auth-point-dot" />Weekly transportation budget that actually holds</li>
            <li><span className="auth-point-dot" />An append-only ledger behind every fare</li>
          </ul>
        </div>

        <AuthArtwork />
      </aside>

      <main className="auth-panel">
        <div className="auth-panel-inner">{children}</div>
      </main>
    </div>
  )
}

/** Decorative transit diagram: three corridors converging on a destination. */
function AuthArtwork() {
  return (
    <svg className="auth-art" viewBox="0 0 520 260" fill="none" aria-hidden="true">
      <defs>
        {/* The chosen corridor is drawn in the brand spectrum; the alternatives
            it beat stay grey, which is the whole idea in one picture. */}
        <linearGradient id="authRoute" x1="40" y1="210" x2="400" y2="70"
                        gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="var(--ff-grad-1)" />
          <stop offset="55%" stopColor="var(--ff-grad-3)" />
          <stop offset="100%" stopColor="var(--ff-grad-4)" />
        </linearGradient>
      </defs>
      <path d="M40 210 L150 210 L250 130 L400 130" stroke="rgba(255,255,255,0.16)" strokeWidth="3"
            strokeLinecap="round" strokeDasharray="8 7" />
      <path d="M40 210 L130 160 L260 190 L400 130" stroke="rgba(255,255,255,0.16)" strokeWidth="3"
            strokeLinecap="round" strokeDasharray="8 7" />
      <path d="M40 210 L170 100 L290 70 L400 130" stroke="url(#authRoute)" strokeWidth="3.5" strokeLinecap="round" />
      <circle cx="170" cy="100" r="5" fill="#100e1c" stroke="url(#authRoute)" strokeWidth="2.5" />
      <circle cx="290" cy="70" r="5" fill="#100e1c" stroke="url(#authRoute)" strokeWidth="2.5" />
      <circle cx="40" cy="210" r="9" fill="#100e1c" stroke="url(#authRoute)" strokeWidth="4" />
      <rect x="392" y="122" width="16" height="16" rx="3" fill="url(#authRoute)" />
    </svg>
  )
}
