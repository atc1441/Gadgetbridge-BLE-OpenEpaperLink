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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.WeatherIcons;

/**
 * The next hours as a temperature curve. A line reads far better than a row of numbers on a
 * display you glance at from across the room.
 */
public class HourlyWeatherContent implements CardContent {
    @Nullable
    private final WeatherSpec spec;
    private final int hours;

    /** Shown in the title bar when the forecast does not name a place. */
    private final String heading;

    public HourlyWeatherContent(@Nullable final WeatherSpec spec, final int hours,
                                final String heading) {
        this.spec = spec;
        this.hours = hours;
        this.heading = heading;
    }

    private static float clamp(final float v, final float lo, final float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    @Nullable
    @Override
    public String title() {
        if (spec == null) {
            return null;
        }
        final String location = spec.getLocation();
        return location == null || location.isEmpty() ? heading : location;
    }

    @Override
    public boolean hasData() {
        return spec != null && spec.getHourly() != null && spec.getHourly().size() >= 2;
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (!hasData()) {
            return;
        }
        final List<WeatherSpec.Hourly> all = spec.getHourly();
        final List<WeatherSpec.Hourly> points = new ArrayList<>(
                all.subList(0, Math.min(hours, all.size())));
        final int n = points.size();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (final WeatherSpec.Hourly h : points) {
            min = Math.min(min, h.getTemp());
            max = Math.max(max, h.getTemp());
        }
        if (max == min) {
            max = min + 1; // a flat curve still needs a range to scale against
        }

        final float pad = Math.max(2f, area.width() * 0.03f);
        final float labelH = Math.max(10f, Math.min(area.height() * 0.17f, area.width() * 0.09f));
        // the sky pictograms are as wide as they are tall and then some, so their height
        // comes from the width as well: a fifth of a tall panel is wider than a narrow one is
        final float iconH = area.height() > 90
                ? Math.max(12f, Math.min(area.height() * 0.22f, area.width() * 0.38f)) : 0f;
        // the curve is drawn with a thick round stroke, so the plot has to keep half of it
        // clear of the edges or the first and last point bleed over the panel border
        // sized by the width as well as by the height: on a panel that is tall for its
        // width the height alone asks for a fourteen pixel stroke, and the dots on it then
        // cover the hour written underneath them
        final float stroke = Math.max(1.5f, Math.min(area.height() * 0.022f,
                area.width() * 0.03f));
        // and the row of hours stays clear of the dots, not merely of the curve
        final RectF plot = new RectF(area.left + pad + stroke, area.top + labelH * 1.3f + iconH,
                area.right - pad - stroke,
                area.bottom - labelH * 1.1f - stroke * 0.9f);

        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(stroke);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(style.accent);

        final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(style.ink);
        final Paint label = style.paint(style.ink, false, labelH * 0.78f);
        final Paint value = style.paint(style.ink, true, labelH * 0.80f);

        final Path path = new Path();
        final float stepX = n > 1 ? plot.width() / (n - 1) : 0f;
        for (int i = 0; i < n; i++) {
            final float x = plot.left + stepX * i;
            final float t = (points.get(i).getTemp() - min) / (float) (max - min);
            final float y = plot.bottom - t * plot.height();
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, line);

        for (int i = 0; i < n; i++) {
            final float x = plot.left + stepX * i;
            final float t = (points.get(i).getTemp() - min) / (float) (max - min);
            final float y = plot.bottom - t * plot.height();
            canvas.drawCircle(x, y, line.getStrokeWidth() * 0.9f, dot);
        }

        // Three rows of marks along the curve, each thinned out on its own: a reading goes in
        // only when it clears the one before it. Thinning by a guessed label width is what let
        // the readings run into each other as soon as the panel was large enough to make them
        // big, and the rows do not need the same spacing - an hour is far narrower than a
        // reading with its degree sign.
        final float breath = Math.max(3f, area.width() * 0.012f);
        float lastValue = Float.NEGATIVE_INFINITY;
        float lastHour = Float.NEGATIVE_INFINITY;
        float lastIcon = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            final float x = plot.left + stepX * i;
            final float t = (points.get(i).getTemp() - min) / (float) (max - min);
            final float y = plot.bottom - t * plot.height();

            final String temp = style.shortTemperature(points.get(i).getTemp());
            final float tw = value.measureText(temp);
            final float tx = clamp(x - tw / 2f, area.left, area.right - tw);
            if (tx >= lastValue + breath) {
                // the reading sits above its point, but never above the top of the card
                canvas.drawText(temp, tx, Math.max(area.top - value.getFontMetrics().ascent,
                        y - line.getStrokeWidth() * 2.2f), value);
                lastValue = tx + tw;
            }

            // The row counts in hours, and once the numbers run past twelve it reads as a
            // date instead - "15, 18, 21" looks like three days. Midnight therefore carries
            // the name of the day that starts there.
            final Date at = new Date(points.get(i).getTimestamp() * 1000L);
            final java.util.Calendar when = java.util.Calendar.getInstance();
            when.setTime(at);
            final String hour = when.get(java.util.Calendar.HOUR_OF_DAY) == 0
                    ? new SimpleDateFormat("EE", Locale.getDefault()).format(at)
                    : new SimpleDateFormat("HH", Locale.getDefault()).format(at);
            final float hw = label.measureText(hour);
            final float hx = clamp(x - hw / 2f, area.left, area.right - hw);
            if (hx >= lastHour + breath) {
                canvas.drawText(hour, hx, style.baselineAbove(area.bottom, label), label);
                lastHour = hx + hw;
            }

            if (iconH > 0 && x - iconH * 0.5f >= lastIcon + breath) {
                // the first and the last pictogram are wider than the padding beside the plot,
                // so they have to be pulled back inside the card
                final float ix = clamp(x, area.left + iconH * 0.5f, area.right - iconH * 0.5f);
                WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(
                                points.get(i).getConditionCode()), false,
                        ix, area.top + labelH * 0.4f + iconH * 0.5f, iconH, style.ink,
                        style.accent);
                lastIcon = ix + iconH * 0.5f;
            }
        }
    }
}
