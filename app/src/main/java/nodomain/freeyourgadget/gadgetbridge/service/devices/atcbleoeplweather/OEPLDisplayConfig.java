/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather;

import android.content.Context;

import androidx.annotation.NonNull;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The tag answers {@link OEPLProtocol#CMD_GET_INFOS} with its raw, packed settings structure.
 * Newer firmware inserted a screen rotation byte in the middle of it, which shifts every field
 * behind it. Rather than hardcode offsets for one firmware revision, both layouts are decoded
 * and the one whose values are self consistent wins.
 */
public class OEPLDisplayConfig {
    public int settingsVersion;
    public int hardwareType;   // the value {@link OEPLProtocol#CMD_SET_TYPE} expects back
    public int screenType;
    public boolean widthHeightSwapped;
    /**
     * Size of the buffer we have to pack, which is not the physical panel size: the firmware
     * reports screen_w and screen_h in its own frame, and on most panels the buffer is a
     * quarter turn away from what you see. For a 384x184 panel the buffer is 184 wide.
     */
    public int bufferWidth;
    public int bufferHeight;
    /**
     * The firmware counts the colours beyond white: 1 = black/white, 2 = one accent,
     * 3 = black/white/red/yellow. Use {@link #colorCount()} for the actual number of colours.
     */
    public int colors;
    /** The tag inverts the black plane before driving the panel when this is set. */
    public boolean blackInvert;
    /** Likewise for the accent plane. */
    public boolean secondInvert;
    public int guiRotation;
    public boolean oeplEnabled;
    public int bleAdvertisingInterval; // milliseconds
    /**
     * Whether that interval is a real setting rather than an unset field. A firmware that has
     * never had one written leaves it at zero, which says nothing about the panel.
     */
    public boolean advertisingIntervalKnown;
    public boolean valid;
    /** True when this came from the dynamic hardware read rather than the settings blob. */
    public boolean fromDynamicRead;
    /** True when nothing readable came from the display and the catalogue filled in. */
    public boolean fromCatalogue;

    /**
     * Decodes the reply to {@link OEPLProtocol#CMD_READ_DYNAMIC_TYPE}, which is the display
     * describing its own hardware rather than the stored settings blob. This is the most
     * authoritative source for geometry and colour count, and is what the vendor web tool reads.
     */
    public static OEPLDisplayConfig parseDynamic(final byte[] frame) {
        final OEPLDisplayConfig c = new OEPLDisplayConfig();
        if (frame == null || frame.length < 2 + 43) {
            return c;
        }
        final ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(2); // skip the echoed notification id
        c.screenType = buf.getShort() & 0xffff;
        c.hardwareType = buf.getShort() & 0xffff;
        buf.getShort();                               // screen functions
        c.widthHeightSwapped = buf.get() != 0;
        buf.getShort();                               // screen w/h inversed
        final int screenH2 = buf.getShort() & 0xffff;
        final int screenW2 = buf.getShort() & 0xffff;
        c.setBufferSize(screenW2, screenH2);
        buf.getShort();                               // height offset
        buf.getShort();                               // width offset
        c.colors = buf.getShort() & 0xffff;
        c.blackInvert = buf.getShort() != 0;
        c.secondInvert = buf.getShort() != 0;
        buf.getInt();                                 // epd pinout pointer
        buf.getInt();                                 // led pinout pointer
        buf.getInt();                                 // nfc pinout pointer
        buf.getInt();                                 // flash pinout pointer
        buf.getShort();                               // adc pin
        buf.getShort();                               // uart pin
        // the rotation byte trails the pinout structures, when the firmware is new enough
        if (buf.remaining() > 0) {
            c.guiRotation = frame[frame.length - 1] & 0x03;
        }
        c.fromDynamicRead = true;
        c.valid = c.bufferWidth >= 32 && c.bufferWidth <= 2048
                && c.bufferHeight >= 32 && c.bufferHeight <= 2048
                && c.colors >= 1 && c.colors <= 8;
        return c;
    }

    public static OEPLDisplayConfig parse(final byte[] frame) {
        // Both layouts are decoded and the one that reads back consistently wins. The
        // advertising interval is what tells them apart - it sits behind the rotation byte,
        // so a reading that is one byte out of step has nonsense there - but only when the
        // firmware has ever set one. When neither reading has a usable interval the two agree
        // about everything that matters, because everything behind that byte is then unset.
        final OEPLDisplayConfig withRotation = decode(frame, true);
        if (withRotation.valid && withRotation.advertisingIntervalKnown) {
            return withRotation;
        }
        final OEPLDisplayConfig withoutRotation = decode(frame, false);
        if (withoutRotation.valid && withoutRotation.advertisingIntervalKnown) {
            return withoutRotation;
        }
        if (withRotation.valid) {
            return withRotation;
        }
        if (withoutRotation.valid) {
            return withoutRotation;
        }
        return withRotation; // nothing looked sane; return the modern reading for logging
    }

    /**
     * What the catalogue knows about a hardware type, as a configuration to draw from.
     * <p>
     * A display whose firmware answers nothing we can read - or nothing at all - still
     * advertises what it is, and the panel behind a hardware type is a fact of the hardware
     * rather than of the firmware. Drawing from the catalogue is a far better answer than
     * leaving the panel blank because a settings structure was not understood.
     */
    public static OEPLDisplayConfig fromCatalogue(final int hardwareType) {
        final OEPLDisplayConfig c = new OEPLDisplayConfig();
        final OEPLTagTypes.TagType type = OEPLTagTypes.byHardwareType(hardwareType);
        if (type == null) {
            return c;
        }
        c.hardwareType = hardwareType;
        // the catalogue states the panel as it is seen; the buffer is the short axis first,
        // the same way the firmware reports screen_w and screen_h
        c.bufferWidth = Math.min(type.width, type.height);
        c.bufferHeight = Math.max(type.width, type.height);
        c.colors = Math.max(1, type.colorCount - 1);
        c.blackInvert = true;
        c.fromCatalogue = true;
        c.valid = c.bufferWidth >= 32 && c.bufferHeight >= 32;
        return c;
    }

    private static OEPLDisplayConfig decode(final byte[] frame, final boolean hasGuiRotation) {
        final OEPLDisplayConfig c = new OEPLDisplayConfig();
        if (frame == null || frame.length < 48) {
            return c;
        }
        final ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(2);        // skip the echoed command id
        buf.getInt();           // magic
        c.settingsVersion = buf.getInt();
        buf.getInt();           // declared structure length
        c.hardwareType = buf.getShort() & 0xffff;
        buf.get();              // screen available
        c.screenType = buf.getShort() & 0xffff;
        buf.getShort();         // screen functions
        c.widthHeightSwapped = buf.get() == 0x01;
        buf.getShort();         // screen w/h inversed
        final int screenH = buf.getShort() & 0xffff;
        final int screenW = buf.getShort() & 0xffff;
        c.setBufferSize(screenW, screenH);
        buf.getShort();         // height offset
        buf.getShort();         // width offset
        c.colors = buf.get() & 0xff;
        c.blackInvert = buf.get() != 0;
        c.secondInvert = buf.get() != 0;
        if (hasGuiRotation) {
            c.guiRotation = buf.get() & 0xff;
        }
        c.oeplEnabled = buf.get() == 0x01;
        buf.getInt();           // OEPL wakeup seconds
        buf.get();              // BLE enabled
        c.bleAdvertisingInterval = buf.getInt();
        c.advertisingIntervalKnown = c.bleAdvertisingInterval >= 100
                && c.bleAdvertisingInterval <= 300000;
        if (!c.advertisingIntervalKnown) {
            c.bleAdvertisingInterval = 0;
        }

        // The panel is what decides whether this reading is usable. The interval behind it is
        // a setting, and a firmware that has never had one written leaves it at zero - which
        // used to make the whole configuration count as broken, and with it went the picture:
        // the display connected, was found to have no usable configuration, and was let go
        // again without ever being sent anything.
        c.valid = c.bufferWidth >= 32 && c.bufferWidth <= 2048
                && c.bufferHeight >= 32 && c.bufferHeight <= 2048
                && c.colors >= 1 && c.colors <= 4
                && c.guiRotation <= 3;
        return c;
    }


    /** Applies the firmware's screen_w / screen_h the same way the vendor web tool does. */
    private void setBufferSize(final int screenW, final int screenH) {
        if (widthHeightSwapped) {
            bufferWidth = screenH;
            bufferHeight = screenW;
        } else {
            bufferWidth = screenW;
            bufferHeight = screenH;
        }
    }

    /** The catalogue entry for this panel, if OpenEPaperLink knows the hardware type. */
    public OEPLTagTypes.TagType tagType() {
        return OEPLTagTypes.byHardwareType(hardwareType);
    }

    /**
     * Quarter turns the rendered card needs before it matches the panel. The tag reports no such
     * thing, so this comes from the OpenEPaperLink catalogue.
     */
    public int autoRotationDegrees() {
        final OEPLTagTypes.TagType type = tagType();
        return type != null ? type.rotationDegrees() : 0;
    }

    /** Whether the accent colour is yellow. The firmware only ever reports a colour count. */
    public boolean autoAccentIsYellow() {
        final OEPLTagTypes.TagType type = tagType();
        return type != null && type.accentIsYellow();
    }

    /** Planes the image payload has to carry: one for plain black/white, two otherwise. */
    public int planeCount() {
        return colors > 1 ? 2 : 1;
    }

    /** Actual number of colours the panel can show, white included. */
    public int colorCount() {
        return Math.max(2, Math.min(4, colors + 1));
    }

    public int planeSizeBytes() {
        return (bufferWidth * bufferHeight) / 8;
    }

    /**
     * A one line summary for the overview screen, in the user's terms rather than in field
     * names - and in the user's language: this line is read on the phone, not drawn on the
     * panel, and it used to be German whatever the app was set to.
     */
    @NonNull
    public String describe() {
        final Context context = GBApplication.getContext();
        if (context == null) {
            return bufferWidth + "x" + bufferHeight + ", " + colorCount() + " colours";
        }
        final StringBuilder line = new StringBuilder(context.getString(
                R.string.oepl_config_summary, bufferWidth, bufferHeight, colorCount(),
                autoRotationDegrees()));
        if (blackInvert) {
            line.append(", ").append(context.getString(R.string.oepl_config_black_inverted));
        }
        if (secondInvert) {
            line.append(", ").append(context.getString(R.string.oepl_config_accent_inverted));
        }
        if (widthHeightSwapped) {
            line.append(", ").append(context.getString(R.string.oepl_config_swapped));
        }
        return line.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return "OEPLDisplayConfig{settingsVersion=" + settingsVersion
                + ", hardwareType=" + hardwareType
                + ", screenType=" + screenType
                + ", buffer=" + bufferWidth + "x" + bufferHeight
                + ", colors=" + colors + " (" + colorCount() + " total)"
                + ", blackInvert=" + blackInvert
                + ", secondInvert=" + secondInvert
                + ", guiRotation=" + guiRotation
                + ", whSwapped=" + widthHeightSwapped
                + ", oeplEnabled=" + oeplEnabled
                + ", advInterval=" + bleAdvertisingInterval
                + ", valid=" + valid + "}";
    }
}
