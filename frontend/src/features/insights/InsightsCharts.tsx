import type { EChartsCoreOption } from 'echarts/core'
import type { Insights, SpendingHistory, SpendingHistoryBucket } from '../../api/types'
import { EChart, type ChartClickEvent } from '../../components/EChart'
import { useTheme } from '../../hooks/useTheme'
import { formatCents, formatMinutes } from '../../lib/format'
import type { AnalyticsFilters, AnalyticsGroup, AnalyticsView } from './analytics'

/**
 * The chart surface's half of the design system.
 *
 * <p>These values are the CSS custom properties resolved to literals, because
 * ECharts paints into an SVG it owns and cannot read `var(--…)`. They must be
 * kept in step with `tokens.css`; the palette in particular is validated there
 * and must not be edited here in isolation.
 */
interface ChartTheme {
  text: string
  secondary: string
  muted: string
  grid: string
  border: string
  /** The panel the chart sits on — pie segment gaps are cut in this colour. */
  surface: string
  /** One step up, for tooltips, which must read as floating above the panel. */
  raised: string
  accent: string
  accentSoft: string
  /** Cyan: trips, operators, activity — the things that move rather than cost. */
  transit: string
  positive: string
  /** Over budget: the one place a chart mark is allowed to go red. */
  negative: string
  /** Series identity. Fixed order, assigned against a stable domain. */
  palette: string[]
  /** The tail of a long series list, past where the palette stays separable. */
  neutral: string
}

const LIGHT: ChartTheme = {
  text: '#1b1824', secondary: '#5c5869', muted: '#817c90', grid: '#e6e5ef',
  border: '#e3e1ea', surface: '#ffffff', raised: '#ffffff',
  accent: '#6547e7', accentSoft: '#e8e2fe',
  transit: '#1e6ff5', positive: '#0c875e', negative: '#eb0d33',
  /* Data is blue, not brand violet. A chart drawn in the brand colour reads as
     decoration; one drawn in the information colour reads as data. */
  palette: ['#3b82f6', '#12a695', '#a86c07', '#f43f5e', '#b5309b', '#2f7d4f'],
  neutral: '#9a94a8',
}

const DARK: ChartTheme = {
  text: '#f5f3f8', secondary: '#b9b4c2', muted: '#8d8797', grid: '#262340',
  border: '#2d2938', surface: '#191722', raised: '#211e2c',
  accent: '#9a82ff', accentSoft: '#30294d',
  transit: '#3b82f6', positive: '#10b981', negative: '#f43f5e',
  /* The same six steps as light. They were validated against both surfaces, so
     the categorical set does not need re-picking per mode — only the semantic
     inks do, and those live in tokens.css. */
  palette: ['#3b82f6', '#12a695', '#a86c07', '#f43f5e', '#b5309b', '#2f7d4f'],
  neutral: '#6b6580',
}

/**
 * What a time series is *about*, which is what decides its colour.
 *
 * <p>Money is purple, activity and time are blue, and savings is the positive
 * green it is everywhere else in the product. Flat, stable colours make these
 * read as analytical marks rather than decorative illustrations.
 */
type SeriesTone = 'transit' | 'positive' | 'money' | 'time'

function toneColor(tone: SeriesTone, theme: ChartTheme): string {
  if (tone === 'transit') return theme.transit
  if (tone === 'positive') return theme.positive
  if (tone === 'time') return theme.palette[5]
  return theme.accent
}

/**
 * Transit mode → palette slot, fixed by identity.
 *
 * <p>Modes are a *closed* domain, unlike operators, so their colour is assigned
 * from the mode itself rather than from its rank. This is what makes rail the
 * same violet in the mode comparison, in a route chip and on the raster mode tile; when
 * the assignment came from list order, filtering the busiest mode out repainted
 * every mode below it.
 *
 * <p>The slots mirror `--mode-*` in tokens.css, chosen so each mode lands on the
 * validated colour nearest its tile artwork.
 */
const MODE_SLOT: Record<string, number> = {
  RAIL: 4, TRAIN: 4,          // magenta — nearest slot to the violet rail plate
  SUBWAY: 0, METRO: 0,        // blue
  FERRY: 1,                   // teal
  TRAM: 2, LIGHT_RAIL: 2,     // amber
  BUS: 3,                     // coral
}

