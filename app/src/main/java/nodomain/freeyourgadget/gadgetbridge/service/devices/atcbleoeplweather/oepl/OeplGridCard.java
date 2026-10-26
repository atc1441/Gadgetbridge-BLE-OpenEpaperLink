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

import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The dashboard grid: a headline with the date, then one framed tile per value, each a symbol
 * beside the figure and the caption underneath.
 * <p>
 * Every tile holds the same three things in the same places, which is what makes a wall of them
 * readable in passing. The figures are all set in one size, taken from the tile that can hold
 * the least: four numbers each as large as its own tile allows cannot be compared at a glance,
 * and comparing them is the only reason to put them side by side.
 * <p>
 * How many tiles there are is decided by the panel, not by how many values were collected. A
 * tile too small for a figure that survives the panel's two or three colours is worse than no
 * tile at all - thin strokes come out of the quantiser as nothing - so a small tag shows the
 * first four values large rather than twelve as grey dust. The order is the collector's, so
 * what gets dropped is always what mattered least.
 */
public class OeplGridCard extends OeplCard {

    /**
     * The most tiles a panel of a given size is worth dividing into.
     * <p>
     * It used to be eight whatever the panel was, which is right for a price tag and wrong
     * for a seven inch display on a wall: eight tiles across 960 by 672 are the size of a
     * playing card each, holding one number and a word. A tile is comfortable at about 150 by
     * 100 - a symbol, a figure a room can be read from, and a caption - so that is what the
     * panel is divided by, and the result is capped at two dozen, beyond which a wall of
     * tiles stops being read and starts being scanned.
     */
    public static int maxTiles(final float width, final float height) {
        final int cols = Math.max(1, (int) (width / 150f));
        final int rows = Math.max(1, (int) (height / 100f));
        return Math.max(8, Math.min(24, cols * rows));
    }
    /** Below this a tile cannot hold a figure that survives the quantiser. */
    private static final float MIN_TILE_HEIGHT = 32f;
    private static final float MIN_TILE_WIDTH = 68f;

    private final List<OeplReading> readings;
    @Nullable
    private final String headline;

    public OeplGridCard(final List<OeplReading> readings, @Nullable final String headline) {
        super(true);
        this.readings = readings;
        this.headline = headline;
    }

    @Override
    public boolean hasData() {
        return readings != null && !readings.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (readings == null || readings.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, Math.min(c.width, c.height) * 0.02f);
        float top = 0f;

        // The headline is the first thing to go: on a price tag the room it takes is a
        // whole row of tiles, and the tiles are what the card is for. Eight pixels is the
        // floor everywhere on this card - a line thinner than that leaves grey dust on the
        // panel once the quantiser has been over it, and dust is worse than a blank.
        final float headSize = c.sizeForHeight(Math.min(c.height * 0.12f, c.width * 0.065f),
                c.height * 0.16f, 12);
        if (headSize > 0f && c.height >= 110f) {
            final String stamp = stamp(c, headSize, c.width - pad * 2f);
            c.rowEnds(headline, stamp, 0f, headSize, c.ink, c.ink, pad, c.width - pad, pad * 2f);
            top = c.lineHeight(headSize) * 1.05f;
            c.line(pad, Math.round(top), c.width - pad, Math.round(top), c.ink, 1f);
            top += pad;
        }

        final float gridHeight = c.height - top;
        final int most = maxTiles(c.width, gridHeight);
        final int[] grid = grid(readings.size(), c.width, gridHeight);
        final int cols = grid[0];
        final int rows = grid[1];
        final int shown = Math.min(Math.min(readings.size(), most), cols * rows);
        if (shown == 0) {
            return;
        }
        final float tileW = c.width / cols;
        final float tileH = gridHeight / rows;
        final float inset = Math.max(1f, Math.min(tileW, tileH) * 0.05f);
        final float w = tileW - inset * 2f;
        final float h = tileH - inset * 2f;

        // No caption rather than an unreadable one: under about eight pixels the panel's
        // quantiser leaves grey dust where the word was, and the room is better spent on the
        // figure. sizeForHeight answers 0 when not even the floor fits.
        final float labelSize = c.sizeForHeight(Math.min(h * 0.22f, 14f), h * 0.26f, OeplCanvas.READABLE);
        final float labelHeight = labelSize > 0f ? c.lineHeight(labelSize) : 0f;

        // One size for every figure on the card, taken from the figure that has the least
        // room - but only from the figures. Not every value is one: a Home Assistant state
        // can be a sentence ("weit offen seit 12:40"), and letting that tile set the size
        // put every temperature on the panel in six pixels. The sentences are cut to fit
        // instead, which costs the end of one caption rather than the whole card.
        float valueSize = Float.MAX_VALUE;
        float anySize = Float.MAX_VALUE;
        for (int i = 0; i < shown; i++) {
            final OeplReading reading = readings.get(i);
            final float band = band(reading, h, labelHeight);
            final RectF box = valueBox(c, reading, w, band);
            final String value = reading.display(fahrenheit);
            final float fits = c.fillingSize(value, box.width(), band * 0.92f);
            anySize = Math.min(anySize, fits);
            if (isFigure(value)) {
                valueSize = Math.min(valueSize, fits);
            }
        }
        if (valueSize == Float.MAX_VALUE) {
            // Nothing on this card is a figure: every tile holds a sentence. Sharing the
            // smallest of their sizes would set the whole panel in four pixels, which it
            // shows as nothing at all, so they share a size that can still be read and are
            // cut to their tiles instead.
            valueSize = anySize;
        }
        // and whatever it came out as, never thinner than the panel can show: a figure that
        // only fits at five pixels is a figure nobody reads, so it is set at the floor and cut
        valueSize = Math.max(valueSize, OeplCanvas.READABLE);

        for (int i = 0; i < shown; i++) {
            final float left = (i % cols) * tileW + inset;
            final float tileTop = top + (i / cols) * tileH + inset;
            tile(c, readings.get(i), left, tileTop, w, h, valueSize, labelSize, labelHeight);
        }
    }

