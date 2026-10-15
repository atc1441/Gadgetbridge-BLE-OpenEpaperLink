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

import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;

/**
 * Base for the cards that are laid out the way an OpenEPaperLink access point lays them out:
 * no title bar, no status strip, the whole panel is the card.
 * <p>
 * Every layout is written as a fraction of the panel rather than in pixels. The proportions
 * are those of the OpenEPaperLink reference layout for a 296x128 tag, which is the shape most
 * of its tag types inherit, so the same card holds together on a 152x152 price tag and on a
 * 960x768 panel.
 */
public abstract class OeplCard implements CardContent {

    /** Whether temperatures are shown in fahrenheit, filled in from the card style. */
    protected boolean fahrenheit;
    /** Whether wind is shown as a Beaufort number rather than metres per second. */
    protected final boolean beaufort;

    protected OeplCard(final boolean beaufort) {
        this.beaufort = beaufort;
    }

    @Nullable
    @Override
    public String title() {
        return null;
    }

    @Override
    public boolean fullBleed() {
        return true;
    }

    @Override
    public final void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        fahrenheit = style.fahrenheit;
        final OeplCanvas c = new OeplCanvas(canvas, area.width(), area.height(),
                style.ink, style.paper, style.accent);
        canvas.save();
        canvas.translate(area.left, area.top);
        try {
            paint(c);
        } finally {
            canvas.restore();
        }
    }

    /** Draws the card into a surface whose origin is the top left of the panel. */
    protected abstract void paint(OeplCanvas c);

    /** A temperature as it goes onto the panel, without the degree sign. */
    protected String temp(final float celsius) {
        final double value = fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius;
        return String.valueOf(Math.round(value));
    }

    /** A temperature with one decimal, the way the current reading is shown. */
    protected String tempFine(final float celsius) {
        final double value = fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius;
        return String.format(java.util.Locale.getDefault(), "%.1f", value);
    }

    /** The wind as the configured unit, with the Beaufort scale as the default. */
    protected String wind(final float metresPerSecond) {
        return beaufort
                ? String.valueOf(OeplIcons.beaufort(metresPerSecond))
                : String.valueOf(Math.round(metresPerSecond));
    }

    /** True once the wind is strong enough to deserve the accent colour. */
    protected boolean windy(final float metresPerSecond) {
        return OeplIcons.beaufort(metresPerSecond) > 4;
    }

    protected int inkOrAccent(final OeplCanvas c, final boolean accent) {
        return accent ? c.accent : c.ink;
    }
}
