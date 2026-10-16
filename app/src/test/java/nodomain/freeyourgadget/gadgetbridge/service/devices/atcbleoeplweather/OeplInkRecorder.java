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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * A canvas that draws normally and writes down where every piece of ink landed.
 * <p>
 * Looking at pixels afterwards tells you that something is wrong somewhere on the panel;
 * it cannot tell you that two lines of text are sitting on top of each other, because ink over
 * ink looks exactly like ink. So each text run is recorded with the box its glyphs really
 * occupy - measured from the face, not from the font's em box, which is a third taller than the
 * letters and would make every tidy list look like a collision - and two boxes that share space
 * are two pieces of text nobody can read.
 * <p>
 * The same record answers the other half of the question: a run whose box leaves the rectangle
 * the card was given is a layout that writes outside its own area. On a real panel that is a
 * smear over the title bar or a line cut in half by the edge, and it stays invisible to a pixel
 * check whenever a clip happens to hide it.
 * <p>
 * Boxes are kept in the coordinates of the bitmap, so a content that is handed an offset
 * rectangle and translates its canvas is measured where the ink actually is.
 */
public class OeplInkRecorder extends Canvas {

    /** One piece of ink, with the box it covers in bitmap coordinates. */
    public static class Run {
        /** What actually reaches the panel: the box after the clip took its share. */
        public final RectF box;
        /**
         * The box the layout asked for, clip or no clip. This is the one that says whether a
         * layout writes outside its area: a clip turns an overflow into a lost letter, which a
         * check on the surviving ink can no longer see.
         */
        public final RectF wanted;
        /** The text, or a short word for the kind of shape it was. */
        public final String what;
        public final boolean text;

        Run(final RectF box, final RectF wanted, final String what, final boolean text) {
            this.box = box;
            this.wanted = wanted;
            this.what = what;
            this.text = text;
        }

        /** False once the clip has eaten the whole run. */
        public boolean visible() {
            return box.width() > 0f && box.height() > 0f;
        }

        public float area() {
            return Math.max(0f, box.width()) * Math.max(0f, box.height());
        }

        @Override
        public String toString() {
            return String.format("\"%s\" [%.0f,%.0f %.0fx%.0f]", what, wanted.left, wanted.top,
                    wanted.width(), wanted.height());
        }
    }

    private final List<Run> runs = new ArrayList<>();

    public OeplInkRecorder(final Bitmap bitmap) {
        super(bitmap);
    }

    public List<Run> runs() {
        return runs;
    }

    /** Only the text that reaches the panel, which is what may not collide. */
    public List<Run> textRuns() {
        final List<Run> only = new ArrayList<>();
        for (final Run run : runs) {
            if (run.text && run.visible()) {
                only.add(run);
            }
        }
        return only;
    }

    // ------------------------------------------------------------------ text

    @Override
    public void drawText(final String text, final float x, final float y, final Paint paint) {
        recordText(text, x, y, paint);
        super.drawText(text, x, y, paint);
    }

