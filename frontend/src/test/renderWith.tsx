import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AssistantRoot } from '../features/assistant/AssistantRoot'
import { AuthProvider } from '../hooks/useAuth'
import { ThemeProvider } from '../hooks/useTheme'

/** Renders inside the router and auth context the real app provides. */
export function renderWithProviders(ui: ReactElement, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ThemeProvider>
        <AuthProvider><AssistantRoot>{ui}</AssistantRoot></AuthProvider>
      </ThemeProvider>
    </MemoryRouter>,
  )
}

/** Renders without the auth provider, for components given props directly. */
export function renderWithRouter(ui: ReactElement, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ThemeProvider>{ui}</ThemeProvider>
    </MemoryRouter>,
  )
}
