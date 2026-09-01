import { useEffect, useState } from 'react'
import { profileApi } from '../../api'
import { ApiError } from '../../api/client'
import type {
  ContextProfileOption, ProfileOptions, TravelModeId, TravelProfile, TypicalPlace,
} from '../../api/types'
import { PageHeader } from '../../components/PageHeader'
import { ErrorState, LoadingState } from '../../components/states'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { ThemeToggle } from '../../components/ThemeToggle'
import { ChoiceCard } from '../onboarding/OnboardingLayout'
import { BudgetStep, CommuteStep, FrequencyStep, HabitsStep, PriorityStep } from '../onboarding/steps'

/**
 * Everything onboarding asked, editable in one place.
 *
 * <p>Deliberately not "run onboarding again". Someone changing their budget should
 * change their budget, not answer five questions to get to it — so the same step
 * components are reused as sections on a single page, and saving here never
 * re-opens onboarding.
 */
export function SettingsPage() {
  const { user, setUser } = useAuth()
  const options = useAsync<ProfileOptions>(() => profileApi.options(), [])
  const profile = useAsync<TravelProfile>(() => profileApi.get(), [])

  const [draft, setDraft] = useState<Draft | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (profile.data) setDraft(toDraft(profile.data))
  }, [profile.data])

  if (options.loading || profile.loading || !draft) {
    return <div className="page"><div className="card"><LoadingState /></div></div>
  }
  if (options.error || profile.error || !options.data) {
    return (
      <div className="page">
        <div className="card">
          <ErrorState
            error={options.error ?? profile.error
              ?? new ApiError(0, { title: 'Could not load your profile' })}
            onRetry={() => { options.refetch(); profile.refetch() }}
          />
        </div>
      </div>
    )
  }

  const catalogue = options.data
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    setSaved(false)
    setDraft((current) => (current === null ? current : { ...current, [key]: value }))
  }

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      const hasCommute = draft.commuteKind !== 'NONE'
        && draft.typicalOrigin !== null && draft.typicalDestination !== null

      const updated = await profileApi.update({
        defaultContextProfile: draft.defaultContextProfile,
        weeklyCommuteFrequency: draft.weeklyCommuteFrequency as never,
        weeklyBudgetCents: draft.weeklyBudgetCents,
        commuteKind: draft.commuteKind as never,
        typicalOrigin: hasCommute ? draft.typicalOrigin : null,
        typicalDestination: hasCommute ? draft.typicalDestination : null,
        passPreference: draft.passPreference as never,
        fareCategory: draft.fareCategory,
        preferredModes: draft.preferredModes,
      })
      // The budget shown in the top bar comes from the user record, so it has to
      // move with the profile or the two would disagree on screen.
      if (user) setUser({ ...user, weeklyBudgetCents: updated.weeklyBudgetCents })
      setSaved(true)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, { title: String(caught) }))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <PageHeader
        tile="navigation-tabs/settings"
        title="Your travel profile"
        subtitle="FareFlow uses these to rank routes and track your spending. Change anything, any time."
      />

      <section className="section">
        <SettingsBlock
          title="Travel priority"
          caption="Your default stance. Any individual trip can override it."
        >
          <PriorityStep
            profiles={catalogue.contextProfiles}
            value={draft.defaultContextProfile}
            onChange={(id) => set('defaultContextProfile', id as ContextProfileOption['id'])}
          />
        </SettingsBlock>
      </section>

      <section className="section">
        <SettingsBlock
          emphasis="lead"
          title="Weekly transportation budget"
          caption="Leave it unset and FareFlow simply will not track against one."
        >
          <BudgetStep
            cents={draft.weeklyBudgetCents}
            onChange={(cents) => set('weeklyBudgetCents', cents)}
          />
        </SettingsBlock>
      </section>

      <section className="section">
        <SettingsBlock title="Commute frequency" caption="Used to project your weekly spend.">
          <FrequencyStep
            options={catalogue.commuteFrequencies}
            value={draft.weeklyCommuteFrequency}
            onChange={(id) => set('weeklyCommuteFrequency', id)}
          />
        </SettingsBlock>
      </section>

      <section className="section">
        <SettingsBlock
          title="Typical commute"
          caption="Shown as a one-tap shortcut on the Plan screen."
        >
          <CommuteStep
            kinds={catalogue.commuteKinds}
            kind={draft.commuteKind}
            onKindChange={(id) => set('commuteKind', id)}
            origin={draft.typicalOrigin}
            destination={draft.typicalDestination}
            onOriginChange={(place) => set('typicalOrigin', place)}
            onDestinationChange={(place) => set('typicalDestination', place)}
          />
        </SettingsBlock>
      </section>

      <section className="section">
        <SettingsBlock
          title="Fare eligibility"
          caption="Used by the stop-based engine. Discounted categories may require verification in a production system."
        >
          <div className="choice-list">
            {catalogue.fareCategories.map((category) => (
              <ChoiceCard
                key={category.id}
                selected={draft.fareCategory === category.id}
                onSelect={() => set('fareCategory', category.id as Draft['fareCategory'])}
                title={category.displayName}
                detail={category.detail}
              />
            ))}
          </div>
        </SettingsBlock>
      </section>

      <section className="section">
        <SettingsBlock title="How you travel and pay" caption="Modes you use, and how you buy fares.">
          <HabitsStep
            modes={catalogue.travelModes}
            selectedModes={draft.preferredModes}
            onToggleMode={(id) => set('preferredModes',
              draft.preferredModes.includes(id)
                ? draft.preferredModes.filter((mode) => mode !== id)
                : [...draft.preferredModes, id])}
            passOptions={catalogue.passPreferences}
            passPreference={draft.passPreference}
            onPassChange={(id) => set('passPreference', id)}
          />
        </SettingsBlock>
      </section>

      {/* Last, and quiet: this is the only setting on the page that does not
          change how FareFlow plans or prices anything. */}
      <section className="section">
        <SettingsBlock
          emphasis="quiet"
          title="Appearance"
          caption="Follows your system by default. A choice here is remembered on this device."
        >
          <ThemeToggle />
        </SettingsBlock>
      </section>

      {error && <p className="auth-error" role="alert">{error.problem.detail ?? error.message}</p>}

      <div className="settings-actions">
        <button className="btn btn-primary btn-lg" onClick={save} disabled={saving}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
        {saved && <span className="settings-saved" role="status">Saved</span>}
      </div>
    </div>
  )
}

