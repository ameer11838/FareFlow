import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'

export interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: ApiError | null
  refetch: () => void
}

/**
 * Minimal data-fetching hook. Four pages doing simple reads do not justify a
 * caching library; this is about thirty lines and every one is understandable.
 */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [nonce, setNonce] = useState(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const callback = useCallback(fn, deps)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    callback()
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setError(caught instanceof ApiError ? caught : new ApiError(0, { title: String(caught) }))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [callback, nonce])

  return { data, loading, error, refetch: () => setNonce((n) => n + 1) }
}
