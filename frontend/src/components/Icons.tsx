/**
 * FareFlow's icon system.
 *
 * <p>One geometry, drawn to a single specification, so a row of icons reads as
 * one family rather than as glyphs collected from three libraries:
 *
 * <ul>
 *   <li><b>24×24 grid</b> for every icon, with the artwork living inside a 20×20
 *       optical square. Icons that fill the full 24 look heavier than their
 *       neighbours at the same nominal size, which is the usual reason an icon
 *       row looks subtly wrong without anyone being able to say why.</li>
 *   <li><b>1.5 stroke</b>, round cap, round join, no fills. A filled icon beside
 *       a stroked one always wins the eye, so "filled" is reserved for the one
 *       state that should win: selected navigation.</li>
 *   <li><b>Geometry on the half-pixel grid</b> (x.5 coordinates for 1.5 strokes)
 *       so edges land on device pixels instead of straddling two.</li>
 * </ul>
 *
 * <p>Every icon takes the same props and inherits <code>currentColor</code>, so
 * colour and active state are decided by the surface that renders it, never
 * baked into the artwork.
 *
 * <p>Transit modes are drawn from the front of the vehicle rather than the side.
 * A side-on bus and a side-on train are two rectangles with different window
 * counts and are genuinely hard to tell apart at 18px; head-on, the silhouettes
 * separate immediately.
 */
export interface IconProps {
  /** Rendered size in px. The stroke does not scale, by design — see below. */
  size?: number
  className?: string
  /**
   * Heavier stroke for selected states. Scaling the stroke with the size would
   * make a 32px icon look like a different typeface's weight than an 18px one;
   * keeping it fixed is what holds the set together across sizes.
   */
  strokeWidth?: number
}

const svg = (size: number, strokeWidth: number, className?: string) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
  focusable: false,
  className,
})

type Icon = (props: IconProps) => React.ReactElement

/** Builds an icon component from its paths, so every icon shares one contract. */
function icon(children: React.ReactNode): Icon {
  return function FareFlowIcon({ size = 20, className, strokeWidth = 1.5 }: IconProps) {
    return <svg {...svg(size, strokeWidth, className)}>{children}</svg>
  }
}

/* ==========================================================================
   NAVIGATION
   The five destinations. Each is a noun a rider would recognise, not an
   abstraction: a route, a journey history, a chart, a card, a conversation.
   ========================================================================== */

/** Plan — a route pinned between two points. */
export const RouteIcon = icon(<>
  <circle cx="6" cy="17.5" r="2.25" />
  <circle cx="18" cy="6.5" r="2.25" />
  <path d="M8.25 17.5h4.25a4 4 0 0 0 4-4v-4.75" />
</>)

/** Trips — a journey line with a start and an end. */
export const TripsIcon = icon(<>
  <circle cx="6.5" cy="6.5" r="2" />
  <circle cx="17.5" cy="17.5" r="2" />
  <path d="M8.5 6.5h5a3.5 3.5 0 0 1 0 7h-3a3.5 3.5 0 0 0 0 7h5" />
</>)

/** Insights — bars with a trend, the two things the section actually shows. */
export const DashboardIcon = icon(<>
  <path d="M3.5 20.5h17" />
  <path d="M6.5 20.5v-5M12 20.5v-9M17.5 20.5v-13" />
</>)

/** Payments — a receipt with a torn edge. */
export const PaymentHistoryIcon = icon(<>
  <path d="M6 3.5h12v17l-2.4-1.6-2.4 1.6-2.4-1.6-2.4 1.6L6 18.9Z" />
    <path d="M9.5 8.5h5M9.5 12.5h5" />
</>)

/** Ask FareFlow — a conversation, with the route dot that marks it as ours. */
export const FareFlowGuideIcon = icon(<>
  <path d="M20.5 12.5a7.5 7.5 0 0 1-7.5 7.5H4.5l1.9-2.9A7.5 7.5 0 1 1 20.5 12.5Z" />
  <circle cx="9.5" cy="12" r="1.15" />
  <path d="M11.75 12h3.25" />
</>)

