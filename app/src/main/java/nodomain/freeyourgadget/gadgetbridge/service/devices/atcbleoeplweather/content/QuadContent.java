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

import java.util.ArrayList;
import java.util.List;

/**
 * The panel divided into quarters, with any other card in each of them. This is not a design of
 * its own - it is the mechanism that lets somebody build one: weather top left, the agenda
 * beside it, the news along the bottom, whatever the room this tag hangs in needs.
 * <p>
 * A quarter is drawn by the very same content that would fill the whole panel. Every card
 * already lays itself out as fractions of the area it is handed, which is what makes this work
 * at all: the pieces shrink, they do not get cut off.
 * <p>
 * How many quarters there are is decided by how many were configured, not by the number four.
 * Two contents split the panel along its longer edge, three put one across the full width and
 * two beside each other, and a panel too small to hold the pieces readably shows fewer of them
 * rather than a grid of fragments.
 */
public class QuadContent implements CardContent {

    /**
     * The narrowest pane a card still holds. It is not a matter of taste: the cards that
     * normally sit inside the Gadgetbridge frame set their type from the height they are given
     * and then write a place name across the top, which runs out of a pane much narrower than
     * the smallest whole panel they were ever designed for. Below this, a quarter is dropped
     * and the remaining ones get the room.
     */
    private static final float MIN_PANE_WIDTH = 118f;
    private static final float MIN_PANE_HEIGHT = 56f;

    private final List<CardContent> parts;

    public QuadContent(final List<CardContent> parts) {
        this.parts = new ArrayList<>();
        if (parts != null) {
            for (final CardContent part : parts) {
                if (part != null && part.hasData() && this.parts.size() < 4) {
                    this.parts.add(part);
                }
            }
        }
    }

    @Nullable
    @Override
    public String title() {
        return null;
    }

    @Override
    public boolean fullBleed() {
        return true;
    }

    @Override
    public boolean hasData() {
        return !parts.isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (parts.isEmpty()) {
            return;
        }
        List<RectF> cells = null;
        for (int count = parts.size(); count >= 1 && cells == null; count--) {
            cells = cells(area, count);
        }
        if (cells == null) {
            // not even one pane of the wanted size: the first card gets the panel as it is,
            // which is what every card is built to cope with anyway
            parts.get(0).draw(canvas, style, area);
            return;
        }
        for (int i = 0; i < cells.size() && i < parts.size(); i++) {
            pane(canvas, style, parts.get(i), cells.get(i));
        }
        rules(canvas, style, area, cells);
    }

    /**
     * The panes for a given number of cards, or null when this panel cannot hold that many.
     * <p>
     * The arrangement is chosen by the shape a pane ends up with rather than by the number of
     * cards: four cards on a tall narrow tag are four rows, on a wide one a grid of quarters,
     * and the same four on a shelf label are two - which is why this may answer that it cannot
     * be done and let the caller ask again for fewer.
     */
    @Nullable
    private List<RectF> cells(final RectF area, final int count) {
        final float gap = Math.max(3f, Math.min(area.width(), area.height()) * 0.025f);
        int bestCols = 0;
        int bestRows = 0;
        float best = Float.MAX_VALUE;
        for (int cols = 1; cols <= count; cols++) {
            final int rows = (count + cols - 1) / cols;
            final float paneW = area.width() / cols - gap;
            final float paneH = area.height() / rows - gap;
            if (paneW < MIN_PANE_WIDTH || paneH < MIN_PANE_HEIGHT) {
                continue;
            }
            // no holes: a grid with an empty cell in it wastes a quarter of the panel, and the
            // only arrangement worth that is the three card one further down, which fills the
            // gap by giving the first card the whole row
            if (cols * rows != count && !(count == 3 && cols == 2)) {
                continue;
            }
            // a pane wants to be about a third wider than it is high
            final float score = Math.abs((float) Math.log((paneW / paneH) / 1.3f));
            if (score < best) {
                best = score;
                bestCols = cols;
                bestRows = rows;
            }
        }
        if (bestCols == 0) {
            return null;
        }
        final List<RectF> cells = new ArrayList<>(count);
        if (count == 3 && bestCols == 2) {
            // three into a two by two grid leaves a hole; giving the first card the whole top
            // row instead uses the panel and says which of the three was put first
            final float midY = area.centerY();
            final float midX = area.centerX();
            cells.add(new RectF(area.left, area.top, area.right, midY - gap));
            cells.add(new RectF(area.left, midY + gap, midX - gap, area.bottom));
            cells.add(new RectF(midX + gap, midY + gap, area.right, area.bottom));
            return cells;
        }
        final float cellW = area.width() / bestCols;
        final float cellH = area.height() / bestRows;
        for (int i = 0; i < count; i++) {
            final float left = area.left + (i % bestCols) * cellW;
            final float top = area.top + (i / bestCols) * cellH;
            cells.add(new RectF(left + (i % bestCols == 0 ? 0f : gap), top
                    + (i < bestCols ? 0f : gap), left + cellW - gap, top + cellH - gap));
        }
        return cells;
    }

    /**
     * One quarter. A card that lays out a whole panel draws straight into it; one that normally
     * sits inside the Gadgetbridge frame gets the heading that frame would have given it,
     * because without it a quarter showing three times and a date says nothing about which of
     * them is which.
     */
    private void pane(final Canvas canvas, final CardStyle style, final CardContent part,
                      final RectF cell) {
        final String heading = part.fullBleed() ? null : part.title();
        // A pane heading has to earn its line. On a quarter of a price tag it comes out at
        // seven pixels, which the panel's quantiser leaves as a grey smear - and it takes a
        // sixth of the pane away from the card that was actually asked for. Nine pixels is
        // where a bold line still survives the panel; under that the heading is left out and
        // the content gets the whole pane.
        final float headSize = Math.min(cell.height() * 0.12f, cell.width() * 0.09f);
        if (heading == null || heading.isEmpty() || cell.height() < 60f || headSize < 9f) {
            part.draw(canvas, style, cell);
            return;
        }
        final Paint paint = style.paint(style.ink, true, headSize);
        // hung from its own ink rather than from the face's ascent: the top row of panes is at
        // the top edge of the panel, where there is nothing above the line to give way
        style.drawEllipsised(canvas, heading, cell.left,
                style.baselineBelow(cell.top, paint, heading), cell.width(), paint);
        final float used = style.lineHeight(paint) * 1.05f;
        part.draw(canvas, style, new RectF(cell.left, cell.top + used, cell.right,
                cell.bottom));
    }

    /** Hairlines in the gaps, so that the quarters read as panes and not as one busy picture. */
    private void rules(final Canvas canvas, final CardStyle style, final RectF area,
                       final List<RectF> cells) {
        if (cells.size() < 2) {
            return;
        }
        final Paint rule = new Paint();
        rule.setColor(style.ink);
        rule.setStrokeWidth(1f);
        for (int i = 1; i < cells.size(); i++) {
            final RectF previous = cells.get(i - 1);
            final RectF cell = cells.get(i);
            if (cell.top > previous.bottom) {
                final float y = Math.round((previous.bottom + cell.top) / 2f);
                canvas.drawLine(area.left, y, area.right, y, rule);
            } else if (cell.left > previous.right) {
                final float x = Math.round((previous.right + cell.left) / 2f);
                canvas.drawLine(x, cell.top, x, cell.bottom, rule);
            }
        }
    }
}
