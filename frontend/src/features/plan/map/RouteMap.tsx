import { useEffect, useRef, useState } from 'react'
import type { JourneyOption } from '../../../api/types'
import { boundsOf, isMapAvailable, loadTomTom, ROUTE_COLORS, type LngLat } from './tomtom'
import { SchematicMap } from './SchematicMap'

/**
 * TomTom map showing every candidate route, with the selected one highlighted.
 *
 * Responsibilities are kept narrow on purpose: this component draws coordinates
 * and reports clicks. It does not know what a fare is, which route is recommended,
 * or how routes are scored — it is handed a selection and renders it.
 *
 * When no TomTom key is configured it renders {@link SchematicMap} instead, so the
 * page remains fully usable.
 */
export function RouteMap({
  journeys, selectedJourneyId, highlightedJourneyId, onSelectJourney, focus,
}: {
  journeys: JourneyOption[]
  selectedJourneyId: string | null
  /** Hovered in the drawer; emphasised without changing the selection. */
  highlightedJourneyId?: string | null
  onSelectJourney: (journeyId: string) => void
  /**
   * Where to look before any search has run — the rider's saved commute.
   *
   * Framing only. It moves the camera and nothing else: no route is fetched, no
   * fare is quoted, and the moment real journeys arrive they take the viewport
   * back. Centring a map is cheap; planning a trip nobody asked for is not.
   */
  focus?: LngLat[] | null
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markersRef = useRef<any[]>([])
  const resizeObserverRef = useRef<ResizeObserver | null>(null)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)

  // One polyline per journey, built by concatenating its legs' waypoints.
  const drawable = journeys
    .map((option) => ({
      id: option.journeyId,
      waypoints: option.legs.flatMap((leg) => leg.waypoints),
    }))
    .filter((entry) => entry.waypoints.length > 1)

  // ---- Create the map once ----
  useEffect(() => {
    if (!isMapAvailable() || !containerRef.current) return

    let cancelled = false
    let map: any
    let loadTimeout = 0

    loadTomTom()
      .then((tt) => {
        if (cancelled || !containerRef.current) return

        if (cancelled) return
        map = tt.map({
          key: (import.meta.env.VITE_TOMTOM_API_KEY ?? '').trim(),
          container: containerRef.current,
          center: [-74.05, 40.73],
          zoom: 10.5,
          dragRotate: false,
          pitchWithRotate: false,
        })
        map.addControl(new tt.NavigationControl({ showCompass: false }), 'bottom-right')

        // The map is created inside an async callback, and in a flex column the
        // container can still report clientHeight 0 at that moment. mapbox-gl then
        // falls back to a hardcoded 300px canvas and never recovers on its own.
        // Observing the container and calling resize() fixes it for good, and also
        // handles the window being resized later.
        resizeObserverRef.current = new ResizeObserver(() => {
          try {
            map.resize()
          } catch {
            // Map torn down mid-observation; nothing to do.
          }
        })
        resizeObserverRef.current.observe(containerRef.current)

        map.on('load', () => {
          if (cancelled) return
          window.clearTimeout(loadTimeout)
          mapRef.current = map
          map.resize()
          setReady(true)
        })

        // Guard against a style that never finishes loading -- a stalled tile
        // request, a blocked network, or a GPU that cannot back a GL context.
        // Kept short: an empty grey rectangle is worse than a working schematic,
        // and a healthy map fires `load` in well under a second.
        loadTimeout = window.setTimeout(() => {
          if (!cancelled && !mapRef.current) {
            setFailed('The map did not finish loading — showing the schematic view instead.')
          }
        }, 6_000)
        map.on('error', () => {
          if (!cancelled) setFailed('TomTom rejected the request. Check that VITE_TOMTOM_API_KEY is valid.')
        })
      })
      .catch((caught: unknown) => {
        // Surface the real reason -- "could not be loaded" is useless to a developer.
        const detail = caught instanceof Error ? caught.message : String(caught)
        console.error('[FareFlow] TomTom map failed to initialise:', caught)
        if (!cancelled) setFailed(`The TomTom map could not start: ${detail}`)
      })

    return () => {
      cancelled = true
      window.clearTimeout(loadTimeout)
      resizeObserverRef.current?.disconnect()
      resizeObserverRef.current = null
      try {
        map?.remove()
      } catch {
        // Map may already be torn down; nothing to clean up.
      }
      mapRef.current = null
    }
  }, [])

  // ---- Draw routes whenever the candidate set or selection changes ----
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return

    // Remove previous layers and sources before redrawing.
    for (const route of drawable) {
      const lineId = `route-line-${route.id}`
      const hitId = `route-hit-${route.id}`
      if (map.getLayer(hitId)) map.removeLayer(hitId)
      if (map.getLayer(lineId)) map.removeLayer(lineId)
      if (map.getSource(lineId)) map.removeSource(lineId)
    }
    markersRef.current.forEach((marker) => marker.remove())
    markersRef.current = []

    if (drawable.length === 0) return

    void loadTomTom().then((tt) => {
      if (!mapRef.current) return

      // Unselected routes first so the selected line always draws on top.
      const ordered = [...drawable].sort((a, b) =>
        Number(a.id === selectedJourneyId) - Number(b.id === selectedJourneyId))

      for (const route of ordered) {
        const isSelected = route.id === selectedJourneyId
        const isHighlighted = route.id === highlightedJourneyId
        const coordinates = route.waypoints.map((point) => [point.longitude, point.latitude])
        const lineId = `route-line-${route.id}`
        const hitId = `route-hit-${route.id}`

        map.addSource(lineId, {
          type: 'geojson',
          data: {
            type: 'Feature',
            properties: { journeyId: route.id },
            geometry: { type: 'LineString', coordinates },
          },
        })

        map.addLayer({
          id: lineId,
          type: 'line',
          source: lineId,
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: {
            'line-color': isSelected || isHighlighted ? ROUTE_COLORS.selected : ROUTE_COLORS.muted,
            'line-width': isSelected ? 6 : isHighlighted ? 5 : 3.5,
            'line-opacity': isSelected ? 1 : isHighlighted ? 0.85 : 0.45,
            // A dashed line signals "schematic corridor", not surveyed geometry.
            'line-dasharray': isSelected ? [1, 0] : [2, 1.6],
          },
        })

        // A wide invisible line makes the route easy to click without thickening it.
        map.addLayer({
          id: hitId,
          type: 'line',
          source: lineId,
          paint: { 'line-color': '#000', 'line-width': 22, 'line-opacity': 0 },
        })
        map.on('click', hitId, () => onSelectJourney(route.id))
        map.on('mouseenter', hitId, () => { map.getCanvas().style.cursor = 'pointer' })
        map.on('mouseleave', hitId, () => { map.getCanvas().style.cursor = '' })
      }

      // Origin and destination markers come from the selected route's endpoints.
      const anchor = drawable.find((route) => route.id === selectedJourneyId) ?? drawable[0]
      const points = anchor.waypoints
      const first = points[0]
      const last = points[points.length - 1]

      markersRef.current.push(
        new tt.Marker({ element: endpointMarker('origin', first.name) })
          .setLngLat([first.longitude, first.latitude])
          .addTo(map),
        new tt.Marker({ element: endpointMarker('destination', last.name) })
          .setLngLat([last.longitude, last.latitude])
          .addTo(map),
      )

      // Intermediate stops of the selected route only, to avoid clutter.
      for (const point of points.slice(1, -1)) {
        markersRef.current.push(
          new tt.Marker({ element: stopMarker(point.name) })
            .setLngLat([point.longitude, point.latitude])
            .addTo(map),
        )
      }
    })
  }, [ready, journeys, selectedJourneyId, highlightedJourneyId, onSelectJourney])

  // ---- Frame the saved commute before anything has been searched ----
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || drawable.length > 0) return
    if (!focus || focus.length === 0) return

    const bounds = boundsOf(focus)
    if (!bounds) return

    map.resize()
    try {
      map.fitBounds(bounds, { padding: 90, maxZoom: 12, duration: 0 })
    } catch {
      // A viewport too small to fit anything: leave the default framing alone.
    }
    // Only the coordinates matter, and they are stable for a given commute.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, journeys.length, focusKey(focus)])

  // ---- Fit the viewport to the whole journey ----
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || drawable.length === 0) return

    const points: LngLat[] = drawable.flatMap((route) =>
      route.waypoints.map((point) => ({ lng: point.longitude, lat: point.latitude })))

    const bounds = boundsOf(points)
    if (!bounds) return

    // Make sure the canvas matches its container before fitting: mapbox-gl falls
    // back to a 400x300 canvas when the container reported no size at creation.
    map.resize()

    // Padding must leave room, or fitBounds produces an invalid viewport that
    // resolves no tiles at all -- a blank map rather than a zoomed-out one.
    const canvas = map.getCanvas()
    const width = canvas.clientWidth || canvas.width
    const height = canvas.clientHeight || canvas.height

    const clamp = (value: number, available: number) =>
      Math.max(0, Math.min(value, Math.floor(available * 0.35)))

    map.fitBounds(bounds, {
      padding: {
        top: clamp(110, height),
        bottom: clamp(200, height),
        left: clamp(400, width),
        right: clamp(80, width),
      },
      duration: 600,
    })
    // Refit only when the journey itself changes, not on every selection.
  }, [ready, journeys])

  if (!isMapAvailable() || failed) {
    return <SchematicMap
      journeys={journeys}
      selectedJourneyId={selectedJourneyId}
      highlightedJourneyId={highlightedJourneyId}
      onSelectJourney={onSelectJourney}
      reason={failed ?? 'missing-key'}
    />
  }

  return (
    <div
      className={`map-canvas${ready ? ' map-ready' : ''}`}
      ref={containerRef}
      data-testid="tomtom-map"
      aria-label="Route map"
    >
      {!ready && (
        <div className="map-loading">
          <div className="skeleton" style={{ width: 120, height: 12 }} />
          <span className="muted" style={{ fontSize: 13 }}>Loading map…</span>
        </div>
      )}
    </div>
  )
}

function endpointMarker(kind: 'origin' | 'destination', label: string): HTMLElement {
  const element = document.createElement('div')
  element.className = `map-pin map-pin-${kind}`
  element.title = label
  element.innerHTML = kind === 'origin'
    ? '<span class="map-pin-dot"></span>'
    : '<span class="map-pin-square"></span>'
  return element
}

function stopMarker(label: string): HTMLElement {
  const element = document.createElement('div')
  element.className = 'map-stop'
  element.title = label
  return element
}

/** Stable dependency for a set of focus points, so the effect is not re-run per render. */
function focusKey(points: LngLat[] | null | undefined): string {
  return (points ?? []).map((point) => `${point.lng},${point.lat}`).join('|')
}
