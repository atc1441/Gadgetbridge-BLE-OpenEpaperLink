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

/** Current conditions plus a short forecast. */
public class WeatherContent implements CardContent {
    /** Above this width to height ratio the forecast moves into a column on the right. */
    private static final float WIDE_RATIO = 1.9f;

    @Nullable
    private final WeatherSpec spec;
    private final int forecastDays;

    public WeatherContent(@Nullable final WeatherSpec spec, final int forecastDays) {
        this.spec = spec;
        this.forecastDays = forecastDays;
    }

    @Nullable
    @Override
    public String title() {
        if (spec == null) {
            return null;
        }
        final String location = spec.getLocation();
        return location == null || location.isEmpty() ? null : location;
    }

    @Override
    public boolean hasData() {
        return spec != null;
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (spec == null) {
            return;
        }
        final int days = available();
        if (area.width() / area.height() >= WIDE_RATIO && days > 0 && area.width() >= 210) {
            drawWithColumn(canvas, style, area, days);
        } else {
            drawStacked(canvas, style, area, days);
        }
    }

    private int available() {
        if (spec == null || spec.getForecasts() == null) {
            return 0;
        }
        return Math.max(0, Math.min(forecastDays, spec.getForecasts().size()));
    }

    private void drawWithColumn(final Canvas canvas, final CardStyle style, final RectF area,
                                final int days) {
        final float pad = Math.max(2f, area.width() * 0.02f);
        final float columnW = area.width() * 0.34f;
        final RectF main = new RectF(area.left, area.top, area.right - columnW, area.bottom);
        drawCurrent(canvas, style, main, pad, false);

        final Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(style.ink);
        rule.setStrokeWidth(Math.max(1f, columnW * 0.012f));
        canvas.drawLine(main.right, area.top, main.right, area.bottom, rule);

        drawForecastColumn(canvas, style, days,
                new RectF(main.right + pad, area.top, area.right - pad, area.bottom));
    }

    private void drawStacked(final Canvas canvas, final CardStyle style, final RectF area,
                             final int days) {
        final float pad = Math.max(2f, area.width() * 0.03f);
        final int fits = (int) ((area.width() - pad * 2) / 52f);
        final int shown = Math.min(days, Math.max(0, fits));
        final boolean withRow = shown > 0 && area.height() > 86;
        // the strip needs three rows of its own; below that it would squeeze the reading above
        // it into nothing, so the card goes without
        final float rowH = withRow ? Math.min(area.height() * 0.34f,
                Math.max(34f, area.height() * 0.30f)) : 0f;

        final RectF upper = new RectF(area.left, area.top, area.right, area.bottom - rowH);
        drawCurrent(canvas, style, upper, pad, true);
        if (withRow) {
            drawForecastRow(canvas, style, shown,
                    new RectF(area.left + pad, area.bottom - rowH, area.right - pad, area.bottom));
        }
    }

