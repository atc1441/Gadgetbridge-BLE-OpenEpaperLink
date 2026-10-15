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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl;

import android.content.Context;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;

/**
 * The two typefaces the cards are drawn with.
 * <p>
 * Signika SemiBold carries the text and Weather Icons carries the pictograms; both are SIL Open
 * Font License faces, bundled under res/font with their licence in assets/oepl. They are the
 * same two faces an OpenEPaperLink access point draws with, which is what makes a card rendered
 * here look like one rendered there.
 * <p>
 * Loading can fail - a unit test may run without resources - so every getter falls back to a
 * stock typeface and {@link #hasIcons()} says whether pictograms are available at all.
 */
public final class OeplFonts {
    private static Typeface text;
    private static Typeface icons;
    private static boolean loaded;

    private OeplFonts() {
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Context context = null;
        try {
            context = GBApplication.getContext();
        } catch (final Throwable ignored) {
            // no application around, which happens in plain unit tests
        }
        if (context == null) {
            return;
        }
        text = font(context, R.font.oepl_signika);
        icons = font(context, R.font.oepl_weathericons);
    }

    private static Typeface font(final Context context, final int id) {
        try {
            return ResourcesCompat.getFont(context, id);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    /** Signika SemiBold, or a stock bold face when the resource could not be read. */
    public static Typeface text() {
        load();
        return text != null ? text : Typeface.DEFAULT_BOLD;
    }

    /** Weather Icons, or null when pictograms have to be drawn some other way. */
    public static Typeface icons() {
        load();
        return icons;
    }

    public static boolean hasIcons() {
        load();
        return icons != null;
    }

    /** Lets a test inject the faces, or clear them to exercise the fallback path. */
    public static synchronized void override(final Typeface textFace, final Typeface iconFace) {
        text = textFace;
        icons = iconFace;
        loaded = true;
    }
}
