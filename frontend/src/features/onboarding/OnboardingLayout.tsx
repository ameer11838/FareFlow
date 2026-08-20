import { Wordmark } from '../../components/Logo'

/**
 * The frame every onboarding step shares.
 *
 * One question per screen, one progress indicator, one pair of controls in a
 * fixed place. Nothing moves between steps except the content, which is what
 * makes six screens feel shorter than one long form.
 *
 * The welcome and summary screens pass `step` as null: they are not questions, so
 * counting them would make the flow look longer than it is.
 */
export function OnboardingLayout({
  step, totalSteps, eyebrow, title, description, children, footer,
}: {
  step: number | null
  totalSteps: number
  eyebrow?: string
  title: string
  description?: string
  children?: React.ReactNode
  footer: React.ReactNode
}) {
  return (
    <div className="onboarding-shell">
      <header className="onboarding-top">
        <Wordmark size={28} tone="dark" />

        {step !== null && (
          <div className="onboarding-progress">
            <span className="onboarding-step-count">Step {step} of {totalSteps}</span>
            <div
              className="onboarding-track"
              role="progressbar"
              aria-valuemin={1}
              aria-valuemax={totalSteps}
              aria-valuenow={step}
              aria-label={`Step ${step} of ${totalSteps}`}
            >
              {Array.from({ length: totalSteps }, (_, index) => (
                <span
                  key={index}
                  className={'onboarding-tick'
                    + (index < step ? ' done' : '')
                    + (index === step - 1 ? ' current' : '')}
                  // Each tick paints its own slice of one track-wide gradient, so
                  // the completed run reads as a single sweep rather than as six
                  // separate bars that happen to be the same colour.
                  style={{ '--tick-index': index } as React.CSSProperties}
                />
              ))}
            </div>
          </div>
        )}
      </header>

      <main className="onboarding-main">
        <div className="onboarding-card">
          {eyebrow && <span className="onboarding-eyebrow">{eyebrow}</span>}
          <h1 className="onboarding-title">{title}</h1>
          {description && <p className="onboarding-desc">{description}</p>}
          {children && <div className="onboarding-body">{children}</div>}
        </div>
      </main>

      <footer className="onboarding-footer">
        <div className="onboarding-footer-inner">{footer}</div>
      </footer>
    </div>
  )
}

/**
 * A choice card.
 *
 * A button rather than a styled radio: the whole card is the target, it focuses
 * and activates from the keyboard on its own, and `aria-pressed` says what a
 * screen reader needs without a hidden input to keep in sync.
 */
export function ChoiceCard({
  selected, onSelect, title, detail, icon, multi = false,
}: {
  selected: boolean
  onSelect: () => void
  title: string
  detail?: string | null
  icon?: React.ReactNode
  multi?: boolean
}) {
  return (
    <button
      type="button"
      className={`choice-card${selected ? ' selected' : ''}`}
      aria-pressed={selected}
      onClick={onSelect}
    >
      {icon && <span className="choice-icon" aria-hidden="true">{icon}</span>}
      <span className="choice-text">
        <span className="choice-title">{title}</span>
        {detail && <span className="choice-detail">{detail}</span>}
      </span>
      <span className={`choice-mark${multi ? ' square' : ''}`} aria-hidden="true">
        {selected && <CheckMark />}
      </span>
    </button>
  )
}

function CheckMark() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
      <path d="M2.5 6.2 4.8 8.5 9.5 3.8" stroke="currentColor" strokeWidth="2"
            strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
