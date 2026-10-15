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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * A drawing surface that speaks the way an e-paper layout wants to be described: a text's
 * anchor is the top of its line, not its baseline, and it sits left of, centred on, or right of
 * the given point. That is how OpenEPaperLink layouts are written down, and keeping the same
 * vocabulary here is what lets the card layouts be transcribed rather than re-invented.
 */
public class OeplCanvas {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;

    public final Canvas canvas;
    public final float width;
    public final float height;
    public final int ink;
    public final int paper;
    public final int accent;

    public OeplCanvas(final Canvas canvas, final float width, final float height,
                      final int ink, final int paper, final int accent) {
        this.canvas = canvas;
        this.width = width;
        this.height = height;
        this.ink = ink;
        this.paper = paper;
        this.accent = accent;
    }

    public Paint textPaint(final float size, final int color) {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(OeplFonts.text());
        p.setTextSize(Math.max(5f, size));
        p.setColor(color);
        return p;
    }

    public Paint iconPaint(final float size, final int color) {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(OeplFonts.icons() != null ? OeplFonts.icons() : OeplFonts.text());
        p.setTextSize(Math.max(5f, size));
        p.setColor(color);
        return p;
    }

    /** Shrinks a paint until the text fits the width. */
    public void fit(final Paint paint, final String text, final float maxWidth) {
        if (text == null) {
            return;
        }
        int guard = 0;
        while (maxWidth > 0 && paint.measureText(text) > maxWidth
                && paint.getTextSize() > 5f && guard++ < 128) {
            paint.setTextSize(paint.getTextSize() * 0.95f);
        }
    }

    public float measure(final String text, final float size) {
        return textPaint(size, ink).measureText(text == null ? "" : text);
    }

    /**
     * Draws one line of text.
     *
     * @param y the top of the line, the way an e-paper layout is written
     */
    public void text(final String text, final float x, final float y, final float size,
                     final int align, final int color) {
        draw(text, x, y, align, textPaint(size, color));
    }

