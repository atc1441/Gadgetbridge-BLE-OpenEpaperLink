package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1;

import android.content.SharedPreferences;

import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.AbstractSoundcoreProtocol;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcorePacket;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public abstract class SoundcoreProtocolImplV1 extends AbstractSoundcoreProtocol {
    public static final short CMD_GET_DEVICE_INFO = (short) 0x0101;
    public static final short CMD_GET_EXTENDED_INFO = (short) 0x0105;
    // Session handshake sent after CMD_GET_DEVICE_INFO; device ACKs with empty payload
    public static final short CMD_SESSION_INIT = (short) 0x8105;

    public static final short CMD_NOTIFY_BATTERY_INFO = (short) 0x0301;
    public static final short CMD_NOTIFY_CHARGING_INFO = (short) 0x0401;
    public static final short CMD_NOTIFY_AUDIO_MODE = (short) 0x0106;

    // Unsolicited notifications from the device on connection
    public static final short CMD_NOTIFY_PAIRED_DEVICES = (short) 0x010b;
    public static final short CMD_NOTIFY_CONNECTION_STATUS = (short) 0x020b;
    public static final short CMD_NOTIFY_DEVICE_STATE = (short) 0x0910;
    // Connect/disconnect a specific paired device; payload = 6-byte address (little-endian)
    public static final short CMD_DISCONNECT_DEVICE = (short) 0x810b;
    public static final short CMD_CONNECT_DEVICE = (short) 0x820b;
    public static final short CMD_FORGET_DEVICE = (short) 0x830b;

    public static final short CMD_SET_AUDIO_MODE = (short) 0x8106;
    public static final short CMD_SET_AMBIENT_SOUND_CONTROL_BUTTON_MODE = (short) 0x8206;
    public static final short CMD_SET_TAP_CONTROLS_FUNCTION = (short) 0x8104;
    public static final short CMD_SET_TAP_CONTROLS_ENABLE = (short) 0x9410;
    public static final short CMD_RESET_TAP_CONTROLS = (short) 0x8204;
    public static final short CMD_SET_TOUCH_TONE = (short) 0x8301;
    public static final short CMD_SET_TOUCH_LOCK = (short) 0x8304;
    public static final short CMD_SET_AUTO_POWER_OFF = (short) 0x8601;
    public static final short CMD_SET_FIND_DEVICE = (short) 0x8910;
    public static final short CMD_ENABLE_PAIRING_MODE = (short) 0x850b;

    public static final short CMD_SET_3D_SURROUND = (short) 0x8602;
    public static final short CMD_SET_BATTERY_LOW_TONE = (short) 0x8210;

    private static final int DEFAULT_BATTERY_MULTIPLIER = 10;
    private static final int DEFAULT_BATTERY_OFFSET = 0;

    public static final int battery_case = 0;
    public static final int battery_earphone_left = 1;
    public static final int battery_earphone_right = 2;

    protected SoundcoreProtocolImplV1(final GBDevice device) {
        super(device);
    }

    /**
     * Decodes the common V1 response envelope and routes commands to device-specific hooks.
     *
     * <p>The payload layouts are not shared by all V1 devices, so this method deliberately
     * centralizes only packet parsing and command routing. Concrete protocols can incrementally
     * implement the protected hooks as their payload contracts are established.</p>
     */
    @Override
    public GBDeviceEvent[] decodeResponse(final byte[] responseData) {
        if (responseData == null) {
            return null;
        }

        final SoundcorePacket packet = decodePacket(responseData);

        if (packet == null) {
            return null;
        }

        final short command = packet.getCommand();
        final byte[] payload = packet.getPayload();

        switch (command) {
            case CMD_GET_DEVICE_INFO:
                return decodeDeviceInfo(payload);
            case CMD_GET_EXTENDED_INFO:
                return decodeExtendedInfo(payload);
            case CMD_NOTIFY_BATTERY_INFO:
                return decodeBatteryInfo(payload);
            case CMD_NOTIFY_CHARGING_INFO:
                return decodeChargingInfo(payload);
            case CMD_NOTIFY_AUDIO_MODE:
                return decodeAudioMode(payload);
            case CMD_NOTIFY_PAIRED_DEVICES:
                return decodePairedDevices(payload);
            case CMD_NOTIFY_CONNECTION_STATUS:
                return decodeConnectionStatus(payload);
            case CMD_NOTIFY_DEVICE_STATE:
                return decodeDeviceState(payload);
            case CMD_SESSION_INIT:
            case CMD_DISCONNECT_DEVICE:
            case CMD_CONNECT_DEVICE:
            case CMD_FORGET_DEVICE:
            case CMD_SET_AUDIO_MODE:
            case CMD_SET_TAP_CONTROLS_FUNCTION:
            case CMD_SET_TAP_CONTROLS_ENABLE:
            case CMD_RESET_TAP_CONTROLS:
            case CMD_SET_TOUCH_TONE:
            case CMD_SET_TOUCH_LOCK:
            case CMD_SET_AUTO_POWER_OFF:
            case CMD_SET_FIND_DEVICE:
            case CMD_ENABLE_PAIRING_MODE:
                return decodeAcknowledgement(command, payload);
            default:
                return decodeCommand(command, payload);
        }
    }

    /** Hook for the device-specific CMD_GET_DEVICE_INFO payload layout. */
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /**
     * Decodes the device-info layout shared by Liberty-derived earbuds. Sport X20 uses this
     * layout for its common header and adds its own settings afterwards.
     */
    protected final GBDeviceEvent[] decodeStandardEarbudDeviceInfo(final byte[] payload) {
        if (payload == null || payload.length < 32) {
            return new GBDeviceEvent[0];
        }

        final GBDeviceEvent[] batteryEvents = handleBatteryInfo(
            new byte[]{payload[2], payload[3]}, getBatteryMultiplier(), getBatteryOffset()
        );
        return new GBDeviceEvent[]{
            batteryEvents[0],
            batteryEvents[1],
            buildVersionInfo(
                readString(payload, 6, 5), // firmware1
                readString(payload, 11, 5), // firmware2
                readString(payload, 16, 16) // serialnumber
            )
        };
    }

    /** Hook for the device-specific CMD_GET_EXTENDED_INFO payload layout. */
    protected GBDeviceEvent[] decodeExtendedInfo(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /**
     * Decodes the common left/right[/case] battery notification layout. Protocols only need to
     * override the multiplier and offset when their wire encoding differs from the defaults.
     */
    protected GBDeviceEvent[] decodeBatteryInfo(final byte[] payload) {
        if (payload == null || payload.length < 2) {
            return new GBDeviceEvent[0];
        }
        return handleBatteryInfo(payload, getBatteryMultiplier(), getBatteryOffset());
    }

    protected int getBatteryMultiplier() {
        return DEFAULT_BATTERY_MULTIPLIER;
    }

    protected int getBatteryOffset() {
        return DEFAULT_BATTERY_OFFSET;
    }

    /**
     * Builds battery events for the earbud/case notification payload used by V1 earbuds.
     * The first two bytes contain left and right earbuds; an optional third byte contains the
     * charging case. Any additional bytes are ignored. The payload values are deliberately kept
     * in their original wire format: concrete protocols provide the multiplier and optional offset.
     */
    protected final GBDeviceEvent[] handleBatteryInfo(final byte[] payload, final int multiplier) {
        return handleBatteryInfo(payload, multiplier, DEFAULT_BATTERY_OFFSET);
    }

    /** Uses the default 10x multiplier and zero raw-value offset. */
    protected final GBDeviceEvent[] handleBatteryInfo(final byte[] payload) {
        return handleBatteryInfo(payload, DEFAULT_BATTERY_MULTIPLIER, DEFAULT_BATTERY_OFFSET);
    }

    protected final GBDeviceEvent[] handleBatteryInfo(final byte[] payload,
                                                       final int multiplier,
                                                       final int offset) {
        if (payload == null || payload.length < 2) {
            return new GBDeviceEvent[0];
        }

        final int[] batteryIndexes = payload.length >= 3
                ? new int[]{battery_earphone_left, battery_earphone_right, battery_case}
                : new int[]{battery_earphone_left, battery_earphone_right};
        final GBDeviceEvent[] events = new GBDeviceEvent[batteryIndexes.length];
        for (int index = 0; index < batteryIndexes.length; index++) {
            events[index] = buildBatteryInfo(
                    batteryIndexes[index],
                    (payload[index] + offset) * multiplier
            );
        }
        return events;
    }

    /** Builds a battery event for devices, such as Q30, with one battery value. */
    protected final GBDeviceEvent[] handleBatteryInfo(final byte battery,
                                                       final int batteryIndex,
                                                       final int multiplier) {
        return new GBDeviceEvent[]{buildBatteryInfo(batteryIndex, battery * multiplier)};
    }

    protected final GBDeviceEvent[] handleBatteryInfo(final byte battery, final int batteryIndex) {
        return handleBatteryInfo(battery, batteryIndex, DEFAULT_BATTERY_MULTIPLIER);
    }

    /** Hook for the device-specific CMD_NOTIFY_CHARGING_INFO payload layout. */
    protected GBDeviceEvent[] decodeChargingInfo(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Hook for the device-specific CMD_NOTIFY_AUDIO_MODE payload layout. */
    protected GBDeviceEvent[] decodeAudioMode(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Hook for the device-specific CMD_NOTIFY_PAIRED_DEVICES payload layout. */
    protected GBDeviceEvent[] decodePairedDevices(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Hook for the device-specific CMD_NOTIFY_CONNECTION_STATUS payload layout. */
    protected GBDeviceEvent[] decodeConnectionStatus(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Hook for the device-specific CMD_NOTIFY_DEVICE_STATE payload layout. */
    protected GBDeviceEvent[] decodeDeviceState(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Hook for common command acknowledgements whose payload has no shared semantics. */
    protected GBDeviceEvent[] decodeAcknowledgement(final short command, final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /**
     * Hook for commands without a common payload contract, including device-specific commands
     * and acknowledgements. Returning an empty array marks the packet as consumed; returning
     * {@code null} preserves the standard unsupported-response behavior.
     */
    protected GBDeviceEvent[] decodeCommand(final short command, final byte[] payload) {
        return null;
    }

    public byte[] encodeDeviceInfoRequest() {
        return encodeRequest(CMD_GET_DEVICE_INFO);
    }

    /** Requests extended device configuration (firmware details, serial, settings). */
    public byte[] encodeExtendedInfoRequest() {
        return encodeRequest(CMD_GET_EXTENDED_INFO);
    }

    /** Sent after CMD_GET_DEVICE_INFO to finalise the session with the device. */
    public byte[] encodeSessionInitRequest() {
        return encodeRequest(CMD_SESSION_INIT);
    }

    @Override
    public byte[] encodeFindDevice(final boolean start) {
        return encodeCommand(CMD_SET_FIND_DEVICE, new byte[]{
                encodeBoolean(start), // left
                encodeBoolean(start), // right
                0x00
        });
    }

    protected byte[] encodePairingMode() {
        return encodeCommand(CMD_ENABLE_PAIRING_MODE, new byte[]{0x00, (byte) 0x90});
    }

    protected byte[] encodeControlFunction(final boolean right, final byte action, final byte function) {
        return encodeCommand(CMD_SET_TAP_CONTROLS_FUNCTION, new byte[]{encodeBoolean(right), action, function});
    }

    /** Encodes the audio-mode cycle assigned to a tap control. */
    protected byte[] encodeControlAmbientMode(final boolean anc,
                                              final boolean transparency,
                                              final boolean normal) {
        final byte ambientModes = (byte) (
            (normal       ? 1 << 2 : 0) | // 4
            (transparency ? 1 << 1 : 0) | // 2
            (anc          ? 1      : 0)   // 1
        );

        return encodeCommand(CMD_SET_AMBIENT_SOUND_CONTROL_BUTTON_MODE, new byte[]{ambientModes});
    }

    /**
     * Encodes the following settings to a payload to set the audio-mode on the headphones:
     * PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL If ANC, Transparent or neither should be active
     * PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING If the strenght of the ANC should be set manual or adaptively according to ambient noise
     * PREF_SONY_AMBIENT_SOUND_LEVEL How strong the ANC should be in manual mode
     * PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE If the Transparency should focus on vocals or should be fully transparent
     * PREF_SOUNDCORE_WIND_NOISE_REDUCTION If Transparency or ANC should reduce Wind Noise
     * @return The payload
    */
    protected byte[] encodeAdvancedAudioMode(final boolean appendStateByte) {
        final Prefs prefs = getDevicePrefs();
        final Byte ambientSoundMode = encodeAmbientSoundMode(prefs.getString(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                "off"
        ));

        if (ambientSoundMode == null) {
            return null;
        }

        final Byte ancStrength = encodeAncStrength(prefs.getInt(
                DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL,
                0
        ));

        if (ancStrength == null) {
            return null;
        }

        final byte vocalMode = encodeBoolean(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE,
                false
        ));
        final byte adaptiveAnc = encodeBoolean(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING,
                true
        ));
        final byte windNoiseReduction = encodeBoolean(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION,
                false
        ));

        final byte[] payload = new byte[]{
                ambientSoundMode.byteValue(),
                ancStrength.byteValue(),
                vocalMode,
                adaptiveAnc,
                windNoiseReduction
        };

        if (appendStateByte) {
            final byte[] statePayload = Arrays.copyOf(payload, payload.length + 1);
            statePayload[statePayload.length - 1] = 0x01;
            return encodeCommand(CMD_SET_AUDIO_MODE, statePayload);
        }

        return encodeCommand(CMD_SET_AUDIO_MODE, payload);
    }

    protected void decodeAdvancedAudioMode(final byte[] payload) {
        if (payload.length < 5) {
            return;
        }

        final SharedPreferences.Editor editor = getDevicePrefs().getPreferences().edit();

        editor.putString(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                decodeAmbientSoundMode(payload[0])
        );
        editor.putInt(
                DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL,
                decodeAncStrength(payload[1])
        );
        editor.putBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE,
                payload[2] == 0x01
        );
        editor.putBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING,
                payload[3] == 0x01
        );
        editor.putBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION,
                payload[4] == 0x01
        );
        editor.apply();
    }

    protected Byte encodeAmbientSoundMode(final String ambientMode) {
        switch (ambientMode) {
            case "noise_cancelling":
                return (byte) 0x00;
            case "ambient_sound":
                return (byte) 0x01;
            case "off":
                return (byte) 0x02;
            default:
                return null;
        }
    }

    protected String decodeAmbientSoundMode(final byte ambientMode) {
        switch (ambientMode) {
            case 0x00:
                return "noise_cancelling";
            case 0x01:
                return "ambient_sound";
            case 0x02:
            default:
                return "off";
        }
    }

    protected Byte encodeAncStrength(final int strength) {
        if (strength < 0 || strength > 2) {
            return null;
        }

        return (byte) ((strength + 1) << 4);
    }

    protected int decodeAncStrength(final byte strength) {
        switch (strength & 0x30) {
            case 0x20:
                return 1;
            case 0x30:
                return 2;
            case 0x10:
            default:
                return 0;
        }
    }
}
