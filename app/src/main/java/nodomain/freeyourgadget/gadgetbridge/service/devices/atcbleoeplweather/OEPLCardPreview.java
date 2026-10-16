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
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;

/**
 * Draws a design the way it would reach a particular display - same panel size, same colours,
 * same frame, same quantiser, and with that display's own data - but the right way up instead of
 * in the panel's buffer orientation.
 * <p>
 * This is what the design picker shows. Keeping it out of the activity is what lets the previews
 * be tested without a screen, and it is the same code path the real push goes through, so what
 * the picker shows is what the panel gets.
 */
public final class OEPLCardPreview {

    /** Used when the display has not said what it is yet: a common 2.9" tag. */
    public static final int FALLBACK_WIDTH = 296;
    public static final int FALLBACK_HEIGHT = 128;

    private OEPLCardPreview() {
    }

    /** The panel as it is read, which is a quarter turn away from the buffer on most tags. */
    public static int[] panelSize(final SharedPreferences prefs) {
        final OEPLTagTypes.TagType type = tagType(prefs);
        if (type == null) {
            return new int[]{FALLBACK_WIDTH, FALLBACK_HEIGHT, 3};
        }
        return new int[]{type.width, type.height, type.colorCount};
    }

    @Nullable
    private static OEPLTagTypes.TagType tagType(final SharedPreferences prefs) {
        final int hardware = prefs.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        return hardware >= 0 ? OEPLTagTypes.byHardwareType(hardware) : null;
    }

    public static Bitmap render(final Context context, final GBDevice device,
                                final SharedPreferences prefs, final String id,
                                final int width, final int height, final int colors,
                                final String emptyHint) {
        final CardRenderer.Options options = new CardRenderer.Options();
        options.frame = OEPLPrefs.frame(prefs);
        options.accent = colors <= 2
                ? OEPLImageCodec.BLACK
                : ("yellow".equals(OEPLPrefs.accentMode(prefs))
                ? OEPLImageCodec.YELLOW : OEPLImageCodec.RED);
        options.showFooter = OEPLPrefs.showFooter(prefs);
        options.batteryMillivolts = prefs.getInt(OEPLPrefs.LAST_BATTERY_MV, CardRenderer.NO_VALUE);
        options.panelTemperature = prefs.getInt(OEPLPrefs.LAST_PANEL_TEMPERATURE,
                CardRenderer.NO_VALUE);
        if (emptyHint != null) {
            options.emptyHint = emptyHint;
        }
        // the preview is what the panel gets, name tag included, so that the mode can be seen
        // to be on without walking up to a display
        options.nameTag = OEPLPrefs.nameTag(device);

        final CardContent content = CardContentFactory.create(context, device, prefs, id);
        final Bitmap card = new CardRenderer(options).render(width, height, 0, content);

        // through the quantiser as well, so a preview shows the three or four colours the panel
        // really has rather than the greys a phone screen would happily draw
        final int[] pixels = OEPLImageCodec.pixelsOf(card);
        OEPLImageCodec.quantize(pixels, OEPLImageCodec.palette(colors, false));
        card.setPixels(pixels, 0, width, 0, 0, width, height);
        return card;
    }
}
