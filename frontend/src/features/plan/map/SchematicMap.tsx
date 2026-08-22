import { useMemo } from 'react'
import type { JourneyOption, LocationCandidate, TransitStop } from '../../../api/types'
import { AlertIcon } from '../../../components/Icons'

/**
 * Fallback route view used when no TomTom key is configured.
 *
 * This is a working fallback, not a placeholder: it projects the same provider
 * geometry and stop coordinates the TomTom layer uses, supports the same selection
 * interaction, and keeps Plan Trip functional without a street basemap.
 *
 * Rendering it honestly matters: it is clearly labelled as a schematic so nobody
 * mistakes it for a real map.
 */
export function SchematicMap({
  journeys, selectedJourneyId, highlightedJourneyId, activeLegIndex,
  onSelectJourney, onSelectLeg, reason, focusLocations = [], nearbyStops = [],
}: {
  journeys: JourneyOption[]
  selectedJourneyId: string | null
  highlightedJourneyId?: string | null
  onSelectJourney: (journeyId: string) => void
  activeLegIndex?: number | null
  onSelectLeg?: (journeyId: string, legIndex: number) => void
  reason: string
  focusLocations?: LocationCandidate[]
  nearbyStops?: TransitStop[]
}) {
  const drawable = journeys
    .map((option) => ({
      id: option.journeyId,
      name: option.summary,
      hasProviderGeometry: option.dataSource === 'GOOGLE_ROUTES',
      waypoints: option.legs.flatMap((leg) => leg.waypoints),
      legs: option.legs,
    }))
    .filter((entry) => entry.waypoints.length > 1)
  const hasGoogleGeometry = drawable.some((entry) => entry.hasProviderGeometry)

  const contextPoints = [
    ...focusLocations.map((place) => ({
      latitude: place.latitude, longitude: place.longitude, name: place.displayName,
    })),
    ...nearbyStops.map((stop) => ({
      latitude: stop.latitude, longitude: stop.longitude, name: stop.name,
    })),
  ]
  const projection = useMemo(
    () => buildProjection(drawable, contextPoints),
    // The coordinate key avoids recomputing for otherwise identical API objects.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [drawable, contextPoints.map((point) => `${point.latitude},${point.longitude}`).join('|')],
  )

  return (
    <div className="map-canvas map-canvas-schematic" data-testid="schematic-map">
      <div className="map-notice">
        <AlertIcon size={16} />
        <span>
          <strong>Schematic view.</strong>{' '}
          {reason === 'missing-key'
            ? 'Set VITE_TOMTOM_API_KEY to load the TomTom basemap.'
            : reason}{' '}
          {hasGoogleGeometry
            ? ' Google routes use provider geometry; fallback routes connect real stops schematically.'
            : ' Station positions are real; connecting lines are indicative.'}
        </span>
      </div>

      {projection && (
        <svg
          className="map-schematic-svg"
          viewBox={`0 0 ${projection.width} ${projection.height}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label="Schematic route diagram"
        >
          <defs>
            <pattern id="grid" width="48" height="48" patternUnits="userSpaceOnUse">
              <path d="M48 0H0V48" fill="none" stroke="rgba(13,23,32,0.05)" strokeWidth="1" />
            </pattern>
          </defs>
          <rect width={projection.width} height={projection.height} fill="url(#grid)" />

          {drawable.length === 0 && nearbyStops.map((stop) => {
            const point = projection.project(stop)
            return (
              <circle key={stop.id} cx={point.x} cy={point.y} r={4.5}
                      fill="#fff" stroke="var(--color-accent)" strokeWidth={2}>
                <title>{stop.name} · {stop.modes.join(', ')}</title>
              </circle>
            )
          })}

          {drawable.length === 0 && focusLocations.map((place) => {
            const point = projection.project(place)
            return (
              <g key={place.providerPlaceId ?? place.displayName}>
                <circle cx={point.x} cy={point.y} r={9} fill="var(--color-accent)"
                        stroke="#fff" strokeWidth={3} />
                <text x={point.x + 16} y={point.y + 4} className="map-schematic-label">
                  {place.displayName}
                </text>
              </g>
            )
          })}

          {/* Unselected routes first so the selected line draws on top. */}
          {[...drawable]
            .sort((a, b) => Number(a.id === selectedJourneyId) - Number(b.id === selectedJourneyId))
            .map((route) => {
              const isSelected = route.id === selectedJourneyId
              const isHighlighted = route.id === highlightedJourneyId
              const points = route.waypoints.map((point) => projection.project(point))
              const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x} ${p.y}`).join(' ')

              return (
                <g key={route.id}>
                  <path
                    d={path}
                    fill="none"
                    stroke={isSelected || isHighlighted ? 'var(--color-accent)' : '#9aa6b2'}
                    strokeWidth={isSelected ? 5 : isHighlighted ? 4 : 3}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeOpacity={isSelected ? 1 : isHighlighted ? 0.85 : 0.45}
                    strokeDasharray={route.hasProviderGeometry || isSelected ? undefined : '7 6'}
                  />
                  <path
                    d={path}
                    fill="none"
                    stroke="transparent"
                    strokeWidth={22}
                    style={{ cursor: 'pointer' }}
                    onClick={() => onSelectJourney(route.id)}
                  >
                    <title>{route.name}</title>
                  </path>

                  {isSelected && route.waypoints.slice(1, -1)
                    .filter((point) => point.name.trim().length > 0)
                    .filter((point, index, all) => index === all.findIndex((candidate) =>
                      candidate.name === point.name
                      && candidate.latitude === point.latitude
                      && candidate.longitude === point.longitude))
                    .map((waypoint, index) => {
                      const point = projection.project(waypoint)
                      return (
                        <circle
                          key={index}
                          cx={point.x}
                          cy={point.y}
                          r={4.5}
                          fill="#fff"
                          stroke="var(--color-accent)"
                          strokeWidth={2.5}
                        />
                      )
                    })}
                  {isSelected && route.legs.map((leg, legIndex) => {
                    if (leg.waypoints.length < 2) return null
                    const legPoints = leg.waypoints.map((point) => projection.project(point))
                    const legPath = legPoints.map((point, index) =>
                      `${index === 0 ? 'M' : 'L'}${point.x} ${point.y}`).join(' ')
                    const active = activeLegIndex === legIndex
                    return (
                      <g key={`leg-${legIndex}`}>
                        <path
                          d={legPath}
                          fill="none"
                          stroke={active ? 'var(--color-accent)' : 'var(--ff-navy-900)'}
                          strokeWidth={active ? 8 : 5}
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeDasharray={leg.mode === 'WALK' ? '3 7' : undefined}
                          opacity={activeLegIndex === null || active ? 1 : .45}
                        />
                        <path
                          d={legPath}
                          fill="none"
                          stroke="transparent"
                          strokeWidth={24}
                          style={{ cursor: 'pointer' }}
                          onClick={(event) => {
                            event.stopPropagation()
                            onSelectLeg?.(route.id, legIndex)
                          }}
                        >
                          <title>{leg.mode === 'WALK' ? `Walk to ${leg.toName}` : leg.lineName}</title>
                        </path>
                      </g>
                    )
                  })}
                </g>
              )
            })}

          {/* Endpoints of the selected route (or the first, before any selection). */}
          {(() => {
            const anchor = drawable.find((r) => r.id === selectedJourneyId) ?? drawable[0]
            if (!anchor) return null
            const points = anchor.waypoints
            const start = projection.project(points[0])
            const end = projection.project(points[points.length - 1])
            return (
              <g>
                <circle cx={start.x} cy={start.y} r={9} fill="#fff" stroke="var(--color-accent)" strokeWidth={4} />
                <rect
                  x={end.x - 8} y={end.y - 8} width={16} height={16} rx={3}
                  fill="var(--color-accent)" stroke="#fff" strokeWidth={3}
                />
                <text x={start.x + 16} y={start.y + 4} className="map-schematic-label">
                  {points[0].name}
                </text>
                <text x={end.x + 16} y={end.y + 4} className="map-schematic-label">
                  {points[points.length - 1].name}
                </text>
              </g>
            )
          })()}
        </svg>
      )}

      {!projection && (
        <div className="map-loading">
          <span className="muted">Choose a station or search for a route to see it here.</span>
        </div>
      )}
    </div>
  )
}