/* ==========================================================================
   MONEY
   ========================================================================== */

export const WalletIcon = icon(<>
  <path d="M20.5 9.5V18a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V6.5" />
  <path d="M3.5 6.5a2 2 0 0 1 2-2h11.5v5" />
  <path d="M20.5 9.5h-4a2.5 2.5 0 0 0 0 5h4" />
</>)

export const CardIcon = icon(<>
  <rect x="3.5" y="5.5" width="17" height="13" rx="2.5" />
  <path d="M3.5 10h17M7 14.5h3" />
</>)

export const RefundIcon = icon(<>
  <path d="M4 11.5a8 8 0 1 1 2.4 5.7" />
  <path d="M3.5 6v5.5H9" />
</>)

/* ==========================================================================
   TRANSIT MODES
   Head-on silhouettes: a train has a roof pantograph line, a subway a tunnel
   arch, a bus a flat cab with a destination blind, a ferry a hull on water.
   ========================================================================== */

export const RailIcon = icon(<>
  <rect x="6.5" y="3.5" width="11" height="12" rx="2.5" />
  <path d="M6.5 10h11" />
  <path d="M9.75 12.9h.01M14.25 12.9h.01" />
  <path d="M9 18.5h6M7.5 21h9" />
</>)

export const SubwayIcon = icon(<>
  <path d="M6 10a6 6 0 0 1 12 0v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2Z" />
  <path d="M6 13.5h12" />
  <path d="M9.5 16.75h.01M14.5 16.75h.01" />
</>)

export const BusIcon = icon(<>
  <rect x="4.5" y="4" width="15" height="12.5" rx="2.5" />
  <path d="M4.5 11h15" />
  <circle cx="8.25" cy="19" r="1.4" />
  <circle cx="15.75" cy="19" r="1.4" />
</>)

export const TramIcon = icon(<>
  <rect x="6.5" y="6" width="11" height="13" rx="2.5" />
  <path d="M6.5 12.5h11" />
  <path d="M9.75 15.75h.01M14.25 15.75h.01" />
  <path d="M12 6V2.75M9.25 2.75h5.5" />
</>)

export const FerryIcon = icon(<>
  <path d="M4.5 12.5 12 10.25l7.5 2.25-1.5 4.6a2 2 0 0 1-1.9 1.4H7.9a2 2 0 0 1-1.9-1.4Z" />
  <path d="M8 11.6V7.5h8v4.1M12 4.25V7.5" />
  <path d="M3 21.5c1.5 0 1.5-1 3-1s1.5 1 3 1 1.5-1 3-1 1.5 1 3 1 1.5-1 3-1" />
</>)

export const WalkIcon = icon(<>
  <circle cx="13" cy="4.75" r="1.75" />
  <path d="M11 21.5 12.5 15l-2.5-2.25V9a2 2 0 0 1 2.6-1.9l2.4.8 2.5 2.85" />
  <path d="m12.5 15 3 2.25 1 4.25M10 12.75 7 14.5l-1.5 3" />
</>)

/** Resolves the API's mode string to its icon. Unknown modes fall back to bus. */
export function ModeIcon({ mode, size = 18, className, strokeWidth }: IconProps & { mode: string }) {
  const props = { size, className, strokeWidth }
  switch (mode?.toUpperCase()) {
    case 'RAIL':
    case 'TRAIN':
    case 'HEAVY_RAIL':
      return <RailIcon {...props} />
    case 'SUBWAY':
    case 'METRO':
      return <SubwayIcon {...props} />
    case 'TRAM':
    case 'LIGHT_RAIL':
      return <TramIcon {...props} />
    case 'FERRY':
      return <FerryIcon {...props} />
    case 'WALK':
    case 'WALKING':
      return <WalkIcon {...props} />
    case 'BUS':
    default:
      return <BusIcon {...props} />
  }
}

/* ==========================================================================
   JOURNEY STRUCTURE
   The vocabulary a transit product needs and a generic icon set does not have.
   ========================================================================== */

/** A stop on a line — a ring on a rule. */
export const StopIcon = icon(<>
  <path d="M12 2.5v6M12 15.5v6" />
  <circle cx="12" cy="12" r="3.5" />
</>)

