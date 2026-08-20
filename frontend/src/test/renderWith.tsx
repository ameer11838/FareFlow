import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../hooks/useAuth'

/** Renders inside the router and auth context the real app provides. */
export function renderWithProviders(ui: ReactElement, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>{ui}</AuthProvider>
    </MemoryRouter>,
  )
}

/** Renders without the auth provider, for components given props directly. */
export function renderWithRouter(ui: ReactElement, { route = '/' } = {}) {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>)
}
