package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.liberty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.prefs.AmbientSoundControlButtonMode;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class SoundcoreLibertyProtocol extends SoundcoreProtocolImplV1 {

    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreLibertyProtocol.class);

    private static final short CMD_GET_UNKNOWN_DATA_8D01 = (short) 0x8d01;
    private static final short CMD_GET_UNKNOWN_DATA_8205 = (short) 0x8205;
    private static final short CMD_SET_WEARING_DETECTION = (short) 0x8101;
    private static final short CMD_SET_WEARING_TONE = (short) 0x8c01;
    private static final int BATTERY_MULTIPLIER = 20;

    protected SoundcoreLibertyProtocol(GBDevice device) {
        super(device);
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        if (payload.length < 32) {
            LOG.warn("CMD_GET_DEVICE_INFO payload too short: {} bytes", payload.length);
            return new GBDeviceEvent[0];
        }

        return decodeStandardEarbudDeviceInfo(payload);
    }

    @Override
    protected GBDeviceEvent[] decodeAudioMode(final byte[] payload) {
        decodeAdvancedAudioMode(payload);
        return new GBDeviceEvent[0];
    }

    @Override
    protected int getBatteryMultiplier() {
        return BATTERY_MULTIPLIER;
    }

    @Override
    protected GBDeviceEvent[] decodeExtendedInfo(final byte[] payload) {
        LOG.debug("Unknown incoming extended information, {} bytes", payload.length);
        return new GBDeviceEvent[0];
    }

    @Override
    protected GBDeviceEvent[] decodeCommand(final short command, final byte[] payload) {
        if (command == CMD_GET_UNKNOWN_DATA_8D01 || command == CMD_GET_UNKNOWN_DATA_8205) {
            LOG.debug("Unknown incoming message - command: {} ({} bytes)", command, payload.length);
            return new GBDeviceEvent[0];
        }

        LOG.debug("Unknown incoming message - command: {} ({} bytes)", command, payload.length);
        return new GBDeviceEvent[0];
    }

    @Override
    public byte[] encodeSendConfiguration(String config) {
        Prefs prefs = getDevicePrefs();
        String pref_string;

        switch (config) {
            // Ambient Sound Modes
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING:
            case DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL:
                return encodeAdvancedAudioMode(true);

            // Control
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_DISABLED:
                return encodeControlTouchLock(TapAction.SINGLE_TAP, prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_DISABLED, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_DISABLED:
                return encodeControlTouchLock(TapAction.DOUBLE_TAP, prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_DISABLED, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_DISABLED:
                return encodeControlTouchLock(TapAction.TRIPLE_TAP, prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_DISABLED, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_DISABLED:
                return encodeControlTouchLock(TapAction.LONG_PRESS, prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_DISABLED, false));

            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_LEFT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_LEFT, "");
                return encodeControlFunction(TapAction.SINGLE_TAP, false, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_RIGHT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_RIGHT, "");
                return encodeControlFunction(TapAction.SINGLE_TAP, true, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_LEFT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_LEFT, "");
                return encodeControlFunction(TapAction.DOUBLE_TAP, false, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_RIGHT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_RIGHT, "");
                return encodeControlFunction(TapAction.DOUBLE_TAP, true, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_LEFT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_LEFT, "");
                return encodeControlFunction(TapAction.TRIPLE_TAP, false, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_RIGHT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_RIGHT, "");
                return encodeControlFunction(TapAction.TRIPLE_TAP, true, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_LEFT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_LEFT, "");
                return encodeControlFunction(TapAction.LONG_PRESS, false, TapFunction.valueOf(pref_string));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_RIGHT:
                pref_string = prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_RIGHT, "");
                return encodeControlFunction(TapAction.LONG_PRESS, true, TapFunction.valueOf(pref_string));

            case DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_CONTROL_BUTTON_MODE:
                AmbientSoundControlButtonMode modes = AmbientSoundControlButtonMode.fromPreferences(prefs.getPreferences());
                switch (modes) {
                    case NC_AS_OFF:
                        return encodeControlAmbientMode(true, true, true);
                    case NC_AS:
                        return encodeControlAmbientMode(true, true, false);
                    case NC_OFF:
                        return encodeControlAmbientMode(true, false, true);
                    case AS_OFF:
                        return encodeControlAmbientMode(false, true, true);
                }

            // Miscellaneous Settings
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WEARING_DETECTION:
                boolean wearingDetection = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WEARING_DETECTION, false);
                return encodeBooleanCommand(CMD_SET_WEARING_DETECTION, wearingDetection);
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WEARING_TONE:
                boolean wearingTone = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WEARING_TONE, false);
                return encodeBooleanCommand(CMD_SET_WEARING_TONE, wearingTone);
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE:
                boolean touchTone = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE, false);
                return encodeBooleanCommand(CMD_SET_TOUCH_TONE, touchTone);
            default:
                LOG.debug("Unsupported CONFIG: " + config);
        }

        return super.encodeSendConfiguration(config);
    }

    byte[] encodeMysteryDataRequest1() {
        byte[] payload = new byte[]{0x00};
        return encodeCommand(CMD_GET_UNKNOWN_DATA_8D01, payload);
    }
    byte[] encodeMysteryDataRequest2() {
        return encodeRequest(CMD_GET_EXTENDED_INFO);
    }
    byte[] encodeMysteryDataRequest3() {
        byte[] payload = new byte[]{0x00};
        return encodeCommand(CMD_GET_UNKNOWN_DATA_8205, payload);
    }

    /**
     * Enables or disables a tap-action
     * @param action The byte that encodes the action (single/double/triple or long tap)
     * @param disabled If the action should be enabled or disabled
     * @return
     */
    private byte[] encodeControlTouchLock(TapAction action, boolean disabled) {
        boolean enabled = !disabled;
        byte enabled_byte;
        byte[] payload;
        switch (action) {
            case SINGLE_TAP:
            case TRIPLE_TAP:
                enabled_byte = encodeBoolean(enabled);
                break;
            case DOUBLE_TAP:
            case LONG_PRESS:
                enabled_byte = enabled?(byte) 0x11: (byte) 0x10;
                break;
            default:
                LOG.error("Invalid Tap action");
                return null;
        }
        payload = new byte[]{0x00, action.getCode(), enabled_byte};
        return encodeCommand(CMD_SET_TOUCH_LOCK, payload);
    }

    /**
     * Assigns a function (eg play/pause) to an action (eg single tap on right bud)
     * @param action The byte that encodes the action (single/double/triple or long tap)
     * @param right  If the right or left earbud is meant
     * @param function The byte that encodes the triggered function (eg play/pause)
     * @return The encoded message
     */
    private byte[] encodeControlFunction(TapAction action, boolean right, TapFunction function) {
        byte function_byte;
        switch (action) {
            case SINGLE_TAP:
            case DOUBLE_TAP:
                function_byte = (byte) (16*6 + function.getCode());
                break;
            case TRIPLE_TAP:
                function_byte = (byte) (16*4 + function.getCode());
                break;
            case LONG_PRESS:
                function_byte = (byte) (16*5 + function.getCode());
                break;
            default:
                LOG.error("Invalid Tap action");
                return null;
        }
        return encodeControlFunction(right, action.getCode(), function_byte);
    }

}
