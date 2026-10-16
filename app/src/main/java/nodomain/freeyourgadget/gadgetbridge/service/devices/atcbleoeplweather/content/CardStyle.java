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
import android.graphics.Typeface;

import java.util.Locale;

/**
 * The drawing vocabulary every card content shares: the three panel colours, text that scales
 * with the panel rather than with pixels, and unit handling. Keeping this in one place is what
 * lets a content look the same on a 152x152 price tag and on a 960x768 panel.
 */
public class CardStyle {
    private static final int OPAQUE = 0xff000000;

    public final int ink;
    public final int paper;
    public final int accent;
    public final boolean fahrenheit;

    public CardStyle(final int inkRgb, final int paperRgb, final int accentRgb,
                     final boolean fahrenheit) {
        this.ink = OPAQUE | inkRgb;
        this.paper = OPAQUE | paperRgb;
        this.accent = OPAQUE | accentRgb;
        this.fahrenheit = fahrenheit;
    }

    public Paint paint(final int color, final boolean bold, final float size) {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(Math.max(6f, size));
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    /** Shrinks the text until it fits, down to the smallest size that is still legible. */
    public void fit(final Paint paint, final String text, final float maxWidth) {
        if (maxWidth <= 0 || text == null) {
            return;
        }
        int guard = 0;
        while (paint.measureText(text) > maxWidth && paint.getTextSize() > 6f && guard++ < 64) {
            paint.setTextSize(paint.getTextSize() * 0.94f);
        }
    }

    /** Draws text clipped to a width, cutting it with an ellipsis rather than overflowing. */
    public void drawEllipsised(final Canvas canvas, String text, final float x, final float y,
                               final float maxWidth, final Paint paint) {
        if (text == null) {
            return;
        }
        if (paint.measureText(text) > maxWidth) {
            while (text.length() > 1 && paint.measureText(text + "…") > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "…";
            if (paint.measureText(text) > maxWidth) {
                // one letter and an ellipsis are wider than the space: the mark alone, or
                // nothing. Drawing it regardless is how a label left its own column.
                text = "…";
                if (paint.measureText(text) > maxWidth) {
                    return;
                }
            }
        }
        canvas.drawText(text, x, y, paint);
    }

    /** How tall one line of this paint is, from the top of its line to the bottom. */
    public float lineHeight(final Paint paint) {
        final Paint.FontMetrics fm = paint.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    /**
     * Draws a line of text hanging from the top of its line rather than sitting on its baseline.
     * A layout is written down in terms of the space a line occupies, so this is the form that
     * can be stacked without guessing at the font's metrics - and guessing is what used to let
     * the last row of a card slide out under its bottom edge.
     */
    public void drawTop(final Canvas canvas, final String text, final float x, final float top,
                        final Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        canvas.drawText(text, x, top - paint.getFontMetrics().ascent, paint);
    }

    /** As {@link #drawTop}, cut with an ellipsis once it would run past maxWidth. */
    public void drawTopEllipsised(final Canvas canvas, final String text, final float x,
                                  final float top, final float maxWidth, final Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        drawEllipsised(canvas, text, x, top - paint.getFontMetrics().ascent, maxWidth, paint);
    }

    /**
     * Draws a label against the left edge and a value against the right edge of the same line,
     * and makes sure the two can never touch: the value is measured first and the label gets
     * whatever is left over, cut with an ellipsis if need be. Two labels drawn at opposite ends
     * without that budget is what collided on every narrow panel.
     *
     * @return the width the right hand side took, including the gap
     */
    public float drawEnds(final Canvas canvas, final String left, final Paint leftPaint,
                          final String right, final Paint rightPaint,
                          final float x0, final float x1, final float top, final float gap) {
        float used = 0f;
        if (right != null && !right.isEmpty()) {
            // the value may not eat the whole line either, or the label disappears entirely
            fit(rightPaint, right, (x1 - x0) * 0.6f);
            used = rightPaint.measureText(right) + gap;
            drawTop(canvas, right, x1 - rightPaint.measureText(right), top, rightPaint);
        }
        if (left != null && !left.isEmpty()) {
            drawTopEllipsised(canvas, left, x0, top, x1 - x0 - used, leftPaint);
        }
        return used;
    }

    /**
     * The baseline for a line that should sit on the bottom edge of a box, so that its
     * descenders stay inside it. Placing the last line at a fraction of the box instead is what
     * cut the bottom row of half the cards through the middle of its letters.
     */
    public float baselineAbove(final float bottom, final Paint paint) {
        return bottom - paint.getFontMetrics().descent;
    }

    /**
     * Shrinks text to fit, but not past the point where a panel can still be read across a
     * room. Anything that does not fit at that size is better cut than made microscopic.
     */
    public void fitNoSmallerThan(final Paint paint, final String text, final float maxWidth,
                                 final float floor) {
        if (maxWidth <= 0 || text == null) {
            return;
        }
        int guard = 0;
        while (paint.measureText(text) > maxWidth && paint.getTextSize() > floor
                && guard++ < 64) {
            paint.setTextSize(Math.max(floor, paint.getTextSize() * 0.94f));
        }
    }

    /** True while another line of this height still fits above the bottom edge. */
    public boolean roomFor(final float top, final float lineHeight, final float bottom) {
        return top + lineHeight <= bottom + 0.5f;
    }

    public String degree() {
        return fahrenheit ? "°F" : "°C";
    }

    /** Weather data travels in kelvin throughout Gadgetbridge. */
    public String temperature(final int kelvin) {
        final double celsius = kelvin - 273.15;
        final double value = fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius;
        return String.valueOf(Math.round(value));
    }

    public String shortTemperature(final int kelvin) {
        return temperature(kelvin) + "°";
    }

    public String time(final long millis) {
        return new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new java.util.Date(millis));
    }
}
