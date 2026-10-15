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
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.WeatherIcons;

/**
 * The week ahead, with a bar per day spanning its low to its high. Seeing the bars shift is a
 * faster read than comparing numbers.
 */
public class ForecastContent implements CardContent {
    @Nullable
    private final WeatherSpec spec;
    private final int days;

    /** Shown in the title bar when the forecast does not name a place. */
    private final String heading;

    public ForecastContent(@Nullable final WeatherSpec spec, final int days,
                           final String heading) {
        this.spec = spec;
        this.days = days;
        this.heading = heading;
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
        return spec != null && spec.getForecasts() != null && !spec.getForecasts().isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (!hasData()) {
            return;
        }
        final int count = Math.min(days, spec.getForecasts().size());
        if (area.width() >= area.height() * 1.4f) {
            drawColumns(canvas, style, area, count);
        } else {
            drawRows(canvas, style, area, count);
        }
    }

    private void drawColumns(final Canvas canvas, final CardStyle style, final RectF area,
                             final int count) {
        final float colW = area.width() / count;
        final Paint day = style.paint(style.ink, true, Math.min(area.height() * 0.15f, colW * 0.34f));
        final Paint hi = style.paint(style.accent, true, day.getTextSize());
        final Paint lo = style.paint(style.ink, false, day.getTextSize() * 0.92f);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            min = Math.min(min, spec.getForecasts().get(i).getMinTemp());
            max = Math.max(max, spec.getForecasts().get(i).getMaxTemp());
        }
        if (max == min) {
            max = min + 1;
        }

        // the bar sits between the icon and the figures, never under them
        final float barTop = area.top + area.height() * 0.50f;
        final float barBottom = area.bottom - area.height() * 0.26f;
        final float barW = Math.max(3f, Math.min(colW * 0.14f, area.height() * 0.06f));
        final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(style.ink);
        bar.setStrokeCap(Paint.Cap.ROUND);
        bar.setStrokeWidth(barW);

        for (int i = 0; i < count; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            final float cx = area.left + colW * (i + 0.5f);

            final String name = dayName(i + 1);
            canvas.drawText(name, cx - day.measureText(name) / 2f,
                    area.top + area.height() * 0.15f, day);

            WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(d.getConditionCode()), false,
                    cx, area.top + area.height() * 0.33f,
                    Math.min(area.height() * 0.20f, colW * 0.46f), style.ink, style.accent);

            final float yHigh = barBottom - (d.getMaxTemp() - min) / (float) (max - min)
                    * (barBottom - barTop);
            final float yLow = barBottom - (d.getMinTemp() - min) / (float) (max - min)
                    * (barBottom - barTop);
            canvas.drawLine(cx, yHigh, cx, yLow, bar);

            // both figures go side by side under the bar, where nothing can overlap them
            final String h = style.shortTemperature(d.getMaxTemp());
            final String l = style.shortTemperature(d.getMinTemp());
            final float gap = colW * 0.08f;
            final float total = hi.measureText(h) + gap + lo.measureText(l);
            // the pair under the first and the last column has to stay on the card even when it
            // is wider than the column it belongs to
            final float x = Math.max(area.left,
                    Math.min(area.right - total, cx - total / 2f));
            final float y = Math.min(area.bottom - area.height() * 0.04f,
                    style.baselineAbove(area.bottom, lo));
            canvas.drawText(h, x, y, hi);
            canvas.drawText(l, x + hi.measureText(h) + gap, y, lo);
        }
    }

    private void drawRows(final Canvas canvas, final CardStyle style, final RectF area,
                          final int count) {
        final float pad = Math.max(2f, area.width() * 0.03f);
        final float rowH = area.height() / count;
        final Paint day = style.paint(style.ink, true, Math.min(rowH * 0.46f, area.width() * 0.16f));
        final Paint hi = style.paint(style.accent, true, rowH * 0.42f);
        final Paint lo = style.paint(style.ink, false, rowH * 0.42f);

        for (int i = 0; i < count; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            final float base = area.top + rowH * (i + 0.68f);
            canvas.drawText(dayName(i + 1), area.left + pad, base, day);

            WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(d.getConditionCode()), false,
                    area.left + area.width() * 0.45f, base - rowH * 0.22f,
                    Math.min(rowH * 0.66f, area.width() * 0.2f), style.ink, style.accent);

            final String l = style.shortTemperature(d.getMinTemp());
            final String h = style.shortTemperature(d.getMaxTemp());
            canvas.drawText(l, area.right - pad - lo.measureText(l), base, lo);
            canvas.drawText(h, area.right - pad - lo.measureText(l) - hi.measureText(h)
                    - area.width() * 0.04f, base, hi);
        }
    }

    private String dayName(final int daysFromNow) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysFromNow);
        return new SimpleDateFormat("EE", Locale.getDefault()).format(cal.getTime());
    }
}
