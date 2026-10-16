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
 * The current weather, laid out the way OpenEPaperLink lays it out: the place in the top left,
 * the wind in the top right with an arrow for its direction and an umbrella next to it when
 * rain is about, the temperature large across the bottom left and the sky as one big pictogram
 * on the right. Sub-zero temperatures, strong wind and severe weather switch to the accent
 * colour.
 * <p>
 * A tall or square panel cannot hold that arrangement, so it gets the same pieces stacked
 * instead: place, range, pictogram, temperature, wind.
 */
public class OeplWeatherCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;

    public OeplWeatherCard(@Nullable final OeplWeather weather, final boolean beaufort) {
        super(beaufort);
        this.weather = weather;
    }

    @Override
    public boolean hasData() {
        return weather != null && weather.hasData();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (weather == null) {
            return;
        }
        if (c.height > c.width * 0.95f) {
            stacked(c);
        } else {
            wide(c);
        }
    }

    /** The landscape arrangement, which is the one the access point draws on most of its tags. */
    private void wide(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.017f);
        final OeplWeather.Current now = weather.current;

        final float headSize = h * 0.20f;
        final String windValue = wind(now.windSpeed);
        final float windWidth = c.measure(windValue, headSize);
        final float arrowSize = headSize * 0.9f;
        final boolean wet = OeplIcons.isWet(now.wmoCode);
        final float headRoom = windWidth + arrowSize * (wet ? 3.2f : 1.8f);

        c.textClipped(weather.location, pad, h * 0.02f, headSize, OeplCanvas.LEFT, c.ink,
                w - headRoom - pad * 2);

        c.text(windValue, w - pad, h * 0.02f, headSize, OeplCanvas.RIGHT,
                inkOrAccent(c, windy(now.windSpeed)));
        c.iconCentered(OeplIcons.windDirection(now.windDirection),
                w - pad - windWidth - arrowSize * 0.75f, h * 0.02f + headSize * 0.5f,
                arrowSize * 0.8f, c.ink);
        if (wet) {
            c.iconCentered(String.valueOf(OeplIcons.UMBRELLA),
                    w - pad - windWidth - arrowSize * 2.3f, h * 0.02f + headSize * 0.5f,
                    headSize * 0.72f, c.accent);
        }

        // the temperature carries the card, so it gets whatever room is left beside the icon
        final String reading = tempFine(now.temperature) + "°";
        final float tempWidth = Math.min(w * 0.62f, w - h * 0.46f - pad * 2);
        final android.graphics.Paint paint = c.textPaint(h * 0.62f, c.ink);
        c.fit(paint, reading, tempWidth);
        c.textOnBaseline(reading, pad, h - h * 0.04f, paint.getTextSize(), OeplCanvas.LEFT,
                inkOrAccent(c, now.temperature < 0));

        c.iconCentered(OeplIcons.weather(now.wmoCode, !now.day),
                w - pad - h * 0.24f, h * 0.52f, h * 0.44f,
                inkOrAccent(c, OeplIcons.isSevere(now.wmoCode)));

        if (!weather.days.isEmpty() && h >= 96) {
            final OeplWeather.Day today = weather.days.get(0);
            c.textClipped(temp(today.minTemperature) + " / " + temp(today.maxTemperature)
                            + "°", pad, h * 0.235f, h * 0.17f, OeplCanvas.LEFT, c.ink,
                    w * 0.5f);
        }
    }

    /** Everything centred in a column, for a panel that is taller than it is wide. */
    private void stacked(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.03f);
        final OeplWeather.Current now = weather.current;

        final float head = Math.min(h * 0.10f, w * 0.22f);
        c.textFilling(weather.location, w / 2f, head * 0.6f, w - pad * 2, head, c.ink);

        float y = head * 1.25f;
        if (!weather.days.isEmpty()) {
            final OeplWeather.Day today = weather.days.get(0);
            final float range = head * 0.8f;
            c.textFilling(temp(today.minTemperature) + " / " + temp(today.maxTemperature)
                    + "°", w / 2f, y + range * 0.5f, w - pad * 2, range, c.ink);
            y += range * 1.2f;
        }

        // the pictogram takes the middle, the temperature and the wind sit under it
        final float windRow = Math.min(h * 0.12f, w * 0.26f);
        final float tempRow = Math.min(h * 0.22f, w * 0.44f);
        final float iconRoom = h - y - windRow * 1.3f - tempRow * 1.25f;
        // a little air, so that neither the pictogram nor the reading under it can be the one
        // pixel of rounding that makes the two touch
        final float icon = Math.min(iconRoom * 0.92f, w * 0.78f);
        if (icon > 8f) {
            c.iconCentered(OeplIcons.weather(now.wmoCode, !now.day), w / 2f, y + iconRoom / 2f,
                    icon, inkOrAccent(c, OeplIcons.isSevere(now.wmoCode)));
        }
        y += Math.max(0f, iconRoom);

        c.textFilling(tempFine(now.temperature) + "°", w / 2f, y + tempRow * 0.5f,
                w - pad * 2, tempRow, inkOrAccent(c, now.temperature < 0));
        y += tempRow * 1.2f;

        final String windValue = wind(now.windSpeed);
        final boolean wet = OeplIcons.isWet(now.wmoCode);
        final float numberWidth = c.measure(windValue, windRow);
        final float block = windRow * (wet ? 2.1f : 1.1f) + numberWidth;
        float x = (w - block) / 2f;
        if (wet) {
            c.iconCentered(String.valueOf(OeplIcons.UMBRELLA), x + windRow * 0.45f,
                    y + windRow * 0.5f, windRow * 0.85f, c.accent);
            x += windRow;
        }
        c.iconCentered(OeplIcons.windDirection(now.windDirection), x + windRow * 0.45f,
                y + windRow * 0.5f, windRow * 0.8f, c.ink);
        c.text(windValue, x + windRow, y, windRow, OeplCanvas.LEFT,
                inkOrAccent(c, windy(now.windSpeed)));
    }
}