    /**
     * The date and time beside the headline, in the longest form that still fits at the size
     * the headline is set in. A narrow panel gets the clock alone, and one too narrow even for
     * that gets nothing: the alternative is a stamp squeezed into four pixels of grey, which
     * the panel then renders as dirt.
     */
    private String stamp(final OeplCanvas c, final float size, final float width) {
        for (final String pattern : new String[]{"EE dd.MM. HH:mm", "dd.MM. HH:mm", "HH:mm"}) {
            final String stamp = new SimpleDateFormat(pattern, Locale.getDefault())
                    .format(new Date());
            if (c.measure(stamp, size) <= width * 0.55f) {
                return stamp;
            }
        }
        return null;
    }

    /**
     * Whether a value is a figure - a number with a unit at most - rather than prose. Only
     * the figures decide how large the figures are set; see the loop above.
     */
    public static boolean isFigure(final String value) {
        if (value == null) {
            return false;
        }
        final String trimmed = value.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 11
                && trimmed.split("\\s+").length <= 2;
    }

    /** The height the symbol and the figure share: everything above the bar and the caption. */
    private float band(final OeplReading reading, final float h, final float labelHeight) {
        final float inner = inner(h);
        final float bar = barHeight(reading, h);
        return Math.max(6f, h - inner * 2f - labelHeight
                - (bar > 0f ? bar + inner * 0.5f : 0f));
    }

    /** Where the figure goes: to the right of the symbol, which is drawn in the same band. */
    private RectF valueBox(final OeplCanvas c, final OeplReading reading, final float w,
                           final float band) {
        final float inner = inner(band);
        final float iconH = iconHeight(reading, w, band);
        final float iconW = iconH > 0f ? reading.iconWidth(c, iconH) : 0f;
        final float left = inner + iconW + (iconW > 0f ? inner : 0f);
        return new RectF(left, 0f, Math.max(left + 6f, w - inner), band);
    }

    private float iconHeight(final OeplReading reading, final float w, final float band) {
        return reading.hasIcon() ? Math.min(band * 0.85f, w * 0.3f) : 0f;
    }

    private float inner(final float reference) {
        return Math.max(2f, reference * 0.1f);
    }

    /** A bar is only drawn for a share, and only where the tile has a line to spare for it. */
    private float barHeight(final OeplReading reading, final float h) {
        return reading.percent >= 0 && h > 38f ? Math.max(2.5f, h * 0.08f) : 0f;
    }

    private void tile(final OeplCanvas c, final OeplReading reading, final float x,
                      final float y, final float w, final float h, final float valueSize,
                      final float labelSize, final float labelHeight) {
        final int color = reading.accent ? c.accent : c.ink;
        final float radius = Math.min(w, h) * 0.14f;
        // from the smaller side of the tile. Taken from the height alone, a tile four times
        // taller than it is wide got a thirty eight pixel margin on a panel eighty wide -
        // which left the caption no column at all and so no width to be cut to.
        final float inner = inner(Math.min(w, h));
        c.canvas.drawRoundRect(new RectF(x, y, x + w, y + h), radius, radius, outline(c));

        final float band = band(reading, h, labelHeight);
        final float bandCentre = y + inner + band / 2f;
        final float iconH = iconHeight(reading, w, band);
        if (iconH > 0f) {
            final float iconW = reading.iconWidth(c, iconH);
            c.canvas.save();
            reading.drawIcon(c, x + inner(band) + iconW / 2f, bandCentre, iconH, color);
            c.canvas.restore();
        }
        final RectF box = valueBox(c, reading, w, band);
        final String value = reading.display(fahrenheit);
        // A value that is a sentence rather than a figure - an appointment's title, a state
        // Home Assistant words - is set across the lines the tile has room for instead of
        // being cut. On a large panel a tile is two hundred pixels tall, and leaving "Tuev
        // weitermelden" as "Tuev weiterm..." in that much room is the card wasting what it
        // was given. Figures are untouched: they stay one line, all of them the same size.
        final String[] lines = c.measure(value, valueSize) > box.width()
                ? paragraph(c, value, box.width(), band, valueSize) : null;
        if (lines != null) {
            final float size = sentenceSize(c, value, box.width(), valueSize);
            final float lineHeight = c.lineHeight(size);
            float top = bandCentre - lineHeight * lines.length / 2f;
            for (final String one : lines) {
                c.textClipped(one, x + (box.left + box.right) / 2f, top, size,
                        OeplCanvas.CENTER, color, box.width());
                top += lineHeight;
            }
        } else {
            c.textFillingAtClipped(value, x + (box.left + box.right) / 2f,
                    bandCentre, valueSize, color, box.width());
        }

        final float bar = barHeight(reading, h);
        if (bar > 0f) {
            // the share as a bar rather than a second number: it is the one thing a tile can
            // show that a figure cannot, and it reads from further away than the figure does
            final float barY = y + h - inner - labelHeight - bar;
            final float barW = w - inner * 2f;
            c.strokeRect(x + inner, barY, barW, bar, c.ink, 1f);
            final float filled = (barW - 2f) * Math.min(100, Math.max(0, reading.percent)) / 100f;
            if (filled >= 1f) {
                c.fillRect(x + inner + 1f, barY + 1f, filled, bar - 2f, color);
            }
        }
        if (labelSize > 0f) {
            c.textClipped(reading.label, x + inner, y + h - inner - labelHeight, labelSize,
                    OeplCanvas.LEFT, c.ink, w - inner * 2f);
        }
    }

