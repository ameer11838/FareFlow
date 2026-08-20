/**
 * Inline stroke icons at a consistent 1.7px weight on a 24px grid.
 * Hand-drawn rather than pulled from a library: six icons do not justify a
 * dependency, and these stay visually consistent with the brand mark.
 */
interface IconProps {
  size?: number
  className?: string
}

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
})

export const RouteIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="6" cy="19" r="2.4" />
    <circle cx="18" cy="5" r="2.4" />
    <path d="M8.4 19h4.1a3 3 0 0 0 3-3V8a3 3 0 0 1 3-3" />
  </svg>
)

export const DashboardIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 13h5v7H4zM10.5 4h3v16h-3zM15 9h5v11h-5z" />
  </svg>
)

export const TripsIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="5" y="3" width="14" height="14" rx="3" />
    <path d="M5 13h14M9 20l-1.5 1.5M15 20l1.5 1.5" />
    <circle cx="8.5" cy="15.5" r=".9" fill="currentColor" stroke="none" />
    <circle cx="15.5" cy="15.5" r=".9" fill="currentColor" stroke="none" />
  </svg>
)

export const LedgerIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M5 4h14v16H5zM9 4v16" />
    <path d="M12 9h4M12 13h4" />
  </svg>
)

export const WalletIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 8.5A2.5 2.5 0 0 1 6.5 6H18a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6.5A2.5 2.5 0 0 1 4 16.5Z" />
    <path d="M4 8.5A2.5 2.5 0 0 1 6.5 6h9.9" />
    <circle cx="16.5" cy="12.5" r="1.2" fill="currentColor" stroke="none" />
  </svg>
)

export const SparkleIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M12 3.5 13.6 9 19 10.5 13.6 12 12 17.5 10.4 12 5 10.5 10.4 9Z" />
    <path d="M18 16.5 18.7 18.8 21 19.5 18.7 20.2 18 22.5 17.3 20.2 15 19.5 17.3 18.8Z" />
  </svg>
)

export const CloseIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M6 6l12 12M18 6 6 18" />
  </svg>
)

export const SettingsIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 3v2M12 19v2M21 12h-2M5 12H3M18.4 5.6l-1.4 1.4M7 17l-1.4 1.4M18.4 18.4L17 17M7 7 5.6 5.6" />
  </svg>
)

export const ClockIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 7.5V12l3 1.8" />
  </svg>
)

export const TransferIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 8h13l-3-3M20 16H7l3 3" />
  </svg>
)

export const ArrowDownIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M12 5v14M6 13l6 6 6-6" />
  </svg>
)

export const SwapIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M7 4v16M7 20l-3-3M7 4l3 3M17 20V4M17 4l3 3M17 20l-3-3" />
  </svg>
)

export const InfoIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 11v5M12 8h.01" />
  </svg>
)

export const SearchIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="m16 16 4 4" />
  </svg>
)

export const AlertIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M12 4 2.5 20h19L12 4Z" />
    <path d="M12 10v4M12 17h.01" />
  </svg>
)

export const CheckIcon = ({ size = 16, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="m4.5 12.5 5 5 10-11" />
  </svg>
)

/** Vehicle glyphs used in activity rows, keyed by the backend `mode` value. */
export const RailIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="6" y="3" width="12" height="13" rx="2.5" />
    <path d="M6 11h12M8 20l-1.5 1.5M16 20l1.5 1.5M9 20h6" />
  </svg>
)

export const SubwayIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="5" y="4" width="14" height="12" rx="4" />
    <path d="M5 11h14M8.5 20l-1 1.5M15.5 20l1 1.5M9 20h6" />
  </svg>
)

export const BusIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="4" y="4" width="16" height="12" rx="2.5" />
    <path d="M4 11h16M7.5 20v-2M16.5 20v-2" />
  </svg>
)

export const FerryIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 17.5c1.6 0 1.6 1.5 3.2 1.5s1.6-1.5 3.2-1.5 1.6 1.5 3.2 1.5 1.6-1.5 3.2-1.5 1.6 1.5 3.2 1.5" />
    <path d="M5.5 14.5 7 9h10l1.5 5.5M12 9V5.5M9.5 5.5h5" />
  </svg>
)

export const WalkIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="13" cy="4.5" r="1.6" />
    <path d="M11 21.5 12.5 15l-2.6-2.4.8-4.1 3.3 1.4 1.4 2.6 2.6 1M9.5 10 7 11.5M12.5 15l2.8 3.2 1.2 3.3" />
  </svg>
)

export function ModeIcon({ mode, size = 18 }: { mode: string; size?: number }) {
  switch (mode?.toUpperCase()) {
    case 'RAIL':
      return <RailIcon size={size} />
    case 'SUBWAY':
      return <SubwayIcon size={size} />
    case 'FERRY':
      return <FerryIcon size={size} />
    case 'BUS':
    default:
      return <BusIcon size={size} />
  }
}
