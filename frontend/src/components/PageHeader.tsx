import { Tile } from './Tile'
import type { TileName } from './tileNames'

/**
 * The band every screen opens with.
 *
 * <p>`tile` is optional and deliberately so. A page that is *about* one thing —
 * the wallet, the assistant, insights — earns an identifying plate beside its
 * title; a page that is a list of many things does not, and giving every screen
 * one would turn a signal into wallpaper.
 */
export function PageHeader({ eyebrow, title, subtitle, actions, tile }: {
  eyebrow?: string
  title: string
  subtitle?: string
  actions?: React.ReactNode
  tile?: TileName
}) {
  return (
    <header className={`page-header${tile ? ' page-header-tiled' : ''}`}>
      <div className="page-header-row">
        {tile && (
          <span className="page-header-plate" aria-hidden="true">
            <Tile name={tile} size={44} />
          </span>
        )}
        <div className="page-header-text">
          {eyebrow && <p className="page-eyebrow">{eyebrow}</p>}
          <h1 className="page-title">{title}</h1>
          {subtitle && <p className="page-subtitle">{subtitle}</p>}
        </div>
        {actions}
      </div>
    </header>
  )
}
