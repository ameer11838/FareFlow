import { useEffect, useRef, useState } from 'react'
import type { JourneyOption, LocationCandidate, TransitStop } from '../../../api/types'
import {
  boundsOf, isMapAvailable, loadGoogleMaps, GOOGLE_MAPS_MAP_ID, ROUTE_COLORS, type LngLat,
} from './googleMaps'
import { SchematicMap } from './SchematicMap'

/**
 * Google map showing every candidate route, with the selected one highlighted.
 *
 * Responsibilities are kept narrow on purpose: this component draws coordinates
 * and reports clicks. It does not know what a fare is, which route is recommended,
 * or how routes are scored — it is handed a selection and renders it.
 *
 * When no Google Maps key is configured it renders {@link SchematicMap} instead,
 * so the page remains fully usable.
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
  const mapRef = useRef<google.maps.Map | null>(null)
  const polylinesRef = useRef<google.maps.Polyline[]>([])
  const markersRef = useRef<google.maps.marker.AdvancedMarkerElement[]>([])
  const contextMarkersRef = useRef<google.maps.marker.AdvancedMarkerElement[]>([])
  const resizeObserverRef = useRef<ResizeObserver | null>(null)
  // The last viewport a effect asked for, replayed when the container resizes.
  const lastFitRef = useRef<{
    bounds: google.maps.LatLngBoundsLiteral
    padding: number | google.maps.Padding
  } | null>(null)
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
    let map: google.maps.Map | undefined
    let loadTimeout = 0

    loadGoogleMaps()
      .then(({ maps }) => {
        if (cancelled || !containerRef.current) return

        map = new maps.Map(containerRef.current, {
          center: { lat: 40.73, lng: -74.05 },
          zoom: 10.5,
          mapId: GOOGLE_MAPS_MAP_ID,
          // The rider is picking a route, not touring a city: rotation and
          // Street View are noise, and a tilted basemap makes a polyline harder
          // to trace. Zoom stays, because comparing routes needs it.
          disableDefaultUI: true,
          zoomControl: true,
          zoomControlOptions: { position: maps.ControlPosition.RIGHT_BOTTOM },
          clickableIcons: false,
          gestureHandling: 'greedy',
          isFractionalZoomEnabled: true,
        })

        // The map is created inside an async callback, and in a flex column the
        // container can still report clientHeight 0 at that moment. Replaying the
        // last requested viewport once the container has real size stops the map
        // settling on a framing that was computed against a zero-height box.
        resizeObserverRef.current = new ResizeObserver(() => {
          const fit = lastFitRef.current
          if (!fit || !mapRef.current) return
          try {
            mapRef.current.fitBounds(fit.bounds, fit.padding as never)
          } catch {
            // Map torn down mid-observation; nothing to do.
          }
        })
        resizeObserverRef.current.observe(containerRef.current)

        maps.event.addListenerOnce(map, 'idle', () => {
          if (cancelled) return
          window.clearTimeout(loadTimeout)
          mapRef.current = map ?? null
          setReady(true)
        })

        // Guard against a basemap that never finishes loading -- a stalled tile
        // request, a blocked network, or a GPU that cannot back a GL context.
        // Kept short: an empty grey rectangle is worse than a working schematic,
        // and a healthy map goes idle in well under a second.
        loadTimeout = window.setTimeout(() => {
          if (!cancelled && !mapRef.current) {
            setFailed('The map did not finish loading — showing the schematic view instead.')
          }
        }, 6_000)
      })
      .catch((caught: unknown) => {
        // Surface the real reason -- "could not be loaded" is useless to a developer.
        const detail = caught instanceof Error ? caught.message : String(caught)
        console.error('[FareFlow] Google map failed to initialise:', caught)
        if (!cancelled) setFailed(`The Google map could not start: ${detail}`)
      })

    return () => {
      cancelled = true
      window.clearTimeout(loadTimeout)
      resizeObserverRef.current?.disconnect()
      resizeObserverRef.current = null
      clearOverlays(polylinesRef, markersRef, contextMarkersRef)
      mapRef.current = null
    }
  }, [])

  // ---- Draw routes whenever the candidate set or selection changes ----
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return

    // Overlays are owned outright rather than diffed: candidate ids change between
    // searches, so reconciling against the new result would leak the old lines.
    clearOverlays(polylinesRef, markersRef)

    if (drawable.length === 0) return

    void loadGoogleMaps().then(({ maps, marker }) => {
      if (!mapRef.current) return

      // Unselected routes first so the selected line always draws on top.
      const ordered = [...drawable].sort((a, b) =>
        Number(a.id === selectedJourneyId) - Number(b.id === selectedJourneyId))

      ordered.forEach((route, index) => {
        const isSelected = route.id === selectedJourneyId
        const isHighlighted = route.id === highlightedJourneyId
        const path = route.waypoints.map((point) => ({
          lat: point.latitude, lng: point.longitude,
        }))
        // Google provides actual route polylines. Local stop-to-stop fallback
        // geometry stays dashed so it is never presented as surveyed track.
        const dashed = !route.hasProviderGeometry && !isSelected

        polylinesRef.current.push(new maps.Polyline({
          map,
          path,
          clickable: false,
          zIndex: 10 + index,
          strokeColor: isSelected || isHighlighted ? ROUTE_COLORS.selected : ROUTE_COLORS.muted,
          strokeWeight: isSelected ? 6 : isHighlighted ? 5 : 3.5,
          strokeOpacity: dashed ? 0 : isSelected ? 1 : isHighlighted ? 0.85 : 0.45,
          ...(dashed ? { icons: dashPattern(3.5, 0.45) } : {}),
        }))

        // A wide invisible line makes the route easy to click without thickening it.
        polylinesRef.current.push(clickTarget(
          maps, map, path, 22, 30 + index, () => onSelectJourney(route.id)))
      })

      // Draw the selected journey leg-by-leg over the route corridor. Walking is
      // dashed; transit is solid. Each segment has its own generous hit target so
      // a map click can select the matching direction step.
      const selected = journeys.find((journey) => journey.journeyId === selectedJourneyId)
      selected?.legs.forEach((leg, legIndex) => {
        if (leg.waypoints.length < 2) return
        const active = activeLegIndex === legIndex
        const path = leg.waypoints.map((point) => ({
          lat: point.latitude, lng: point.longitude,
        }))
        const opacity = activeLegIndex == null || active ? 1 : 0.42
        const walking = leg.mode === 'WALK'
        const weight = active ? 8 : 5

        polylinesRef.current.push(new maps.Polyline({
          map,
          path,
          clickable: false,
          zIndex: active ? 70 : 60,
          strokeColor: active ? ROUTE_COLORS.selected : '#18202b',
          strokeWeight: weight,
          strokeOpacity: walking ? 0 : opacity,
          ...(walking ? { icons: dashPattern(weight, opacity) } : {}),
        }))

        polylinesRef.current.push(clickTarget(
          maps, map, path, 24, 80,
          () => onSelectLeg?.(selected.journeyId, legIndex)))
      })

      // Origin and destination markers come from the selected route's endpoints.
      const anchor = drawable.find((route) => route.id === selectedJourneyId) ?? drawable[0]
      const points = anchor.waypoints
      const first = points[0]
      const last = points[points.length - 1]

      markersRef.current.push(
        new marker.AdvancedMarkerElement({
          map,
          position: { lat: first.latitude, lng: first.longitude },
          content: endpointMarker('origin', first.name),
          zIndex: 100,
        }),
        new marker.AdvancedMarkerElement({
          map,
          position: { lat: last.latitude, lng: last.longitude },
          content: endpointMarker('destination', last.name),
          zIndex: 100,
        }),
      )

      // One marker per transit stop boundary. Intermediate Google markers are
      // explicitly numbered route positions; GTFS markers retain agency names.
      for (const point of selected ? transitStopMarkers(selected) : []) {
        const state = activeStopSequence == null ? 'upcoming'
          : point.sequence < activeStopSequence ? 'completed'
            : point.sequence === activeStopSequence ? 'current' : 'upcoming'
        markersRef.current.push(new marker.AdvancedMarkerElement({
          map,
          position: { lat: point.latitude, lng: point.longitude },
          content: stopMarker(
            point.name,
            point.marker,
            state,
            state === 'current' ? activeStopName : null,
          ),
          zIndex: state === 'current' ? 120 : 90,
        }))
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
    clearOverlays(contextMarkersRef)
    setSelectedStop(null)
    if (drawable.length > 0) return

    void loadGoogleMaps().then(({ marker }) => {
      if (!mapRef.current || drawable.length > 0) return
      for (const stop of nearbyStops) {
        const element = transitStopMarker(stop)
        element.addEventListener('click', (event) => {
          event.stopPropagation()
          setSelectedStop(stop)
          map.panTo({ lat: stop.latitude, lng: stop.longitude })
        })
        contextMarkersRef.current.push(new marker.AdvancedMarkerElement({
          map,
          position: { lat: stop.latitude, lng: stop.longitude },
          content: element,
        }))
      }
      for (const place of focusLocations) {
        contextMarkersRef.current.push(new marker.AdvancedMarkerElement({
          map,
          position: { lat: place.latitude, lng: place.longitude },
          content: placeMarker(place.displayName),
        }))
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
    if (bounds) fit(map, lastFitRef, bounds, 140)
  }, [ready, journeys, selectedJourneyId, activeLegIndex])

  // ---- Frame the saved commute before anything has been searched ----
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || drawable.length > 0) return
    if (!focus || focus.length === 0) return

    const bounds = boundsOf(focus)
    if (!bounds) return

    try {
      fit(map, lastFitRef, bounds, 90)
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

    // Padding must leave room, or fitBounds produces an invalid viewport that
    // resolves no tiles at all -- a blank map rather than a zoomed-out one. The
    // left inset is generous because the route drawer overlays that edge.
    const element = map.getDiv() as HTMLElement
    const width = element.clientWidth || 800
    const height = element.clientHeight || 600

    const clamp = (value: number, available: number) =>
      Math.max(0, Math.min(value, Math.floor(available * 0.35)))

    fit(map, lastFitRef, bounds, {
      top: clamp(110, height),
      bottom: clamp(200, height),
      left: clamp(400, width),
      right: clamp(80, width),
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
      data-testid="google-map"
      aria-label="Route map"
    >
      {/*
        The map gets a child of its own that React never puts anything inside.
        `new maps.Map(el)` takes ownership of its container and replaces the
        children wholesale, so sharing one element with React's overlays makes
        React try to remove nodes Google has already swapped out — which throws
        `removeChild: not a child of this node` and unmounts the whole page.
      */}
      <div className="map-surface" ref={containerRef} />
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

