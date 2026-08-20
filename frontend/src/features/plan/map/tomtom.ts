/**
 * TomTom Maps SDK access.
 *
 * The key is read from the environment and never hardcoded. When it is absent the
 * app degrades deliberately: `isMapAvailable` is false, the Plan Trip page renders
 * a schematic fallback, and every other feature — scoring, recommendations, route
 * selection — keeps working. A missing map key is not a broken product.
 */

export const TOMTOM_API_KEY = import.meta.env.VITE_TOMTOM_API_KEY ?? ''

export function isMapAvailable(): boolean {
  return TOMTOM_API_KEY.trim().length > 0
}

/**
 * Loaded on demand so the ~800KB SDK stays out of the initial bundle.
 *
 * The stylesheet is imported separately from the module: a failure in either one
 * has a different cause, and collapsing them into one Promise.all hides which.
 */
let cached: Promise<any> | null = null

export function loadTomTom(): Promise<any> {
  cached ??= (async () => {
    await import('@tomtom-international/web-sdk-maps/dist/maps.css')
    const module = await import('@tomtom-international/web-sdk-maps')
    // The SDK is a UMD bundle: depending on interop it lands on `default` or
    // directly on the namespace. `map` is the function we actually need.
    const sdk = (module as any).default ?? module
    if (typeof sdk?.map !== 'function') {
      throw new Error('TomTom SDK loaded but exposes no map() function')
    }
    return sdk
  })()
  return cached
}

export interface LngLat {
  lng: number
  lat: number
}

/** Bounding box of a set of points, with a little breathing room. */
export function boundsOf(points: LngLat[]): [[number, number], [number, number]] | null {
  if (points.length === 0) return null

  let minLng = points[0].lng
  let maxLng = points[0].lng
  let minLat = points[0].lat
  let maxLat = points[0].lat

  for (const point of points) {
    minLng = Math.min(minLng, point.lng)
    maxLng = Math.max(maxLng, point.lng)
    minLat = Math.min(minLat, point.lat)
    maxLat = Math.max(maxLat, point.lat)
  }

  // A single point would produce a zero-area box, which fitBounds cannot use.
  if (minLng === maxLng && minLat === maxLat) {
    const pad = 0.01
    return [[minLng - pad, minLat - pad], [maxLng + pad, maxLat + pad]]
  }

  return [[minLng, minLat], [maxLng, maxLat]]
}

/**
 * Route colours, keyed so the map line and the card accent always agree.
 *
 * Flat hexes rather than the CSS gradient tokens: mapbox-gl paints into a WebGL
 * canvas and cannot read a CSS custom property, let alone a gradient. These are
 * the brand indigo and a neutral picked to sit on the basemap without competing
 * with its own road colours.
 */
export const ROUTE_COLORS = {
  selected: '#5b3ce8',
  recommended: '#5b3ce8',
  muted: '#9a97ad',
} as const
