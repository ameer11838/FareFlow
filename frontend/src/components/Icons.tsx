/**
 * FareFlow's compact product icon set. The slightly squared geometry echoes
 * station signage and ticketing hardware instead of generic dashboard glyphs.
 * Every icon shares the same optical weight and 24px grid.
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
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
})

export const RouteIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="6" cy="18" r="2.25" />
    <rect x="15.75" y="3.75" width="4.5" height="4.5" rx="1" />
    <path d="M8.25 18h3.5a4 4 0 0 0 4-4V8.25" />
  </svg>
)

export const DashboardIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 18.5V14M10 18.5V9M16 18.5V5.5M3.5 20h17" />
    <path d="m4 11 5-4 5 2 5-5" />
  </svg>
)

export const TripsIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M5 8.5A7.5 7.5 0 1 1 4.7 15" />
    <path d="M5 4v4.5h4.5M12 8v4.25l3 1.75" />
  </svg>
)

export const PaymentHistoryIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M6 3.5h12v17l-2-1.25L14 20.5l-2-1.25-2 1.25-2-1.25L6 20.5Z" />
    <path d="M9 8h6M9 12h6M9 16h3.5" />
  </svg>
)

export const WalletIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="3.5" y="6" width="17" height="13" rx="3" />
    <path d="M3.5 10h17M15.5 14h2" />
  </svg>
)

/** A route conversation, unique to the FareFlow assistant entry point. */
export const FareFlowGuideIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 4.5h16v11H9l-5 4v-15Z" />
    <circle cx="9" cy="10" r="1.35" />
    <rect x="14" y="8.65" width="2.7" height="2.7" rx=".55" />
    <path d="M10.35 10h2.3" />
  </svg>
)

export const SendIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="m4 12 16-8-5.5 16-3-6.5L4 12Z" />
    <path d="m11.5 13.5 4-4" />
  </svg>
)

export const PlusIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M12 5v14M5 12h14" />
  </svg>
)

export const CloseIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M6 6l12 12M18 6 6 18" />
  </svg>
)

export const SettingsIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M4 6h7M15 6h5M4 12h3M11 12h9M4 18h9M17 18h3" />
    <circle cx="13" cy="6" r="2" />
    <circle cx="9" cy="12" r="2" />
    <circle cx="15" cy="18" r="2" />
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

export const SunIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <circle cx="12" cy="12" r="4.2" />
    <path d="M12 2.6v2.2M12 19.2v2.2M4.2 12H2M22 12h-2.2M6.3 6.3 4.8 4.8M19.2 19.2l-1.5-1.5M17.7 6.3l1.5-1.5M4.8 19.2l1.5-1.5" />
  </svg>
)

export const MoonIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <path d="M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1z" />
  </svg>
)

export const SystemIcon = ({ size = 18, className }: IconProps) => (
  <svg {...base(size)} className={className}>
    <rect x="2.6" y="4.2" width="18.8" height="12.4" rx="2" />
    <path d="M8.4 20.2h7.2M12 16.6v3.6" />
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
