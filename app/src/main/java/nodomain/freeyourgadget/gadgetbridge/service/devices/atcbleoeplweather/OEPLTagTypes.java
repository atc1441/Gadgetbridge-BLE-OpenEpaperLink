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

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The panel catalogue, taken from the OpenEPaperLink tag type definitions
 * (OpenEPaperLink/OpenEPaperLink, resources/tagtypes). Gadgetbridge has no internet access, so
 * the table is generated into the build rather than fetched at runtime.
 * <p>
 * It answers the two things the tag itself cannot tell us: whether the accent colour is red or
 * yellow - the firmware only reports a colour count - and how far the image buffer is turned
 * against the physical panel.
 * <p>
 * Generated from 109 definitions; do not edit by hand.
 */
public final class OEPLTagTypes {

    public static class TagType {
        public final int hardwareType;
        public final String name;
        /** Physical panel size, in pixels. */
        public final int width;
        public final int height;
        /** Quarter turns between the image buffer and the panel. */
        public final int rotateBuffer;
        public final int colorCount;
        public final boolean hasRed;
        public final boolean hasYellow;

        TagType(final int hardwareType, final String name, final int width, final int height,
                final int rotateBuffer, final int colorCount,
                final boolean hasRed, final boolean hasYellow) {
            this.hardwareType = hardwareType;
            this.name = name;
            this.width = width;
            this.height = height;
            this.rotateBuffer = rotateBuffer;
            this.colorCount = colorCount;
            this.hasRed = hasRed;
            this.hasYellow = hasYellow;
        }

        /**
         * How far the rendered card has to be turned to land in the panel buffer.
         * OpenEPaperLink states the rotation from the buffer to the panel, and we go the other
         * way round, so this is the inverse.
         */
        public int rotationDegrees() {
            return ((4 - (rotateBuffer % 4)) % 4) * 90;
        }

        /** True when the accent colour of this panel is yellow rather than red. */
        public boolean accentIsYellow() {
            return hasYellow && !hasRed;
        }
    }

    private static final Map<Integer, TagType> TYPES;

    private static TagType e(final int hw, final String name, final int w, final int h,
                             final int rot, final int colors, final boolean red, final boolean yellow) {
        return new TagType(hw, name, w, h, rot, colors, red, yellow);
    }

