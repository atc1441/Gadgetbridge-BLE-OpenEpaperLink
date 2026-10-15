package nodomain.freeyourgadget.gadgetbridge.devices.cards

import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory

/**
 * The user order and the removed items of the device card icon row, persisted per device.
 * Items that are not in the persisted order are new, and display at their default position.
 * This avoids the need for preference migrations when devices get new items, or new capabilities.
 */
object DeviceCardLayout {
    private val LOG = LoggerFactory.getLogger(DeviceCardLayout::class.java)

    private const val PREF_ORDER = "device_card_items_order"
    private const val PREF_REMOVED = "device_card_items_removed"

    private const val SHOW_NEVER = "never"
    private const val SHOW_CONNECTED = "connected"

    /**
     * Returns the items of [defaults] that the user did not remove, in the user order.
     */
    @JvmStatic
    fun apply(device: GBDevice, defaults: List<DeviceCardItem>): List<DeviceCardItem> {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val order = split(prefs.getString(PREF_ORDER, null))
        val removed = split(prefs.getString(PREF_REMOVED, null)).toSet()

        val byId = defaults.filter { it.id !in removed }.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }.toMutableList()
        val orderedIds = order.toSet()

        for ((index, item) in defaults.withIndex()) {
            if (item.id in removed || item.id in orderedIds) {
                continue
            }
            val previous = defaults.subList(0, index).lastOrNull { prev -> ordered.any { it.id == prev.id } }
            val position = if (previous == null) 0 else ordered.indexOfFirst { it.id == previous.id } + 1
            ordered.add(position, item)
        }

        return ordered
    }

    /**
     * Returns the items of [defaults] that the user removed.
     */
    fun removed(device: GBDevice, defaults: List<DeviceCardItem>): List<DeviceCardItem> {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val removed = split(prefs.getString(PREF_REMOVED, null)).toSet()
        return defaults.filter { it.id in removed }
    }

    fun save(device: GBDevice, ordered: List<DeviceCardItem>, removed: List<DeviceCardItem>) {
        val order = ordered.joinToString(",") { it.id }
        val removedIds = removed.joinToString(",") { it.id }
        LOG.debug("Saving device card items for {}: order={}, removed={}", device.address, order, removedIds)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            putString(PREF_ORDER, order)
            putString(PREF_REMOVED, removedIds)
        }
    }

    fun reset(device: GBDevice) {
        LOG.debug("Resetting device card items for {}", device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            remove(PREF_ORDER)
            remove(PREF_REMOVED)
        }
    }

    /**
     * Whether the device card icon row is shown for the current state of the device.
     */
    @JvmStatic
    fun isShown(device: GBDevice): Boolean {
        val mode = GBApplication.getDevicePrefs(device)
            .getString(DeviceSettingsPreferenceConst.PREF_DEVICE_CARD_SHOW_ITEMS, "always")
        return when (mode) {
            SHOW_NEVER -> false
            SHOW_CONNECTED -> device.isInitialized
            else -> true
        }
    }

    private fun split(value: String?): List<String> = value?.split(",")
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
}