    @Override
    public void drawText(final String text, final int start, final int end, final float x,
                         final float y, final Paint paint) {
        recordText(text == null ? null : text.substring(start, end), x, y, paint);
        super.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawText(final CharSequence text, final int start, final int end, final float x,
                         final float y, final Paint paint) {
        recordText(text == null ? null : text.subSequence(start, end).toString(), x, y, paint);
        super.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawText(final char[] text, final int index, final int count, final float x,
                         final float y, final Paint paint) {
        recordText(text == null ? null : new String(text, index, count), x, y, paint);
        super.drawText(text, index, count, x, y, paint);
    }

    // ------------------------------------------------------------------ shapes

    @Override
    public void drawRect(final float left, final float top, final float right, final float bottom,
                         final Paint paint) {
        record(new RectF(left, top, right, bottom), "rect", paint, false);
        super.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public void drawRect(final RectF rect, final Paint paint) {
        record(new RectF(rect), "rect", paint, false);
        super.drawRect(rect, paint);
    }

    @Override
    public void drawLine(final float x0, final float y0, final float x1, final float y1,
                         final Paint paint) {
        record(new RectF(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1)),
                "line", paint, false);
        super.drawLine(x0, y0, x1, y1, paint);
    }

    @Override
    public void drawCircle(final float cx, final float cy, final float radius, final Paint paint) {
        record(new RectF(cx - radius, cy - radius, cx + radius, cy + radius), "circle", paint,
                false);
        super.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public void drawRoundRect(final RectF rect, final float rx, final float ry,
                              final Paint paint) {
        record(new RectF(rect), "roundrect", paint, false);
        super.drawRoundRect(rect, rx, ry, paint);
    }

    /**
     * Paths matter as much as the rest: a pictogram drawn as a shape rather than set from a
     * font goes through here, and one that was not recorded could reach over the panel edge
     * without the audit ever noticing.
     */
    @Override
    public void drawPath(final Path path, final Paint paint) {
        record(strokeBounds(path, paint), "path", paint, false);
        super.drawPath(path, paint);
    }

    /** What a path covers once drawn: its outline, widened by half the stroke on either side. */
    private static RectF strokeBounds(final Path path, final Paint paint) {
        final RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        if (paint != null && paint.getStyle() != Paint.Style.FILL) {
            final float half = paint.getStrokeWidth() / 2f;
            bounds.inset(-half, -half);
        }
        return bounds;
    }

    /**
     * An arc covers the part of its oval that is actually swept, not the whole oval. Recording
     * the oval instead made every half circle look twice as tall as the ink it leaves, which
     * is the difference between a pictogram that sits beside its label and one that is
     * reported as lying on top of it.
     */
    @Override
    public void drawArc(final RectF oval, final float startAngle, final float sweepAngle,
                        final boolean useCenter, final Paint paint) {
        final Path path = new Path();
        if (useCenter) {
            path.moveTo(oval.centerX(), oval.centerY());
        }
        path.addArc(oval, startAngle, sweepAngle);
        if (useCenter) {
            path.close();
        }
        record(strokeBounds(path, paint), "arc", paint, false);
        super.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public void drawBitmap(final Bitmap bitmap, final float left, final float top,
                           final Paint paint) {
        record(new RectF(left, top, left + bitmap.getWidth(), top + bitmap.getHeight()),
                "bitmap", null, false);
        super.drawBitmap(bitmap, left, top, paint);
    }

    @Override
    public void drawBitmap(final Bitmap bitmap, final Rect src, final RectF dst,
                           final Paint paint) {
        record(new RectF(dst), "bitmap", null, false);
        super.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(final Bitmap bitmap, final Rect src, final Rect dst,
                           final Paint paint) {
        record(new RectF(dst), "bitmap", null, false);
        super.drawBitmap(bitmap, src, dst, paint);
    }

    // ------------------------------------------------------------------ the measuring

    private void recordText(final String text, final float x, final float y, final Paint paint) {
        if (text == null || text.trim().isEmpty() || paint == null) {
            return;
        }
        final Rect ink = new Rect();
        paint.getTextBounds(text, 0, text.length(), ink);
        if (ink.width() <= 0 || ink.height() <= 0) {
            return; // a space, or a glyph the face does not have
        }
        float left = x;
        if (paint.getTextAlign() == Paint.Align.CENTER) {
            left -= paint.measureText(text) / 2f;
        } else if (paint.getTextAlign() == Paint.Align.RIGHT) {
            left -= paint.measureText(text);
        }
        record(new RectF(left + ink.left, y + ink.top, left + ink.right, y + ink.bottom),
                text, paint, true);
    }

    private void record(final RectF box, final String what, final Paint paint,
                        final boolean text) {
        if (!text && paint != null && paint.getStyle() != Paint.Style.FILL) {
            // a stroke straddles the line it is drawn on
            final float half = Math.max(1f, paint.getStrokeWidth()) / 2f;
            box.inset(-half, -half);
        }
        final RectF wanted = new RectF(box);
        // whatever a clip holds back never reaches the panel, so it is not ink - but it is
        // still what the layout asked for, and that is recorded separately
        final Rect clip = new Rect();
        if (getClipBounds(clip) && !box.intersect(new RectF(clip))) {
            box.set(0f, 0f, 0f, 0f);
        }
        final Matrix matrix = getMatrix();
        if (matrix != null && !matrix.isIdentity()) {
            matrix.mapRect(box);
            matrix.mapRect(wanted);
        }
        if (wanted.width() <= 0f || wanted.height() <= 0f) {
            return;
        }
        runs.add(new Run(box, wanted, what, text));
    }
}