    /**
     * The current conditions, laid out from the bottom up: the condition sits on the bottom
     * edge, the day's range above it, and whatever is left over goes to the pictogram and the
     * reading. Placing those rows at fractions of the box instead is what used to push the last
     * one out under the card - and on a split card out under the status strip.
     */
    private void drawCurrent(final Canvas canvas, final CardStyle style, final RectF area,
                             final float pad, final boolean centred) {
        final float h = area.height();

        final String condition = spec.getCurrentCondition() == null
                ? null : spec.getCurrentCondition().trim();
        // Nine pixels rather than seven: these two lines sit under the reading on a card
        // that is often only half a panel wide, and a panel with two or three colours leaves
        // dust of anything thinner. The rows below are placed from the metrics of these
        // paints, so a larger line simply takes its room from the pictogram above it.
        final float condSize = Math.max(9f, h * 0.15f);
        // bold once the line gets small: a regular face at ten pixels is half grey, and half
        // grey is what the panel's quantiser throws away
        final Paint cond = style.paint(style.ink, condSize < 12f, condSize);
        final boolean withCondition = condition != null && !condition.isEmpty() && h > 46;
        if (withCondition) {
            style.fitNoSmallerThan(cond, condition, area.width() - pad * 2, 9f);
        }

        // Sized by the width as well as by the height. On a panel that is tall for its
        // width - a tag turned on its side, where the card is handed 152 by 522 - the height
        // alone asks for eighty pixel figures, and the second of the two then stood a long way
        // past the right hand edge of the panel.
        final float smallSize = Math.max(9f, Math.min(h * 0.16f, area.width() * 0.22f));
        final Paint small = style.paint(style.ink, smallSize < 12f, smallSize);
        final Paint high = style.paint(style.accent, true, small.getTextSize());
        final String hi = style.shortTemperature(spec.getTodayMaxTemp());
        final String lo = style.shortTemperature(spec.getTodayMinTemp());
        int pairGuard = 0;
        while (high.measureText(hi) + pad * 1.4f + small.measureText(lo)
                > area.width() - pad * 2f && small.getTextSize() > 9f && pairGuard++ < 64) {
            small.setTextSize(Math.max(OeplCanvas.READABLE, small.getTextSize() * 0.94f));
            high.setTextSize(small.getTextSize());
        }

        float floor = area.bottom;
        float condBase = 0f;
        if (withCondition) {
            condBase = style.baselineAbove(floor, cond);
            floor -= style.lineHeight(cond);
        }
        final boolean withRange = floor - area.top > style.lineHeight(small) * 1.8f;
        float pairBase = 0f;
        if (withRange) {
            pairBase = style.baselineAbove(floor, small);
            floor -= style.lineHeight(small);
        }

        // what is left over carries the reading and the pictogram
        final float band = Math.max(OeplCanvas.READABLE, floor - area.top);
        final float iconSize = Math.min(band * 0.92f, area.width() * 0.30f);
        final String temp = style.temperature(spec.getCurrentTemp());
        final Paint big = style.paint(style.ink, true, band * 0.92f);
        final Paint unit = style.paint(style.accent, true, big.getTextSize() * 0.38f);
        final String unitText = style.degree();

        final float gap = pad * 0.8f;
        int guard = 0;
        final float room = area.width() - iconSize - gap - pad * 2;
        while (big.measureText(temp) + unit.measureText(unitText) > room
                && big.getTextSize() > 6f && guard++ < 64) {
            big.setTextSize(Math.max(OeplCanvas.READABLE, big.getTextSize() * 0.94f));
            unit.setTextSize(Math.max(OeplCanvas.READABLE, big.getTextSize() * 0.38f));
        }

        final float groupW = iconSize + gap + big.measureText(temp) + unit.measureText(unitText);
        final float left = centred
                ? Math.max(area.left + pad, area.left + (area.width() - groupW) / 2f)
                : area.left + pad;
        final float middle = area.top + band / 2f;

        WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(spec.getCurrentConditionCode()),
                spec.isCurrentTimeNight(), left + iconSize * 0.5f, middle,
                iconSize, style.ink, style.accent);

        final float tempLeft = left + iconSize + gap;
        canvas.drawText(temp, tempLeft, middle + big.getTextSize() * 0.36f, big);
        canvas.drawText(unitText, tempLeft + big.measureText(temp) + pad * 0.3f,
                middle - big.getTextSize() * 0.14f, unit);

