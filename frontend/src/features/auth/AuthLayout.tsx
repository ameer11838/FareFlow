import { Wordmark } from '../../components/Logo'

/**
 * The shell for sign in and sign up.
 *
 * <p>Deep navy with ambient brand light rather than a photograph or a live map:
 * it needs no API key, no network, and no tiles, so the first screen anyone sees
 * renders instantly and identically for everyone. The artwork is the product's
 * own idea drawn in one picture — three corridors between the same two points,
 * with the chosen one lit.
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
            Travel smarter.<br />Spend better.
          </h1>
          <p className="auth-sub">
            FareFlow finds transportation options based on your time, budget, and
            travel preferences — then tells you exactly what each trade-off costs.
          </p>

          <ul className="auth-points">
            <li><span className="auth-point-dot" />Cheapest, fastest, and best value on every route</li>
            <li><span className="auth-point-dot" />A weekly transportation budget that actually holds</li>
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

/** Three corridors converging on one destination; the chosen one is lit. */
function AuthArtwork() {
  return (
    <svg className="auth-art" viewBox="0 0 520 260" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="authRoute" x1="40" y1="210" x2="400" y2="70"
                        gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="var(--ff-cyan)" />
          <stop offset="55%" stopColor="var(--ff-violet)" />
          <stop offset="100%" stopColor="var(--ff-magenta)" />
        </linearGradient>
      </defs>

      <path d="M40 210 L150 210 L250 130 L400 130" stroke="rgba(255,255,255,0.13)" strokeWidth="3"
            strokeLinecap="round" strokeDasharray="8 7" />
      <path d="M40 210 L130 160 L260 190 L400 130" stroke="rgba(255,255,255,0.13)" strokeWidth="3"
            strokeLinecap="round" strokeDasharray="8 7" />
      <path d="M40 210 L170 100 L290 70 L400 130" stroke="url(#authRoute)" strokeWidth="3.5"
            strokeLinecap="round" />
      <circle cx="170" cy="100" r="5" fill="var(--ff-navy-950)" stroke="url(#authRoute)" strokeWidth="2.5" />
      <circle cx="290" cy="70" r="5" fill="var(--ff-navy-950)" stroke="url(#authRoute)" strokeWidth="2.5" />
      <circle cx="40" cy="210" r="9" fill="var(--ff-navy-950)" stroke="url(#authRoute)" strokeWidth="4" />
      <rect x="392" y="122" width="16" height="16" rx="3" fill="url(#authRoute)" />
    </svg>
  )
}
