package nodomain.freeyourgadget.gadgetbridge.activities.charts.spec

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Chart colors, resolved once from a themed [Context].
 */
data class ChartTheme(
    val textColor: Int,
    val secondaryTextColor: Int,
    val backgroundColor: Int,
    val heartRateColor: Int,
    val heartRateFillColor: Int,
    val activityColor: Int,
    val deepSleepColor: Int,
    val lightSleepColor: Int,
    val remSleepColor: Int,
    val awakeSleepColor: Int,
    val notWornColor: Int,
    val markerBackgroundColor: Int,
    val markerBorderColor: Int,
    val markerTitleColor: Int,
    val markerValueColor: Int,
    val markerDotGapColor: Int,
) {
    companion object {
        private const val PREF_HEARTRATE_ALTERNATIVE_COLOR = "chart_heartrate_color"
        private const val DARK_LUMINANCE = 0.5
        private val DARK_MARKER_BACKGROUND = Color.parseColor("#262626")
        private val DARK_MARKER_TITLE = Color.parseColor("#B4B4B4")
        private val DARK_MARKER_VALUE = Color.parseColor("#F2F2F2")

        fun from(context: Context): ChartTheme {
            val theme = context.theme
            fun attrColor(attrRes: Int): Int {
                val value = TypedValue()
                theme.resolveAttribute(attrRes, value, true)
                return value.data
            }

            val useAlternativeHeartRateColor = GBApplication.getPrefs()
                .getBoolean(PREF_HEARTRATE_ALTERNATIVE_COLOR, false)
            val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
            val dark = ColorUtils.calculateLuminance(surface) < DARK_LUMINANCE

            return ChartTheme(
                textColor = GBApplication.getTextColor(context),
                secondaryTextColor = GBApplication.getSecondaryTextColor(context),
                backgroundColor = GBApplication.getBackgroundColor(context),
                heartRateColor = ContextCompat.getColor(
                    context,
                    if (useAlternativeHeartRateColor) R.color.chart_heartrate_alternative else R.color.chart_heartrate,
                ),
                heartRateFillColor = ContextCompat.getColor(context, R.color.chart_heartrate_fill),
                activityColor = attrColor(R.attr.chart_activity),
                deepSleepColor = attrColor(R.attr.chart_deep_sleep),
                lightSleepColor = attrColor(R.attr.chart_light_sleep),
                remSleepColor = attrColor(R.attr.chart_rem_sleep),
                awakeSleepColor = attrColor(R.attr.chart_awake_sleep),
                notWornColor = attrColor(R.attr.chart_not_worn),
                markerBackgroundColor = if (dark) {
                    DARK_MARKER_BACKGROUND
                } else {
                    MaterialColors.getColor(context, R.attr.stat_tile_bg, surface)
                },
                markerBorderColor = MaterialColors.getColor(context, R.attr.stat_tile_border, Color.GRAY),
                markerTitleColor = if (dark) {
                    DARK_MARKER_TITLE
                } else {
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                },
                markerValueColor = if (dark) {
                    DARK_MARKER_VALUE
                } else {
                    MaterialColors.getColor(context, R.attr.textColorPrimary, Color.BLACK)
                },
                markerDotGapColor = GBApplication.getWindowBackgroundColor(context),
            )
        }
    }
}
