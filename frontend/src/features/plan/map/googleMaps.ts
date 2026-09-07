/**
 * Google Maps JavaScript API access.
 *
 * The key is read from the environment and never hardcoded. When it is absent the
 * app degrades deliberately: `isMapAvailable` is false, the Plan Trip page renders
 * a schematic fallback, and every other feature — scoring, recommendations, route
 * selection — keeps working. A missing map key is not a broken product.
 */

export const GOOGLE_MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? ''

/**
 * Advanced markers are vector-only and refuse to render without a map id. Google
 * publishes `DEMO_MAP_ID` for development; a real deployment sets its own styled
 * id so the basemap can be themed without touching this code.
 */
export const GOOGLE_MAPS_MAP_ID =
  import.meta.env.VITE_GOOGLE_MAPS_MAP_ID?.trim() || 'DEMO_MAP_ID'

export function isMapAvailable(): boolean {
  return GOOGLE_MAPS_API_KEY.trim().length > 0
}

export interface GoogleMapsApi {
  maps: typeof google.maps
  marker: google.maps.MarkerLibrary
}

/**
 * Loaded on demand so the SDK stays out of the initial bundle.
 *
 * Google exposes two different loading APIs and they are not interchangeable.
 * `google.maps.importLibrary()` exists only when the page has installed Google's
 * inline bootstrap shim; loading the script by URL instead — as this does — makes
 * the API ready at its `callback`, and `importLibrary` appears some time after the
 * script's own `load` event. Waiting on `load` and then reaching for
 * `importLibrary` is therefore a race, and it loses on a cold cache.
 *
 * So the handshake is the callback: Google invokes it once every requested
 * library is genuinely usable, which is the only signal here that means ready.
 */
let cached: Promise<GoogleMapsApi> | null = null

export function loadGoogleMaps(): Promise<GoogleMapsApi> {
  cached ??= (async () => {
    if (!isMapAvailable()) {
      throw new Error('VITE_GOOGLE_MAPS_API_KEY is not configured')
    }
    await injectBootstrap()

    const maps = (globalThis as any).google?.maps as typeof google.maps | undefined
    // `maps` paints the basemap; `marker` supplies AdvancedMarkerElement, which is
    // what lets FareFlow keep its own styled DOM pins instead of raster icons.
    // Both are requested in the script URL, so both are present at the callback.
    const marker = (maps as any)?.marker as google.maps.MarkerLibrary | undefined
    if (!maps?.Map) {
      throw new Error('Google Maps loaded but exposes no Map constructor')
    }
    if (!marker?.AdvancedMarkerElement) {
      throw new Error('Google Maps loaded without the marker library')
    }
    return { maps, marker }
  })()
  return cached
}

const BOOTSTRAP_ID = 'fareflow-google-maps-bootstrap'
const READY_CALLBACK = '__fareflowGoogleMapsReady'

/**
 * Injects the API script once, and resolves when Google says it is ready.
 *
 * Concurrent callers share one script tag: `loadGoogleMaps` memoises the whole
 * promise, and a tag left by a previous module instance (a dev-server hot reload)
 * is adopted rather than duplicated.
 */
function injectBootstrap(): Promise<void> {
  if (isReady()) return Promise.resolve()

  const existing = document.getElementById(BOOTSTRAP_ID) as HTMLScriptElement | null
  if (existing) return awaitReady(existing)

  const script = document.createElement('script')
  script.id = BOOTSTRAP_ID
  script.async = true
  const params = new URLSearchParams({
    key: GOOGLE_MAPS_API_KEY.trim(),
    v: 'weekly',
    // Requested up front, so the callback fires with both already usable.
    libraries: 'maps,marker',
    loading: 'async',
    callback: READY_CALLBACK,
  })
  script.src = `https://maps.googleapis.com/maps/api/js?${params.toString()}`
  document.head.append(script)
  return awaitReady(script)
}

function isReady(): boolean {
  const maps = (globalThis as any).google?.maps
  return !!maps?.Map && !!maps?.marker?.AdvancedMarkerElement
}

/**
 * Resolves on Google's callback, and separately on the script failing to load.
 *
 * The timeout is a backstop for the case that produces no event at all: a key
 * rejected for referrer or billing reasons loads the script fine and then never
 * calls back, which would otherwise leave the map spinning forever instead of
 * falling through to the schematic view.
 */
function awaitReady(script: HTMLScriptElement): Promise<void> {
  if (isReady()) return Promise.resolve()
  return new Promise<void>((resolve, reject) => {
    let settled = false
    const finish = (error?: Error) => {
      if (settled) return
      settled = true
      window.clearTimeout(timer)
      error ? reject(error) : resolve()
    }

    const timer = window.setTimeout(
      () => finish(new Error(
        'Google Maps did not become ready — check the key\'s API restrictions,'
          + ' referrer allowlist, and billing status.')),
      10_000,
    )

    ;(globalThis as any)[READY_CALLBACK] = () => finish()
    script.addEventListener(
      'error',
      () => finish(new Error('Google Maps JavaScript API failed to load')),
      { once: true },
    )
    // A tag adopted mid-flight may already have fired its callback.
    if (isReady()) finish()
  })
}

export interface LngLat {
  lng: number
  lat: number
}

/**
 * Bounding box of a set of points, with a little breathing room.
 *
 * Returned in Google's literal form so it can go straight into `fitBounds`.
 */
export function boundsOf(points: LngLat[]): google.maps.LatLngBoundsLiteral | null {
  if (points.length === 0) return null

  let west = points[0].lng
  let east = points[0].lng
  let south = points[0].lat
  let north = points[0].lat

  for (const point of points) {
    west = Math.min(west, point.lng)
    east = Math.max(east, point.lng)
    south = Math.min(south, point.lat)
    north = Math.max(north, point.lat)
  }

  // A single point would produce a zero-area box, which fitBounds cannot use.
  if (west === east && south === north) {
    const pad = 0.01
    return { west: west - pad, south: south - pad, east: east + pad, north: north + pad }
  }

  return { west, south, east, north }
}

/**
 * Route colours, keyed so the map line and the card accent always agree.
 *
 * Flat hexes rather than the CSS gradient tokens: the map paints its own overlay
 * layer and cannot read a CSS custom property, let alone a gradient. These are
 * the brand indigo and a neutral picked to sit on the basemap without competing
 * with its own road colours.
 */
export const ROUTE_COLORS = {
  selected: '#5b3ce8',
  recommended: '#5b3ce8',
  muted: '#9a97ad',
} as const