    /** As {@link #text}, but cut with an ellipsis once it would run past maxWidth. */
    public void textClipped(String text, final float x, final float y, final float size,
                            final int align, final int color, final float maxWidth) {
        if (text == null) {
            return;
        }
        final Paint paint = textPaint(size, color);
        if (maxWidth > 0 && paint.measureText(text) > maxWidth) {
            while (text.length() > 1 && paint.measureText(text + "…") > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "…";
        }
        draw(text, x, y, align, paint);
    }

    /** Draws text shrunk until it fits, rather than cut. */
    public void textFitted(final String text, final float x, final float y, final float size,
                           final int align, final int color, final float maxWidth) {
        final Paint paint = textPaint(size, color);
        fit(paint, text, maxWidth);
        draw(text, x, y, align, paint);
    }

    /**
     * Draws text sitting on a baseline instead of hanging from the top of its line, which is
     * what a layout wants for a big number that should rest on the bottom edge.
     */
    public void textOnBaseline(final String text, final float x, final float baseline,
                               final float size, final int align, final int color) {
        final Paint paint = textPaint(size, color);
        if (text == null || text.isEmpty()) {
            return;
        }
        float left = x;
        if (align == CENTER) {
            left = x - paint.measureText(text) / 2f;
        } else if (align == RIGHT) {
            left = x - paint.measureText(text);
        }
        canvas.drawText(text, left, baseline, paint);
    }

    /**
     * Sets a piece of text as large as a box allows and centres it on the ink rather than on
     * the font box. A font leaves room above its capitals for accents and below for
     * descenders, so a line of digits placed by its metrics always looks too low; measuring the
     * drawn glyphs instead is what makes a big number sit in the middle of a panel.
     */
    public void textFilling(final String text, final float cx, final float cy,
                            final float maxWidth, final float maxHeight, final int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final Paint paint = textPaint(Math.max(8f, maxHeight), color);
        final android.graphics.Rect bounds = new android.graphics.Rect();
        // Converge on the size where the drawn glyphs just touch the box, from either side:
        // the cap height of a face is well under its em size, so the first guess is too small.
        for (int step = 0; step < 24; step++) {
            paint.getTextBounds(text, 0, text.length(), bounds);
            final float wide = paint.measureText(text);
            if (bounds.height() <= 0 || wide <= 0) {
                break;
            }
            final float factor = Math.min(maxHeight / bounds.height(), maxWidth / wide);
            if (factor > 0.99f && factor < 1.02f) {
                break;
            }
            final float next = paint.getTextSize() * factor;
            if (next < 6f) {
                paint.setTextSize(6f);
                break;
            }
            paint.setTextSize(next);
        }
        paint.getTextBounds(text, 0, text.length(), bounds);
        final float left = cx - paint.measureText(text) / 2f;
        final float baseline = cy + bounds.height() / 2f - bounds.bottom;
        canvas.drawText(text, left, baseline, paint);
    }

    /** How far a line of this size reaches above its baseline. */
    public float ascent(final float size) {
        return -textPaint(size, ink).getFontMetrics().ascent;
    }

    /** Draws a pictogram from the icon face. */
    public void icon(final String glyph, final float x, final float y, final float size,
                     final int align, final int color) {
        if (glyph == null || glyph.isEmpty()) {
            return;
        }
        if (!OeplFonts.hasIcons()) {
            return; // a tofu box would be worse than a gap
        }
        draw(glyph, x, y, align, iconPaint(size, color));
    }

    /**
     * Draws a pictogram centred on a point, measured from the glyph itself rather than from the
     * font box. The icon face leaves a lot of air above and below its glyphs, so this is the
     * only way to put one reliably in the middle of a space.
     *
     * @param size the height the glyph should end up with
     */
    public void iconCentered(final String glyph, final float cx, final float cy,
                             final float size, final int color) {
        if (glyph == null || glyph.isEmpty() || !OeplFonts.hasIcons()) {
            return;
        }
        final Paint paint = iconPaint(size, color);
        final android.graphics.Rect bounds = new android.graphics.Rect();
        paint.getTextBounds(glyph, 0, glyph.length(), bounds);
        if (bounds.height() > 0 && bounds.height() != size) {
            // scale so that the drawn glyph, not its em box, matches the requested height
            paint.setTextSize(paint.getTextSize() * size / bounds.height());
            paint.getTextBounds(glyph, 0, glyph.length(), bounds);
        }
        final float left = cx - bounds.width() / 2f - bounds.left;
        final float baseline = cy + bounds.height() / 2f - bounds.bottom;
        canvas.drawText(glyph, left, baseline, paint);
    }

    private void draw(final String text, final float x, final float y, final int align,
                      final Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float baseline = y - fm.ascent;
        float left = x;
        if (align == CENTER) {
            left = x - paint.measureText(text) / 2f;
        } else if (align == RIGHT) {
            left = x - paint.measureText(text);
        }
        canvas.drawText(text, left, baseline, paint);
    }

    /**
     * Fills a box with wrapped text, breaking on spaces and hyphens and stopping once the box is
     * full.
     *
     * @return the y the next block of text would start at
     */
    public float textBox(final String content, final float x, final float y, final float boxWidth,
                         final float boxHeight, final float size, final int color,
                         final float lineSpacing, final int align) {
        if (content == null || content.isEmpty()) {
            return y;
        }
        final Paint paint = textPaint(size, color);
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float lineHeight = (fm.descent - fm.ascent) * Math.max(0.6f, lineSpacing);
        float cursor = y;
        int start = 0;
        final int length = content.length();

        while (start < length && cursor + (fm.descent - fm.ascent) <= y + boxHeight) {
            int end = start;
            boolean breakable = false;
            while (end < length && content.charAt(end) != 10
                    && paint.measureText(content.substring(start, end + 1)) <= boxWidth) {
                final char c = content.charAt(end);
                if (c == 32 || c == 45) {
                    breakable = true;
                }
                end++;
            }
            // step back to the last word boundary, unless the whole line is one long word
            while (breakable && end > start && end < length && content.charAt(end) != 10
                    && content.charAt(end - 1) != 32 && content.charAt(end - 1) != 45) {
                end--;
            }
            if (end == start) {
                end = Math.min(length, start + 1);
            }
            draw(content.substring(start, end).trim(), x, cursor, align, paint);
            cursor += lineHeight;
            if (end < length && content.charAt(end) == 10) {
                end++;
            }
            start = end;
            while (start < length && content.charAt(start) == 32) {
                start++;
            }
        }
        return cursor;
    }

    /**
     * Draws the two ends of a row - a caption on the left, a value on the right - so that they
     * can never run into each other. The right hand side is measured first and the caption gets
     * whatever is left, cut with an ellipsis if need be. Writing both ends by hand and hoping
     * the panel is wide enough is what collided on every small tag.
     */
    public void rowEnds(final String left, final String right, final float y, final float size,
                        final int leftColor, final int rightColor, final float x0,
                        final float x1, final float gap) {
        float used = 0f;
        if (right != null && !right.isEmpty()) {
            final Paint paint = textPaint(size, rightColor);
            // the value may not eat the whole line either, or the caption disappears entirely
            fit(paint, right, (x1 - x0) * 0.6f);
            used = paint.measureText(right) + gap;
            draw(right, x1, y, RIGHT, paint);
        }
        if (left != null && !left.isEmpty()) {
            textClipped(left, x0, y, size, LEFT, leftColor, x1 - x0 - used);
        }
    }

    /** True while a line of this size still fits above the bottom edge. */
    public boolean roomFor(final float y, final float size) {
        return y + lineHeight(size) <= height + 0.5f;
    }

    /**
     * Keeps a centred label inside the panel. A label under the first or last column of a chart
     * would otherwise hang over the edge and be cut in half by the panel border.
     */
    public float clampCentre(final float cx, final String text, final float size) {
        final float half = measure(text, size) / 2f + 1f;
        return Math.max(half, Math.min(width - half, cx));
    }

    public void fillRect(final float x, final float y, final float w, final float h,
                         final int color) {
        final Paint p = new Paint();
        p.setColor(color);
        canvas.drawRect(x, y, x + w, y + h, p);
    }

    public void line(final float x1, final float y1, final float x2, final float y2,
                     final int color, final float thickness) {
        final Paint p = new Paint();
        p.setColor(color);
        p.setStrokeWidth(Math.max(1f, thickness));
        canvas.drawLine(x1, y1, x2, y2, p);
    }

    /** A vertical divider made of single dots, the way forecast columns are separated. */
    public void dottedColumn(final float x, final float top, final float bottom,
                             final int color, final float step) {
        final Paint p = new Paint();
        p.setColor(color);
        final float gap = Math.max(2f, step);
        // on whole pixels, otherwise a single pixel dot lands half on each side and the
        // quantiser that follows rounds the resulting grey away to nothing
        final float column = Math.round(x);
        for (float y = top; y < bottom; y += gap) {
            canvas.drawRect(column, Math.round(y), column + 1f, Math.round(y) + 1f, p);
        }
    }

    public void strokeRect(final float x, final float y, final float w, final float h,
                           final int color, final float thickness) {
        final Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, thickness));
        canvas.drawRect(x, y, x + w, y + h, p);
    }

    /** The line height a given text size occupies, for layouts that stack rows. */
    public float lineHeight(final float size) {
        final Paint.FontMetrics fm = textPaint(size, ink).getFontMetrics();
        return fm.descent - fm.ascent;
    }

    /** Lets a caller use a different face for one piece of text, e.g. a condensed one. */
    public void textWith(final Typeface face, final String text, final float x, final float y,
                         final float size, final int align, final int color) {
        final Paint p = textPaint(size, color);
        p.setTypeface(face);
        draw(text, x, y, align, p);
    }
}
