import { useEffect, useRef, useState } from 'react'
import type { JourneyOption, LocationCandidate, TransitStop } from '../../../api/types'
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
  journeys, selectedJourneyId, highlightedJourneyId, activeLegIndex,
  activeStopSequence, activeStopName,
  onSelectJourney, onSelectLeg, focus, focusLocations = [], nearbyStops = [],
}: {
  journeys: JourneyOption[]
  selectedJourneyId: string | null
  /** Hovered in the drawer; emphasised without changing the selection. */
  highlightedJourneyId?: string | null
  activeLegIndex?: number | null
  /** Rider-confirmed position within the selected journey's ordered stop markers. */
  activeStopSequence?: number | null
  activeStopName?: string | null
  onSelectJourney: (journeyId: string) => void
  onSelectLeg?: (journeyId: string, legIndex: number) => void
  /**
   * Where to look before any search has run — the rider's saved commute.
   *
   * Framing only. It moves the camera and nothing else: no route is fetched, no
   * fare is quoted, and the moment real journeys arrive they take the viewport
   * back. Centring a map is cheap; planning a trip nobody asked for is not.
   */
  focus?: LngLat[] | null
  /** Places explicitly chosen in autocomplete, before a route is searched. */
  focusLocations?: LocationCandidate[]
  /** Real markers from imported GTFS feeds near the chosen places. */
  nearbyStops?: TransitStop[]
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markersRef = useRef<any[]>([])
  const contextMarkersRef = useRef<any[]>([])
  const resizeObserverRef = useRef<ResizeObserver | null>(null)
  const renderedLayersRef = useRef<string[]>([])
  const renderedSourcesRef = useRef<string[]>([])
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  const [selectedStop, setSelectedStop] = useState<TransitStop | null>(null)

  // One polyline per journey, built by concatenating its legs' waypoints.
  const drawable = journeys
    .map((option) => ({
      id: option.journeyId,
      hasProviderGeometry: option.dataSource === 'GOOGLE_ROUTES',
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

    // Remove every id from the previous render. Candidate ids can change between
    // searches, so deriving cleanup ids from the new result would leak old layers.
    for (const id of [...renderedLayersRef.current].reverse()) {
      if (map.getLayer(id)) map.removeLayer(id)
    }
    for (const id of renderedSourcesRef.current) {
      if (map.getSource(id)) map.removeSource(id)
    }
    renderedLayersRef.current = []
    renderedSourcesRef.current = []
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
        renderedSourcesRef.current.push(lineId)

        map.addLayer({
          id: lineId,
          type: 'line',
          source: lineId,
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: {
            'line-color': isSelected || isHighlighted ? ROUTE_COLORS.selected : ROUTE_COLORS.muted,
            'line-width': isSelected ? 6 : isHighlighted ? 5 : 3.5,
            'line-opacity': isSelected ? 1 : isHighlighted ? 0.85 : 0.45,
            // Google provides actual route polylines. Local stop-to-stop fallback
            // geometry remains dashed so it is never presented as surveyed track.
            'line-dasharray': route.hasProviderGeometry || isSelected ? [1, 0] : [2, 1.6],
          },
        })
        renderedLayersRef.current.push(lineId)

        // A wide invisible line makes the route easy to click without thickening it.
        map.addLayer({
          id: hitId,
          type: 'line',
          source: lineId,
          paint: { 'line-color': '#000', 'line-width': 22, 'line-opacity': 0 },
        })
        renderedLayersRef.current.push(hitId)
        map.on('click', hitId, () => onSelectJourney(route.id))
        map.on('mouseenter', hitId, () => { map.getCanvas().style.cursor = 'pointer' })
        map.on('mouseleave', hitId, () => { map.getCanvas().style.cursor = '' })
      }

      // Draw the selected journey leg-by-leg over the route corridor. Walking is
      // dashed; transit is solid. Each segment has its own generous hit target so
      // a map click can select the matching direction step.
      const selected = journeys.find((journey) => journey.journeyId === selectedJourneyId)
      selected?.legs.forEach((leg, legIndex) => {
        if (leg.waypoints.length < 2) return
        const sourceId = `route-leg-source-${selected.journeyId}-${legIndex}`
        const layerId = `route-leg-${selected.journeyId}-${legIndex}`
        const hitId = `route-leg-hit-${selected.journeyId}-${legIndex}`
        const active = activeLegIndex === legIndex
        map.addSource(sourceId, {
          type: 'geojson',
          data: {
            type: 'Feature',
            properties: { journeyId: selected.journeyId, legIndex },
            geometry: {
              type: 'LineString',
              coordinates: leg.waypoints.map((point) => [point.longitude, point.latitude]),
            },
          },
        })
        renderedSourcesRef.current.push(sourceId)
        map.addLayer({
          id: layerId,
          type: 'line',
          source: sourceId,
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: {
            'line-color': active ? ROUTE_COLORS.selected : '#18202b',
            'line-width': active ? 8 : 5,
            'line-opacity': activeLegIndex === null || active ? 1 : .42,
            'line-dasharray': leg.mode === 'WALK' ? [1, 1.8] : [1, 0],
          },
        })
        renderedLayersRef.current.push(layerId)
        map.addLayer({
          id: hitId,
          type: 'line',
          source: sourceId,
          paint: { 'line-color': '#000', 'line-width': 24, 'line-opacity': 0 },
        })
        renderedLayersRef.current.push(hitId)
        map.on('click', hitId, () => onSelectLeg?.(selected.journeyId, legIndex))
        map.on('mouseenter', hitId, () => { map.getCanvas().style.cursor = 'pointer' })
        map.on('mouseleave', hitId, () => { map.getCanvas().style.cursor = '' })
      })

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

      // One marker per transit stop boundary. Intermediate Google markers are
      // explicitly numbered route positions; GTFS markers retain agency names.
      for (const point of selected ? transitStopMarkers(selected) : []) {
        const state = activeStopSequence == null ? 'upcoming'
          : point.sequence < activeStopSequence ? 'completed'
            : point.sequence === activeStopSequence ? 'current' : 'upcoming'
        markersRef.current.push(
          new tt.Marker({
            element: stopMarker(
              point.name,
              point.marker,
              state,
              state === 'current' ? activeStopName : null,
            ),
          })
            .setLngLat([point.longitude, point.latitude])
            .addTo(map),
        )
      }
    })
  }, [
    ready, journeys, selectedJourneyId, highlightedJourneyId, activeLegIndex,
    activeStopSequence, activeStopName, onSelectJourney, onSelectLeg,
  ])

  // Before planning, make a chosen station/place immediately tangible on the map
  // and surround it with stops from imported GTFS. These are not generic POI dots:
  // every transit marker is backed by a normalized feed record.
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    contextMarkersRef.current.forEach((marker) => marker.remove())
    contextMarkersRef.current = []
    setSelectedStop(null)
    if (drawable.length > 0) return

    void loadTomTom().then((tt) => {
      if (!mapRef.current || drawable.length > 0) return
      for (const stop of nearbyStops) {
        const element = transitStopMarker(stop)
        element.addEventListener('click', (event) => {
          event.stopPropagation()
          setSelectedStop(stop)
          map.easeTo({ center: [stop.longitude, stop.latitude], duration: 350 })
        })
        contextMarkersRef.current.push(
          new tt.Marker({ element })
            .setLngLat([stop.longitude, stop.latitude])
            .addTo(map),
        )
      }
      for (const place of focusLocations) {
        contextMarkersRef.current.push(
          new tt.Marker({ element: placeMarker(place.displayName) })
            .setLngLat([place.longitude, place.latitude])
            .addTo(map),
        )
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, journeys.length, focusKey(focus), transitStopsKey(nearbyStops)])

  // An itinerary-step click moves smoothly to just that leg without disabling the
  // rider's normal pan/zoom controls. Clearing the step leaves the current camera.
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || activeLegIndex == null || !selectedJourneyId) return
    const journey = journeys.find((option) => option.journeyId === selectedJourneyId)
    const leg = journey?.legs[activeLegIndex]
    if (!leg || leg.waypoints.length < 2) return
    const bounds = boundsOf(leg.waypoints.map((point) => ({
      lng: point.longitude,
      lat: point.latitude,
    })))
    if (bounds) map.fitBounds(bounds, { padding: 140, maxZoom: 15, duration: 550 })
  }, [ready, journeys, selectedJourneyId, activeLegIndex])

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
      activeLegIndex={activeLegIndex}
      activeStopSequence={activeStopSequence}
      activeStopName={activeStopName}
      onSelectJourney={onSelectJourney}
      onSelectLeg={onSelectLeg}
      reason={failed ?? 'missing-key'}
      focusLocations={focusLocations}
      nearbyStops={nearbyStops}
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
      {selectedStop && (
        <aside className="map-stop-card" aria-label={`${selectedStop.name} station details`}>
          <button
            type="button"
            className="map-stop-card-close"
            aria-label="Close station details"
            onClick={() => setSelectedStop(null)}
          >×</button>
          <span className="map-stop-card-kicker">{selectedStop.modes.map(modeLabel).join(' · ')}</span>
          <strong>{selectedStop.name}</strong>
          <span>{selectedStop.operators.join(', ') || selectedStop.publisherName}</span>
          {selectedStop.lines.length > 0 && (
            <span className="map-stop-card-lines">Lines {selectedStop.lines.slice(0, 8).join(', ')}</span>
          )}
          <span className="map-stop-card-source">
            Imported GTFS schedule{selectedStop.realtimeAvailable ? ' · Realtime available' : ''}
          </span>
        </aside>
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

function stopMarker(
  label: string,
  marker: string,
  state: 'completed' | 'current' | 'upcoming' = 'upcoming',
  currentName: string | null = null,
): HTMLElement {
  const element = document.createElement('div')
  element.className = `map-stop is-${state}`
  element.title = label
  const accessibleLabel = state === 'current'
    ? `You are here · ${currentName ?? label}`
    : label
  element.dataset.label = accessibleLabel
  element.setAttribute('aria-label', accessibleLabel)
  if (state === 'current') element.setAttribute('aria-current', 'location')
  const number = document.createElement('span')
  number.textContent = marker
  element.append(number)
  return element
}

function transitStopMarkers(journey: JourneyOption) {
  let reached = 0
  const markers: Array<JourneyOption['legs'][number]['waypoints'][number] & {
    marker: string
    sequence: number
  }> = []
  for (const leg of journey.legs) {
    if (leg.mode === 'WALK') continue
    const named = leg.waypoints.filter((point) => point.name.trim().length > 0)
    named.forEach((point, index) => {
      if (markers.some((candidate) => candidate.name === point.name
        && candidate.latitude === point.latitude && candidate.longitude === point.longitude)) return
      const sequence = index === 0 && markers.length === 0 ? 0 : ++reached
      const marker = sequence === 0 ? 'B' : String(sequence)
      markers.push({ ...point, marker, sequence })
    })
  }
  return markers
}

function placeMarker(label: string): HTMLElement {
  const element = document.createElement('div')
  element.className = 'map-place-pin'
  element.title = label
  element.setAttribute('aria-label', label)
  return element
}

function transitStopMarker(stop: TransitStop): HTMLElement {
  const element = document.createElement('button')
  element.type = 'button'
  const isStation = stop.modes.some((mode) =>
    mode === 'RAIL' || mode === 'SUBWAY' || mode === 'LIGHT_RAIL')
  element.className = `map-transit-stop${isStation ? ' is-station' : ''}`
  element.title = `${stop.name} · ${stop.modes.map(modeLabel).join(', ')}`
  element.setAttribute('aria-label', element.title)
  return element
}

function modeLabel(mode: TransitStop['modes'][number]): string {
  if (mode === 'RAIL') return 'Train'
  if (mode === 'LIGHT_RAIL') return 'Light rail'
  return mode.charAt(0) + mode.slice(1).toLowerCase()
}

/** Stable dependency for a set of focus points, so the effect is not re-run per render. */
function focusKey(points: LngLat[] | null | undefined): string {
  return (points ?? []).map((point) => `${point.lng},${point.lat}`).join('|')
}

function transitStopsKey(stops: TransitStop[]): string {
  return stops.map((stop) => stop.id).join('|')
}
