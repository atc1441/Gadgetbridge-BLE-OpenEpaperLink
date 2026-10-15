package nodomain.freeyourgadget.gadgetbridge.adapter

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.BatteryInfoActivity
import nodomain.freeyourgadget.gadgetbridge.devices.cards.ActionCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.BatteryCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.ColorCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.HeartRateCardItem
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import java.util.Locale

/**
 * Renders a list of [DeviceCardItem]s in the icon row of a device card.
 */
object DeviceCardItemBinder {
    @JvmStatic
    fun bind(
        container: FlexboxLayout,
        items: List<DeviceCardItem>,
        device: GBDevice,
        context: Context,
        hrSampleText: String?,
    ) {
        val visibleItems = items.filter { it.isVisible(device) }
        for (i in visibleItems.indices) {
            val view = container.getChildAt(i)
                ?: LayoutInflater.from(context).inflate(R.layout.device_card_item, container, false)
                    .also { container.addView(it) }
            bindItem(view, visibleItems[i], device, context, hrSampleText)
        }
        if (container.childCount > visibleItems.size) {
            container.removeViews(visibleItems.size, container.childCount - visibleItems.size)
        }
    }

    private fun bindItem(
        view: View,
        item: DeviceCardItem,
        device: GBDevice,
        context: Context,
        hrSampleText: String?,
    ) {
        val icon = view.findViewById<ImageView>(R.id.card_item_icon)
        val label = view.findViewById<TextView>(R.id.card_item_label)
        val busy = view.findViewById<View>(R.id.card_item_busy)

        // The view can come from a different item type, so reset all the state that the types change
        view.visibility = View.VISIBLE
        busy.visibility = View.GONE
        label.alpha = 1.0f
        ImageViewCompat.setImageTintList(
            icon,
            ColorStateList.valueOf(MaterialColors.getColor(icon, R.attr.textColorSecondary))
        )

        when (item) {
            is ActionCardItem -> bindAction(view, icon, label, busy, item, device, context)
            is BatteryCardItem -> bindBattery(view, icon, label, item, device, context)
            is ColorCardItem -> bindColor(view, icon, label, item, device, context)
            is HeartRateCardItem -> bindHeartRate(view, icon, label, item, device, context, hrSampleText)
        }
    }

    /**
     * Returns the icon that represents the item in a list.
     */
    @DrawableRes
    fun icon(item: DeviceCardItem, device: GBDevice): Int = when (item) {
        is ActionCardItem -> item.action.getIcon(device)
        is BatteryCardItem -> batteryConfig(item, device)?.batteryIcon
            ?.takeIf { it != GBDevice.BATTERY_ICON_DEFAULT.toInt() }
            ?: R.drawable.ic_battery_full

        is ColorCardItem -> R.drawable.ic_lightbulb
        is HeartRateCardItem -> R.drawable.ic_heart
    }

    /**
     * Returns the name of the item in a list.
     */
    fun title(item: DeviceCardItem, device: GBDevice, context: Context): String = when (item) {
        is ActionCardItem -> item.action.getDescription(device, context)
        is BatteryCardItem -> batteryName(item, device, context)

        is ColorCardItem -> item.description(device, context)
        is HeartRateCardItem -> context.getString(R.string.controlcenter_get_heartrate_measurement)
    }

    private fun batteryConfig(item: BatteryCardItem, device: GBDevice): BatteryConfig? =
        device.deviceCoordinator.getBatteryConfig(device).firstOrNull { it.batteryIndex == item.batteryIndex }

    private fun batteryName(item: BatteryCardItem, device: GBDevice, context: Context): String {
        val labelDefault = GBDevice.BATTERY_LABEL_DEFAULT.toInt()
        val label = batteryConfig(item, device)?.batteryLabel?.takeIf { it != labelDefault }
            ?: device.getBatteryLabel(item.batteryIndex).takeIf { it != labelDefault }
        return when {
            label != null -> context.getString(R.string.battery_with_label, context.getString(label))
            device.deviceCoordinator.getBatteryCount(device) > 1 -> context.getString(
                R.string.battery_i,
                item.batteryIndex + 1
            )

            else -> context.getString(R.string.battery)
        }
    }

    private fun setDescription(view: View, description: String) {
        view.contentDescription = description
        TooltipCompat.setTooltipText(view, description)
    }

