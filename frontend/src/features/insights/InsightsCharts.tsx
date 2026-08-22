import type { EChartsCoreOption } from 'echarts/core'
import type { Insights, SpendingHistory, SpendingHistoryBucket } from '../../api/types'
import { EChart, type ChartClickEvent } from '../../components/EChart'
import { useTheme } from '../../hooks/useTheme'
import { formatCents, formatMinutes, formatOptionalCents } from '../../lib/format'
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
  /**
   * Cyan → purple. The only gradient the dashboard is allowed, and it is spent
   * on one series: spending over time, the chart the page exists to show.
   */
  flow: [string, string]
  /** Series identity. Fixed order, assigned against a stable domain. */
  palette: string[]
  /** The tail of a long series list, past where the palette stays separable. */
  neutral: string
}

const LIGHT: ChartTheme = {
  text: '#1b1824', secondary: '#5c5869', muted: '#817c90', grid: '#e6e5ef',
  border: '#e3e1ea', surface: '#ffffff', raised: '#ffffff',
  accent: '#6547e7', accentSoft: '#e8e2fe',
  transit: '#0094a8', positive: '#0f7a4f', negative: '#b3261e',
  flow: ['#0094a8', '#6547e7'],
  palette: ['#0094a8', '#9c65ff', '#a91d8e', '#006c4b', '#a67200', '#173ebb'],
  neutral: '#9a94a8',
}

const DARK: ChartTheme = {
  text: '#f5f3f8', secondary: '#b9b4c2', muted: '#8d8797', grid: '#262340',
  border: '#2d2938', surface: '#191722', raised: '#211e2c',
  accent: '#9a82ff', accentSoft: '#30294d',
  transit: '#22d3ee', positive: '#3ddc9a', negative: '#ff8a80',
  flow: ['#22d3ee', '#9a82ff'],
  palette: ['#00a4ba', '#a06fff', '#b72e9a', '#007653', '#b57d00', '#2e5bda'],
  neutral: '#6b6580',
}

/**
 * What a time series is *about*, which is what decides its colour.
 *
 * <p>Money is purple, activity and time are cyan and blue, savings is the
 * positive green it is everywhere else in the product, and the one headline
 * series — spending — carries the cyan→purple flow. A reader who has learned the
 * page once can tell which question a chart answers before reading its title.
 */
type SeriesTone = 'flow' | 'transit' | 'positive' | 'money' | 'time'

