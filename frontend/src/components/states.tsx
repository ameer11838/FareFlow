import type { ApiError } from '../api/client'
import { AlertIcon } from './Icons'
import { Tile } from './Tile'
import type { TileName } from './tileNames'

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite" aria-busy="true">
      <span className="state-icon"><span className="skeleton" style={{ width: 16, height: 16 }} /></span>
      <div className="state-text">
        <span className="skeleton" style={{ height: 10, width: 180 }} />
        <span className="skeleton" style={{ height: 9, width: 260, marginTop: 6 }} />
      </div>
      <span className="visually-hidden">{label}</span>
    </div>
  )
}

/**
 * Empty and error states are one horizontal notice.
 *
 * <p>They used to be centred blocks 350px tall with an icon floating in the
 * middle. "Nothing has happened yet" is a one-line fact; giving it a third of
 * the viewport makes an empty account look broken rather than new, and it was
 * the single biggest source of dead space in the product.
 *
 * <p>The action sits on the same line as the message, at the end, where the eye
 * already is after reading it.
 *
 * <p>The notice carries a raster tile rather than a hairline glyph. It is the
 * one place in a quiet screen where the product gets to have a voice, and 40px
 * of plate colour does that without costing the layout anything — the row is
 * the same height either way, because the text block was always the taller of
 * the two.
 */
export function ErrorState({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <span className="state-plate state-plate-alert">
        <Tile name="notifications/error" size={38} />
        {/* The SVG stays as the fallback: if the raster ever fails to load, the
            notice must still look like an error rather than like a gap. */}
        <AlertIcon size={18} className="state-plate-fallback" />
      </span>
      <div className="state-text">
        <p className="state-title">{error.problem.title ?? 'Something went wrong'}</p>
        <p className="state-body">{error.message}</p>
      </div>
      {onRetry && (
        <div className="state-action">
          <button className="btn btn-sm" onClick={onRetry}>Try again</button>
        </div>
      )}
    </div>
  )
}

export function EmptyState({ title, description, action, icon, tile = 'actions-ui/search' }: {
  title: string
  description?: string
  action?: React.ReactNode
  /** Escape hatch for a caller that genuinely needs a glyph instead of a tile. */
  icon?: React.ReactNode
  /**
   * The tile that says what kind of nothing this is — an empty wallet and an
   * empty trip history are different facts and should not open with the same
   * picture.
   */
  tile?: TileName
}) {
  return (
    <div className="state">
      <span className="state-plate">
        {icon ?? <Tile name={tile} size={38} />}
      </span>
      <div className="state-text">
        <p className="state-title">{title}</p>
        {description && <p className="state-body">{description}</p>}
      </div>
      {action && <div className="state-action">{action}</div>}
    </div>
  )
}
