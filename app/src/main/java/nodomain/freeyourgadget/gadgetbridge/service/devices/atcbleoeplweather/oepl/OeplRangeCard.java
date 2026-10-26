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
import java.util.List;
import java.util.Locale;

/**
 * The week as a row of bars: one line per day, each with a bar reaching from that day's low to
 * its high on a scale shared by the whole week. A column of figures has to be read and compared;
 * a bar chart is compared by looking at it, which is what a wall display is for.
 * <p>
 * The weekday column and the two figure columns are measured from the widest entry in the whole
 * week, so every bar starts and ends on the same two lines and the chart can be read across.
 */
public class OeplRangeCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;
    private final int requestedDays;

    public OeplRangeCard(@Nullable final OeplWeather weather, final int days) {
        super(true);
        this.weather = weather;
        this.requestedDays = Math.max(1, days);
    }

    @Override
    public boolean hasData() {
        return weather != null && !weather.upcomingDays().isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (weather == null || weather.upcomingDays().isEmpty()) {
            return;
        }
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.025f);

        final float headSize = c.sizeForHeight(Math.min(h * 0.14f, w * 0.085f), h * 0.2f, OeplCanvas.READABLE);
        final boolean head = headSize > 0f && !weather.location.isEmpty() && h >= 72f;
        final float top = head ? c.lineHeight(headSize) * 1.05f : Math.max(1f, h * 0.02f);
        if (head) {
            // the place and the reading of the moment, as on the forecast card
            c.rowEnds(weather.location, tempFine(weather.current.temperature) + "°", 0f,
                    headSize, c.ink, inkOrAccent(c, weather.current.temperature < 0f), pad,
                    w - pad, pad * 2f);
        }

        // a row has to stay thick enough for a readable figure, so the number of days follows
        // from the room rather than from the setting
        // from today on, never from a day that is already over
        final List<OeplWeather.Day> forecast = weather.upcomingDays();
        final int days = Math.max(1, Math.min(Math.min(requestedDays, forecast.size()),
                (int) ((h - top) / 11f)));
        final float row = (h - top) / days;
        final float textSize = c.sizeForHeight(Math.min(row * 0.72f, w * 0.14f), row * 0.94f,
                12);
        if (textSize <= 0f) {
            return;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float dayColumn = 0f;
        float figureColumn = 0f;
        final SimpleDateFormat weekday = weather.format("EE");
        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = forecast.get(i);
            min = Math.min(min, day.minTemperature);
            max = Math.max(max, day.maxTemperature);
            dayColumn = Math.max(dayColumn,
                    c.measure(dayLabel(c, weather, day, weekday, textSize, 0f), textSize));
            figureColumn = Math.max(figureColumn, Math.max(
                    c.measure(temp(day.minTemperature) + "°", textSize),
                    c.measure(temp(day.maxTemperature) + "°", textSize)));
        }
        if (max - min < 1f) {
            max = min + 1f; // a week of the same temperature still needs a scale
        }

        final float gap = Math.max(2f, w * 0.015f);
        final float barLeft = pad + dayColumn + gap + figureColumn + gap;
        final float barRight = w - pad - figureColumn - gap;
        final float thickness = Math.max(2f, Math.min(row * 0.34f, h * 0.06f));
        // A day covers a fraction of the week's range, so a bar is a fraction of the room
        // between the two figure columns. Below this the difference between a mild day and a
        // cold one is a few pixels, which says less than the figures do on their own - so a
        // narrow tag gets the figures and no chart at all.
        if (barRight - barLeft < Math.max(w * 0.3f, 80f)) {
            rowsWithoutBars(c, forecast, days, top, row, textSize, pad, gap, weekday);
            return;
        }

        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = forecast.get(i);
            final float middle = top + row * (i + 0.5f);
            final float textTop = middle - c.lineHeight(textSize) / 2f;

            c.text(dayLabel(c, weather, day, weekday, textSize, 0f), pad, textTop, textSize,
                    OeplCanvas.LEFT, weather.isToday(day) ? c.accent : c.ink);

            final float x0 = barLeft + (day.minTemperature - min) / (max - min)
                    * (barRight - barLeft);
            final float x1 = barLeft + (day.maxTemperature - min) / (max - min)
                    * (barRight - barLeft);
            c.fillRect(x0, middle - thickness / 2f, Math.max(2f, x1 - x0), thickness,
                    inkOrAccent(c, day.maxTemperature >= max - 0.01f));

            c.text(temp(day.minTemperature) + "°", barLeft - gap, textTop, textSize,
                    OeplCanvas.RIGHT, inkOrAccent(c, day.minTemperature < 0f));
            c.text(temp(day.maxTemperature) + "°", barRight + gap, textTop, textSize,
                    OeplCanvas.LEFT, c.ink);
        }
    }

    /** The same week on a tag too narrow for a chart: weekday, low and high, nothing drawn. */
    private void rowsWithoutBars(final OeplCanvas c, final List<OeplWeather.Day> forecast,
                                 final int days, final float top, final float row,
                                 final float textSize, final float pad, final float gap,
                                 final SimpleDateFormat weekday) {
        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = forecast.get(i);
            final float textTop = top + row * (i + 0.5f) - c.lineHeight(textSize) / 2f;
            c.rowEnds(dayLabel(c, weather, day, weekday, textSize, c.width * 0.3f),
                    temp(day.minTemperature) + " / " + temp(day.maxTemperature) + "°",
                    textTop, textSize, weather.isToday(day) ? c.accent : c.ink, c.ink, pad,
                    c.width - pad, gap);
        }
    }
}