/**
 * Series identity, assigned against the *unfiltered* domain.
 *
 * <p>Built from the full history rather than the filtered view, so an operator
 * keeps its colour when cross-filtering removes the operators above it. The
 * previous implementation hashed the id into the palette, which was stable but
 * could collide — two operators the same colour, with nothing to say why.
 *
 * <p>Slot 6 is the shared tail: a seventh series folds into it rather than
 * inventing a hue the palette was never validated for.
 */
function domainColors(ids: string[], theme: ChartTheme): Map<string, string> {
  return new Map(ids.map((id, index) =>
    [id, theme.palette[Math.min(index, theme.palette.length - 1)]]))
}

/**
 * The same, for a closed domain that owns its colours outright.
 *
 * <p>Anything outside the known set — a walking leg, an operator-specific mode
 * the API starts returning — takes the neutral rather than borrowing a hue that
 * already means something else.
 */
function modeColors(ids: string[], theme: ChartTheme): Map<string, string> {
  return new Map(ids.map((id) => {
    const slot = MODE_SLOT[id?.toUpperCase?.() ?? '']
    return [id, slot === undefined ? theme.neutral : theme.palette[slot]]
  }))
}

/** ECharts wants rgba() literals for translucent fills, not an opacity channel. */
function alpha(hex: string, amount: number): string {
  const value = parseInt(hex.slice(1), 16)
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${amount})`
}

export function InsightsCharts({
  history, weekly, view, filters, onOperator, onMode, onBucket,
}: {
  history: SpendingHistory
  weekly: Insights
  view: AnalyticsView
  filters: AnalyticsFilters
  onOperator: (operator: string | null) => void
  onMode: (mode: string | null) => void
  onBucket: (date: string | null) => void
}) {
  const { resolved } = useTheme()
  const theme = resolved === 'dark' ? DARK : LIGHT
  const hasFilter = Boolean(filters.operator || filters.mode || filters.bucketDate)
  // Built from `history`, never from `view`: the filtered view's ordering shifts
  // as the reader cross-filters, and a series that changes colour when its
  // neighbours disappear is unreadable.
  const operatorColors = domainColors(history.byOperator.map((o) => o.provider), theme)
  const modeColorMap = modeColors(history.byMode.map((m) => m.mode), theme)

  if (view.observations.length === 0) {
    return (
      <div className="analytics-filter-empty" role="status">
        <strong>No completed trips match these filters</strong>
        <span>Clear one of the active filters to bring the dashboard back into view.</span>
      </div>
    )
  }

  const spending = view.buckets.map((bucket) => bucket.spentCents)
  const trips = view.buckets.map((bucket) => bucket.tripCount)
  const savings = view.buckets.map((bucket) => bucket.savedCents)
  const fares = view.buckets.map((bucket) => bucket.averageFareCents)
  const durations = view.buckets.map((bucket) => bucket.averageDurationMinutes)
  // Three, not two. Two points is a line segment, and a line segment drawn in a
  // gridded frame with an axis and a zoom handle claims to be a trend it cannot
  // support. At one or two active days the same figures are shown as a stat
  // comparison instead; three is the first length where a direction is real.
  const CHART_MIN_POINTS = 3
  const timeReady = view.distinctTripDays >= CHART_MIN_POINTS
  const sparseTime = view.distinctTripDays >= 2 && view.distinctTripDays < CHART_MIN_POINTS
  const bucketLabels = view.buckets.map((bucket) => bucket.label)
  const pointsOf = (values: Array<number | null>) =>
    bucketLabels.map((label, index) => ({ label, value: values[index] ?? null }))
        .filter((point) => point.value !== null)

  return (
    <div className="analytics-grid">
      {timeReady ? (
        <>
          <ChartPanel
            title="Spending over time"
            note="Click a point to filter the dashboard to that day"
            className="analytics-panel-wide"
          >
            <EChart
              option={timeOption(history, spending, 'Spending', 'line', theme, 'money')}
              ariaLabel={`Spending over ${history.rangeName}`}
              testId="chart-spending"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>

          <ChartPanel title="Trips over time">
            <EChart
              option={timeOption(history, trips, 'Completed trips', 'bar', theme, 'number', 'transit')}
              ariaLabel={`Trips over ${history.rangeName}`}
              testId="chart-trips"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>

          <ChartPanel title="Savings over time">
            {view.totals.savedCents === null ? (
              <ChartState
                title="No comparable trips in this view"
                detail="Savings appears only when a completed trip recorded a real comparison route."
              />
            ) : (
              <EChart
                option={timeOption(history, savings, 'Savings', 'line', theme, 'money', 'positive')}
                ariaLabel={`Savings over ${history.rangeName}`}
                testId="chart-savings"
                onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
              />
            )}
          </ChartPanel>

          <ChartPanel title="Average fare">
            <EChart
              option={timeOption(history, fares, 'Average fare', 'line', theme, 'money', 'money')}
              ariaLabel={`Average fare over ${history.rangeName}`}
              testId="chart-average-fare"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>

          <ChartPanel title="Average commute time">
            <EChart
              option={timeOption(history, durations, 'Average duration', 'line', theme, 'minutes', 'time')}
              ariaLabel={`Average commute time over ${history.rangeName}`}
              testId="chart-average-duration"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>
        </>
      ) : sparseTime ? (
        /* Two or three active days: the same figures, without a frame that
           would imply a trend they cannot support. */
        <>
          <ChartPanel
            title="Spending"
            note={`${view.distinctTripDays} days with completed trips in this period`}
            className="analytics-panel-wide"
          >
            <SparseSeries points={pointsOf(spending)} format={formatCents} label="spending" />
          </ChartPanel>
          <ChartPanel title="Trips">
            <SparseSeries points={pointsOf(trips)} format={(value) => `${value}`} label="trips" />
          </ChartPanel>
          <ChartPanel title="Average fare">
            <SparseSeries points={pointsOf(fares)} format={formatCents} label="fares" />
          </ChartPanel>
        </>
      ) : (
        <div className="analytics-history-short analytics-panel-wide" data-testid="history-sparse-state">
          <strong>One day of activity in this view</strong>
          <p>
            {formatCents(view.totals.spentCents)} across {view.totals.tripCount} completed
            trip{view.totals.tripCount === 1 ? '' : 's'}. Trends need trips on more than one day.
          </p>
        </div>
      )}

      <ChartPanel
        title="Spending by operator"
        note={view.byOperator.length > 1 ? 'Click a segment to cross-filter' : null}
      >
        {view.byOperator.length < 2 ? (
          /* A one-row chart adds axes without adding information. */
          <SingleShare
            rows={view.byOperator}
            total={view.totals.spentCents}
            empty="No operator spending in this view"
          />
        ) : (
          <EChart
            option={operatorOption(view.byOperator, theme, filters.operator, operatorColors)}
            ariaLabel="Spending by transit operator"
            testId="chart-operators"
            onClick={(event) => selectDimension(event, filters.operator, onOperator)}
          />
        )}
      </ChartPanel>

      <ChartPanel
        title="Spending by transit mode"
        note={view.byMode.length > 1 ? 'Click a segment to cross-filter' : null}
      >
        {view.byMode.length < 2 ? (
          <SingleShare
            rows={view.byMode}
            total={view.totals.spentCents}
            empty="No mode spending in this view"
          />
        ) : (
          <EChart
            option={modeOption(view.byMode, theme, filters.mode, modeColorMap)}
            ariaLabel="Spending by public-transit mode"
            testId="chart-modes"
            onClick={(event) => selectDimension(event, filters.mode, onMode)}
          />
        )}
      </ChartPanel>

      <ChartPanel
        title="Budget vs actual vs projected"
        note={hasFilter ? 'Projection hidden while cross-filtering' : budgetDelta(weekly)}
        className="analytics-panel-wide"
      >
        {weekly.weeklyBudgetCents === null ? (
          <ChartState
            title="Set a weekly budget to unlock this comparison"
            detail="FareFlow will compare actual and projected transportation spending against it."
          />
        ) : (
          <EChart
            option={budgetOption(history, weekly, view, theme, hasFilter)}
            ariaLabel="Weekly budget compared with actual and projected spending"
            testId="chart-budget"
            height={250}
          />
        )}
      </ChartPanel>

      <ChartPanel
        title="Fare vs trip duration"
        note="Click a point to filter by operator"
        className="analytics-panel-wide"
      >
        {view.observations.length < 2 ? (
          <ChartState
            title="One trip cannot show a relationship"
            detail={`${formatCents(view.observations[0].fareCents)} · ${formatMinutes(view.observations[0].durationMinutes)}. Complete another trip to compare fare and duration.`}
          />
        ) : (
          <EChart
            option={scatterOption(view, theme, operatorColors)}
            ariaLabel="Fare plotted against completed-trip duration"
            testId="chart-fare-duration"
            height={330}
            onClick={(event) => selectDimension(event, filters.operator, onOperator)}
            onLegendSelect={(name) => selectNamedGroup(
              name, view.byOperator, filters.operator, onOperator)}
          />
        )}
      </ChartPanel>
    </div>
  )
}

/**
 * A chart and its title. Deliberately almost nothing else.
 *
 * <p>This used to carry three more things: a rhetorical question under the
 * title ("When did transportation spending change?"), the series total set
 * large beside it, and a delta under that. All three were removed.
 *
 * <p>The question narrated what the reader was about to look at, which a chart
 * with a clear title does not need. The total was worse: it printed a number
 * the chart underneath already draws, and on the operator and mode panels it
 * printed the *name of the top category* as though it were a headline figure —
 * a data value standing in for a title. When the same total also appeared in
 * the summary row above, one number was on screen three times.
 *
 * <p>What is left is a title, and an optional `note` for something the chart
 * genuinely cannot say about itself — that it is clickable, or that a
 * projection is suppressed while filtered. A note is never a number.
 */
function ChartPanel({ title, note, className = '', children }: {
  title: string
  note?: string | null
  className?: string
  children: React.ReactNode
}) {
  return (
    <article className={`analytics-panel ${className}`.trim()}>
      <header className="analytics-panel-head">
        <h3>{title}</h3>
        {note && <p className="analytics-panel-note">{note}</p>}
      </header>
      {children}
    </article>
  )
}

/**
 * The fallback when a series is too short to be a chart.
 *
 * <p>Two or three points do not make a trend, but drawing them with a full
 * cartesian frame — axes, gridlines, a zoom handle — asserts that they do. The
 * honest form at that length is the numbers themselves, side by side, with the
 * change between the ends stated once.
 */
function SparseSeries({ points, format, label }: {
  points: Array<{ label: string; value: number | null }>
  format: (value: number) => string
  label: string
}) {
  const real = points.filter((point): point is { label: string; value: number } =>
    point.value !== null)
  if (real.length === 0) {
    return <ChartState title={`No ${label} recorded yet`} detail="Complete a trip to start this series." />
  }
  const first = real[0], last = real[real.length - 1]
  const delta = real.length > 1 ? last.value - first.value : null

  return (
    <div className="sparse-series" data-testid="sparse-series">
      <ol className="sparse-points">
        {real.map((point) => (
          <li key={point.label}>
            <span className="sparse-point-label">{point.label}</span>
            <span className="sparse-point-value numeric">{format(point.value)}</span>
          </li>
        ))}
      </ol>
      {delta !== null && delta !== 0 && (
        <p className={`sparse-delta${delta > 0 ? ' is-up' : ' is-down'}`}>
          {delta > 0 ? '↑' : '↓'} {format(Math.abs(delta))} between these {real.length} points
        </p>
      )}
    </div>
  )
}

function ChartState({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="analytics-chart-state" role="status">
      <strong>{title}</strong>
      <span>{detail}</span>
    </div>
  )
}

/**
 * What a one-category breakdown looks like without unnecessary chart chrome.
 *
 * <p>Stating the name and the amount is the whole content; a ring drawn around
 * a single 100% segment adds a shape but no information.
 */
function SingleShare({ rows, total, empty }: {
  rows: Array<{ id: string; name: string; spentCents: number }>
  total: number
  empty: string
}) {
  const row = rows[0]
  if (!row) return <ChartState title={empty} detail="Complete a trip to populate this breakdown." />
  return (
    <div className="single-share" data-testid="single-share">
      <span className="single-share-name">{row.name}</span>
      <span className="single-share-value numeric">{formatCents(row.spentCents)}</span>
      <span className="single-share-note">
        {total > 0 && row.spentCents === total
          ? 'All spending in this view'
          : total > 0 ? `${Math.round((row.spentCents / total) * 100)}% of spending in this view` : ''}
      </span>
    </div>
  )
}

function timeOption(
  history: SpendingHistory,
  values: Array<number | null>,
  name: string,
  type: 'line' | 'bar',
  theme: ChartTheme,
  unit: 'money' | 'minutes' | 'number',
  tone: SeriesTone = 'money',
): EChartsCoreOption {
  const data = values.map((value, index) => ({
    value,
    filterId: history.buckets[index]?.date,
  }))
  const solid = toneColor(tone, theme)
  const zoom = dataZoom(history.buckets.length, theme, solid)

  return {
    animationDuration: 220,
    animationEasing: 'cubicOut',
    color: [solid],
    grid: { left: 64, right: 24, top: 22, bottom: zoom.length > 1 ? 58 : 38 },
    tooltip: tooltip(theme, 'axis'),
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    xAxis: categoryAxis(history.buckets, theme),
    yAxis: valueAxis(theme, unit),
    dataZoom: zoom,
    series: [{
      name,
      type,
      data,
      emphasis: { focus: 'series' },
      tooltip: { valueFormatter: (value: unknown) => formatUnit(value, unit) },
      ...(type === 'line' ? {
        smooth: false,
        connectNulls: false,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: history.buckets.length <= 18,
        lineStyle: { width: 2, color: solid },
        // Points are ringed in the panel colour so a dense series reads as
        // separate marks rather than as a beaded string.
        itemStyle: { color: solid, borderColor: theme.surface, borderWidth: 1.5 },
      } : {
        barMaxWidth: 28,
        itemStyle: { color: solid, borderRadius: [2, 2, 0, 0] },
      }),
    }],
  }
}

/**
 * Spending by operator: horizontal bars, direct-labelled, one colour per
 * operator held stable across every filter.
 *
 * <p>Selection is carried by two channels, not one. The chosen bar keeps full
 * saturation while the rest drop to .22, *and* it gains a ring in the panel
 * colour — so "which one did I pick" survives being read in greyscale.
 */
function operatorOption(
  groups: AnalyticsGroup[], theme: ChartTheme, selected: string | null,
  colors: Map<string, string>,
): EChartsCoreOption {
  return {
    animationDuration: 220,
    animationEasing: 'cubicOut',
    // Room for the direct label at the end of the longest bar; at 34 the last
    // two characters of a four-figure amount were being clipped.
    grid: { left: 118, right: 68, top: 18, bottom: 42 },
    tooltip: tooltip(theme, 'item'),
    xAxis: valueAxis(theme, 'money'),
    yAxis: {
      type: 'category',
      data: groups.map((group) => group.name),
      inverse: true,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: {
        fontSize: 11, width: 105, overflow: 'truncate',
        // The selected row's label steps up to primary ink and semibold, so the
        // axis agrees with the bar about what is selected.
        color: (name: string) => {
          const group = groups.find((candidate) => candidate.name === name)
          return selected && group?.id === selected ? theme.text : theme.secondary
        },
        fontWeight: (name: string) => {
          const group = groups.find((candidate) => candidate.name === name)
          return selected && group?.id === selected ? 600 : 400
        },
      },
    },
    series: [{
      name: 'Spending', type: 'bar', barMaxWidth: 24,
      data: groups.map((group) => {
        const active = !selected || selected === group.id
        return {
          name: group.name, value: group.spentCents, filterId: group.id,
          itemStyle: {
            color: colorOf(group.id, colors, theme),
            opacity: active ? 1 : .22,
            borderRadius: [0, 2, 2, 0],
            borderColor: selected === group.id ? theme.text : 'transparent',
            borderWidth: selected === group.id ? 1 : 0,
          },
        }
      }),
      label: {
        show: true, position: 'right', color: theme.text, fontSize: 11,
        formatter: (params: { value?: unknown }) => formatUnit(params.value, 'money'),
      },
      tooltip: { valueFormatter: (value: unknown) => formatUnit(value, 'money') },
      emphasis: { focus: 'self', itemStyle: { opacity: 1 } },
    }],
  }
}

/** Spending by transit mode, direct-labelled for quick comparison. */
function modeOption(
  groups: AnalyticsGroup[], theme: ChartTheme, selected: string | null,
  colors: Map<string, string>,
): EChartsCoreOption {
  const total = groups.reduce((sum, group) => sum + group.spentCents, 0)
  return {
    animationDuration: 220,
    animationEasing: 'cubicOut',
    color: groups.map((group) => colorOf(group.id, colors, theme)),
    tooltip: tooltip(theme, 'item'),
    grid: { left: 104, right: 92, top: 18, bottom: 36 },
    xAxis: valueAxis(theme, 'money'),
    yAxis: {
      type: 'category', inverse: true,
      data: groups.map((group) => group.name),
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: theme.secondary, fontSize: 11, width: 92, overflow: 'truncate' },
    },
    series: [{
      name: 'Transit mode', type: 'bar', barMaxWidth: 22,
      data: groups.map((group) => {
        const active = !selected || selected === group.id
        return {
          name: group.name, value: group.spentCents, filterId: group.id,
          itemStyle: {
            color: colorOf(group.id, colors, theme),
            borderRadius: [0, 2, 2, 0],
            borderColor: selected === group.id ? theme.text : 'transparent',
            borderWidth: selected === group.id ? 1 : 0,
            opacity: active ? 1 : .22,
          },
        }
      }),
      label: {
        show: true, position: 'right', color: theme.text, fontSize: 11,
        formatter: (params: { value?: unknown }) => {
          const value = Number(params.value ?? 0)
          const share = total > 0 ? Math.round((value / total) * 100) : 0
          return `${formatCents(value)} · ${share}%`
        },
      },
      tooltip: { valueFormatter: (value: unknown) => formatUnit(value, 'money') },
      emphasis: { focus: 'self', itemStyle: { opacity: 1 } },
    }],
  }
}

function budgetOption(
  history: SpendingHistory,
  weekly: Insights,
  view: AnalyticsView,
  theme: ChartTheme,
  filtered: boolean,
): EChartsCoreOption {
  const budget = weekly.weeklyBudgetCents ?? 0
  const weekStart = mondayOf(history.endDate)
  const filteredWeekSpend = view.observations
    .filter((trip) => trip.tripDate >= weekStart)
    .reduce((total, trip) => total + trip.fareCents, 0)
  const actual = filtered ? filteredWeekSpend : weekly.spentCents
  const projected = filtered ? null : weekly.personalization?.projectedWeeklySpendCents ?? null
  const maximum = Math.max(budget, actual, projected ?? 0, 1)
  const series: EChartsCoreOption[] = []

  // Actual is money, so it is purple. Projected is a forecast rather than a
  // fact, so it is cyan and thinner — the eye reads the solid bar first and the
  // prediction second, which is the order they deserve.
  const over = actual > budget
  series.push({
    name: 'Actual', type: 'bar', barWidth: 24,
    data: [actual],
    itemStyle: {
      color: over ? theme.negative : theme.accent,
      borderRadius: [0, 2, 2, 0],
    },
    label: { show: true, position: 'right', color: theme.text,
      formatter: () => formatCents(actual) },
    markLine: {
      symbol: 'none', silent: true,
      // The budget is a limit, not a series: a dashed rule in secondary ink,
      // which is what keeps it from competing with the two bars measured
      // against it.
      lineStyle: { color: theme.secondary, width: 2, type: 'dashed' },
      // Without an explicit rotate the label inherits the rule's own direction
      // and is printed sideways, which is unreadable on a vertical limit line.
      label: { show: true, formatter: `Budget ${formatCents(budget)}`,
        color: theme.secondary, position: 'end', rotate: 0,
        distance: 6, fontSize: 11 },
      data: [{ xAxis: budget }],
    },
    tooltip: { valueFormatter: (value: unknown) => formatUnit(value, 'money') },
  })
  if (projected !== null) {
    series.push({
      name: 'Projected', type: 'bar', barWidth: 12, barGap: '-72%',
      data: [projected],
      // Outlined rather than filled. Projected spend is a forecast, and when it
      // runs past actual — which is the case worth noticing — a solid bar makes
      // the prediction the biggest object in the panel. An outline keeps it
      // legible as a reach without letting it read as money already spent.
      itemStyle: {
        color: alpha(theme.transit, .16),
        borderColor: theme.transit, borderWidth: 1.5,
        borderRadius: [0, 2, 2, 0],
      },
      label: { show: true, position: 'right', color: theme.text,
        formatter: () => formatCents(projected) },
      tooltip: { valueFormatter: (value: unknown) => formatUnit(value, 'money') },
    })
  }

  return {
    animationDuration: 220,
    animationEasing: 'cubicOut',
    color: [theme.accent, theme.transit],
    grid: { left: 72, right: 90, top: 44, bottom: 42 },
    tooltip: tooltip(theme, 'axis'),
    legend: { top: 0, left: 0, textStyle: { color: theme.secondary, fontSize: 11 } },
    xAxis: { ...valueAxis(theme, 'money'), max: Math.ceil(maximum * 1.18) },
    yAxis: {
      type: 'category', data: ['Current week'],
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: theme.secondary, fontSize: 11 },
    },
    series,
  }
}

/**
 * Fare against duration, one colour per operator.
 *
 * <p>A scatter is the strictest case for colour: any two marks can land beside
 * each other, so the palette has to hold up pairwise rather than merely between
 * neighbours. It is validated that way for four operators. Beyond four the tail
 * folds into one neutral series rather than reaching for hues the palette cannot
 * keep apart — the points are all still plotted, still clickable, and the
 * tooltip still names the real operator behind each one.
 */
function scatterOption(
  view: AnalyticsView, theme: ChartTheme, colors: Map<string, string>,
): EChartsCoreOption {
  const COLOURED = 4
  const operators = [...new Set(view.observations.map((trip) => trip.provider))]
  const ranked = view.byOperator
    .map((group) => group.id)
    .filter((id) => operators.includes(id))
  const named = ranked.slice(0, COLOURED)
  const folded = operators.filter((operator) => !named.includes(operator))

  const groups: Array<{ label: string; ids: string[]; color: string }> = named.map((id) => ({
    label: view.byOperator.find((group) => group.id === id)?.name ?? id,
    ids: [id],
    color: colorOf(id, colors, theme),
  }))
  if (folded.length > 0) {
    groups.push({
      label: `${folded.length} other operator${folded.length === 1 ? '' : 's'}`,
      ids: folded,
      color: theme.neutral,
    })
  }

  return {
    animationDuration: 220,
    animationEasing: 'cubicOut',
    color: groups.map((group) => group.color),
    grid: { left: 64, right: 26, top: 34, bottom: 66 },
    tooltip: {
      ...tooltip(theme, 'item'),
      formatter: (raw: unknown) => {
        const param = raw as {
          seriesName?: string
          data?: { value?: unknown[]; route?: string; operatorName?: string }
        }
        const values = param.data?.value ?? []
        return [
          `<strong>${escapeHtml(param.data?.operatorName ?? param.seriesName ?? 'Trip')}</strong>`,
          escapeHtml(param.data?.route ?? ''),
          `${formatUnit(values[1], 'money')} · ${formatUnit(values[0], 'minutes')}`,
        ].join('<br>')
      },
    },
    // Bottom-left, because at the top it sat on the y-axis name.
    legend: {
      type: 'scroll', bottom: 0, left: 'center', icon: 'circle',
      textStyle: { color: theme.secondary, fontSize: 11 },
      pageTextStyle: { color: theme.muted },
    },
    xAxis: { ...valueAxis(theme, 'minutes'), name: 'Trip duration' },
    yAxis: { ...valueAxis(theme, 'money'), name: 'Fare', nameGap: 14 },
    series: groups.map((group) => {
      const trips = view.observations.filter((trip) => group.ids.includes(trip.provider))
      return {
        name: group.label,
        type: 'scatter',
        symbolSize: 11,
        data: trips.map((trip) => ({
          value: [trip.durationMinutes, trip.fareCents],
          filterId: trip.provider,
          operatorName: trip.providerName,
          route: `${trip.origin} → ${trip.destination}`,
        })),
        // A 1.5px ring in the panel colour keeps overlapping points countable
        // instead of merging into one blob.
        itemStyle: {
          color: group.color, opacity: .88,
          borderColor: theme.surface, borderWidth: 1.5,
        },
        emphasis: { focus: 'series', scale: 1.45 },
      }
    }),
  }
}

function tooltip(theme: ChartTheme, trigger: 'axis' | 'item') {
  return {
    trigger,
    // The crosshair is the one piece of chrome that should feel live, so it
    // takes the transit cyan rather than a grey.
    axisPointer: trigger === 'axis' ? { type: 'line', snap: true,
      lineStyle: { color: theme.transit, type: 'solid', opacity: .55 } } : undefined,
    // A tooltip has to read as floating above the panel, so it takes the raised
    // surface and a brand-tinted hairline rather than the panel's own colours.
    backgroundColor: theme.raised,
    borderColor: theme.accentSoft,
    borderWidth: 1,
    textStyle: { color: theme.text, fontSize: 12 },
    extraCssText: 'box-shadow:0 8px 24px rgba(10,9,24,.28);border-radius:8px;padding:8px 10px;',
  }
}

function categoryAxis(buckets: SpendingHistoryBucket[], theme: ChartTheme) {
  return {
    type: 'category', boundaryGap: true,
    data: buckets.map((bucket) => bucket.label),
    axisLine: { lineStyle: { color: theme.border } },
    axisTick: { show: false },
    axisLabel: { color: theme.muted, fontSize: 10, hideOverlap: true },
  }
}

function valueAxis(theme: ChartTheme, unit: 'money' | 'minutes' | 'number') {
  return {
    type: 'value',
    axisLine: { show: false }, axisTick: { show: false },
    axisLabel: { color: theme.muted, fontSize: 10,
      formatter: (value: number) => formatAxis(value, unit) },
    splitLine: { lineStyle: { color: theme.grid, type: 'solid', width: 1 } },
    nameTextStyle: { color: theme.muted, fontSize: 10, padding: [0, 0, 6, 0] },
  }
}

function dataZoom(length: number, theme: ChartTheme, accent: string): EChartsCoreOption[] {
  const controls: EChartsCoreOption[] = [{
    type: 'inside', xAxisIndex: 0, filterMode: 'none', zoomOnMouseWheel: true,
    moveOnMouseMove: true, moveOnMouseWheel: false,
  }]
  if (length > 8) {
    controls.push({
      type: 'slider', xAxisIndex: 0, filterMode: 'none', height: 15, bottom: 8,
      borderColor: theme.border, backgroundColor: theme.surface,
      // The brushed window is tinted in the series' own colour, so the control
      // belongs to the chart above it rather than to the page chrome.
      fillerColor: alpha(accent, .18), dataBackground: {
        lineStyle: { color: theme.muted }, areaStyle: { color: theme.grid },
      }, selectedDataBackground: {
        lineStyle: { color: accent }, areaStyle: { color: alpha(accent, .28) },
      }, handleStyle: { color: accent, borderColor: accent },
      textStyle: { color: theme.muted, fontSize: 9 },
    })
  }
  return controls
}

function selectBucket(
  event: ChartClickEvent, current: string | null, select: (value: string | null) => void,
) {
  const id = filterId(event)
  if (id) select(current === id ? null : id)
}

function selectDimension(
  event: ChartClickEvent, current: string | null, select: (value: string | null) => void,
) {
  const id = filterId(event)
  if (id) select(current === id ? null : id)
}

function filterId(event: ChartClickEvent): string | null {
  if (!event.data || typeof event.data !== 'object') return null
  const value = (event.data as { filterId?: unknown }).filterId
  return typeof value === 'string' ? value : null
}



function selectNamedGroup(
  name: string,
  groups: AnalyticsGroup[],
  current: string | null,
  select: (value: string | null) => void,
) {
  const group = groups.find((candidate) => candidate.name === name)
  if (group) select(current === group.id ? null : group.id)
}


/**
 * The projected buffer, which is the one number the budget chart cannot draw.
 *
 * <p>Kept when the panel's headline value was removed: that value merely
 * reprinted the budget, but this is a derived figure that appears nowhere else
 * on the page.
 */
function budgetDelta(weekly: Insights): string | null {
  const budget = weekly.weeklyBudgetCents
  const projected = weekly.personalization?.projectedWeeklySpendCents
  if (budget === null || projected == null) return null
  const difference = budget - projected
  return difference >= 0
    ? `${formatCents(difference)} projected buffer`
    : `${formatCents(Math.abs(difference))} projected over budget`
}

function mondayOf(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`)
  const weekday = parsed.getUTCDay()
  parsed.setUTCDate(parsed.getUTCDate() - (weekday === 0 ? 6 : weekday - 1))
  return parsed.toISOString().slice(0, 10)
}


function formatAxis(value: number, unit: 'money' | 'minutes' | 'number'): string {
  if (unit === 'money') return `$${(value / 100).toFixed(value >= 10_000 ? 0 : 2)}`
  if (unit === 'minutes') return `${value}m`
  return String(value)
}

function formatUnit(value: unknown, unit: 'money' | 'minutes' | 'number'): string {
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) return 'No data'
  if (unit === 'money') return formatCents(numeric)
  if (unit === 'minutes') return formatMinutes(numeric)
  return String(numeric)
}

/** A colour from the stable domain map, falling back to the shared tail slot. */
function colorOf(id: string, colors: Map<string, string>, theme: ChartTheme): string {
  return colors.get(id) ?? theme.palette[theme.palette.length - 1]
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  }[character] ?? character))
}
