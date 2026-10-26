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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The rain graph: one bar per hour over a dotted grid, with the hours labelled along the
 * bottom. It is the OpenEPaperLink rain radar card, drawn from the hourly precipitation of the
 * forecast rather than from a service that only covers one country.
 * <p>
 * Bars past half the scale switch to the accent colour, so a glance tells heavy from light.
 */
public class OeplRainCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;
    private final int hours;

    public OeplRainCard(@Nullable final OeplWeather weather, final int hours) {
        super(true);
        this.weather = weather;
        this.hours = Math.max(6, Math.min(48, hours));
    }

    @Override
    public boolean hasData() {
        return weather != null && !upcoming().isEmpty();
    }

    private List<OeplWeather.Hour> upcoming() {
        final List<OeplWeather.Hour> result = new ArrayList<>();
        if (weather == null) {
            return result;
        }
        final long now = System.currentTimeMillis() / 1000L - 1800;
        for (final OeplWeather.Hour hour : weather.hours) {
            if (hour.epochSeconds >= now && hour.precipitation >= 0f) {
                result.add(hour);
            }
            if (result.size() >= hours) {
                break;
            }
        }
        return result;
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final List<OeplWeather.Hour> slots = upcoming();
        if (slots.isEmpty()) {
            return;
        }
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.014f);

        float peak = 0f;
        for (final OeplWeather.Hour hour : slots) {
            peak = Math.max(peak, hour.precipitation);
        }
        final float scale = Math.max(2f, (float) Math.ceil(peak));

        // the place and the scale share one line and are measured against each other
        final float headSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.17f, w * 0.13f));
        c.rowEnds(weather.location, Math.round(scale) + "mm", 0, headSize, c.ink, c.ink,
                pad, w - pad, pad * 2);

        final float labelSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.12f, w * 0.07f));
        final float top = c.lineHeight(headSize) * 1.02f;
        final float bottom = h - c.lineHeight(labelSize);

        // the dotted guides at a quarter, a half and three quarters of the scale
        for (int i = 1; i <= 4; i++) {
            final float y = bottom - (bottom - top) * i / 4f;
            for (float x = 0; x < w; x += 4f) {
                c.fillRect(x, y, 1f, 1f, c.ink);
            }
        }
        c.line(0, bottom, w, bottom, c.ink, 1f);

        final float column = w / slots.size();
        final float barWidth = Math.max(1f, column * 0.66f);
        // the same clock the temperature curve carries, and the same marker for the day
        // boundary: a row of numbers running past twelve reads as a date otherwise
        final SimpleDateFormat clock = weather.format("HH");
        final SimpleDateFormat weekday = weather.format("EE");
        final java.util.Calendar when = java.util.Calendar.getInstance(weather.zone());
        final int labelEvery = Math.max(1, (int) Math.ceil(c.measure("00", labelSize) * 2f / column));

        for (int i = 0; i < slots.size(); i++) {
            final OeplWeather.Hour hour = slots.get(i);
            final float centre = column * i + column / 2f;
            final float fraction = Math.min(1f, hour.precipitation / scale);
            final float barHeight = fraction * (bottom - top);
            if (barHeight >= 1f) {
                c.fillRect(centre - barWidth / 2f, bottom - barHeight, barWidth, barHeight,
                        fraction > 0.5f ? c.accent : c.ink);
            }
            if (i % labelEvery == 0) {
                // the first and the last label would hang over the panel edge if they were
                // simply centred under their column
                final Date at = new Date(hour.epochSeconds * 1000L);
                when.setTime(at);
                final String stamp = when.get(java.util.Calendar.HOUR_OF_DAY) < labelEvery
                        ? weekday.format(at) : clock.format(at);
                c.text(stamp, c.clampCentre(centre, stamp, labelSize), bottom + 1f, labelSize,
                        OeplCanvas.CENTER, c.ink);
            }
        }
    }
}
