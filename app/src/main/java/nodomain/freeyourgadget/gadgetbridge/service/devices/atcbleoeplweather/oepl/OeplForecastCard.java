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
        final boolean showRain = h >= 110;

        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = weather.days.get(i);
            final float centre = column * i + column / 2f;

            c.textClipped(weekday.format(new Date(day.epochSeconds * 1000L)), centre, top,
                    h * 0.15f, OeplCanvas.CENTER, c.ink, column - 2);

            final float windY = top + h * 0.175f;
            final String windValue = wind(day.windSpeed);
            // arrow and number are placed from their measured widths: a wide arrow glyph used
            // to be drawn straight over a two digit wind
            final float windSize = Math.min(h * 0.13f, column * 0.3f);
            final float arrow = Math.min(h * 0.11f, column * 0.26f);
            final float numberWidth = c.measure(windValue, windSize);
            final float block = arrow + numberWidth + 2f;
            c.iconCentered(OeplIcons.windDirection(day.windDirection),
                    centre - block / 2f + arrow / 2f, windY + c.lineHeight(windSize) * 0.45f,
                    arrow, c.ink);
            c.text(windValue, centre - block / 2f + arrow + 2f, windY, windSize,
                    OeplCanvas.LEFT, inkOrAccent(c, OeplIcons.beaufort(day.windSpeed) > 5));

            c.iconCentered(OeplIcons.weather(day.wmoCode, false), centre,
                    h * (showRain ? 0.60f : 0.64f), h * 0.26f,
                    inkOrAccent(c, OeplIcons.isSevere(day.wmoCode)));

            if (showRain && day.precipitation > 0.05f) {
                c.textFitted(Math.round(day.precipitation) + "mm", centre, h * 0.765f,
                        h * 0.105f, OeplCanvas.CENTER,
                        inkOrAccent(c, day.precipitation > 10f), column * 0.8f);
            }

            final float tempY = h - h * 0.165f;
            final float tempSize = Math.min(h * 0.15f, column * 0.28f);
            c.text(temp(day.minTemperature) + " ", centre, tempY, tempSize, OeplCanvas.RIGHT,
                    inkOrAccent(c, day.minTemperature < 0));
            c.text(" " + temp(day.maxTemperature), centre, tempY, tempSize, OeplCanvas.LEFT,
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
