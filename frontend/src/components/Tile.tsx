import type { TileName } from './tileNames'

/**
 * A raster tile from the FareFlow icon sheet.
 *
 * <p>These are the expressive half of the icon system: a rounded plate with a
 * white glyph on it, in the colour the sheet assigned to that concept — violet
 * for rail, green for bus, amber for a delay. They carry the brand in a way a
 * 1.5px stroke cannot, and they are the right thing at the top of an empty
 * state or beside a section title.
 *
 * <p>They are also raster, sourced at roughly 30-86px, and they cannot inherit
 * <code>currentColor</code>. So they are used <em>large and decoratively</em>.
 * Inline with text, under 24px, or anywhere the colour has to follow the
 * surface, use the SVG set in {@link ./Icons.tsx} instead. The two sets are
 * complements, not alternatives, and mixing them at the same size in the same
 * row is the one thing that makes both look wrong.
 */

/* Vite resolves every tile at build time, so a name that no longer has a file
   fails here rather than as a broken image in the browser. */
const urls = import.meta.glob('../assets/tiles/**/*.png', {
  eager: true, query: '?url', import: 'default',
}) as Record<string, string>

const byName: Record<string, string> = {}
for (const [filePath, url] of Object.entries(urls)) {
  const match = filePath.match(/tiles\/(.+)\.png$/)
  if (match) byName[match[1]] = url
}

export interface TileProps {
  name: TileName
  /** Rendered px. Below 28 the artwork goes soft — reach for an SVG icon. */
  size?: number
  /**
   * Tiles are decorative by default and are hidden from assistive tech. Pass a
   * label only when the tile is the *only* carrier of a meaning — a mode badge
   * with no visible text beside it.
   */
  alt?: string
  className?: string
}

export function Tile({ name, size = 40, alt, className }: TileProps) {
  const src = byName[name]
  if (!src) return null

  return (
    <img
      src={src}
      width={size}
      height={size}
      alt={alt ?? ''}
      aria-hidden={alt ? undefined : true}
      loading="lazy"
      decoding="async"
      draggable={false}
      className={['tile', className].filter(Boolean).join(' ')}
      style={{ width: size, height: size }}
    />
  )
}

/** The tile that represents a transit mode, keyed off the API's mode strings. */
export const MODE_TILE: Record<string, TileName> = {
  rail: 'transit-modes/train',
  train: 'transit-modes/train',
  subway: 'transit-modes/subway',
  bus: 'transit-modes/bus',
  tram: 'transit-modes/light-rail',
  light_rail: 'transit-modes/light-rail',
  ferry: 'transit-modes/ferry',
  walk: 'transit-modes/walking',
  walking: 'transit-modes/walking',
}

export function ModeTile({ mode, size = 40, alt, className }: { mode: string } & Omit<TileProps, 'name'>) {
  const name = MODE_TILE[mode] ?? 'transit-modes/train'
  return <Tile name={name} size={size} alt={alt} className={className} />
}
