import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

export type ThemePreference = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'fareflow.theme'

interface ThemeValue {
  /** What the rider chose, including 'system'. */
  preference: ThemePreference
  /** What that resolves to right now — never 'system'. */
  resolved: ResolvedTheme
  setPreference: (preference: ThemePreference) => void
}

const ThemeContext = createContext<ThemeValue | null>(null)

function systemTheme(): ResolvedTheme {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function storedPreference(): ThemePreference {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
}

/**
 * Theme preference, persisted and applied to the document root.
 *
 * <p>Three states rather than two: 'system' is a real choice, not the absence of
 * one. A rider whose OS switches to dark at sunset should follow it unless they
 * have said otherwise, so 'system' stays live and re-resolves when the OS changes.
 *
 * <p>The attribute is written to {@code <html>} rather than kept in React state
 * alone, because the CSS does all the work: every token has a value under
 * {@code [data-theme="dark"]} and under the {@code prefers-color-scheme} media
 * query, so no component has to know which theme is active.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(() => {
    try {
      return storedPreference()
    } catch {
      // Private browsing can throw on localStorage access. Falling back to the
      // OS setting is strictly better than failing to render.
      return 'system'
    }
  })
  const [system, setSystem] = useState<ResolvedTheme>(() => {
    try {
      return systemTheme()
    } catch {
      return 'light'
    }
  })

  // 'system' has to keep tracking the OS after load, or a rider who never picked
  // a theme stays on whatever it was when the tab opened.
  useEffect(() => {
    const query = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (!query) return
    const onChange = (event: MediaQueryListEvent) => setSystem(event.matches ? 'dark' : 'light')
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const resolved: ResolvedTheme = preference === 'system' ? system : preference

  useEffect(() => {
    const root = document.documentElement
    if (preference === 'system') {
      // No attribute at all, so the media query in the stylesheet takes over.
      root.removeAttribute('data-theme')
    } else {
      root.setAttribute('data-theme', preference)
    }
    // Tells the browser which scrollbars and form controls to draw.
    root.style.colorScheme = resolved
  }, [preference, resolved])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Not persisting is a degraded experience, not a broken one.
    }
  }, [])

  const value = useMemo<ThemeValue>(
    () => ({ preference, resolved, setPreference }),
    [preference, resolved, setPreference],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeValue {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside ThemeProvider')
  return context
}
