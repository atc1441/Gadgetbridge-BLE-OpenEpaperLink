package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.q30;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class SoundcoreQ30Protocol extends SoundcoreProtocolImplV1 {

    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreQ30Protocol.class);

    private static final short CMD_SET_EQUALIZER = (short) 0x8102;
    private static final int BATTERY_MULTIPLIER = 20;

    protected SoundcoreQ30Protocol(GBDevice device) {
        super(device);
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        if (payload.length < 60) {
            LOG.warn("CMD_GET_DEVICE_INFO payload too short: {} bytes", payload.length);
            return new GBDeviceEvent[0];
        }

        decodeEqualizer(Arrays.copyOfRange(payload, 2, 12));
        decodeAudioMode(Arrays.copyOfRange(payload, 35, 39));

        final String firmware1 = readString(payload, 39, 5);
        final String serialNumber = readString(payload, 44, 16);
        return new GBDeviceEvent[]{
                handleBatteryInfo(payload[0], 0, BATTERY_MULTIPLIER)[0],
                buildVersionInfo(firmware1, "", serialNumber)
        };
    }

    @Override
    protected GBDeviceEvent[] decodeAudioMode(final byte[] payload) {
        decodeAudioModePayload(payload);
        return new GBDeviceEvent[0];
    }

    /** Q30 reports its single battery in CMD_GET_DEVICE_INFO, not the earbud/case notification layout. */
    @Override
    protected GBDeviceEvent[] decodeBatteryInfo(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    @Override
    protected GBDeviceEvent[] decodeCommand(final short command, final byte[] payload) {
        if (command == CMD_SET_EQUALIZER) {
            decodeEqualizer(payload);
            return new GBDeviceEvent[0];
        }
        LOG.debug("Unknown incoming message - command: {} ({} bytes)", command, payload.length);
        return new GBDeviceEvent[0];
    }


    @Override
    public byte[] encodeSendConfiguration(String config) {
        switch (config) {
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE:
                return encodeAudioMode();

            default:
                LOG.debug("Unsupported CONFIG: " + config);
        }

        return super.encodeSendConfiguration(config);
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
    private byte[] encodeAudioMode() {
        Prefs prefs = getDevicePrefs();

        final Byte ambient_sound_mode = encodeAmbientSoundMode(prefs.getString(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                "off"
        ));

        if (ambient_sound_mode == null) {
            LOG.error("Invalid Ambient Mode selected");
            return null;
        }

        byte anc_mode;
        switch (prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, "transport")) {
            case "transport":
                anc_mode = 0x00;
                break;
            case "outdoor":
                anc_mode = 0x01;
                break;
            case "indoor":
                anc_mode = 0x02;
                break;
            default:
                LOG.error("Invalid ANC Mode selected");
                return null;
        }

        byte[] payload = new byte[]{ambient_sound_mode.byteValue(), anc_mode, 0x01};
        return encodeCommand(CMD_SET_AUDIO_MODE, payload);
    }

    /**
     * Gets triggered when the button on the device is pressed or transparency toggled with the right palm.
     */
    private void decodeAudioModePayload(byte[] payload) {
        if (payload.length < 2) {
            LOG.warn("Audio mode payload too short: {} bytes", payload.length);
            return;
        }
        SharedPreferences prefs = getDevicePrefs().getPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        String ambient_sound_mode = decodeAmbientSoundMode(payload[0]);
        String anc_mode = "transport";

        if (payload[1] == 0x00) {
            anc_mode = "transport";
        } else if (payload[1] == 0x01) {
            anc_mode = "outdoor";
        } else if (payload[1] == 0x02) {
            anc_mode = "indoor";
        }

        // payload has two more bytes
        // payload[2] always 1 ?
        // payload[3] checksum ?

        editor.putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL, ambient_sound_mode);
        editor.putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, anc_mode);
        editor.apply();
    }

    private byte[] encodeEqualizer() {
        // example payload for a plain eq (all frequencies the same strength):
        // plain eq: fe fe 78 78 78 78 78 78 78 78

        byte band1 = 0x78;
        byte band2 = 0x78;
        byte band3 = 0x78;
        byte band4 = 0x78;
        byte band5 = 0x78;
        byte band6 = 0x78;
        byte band7 = 0x78;
        byte band8 = 0x78;

        byte[] payload = new byte[]{(byte) 0xfe, (byte) 0xfe, band1, band2, band3, band4, band5, band6, band7, band8};
        return encodeCommand(CMD_SET_EQUALIZER, payload);
    }

    private void decodeEqualizer(byte[] payload) {
        if (payload.length < 10) {
            LOG.warn("Equalizer payload too short: {} bytes", payload.length);
            return;
        }
        // payload[0] und payload[1] immer 0xfe ?
        int band1 = Byte.toUnsignedInt(payload[2]);
        int band2 = Byte.toUnsignedInt(payload[3]);
        int band3 = Byte.toUnsignedInt(payload[4]);
        int band4 = Byte.toUnsignedInt(payload[5]);
        int band5 = Byte.toUnsignedInt(payload[6]);
        int band6 = Byte.toUnsignedInt(payload[7]);
        int band7 = Byte.toUnsignedInt(payload[8]);
        int band8 = Byte.toUnsignedInt(payload[9]);
    }


    byte[] encodeMysteryDataRequest2() {
        return encodeRequest(CMD_GET_EXTENDED_INFO);
    }
}
