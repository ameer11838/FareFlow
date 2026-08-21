import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ThemeButton, ThemeToggle } from '../ThemeToggle'
import { ThemeProvider } from '../../hooks/useTheme'

/** Lets a test pretend the operating system is in dark mode. */
function mockSystemTheme(dark: boolean) {
  const listeners = new Set<(event: MediaQueryListEvent) => void>()
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: dark && query.includes('dark'),
    media: query,
    addEventListener: (_: string, fn: (event: MediaQueryListEvent) => void) => listeners.add(fn),
    removeEventListener: (_: string, fn: (event: MediaQueryListEvent) => void) => listeners.delete(fn),
    dispatchEvent: () => false,
  }))
  return {
    change: (nowDark: boolean) =>
      listeners.forEach((fn) => fn({ matches: nowDark } as MediaQueryListEvent)),
  }
}

const renderToggle = () =>
  render(<ThemeProvider><ThemeToggle /></ThemeProvider>)

describe('theme preference', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })
  afterEach(() => vi.unstubAllGlobals())

  it('follows the system by default, writing no attribute', () => {
    mockSystemTheme(true)
    renderToggle()

    // No attribute at all, so the stylesheet's prefers-color-scheme query wins.
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false)
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(screen.getByRole('radio', { name: /system/i })).toBeChecked()
  })

  it('applies an explicit choice to the document and remembers it', async () => {
    mockSystemTheme(false)
    renderToggle()

    await userEvent.click(screen.getByRole('radio', { name: /dark/i }))

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(localStorage.getItem('fareflow.theme')).toBe('dark')
  })

  it('restores the stored preference on the next visit', () => {
    localStorage.setItem('fareflow.theme', 'light')
    mockSystemTheme(true)
    renderToggle()

    // An explicit light choice beats a dark operating system.
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    expect(screen.getByRole('radio', { name: /^light$/i })).toBeChecked()
  })

  it('keeps tracking the system after load when set to system', () => {
    const system = mockSystemTheme(false)
    renderToggle()
    expect(document.documentElement.style.colorScheme).toBe('light')

    // A rider who never picked a theme should follow the OS at sunset. The
    // change originates outside React, so it has to be flushed through act.
    act(() => system.change(true))
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false)
  })

  it('ignores a stored value that is not a theme', () => {
    localStorage.setItem('fareflow.theme', 'neon')
    mockSystemTheme(false)
    renderToggle()

    expect(screen.getByRole('radio', { name: /system/i })).toBeChecked()
  })

  it('survives storage being unavailable', async () => {
    mockSystemTheme(false)
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
      .mockImplementation(() => { throw new Error('denied') })
    renderToggle()

    await userEvent.click(screen.getByRole('radio', { name: /dark/i }))

    // Not persisting is a degraded experience, not a broken one.
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    setItem.mockRestore()
  })

  it('the compact button cycles light, dark, then back to system', async () => {
    mockSystemTheme(false)
    render(<ThemeProvider><ThemeButton /></ThemeProvider>)

    await userEvent.click(screen.getByRole('button'))
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')

    await userEvent.click(screen.getByRole('button'))
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')

    await userEvent.click(screen.getByRole('button'))
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false)
  })
})
