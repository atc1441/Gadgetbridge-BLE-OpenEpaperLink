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

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCanvas;

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

        // The two figures of a day have to fit the column they belong to, not merely the
        // card: in Fahrenheit they are three digits wide where Celsius has two, and the pair
        // under one day then ran into the pair under the next.
        final float figureGap = colW * 0.08f;
        for (int guard = 0; guard < 24; guard++) {
            float widest = 0f;
            for (int i = 0; i < count; i++) {
                final WeatherSpec.Daily d = spec.getForecasts().get(i);
                widest = Math.max(widest,
                        hi.measureText(style.shortTemperature(d.getMaxTemp())) + figureGap
                                + lo.measureText(style.shortTemperature(d.getMinTemp())));
            }
            if (widest <= colW - figureGap || hi.getTextSize() <= 7f) {
                break;
            }
            hi.setTextSize(Math.max(OeplCanvas.READABLE, hi.getTextSize() * 0.94f));
            lo.setTextSize(Math.max(OeplCanvas.READABLE, lo.getTextSize() * 0.94f));
        }

        // the weekday fits its column in every language, or it is set smaller until it does:
        // "Mon" and "segunda-feira" are the same column
        style.fitNoSmallerThan(day, widestDayName(day, count), colW * 0.95f, OeplCanvas.READABLE);

        for (int i = 0; i < count; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            final float cx = area.left + colW * (i + 0.5f);

            final String name = dayName(i + 1);
            style.drawEllipsised(canvas, name,
                    Math.max(area.left, cx - Math.min(day.measureText(name), colW * 0.95f) / 2f),
                    area.top + area.height() * 0.15f, colW * 0.95f, day);

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
            // Where the pair will not fit the column even at the smallest readable size, the
            // low goes and the high stays. Two figures written through each other are two
            // figures nobody can read; one is one.
            final String l = hi.measureText(h) + figureGap
                    + lo.measureText(style.shortTemperature(d.getMinTemp())) > colW
                    ? "" : style.shortTemperature(d.getMinTemp());
            final float gap = l.isEmpty() ? 0f : figureGap;
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
        final float gap = Math.max(2f, area.width() * 0.04f);

        // A row holds a weekday, the sky and two figures, and on a tall narrow panel the rows
        // are high enough to make the figures wide enough to reach the weekday. So the figures
        // are measured first, over all the days, and the rest of the row is what is left: the
        // weekday gets a budget, and the pictogram only appears while there is room for it.
        float figures = 0f;
        for (int guard = 0; guard < 24; guard++) {
            figures = 0f;
            for (int i = 0; i < count; i++) {
                final WeatherSpec.Daily d = spec.getForecasts().get(i);
                figures = Math.max(figures,
                        hi.measureText(style.shortTemperature(d.getMaxTemp())) + gap
                                + lo.measureText(style.shortTemperature(d.getMinTemp())));
            }
            if (figures <= area.width() * 0.52f || hi.getTextSize() <= 7f) {
                break;
            }
            hi.setTextSize(Math.max(OeplCanvas.READABLE, hi.getTextSize() * 0.94f));
            lo.setTextSize(Math.max(OeplCanvas.READABLE, lo.getTextSize() * 0.94f));
        }

        final float figuresLeft = area.right - pad - figures;
        final float dayLeft = area.left + pad;
        final float room = figuresLeft - dayLeft - gap;
        float iconSize = Math.min(Math.min(rowH * 0.66f, area.width() * 0.2f), room * 0.42f);
        if (iconSize < 9f) {
            iconSize = 0f; // a pictogram smaller than this says nothing anyway
        }
        final float dayRoom = room - (iconSize > 0f ? iconSize + gap : 0f);

        // The weekday is an abbreviation already, so it is set smaller rather than cut: on a
        // panel narrower than its own labels the figures take half the row and leave the day
        // thirteen pixels, where "Mo" set small still says which day it is and a cut one says
        // nothing. The widest of the days decides the size, so they all come out the same.
        style.fitNoSmallerThan(day, widestDayName(day, count), dayRoom, OeplCanvas.READABLE);

        for (int i = 0; i < count; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            final float base = area.top + rowH * (i + 0.68f);
            style.drawEllipsised(canvas, dayName(i + 1), dayLeft, base, dayRoom, day);

            if (iconSize > 0f) {
                WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(d.getConditionCode()),
                        false, figuresLeft - gap - iconSize * 0.5f, base - rowH * 0.22f,
                        iconSize, style.ink, style.accent);
            }

            final String l = style.shortTemperature(d.getMinTemp());
            final String h = style.shortTemperature(d.getMaxTemp());
            canvas.drawText(h, figuresLeft, base, hi);
            canvas.drawText(l, area.right - pad - lo.measureText(l), base, lo);
        }
    }

    /**
     * The widest of the weekday names that are drawn, so that one size fits them all. It is
     * measured rather than counted: "Wed" has as many letters as "Tue" and is wider, and the
     * one that does not fit is the one that ends up cut.
     */
    private String widestDayName(final Paint paint, final int count) {
        String widest = dayName(1);
        for (int i = 2; i <= count; i++) {
            final String name = dayName(i);
            if (paint.measureText(name) > paint.measureText(widest)) {
                widest = name;
            }
        }
        return widest;
    }

    private String dayName(final int daysFromNow) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysFromNow);
        return new SimpleDateFormat("EE", Locale.getDefault()).format(cal.getTime());
    }
}