/** A station — a stop that is also an interchange. */
export const StationIcon = icon(<>
  <circle cx="12" cy="12" r="7.5" />
  <circle cx="12" cy="12" r="3" />
</>)

/** A transfer between two lines. */
export const TransferIcon = icon(<>
  <path d="M4.5 8.5h11.5M12.5 5l3.5 3.5-3.5 3.5" />
  <path d="M19.5 15.5H8M11.5 12 8 15.5l3.5 3.5" />
</>)

/** The origin marker — a pin over a point. */
export const PinIcon = icon(<>
  <path d="M19 10.5c0 5.2-7 11-7 11s-7-5.8-7-11a7 7 0 1 1 14 0Z" />
  <circle cx="12" cy="10.25" r="2.5" />
</>)

/** Live movement — the transit signal. */
export const LiveIcon = icon(<>
  <circle cx="12" cy="12" r="2" />
  <path d="M8.4 8.4a5 5 0 0 0 0 7.2M15.6 15.6a5 5 0 0 0 0-7.2" />
  <path d="M5.9 5.9a8.5 8.5 0 0 0 0 12.2M18.1 18.1a8.5 8.5 0 0 0 0-12.2" />
</>)

/* ==========================================================================
   UI ACTIONS
   ========================================================================== */

export const SearchIcon = icon(<>
  <circle cx="10.75" cy="10.75" r="6.25" />
  <path d="m15.5 15.5 4 4" />
</>)

export const FilterIcon = icon(<>
  <path d="M4.5 7h15M7.5 12h9M10.5 17h3" />
</>)

export const SortIcon = icon(<>
  <path d="M7 4.5v15M7 19.5 4 16.5M7 19.5l3-3" />
  <path d="M17 19.5v-15M17 4.5l-3 3M17 4.5l3 3" />
</>)

export const CalendarIcon = icon(<>
  <rect x="3.5" y="5.5" width="17" height="15" rx="2.5" />
  <path d="M3.5 10h17M8.5 3.5v4M15.5 3.5v4" />
</>)

export const DownloadIcon = icon(<>
  <path d="M12 3.5v11M12 14.5 8 10.5M12 14.5l4-4" />
  <path d="M4.5 17v1.5a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V17" />
</>)

export const ShareIcon = icon(<>
  <circle cx="17.5" cy="6" r="2.5" />
  <circle cx="6.5" cy="12" r="2.5" />
  <circle cx="17.5" cy="18" r="2.5" />
  <path d="m8.75 10.8 6.5-3.6M8.75 13.2l6.5 3.6" />
</>)

export const MoreIcon = icon(<>
  <circle cx="5.5" cy="12" r="1.15" />
  <circle cx="12" cy="12" r="1.15" />
  <circle cx="18.5" cy="12" r="1.15" />
</>)

export const InfoIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="M12 11v5.5" />
  <path d="M12 7.75h.01" />
</>)

export const AlertIcon = icon(<>
  <path d="M12 3.75 21 19.5H3Z" />
  <path d="M12 9.75v4" />
  <path d="M12 16.75h.01" />
</>)

export const CheckIcon = icon(<>
  <path d="m4.5 12.5 5 5 10-11" />
</>)

export const CloseIcon = icon(<>
  <path d="m6 6 12 12M18 6 6 18" />
</>)

export const PlusIcon = icon(<>
  <path d="M12 4.5v15M4.5 12h15" />
</>)

export const EditIcon = icon(<>
  <path d="M4.5 19.5h4l10-10a2.12 2.12 0 0 0-3-3l-10 10Z" />
  <path d="m14 6 4 4" />
</>)

export const SendIcon = icon(<>
  <path d="M20.5 3.5 11 13" />
  <path d="M20.5 3.5 14.5 20.5l-3.5-7.5-7.5-3.5Z" />
</>)

export const ClockIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="M12 7v5.25l3.25 1.9" />
</>)

