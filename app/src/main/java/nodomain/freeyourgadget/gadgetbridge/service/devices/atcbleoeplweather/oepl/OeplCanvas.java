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

    /**
     * Shrinks a paint until the text fits the width, but no further than a floor. What does
     * not fit at that size is a caller's decision to cut; everything above it is a label that
     * can be read in full, which for a name is the difference between useful and useless.
     */
    public void fitNoSmallerThan(final Paint paint, final String text, final float maxWidth,
                                 final float floor) {
        if (text == null) {
            return;
        }
        int guard = 0;
        while (maxWidth > 0 && paint.measureText(text) > maxWidth
                && paint.getTextSize() > floor && guard++ < 128) {
            paint.setTextSize(Math.max(floor, paint.getTextSize() * 0.95f));
        }
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
            if (paint.measureText(text) > maxWidth) {
                // not even one letter and an ellipsis fit into the space. Drawing it anyway is
                // what pushed labels out of their column and over the edge of the panel, so
                // the mark alone goes in, and nothing at all when even that is too wide.
                text = "…";
                if (paint.measureText(text) > maxWidth) {
                    return;
                }
            }
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
        textFillingAt(text, cx, cy, fillingSize(text, maxWidth, maxHeight), color);
    }

    /**
     * Draws text at a size that was worked out elsewhere, centred on its ink the way
     * {@link #textFilling} centres it. Several figures that belong together are set in one size
     * this way - four tiles whose numbers are each as large as their own tile allows cannot be
     * compared with a glance, which is the only thing a tile is for.
     */
    public void textFillingAt(final String text, final float cx, final float cy,
                              final float size, final int color) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return;
        }
        final Paint paint = textPaint(size, color);
        final android.graphics.Rect bounds = new android.graphics.Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        canvas.drawText(text, cx - bounds.width() / 2f - bounds.left,
                cy + bounds.height() / 2f - bounds.bottom, paint);
    }

    /**
     * As {@link #textFilling}, but cut with an ellipsis when no size would make it fit.
     * <p>
     * Setting a line as large as its box allows only works while the line is a figure. A
     * caption somebody typed, or a state a home automation system words rather than numbers,
     * is a sentence: it is still too wide at the smallest size that can be read, and
     * {@link #fillingSize} has to stop shrinking somewhere. Whatever is left over then goes
     * over the edge of the panel - which is what this exists to prevent.
     */
    public void textFillingClipped(final String text, final float cx, final float cy,
                                   final float maxWidth, final float maxHeight,
                                   final int color) {
        textFillingAtClipped(text, cx, cy, fillingSize(text, maxWidth, maxHeight), color,
                maxWidth);
    }

    /** As {@link #textFillingClipped}, at a size that was worked out for several lines at once. */
    public void textFillingAtClipped(final String text, final float cx, final float cy,
                                     final float size, final int color, final float maxWidth) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return;
        }
        // measured as ink, the way textFillingAt places it: a face carries side bearings,
        // and comparing the advance width against the box declares a figure too wide that
        // fits perfectly well - which came out as a cut "-12...." where "-12.7" belonged
        if (inkWidth(text, size) <= maxWidth) {
            textFillingAt(text, cx, cy, size, color);
        } else {
            textClipped(text, cx, cy - lineHeight(size) / 2f, size, CENTER, color, maxWidth);
        }
    }

    /** How wide the drawn glyphs of a line are, side bearings excluded. */
    public float inkWidth(final String text, final float size) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        final android.graphics.Rect bounds = new android.graphics.Rect();
        textPaint(size, ink).getTextBounds(text, 0, text.length(), bounds);
        return bounds.width();
    }

    /** The size {@link #textFilling} would set this text in, or 0 when there is none. */
    public float fillingSize(final String text, final float maxWidth, final float maxHeight) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        final Paint paint = textPaint(Math.max(8f, maxHeight), ink);
        final android.graphics.Rect bounds = new android.graphics.Rect();
        // Converge on the size where the drawn glyphs just touch the box, from either side:
        // the cap height of a face is well under its em size, so the first guess is too small.
        // Both the fit and the centring go by the ink the glyphs cover, never by the advance
        // width - a face carries side bearings, and a line sized to its advance width reaches
        // past the box it was given.
        for (int step = 0; step < 24; step++) {
            paint.getTextBounds(text, 0, text.length(), bounds);
            if (bounds.height() <= 0 || bounds.width() <= 0) {
                break;
            }
            final float factor = Math.min(maxHeight / bounds.height(),
                    maxWidth / bounds.width());
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
        // the floor above may still be too wide for a very narrow panel, and a line that does
        // not fit has to be made to fit rather than be allowed over the edge
        paint.getTextBounds(text, 0, text.length(), bounds);
        for (int guard = 0; guard < 32 && paint.getTextSize() > 4f
                && (bounds.width() > maxWidth || bounds.height() > maxHeight); guard++) {
            paint.setTextSize(Math.max(4f, paint.getTextSize() * 0.94f));
            paint.getTextBounds(text, 0, text.length(), bounds);
        }
        return paint.getTextSize();
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
        // scaled so that the drawn glyph, not its em box, matches the requested height
        final android.graphics.Rect bounds = new android.graphics.Rect();
        final Paint paint = fitIcon(glyph, size, color, bounds);
        final float left = cx - bounds.width() / 2f - bounds.left;
        final float baseline = cy + bounds.height() / 2f - bounds.bottom;
        canvas.drawText(glyph, left, baseline, paint);
    }

    /**
     * How wide a pictogram ends up when it is drawn at a given height with
     * {@link #iconCentered}. The icon face is far from square, so a layout that reserves the
     * height for an icon and puts a number beside it has them overlap on every panel where the
     * glyph happens to be wide. Returns 0 when there is no icon face, which is also how much
     * room the gap then needs.
     */
    public float iconWidth(final String glyph, final float size) {
        if (glyph == null || glyph.isEmpty() || !OeplFonts.hasIcons()) {
            return 0f;
        }
        final android.graphics.Rect bounds = new android.graphics.Rect();
        fitIcon(glyph, size, ink, bounds);
        return bounds.width();
    }

    /**
     * A paint whose glyph is as close to the requested height as the face allows, and never
     * taller. One correction step is not enough - scaling a face by the ratio of two measured
     * heights lands next to the target rather than on it, and a pictogram a few pixels taller
     * than the room it was given is a pictogram touching the line below it.
     */
    private Paint fitIcon(final String glyph, final float size, final int color,
                          final android.graphics.Rect bounds) {
        final Paint paint = iconPaint(size, color);
        paint.getTextBounds(glyph, 0, glyph.length(), bounds);
        for (int step = 0; step < 6; step++) {
            if (bounds.height() <= 0 || Math.abs(bounds.height() - size) <= 0.5f) {
                break;
            }
            paint.setTextSize(Math.max(4f, paint.getTextSize() * size / bounds.height()));
            paint.getTextBounds(glyph, 0, glyph.length(), bounds);
        }
        for (int guard = 0; guard < 12 && bounds.height() > size
                && paint.getTextSize() > 4f; guard++) {
            paint.setTextSize(Math.max(4f, paint.getTextSize() * 0.96f));
            paint.getTextBounds(glyph, 0, glyph.length(), bounds);
        }
        return paint;
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
            String line = content.substring(start, end).trim();
            // the last line that fits, with text still to come: it ends in an ellipsis rather
            // than in the middle of a word, so that a reader can tell a summary that was cut
            // from a sentence that simply ends there
            final boolean last = cursor + lineHeight + (fm.descent - fm.ascent) > y + boxHeight;
            if (last && end < length) {
                while (line.length() > 1 && paint.measureText(line + "…") > boxWidth) {
                    line = line.substring(0, line.length() - 1);
                }
                line = line.trim() + "…";
            }
            draw(line, x, cursor, align, paint);
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
            // The caption is made smaller before it is cut. A row headed "Hamb…" names
            // nowhere, and the line has the height for a smaller word either way; only a
            // caption that would have to go below two thirds of the row is cut after all.
            final float room = x1 - x0 - used;
            final Paint paint = textPaint(size, leftColor);
            fitNoSmallerThan(paint, left, room, Math.max(7f, size * 0.62f));
            if (paint.measureText(left) <= room) {
                draw(left, x0, y, LEFT, paint);
            } else {
                textClipped(left, x0, y, paint.getTextSize(), LEFT, leftColor, room);
            }
        }
    }

    /**
     * The largest text size, no larger than the one asked for, whose line fits into the room
     * given - or 0 when not even the smallest legible line does, which is a caller's cue to
     * leave the label out. A line occupies about a quarter more than its size, so a label sized
     * to the box it sits in reaches out of the bottom of it.
     */
    public float sizeForHeight(final float wanted, final float available, final float floor) {
        float size = Math.max(floor, wanted);
        for (int guard = 0; guard < 48 && size > floor && lineHeight(size) > available; guard++) {
            size = Math.max(floor, size * 0.93f);
        }
        return lineHeight(size) <= available ? size : 0f;
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
