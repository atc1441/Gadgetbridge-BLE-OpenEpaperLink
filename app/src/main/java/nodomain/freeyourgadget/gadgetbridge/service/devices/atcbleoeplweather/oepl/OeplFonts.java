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
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
        factorLocale = null;
    }

    private static Locale factorLocale;
    private static float factor = 1f;

    /**
     * How much taller a line of the reader's language is than the bundled face asks for.
     * <p>
     * The face carries Latin, Greek and Cyrillic; everything else is drawn by whatever font
     * Android falls back to. The paint still reports the metrics of the face it was handed
     * though, so a layout that stacks its rows by those metrics stacks Burmese, Malayalam or
     * Tamil too tightly - their marks sit above the Latin ascender and below its descender,
     * and the top row of a card then writes over the edge of the panel.
     * <p>
     * So a sample of the language is measured once and kept: one for the scripts the face
     * covers, and up to about a half more for the ones that stack.
     */
    public static synchronized float lineFactor() {
        final Locale locale = Locale.getDefault();
        if (locale.equals(factorLocale)) {
            return factor;
        }
        factorLocale = locale;
        factor = measureFactor(locale);
        return factor;
    }

    private static float measureFactor(final Locale locale) {
        try {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(text());
            paint.setTextSize(100f);
            final Paint.FontMetrics fm = paint.getFontMetrics();
            final float line = fm.descent - fm.ascent;
            if (line <= 0f) {
                return 1f;
            }
            final Date now = new Date();
            final String sample = new SimpleDateFormat("EEEE MMMM", locale).format(now)
                    + " " + new SimpleDateFormat("EE d.M. HH:mm", locale).format(now);
            final Rect ink = new Rect();
            paint.getTextBounds(sample, 0, sample.length(), ink);
            // a tenth of slack on top of what the sample needs: the sample is dates, and a
            // title in the same language may stack one mark higher still
            return Math.max(1f, Math.min(2f, ink.height() * 1.15f / line));
        } catch (final Throwable ignored) {
            return 1f; // no font stack around, which happens in plain unit tests
        }
    }
}
