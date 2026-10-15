package nodomain.freeyourgadget.gadgetbridge.activities.charts.weight

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import kotlin.math.ceil
import kotlin.math.floor

object WeightChartData {
    private const val Y_MARGIN = 1.0

    /**
     * Weight readings between [startTs] and [endTs] (epoch seconds), with the target as a dashed line.
     */
    @JvmStatic
    fun spec(
        startTs: Long,
        endTs: Long,
        seconds: LongArray,
        weights: DoubleArray,
        target: Double,
        label: String,
        color: Int,
        targetColor: Int,
    ): ChartSpec {
        val points = seconds.indices
            .filter { weights[it] > 0 }
            .map { ChartPoint(seconds[it].toDouble(), weights[it]) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val values = points.map { it.y } + if (target > 0) listOf(target) else emptyList()
        return ChartSpec(
            series = listOf(ChartSeries("weight", label, points, SeriesStyle.Line(color, curve = LineCurve.FLAT_AT_POINTS, showPoints = true))),
            xAxis = AxisSpec(format = ChartValueFormat.DATE, minimum = startTs.toDouble(), maximum = endTs.toDouble()),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = maxOf(0.0, floor(values.min() - Y_MARGIN)),
                maximum = ceil(values.max() + Y_MARGIN),
            ),
            limitLines = if (target > 0) listOf(LimitLineSpec(value = target, color = targetColor)) else emptyList(),
        )
    }
}
