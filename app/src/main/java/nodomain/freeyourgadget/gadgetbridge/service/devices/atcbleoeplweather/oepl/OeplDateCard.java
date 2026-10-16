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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Today's date, the way OpenEPaperLink shows it: the weekday across the top in the accent
 * colour and the day and month under it. When sunrise and sunset are known the card switches to
 * the variant that adds them along the bottom, together with the phase of the moon.
 */
public class OeplDateCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;
    private final boolean showSun;

    public OeplDateCard(@Nullable final OeplWeather weather, final boolean showSun) {
        super(true);
        this.weather = weather;
        this.showSun = showSun;
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final Date today = new Date();
        final boolean sun = showSun && weather != null && weather.sunrise > 0 && weather.sunset > 0
                && h >= 96;

        final String weekday = new SimpleDateFormat("EEEE", Locale.getDefault()).format(today);
        final String date = new SimpleDateFormat("d. MMMM", Locale.getDefault()).format(today);

        // with the sun times along the bottom the two lines share the upper two thirds
        final float band = sun ? h * 0.62f : h;
        c.textFilling(weekday, w / 2f, band * 0.27f, w * 0.96f, band * 0.42f, c.accent);
        c.textFilling(date, w / 2f, band * 0.74f, w * 0.96f, band * 0.36f, c.ink);

        if (!sun) {
            return;
        }
        final float pad = Math.max(2f, w * 0.02f);
        final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
        final String rise = clock.format(new Date(weather.sunrise * 1000L));
        final String set = clock.format(new Date(weather.sunset * 1000L));
        final float gap = Math.max(2f, w * 0.012f);

        // Both times, their pictograms and the gaps between them have to share one line, so the
        // size comes from what actually fits rather than from the panel height alone: on a
        // narrow tag the two used to be drawn straight through each other. The pictograms are
        // measured as well - an icon face is nowhere near square, so the room one needs cannot
        // be taken from its height.
        final String riseGlyph = String.valueOf(OeplIcons.SUNRISE);
        final String setGlyph = String.valueOf(OeplIcons.SUNSET);
        float size = h * 0.19f;
        for (int guard = 0; guard < 48; guard++) {
            final float need = c.iconWidth(riseGlyph, size * 0.8f)
                    + c.iconWidth(setGlyph, size * 0.8f)
                    + c.measure(rise, size) + c.measure(set, size) + gap * 4f;
            if (need <= w - pad * 2 || size <= 7f) {
                break;
            }
            size *= 0.94f;
        }
        final float y = h - c.lineHeight(size) - Math.max(1f, h * 0.015f);
        final float middle = y + c.lineHeight(size) * 0.45f;
        final float riseIcon = c.iconWidth(riseGlyph, size * 0.8f);
        final float setIcon = c.iconWidth(setGlyph, size * 0.8f);

        c.iconCentered(riseGlyph, pad + riseIcon / 2f, middle, size * 0.8f, c.ink);
        final float riseLeft = pad + riseIcon + gap;
        c.text(rise, riseLeft, y, size, OeplCanvas.LEFT, c.ink);

        final float setRight = w - pad;
        final float setLeft = setRight - c.measure(set, size);
        c.iconCentered(setGlyph, setLeft - gap - setIcon / 2f, middle, size * 0.8f, c.ink);
        c.text(set, setRight, y, size, OeplCanvas.RIGHT, c.ink);

        // the moon only gets a place when one is left between the two times
        final float freeFrom = riseLeft + c.measure(rise, size);
        final float freeTo = setLeft - gap - setIcon;
        final float moon = c.iconWidth(OeplIcons.moonPhase(
                OeplIcons.moonAge(weather.moonPhase)), size * 0.95f);
        if (weather.moonPhase >= 0 && freeTo - freeFrom > moon + gap * 2f) {
            c.iconCentered(OeplIcons.moonPhase(OeplIcons.moonAge(weather.moonPhase)),
                    (freeFrom + freeTo) / 2f, middle, size * 0.95f, c.ink);
        }
    }
}