export const SwapIcon = icon(<>
  <path d="M8 4.5v15M8 4.5 4.75 7.75M8 4.5l3.25 3.25" />
  <path d="M16 19.5v-15M16 19.5l-3.25-3.25M16 19.5l3.25-3.25" />
</>)

export const ArrowDownIcon = icon(<>
  <path d="M12 4.5v15M12 19.5l-4.5-4.5M12 19.5l4.5-4.5" />
</>)

export const ChevronRightIcon = icon(<>
  <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />
</>)

export const ChevronDownIcon = icon(<>
  <path d="m5.5 9.5 6.5 6.5 6.5-6.5" />
</>)

export const SettingsIcon = icon(<>
  <circle cx="12" cy="12" r="3" />
  <path d="M19.4 14.5a1.6 1.6 0 0 0 .32 1.77l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.6 1.6 0 0 0-1.77-.32 1.6 1.6 0 0 0-.97 1.47V20a2 2 0 0 1-4 0v-.1a1.6 1.6 0 0 0-1.05-1.46 1.6 1.6 0 0 0-1.77.32l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.6 1.6 0 0 0 .32-1.77 1.6 1.6 0 0 0-1.47-.97H4a2 2 0 0 1 0-4h.1a1.6 1.6 0 0 0 1.46-1.05 1.6 1.6 0 0 0-.32-1.77l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.6 1.6 0 0 0 1.77.32H10a1.6 1.6 0 0 0 .97-1.47V4a2 2 0 0 1 4 0v.1a1.6 1.6 0 0 0 .97 1.47 1.6 1.6 0 0 0 1.77-.32l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.6 1.6 0 0 0-.32 1.77V10a1.6 1.6 0 0 0 1.47.97H20a2 2 0 0 1 0 4h-.1a1.6 1.6 0 0 0-1.47.97Z" />
</>)

export const SunIcon = icon(<>
  <circle cx="12" cy="12" r="4" />
  <path d="M12 2.5v2.25M12 19.25v2.25M4.22 4.22l1.6 1.6M18.18 18.18l1.6 1.6M2.5 12h2.25M19.25 12h2.25M4.22 19.78l1.6-1.6M18.18 5.82l1.6-1.6" />
</>)

export const MoonIcon = icon(<>
  <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z" />
</>)

export const SystemIcon = icon(<>
  <rect x="2.5" y="4.5" width="19" height="13" rx="2.5" />
  <path d="M8.5 20.5h7" />
</>)

export const LogoutIcon = icon(<>
  <path d="M9.5 4.5h-3a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h3" />
  <path d="M15 8.5 18.5 12 15 15.5M18.5 12h-9" />
</>)

/* ==========================================================================
   TRIP STATES
   The lifecycle of a journey, which is the vocabulary the live-trip screen and
   the activity feed both need. Each state is a distinct silhouette rather than
   the same dot in six colours, so the sequence is legible without colour.
   ========================================================================== */

/** Boarded — entering the vehicle. */
export const BoardedIcon = icon(<>
  <path d="M14.5 4.5h3a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-3" />
  <path d="M9 8.5 12.5 12 9 15.5M12.5 12h-9" />
</>)

/** In transit — moving between stops. */
export const InTransitIcon = icon(<>
  <rect x="6.5" y="4.5" width="11" height="11" rx="2.5" />
  <path d="M6.5 10.5h11M9.75 12.9h.01M14.25 12.9h.01" />
  <path d="M4 18.5h16" />
</>)

/** Next stop — the upcoming node on the line. */
export const NextStopIcon = icon(<>
  <path d="M12 3v4.5M12 16.5V21" />
  <circle cx="12" cy="12" r="4" />
  <path d="M12 10v2l1.5 1" />
</>)

/** Arrived — the journey completed. */
export const ArrivedIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="m8 12.25 2.75 2.75L16 9.5" />
</>)

/** Fare update — the charge changed because a stop was reached. */
export const FareUpdateIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="M12 7.25v9.5M14.25 9.5h-3.5a1.75 1.75 0 0 0 0 3.5h2.5a1.75 1.75 0 0 1 0 3.5H9.5" />
</>)

