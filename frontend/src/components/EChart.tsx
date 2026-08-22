import { useEffect, useRef } from 'react'
import { BarChart, LineChart, PieChart, ScatterChart } from 'echarts/charts'
import {
  AriaComponent, AxisPointerComponent, DataZoomComponent, GridComponent,
  LegendComponent, MarkLineComponent, TooltipComponent,
} from 'echarts/components'
import { init, use, type EChartsCoreOption, type EChartsType, type ECElementEvent } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { useTheme } from '../hooks/useTheme'

use([
  BarChart, LineChart, PieChart, ScatterChart,
  AriaComponent, AxisPointerComponent, DataZoomComponent, GridComponent,
  LegendComponent, MarkLineComponent, TooltipComponent, SVGRenderer,
])

export type ChartClickEvent = ECElementEvent

/**
 * Small React lifecycle boundary around Apache ECharts. SVG keeps chart text crisp
 * and testable, ResizeObserver makes panels responsive, and disposal prevents
 * stale canvas/SVG instances when the date range changes.
 */
export function EChart({
  option, ariaLabel, height = 300, onClick, onLegendSelect, testId,
}: {
  option: EChartsCoreOption
  ariaLabel: string
  height?: number
  onClick?: (event: ChartClickEvent) => void
  onLegendSelect?: (name: string) => void
  testId: string
}) {
  const elementRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<EChartsType | null>(null)
  const clickRef = useRef(onClick)
  const legendRef = useRef(onLegendSelect)
  const { resolved } = useTheme()
  clickRef.current = onClick
  legendRef.current = onLegendSelect

  useEffect(() => {
    const element = elementRef.current
    if (!element || import.meta.env.MODE === 'test') return

    const chart = init(element, undefined, { renderer: 'svg' })
    chartRef.current = chart
    const handleClick = (event: ECElementEvent) => clickRef.current?.(event)
    const handleLegend = (event: unknown) => {
      const name = (event as { name?: unknown })?.name
      if (typeof name === 'string') legendRef.current?.(name)
    }
    chart.on('click', handleClick)
    chart.on('legendselectchanged', handleLegend)

    const observer = typeof ResizeObserver === 'undefined'
      ? null : new ResizeObserver(() => chart.resize())
    observer?.observe(element)

    return () => {
      observer?.disconnect()
      chart.off('click', handleClick)
      chart.off('legendselectchanged', handleLegend)
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption({
      ...option,
      aria: { enabled: true, label: { description: ariaLabel } },
    }, { notMerge: true, lazyUpdate: true })
  }, [option, ariaLabel, resolved])

  return (
    <div
      ref={elementRef}
      className="echart"
      style={{ height }}
      role="img"
      aria-label={ariaLabel}
      data-testid={testId}
    />
  )
}
