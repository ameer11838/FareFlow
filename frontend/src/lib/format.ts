/**
 * Formatting lives in exactly one file. Money bugs are embarrassing in a fintech
 * product, and a single implementation means a single place to test.
 */

/** 625 -> "$6.25", -300 -> "-$3.00" */
export function formatCents(cents: number): string {
  const sign = cents < 0 ? '-' : ''
  const absolute = Math.abs(cents)
  return `${sign}$${(absolute / 100).toFixed(2)}`
}

/**
 * Money that may not exist.
 *
 * A null budget is not a zero budget, and the difference matters everywhere it is
 * displayed: "$0.00 remaining" reads as "you are out of money", which is the
 * opposite of "you have not told us your budget". Callers pass what should be
 * shown instead.
 */
export function formatOptionalCents(cents: number | null, fallback = 'Not set'): string {
  return cents === null ? fallback : formatCents(cents)
}

/** Signed display for ledger rows: -300 -> "-$3.00", 300 -> "+$3.00" */
export function formatSignedCents(cents: number): string {
  if (cents === 0) return '$0.00'
  const prefix = cents > 0 ? '+' : '-'
  return `${prefix}$${(Math.abs(cents) / 100).toFixed(2)}`
}

export function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} hr` : `${hours} hr ${rest} min`
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

export function formatPercent(ratio: number): string {
  return `${Math.round(ratio * 100)}%`
}

export function labelText(label: string): string {
  switch (label) {
    case 'BEST_VALUE':
      return 'Best value'
    case 'CHEAPEST':
      return 'Cheapest'
    case 'FASTEST':
      return 'Fastest'
    default:
      return label
  }
}

export function ledgerTypeText(type: string): string {
  switch (type) {
    case 'TRIP_CHARGE':
      return 'Trip charge'
    case 'REFUND':
      return 'Refund'
    case 'FARE_ADJUSTMENT':
      return 'Fare adjustment'
    default:
      return type
  }
}
