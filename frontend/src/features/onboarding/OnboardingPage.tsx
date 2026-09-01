import { useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { profileApi } from '../../api'
import { ApiError } from '../../api/client'
import type {
  ContextProfileOption, ProfileOptions, TravelModeId, TravelProfileInput, TypicalPlace,
} from '../../api/types'
import { ErrorState, LoadingState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { formatCents } from '../../lib/format'
import { OnboardingLayout } from './OnboardingLayout'
import { BudgetStep, CommuteStep, FrequencyStep, HabitsStep, PriorityStep } from './steps'

/**
 * The onboarding flow.
 *
 * <p>Five questions and a summary, one idea per screen. Nothing is required: every
 * step can be moved past, and an unanswered question stays unanswered rather than
 * being filled in with something plausible. A fabricated commute would drive real
 * recommendations, which is worse than no commute at all.
 *
 * <p>All the answers are held here and submitted once, at the end. A rider who
 * closes the tab on step three has written nothing, which is the honest outcome —
 * they did not finish telling FareFlow about themselves.
 */

/** Welcome and summary are not questions, so they are not counted. */
const QUESTION_STEPS = 5
const TOTAL_STEPS = QUESTION_STEPS + 1

type Screen = 'welcome' | 'frequency' | 'priority' | 'budget' | 'commute' | 'habits' | 'summary'

const ORDER: Screen[] = ['welcome', 'frequency', 'priority', 'budget', 'commute', 'habits', 'summary']

/** Position on the progress indicator, or null for the screens that do not count. */
const STEP_NUMBER: Record<Screen, number | null> = {
  welcome: null,
  frequency: 1,
  priority: 2,
  budget: 3,
  commute: 4,
  habits: 5,
  summary: TOTAL_STEPS,
}

interface Answers {
  defaultContextProfile: ContextProfileOption['id']
  weeklyCommuteFrequency: string | null
  weeklyBudgetCents: number | null
  commuteKind: string | null
  typicalOrigin: TypicalPlace | null
  typicalDestination: TypicalPlace | null
  passPreference: string | null
  preferredModes: TravelModeId[]
}

export function OnboardingPage() {
  const { user, demoMode, needsOnboarding, setUser } = useAuth()
  const navigate = useNavigate()
  const options = useAsync<ProfileOptions>(() => profileApi.options(), [])

  const [screen, setScreen] = useState<Screen>('welcome')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [answers, setAnswers] = useState<Answers>({
    defaultContextProfile: 'BALANCED',
    weeklyCommuteFrequency: null,
    weeklyBudgetCents: null,
    commuteKind: null,
    typicalOrigin: null,
    typicalDestination: null,
    passPreference: null,
    preferredModes: [],
  })

  // Signed out, in demo mode, or already finished: there is nothing to do here.
  if (!user && !demoMode) return <Navigate to="/login" replace />
  if (demoMode || (user && !needsOnboarding)) return <Navigate to="/plan" replace />

  if (options.loading) {
    return <div className="boot"><LoadingState label="Setting things up" /></div>
  }
  if (options.error || !options.data) {
    return (
      <div className="boot">
        <div className="card card-body" style={{ maxWidth: 460 }}>
          <ErrorState
            error={options.error ?? new ApiError(0, { title: 'Could not load onboarding' })}
            onRetry={options.refetch}
          />
        </div>
      </div>
    )
  }

  const catalogue = options.data
  const set = <K extends keyof Answers>(key: K, value: Answers[K]) =>
    setAnswers((current) => ({ ...current, [key]: value }))

  const go = (direction: 1 | -1) => {
    const index = ORDER.indexOf(screen)
    const next = ORDER[Math.min(Math.max(index + direction, 0), ORDER.length - 1)]
    setScreen(next)
  }

  const submit = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const profile = await profileApi.completeOnboarding(toInput(answers))
      // Reflect the finished profile immediately so the guard stops redirecting
      // here the moment we navigate away.
      if (user) {
        setUser({ ...user,
          weeklyBudgetCents: profile.weeklyBudgetCents,
          onboardingCompleted: true })
      }
      navigate('/plan', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, { title: String(caught) }))
    } finally {
      setSubmitting(false)
    }
  }

  const back = (
    <button type="button" className="btn btn-ghost" onClick={() => go(-1)} disabled={submitting}>
      Back
    </button>
  )
  const next = (label = 'Continue') => (
    <button type="button" className="btn btn-primary btn-lg" onClick={() => go(1)}>
      {label}
    </button>
  )

  switch (screen) {
    case 'welcome':
      return (
        <OnboardingLayout
          step={null}
          totalSteps={TOTAL_STEPS}
          eyebrow={user ? `Welcome, ${firstName(user.name)}` : 'Welcome'}
          title="Let's make FareFlow work for you."
          description={
            'FareFlow uses your travel habits and budget to recommend routes that fit '
            + 'your life — not just the fastest route. Five short questions, about a minute.'}
          footer={
            <>
              <span className="onboarding-foot-note">You can change any of this later.</span>
              {next('Get started')}
            </>
          }
        />
      )

    case 'frequency':
      return (
        <OnboardingLayout
          step={STEP_NUMBER.frequency} totalSteps={TOTAL_STEPS}
          title="How often do you typically commute?"
          description="This sets how FareFlow projects your spending for the week."
          footer={<>{back}{next()}</>}
        >
          <FrequencyStep
            options={catalogue.commuteFrequencies}
            value={answers.weeklyCommuteFrequency}
            onChange={(id) => set('weeklyCommuteFrequency', id)}
          />
        </OnboardingLayout>
      )

    case 'priority':
      return (
        <OnboardingLayout
          step={STEP_NUMBER.priority} totalSteps={TOTAL_STEPS}
          title="What usually matters most to you?"
          description="Your default. You can override it on any individual trip."
          footer={<>{back}{next()}</>}
        >
          <PriorityStep
            profiles={catalogue.contextProfiles}
            value={answers.defaultContextProfile}
            onChange={(id) => set('defaultContextProfile', id as ContextProfileOption['id'])}
          />
        </OnboardingLayout>
      )

    case 'budget':
      return (
        <OnboardingLayout
          step={STEP_NUMBER.budget} totalSteps={TOTAL_STEPS}
          title="What's your usual weekly transportation budget?"
          description="FareFlow leans toward cheaper routes as you approach it."
          footer={<>{back}{next()}</>}
        >
          <BudgetStep
            cents={answers.weeklyBudgetCents}
            onChange={(cents) => set('weeklyBudgetCents', cents)}
          />
        </OnboardingLayout>
      )

    case 'commute':
      return (
        <OnboardingLayout
          step={STEP_NUMBER.commute} totalSteps={TOTAL_STEPS}
          title="Do you have a regular commute?"
          description="FareFlow puts it one tap away on the Plan screen."
          footer={<>{back}{next()}</>}
        >
          <CommuteStep
            kinds={catalogue.commuteKinds}
            kind={answers.commuteKind}
            onKindChange={(id) => set('commuteKind', id)}
            origin={answers.typicalOrigin}
            destination={answers.typicalDestination}
            onOriginChange={(place) => set('typicalOrigin', place)}
            onDestinationChange={(place) => set('typicalDestination', place)}
          />
        </OnboardingLayout>
      )

    case 'habits':
      return (
        <OnboardingLayout
          step={STEP_NUMBER.habits} totalSteps={TOTAL_STEPS}
          title="How do you usually travel?"
          description="Pick as many as apply."
          footer={<>{back}{next('Review')}</>}
        >
          <HabitsStep
            modes={catalogue.travelModes}
            selectedModes={answers.preferredModes}
            onToggleMode={(id) => set('preferredModes',
              answers.preferredModes.includes(id)
                ? answers.preferredModes.filter((mode) => mode !== id)
                : [...answers.preferredModes, id])}
            passOptions={catalogue.passPreferences}
            passPreference={answers.passPreference}
            onPassChange={(id) => set('passPreference', id)}
          />
        </OnboardingLayout>
      )

    case 'summary':
    default:
      return (
        <OnboardingLayout
          step={STEP_NUMBER.summary} totalSteps={TOTAL_STEPS}
          eyebrow="Your FareFlow profile"
          title="You're ready."
          description="Here's what FareFlow will use. Change any of it from Settings."
          footer={
            <>
              {back}
              <button type="button" className="btn btn-primary btn-lg"
                      onClick={submit} disabled={submitting}>
                {submitting ? 'Saving…' : 'Start planning'}
              </button>
            </>
          }
        >
          <Summary answers={answers} catalogue={catalogue} />
          {error && (
            <p className="auth-error" role="alert" style={{ marginTop: 'var(--space-4)' }}>
              {error.problem.detail ?? error.message}
            </p>
          )}
        </OnboardingLayout>
      )
  }
}