/** End trip — stopping the meter. */
export const EndTripIcon = icon(<>
  <path d="M5 3.5v17" />
  <path d="M5 4.5h11l-2 3.5 2 3.5H5" />
</>)

/* ==========================================================================
   TIME & SCHEDULE
   ========================================================================== */

export const ScheduleIcon = icon(<>
  <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
  <path d="M3.5 9.5h17M8 3.5v3M16 3.5v3" />
  <path d="M7.5 13h3M7.5 16.5h3M13.5 13h3M13.5 16.5h3" />
</>)

export const EtaIcon = icon(<>
  <circle cx="12" cy="13" r="7.5" />
  <path d="M12 9.25V13l2.5 1.5M9.5 2.5h5M19 6l1.5-1.5" />
</>)

export const DurationIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="M12 3.5a8.5 8.5 0 0 1 8.5 8.5" strokeWidth="2.6" />
</>)

export const DeadlineIcon = icon(<>
  <path d="M6.5 3h11M6.5 21h11" />
  <path d="M7.5 3v3.2c0 2.2 4.5 3.6 4.5 5.8s-4.5 3.6-4.5 5.8V21" />
  <path d="M16.5 3v3.2c0 2.2-4.5 3.6-4.5 5.8s4.5 3.6 4.5 5.8V21" />
</>)

/* ==========================================================================
   MONEY & ANALYTICS
   ========================================================================== */

export const SpendingIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="M12 7v10M14.25 9.5h-3.5a1.75 1.75 0 0 0 0 3.5h2.5a1.75 1.75 0 0 1 0 3.5H9.5" />
</>)

export const SavingsIcon = icon(<>
  <ellipse cx="12" cy="6" rx="7.5" ry="2.75" />
  <path d="M4.5 6v6c0 1.5 3.4 2.75 7.5 2.75s7.5-1.25 7.5-2.75V6" />
  <path d="M4.5 12v6c0 1.5 3.4 2.75 7.5 2.75s7.5-1.25 7.5-2.75v-6" />
</>)

export const BudgetIcon = icon(<>
  <path d="M3.5 17.5a8.5 8.5 0 1 1 17 0" />
  <path d="M12 17.5 16 10" />
  <path d="M3.5 17.5h3M17.5 17.5h3" />
</>)

export const TrendsIcon = icon(<>
  <path d="M3.5 17 9 11l4 3.5 7.5-8" />
  <path d="M15.5 6.5h5v5" />
</>)

export const BreakdownIcon = icon(<>
  <rect x="3.5" y="9.5" width="17" height="5" rx="1.5" />
  <path d="M10 9.5v5M14.5 9.5v5" />
</>)

/* ---- Charts -------------------------------------------------------------- */

export const BarChartIcon = icon(<>
  <path d="M3.5 20.5h17" />
  <path d="M7 20.5v-6M12 20.5v-11M17 20.5v-8" />
</>)

export const LineChartIcon = icon(<>
  <path d="M3.5 3.5v17h17" />
  <path d="m7 15 3.5-4.5L14 13l4.5-6" />
</>)

export const PieChartIcon = icon(<>
  <path d="M12 3.5v8.5h8.5A8.5 8.5 0 0 0 12 3.5Z" />
  <path d="M20.4 14.5A8.5 8.5 0 1 1 9.5 3.8" />
</>)

export const DonutChartIcon = icon(<>
  <path d="M12 3.5a8.5 8.5 0 1 1-6 14.5" />
  <path d="M12 8a4 4 0 1 0 2.8 6.85" />
  <path d="M12 3.5v4.5M18.05 17.98 15 14.8" />
</>)

export const HeatmapIcon = icon(<>
  <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" />
  <rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
  <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" />
  <rect x="13.5" y="13.5" width="7" height="7" rx="1.5" />
</>)

export const CompareIcon = icon(<>
  <rect x="4" y="8.5" width="6" height="12" rx="1.5" />
  <rect x="14" y="3.5" width="6" height="17" rx="1.5" />
  <path d="M4 20.5h16" />
</>)