function toneColor(tone: SeriesTone, theme: ChartTheme): string {
  if (tone === 'transit') return theme.transit
  if (tone === 'positive') return theme.positive
  if (tone === 'time') return theme.palette[5]
  // 'flow' resolves to its purple end wherever a flat colour is required —
  // symbols, the zoom handle — so the series still reads as one object.
  return theme.accent
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
  const modeColors = domainColors(history.byMode.map((m) => m.mode), theme)

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
  const timeReady = view.distinctTripDays >= 2

  return (
    <div className="analytics-grid">
      {timeReady ? (
        <>
          <ChartPanel
            title="Spending over time"
            question="When did transportation spending change?"
            value={formatCents(view.totals.spentCents)}
            delta={seriesDelta(activeValues(view.buckets, spending), 'money')}
            className="analytics-panel-wide"
          >
            <EChart
              option={timeOption(history, spending, 'Spending', 'line', theme, 'money', 'flow')}
              ariaLabel={`Spending over ${history.rangeName}`}
              testId="chart-spending"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>

          <ChartPanel
            title="Trips over time"
            question="How often did you travel?"
            value={`${view.totals.tripCount} trip${view.totals.tripCount === 1 ? '' : 's'}`}
            delta={seriesDelta(activeValues(view.buckets, trips), 'number')}
          >
            <EChart
              option={timeOption(history, trips, 'Completed trips', 'bar', theme, 'number', 'transit')}
              ariaLabel={`Trips over ${history.rangeName}`}
              testId="chart-trips"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>

          <ChartPanel
            title="Savings over time"
            question="Where did route choices save money?"
            value={formatOptionalCents(view.totals.savedCents, 'Not available')}
            delta={seriesDelta(activeValues(view.buckets, savings), 'money')}
          >
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

          <ChartPanel
            title="Average fare trend"
            question="Is each trip becoming more or less expensive?"
            value={formatOptionalCents(view.totals.averageFareCents, 'Not available')}
            delta={seriesDelta(activeValues(view.buckets, fares), 'money')}
          >
            <EChart
              option={timeOption(history, fares, 'Average fare', 'line', theme, 'money', 'money')}
              ariaLabel={`Average fare over ${history.rangeName}`}
              testId="chart-average-fare"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>

          <ChartPanel
            title="Average commute time"
            question="Is travel taking longer?"
            value={view.totals.averageDurationMinutes === null
              ? 'Not available' : formatMinutes(view.totals.averageDurationMinutes)}
            delta={seriesDelta(activeValues(view.buckets, durations), 'minutes')}
          >
            <EChart
              option={timeOption(history, durations, 'Average duration', 'line', theme, 'minutes', 'time')}
              ariaLabel={`Average commute time over ${history.rangeName}`}
              testId="chart-average-duration"
              onClick={(event) => selectBucket(event, filters.bucketDate, onBucket)}
            />
          </ChartPanel>
        </>
      ) : (
        <div className="analytics-history-short analytics-panel-wide" data-testid="history-sparse-state">
          <span className="analytics-kicker">Not enough history yet</span>
          <strong>{formatCents(view.totals.spentCents)} across {view.totals.tripCount} completed trip{view.totals.tripCount === 1 ? '' : 's'}</strong>
          <p>FareFlow has one day of real activity in this view. Complete trips on another day to unlock meaningful spending, trip, savings, fare, and commute trends.</p>
        </div>
      )}

      <ChartPanel
        title="Spending by operator"
        question="Which operator receives most of your transit spend?"
        value={view.byOperator[0]?.name ?? 'Not available'}
        delta={view.byOperator[0]
          ? `${share(view.byOperator[0].spentCents, view.totals.spentCents)} of filtered spend`
          : null}
      >
        <EChart
          option={operatorOption(view.byOperator, theme, filters.operator, operatorColors)}
          ariaLabel="Spending by transit operator"
          testId="chart-operators"
          onClick={(event) => selectDimension(event, filters.operator, onOperator)}
        />
      </ChartPanel>

      <ChartPanel
        title="Spending by transit mode"
        question="How is spending split across bus, train, subway, and ferry?"
        value={view.byMode[0]?.name ?? 'Not available'}
        delta={view.byMode[0]
          ? `${share(view.byMode[0].spentCents, view.totals.spentCents)} of filtered spend`
          : null}
      >
        <EChart
          option={modeOption(view.byMode, theme, filters.mode, modeColors)}
          ariaLabel="Spending by public-transit mode"
          testId="chart-modes"
          onClick={(event) => selectDimension(event, filters.mode, onMode)}
          onLegendSelect={(name) => selectNamedGroup(name, view.byMode, filters.mode, onMode)}
        />
      </ChartPanel>

      <ChartPanel
        title="Budget vs actual vs projected"
        question="Is this week tracking within budget?"
        value={weekly.weeklyBudgetCents === null
          ? 'No budget set' : formatCents(weekly.weeklyBudgetCents)}
        delta={hasFilter ? 'Projection hidden while cross-filtering' : budgetDelta(weekly)}
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
        question="Are higher fares actually buying faster trips?"
        value={`${view.observations.length} trip${view.observations.length === 1 ? '' : 's'}`}
        delta="Click a point to filter by operator"
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

function ChartPanel({
  title, question, value, delta, className = '', children,
}: {
  title: string
  question: string
  value: string
  delta: string | null
  className?: string
  children: React.ReactNode
}) {
  return (
    <article className={`analytics-panel ${className}`.trim()}>
      <header className="analytics-panel-head">
        <div>
          <h3>{title}</h3>
          <p>{question}</p>
        </div>
        <div className="analytics-panel-value">
          <strong>{value}</strong>
          {delta && <span>{delta}</span>}
        </div>
      </header>
      {children}
    </article>
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
  // The flow is a left-to-right sweep across the plot, not a per-segment tint:
  // it reads as one line that travels from transit into money, which is the
  // sentence the chart is making. Every other series is a flat colour.
  const stroke = tone === 'flow'
    ? {
      type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
      colorStops: [
        { offset: 0, color: theme.flow[0] },
        { offset: 1, color: theme.flow[1] },
      ],
    }
    : solid

  return {
    animationDuration: 360,
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
        smooth: .28,
        connectNulls: false,
        symbol: 'circle',
        symbolSize: history.buckets.length > 18 ? 5 : 7,
        showSymbol: history.buckets.length <= 32,
        lineStyle: { width: 2.5, color: stroke },
        // Points are ringed in the panel colour so a dense series reads as
        // separate marks rather than as a beaded string.
        itemStyle: { color: solid, borderColor: theme.surface, borderWidth: 1.5 },
        areaStyle: tone === 'flow' ? {
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: alpha(theme.flow[0], .20) },
              { offset: 1, color: alpha(theme.flow[1], .20) },
            ],
          },
        } : undefined,
      } : {
        barMaxWidth: 28,
        itemStyle: { color: solid, borderRadius: [4, 4, 0, 0] },
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
    animationDuration: 320,
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
            borderRadius: [0, 4, 4, 0],
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

/**
 * Spending by transit mode.
 *
 * <p>A donut is an all-pairs form — any two segments can end up adjacent as the
 * split changes — and the palette is validated pairwise for its first four
 * slots. That covers the real domain (bus, train, subway, ferry). Past four,
 * identity is carried by the direct label on every segment and by the legend,
 * never by colour alone.
 */
function modeOption(
  groups: AnalyticsGroup[], theme: ChartTheme, selected: string | null,
  colors: Map<string, string>,
): EChartsCoreOption {
  return {
    animationDuration: 320,
    color: groups.map((group) => colorOf(group.id, colors, theme)),
    tooltip: tooltip(theme, 'item'),
    legend: {
      type: 'scroll', bottom: 0, left: 'center', icon: 'roundRect',
      textStyle: { color: theme.secondary, fontSize: 11 },
      pageTextStyle: { color: theme.muted },
      inactiveColor: theme.muted,
    },
    series: [{
      name: 'Transit mode', type: 'pie', radius: ['48%', '72%'], center: ['50%', '43%'],
      avoidLabelOverlap: true,
      data: groups.map((group) => {
        const active = !selected || selected === group.id
        return {
          name: group.name, value: group.spentCents, filterId: group.id,
          itemStyle: {
            color: colorOf(group.id, colors, theme),
            // A 2px cut in the panel colour, so two adjacent shares never read
            // as one continuous arc.
            borderColor: theme.surface,
            borderWidth: selected === group.id ? 3 : 2,
            opacity: active ? 1 : .22,
          },
        }
      }),
      label: {
        color: theme.secondary, fontSize: 11, formatter: '{b}\n{d}%',
      },
      labelLine: { lineStyle: { color: theme.border } },
      tooltip: { valueFormatter: (value: unknown) => formatUnit(value, 'money') },
      emphasis: { scaleSize: 5 },
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
      borderRadius: [0, 4, 4, 0],
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
        borderRadius: [0, 4, 4, 0],
      },
      label: { show: true, position: 'right', color: theme.text,
        formatter: () => formatCents(projected) },
      tooltip: { valueFormatter: (value: unknown) => formatUnit(value, 'money') },
    })
  }

  return {
    animationDuration: 320,
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
    animationDuration: 320,
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
    axisPointer: trigger === 'axis' ? { type: 'cross', snap: true,
      lineStyle: { color: theme.transit, type: 'dashed', opacity: .7 },
      crossStyle: { color: theme.transit, opacity: .7 } } : undefined,
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
    splitLine: { lineStyle: { color: theme.grid, type: 'dashed' } },
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

function seriesDelta(
  values: Array<number | null>, unit: 'money' | 'minutes' | 'number',
): string | null {
  const actual = values.filter((value): value is number => value !== null)
  if (actual.length < 2) return null
  const change = actual[actual.length - 1] - actual[0]
  if (change === 0) return 'No change vs first active period'
  return `${change > 0 ? '↑' : '↓'} ${formatUnit(Math.abs(change), unit)} vs first active period`
}

function activeValues(
  buckets: SpendingHistoryBucket[], values: Array<number | null>,
): Array<number | null> {
  return values.filter((_, index) => buckets[index]?.tripCount > 0)
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

function share(value: number, total: number): string {
  return total === 0 ? '0%' : `${Math.round((value / total) * 100)}%`
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
