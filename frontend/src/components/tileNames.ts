/**
 * Raster tiles currently used by FareFlow.
 *
 * Keep this list aligned with `src/assets/tiles`. Unused artwork is deliberately
 * excluded so Vite does not bundle or watch an entire speculative icon library.
 */
export type TileName =
  | 'actions-ui/filter'
  | 'actions-ui/search'
  | 'financial-analytics/history'
  | 'insights-charts/bar-chart'
  | 'insights-charts/line-chart'
  | 'misc-branding-top-right/ai-assistant'
  | 'navigation-tabs/settings'
  | 'navigation-tabs/trips'
  | 'notifications/error'
  | 'notifications/success'
  | 'payments-wallet/apple-pay'
  | 'payments-wallet/credit-card'
  | 'payments-wallet/google-pay'
  | 'payments-wallet/receipt'
  | 'payments-wallet/wallet'
  | 'transit-modes/bus'
  | 'transit-modes/ferry'
  | 'transit-modes/light-rail'
  | 'transit-modes/subway'
  | 'transit-modes/train'
  | 'transit-modes/walking'
  | 'trip-states/fare-update'

export const TILE_NAMES: readonly TileName[] = [
  'actions-ui/filter',
  'actions-ui/search',
  'financial-analytics/history',
  'insights-charts/bar-chart',
  'insights-charts/line-chart',
  'misc-branding-top-right/ai-assistant',
  'navigation-tabs/settings',
  'navigation-tabs/trips',
  'notifications/error',
  'notifications/success',
  'payments-wallet/apple-pay',
  'payments-wallet/credit-card',
  'payments-wallet/google-pay',
  'payments-wallet/receipt',
  'payments-wallet/wallet',
  'transit-modes/bus',
  'transit-modes/ferry',
  'transit-modes/light-rail',
  'transit-modes/subway',
  'transit-modes/train',
  'transit-modes/walking',
  'trip-states/fare-update',
]