    private fun setLabel(label: TextView, text: CharSequence?) {
        label.text = text
        label.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun setOnClick(view: View, onClick: (() -> Unit)?) {
        if (onClick != null) {
            view.setOnClickListener { onClick() }
        } else {
            view.setOnClickListener(null)
        }
        view.isClickable = onClick != null
        view.isFocusable = onClick != null
    }

    private fun bindAction(
        view: View,
        icon: ImageView,
        label: TextView,
        busy: View,
        item: ActionCardItem,
        device: GBDevice,
        context: Context,
    ) {
        val action = item.action
        icon.setImageResource(action.getIcon(device))
        setDescription(view, action.getDescription(device, context))
        setLabel(label, action.getLabel(device, context))
        if (item.busyIndicator && device.isBusy) {
            busy.visibility = View.VISIBLE
        }
        setOnClick(view) { action.onClick(device, context) }
    }

    private fun bindBattery(
        view: View,
        icon: ImageView,
        label: TextView,
        item: BatteryCardItem,
        device: GBDevice,
        context: Context,
    ) {
        val batteryIndex = item.batteryIndex
        val batteryLevel = device.getBatteryLevel(batteryIndex)
        val batteryVoltage = device.getBatteryVoltage(batteryIndex)
        val batteryState = device.getBatteryState(batteryIndex)
        val batteryIcon = device.getBatteryIcon(batteryIndex)

        setDescription(view, batteryName(item, device, context))

        if (batteryIcon != GBDevice.BATTERY_ICON_DEFAULT.toInt()) {
            icon.setImageResource(batteryIcon)
        } else {
            icon.setImageResource(R.drawable.level_list_battery)
        }

        if (batteryLevel != GBDevice.BATTERY_UNKNOWN.toInt()) {
            setLabel(label, context.getString(R.string.battery_percentage_str, batteryLevel.toString()))
            if (BatteryState.BATTERY_CHARGING == batteryState || BatteryState.BATTERY_CHARGING_FULL == batteryState) {
                icon.setImageLevel(batteryLevel + 100)
            } else if (BatteryState.NO_BATTERY == batteryState) {
                label.alpha = 0.3f
                icon.setImageLevel(300)
            } else {
                icon.setImageLevel(batteryLevel)
            }
        } else if (BatteryState.NO_BATTERY == batteryState && batteryVoltage != GBDevice.BATTERY_UNKNOWN.toFloat()) {
            setLabel(label, String.format(Locale.getDefault(), "%.2f", batteryVoltage))
            icon.setImageLevel(200)
        } else {
            // The "default" status, shown when the device is not connected
            setLabel(label, null)
            icon.setImageLevel(300)
        }

        setOnClick(view) {
            val startIntent = Intent(context, BatteryInfoActivity::class.java)
            startIntent.putExtra(GBDevice.EXTRA_DEVICE, device)
            startIntent.putExtra(GBDevice.BATTERY_INDEX, batteryIndex)
            context.startActivity(startIntent)
        }
    }

    private fun bindColor(
        view: View,
        icon: ImageView,
        label: TextView,
        item: ColorCardItem,
        device: GBDevice,
        context: Context,
    ) {
        icon.setImageResource(R.drawable.ic_led_color)
        // The tint covers the fill color
        ImageViewCompat.setImageTintList(icon, null)
        (icon.drawable.mutate() as GradientDrawable).setColor(item.color(device))
        setDescription(view, item.description(device, context))
        setLabel(label, null)
        setOnClick(view) { item.onClick(device, context) }
    }

    private fun bindHeartRate(
        view: View,
        icon: ImageView,
        label: TextView,
        item: HeartRateCardItem,
        device: GBDevice,
        context: Context,
        hrSampleText: String?,
    ) {
        icon.setImageResource(R.drawable.ic_heart)
        setDescription(view, context.getString(R.string.controlcenter_get_heartrate_measurement))

        // Live-only devices display the item only while a sample is available
        if (item.liveOnly && hrSampleText == null) {
            view.visibility = View.GONE
        }

        setLabel(label, hrSampleText)
        setOnClick(
            view, if (item.liveOnly) null else {
                { item.onClick(device, context) }
            })
    }
}
