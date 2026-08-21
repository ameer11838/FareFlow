import { useTheme, type ThemePreference } from '../hooks/useTheme'
import { MoonIcon, SunIcon, SystemIcon } from './Icons'

const OPTIONS: { id: ThemePreference; label: string; Icon: typeof SunIcon }[] = [
  { id: 'light', label: 'Light', Icon: SunIcon },
  { id: 'dark', label: 'Dark', Icon: MoonIcon },
  { id: 'system', label: 'System', Icon: SystemIcon },
]

/**
 * The full three-way control, for Settings.
 *
 * <p>A segmented control rather than a switch, because there are three states and
 * "System" is a real choice. A two-position switch would force a rider to pick a
 * theme when what they usually want is "whatever my laptop is doing".
 */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme()

  return (
    <div className="theme-toggle" role="radiogroup" aria-label="Colour theme">
      {OPTIONS.map(({ id, label, Icon }) => (
        <button
          key={id}
          type="button"
          role="radio"
          aria-checked={preference === id}
          className="theme-option"
          onClick={() => setPreference(id)}
        >
          <Icon size={15} />
          <span>{label}</span>
        </button>
      ))}
    </div>
  )
}

/**
 * The compact version for the navigation bar.
 *
 * <p>Cycles light → dark → system. A single button cannot show three states at
 * once, so it shows the one that is active and names the next in its label —
 * the full control lives in Settings for anyone who wants to see all three.
 */
export function ThemeButton() {
  const { preference, resolved, setPreference } = useTheme()

  const next: ThemePreference =
    preference === 'light' ? 'dark' : preference === 'dark' ? 'system' : 'light'
  const Icon = preference === 'system' ? SystemIcon : resolved === 'dark' ? MoonIcon : SunIcon
  const current = preference === 'system' ? `System (${resolved})` : resolved === 'dark' ? 'Dark' : 'Light'

  return (
    <button
      type="button"
      className="theme-button"
      onClick={() => setPreference(next)}
      title={`Theme: ${current}. Switch to ${next}.`}
      aria-label={`Theme: ${current}. Switch to ${next}.`}
    >
      <Icon size={16} />
    </button>
  )
}
