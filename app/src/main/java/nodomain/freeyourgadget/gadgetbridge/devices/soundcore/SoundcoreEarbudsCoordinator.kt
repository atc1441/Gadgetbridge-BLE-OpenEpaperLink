/*  Copyright (C) 2026 David Giron

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.soundcore

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.multipointPairing
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig

abstract class SoundcoreEarbudsCoordinator : AbstractBLClassicDeviceCoordinator() {
    enum class Capability {
        FindDevice,
        Multipoint,
        NoiseControl,
        AmbientSound,
        AdaptiveNoiseCancellation,
        NoiseCancellationModes,
        WindNoiseReduction,
        VocalMode,
        TouchControls,
        TouchControlDisable,
        SurroundSound,
        FitTest,
        AutoPowerOff,
        TouchTone,
        AudioEqualizer,
    }

    enum class ButtonAction(
        @androidx.annotation.StringRes val title: Int,
        val leftPreference: String,
        val rightPreference: String,
    ) {
        SINGLE_TAP(
            R.string.single_tap,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_LEFT,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_RIGHT,
        ),
        DOUBLE_TAP(
            R.string.double_tap,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_LEFT,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_RIGHT,
        ),
        TRIPLE_TAP(
            R.string.triple_tap,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_LEFT,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_RIGHT,
        ),
        LONG_PRESS(
            R.string.long_press,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_LEFT,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_RIGHT,
        ),
    }

    enum class TapFunction(
        val code: Int,
        @androidx.annotation.StringRes val label: Int,
    ) {
        VOLUME_UP(0, R.string.pref_media_volumeup),
        VOLUME_DOWN(1, R.string.pref_media_volumedown),
        MEDIA_PREV(2, R.string.pref_media_previous),
        MEDIA_NEXT(3, R.string.pref_media_next),
        PLAYPAUSE(6, R.string.pref_media_playpause),
        VOICE_ASSISTANT(5, R.string.pref_title_touch_voice_assistant),
        AMBIENT_SOUND_CONTROL(4, R.string.sony_button_mode_ambient_sound_control),
        NONE(15, R.string.none),

        ;

        val value: String
            get() = name
    }

    open val supportedButtonActions: List<ButtonAction>
        get() = emptyList()

    open val supportedTapFunctions: List<TapFunction>
        get() = emptyList()

    /* TIP: Override this function in case you need conditions/exclusions based on Action. */
    open fun getSupportedTapFunctions(action: ButtonAction): List<TapFunction> = supportedTapFunctions

    open fun getSupportedEqualizerBands(): IntArray = intArrayOf()

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_galaxy_buds

    override fun getManufacturer(): String = "Anker"

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getBatteryCount(device: GBDevice): Int = 3

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> = arrayOf(
        BatteryConfig(0, R.drawable.ic_buds_pro_case, R.string.battery_case),
        BatteryConfig(1, R.drawable.ic_nothing_ear_l, R.string.left_earbud),
        BatteryConfig(2, R.drawable.ic_nothing_ear_r, R.string.right_earbud),
    )

    override fun supportsFindDevice(device: GBDevice): Boolean =
        getCapabilities(device).contains(Capability.FindDevice)

    override fun getSupportedDeviceSpecificConnectionSettings(): IntArray =
        connectionSettings + super.getSupportedDeviceSpecificConnectionSettings()

    protected open val connectionSettings: IntArray = intArrayOf()

    open fun getDefaultCapabilities(): Set<Capability> = emptySet()

    open fun getCapabilities(device: GBDevice): Set<Capability> {
        val devicePrefs = GBApplication.getDevicePrefs(device)
        if (devicePrefs.getBoolean(DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_ENABLED, false)) {
            return devicePrefs.getStringSet(
                DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_LIST,
                emptySet(),
            ).mapTo(mutableSetOf()) { Capability.valueOf(it) }
        }

        return getDefaultCapabilities()
    }

    fun supports(device: GBDevice, capability: Capability): Boolean =
        getCapabilities(device).contains(capability)

    /**
     * Builds the standardized modern settings tree for Soundcore earbuds. A model that needs a
     * completely isolated settings tree can override [getDeviceSettings].
     */
    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec =
        soundcoreEarbudsDeviceSettings(device)

    private fun equalizerBandTitleResource(frequency: Int): Int = when (frequency) {
        100 -> R.string.soundcore_equalizer_band1_freq
        200 -> R.string.soundcore_equalizer_band2_freq
        400 -> R.string.soundcore_equalizer_band3_freq
        800 -> R.string.soundcore_equalizer_band4_freq
        1600 -> R.string.soundcore_equalizer_band5_freq
        3200 -> R.string.soundcore_equalizer_band6_freq
        6400 -> R.string.soundcore_equalizer_band7_freq
        12800 -> R.string.soundcore_equalizer_band8_freq
        else -> R.string.soundcore_equalizer_value
    }

    protected fun soundcoreEarbudsDeviceSettings(device: GBDevice): DeviceSettingsSpec {
        val capabilities = getCapabilities(device)
        fun has(capability: Capability) = capabilities.contains(capability)

        return deviceSettings {
            if (has(Capability.TouchControls)) {
                screen(
                    key = "pref_soundcore_touch_options",
                    title = R.string.prefs_galaxy_touch_options,
                    icon = R.drawable.ic_touch,
                ) {
                    if (has(Capability.TouchTone)) {
                        switchSetting(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE,
                            title = R.string.pref_touch_tone,
                            summary = R.string.pref_touch_tone_summary,
                            icon = R.drawable.ic_volume_up,
                            defaultValue = false,
                        )
                    }
                    switchSetting(
                        key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TOUCH_DISABLED,
                        title = R.string.prefs_touch_lock_buds2,
                        summary = R.string.prefs_touch_lock_summary,
                        icon = R.drawable.ic_lock_open,
                        defaultValue = false,
                        disableDependentsState = true,
                        visibleWhen = { _ -> has(Capability.TouchControlDisable) },
                    )
                    list(
                        key = DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_CONTROL_BUTTON_MODE,
                        title = R.string.sony_ambient_sound_control_button_modes,
                        icon = R.drawable.ic_hearing,
                        entriesRes = R.array.sony_ambient_sound_control_button_mode_names,
                        entryValuesRes = R.array.sony_ambient_sound_control_button_mode_values,
                        defaultValue = "nc_as_off",
                        visibleWhen = { _ -> TapFunction.AMBIENT_SOUND_CONTROL in supportedTapFunctions },
                    )
                    supportedButtonActions.forEach { action ->
                        category(key = "pref_header_${action.name.lowercase()}", title = action.title) {
                            list(
                                key = action.leftPreference,
                                title = R.string.prefs_left,
                                icon = R.drawable.ic_touch,
                                entries = getSupportedTapFunctions(action).map { ListEntry.Res(it.value, it.label) },
                                dependency = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TOUCH_DISABLED,
                            )
                            list(
                                key = action.rightPreference,
                                title = R.string.prefs_right,
                                icon = R.drawable.ic_touch,
                                entries = getSupportedTapFunctions(action).map { ListEntry.Res(it.value, it.label) },
                                dependency = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TOUCH_DISABLED,
                            )
                        }
                    }
                }
            }

            if (has(Capability.AudioEqualizer) || has(Capability.SurroundSound)) {
                screen(
                    key = "pref_soundcore_audio",
                    title = R.string.pref_header_audio,
                    icon = R.drawable.ic_music_note,
                ) {
                    if (has(Capability.SurroundSound)) {
                        switchSetting(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND,
                            title = R.string.soundcore_3d_surround_title,
                            summary = R.string.soundcore_3d_surround_summary,
                        )
                    }
                    if (has(Capability.AudioEqualizer)) {
                        category(key = "pref_header_soundcore_equalizer", title = R.string.pref_header_equalizer) {
                            list(
                                key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_PRESET,
                                title = R.string.soundcore_equalizer_preset,
                                icon = R.drawable.ic_equalizer,
                                entriesRes = R.array.soundcore_sport_x20_equalizer_preset_names,
                                entryValuesRes = R.array.soundcore_sport_x20_equalizer_preset_values,
                                defaultValue = "0",
                            )
                            screen(
                                key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_CUSTOM,
                                title = R.string.soundcore_equalizer_custom_title,
                                summary = R.string.soundcore_equalizer_custom_summary,
                                icon = R.drawable.ic_graphic_eq,
                                enabled = { prefs ->
                                    prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_PRESET, "0") == "254"
                                },
                            ) {
                                val equalizerBandKeys = listOf(
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND1_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND2_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND3_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND4_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND5_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND6_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND7_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND8_VALUE,
                                    DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND9_VALUE,
                                )
                                action(
                                    key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_RESET,
                                    title = R.string.soundcore_equalizer_reset_title,
                                    summary = R.string.soundcore_equalizer_reset_summary,
                                    icon = R.drawable.ic_history,
                                    onClick = { _, selectedDevice ->
                                        selectedDevice?.let {
                                            val prefs = GBApplication.getDevicePrefs(it).preferences
                                            prefs.edit().apply {
                                                equalizerBandKeys.take(getSupportedEqualizerBands().size)
                                                    .forEach { key -> putInt(key, 0) }
                                                apply()
                                            }
                                            equalizerBandKeys.take(getSupportedEqualizerBands().size)
                                                .lastOrNull()
                                                ?.let { key -> GBApplication.deviceService(it).onSendConfiguration(key) }
                                        }
                                        true
                                    },
                                )
                                getSupportedEqualizerBands().take(equalizerBandKeys.size).forEachIndexed { index, frequency ->
                                    val key = equalizerBandKeys[index]
                                    category(
                                        key = "${key}_category",
                                        title = equalizerBandTitleResource(frequency),
                                    ) {
                                        seekbar(
                                            key = key,
                                            title = R.string.soundcore_equalizer_value,
                                            icon = R.drawable.ic_graphic_eq,
                                            min = -6,
                                            max = 6,
                                            defaultValue = 0,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (has(Capability.NoiseControl) || has(Capability.AmbientSound)) {
                val ambientSoundControlEntries = buildList {
                    add(ListEntry.Res("off", R.string.sony_ambient_sound_off))
                    if (has(Capability.NoiseControl)) {
                        add(ListEntry.Res("noise_cancelling", R.string.sony_ambient_sound_noise_cancelling))
                    }
                    if (has(Capability.AmbientSound)) {
                        add(ListEntry.Res("ambient_sound", R.string.sony_ambient_sound_ambient_sound))
                    }
                }
                category(key = "pref_header_soundcore_ambient_sound_control", title = R.string.pref_header_sony_ambient_sound_control) {
                    list(
                        key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                        title = R.string.sony_ambient_sound,
                        icon = R.drawable.ic_hearing,
                        entries = ambientSoundControlEntries,
                        defaultValue = "off",
                    )
                    if (has(Capability.NoiseCancellationModes)) {
                        list(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE,
                            title = R.string.earfun_anc_mode,
                            icon = R.drawable.ic_hearing,
                            entriesRes = R.array.soundcore_anc_mode_names,
                            entryValuesRes = R.array.soundcore_anc_mode_values,
                            defaultValue = "transport",
                        )
                    }
                    if (has(Capability.AdaptiveNoiseCancellation)) {
                        switchSetting(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING,
                            title = R.string.pref_adaptive_noise_cancelling_title,
                            summary = R.string.pref_adaptive_noise_cancelling_summary,
                            icon = R.drawable.ic_hearing,
                            defaultValue = true,
                            disableDependentsState = true,
                        )
                        seekbar(
                            key = DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL,
                            title = R.string.prefs_active_noise_cancelling_level,
                            icon = R.drawable.ic_hearing,
                            min = 0,
                            max = 2,
                            defaultValue = 0,
                            dependency = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING,
                        )
                    }
                    if (has(Capability.WindNoiseReduction)) {
                        switchSetting(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION,
                            title = R.string.sony_ambient_sound_wind_noise_reduction,
                            icon = R.drawable.ic_block,
                        )
                    }
                    if (has(Capability.VocalMode)) {
                        switchSetting(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE,
                            title = R.string.sony_ambient_sound_focus_voice,
                            icon = R.drawable.ic_voice,
                        )
                    }
                }
            }
            if (has(Capability.FitTest) || has(Capability.AutoPowerOff)) {
                category(key = "pref_header_soundcore_general", title = R.string.pref_header_general) {
                    if (has(Capability.FitTest)) {
                        action(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIT_TEST,
                            title = R.string.pref_soundcore_fit_test_title,
                            summary = R.string.pref_soundcore_fit_test_summary,
                            icon = R.drawable.ic_verified,
                            onClick = { _, selectedDevice ->
                                selectedDevice?.let {
                                    GBApplication.deviceService(it).onSendConfiguration(
                                        DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIT_TEST
                                    )
                                }
                                true
                            },
                        )
                    }
                    if (has(Capability.AutoPowerOff)) {
                        list(
                            key = DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF,
                            title = R.string.soundcore_auto_power_off_title,
                            summary = R.string.soundcore_auto_power_off_summary,
                            icon = R.drawable.ic_power_settings_new,
                            entriesRes = R.array.soundcore_aerofit_auto_power_off_names,
                            entryValuesRes = R.array.soundcore_auto_power_off_values,
                            defaultValue = "3",
                        )
                    }
                }
            }

            screen(
                key = DeviceSpecificSettingsScreen.CONNECTION.key,
                title = R.string.pref_header_connection,
                icon = R.drawable.ic_mtu,
            ) {
                if (has(Capability.Multipoint)) {
                    multipointPairing()
                }
            }

            xmlScreen(
                DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
                R.xml.devicesettings_headphones,
                connectedOnly = false,
            )
        }
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.EARBUDS
}
