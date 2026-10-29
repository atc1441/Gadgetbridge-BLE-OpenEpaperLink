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

import androidx.annotation.Nullable;

/**
 * One measured thing, in the form the dashboard cards draw: a symbol, a short label and a
 * value that was already formatted by whoever knows what it means.
 * <p>
 * Three cards share this - the tile grid, the big value with its badges and the row list - and
 * they all take the same list in the same order. That is the point of it: what a display shows
 * is decided once, by {@link OeplReadings}, and choosing a different layout for it never
 * changes which values appear or what they are called.
 */
public class OeplReading {

    /** The heading this value belongs under, already translated, or null outside any. */
    @Nullable
    public final String section;
    /** What the value is, two or three words at most - a tile has no room for a sentence. */
    public final String label;
    /** The value as it goes onto the panel, unit included. */
    public final String value;

    /**
     * Which of {@link OeplReadings}' four groups this came from: the key the setting stores,
     * not the heading drawn above it.
     * <p>
     * A card that draws one of those groups itself has to be able to take that group out of
     * the list it is given - the wall sheet has a week grid on it, the big value card has the
     * agenda beside it - and the heading cannot answer that question, because the heading is
     * translated and a card is not the place to compare translated strings.
     */
    @Nullable
    public String group;

    /** A glyph from the Weather Icons face, for the things that face has a symbol for. */
    @Nullable
    public String fontGlyph;
    /** One of our own shapes, for everything else. */
    @Nullable
    public OeplGlyphs.Glyph shape;
    /** 0 to 100 when this value is a share of something, so a bar can be drawn for it. */
    public int percent = -1;
    /** True for a value that should catch the eye: freezing, nearly empty, overdue. */
    public boolean accent;
    /**
     * The temperature behind this value, when it is one. A temperature cannot be formatted
     * when it is collected: whether the panel shows celsius or fahrenheit belongs to the card
     * style, which only exists once drawing starts. So the degrees are carried along and the
     * card asks for the text it should draw.
     */
    @Nullable
    public Float celsius;
    /** Whether that temperature is drawn with one decimal, as a current reading is. */
    public boolean fineTemperature;
    /** The warm end, for a value that is a range of two temperatures. */
    @Nullable
    public Float celsiusHigh;

    public OeplReading(@Nullable final String section, final String label, final String value) {
        this.section = section;
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
    }

    /** Marks which group this was collected as part of; see {@link #group}. */
    public OeplReading inGroup(@Nullable final String name) {
        this.group = name;
        return this;
    }

    public OeplReading withFontGlyph(final String glyph) {
        this.fontGlyph = glyph;
        return this;
    }

    public OeplReading withShape(final OeplGlyphs.Glyph glyph) {
        this.shape = glyph;
        return this;
    }

    public OeplReading withPercent(final int value) {
        this.percent = value;
        return this;
    }

    public OeplReading accented(final boolean yes) {
        this.accent = yes;
        return this;
    }

    public OeplReading withTemperature(final float degreesCelsius, final boolean fine) {
        this.celsius = degreesCelsius;
        this.fineTemperature = fine;
        return this;
    }

    /** A range of two temperatures, as the day's low and high are shown. */
    public OeplReading withRange(final float low, final float high) {
        this.celsius = low;
        this.celsiusHigh = high;
        return this;
    }

    /** The value as this card should draw it, with the unit the style asks for. */
    public String display(final boolean fahrenheit) {
        if (celsius == null) {
            return value;
        }
        if (celsiusHigh != null) {
            return degrees(celsius, fahrenheit) + "/" + degrees(celsiusHigh, fahrenheit) + "°";
        }
        return degrees(celsius, fahrenheit) + "°";
    }

    private String degrees(final float celsiusValue, final boolean fahrenheit) {
        final double value = fahrenheit ? celsiusValue * 9.0 / 5.0 + 32.0 : celsiusValue;
        return fineTemperature
                ? String.format(java.util.Locale.ROOT, "%.1f", value)
                : String.valueOf(Math.round(value));
    }

    /** Draws whichever symbol this reading carries, centred on a point. */
    public void drawIcon(final OeplCanvas c, final float cx, final float cy, final float size,
                         final int color) {
        if (fontGlyph != null && OeplFonts.hasIcons()) {
            c.iconCentered(fontGlyph, cx, cy, size, color);
        } else if (shape != null) {
            OeplGlyphs.draw(c, shape, cx, cy, size, color, percent);
        }
    }

    /**
     * How wide the symbol is at a given height. A font glyph and a shape are both far from
     * square, and a layout that reserves the height for either has the value beside it
     * overlapping on the panels where the symbol happens to be wide.
     */
    public float iconWidth(final OeplCanvas c, final float size) {
        if (fontGlyph != null && OeplFonts.hasIcons()) {
            return c.iconWidth(fontGlyph, size);
        }
        if (shape != null) {
            return OeplGlyphs.width(shape, size);
        }
        return 0f;
    }

    public boolean hasIcon() {
        return (fontGlyph != null && OeplFonts.hasIcons()) || shape != null;
    }
}
