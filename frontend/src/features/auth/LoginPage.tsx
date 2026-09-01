import { useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useAuth } from '../../hooks/useAuth'
import { AuthLayout } from './AuthLayout'

export function LoginPage() {
  const { user, demoMode, signIn } = useAuth()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Already signed in, or auth is off entirely: there is nothing to do here.
  if (user) {
    const target = (location.state as { from?: string } | null)?.from ?? '/plan'
    return <Navigate to={target} replace />
  }
  if (demoMode) return <Navigate to="/plan" replace />

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await signIn(email.trim(), password)
    } catch (caught) {
      setError(caught instanceof ApiError
        ? (caught.problem.detail ?? caught.message)
        : 'Could not sign in')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <h2 className="auth-title">Welcome back</h2>
      <p className="auth-lede">Sign in to plan a trip and track your spending.</p>

      <form className="auth-form" onSubmit={submit} noValidate>
        <div className="field">
          <label className="label" htmlFor="email">Email</label>
          <input
            id="email" className="input" type="email" autoComplete="email" required
            value={email} onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
          />
        </div>

        {/*
          The placeholder is words, not bullets. A "••••••••" placeholder renders
          identically to a filled password field, so a rider who clicks in and
          types sees dots before and dots after and concludes the field is
          broken — which is exactly the report this changed in response to.
        */}
        <div className="field">
          <label className="label" htmlFor="password">Password</label>
          <div className="auth-password-control">
            <input
              id="password" className="input" type={showPassword ? 'text' : 'password'}
              autoComplete="current-password" required
              value={password} onChange={(event) => setPassword(event.target.value)}
              placeholder="Your password"
            />
            <button
              className="auth-password-toggle"
              type="button"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              aria-pressed={showPassword}
              onClick={() => setShowPassword((visible) => !visible)}
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
        </div>

        {error && <p className="auth-error" role="alert">{error}</p>}

        <button className="btn btn-primary btn-lg btn-block" type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="auth-switch">
        Don&apos;t have an account? <Link to="/register">Create one</Link>
      </p>
    </AuthLayout>
  )
}
