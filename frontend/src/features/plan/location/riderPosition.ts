import type { RiderPosition } from '../../../api/types'

/**
 * One-shot location capture for confirming a stop.
 *
 * Deliberately not a watch: FareFlow asks where the rider is at the instant they
 * say "I'm here", and holding a continuous GPS subscription for the length of a
 * trip would drain the battery to collect a position nothing reads. A stop
 * confirmation is a moment, so the evidence is a moment.
 *
 * Never rejects. Every failure path — no permission, no hardware, no fix before
 * the timeout — resolves to null, because the caller must advance the rider
 * either way. A location problem is not a fare problem.
 */
export async function captureRiderPosition(
  timeoutMs = 8_000,
): Promise<RiderPosition | null> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) return null

  return new Promise((resolve) => {
    let settled = false
    const finish = (value: RiderPosition | null) => {
      if (settled) return
      settled = true
      resolve(value)
    }

    // The browser's own timeout is advisory in some engines, so the deadline is
    // enforced here too: a confirm button that hangs on a stalled fix is worse
    // than one that records the stop unverified.
    const timer = window.setTimeout(() => finish(null), timeoutMs)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        window.clearTimeout(timer)
        const { latitude, longitude, accuracy } = position.coords
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
          finish(null)
          return
        }
        finish({
          latitude,
          longitude,
          ...(Number.isFinite(accuracy) && accuracy > 0
            ? { accuracyMetres: accuracy }
            : {}),
        })
      },
      () => {
        window.clearTimeout(timer)
        finish(null)
      },
      {
        enableHighAccuracy: true,
        timeout: timeoutMs,
        // A fix from the last half minute is fine for "am I at this stop", and
        // reusing it avoids a cold GPS lock at every single boundary.
        maximumAge: 30_000,
      },
    )
  })
}

/** Whether the browser can offer location at all, for honest UI copy. */
export function isLocationSupported(): boolean {
  return typeof navigator !== 'undefined' && !!navigator.geolocation
}