/**
 * A dashed stroke.
 *
 * Google draws dashes as repeated symbols along a zero-opacity line rather than
 * with a dash array, so the line's own `strokeOpacity` must be 0 and the dash
 * carries the colour weight instead.
 */
function dashPattern(weight: number, opacity: number): google.maps.IconSequence[] {
  return [{
    icon: {
      path: 'M 0,-1 0,1',
      strokeOpacity: opacity,
      strokeWeight: weight,
      scale: 1.6,
    },
    offset: '0',
    repeat: `${Math.round(weight * 2.6)}px`,
  }]
}

/** A fat transparent line: easy to click, invisible on the basemap. */
function clickTarget(
  maps: typeof google.maps,
  map: google.maps.Map,
  path: google.maps.LatLngLiteral[],
  weight: number,
  zIndex: number,
  onClick: () => void,
): google.maps.Polyline {
  const line = new maps.Polyline({
    map, path, zIndex,
    clickable: true,
    strokeColor: '#000000',
    strokeOpacity: 0,
    strokeWeight: weight,
  })
  line.addListener('click', onClick)
  return line
}

/** Fits the viewport and remembers the request so a resize can replay it. */
function fit(
  map: google.maps.Map,
  lastFitRef: { current: { bounds: google.maps.LatLngBoundsLiteral; padding: number | google.maps.Padding } | null },
  bounds: google.maps.LatLngBoundsLiteral,
  padding: number | google.maps.Padding,
) {
  lastFitRef.current = { bounds, padding }
  map.fitBounds(bounds, padding as never)
}

/** Detaches every overlay in the given refs and empties them. */
function clearOverlays(
  ...refs: Array<{ current: Array<google.maps.Polyline | google.maps.marker.AdvancedMarkerElement> }>
) {
  for (const ref of refs) {
    for (const overlay of ref.current) {
      try {
        // Polylines detach through setMap; advanced markers through a property.
        if ('setMap' in overlay) overlay.setMap(null)
        else overlay.map = null
      } catch {
        // Already detached with the map itself; nothing to do.
      }
    }
    ref.current = []
  }
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