    static {
        final Map<Integer, TagType> map = new LinkedHashMap<>(109);
        for (final TagType t : new TagType[]{
            e(0x00, "M2 1.54\"", 152, 152, 0, 3, true, false),
            e(0x01, "M2 2.9\"", 296, 128, 1, 3, true, false),
            e(0x02, "M2 4.2\"", 400, 300, 0, 3, true, false),
            e(0x03, "M2 2.2\"", 212, 104, 1, 3, true, false),
            e(0x04, "M2 2.6\"", 296, 152, 1, 3, true, false),
            e(0x05, "M2 7.4\"", 640, 384, 0, 3, true, false),
            e(0x06, "Opticon 2.2\"", 250, 128, 3, 4, true, true),
            e(0x07, "Opticon 2.9\"", 296, 128, 3, 4, true, true),
            e(0x08, "Opticon 4.2\"", 400, 300, 2, 3, true, false),
            e(0x09, "Opticon 7.5\"", 640, 384, 2, 3, true, false),
            e(0x11, "M2 2.9\" (UC8151)", 296, 128, 1, 3, true, false),
            e(0x12, "M2 4.2\" UC", 400, 300, 0, 3, true, false),
            e(0x21, "ST‐GM29XXF 2.9\"", 296, 128, 1, 2, false, false),
            e(0x22, "M2 2.7\"", 264, 176, 1, 3, true, false),
            e(0x26, "M2 7.5\" BW", 640, 384, 0, 2, false, false),
            e(0x27, "ST‐GM29MT1 2.9\"", 296, 128, 1, 2, false, false),
            e(0x28, "M3 1.6\" BWRY", 168, 168, 0, 4, true, true),
            e(0x29, "M3 2.4\" BWRY", 296, 168, 3, 4, true, true),
            e(0x2A, "M3 3.0\" BWRY", 400, 168, 3, 4, true, true),
            e(0x2B, "M3 2.9\" BWRY", 384, 168, 3, 4, true, true),
            e(0x2C, "M3 4.3\" BWRY", 522, 152, 3, 4, true, true),
            e(0x2D, "M3 12.2\"", 960, 768, 2, 3, true, false),
            e(0x2E, "M3 9.7\"", 960, 672, 2, 3, true, false),
            e(0x2F, "M3 4.3\"", 522, 152, 3, 3, true, false),
            e(0x30, "M3 1.6\"", 200, 200, 0, 3, true, false),
            e(0x31, "M3 2.2\"", 296, 160, 3, 3, true, false),
            e(0x32, "M3 2.6\"", 360, 184, 3, 3, true, false),
            e(0x33, "M3 2.9\"", 384, 168, 3, 3, true, false),
            e(0x34, "M3 4.2\"", 400, 300, 0, 3, true, false),
            e(0x35, "M3 6.0\"", 600, 448, 0, 3, true, false),
            e(0x36, "M3 7.5\"", 800, 480, 0, 3, true, false),
            e(0x37, "M3 11.6\"", 960, 640, 2, 3, true, false),
            e(0x38, "M3 8.2\" BWRY", 1024, 576, 2, 4, true, true),
            e(0x3C, "M3 4.2\" BWY", 400, 300, 0, 3, false, true),
            e(0x40, "M3 2.9\" BW", 384, 168, 3, 2, false, false),
            e(0x41, "M3 5.85\"", 792, 272, 0, 3, true, false),
            e(0x42, "M3 5.85\" BW", 792, 272, 0, 2, false, false),
            e(0x43, "M3 1.3\" Peghook", 144, 200, 0, 3, true, false),
            e(0x44, "M3 5.81\" BW", 720, 256, 1, 2, false, false),
            e(0x45, "M3 2.2 Lite\"", 250, 128, 3, 3, true, false),
            e(0x46, "M3 2.2\" BW", 296, 160, 3, 2, false, false),
            e(0x47, "M3 2.7\"", 300, 200, 3, 3, true, false),
            e(0x48, "M3 5.81\" BWR", 720, 256, 1, 3, true, false),
            e(0x49, "M3 5.81\" V2 BWR", 720, 256, 0, 3, true, false),
            e(0x4A, "M3 1.6\" 200px BWRY", 200, 200, 0, 4, true, true),
            e(0x4B, "M3 2.2\" BWRY", 296, 160, 3, 4, true, true),
            e(0x4C, "M3 7.5\" BWRY", 800, 480, 0, 4, true, true),
            e(0x4D, "M3 11.6\" BWRY", 960, 640, 0, 4, true, true),
            e(0x4E, "M3 2.6\" BW", 360, 184, 3, 2, false, false),
            e(0x4F, "M3 2.6\" BWRY", 360, 184, 3, 4, true, true),
            e(0x50, "HD150 5.83\" BWR", 648, 480, 0, 3, true, false),
            e(0x53, "M3 3.5\" BWRY RTL", 480, 224, 3, 4, true, true),
            e(0x54, "HS BW 2.13\"", 256, 128, 1, 2, false, false),
            e(0x55, "HS BWR 2.13\"", 256, 128, 1, 3, true, false),
            e(0x56, "HS BWR 2.66\"", 296, 152, 1, 3, true, false),
            e(0x57, "TLSR BWR 1.54\"", 200, 200, 1, 3, true, false),
            e(0x58, "TLSR BW 2.13\"", 256, 128, 1, 3, true, false),
            e(0x59, "TLSR BWR 2.13\"", 264, 136, 1, 4, true, false),
            e(0x5A, "HS BW 2.13\" LowRes", 212, 104, 1, 2, false, false),
            e(0x60, "HS BWY 3.5\"", 384, 184, 1, 3, false, true),
            e(0x61, "HS BWR 3.5\"", 384, 184, 1, 3, true, false),
            e(0x62, "HS BW 3.5\"", 384, 184, 1, 2, false, false),
            e(0x63, "TLSR BWR 4.2\"", 400, 300, 0, 3, true, false),
            e(0x66, "HS BWY 7,5\"", 800, 480, 2, 3, false, true),
            e(0x67, "HS 2.00\" BWY", 152, 200, 1, 3, false, true),
            e(0x68, "HS BWY 3.46\"", 480, 176, 1, 3, false, true),
            e(0x69, "TLSR BW 2.13\"", 250, 136, 1, 2, false, false),
            e(0x6A, "HS BWR 5,83\"", 648, 480, 0, 3, true, false),
            e(0x6B, "HS BWRY 7,5\"", 800, 480, 2, 4, true, true),
            e(0x6C, "HS BWRY 2,00\"", 152, 200, 1, 4, true, true),
            e(0x6D, "HS BWRY 3,5\"", 384, 184, 1, 4, true, true),
            e(0x6E, "HS BWRY 2,9\"", 296, 128, 1, 4, true, true),
            e(0x6F, "HS BWRY 2,60\"", 296, 152, 1, 4, true, true),
            e(0x70, "HS 2.9\" HighRes", 384, 168, 1, 3, true, false),
            e(0x71, "HS 2.13\" BWR High Res", 296, 144, 1, 3, true, false),
            e(0x80, "Chroma 7.4\"", 640, 384, 0, 3, true, false),
            e(0x81, "Chroma Aeon 74 7.4\"", 800, 480, 0, 3, true, false),
            e(0x82, "Chroma29 2.9\"", 296, 128, 1, 3, true, false),
            e(0x83, "Chroma42 4.2\"", 400, 300, 0, 3, true, false),
            e(0x90, "M3 4.2\" BWRY", 400, 300, 0, 4, true, true),
            e(0x91, "M3 1.6\" 200px BWRY", 200, 200, 3, 4, true, true),
            e(0x92, "M3 3.5\" BWRY", 384, 184, 3, 4, true, true),
            e(0x93, "M3 6.0\" BWRY", 600, 448, 0, 4, true, true),
            e(0xB0, "Gicisky BLE EPD BW 2.13\"", 250, 128, 3, 2, false, false),
            e(0xB1, "Gicisky BLE EPD BWR 2.13\"", 250, 128, 3, 3, true, false),
            e(0xB2, "Gicisky BLE EPD BW 2.9\"", 296, 128, 1, 2, false, false),
            e(0xB3, "Gicisky BLE EPD BWR 2.9\"", 296, 128, 1, 3, true, false),
            e(0xB5, "Gicisky BLE EPD BWR 4.2\"", 400, 300, 0, 3, true, false),
            e(0xBA, "Gicisky BLE TFT 2.13\"", 250, 136, 1, 2, false, false),
            e(0xBB, "Gicisky BLE EPD BWRY 2.9\"", 296, 128, 1, 4, true, true),
            e(0xBD, "BLE EPD BWR 2.9\" Silabs", 384, 168, 1, 3, true, false),
            e(0xBE, "ATC MiThermometer BLE", 6, 8, 1, 0, false, false),
            e(0xC0, "BWRY example", 360, 184, 1, 4, true, true),
            e(0xC1, "ACeP 4.01", 640, 400, 0, 7, true, true),
            e(0xC2, "Spectra 7.3", 800, 480, 0, 7, true, true),
            e(0xD0, "GFSK10 2.7\" BWR 176x264", 264, 176, 1, 3, true, false),
            e(0xD1, "GFSK10 1.54\" BWR 152x152", 152, 152, 1, 3, true, false),
            e(0xD2, "GFSK10 2.13\" BWR 212x104", 212, 104, 1, 3, true, false),
            e(0xD3, "GFSK10 7.4\" BWR 800x480", 800, 480, 1, 3, true, false),
            e(0xD4, "GFSK10 12.2\" BWR 960x768", 960, 768, 2, 3, true, false),
            e(0xD5, "GFSK10 9.7\" BWR 960x672", 960, 672, 0, 3, true, false),
            e(0xE0, "TFT 320x172", 320, 172, 0, 3, true, false),
            e(0xE1, "TFT 160x80", 160, 80, 1, 3, true, false),
            e(0xE2, "LILYGO TPANEL 4\"", 480, 480, 0, 3, true, false),
            e(0xE3, "GDEM1085Z51 10.85\"", 1360, 480, 0, 3, true, false),
            e(0xE4, "BLE TFT 128x128", 128, 128, 0, 3, true, false),
            e(0xE5, "TFT 240x320", 320, 172, 0, 3, true, false),
            e(0xF0, "SLT‐EM007 Segmented", 0, 0, 0, 0, false, false),
            e(0xFA, "ConfigMode", 0, 0, 0, 0, false, false),
        }) {
            map.put(t.hardwareType, t);
        }
        TYPES = Collections.unmodifiableMap(map);
    }

    private OEPLTagTypes() {
    }

    @Nullable
    public static TagType byHardwareType(final int hardwareType) {
        return TYPES.get(hardwareType);
    }

    public static Map<Integer, TagType> all() {
        return TYPES;
    }

    public static String nameOf(final int hardwareType) {
        final TagType t = TYPES.get(hardwareType);
        return t != null ? t.name : String.format("Unknown (0x%02X)", hardwareType);
    }

    /**
     * One line naming a tag the way a list has to name it: the model and the panel behind it.
     * A type the catalogue does not know still gets its code, which is the only thing that can
     * be looked up afterwards.
     */
    public static String describe(final int hardwareType) {
        final TagType t = TYPES.get(hardwareType);
        if (t == null) {
            return nameOf(hardwareType);
        }
        return t.name + " · " + t.width + "×" + t.height;
    }
}