/* ==========================================================================
   PAYMENT RAILS
   Drawn as concepts, not as brand marks — FareFlow does not ship other
   companies' logos, and a wallet that shows a brand it cannot actually charge
   is worse than one that names the rail plainly.
   ========================================================================== */

export const ContactlessIcon = icon(<>
  <path d="M6 5.5a11 11 0 0 1 0 13M10.5 8a6.5 6.5 0 0 1 0 8M15 10.25a2.5 2.5 0 0 1 0 3.5" />
</>)

export const ReceiptIcon = icon(<>
  <path d="M5.5 3.5h13v17l-2.2-1.5-2.2 1.5-2.2-1.5-2.2 1.5-2.2-1.5Z" />
  <path d="M9 8h6M9 12h6M9 16h3" />
</>)

/* ==========================================================================
   MAP & LOCATION
   ========================================================================== */

export const CurrentLocationIcon = icon(<>
  <circle cx="12" cy="12" r="3" />
  <circle cx="12" cy="12" r="7.5" />
  <path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3" />
</>)

export const DestinationIcon = icon(<>
  <path d="M19 10.5c0 5.2-7 11-7 11s-7-5.8-7-11a7 7 0 1 1 14 0Z" />
  <path d="m9.5 10.25 1.75 1.75 3.25-3.5" />
</>)

export const GeofenceIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" strokeDasharray="3 3" />
  <circle cx="12" cy="12" r="2" />
</>)

export const RouteLineIcon = icon(<>
  <circle cx="5.5" cy="18.5" r="2" />
  <circle cx="18.5" cy="5.5" r="2" />
  <path d="m7.25 17 9.5-9.5" />
</>)

export const LayersIcon = icon(<>
  <path d="m12 3.5 8.5 4.5-8.5 4.5L3.5 8Z" />
  <path d="m3.5 13 8.5 4.5 8.5-4.5" />
</>)

export const CompassIcon = icon(<>
  <circle cx="12" cy="12" r="8.5" />
  <path d="m15 9-1.75 4.25L9 15l1.75-4.25Z" />
</>)

export const RecenterIcon = icon(<>
  <circle cx="12" cy="12" r="4" />
  <path d="M12 2.5v3.5M12 18v3.5M2.5 12h3.5M18 12h3.5" />
</>)

export const MinusIcon = icon(<>
  <path d="M4.5 12h15" />
</>)

/* ==========================================================================
   DOCUMENTS
   ========================================================================== */

export const ItineraryIcon = icon(<>
  <rect x="4.5" y="3.5" width="15" height="17" rx="2.5" />
  <path d="M8.5 8.5h7M8.5 12h7M8.5 15.5h4" />
</>)

export const TicketIcon = icon(<>
  <path d="M3.5 8a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v1.5a2.5 2.5 0 0 0 0 5V18a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-3.5a2.5 2.5 0 0 0 0-5Z" />
  <path d="M9 6v2M9 11v2M9 16v2" />
</>)

export const ExportIcon = icon(<>
  <path d="M12 14.5V3.5M12 3.5 8 7.5M12 3.5l4 4" />
  <path d="M4.5 15v3.5a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V15" />
</>)

export const PrintIcon = icon(<>
  <path d="M7 8.5V3.5h10v5" />
  <path d="M7 17.5H5a1.5 1.5 0 0 1-1.5-1.5v-5A2 2 0 0 1 5.5 9h13a2 2 0 0 1 2 2v5a1.5 1.5 0 0 1-1.5 1.5h-2" />
  <rect x="7" y="14" width="10" height="6.5" rx="1" />
</>)

/* ==========================================================================
   STATUS & SIGNALS
   ========================================================================== */

export const BellIcon = icon(<>
  <path d="M18 9.5a6 6 0 1 0-12 0c0 4.5-2 6-2 6h16s-2-1.5-2-6Z" />
  <path d="M13.75 19a2 2 0 0 1-3.5 0" />
</>)

export const MessageIcon = icon(<>
  <path d="M20.5 12a7.5 7.5 0 0 1-7.5 7.5H4.5l1.9-2.9A7.5 7.5 0 1 1 20.5 12Z" />
  <path d="M9 11.5h.01M12 11.5h.01M15 11.5h.01" />
</>)

