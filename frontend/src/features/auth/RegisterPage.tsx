import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useAuth } from '../../hooks/useAuth'
import { AuthLayout } from './AuthLayout'

export function RegisterPage() {
  const { user, demoMode, signUp } = useAuth()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  if (user) return <Navigate to="/plan" replace />
  if (demoMode) return <Navigate to="/plan" replace />

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setFieldErrors({})
    setError(null)

    // Checked here rather than server-side: the confirmation field exists only to
    // catch typing mistakes, so the backend has no reason to know about it.
    if (password !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }
    setSubmitting(true)
    try {
      // Name, email, password. Nothing else — the budget and every travel
      // preference are asked for in onboarding, where there is room to say why.
      await signUp({ name: name.trim(), email: email.trim(), password })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors)
        setError(caught.problem.detail ?? caught.message)
      } else {
        setError('Could not create your account')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <h2 className="auth-title">Create your FareFlow account</h2>
      <p className="auth-lede">
        Takes a moment. We&apos;ll ask about your travel next, so FareFlow can
        recommend routes that fit your week.
      </p>

      <form className="auth-form" onSubmit={submit} noValidate>
        <div className="field">
          <label className="label" htmlFor="name">Name</label>
          <input id="name" className="input" required autoComplete="name"
                 value={name} onChange={(event) => setName(event.target.value)}
                 placeholder="Ameer Hassan" />
          {fieldErrors.name && <span className="field-error">{fieldErrors.name}</span>}
        </div>

        <div className="field">
          <label className="label" htmlFor="email">Email</label>
          <input id="email" className="input" type="email" required autoComplete="email"
                 value={email} onChange={(event) => setEmail(event.target.value)}
                 placeholder="you@example.com" />
          {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
        </div>

        <div className="auth-form-row">
          <div className="field">
            <label className="label" htmlFor="password">Password</label>
            <input id="password" className="input" type="password" required
                   autoComplete="new-password" minLength={8}
                   value={password} onChange={(event) => setPassword(event.target.value)}
                   placeholder="At least 8 characters" />
            {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
          </div>

          <div className="field">
            <label className="label" htmlFor="confirm">Confirm password</label>
            <input id="confirm" className="input" type="password" required
                   autoComplete="new-password"
                   value={confirm} onChange={(event) => setConfirm(event.target.value)}
                   placeholder="Repeat it" />
            {fieldErrors.confirm && <span className="field-error">{fieldErrors.confirm}</span>}
          </div>
        </div>

        {error && <p className="auth-error" role="alert">{error}</p>}

        <button className="btn btn-primary btn-lg btn-block" type="submit" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="auth-switch">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  )
}
