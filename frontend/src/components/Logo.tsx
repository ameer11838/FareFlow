import { useId } from 'react'

/**
 * The FareFlow app mark: an F whose lower arm sweeps into a rail corridor, with a
 * train emerging from the curve — the product in one glyph, a route and a fare.
 *
 * <p>Drawn as SVG rather than imported as a raster so it stays sharp at every size
 * from a 22px tab favicon to a 96px auth panel, needs no icon-library dependency,
 * and inherits nothing from the network.
 *
 * <p>The gradient is defined per instance with a generated id. Two logos on one
 * page (the top bar and a footer, say) would otherwise share one `<defs>` id, and
 * whichever unmounted first would take the fill with it — a genuinely confusing
 * bug to chase.
 */
export function Logo({ size = 32, plate = true }: {
  size?: number
  /** False renders the glyph alone, for use on an already-branded surface. */
  plate?: boolean
}) {
  const id = useId()
  const gradientId = `ff-brand-${id}`

  // On the plate the glyph is knocked out in white and the train's window and
  // lamps are cut back to the gradient beneath. Without the plate there is no
  // "beneath", so the glyph itself carries the gradient and the cutouts go clear.
  const glyphFill = plate ? '#fff' : `url(#${gradientId})`
  const cutoutFill = plate ? `url(#${gradientId})` : 'transparent'

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      aria-hidden="true"
      className="ff-logo"
    >
      <defs>
        <linearGradient id={gradientId} x1="4" y1="4" x2="60" y2="60"
                        gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="var(--ff-grad-1, #22d3ee)" />
          <stop offset="38%" stopColor="var(--ff-grad-2, #4f6bf6)" />
          <stop offset="72%" stopColor="var(--ff-grad-3, #8b3ff0)" />
          <stop offset="100%" stopColor="var(--ff-grad-4, #e935d6)" />
        </linearGradient>
      </defs>

      {plate && <rect width="64" height="64" rx="15" fill={`url(#${gradientId})`} />}

      {/*
        The F. The upper arm sweeps out to the right like a line pulling away from
        a platform, and the stem falls into a taper — the letter reads as motion
        rather than as a serif.
      */}
      <path
        d="M49.5 13.5H32.2c-5.6 0-9.4 3.1-11 9.2L13 52.5c3.6-.4 6.2-2.5 7.3-6.6l6.1-23.2
           c.7-2.7 2.2-3.9 4.9-3.9h10.3c3.6 0 5.9-1.9 7.9-5.3z"
        fill={glyphFill}
      />

      {/* The middle arm, cut on the same angle so the two arms rhyme. */}
      <path
        d="M44.6 29.6H24.9c-.8 3 .4 4.6 3.3 4.6h9.8c2.9 0 5.2-1.5 6.6-4.6z"
        fill={glyphFill}
      />

      {/* The corridor: a rail curving in behind the stem and around the train. */}
      <path
        d="M23.2 44.6c-1.1-7.9 3.4-14.6 11.4-15.4 3.3-.3 6.6-.1 9.9-.1
           c-3 1.9-6.3 2.6-9.7 3.1-5.5.8-8.9 4.9-8.6 10.4.1 1.9.5 3.7 1.2 5.6
           -2.1-.9-3.6-2-4.2-3.6z"
        fill={glyphFill}
        opacity={plate ? 0.92 : 1}
      />

      {/*
        The train, nosing out from behind the stem. Kept to a cab, a window, and
        two lamps: any more detail turns to mud below 24px.
      */}
      <path
        d="M34.9 36.4h9.6c2.9 0 4.6 1.7 5 4.5l1.3 8.3c.3 2.3-1 3.7-3.3 3.7H33.6
           c-2.3 0-3.8-1.4-3.7-3.7l.6-8.3c.2-2.8 1.7-4.5 4.4-4.5z"
        fill={glyphFill}
      />
      <rect x="34.1" y="39.6" width="11.4" height="6.5" rx="1.9" fill={cutoutFill} />
      <circle cx="34.9" cy="49.4" r="1.5" fill={cutoutFill} />
      <circle cx="44.7" cy="49.4" r="1.5" fill={cutoutFill} />
    </svg>
  )
}

/**
 * The wordmark: the glyph plus "FareFlow" set as one unit.
 *
 * <p>Exists so the lockup — mark size, gap, weight, letter-spacing — is defined
 * once instead of being re-approximated in the top bar, the auth screens, and the
 * onboarding header, which is how a wordmark quietly ends up in three sizes.
 */
export function Wordmark({ size = 30, tone = 'dark' }: {
  size?: number
  /** 'light' for the ink navigation, 'dark' for pale surfaces. */
  tone?: 'light' | 'dark'
}) {
  return (
    <span className={`ff-wordmark ff-wordmark-${tone}`}>
      <Logo size={size} />
      <span className="ff-wordmark-text" style={{ fontSize: Math.round(size * 0.52) }}>
        FareFlow
      </span>
    </span>
  )
}