/**
 * One setting.
 *
 * <p>`emphasis` exists because these are not equally consequential and the page
 * used to pretend they were: six identical cards, same border, same padding,
 * same heading weight, in the order they happened to be written. The budget is
 * the setting that changes what the product does on every other screen; the
 * theme is a device preference. They should not look the same.
 */
function SettingsBlock({ title, caption, emphasis = 'default', children }: {
  title: string
  caption: string
  emphasis?: 'lead' | 'default' | 'quiet'
  children: React.ReactNode
}) {
  const tone = emphasis === 'lead' ? ' surface-lead'
    : emphasis === 'quiet' ? ' surface-quiet-flat surface-dense' : ''
  return (
    <div className={`card card-body settings-block settings-block-${emphasis}${tone}`}>
      <div className="settings-block-head">
        <h2 className="section-title">{title}</h2>
        <p className="section-sub">{caption}</p>
      </div>
      {children}
    </div>
  )
}

interface Draft {
  defaultContextProfile: ContextProfileOption['id']
  weeklyCommuteFrequency: string | null
  weeklyBudgetCents: number | null
  commuteKind: string | null
  typicalOrigin: TypicalPlace | null
  typicalDestination: TypicalPlace | null
  passPreference: string | null
  fareCategory: 'REGULAR' | 'STUDENT' | 'SENIOR' | 'REDUCED'
  preferredModes: TravelModeId[]
}

function toDraft(profile: TravelProfile): Draft {
  return {
    defaultContextProfile: profile.defaultContextProfile,
    weeklyCommuteFrequency: profile.weeklyCommuteFrequency,
    weeklyBudgetCents: profile.weeklyBudgetCents,
    commuteKind: profile.commuteKind,
    typicalOrigin: profile.typicalOrigin,
    typicalDestination: profile.typicalDestination,
    passPreference: profile.passPreference,
    fareCategory: profile.fareCategory,
    preferredModes: profile.preferredModes.map((mode) => mode.id),
  }
}

/** Re-exported for the settings block used inside onboarding-style sections. */
export { ChoiceCard }