    /**
     * The size a sentence is set in: small enough that its longest word fits the column, and
     * never under what the panel can show. A figure keeps the size every other figure has;
     * only prose is allowed to be smaller, because only prose can be made to fit that way.
     */
    private float sentenceSize(final OeplCanvas c, final String text, final float maxWidth,
                               final float valueSize) {
        float size = valueSize;
        int guard = 0;
        while (size > 9f && guard++ < 64 && longestWord(c, text, size) > maxWidth) {
            size = Math.max(9f, size * 0.94f);
        }
        return size;
    }

    private float longestWord(final OeplCanvas c, final String text, final float size) {
        float longest = 0f;
        for (final String word : text.trim().split("\\s+")) {
            longest = Math.max(longest, c.measure(word, size));
        }
        return longest;
    }

    /**
     * A sentence broken across as many lines as the tile has room for, or null when it is one
     * word - a machine has no business hyphenating a word, so that one is cut as before.
     */
    private String[] paragraph(final OeplCanvas c, final String text, final float maxWidth,
                               final float band, final float valueSize) {
        final String trimmed = text == null ? "" : text.trim();
        if (!trimmed.contains(" ")) {
            return null;
        }
        final float size = sentenceSize(c, trimmed, maxWidth, valueSize);
        final int room = (int) (band / c.lineHeight(size));
        if (room < 2) {
            return null; // no room for a second line, so nothing is gained by breaking it
        }
        final java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (final String word : trimmed.split("\\s+")) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (c.measure(candidate, size) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
                if (lines.size() == room - 1) {
                    break;
                }
            }
        }
        if (line.length() > 0 && lines.size() < room) {
            lines.add(line.toString());
        }
        return lines.size() < 2 ? null : lines.toArray(new String[0]);
    }

    private Paint outline(final OeplCanvas c) {
        final Paint paint = new Paint();
        paint.setColor(c.ink);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        return paint;
    }

    /**
     * How many columns and rows the panel is divided into - and with that, how many values are
     * shown at all. As many tiles as the panel can hold at a readable size, which is a very
     * different rule from "as many as there are values": twelve values on a shelf label are
     * twelve illegible boxes, and the four that matter most are a dashboard.
     */
    static int[] grid(final int count, final float width, final float height) {
        final int wanted = Math.min(count, maxTiles(width, height));
        int bestCols = 0;
        int bestRows = 0;
        int bestTiles = 0;
        float bestScore = Float.MAX_VALUE;
        for (int cols = 1; cols <= wanted; cols++) {
            for (int rows = 1; rows * cols <= wanted + cols - 1; rows++) {
                final float tileW = width / cols;
                final float tileH = height / rows;
                if (tileW < MIN_TILE_WIDTH || tileH < MIN_TILE_HEIGHT) {
                    continue;
                }
                final int tiles = Math.min(wanted, cols * rows);
                // a tile holds a symbol beside a figure, so it wants to be about half again
                // as wide as it is high; holes in the last row count against an arrangement
                final float score = Math.abs((float) Math.log((tileW / tileH) / 1.5f))
                        + (cols * rows - tiles) * 0.3f;
                if (tiles > bestTiles || (tiles == bestTiles && score < bestScore)) {
                    bestTiles = tiles;
                    bestScore = score;
                    bestCols = cols;
                    bestRows = rows;
                }
            }
        }
        if (bestCols == 0) {
            // a panel too small for even one tile of the wanted size still gets one tile
            return new int[]{1, 1};
        }
        return new int[]{bestCols, bestRows};
    }
}