interface Projection {
  width: number
  height: number
  project: (point: { latitude: number; longitude: number }) => { x: number; y: number }
}

/**
 * Equirectangular projection fitted to the bounding box of all routes. Good enough
 * at city scale, and honest: this is a diagram, not a survey.
 */
function buildProjection(
  routes: { waypoints: { latitude: number; longitude: number }[] }[],
  contextPoints: { latitude: number; longitude: number }[] = [],
): Projection | null {
  const points = [...routes.flatMap((route) => route.waypoints), ...contextPoints]
  if (points.length === 0) return null

  const lats = points.map((point) => point.latitude)
  const lngs = points.map((point) => point.longitude)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)

  const width = 1000
  const height = 620
  // Asymmetric padding: the floating panels cover the left edge of the viewport,
  // and station labels extend to the right of their marker. Without this the
  // origin label renders underneath the search panel.
  const padLeft = 320
  const padRight = 170
  const padY = 110

  const spanLng = Math.max(maxLng - minLng, 1e-6)
  const spanLat = Math.max(maxLat - minLat, 1e-6)

  // Preserve aspect ratio so the diagram is not stretched.
  const scale = Math.min((width - padLeft - padRight) / spanLng, (height - padY * 2) / spanLat)
  const offsetX = padLeft + (width - padLeft - padRight - spanLng * scale) / 2
  const offsetY = (height - spanLat * scale) / 2

  return {
    width,
    height,
    project: (point) => ({
      x: offsetX + (point.longitude - minLng) * scale,
      // SVG y grows downward; latitude grows northward.
      y: offsetY + (maxLat - point.latitude) * scale,
    }),
  }
}
