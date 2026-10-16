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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The day's appointments as a departure board: a reversed strip along the top, a column of
 * times, a rule under every line and the day written across the rows where it changes. It is
 * the same information the appointment list carries, read the way a station board is read -
 * down the times first, and across only for the one that matters.
 * <p>
 * The time column is as wide as its widest entry and no wider, and the rule under a row is
 * drawn at the row's own bottom edge, so the board holds together from a price tag to a
 * notice board.
 */
public class OeplBoardCard extends OeplCard {

    private final List<CalendarEvent> events;
    private final int maxEntries;
    private final String caption;

    public OeplBoardCard(final List<CalendarEvent> events, final int maxEntries,
                         final String caption) {
        super(true);
        this.events = events;
        this.maxEntries = maxEntries;
        this.caption = caption;
    }

    @Override
    public boolean hasData() {
        return events != null && !events.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.02f);

        // the strip across the top is the board's header; it is reversed, so its text is drawn
        // in the paper colour and its height has to hold a whole line
        final float headSize = c.sizeForHeight(Math.min(h * 0.16f, w * 0.1f), h * 0.22f, 7f);
        float top = Math.max(1f, h * 0.02f);
        if (headSize > 0f) {
            final float strip = c.lineHeight(headSize) * 1.1f;
            c.fillRect(0f, 0f, w, strip, c.ink);
            c.rowEnds(caption, new SimpleDateFormat("EE d.M.", Locale.getDefault())
                            .format(new Date()), (strip - c.lineHeight(headSize)) / 2f,
                    headSize, c.paper, c.paper, pad, w - pad, pad * 2f);
            top = strip + Math.max(1f, h * 0.02f);
        }

        final float rowSize = c.sizeForHeight(Math.min(h * 0.13f, w * 0.085f), h * 0.2f, 6f);
        if (rowSize <= 0f) {
            return;
        }
        final float rowHeight = c.lineHeight(rowSize) * 1.25f;
        final int fits = (int) ((h - top) / rowHeight);
        final int count = Math.min(Math.min(maxEntries, fits), events.size());
        if (count <= 0) {
            return;
        }

        float timeColumn = 0f;
        for (int i = 0; i < count; i++) {
            timeColumn = Math.max(timeColumn, c.measure(time(events.get(i)), rowSize));
        }
        timeColumn = Math.min(timeColumn, w * 0.36f);
        final float titleLeft = pad + timeColumn + pad * 2f;
        final long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            final CalendarEvent event = events.get(i);
            final float rowTop = top + i * rowHeight;
            final float textTop = rowTop + (rowHeight - c.lineHeight(rowSize)) / 2f;
            final boolean running = event.getBegin() <= now && event.getEnd() > now;

            // the running entry is marked by a block in front of its time rather than by a bar
            // across the row: on a board the eye goes down the left edge
            if (running) {
                c.fillRect(0f, rowTop + rowHeight * 0.18f, Math.max(2f, pad * 0.6f),
                        rowHeight * 0.64f, c.accent);
            }
            c.textClipped(time(event), pad + timeColumn, textTop, rowSize, OeplCanvas.RIGHT,
                    running ? c.accent : c.ink, timeColumn);
            c.textClipped(event.getTitle(), titleLeft, textTop, rowSize, OeplCanvas.LEFT,
                    c.ink, w - titleLeft - pad);

            // the rule belongs to the row above it, and the last one is left out so that it
            // cannot land on the panel edge
            final float rule = rowTop + rowHeight;
            if (i < count - 1 && rule < h - 1f) {
                c.line(pad, rule, w - pad, rule, c.ink, 1f);
            }
        }
    }

    /** A clock time for today, a date for anything further out, a dash for an all day entry. */
    private String time(final CalendarEvent event) {
        if (event.isAllDay()) {
            return today(event.getBegin()) ? "——" : new SimpleDateFormat("d.M.",
                    Locale.getDefault()).format(event.getBegin());
        }
        if (today(event.getBegin())) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.getBegin());
        }
        return new SimpleDateFormat("EE HH:mm", Locale.getDefault()).format(event.getBegin());
    }

    private static boolean today(final long millis) {
        final Calendar now = Calendar.getInstance();
        final Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        return now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
    }
}
