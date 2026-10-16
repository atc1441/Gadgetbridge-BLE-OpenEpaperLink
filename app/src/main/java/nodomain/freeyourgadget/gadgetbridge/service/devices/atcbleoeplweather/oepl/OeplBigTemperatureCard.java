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
 * The temperature, as large as the panel can hold it, and almost nothing else: the place and the
 * sky above it, the day's range and the wind below. A tag on a kitchen wall is read in passing
 * from three metres away, and at that distance one large figure says more than a full card.
 * <p>
 * The three bands are measured against each other rather than placed at fractions of the
 * height: the number takes whatever the two lines leave over, and on a tag too small for them
 * it takes the whole panel.
 */
public class OeplBigTemperatureCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;

    public OeplBigTemperatureCard(@Nullable final OeplWeather weather, final boolean beaufort) {
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
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.025f);
        final OeplWeather.Current now = weather.current;

        // the head carries the place and the sky, the foot the range and the wind; both only
        // appear while a line of them is small enough to leave the figure the larger half
        final float headSize = c.sizeForHeight(Math.min(h * 0.15f, w * 0.1f), h * 0.22f, 7f);
        final float footSize = c.sizeForHeight(Math.min(h * 0.13f, w * 0.09f), h * 0.2f, 7f);
        final boolean head = headSize > 0f && !weather.location.isEmpty();
        final boolean foot = footSize > 0f && !weather.upcomingDays().isEmpty() && h >= 70f;

        float top = 0f;
        float bottom = h;

        if (head) {
            final String sky = OeplIcons.weather(now.wmoCode, !now.day);
            final float iconH = headSize * 1.1f;
            final float iconW = c.iconWidth(sky, iconH);
            c.iconCentered(sky, w - pad - iconW / 2f, c.lineHeight(headSize) * 0.5f, iconH,
                    inkOrAccent(c, OeplIcons.isSevere(now.wmoCode)));
            c.textFitted(weather.location, pad, 0f, headSize, OeplCanvas.LEFT, c.ink,
                    w - pad * 2f - iconW - pad);
            top = Math.max(c.lineHeight(headSize), iconH) * 1.02f;
        }

        if (foot) {
            final OeplWeather.Day today = weather.upcomingDays().get(0);
            final float line = c.lineHeight(footSize);
            bottom = h - line;
            final String range = temp(today.minTemperature) + " / "
                    + temp(today.maxTemperature) + "°";
            // the wind reads right to left - value against the edge, its arrow in front of it,
            // and the range gets whatever is left of the line
            final String windValue = wind(now.windSpeed);
            final String arrow = OeplIcons.windDirection(now.windDirection);
            final float arrowW = c.iconWidth(arrow, footSize * 0.9f);
            final float gap = Math.max(2f, w * 0.02f);
            final float arrowCx = w - pad - c.measure(windValue, footSize) - gap - arrowW / 2f;
            c.text(windValue, w - pad, bottom, footSize, OeplCanvas.RIGHT,
                    inkOrAccent(c, windy(now.windSpeed)));
            c.iconCentered(arrow, arrowCx, bottom + line * 0.5f, footSize * 0.9f, c.ink);
            c.textClipped(range, pad, bottom, footSize, OeplCanvas.LEFT, c.ink,
                    arrowCx - arrowW / 2f - gap - pad);
        }

        final String reading = tempFine(now.temperature) + "°";
        c.textFilling(reading, w / 2f, (top + bottom) / 2f, w - pad * 2f,
                (bottom - top) * 0.94f, inkOrAccent(c, now.temperature < 0f));
    }
}
