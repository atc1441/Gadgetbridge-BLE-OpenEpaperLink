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
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;

/**
 * Draws the card: a title bar, the chosen content, and a status strip. Nothing here is measured
 * in pixels - every size derives from the panel - so the same code covers a 152x152 price tag
 * and a 960x768 panel, in either orientation.
 */
public class CardRenderer {
    public static final int NO_VALUE = Integer.MIN_VALUE;

    /** How the frame around the content looks. */
    public enum Frame {
        /** Solid title bar and status strip. Highest contrast, reads from furthest away. */
        BARS,
        /** No fills, just hairlines. Leaves the most room for the content. */
        CLEAN,
        /** An oversized inverted title block, with the status folded into it. */
        BOLD
    }

    public static class Options {
        public Frame frame = Frame.BARS;
        public int accent = OEPLImageCodec.RED;
        public boolean fahrenheit = false;
        public int batteryMillivolts = NO_VALUE;
        public int panelTemperature = NO_VALUE;
        public boolean showFooter = true;
        /** Shown when the chosen content has nothing to say. */
        public String emptyHint = "no data";
    }

    private final Options options;
    private final CardStyle style;

    public CardRenderer(final Options options) {
        this.options = options;
        this.style = new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                options.accent, options.fahrenheit);
    }

    /**
     * @param rotation degrees the finished card is turned by to land in the panel buffer
     * @return a bitmap of exactly bufferWidth x bufferHeight
     */
    public Bitmap render(final int bufferWidth, final int bufferHeight, final int rotation,
                         final CardContent content) {
        final boolean quarterTurn = rotation == 90 || rotation == 270;
        final int w = quarterTurn ? bufferHeight : bufferWidth;
        final int h = quarterTurn ? bufferWidth : bufferHeight;

        final Bitmap card = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(card);
        canvas.drawColor(style.paper);

        if (content.fullBleed()) {
            // the content owns every pixel, so no title bar and no status strip
            final RectF whole = new RectF(0, 0, w, h);
            if (content.hasData()) {
                drawClipped(canvas, content, whole);
            } else {
                drawHint(canvas, whole);
            }
            return finish(card, rotation, w, h, bufferWidth, bufferHeight);
        }

        drawHeader(canvas, w, h, content.title());
        drawFooter(canvas, w, h);
        final RectF body = bodyArea(w, h);

        if (content.hasData()) {
            drawClipped(canvas, content, body);
        } else {
            drawHint(canvas, body);
        }

        return finish(card, rotation, w, h, bufferWidth, bufferHeight);
    }

    /**
     * Draws a content with the canvas clipped to the area it was given. A layout that misjudges
     * its space then loses a letter instead of painting over the title bar, over the status
     * strip, or over the panel edge - which is what a tag shows as a smear of ink.
     */
    private void drawClipped(final Canvas canvas, final CardContent content, final RectF area) {
        canvas.save();
        try {
            canvas.clipRect(area);
            content.draw(canvas, style, area);
        } finally {
            canvas.restore();
        }
    }

    /** Turns the finished card into the panel buffer. */
    private Bitmap finish(final Bitmap card, final int rotation, final int w, final int h,
                          final int bufferWidth, final int bufferHeight) {
        if (rotation == 0) {
            return card;
        }
        final Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        final Bitmap rotated = Bitmap.createBitmap(card, 0, 0, w, h, matrix, true);
        if (rotated.getWidth() == bufferWidth && rotated.getHeight() == bufferHeight) {
            return rotated;
        }
        // guard against an off by one from the rotation matrix on odd dimensions
        final Bitmap exact = Bitmap.createBitmap(bufferWidth, bufferHeight, Bitmap.Config.ARGB_8888);
        final Canvas fix = new Canvas(exact);
        fix.drawColor(style.paper);
        fix.drawBitmap(rotated, 0, 0, null);
        return exact;
    }

    /**
     * The rectangle a framed content is given: what is left between the title bar and the
     * status strip. Measured rather than drawn, so that the design audit can ask for it without
     * a panel, and so that the drawing code and the check can never disagree about it.
     */
    public RectF bodyArea(final int w, final int h) {
        final float pad = Math.max(2f, h * 0.02f);
        return new RectF(0, headerHeight(w, h) + pad, w, h - footerHeight(w, h) - pad);
    }

    /** How much of the panel the title bar takes, including the rule under a clean one. */
    private float headerHeight(final int w, final int h) {
        if (options.frame == Frame.CLEAN) {
            return Math.max(9f, h * 0.13f) + Math.max(1f, h * 0.008f);
        }
        return Math.max(10f, h * (options.frame == Frame.BOLD ? 0.26f : 0.155f));
    }

    /** How much the status strip takes; nothing at all on a short panel or a bold frame. */
    private float footerHeight(final int w, final int h) {
        if (!options.showFooter || h < 96 || options.frame == Frame.BOLD) {
            return 0f; // the bold frame already carries the status in its title block
        }
        return Math.max(9f, h * 0.105f);
    }

    private float drawHeader(final Canvas canvas, final int w, final int h,
                             @Nullable final String title) {
        final float pad = Math.max(2f, w * 0.018f);
        final String stamp = style.time(System.currentTimeMillis());

        if (options.frame == Frame.CLEAN) {
            final float headerH = Math.max(9f, h * 0.13f);
            final Paint time = style.paint(style.ink, false, headerH * 0.62f);
            final float stampW = time.measureText(stamp);
            canvas.drawText(stamp, w - stampW - pad, headerH * 0.80f, time);
            if (title != null && !title.isEmpty()) {
                final Paint text = style.paint(style.ink, true, headerH * 0.78f);
                style.fit(text, title, w - stampW - pad * 3);
                style.drawEllipsised(canvas, title, pad, headerH * 0.82f,
                        w - stampW - pad * 3, text);
            }
            final Paint rule = new Paint();
            rule.setColor(style.ink);
            canvas.drawRect(pad, headerH, w - pad, headerH + Math.max(1f, h * 0.008f), rule);
            return headerH + Math.max(1f, h * 0.008f);
        }

        final boolean bold = options.frame == Frame.BOLD;
        final float headerH = headerHeight(w, h);
        final Paint bar = new Paint();
        bar.setColor(style.ink);
        canvas.drawRect(0, 0, w, headerH, bar);

        final Paint time = style.paint(style.paper, false, headerH * (bold ? 0.30f : 0.60f));
        final float stampW = time.measureText(stamp);
        canvas.drawText(stamp, w - stampW - pad, headerH * (bold ? 0.42f : 0.76f), time);

        if (title != null && !title.isEmpty()) {
            final Paint text = style.paint(style.paper, true, headerH * (bold ? 0.44f : 0.74f));
            style.fit(text, title, w - stampW - pad * 3);
            style.drawEllipsised(canvas, title, pad, headerH * (bold ? 0.46f : 0.78f),
                    w - stampW - pad * 3, text);
        }
        if (bold) {
            // the status line lives inside the block instead of taking its own strip
            final Paint sub = style.paint(style.paper, false, headerH * 0.26f);
            final String info = statusLine();
            if (!info.isEmpty()) {
                canvas.drawText(info, pad, headerH * 0.86f, sub);
            }
        }
        return headerH;
    }

    /** Battery and panel temperature, as far as they are known. */
    private String statusLine() {
        final StringBuilder sb = new StringBuilder();
        if (options.batteryMillivolts != NO_VALUE) {
            sb.append(String.format(Locale.ROOT, "%.2f V", options.batteryMillivolts / 1000f));
        }
        if (options.panelTemperature != NO_VALUE) {
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(options.panelTemperature).append(style.degree());
        }
        return sb.toString();
    }

    private float drawFooter(final Canvas canvas, final int w, final int h) {
        final float footerH = footerHeight(w, h);
        if (footerH <= 0f) {
            return 0f;
        }
        final boolean clean = options.frame == Frame.CLEAN;
        final Paint bar = new Paint();
        bar.setColor(style.ink);
        if (clean) {
            canvas.drawRect(Math.max(2f, w * 0.018f), h - footerH,
                    w - Math.max(2f, w * 0.018f), h - footerH + Math.max(1f, h * 0.006f), bar);
        } else {
            canvas.drawRect(0, h - footerH, w, h, bar);
        }

        final float pad = Math.max(2f, w * 0.018f);
        final Paint text = style.paint(clean ? style.ink : style.paper, false, footerH * 0.70f);
        final float baseline = h - footerH * 0.22f;

        String left = statusLine();
        if (text.measureText(left) > w * 0.55f && options.batteryMillivolts != NO_VALUE) {
            left = String.format(Locale.ROOT, "%.2f V", options.batteryMillivolts / 1000f);
        }
        String date = new SimpleDateFormat("EE d.M.", Locale.getDefault()).format(new Date());
        if (text.measureText(left) + text.measureText(date) + pad * 3 > w) {
            date = ""; // a narrow panel keeps the battery reading and drops the date
        }
        if (!left.isEmpty()) {
            canvas.drawText(left, pad, baseline, text);
        }
        if (!date.isEmpty()) {
            canvas.drawText(date, w - text.measureText(date) - pad, baseline, text);
        }
        return footerH;
    }

    private void drawHint(final Canvas canvas, final RectF area) {
        final Paint p = style.paint(style.ink, false, Math.max(8f, area.height() * 0.18f));
        style.fit(p, options.emptyHint, area.width() * 0.9f);
        canvas.drawText(options.emptyHint,
                area.left + (area.width() - p.measureText(options.emptyHint)) / 2f,
                area.top + area.height() * 0.55f, p);
    }
}