export const StarIcon = icon(<>
  <path d="m12 3.5 2.6 5.5 5.9.8-4.3 4.2 1.05 6L12 17.2 6.75 20l1.05-6L3.5 9.8l5.9-.8Z" />
</>)

export const BookmarkIcon = icon(<>
  <path d="M6 4.5h12v16l-6-4.25L6 20.5Z" />
</>)

export const TrashIcon = icon(<>
  <path d="M4 6.5h16M9.5 6.5V4h5v2.5" />
  <path d="M6.5 6.5 7.5 20a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1l1-13.5" />
  <path d="M10.5 10.5v6M13.5 10.5v6" />
</>)

export const ShieldIcon = icon(<>
  <path d="M12 3 19.5 6v6c0 4.5-3.4 7.7-7.5 9-4.1-1.3-7.5-4.5-7.5-9V6Z" />
</>)

export const BoltIcon = icon(<>
  <path d="M13 2.5 5 13.5h6l-1 8 8-11h-6Z" />
</>)

export const SparkleIcon = icon(<>
  <path d="m12 3.5 1.9 4.6 4.6 1.9-4.6 1.9L12 16.5l-1.9-4.6L5.5 10l4.6-1.9Z" />
  <path d="m18.5 15.5.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9Z" />
</>)

export const HomeIcon = icon(<>
  <path d="M4 10.5 12 3.5l8 7" />
  <path d="M6 9.5V20h12V9.5" />
  <path d="M10 20v-5.5h4V20" />
</>)

/* ==========================================================================
   COMPOSED MARKS
   Small components rather than raw icons, because a mode is never shown as a
   bare glyph — it is always a glyph in its own colour, at one of three sizes.
   Centralising that here is what stops six pages from each inventing their own
   tinted square.
   ========================================================================== */

/** A transit mode in its own colour. `size` follows the badge scale, not px. */
export function ModeBadge({ mode, size = 'md', className = '' }: {
  mode: string
  size?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const key = (mode ?? '').toLowerCase()
  const scale = size === 'sm' ? ' mode-badge-sm' : size === 'lg' ? ' mode-badge-lg' : ''
  const glyph = size === 'sm' ? 12 : size === 'lg' ? 17 : 14
  return (
    <span className={`mode-badge mode-${key}${scale} ${className}`.trim()}
          title={label(mode)}>
      <ModeIcon mode={mode} size={glyph} />
    </span>
  )
}

/** A run of modes as one connected line — the shape of a journey. */
export function ModeRun({ modes, max = 4, size = 'sm' }: {
  modes: string[]
  max?: number
  size?: 'sm' | 'md'
}) {
  const shown = modes.slice(0, max)
  return (
    <span className="mode-run" aria-hidden="true">
      {shown.map((mode, index) => (
        <span key={index} className={`mode-run-item mode-${mode.toLowerCase()}`}
              style={{ display: 'inline-flex', alignItems: 'center' }}>
          {index > 0 && <span className="mode-run-link" />}
          <ModeBadge mode={mode} size={size} />
        </span>
      ))}
    </span>
  )
}

/**
 * Trip status.
 *
 * <p>A dot and a word, never a filled badge: "Completed" is the unremarkable
 * default and must not be the loudest thing in a list of twenty trips.
 */
export function StatusDot({ status, children }: {
  status: 'completed' | 'progress' | 'planned' | 'delayed' | 'cancelled'
  children: React.ReactNode
}) {
  return <span className={`status status-${status}`}>{children}</span>
}

function label(mode: string): string {
  const key = (mode ?? '').toUpperCase()
  if (key === 'RAIL' || key === 'TRAIN' || key === 'HEAVY_RAIL') return 'Train'
  if (key === 'SUBWAY' || key === 'METRO') return 'Subway'
  if (key === 'TRAM' || key === 'LIGHT_RAIL') return 'Light rail'
  if (key === 'FERRY') return 'Ferry'
  if (key === 'WALK' || key === 'WALKING') return 'Walking'
  return 'Bus'
}
