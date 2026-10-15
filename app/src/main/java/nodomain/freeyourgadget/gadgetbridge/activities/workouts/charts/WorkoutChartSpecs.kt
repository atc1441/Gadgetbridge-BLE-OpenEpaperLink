package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.durationLabel
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart
import java.util.Locale
import kotlin.math.abs

/**
 * Turns [WorkoutChart]s into a [ChartSpec] over the seconds since the workout started. The first chart uses the
 * start axis, a second one the end axis.
 */
object WorkoutChartSpecs {
    @JvmStatic
    fun spec(context: Context, charts: List<WorkoutChart>, showZones: Boolean): ChartSpec {
        val metricSeries = charts.mapIndexed { index, chart -> series(chart, sideOf(index)) }
        val points = metricSeries.flatMap { it.points }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val zoneSeries = mutableListOf<ChartSeries>()
        val zoneLines = mutableListOf<LimitLineSpec>()
        if (showZones) {
            charts.forEachIndexed { index, chart ->
                zoneSeries += zoneBands(context, chart, metricSeries[index].points, sideOf(index))
                zoneLines += zoneLines(context, chart, sideOf(index))
            }
        }
        return ChartSpec(
            series = zoneSeries + metricSeries,
            xAxis = AxisSpec(
                format = ChartValueFormat.DURATION_SECONDS,
                minimum = points.minOf { it.x },
                maximum = points.maxOf { it.x },
            ),
            yAxis = yAxis(charts[0]),
            endYAxis = charts.getOrNull(1)?.let { yAxis(it) },
            limitLines = zoneLines,
        )
    }

    /**
     * Tooltip content for the charts: the elapsed time, then each chart's nearest value.
     */
    @JvmStatic
    fun selection(charts: List<WorkoutChart>): (Double) -> ChartSelection {
        val points = charts.map { chart -> chart.series.points.sortedBy { it.x } }
        val xs = points.map { list -> DoubleArray(list.size) { list[it].x } }
        return { x ->
            val title = durationLabel(x)
            val rows = charts.indices.mapNotNull { index ->
                val chart = charts[index]
                val nearest = nearest(points[index], xs[index], x) ?: return@mapNotNull null
                val value = chart.labeler?.invoke(nearest.y) ?: String.format(Locale.getDefault(), "%.1f", nearest.y)
                val text = listOfNotNull(value, chart.unitString?.takeIf { it.isNotEmpty() }).joinToString(" ")
                Triple(chart.title, colorOf(chart), text)
            }
            ChartSelection(
                title = title,
                rows = rows.map { ChartSelection.Row(it.second, it.third) },
                description = (listOf(title) + rows.map { "${it.first} ${it.third}" }).joinToString(". ", postfix = "."),
            )
        }
    }

    private fun nearest(points: List<ChartPoint>, xs: DoubleArray, x: Double): ChartPoint? {
        if (points.isEmpty()) return null
        val found = xs.binarySearch(x)
        if (found >= 0) return points[found]
        val after = -found - 1
        return when {
            after >= points.size -> points.last()
            after == 0 -> points.first()
            abs(xs[after] - x) < abs(x - xs[after - 1]) -> points[after]
            else -> points[after - 1]
        }
    }

    @JvmStatic
    fun colorOf(chart: WorkoutChart): Int = chart.series.color

    private fun sideOf(index: Int) = if (index == 0) AxisSide.START else AxisSide.END

    private fun series(chart: WorkoutChart, side: AxisSide): ChartSeries {
        val style = if (chart.series.dots) {
            SeriesStyle.Line(chart.series.color, showLine = false, showPoints = true)
        } else {
            SeriesStyle.Line(chart.series.color, curve = LineCurve.FLAT_AT_POINTS, maxGap = chart.series.maxGap)
        }
        return ChartSeries(chart.id, chart.title, chart.series.points, style, side)
    }

    private fun zoneBands(context: Context, chart: WorkoutChart, metric: List<ChartPoint>, side: AxisSide): List<ChartSeries> {
        val zones = chart.zoneThresholds ?: return emptyList()
        val points = metric.sortedBy { it.x }
        if (points.isEmpty()) {
            return emptyList()
        }
        val chartMax = maxOf(HeartRateUtils.getInstance().maxHeartRate, zones.zone5 + 1)
        val peak = points.maxOf { it.y }
        return (1..5).mapNotNull { zone ->
            val top = HeartRateZonesResolver.upperBoundOf(zone, zones, chartMax).toDouble()
            val bottom = HeartRateZonesResolver.lowerBoundOf(zone, zones).toDouble()
            if (top <= bottom || peak <= bottom) {
                return@mapNotNull null
            }
            ChartSeries(
                "zone_$zone", "",
                points.map { ChartPoint(it.x, it.y.coerceIn(bottom, top)) },
                SeriesStyle.Line(HeartRateZonesResolver.colorForZone(context, zone), filled = true, showLine = false, fillBase = bottom),
                side,
                selectable = false,
            )
        }
    }

    private fun zoneLines(context: Context, chart: WorkoutChart, side: AxisSide): List<LimitLineSpec> {
        val zones = chart.zoneThresholds ?: return emptyList()
        return listOf(2 to zones.zone2, 3 to zones.zone3, 4 to zones.zone4, 5 to zones.zone5)
            .filter { it.second > 0 }
            .map { (zone, bpm) -> LimitLineSpec(bpm.toDouble(), HeartRateZonesResolver.colorForZone(context, zone), axis = side) }
    }

    private fun yAxis(chart: WorkoutChart) = AxisSpec(
        format = ChartValueFormat.DECIMAL,
        minimum = chart.minimum,
        maximum = chart.maximum,
        labeler = chart.labeler,
    )
}