        if (withRange) {
            final float pairW = high.measureText(hi) + pad * 1.4f + small.measureText(lo);
            final float pairX = centred
                    ? Math.max(area.left + pad, area.left + (area.width() - pairW) / 2f)
                    : Math.min(tempLeft, area.right - pad - pairW);
            canvas.drawText(hi, pairX, pairBase, high);
            canvas.drawText(lo, pairX + high.measureText(hi) + pad * 1.4f, pairBase, small);
        }
        if (withCondition) {
            final float x = centred
                    ? area.left + (area.width() - cond.measureText(condition)) / 2f
                    : area.left + pad;
            style.drawEllipsised(canvas, condition, Math.max(area.left + pad, x), condBase,
                    area.width() - pad * 2, cond);
        }
    }

    private void drawForecastColumn(final Canvas canvas, final CardStyle style, final int days,
                                    final RectF area) {
        final float rowH = area.height() / days;
        final float nameW = area.width() * 0.32f;
        final float tempW = area.width() * 0.42f;
        final float iconW = area.width() - nameW - tempW;

        final Paint day = style.paint(style.ink, true, Math.min(rowH * 0.46f, nameW * 0.56f));
        final Paint hi = style.paint(style.accent, true, Math.min(rowH * 0.44f, tempW * 0.44f));
        final Paint lo = style.paint(style.ink, false, hi.getTextSize());

        final float gap = area.width() * 0.04f;
        float widest = 0f;
        for (int i = 0; i < days; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            widest = Math.max(widest, hi.measureText(style.shortTemperature(d.getMaxTemp()))
                    + lo.measureText(style.shortTemperature(d.getMinTemp())));
        }
        while (widest + gap > tempW && hi.getTextSize() > 6f) {
            hi.setTextSize(Math.max(OeplCanvas.READABLE, hi.getTextSize() * 0.94f));
            lo.setTextSize(hi.getTextSize());
            widest *= 0.94f;
        }

        for (int i = 0; i < days; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            final float base = Math.min(area.top + rowH * (i + 0.68f),
                    style.baselineAbove(area.bottom, lo));
            final String name = dayName(i + 1);
            style.fitNoSmallerThan(day, name, nameW, OeplCanvas.READABLE);
            style.drawEllipsised(canvas, name, area.left, base, nameW, day);

            WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(d.getConditionCode()), false,
                    area.left + nameW + iconW * 0.5f, base - rowH * 0.22f,
                    Math.min(rowH * 0.62f, iconW * 0.82f), style.ink, style.accent);

            final String max = style.shortTemperature(d.getMaxTemp());
            final String min = style.shortTemperature(d.getMinTemp());
            canvas.drawText(min, area.right - lo.measureText(min), base, lo);
            canvas.drawText(max, area.right - lo.measureText(min) - hi.measureText(max) - gap,
                    base, hi);
        }
    }

    /**
     * The strip of coming days under the current conditions. Its three rows are measured from
     * the font rather than placed at fractions of the strip, so the temperatures keep their
     * descenders instead of being cut off by the bottom edge of the card.
     */
    private void drawForecastRow(final Canvas canvas, final CardStyle style, final int days,
                                 final RectF area) {
        final Paint rule = new Paint();
        rule.setColor(style.ink);
        final float ruleH = Math.max(1f, area.height() * 0.025f);
        canvas.drawRect(area.left, area.top, area.right, area.top + ruleH, rule);

        final float colW = area.width() / days;
        final Paint day = style.paint(style.ink, true,
                Math.max(12f, Math.min(area.height() * 0.28f, colW * 0.36f)));
        final Paint hi = style.paint(style.accent, true, day.getTextSize());
        final Paint lo = style.paint(style.ink, false, day.getTextSize());

        // The two figures of a day have to fit the column they stand under: in Fahrenheit they
        // are three digits wide where Celsius has two, and one day's pair then ran into the
        // next day's.
        final float figureGap = colW * 0.08f;
        for (int guard = 0; guard < 24; guard++) {
            float widest = 0f;
            for (int i = 0; i < days; i++) {
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

        final float tempBase = style.baselineAbove(area.bottom, hi);
        final float tempTop = tempBase + hi.getFontMetrics().ascent;
        final float dayTop = area.top + ruleH + Math.max(1f, area.height() * 0.04f);
        final float dayBase = dayTop - day.getFontMetrics().ascent;
        final float iconTop = dayTop + style.lineHeight(day) * 0.95f;
        final float iconSize = Math.min(Math.max(6f, tempTop - iconTop), colW * 0.52f);

        // The weekday has to fit its column. Written straight out it is Monday in German and
        // segunda-feira in Portuguese, and the strip then had one day's name written through
        // the next one's. One size for all of them, taken from the widest.
        float widestDay = 0f;
        for (int i = 0; i < days; i++) {
            widestDay = Math.max(widestDay, day.measureText(dayName(i + 1)));
        }
        if (widestDay > colW * 0.95f) {
            style.fitNoSmallerThan(day, widestDayName(day, days), colW * 0.95f, OeplCanvas.READABLE);
        }

        for (int i = 0; i < days; i++) {
            final WeatherSpec.Daily d = spec.getForecasts().get(i);
            final float cx = area.left + colW * (i + 0.5f);
            final String name = dayName(i + 1);
            style.drawEllipsised(canvas, name,
                    Math.max(area.left, cx - Math.min(day.measureText(name), colW * 0.95f) / 2f),
                    dayBase, colW * 0.95f, day);

            WeatherIcons.draw(canvas, WeatherIcons.forConditionCode(d.getConditionCode()), false,
                    cx, iconTop + iconSize / 2f, iconSize, style.ink, style.accent);

            final String max = style.shortTemperature(d.getMaxTemp());
            // as in the forecast card: where both will not fit the column at a size that can
            // be read, the day's high stays and its low goes
            final String min = hi.measureText(max) + figureGap
                    + lo.measureText(style.shortTemperature(d.getMinTemp())) > colW
                    ? "" : style.shortTemperature(d.getMinTemp());
            final float total = hi.measureText(max) + lo.measureText(min)
                    + (min.isEmpty() ? 0f : figureGap);
            final float startX = cx - total / 2f;
            canvas.drawText(max, startX, tempBase, hi);
            if (!min.isEmpty()) {
                canvas.drawText(min, startX + hi.measureText(max) + figureGap, tempBase, lo);
            }
        }
    }

    /** The widest of the weekday names drawn, measured rather than counted. */
    private String widestDayName(final Paint paint, final int days) {
        String widest = dayName(1);
        for (int i = 2; i <= days; i++) {
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