function Summary({ answers, catalogue }: { answers: Answers; catalogue: ProfileOptions }) {
  const rows = useMemo(() => {
    const label = (options: { id: string; displayName: string }[], id: string | null) =>
      options.find((option) => option.id === id)?.displayName ?? null

    return [
      {
        label: 'Typical commute',
        value: answers.typicalOrigin && answers.typicalDestination
          ? `${answers.typicalOrigin.name} → ${answers.typicalDestination.name}`
          : label(catalogue.commuteKinds, answers.commuteKind) ?? 'Not set',
      },
      {
        label: 'Travel priority',
        value: label(catalogue.contextProfiles, answers.defaultContextProfile) ?? 'Balanced',
      },
      {
        label: 'Weekly budget',
        // No budget stays no budget. Showing $0.00 here would be the first of
        // many places the app then lies about the rider's money.
        value: answers.weeklyBudgetCents === null
          ? 'Not set'
          : formatCents(answers.weeklyBudgetCents),
      },
      {
        label: 'Commute frequency',
        value: label(catalogue.commuteFrequencies, answers.weeklyCommuteFrequency) ?? 'Not set',
      },
      {
        label: 'Usual modes',
        value: answers.preferredModes.length === 0
          ? 'Not set'
          : answers.preferredModes
              .map((id) => label(catalogue.travelModes, id) ?? id)
              .join(', '),
      },
      {
        label: 'Payment style',
        value: label(catalogue.passPreferences, answers.passPreference) ?? 'Not set',
      },
    ]
  }, [answers, catalogue])

  return (
    <dl className="summary-list">
      {rows.map((row) => (
        <div key={row.label} className="summary-row">
          <dt className="summary-label">{row.label}</dt>
          <dd className={`summary-value${row.value === 'Not set' ? ' unset' : ''}`}>
            {row.value}
          </dd>
        </div>
      ))}
    </dl>
  )
}

/**
 * Answers to the wire.
 *
 * A commute needs both ends to be a commute, so a half-answered one is dropped
 * rather than sent — the backend would reject it, and the rider skipping a
 * question should not look like an error to them.
 */
export function toInput(answers: Answers): TravelProfileInput {
  const hasCommute = answers.commuteKind !== 'NONE'
    && answers.typicalOrigin !== null
    && answers.typicalDestination !== null

  return {
    defaultContextProfile: answers.defaultContextProfile,
    weeklyCommuteFrequency: answers.weeklyCommuteFrequency as TravelProfileInput['weeklyCommuteFrequency'],
    weeklyBudgetCents: answers.weeklyBudgetCents,
    commuteKind: answers.commuteKind as TravelProfileInput['commuteKind'],
    typicalOrigin: hasCommute ? answers.typicalOrigin : null,
    typicalDestination: hasCommute ? answers.typicalDestination : null,
    passPreference: answers.passPreference as TravelProfileInput['passPreference'],
    fareCategory: 'REGULAR',
    preferredModes: answers.preferredModes,
  }
}

function firstName(name: string): string {
  return name.split(/\s+/)[0]
}
