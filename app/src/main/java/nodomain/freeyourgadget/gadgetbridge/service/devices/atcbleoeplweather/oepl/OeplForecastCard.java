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
        this(weather, days, beaufort, true);
    }

    /**
     * @param headline whether the place and what it is doing now go across the top. A sheet
     *                 that has already said both directly above this card asks for them to be
     *                 left out, and the days get the room instead.
     */
    public OeplForecastCard(@Nullable final OeplWeather weather, final int days,
                            final boolean beaufort, final boolean headline) {
        super(beaufort);
        this.weather = weather;
        this.requestedDays = days;
        this.headline = headline;
    }

    private final boolean headline;

    @Override
    public boolean hasData() {
        return weather != null && !weather.upcomingDays().isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (weather == null || weather.upcomingDays().isEmpty()) {
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

        // The place on the left, what it is doing right now on the right. A forecast
        // without the present is a card that answers the second question and not the first:
        // somebody standing in front of it wants to know what to put on now, and then what
        // the week holds.
        final float headSize = headline ? h * 0.125f : 0f;
        if (headline) {
            c.rowEnds(weather.location, now(), h * 0.005f, headSize, c.ink,
                    inkOrAccent(c, weather.current.temperature < 0f), pad, w - pad, pad * 2f);
        }

        // From today on: a forecast held over from yesterday would otherwise put a day that
        // is over into the first column, where the reader takes it for today.
        final List<OeplWeather.Day> forecast = weather.upcomingDays();
        // How many columns fit is decided by what a column has to hold - two temperatures
        // beside each other, at the smallest size this panel still shows - rather than by a
        // fraction of the card's height. The height rule cost a large panel most of its week:
        // on a 800x480 poster it asked 172 pixels per column and so drew four days where
        // seven would have been readable, which is most of the reason to hang a large panel.
        final float smallest = 9f;
        // What a column has to hold, measured from the figures themselves rather than from a
        // sample of them. In Fahrenheit a day reads "100 111" where in Celsius it reads
        // "-9 44", and a column sized around the Celsius shape had one day's pair written
        // into the next day's.
        String widest = "-9 99";
        for (final OeplWeather.Day day : forecast) {
            final String pair = temp(day.minTemperature) + " " + temp(day.maxTemperature);
            if (c.measure(pair, smallest) > c.measure(widest, smallest)) {
                widest = pair;
            }
        }
        final float minColumn = Math.max(c.measure(widest, smallest) + 6f, smallest / 0.28f);
        int days = Math.min(requestedDays, forecast.size());
        days = Math.max(1, Math.min(days, (int) (w / Math.max(24f, minColumn))));
        final float column = w / days;
        // under the headline rather than at a fixed fraction of the card: a place name set in
        // a script that stacks its marks takes a taller line than the metrics promise
        final float top = headline
                ? Math.max(h * 0.16f, h * 0.005f + c.lineHeight(headSize)) : h * 0.02f;

        // and the size the pair is set in is the one at which it fits that column
        float tempSize = Math.min(h * 0.15f, column * 0.28f);
        int tempGuard = 0;
        while (tempSize > smallest && tempGuard++ < 64
                && c.measure(widest, tempSize) > column - 4f) {
            tempSize = Math.max(smallest, tempSize * 0.95f);
        }
        final float tempTop = h - c.lineHeight(tempSize);

        // read in the place's own time: a forecast for somewhere two time zones away is
        // about that place's days, not about the phone's
        final SimpleDateFormat weekday = weather.format("EE");

        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = forecast.get(i);
            final float centre = column * i + column / 2f;

            // today says so, and in the accent colour: which column one is standing in is the
            // first thing read off a forecast and the easiest to get wrong
            // the day's name is set by the column as much as by the height: a poster is tall
            // enough for seventy pixel type and no column is seventy pixels wide, so sizing it
            // off the height alone clipped every name to its first letter
            final float labelSize = Math.min(h * 0.15f, column * 0.34f);
            final boolean today = weather.isToday(day);
            c.textClipped(dayLabel(c, weather, day, weekday, labelSize, column - 2), centre, top,
                    labelSize, OeplCanvas.CENTER, today ? c.accent : c.ink, column - 2);

            // below the weekday rather than at a fixed fraction of the panel: a script that
            // stacks its marks - Burmese, Malayalam - writes a line half as tall again as a
            // Latin one, and the wind was then written through the name of the day
            final float windY = Math.max(top + h * 0.175f, top + c.lineHeight(labelSize));
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
            final float rainSize = Math.min(h * 0.105f, column * 0.2f);
            final float windBottom = windY + c.lineHeight(windSize);
            // seven pixels or no rain figure at all: under that the panel's quantiser
            // leaves a grey mark where the millimetres were, and the column reads as dirty
            // rather than as wet
            final boolean rain = day.precipitation > 0.05f && rainSize >= 7f
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
    /** The reading of the moment, which is what the header carries beside the place. */
    private String now() {
        return weather.hasData() ? tempFine(weather.current.temperature) + "°" : null;
    }

    private void rows(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.03f);

        final float headSize = Math.min(h * 0.07f, w * 0.16f);
        if (headline) {
            c.rowEnds(weather.location, now(), 0, headSize, c.ink,
                    inkOrAccent(c, weather.current.temperature < 0f), pad, w - pad, pad * 2f);
        }
        // the headline's own line rather than a multiple of its size: floored up on a small
        // tag, it is taller than the card assumed and the first sky symbol sat in it
        final float top = headline ? c.lineHeight(headSize) * 1.05f : pad;

        final List<OeplWeather.Day> forecast = weather.upcomingDays();
        // as with the columns: a row thinner than this holds nothing anybody reads, so a tag
        // too short for the whole week shows the first days of it instead
        final int fits = Math.max(1, (int) ((h - top) / 14f));
        final int days = Math.max(1, Math.min(Math.min(requestedDays, forecast.size()), fits));
        final float row = (h - top) / days;
        final float textSize = Math.min(row * 0.46f, w * 0.17f);
        final SimpleDateFormat weekday = weather.format("EE");

        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = forecast.get(i);
            final float y = top + i * row;
            final float middle = y + row / 2f;

            // the millimetres go under the weekday at two thirds its size, and are left out
            // rather than set under seven pixels, where the panel shows a smudge
            final float rainSize = Math.max(OeplCanvas.READABLE, textSize * 0.62f);
            final boolean withRain = day.precipitation > 0.05f && row > textSize * 1.9f
                    && textSize * 0.62f >= 7f;
            final float firstLine = middle - textSize * (withRain ? 0.95f : 0.55f);
            // The room left of the pictogram, measured rather than assumed. An icon glyph is
            // nowhere near square - the same reason the wind arrow above is placed from its
            // measured width - so a wide sky symbol reaches further left than its height
            // suggests, and a quarter of the panel for the weekday had the two touching.
            final String sky = OeplIcons.weather(day.wmoCode, false);
            final float skyHeight = Math.min(row * 0.70f, w * 0.24f);
            final float skyWidth = c.iconWidth(sky, skyHeight);
            final float labelRoom = Math.max(w * 0.12f,
                    w * 0.45f - skyWidth / 2f - pad * 1.5f);
            c.textFitted(dayLabel(c, weather, day, weekday, textSize, labelRoom), pad,
                    firstLine, textSize, OeplCanvas.LEFT,
                    weather.isToday(day) ? c.accent : c.ink, labelRoom);
            c.iconCentered(sky, w * 0.45f, middle, skyHeight,
                    inkOrAccent(c, OeplIcons.isSevere(day.wmoCode)));

            final String range = temp(day.minTemperature) + " " + temp(day.maxTemperature);
            c.textFitted(range, w - pad, middle - textSize * 0.55f, textSize, OeplCanvas.RIGHT,
                    inkOrAccent(c, day.minTemperature < 0), w * 0.34f);

            if (withRain) {
                c.textFitted(Math.round(day.precipitation) + "mm", pad,
                        middle + textSize * 0.2f, rainSize, OeplCanvas.LEFT,
                        inkOrAccent(c, day.precipitation > 10f), labelRoom);
            }
            if (i > 0) {
                for (float x = 0; x < w; x += 3f) {
                    c.fillRect(Math.round(x), Math.round(y), 1f, 1f, c.ink);
                }
            }
        }
    }
}
