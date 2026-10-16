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
 * The multi day forecast, in the column layout OpenEPaperLink uses: the place across the top,
 * then one column per day holding the weekday, the wind as an arrow and a Beaufort number, the
 * sky as a pictogram, the rain total when there is any, and the day's low and high at the
 * bottom. Dotted rules separate the columns.
 * <p>
 * A tall panel has no room for columns, so there the days become rows instead, with the same
 * pieces in the same order from left to right.
 */
public class OeplForecastCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;
    private final int requestedDays;

    public OeplForecastCard(@Nullable final OeplWeather weather, final int days,
                            final boolean beaufort) {
        super(beaufort);
        this.weather = weather;
        this.requestedDays = days;
    }

    @Override
    public boolean hasData() {
        return weather != null && !weather.days.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (weather == null || weather.days.isEmpty()) {
            return;
        }
        if (c.height > c.width * 1.1f) {
            rows(c);
        } else {
            columns(c);
        }
    }

    private void columns(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.017f);

        final float headSize = h * 0.125f;
        c.textClipped(weather.location, pad, h * 0.005f, headSize, OeplCanvas.LEFT, c.ink,
                w - pad * 2);

        // a column needs room for two temperatures side by side, so cap the count by the width
        final float minColumn = h * 0.36f;
        int days = Math.min(requestedDays, weather.days.size());
        days = Math.max(1, Math.min(days, (int) (w / Math.max(24f, minColumn))));
        final float column = w / days;
        final float top = h * 0.16f;

        final SimpleDateFormat weekday = new SimpleDateFormat("EE", Locale.getDefault());

        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = weather.days.get(i);
            final float centre = column * i + column / 2f;

            c.textClipped(weekday.format(new Date(day.epochSeconds * 1000L)), centre, top,
                    h * 0.15f, OeplCanvas.CENTER, c.ink, column - 2);

            final float windY = top + h * 0.175f;
            final String windValue = wind(day.windSpeed);
            // Arrow and number are placed from their measured widths. The arrow is an icon
            // glyph, and those are nowhere near square, so the space it needs has to be asked
            // for rather than assumed from its height - a wide arrow was drawn over the wind.
            final float windSize = Math.min(h * 0.13f, column * 0.3f);
            final String arrowGlyph = OeplIcons.windDirection(day.windDirection);
            final float arrowH = Math.min(h * 0.11f, column * 0.26f);
            final float arrowW = c.iconWidth(arrowGlyph, arrowH);
            final float numberWidth = c.measure(windValue, windSize);
            final float gap = arrowW > 0f ? Math.max(2f, column * 0.03f) : 0f;
            final float block = arrowW + gap + numberWidth;
            c.iconCentered(arrowGlyph, centre - block / 2f + arrowW / 2f,
                    windY + c.lineHeight(windSize) * 0.45f, arrowH, c.ink);
            c.text(windValue, centre - block / 2f + arrowW + gap, windY, windSize,
                    OeplCanvas.LEFT, inkOrAccent(c, OeplIcons.beaufort(day.windSpeed) > 5));

            // The bottom of the column is stacked from the bottom edge up: the two temperatures
            // sit on the edge, the rain goes above them and only when a line fits there, and
            // the sky pictogram takes whatever is left between the wind and that. Fixed
            // fractions of the height had the rain written through the temperatures on every
            // panel just short of the next size up.
            final float tempSize = Math.min(h * 0.15f, column * 0.28f);
            final float tempTop = h - c.lineHeight(tempSize);
            final float rainSize = Math.min(h * 0.105f, column * 0.2f);
            final float windBottom = windY + c.lineHeight(windSize);
            final boolean rain = day.precipitation > 0.05f
                    && tempTop - c.lineHeight(rainSize) - windBottom > h * 0.18f;
            final float rainTop = tempTop - c.lineHeight(rainSize);
            if (rain) {
                c.textFitted(Math.round(day.precipitation) + "mm", centre, rainTop,
                        rainSize, OeplCanvas.CENTER,
                        inkOrAccent(c, day.precipitation > 10f), column * 0.8f);
            }

            final float skyTop = windBottom;
            final float skyBottom = rain ? rainTop : tempTop;
            if (skyBottom - skyTop > 8f) {
                c.iconCentered(OeplIcons.weather(day.wmoCode, false), centre,
                        (skyTop + skyBottom) / 2f,
                        Math.min(skyBottom - skyTop, Math.min(h * 0.26f, column * 0.62f)),
                        inkOrAccent(c, OeplIcons.isSevere(day.wmoCode)));
            }

            c.text(temp(day.minTemperature) + " ", centre, tempTop, tempSize, OeplCanvas.RIGHT,
                    inkOrAccent(c, day.minTemperature < 0));
            c.text(" " + temp(day.maxTemperature), centre, tempTop, tempSize, OeplCanvas.LEFT,
                    inkOrAccent(c, day.maxTemperature < 0));

            if (i > 0) {
                c.dottedColumn(column * i, top, h, c.ink, 3f);
            }
        }
    }

    /** One day per line: weekday, sky, rain, wind, low and high. */
    private void rows(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.03f);

        final float headSize = Math.min(h * 0.07f, w * 0.16f);
        c.textClipped(weather.location, pad, 0, headSize, OeplCanvas.LEFT, c.ink, w - pad * 2);
        final float top = headSize * 1.25f;

        final int days = Math.max(1, Math.min(requestedDays, weather.days.size()));
        final float row = (h - top) / days;
        final float textSize = Math.min(row * 0.46f, w * 0.17f);
        final SimpleDateFormat weekday = new SimpleDateFormat("EE", Locale.getDefault());

        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = weather.days.get(i);
            final float y = top + i * row;
            final float middle = y + row / 2f;

            final boolean withRain = day.precipitation > 0.05f && row > textSize * 1.9f;
            final float firstLine = middle - textSize * (withRain ? 0.95f : 0.55f);
            c.textFitted(weekday.format(new Date(day.epochSeconds * 1000L)), pad, firstLine,
                    textSize, OeplCanvas.LEFT, c.ink, w * 0.25f);
            c.iconCentered(OeplIcons.weather(day.wmoCode, false), w * 0.45f, middle,
                    Math.min(row * 0.70f, w * 0.24f),
                    inkOrAccent(c, OeplIcons.isSevere(day.wmoCode)));

            final String range = temp(day.minTemperature) + " " + temp(day.maxTemperature);
            c.textFitted(range, w - pad, middle - textSize * 0.55f, textSize, OeplCanvas.RIGHT,
                    inkOrAccent(c, day.minTemperature < 0), w * 0.34f);

            if (withRain) {
                c.textFitted(Math.round(day.precipitation) + "mm", pad,
                        middle + textSize * 0.2f, textSize * 0.62f, OeplCanvas.LEFT,
                        inkOrAccent(c, day.precipitation > 10f), w * 0.25f);
            }
            if (i > 0) {
                for (float x = 0; x < w; x += 3f) {
                    c.fillRect(Math.round(x), Math.round(y), 1f, 1f, c.ink);
                }
            }
        }
    }
}
