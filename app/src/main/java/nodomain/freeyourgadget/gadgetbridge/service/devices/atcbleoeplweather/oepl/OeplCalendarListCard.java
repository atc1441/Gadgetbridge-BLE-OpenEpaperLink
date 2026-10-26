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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The appointment list, in the shape OpenEPaperLink gives it: a caption on the left and today's
 * date on the right, then one line per appointment with its time in a narrow left column and
 * its title filling the rest. Whatever is happening right now gets a bar in the accent colour
 * and is drawn in the paper colour on top of it.
 * <p>
 * Appointments that start today show a time, later ones show their date, the same rule the
 * access point applies.
 */
public class OeplCalendarListCard extends OeplCard {

    private final List<CalendarEvent> events;
    private final int maxEntries;
    private final String caption;

    public OeplCalendarListCard(final List<CalendarEvent> events, final int maxEntries,
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
        final float pad = Math.max(2f, w * 0.017f);

        // the caption and the date share one line, so neither may be sized on its own
        final float headSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.145f, w * 0.11f));
        c.rowEnds(caption, new SimpleDateFormat("EE d.M.", Locale.getDefault())
                        .format(new Date()), h * 0.01f, headSize, c.ink, c.ink,
                pad, w - pad, pad * 2);

        final float top = h * 0.01f + c.lineHeight(headSize) * 1.05f;
        // A row has to fit the width as well - on a narrow tag a row sized off the height
        // alone left nothing but a single letter of the title beside the time - and it stops
        // growing at a size a wall is read from. An eighth of a seven inch panel is eighty
        // pixel type, which is five appointments and a great deal of paper; the room that
        // frees goes to the rest of the list and to where each appointment is.
        final float rowSize = Math.max(OeplCanvas.READABLE, Math.min(Math.min(h * 0.125f, w * 0.095f), MAX_ROW));
        final float minRow = c.lineHeight(rowSize) * 1.08f;
        final int fits = (int) ((h - top) / minRow);
        final int count = Math.min(Math.min(maxEntries, fits), events.size());
        if (count <= 0) {
            return;
        }
        // whatever is left over is shared out between the rows rather than left at the foot
        final float rowHeight = Math.max(minRow, (h - top - pad) / count);
        final float placeSize = Math.max(OeplCanvas.READABLE, rowSize * 0.62f);
        // only when there is a place to write, and room for the line it goes on: a second
        // line reserved for nothing is a gap down the middle of every entry
        boolean anyPlace = false;
        for (int i = 0; i < count; i++) {
            anyPlace |= !place(events.get(i)).isEmpty();
        }
        final boolean withPlace = anyPlace && rowHeight >= c.lineHeight(rowSize)
                + c.lineHeight(placeSize) * 1.1f;
        // what an entry occupies, as against what it is given: on a tall panel the rows are
        // spread out, and the mark for what is running now belongs to the line rather than to
        // the gap around it
        final float entry = (withPlace ? c.lineHeight(rowSize) + c.lineHeight(placeSize)
                : c.lineHeight(rowSize)) * 1.08f;
        float timeColumn = 0f;
        for (int i = 0; i < count; i++) {
            timeColumn = Math.max(timeColumn, c.measure(when(events.get(i)), rowSize));
        }
        timeColumn = Math.min(timeColumn + pad * 2, w * 0.42f);

        // One size for all the titles rather than one per row. A line set half the size of the
        // one above it reads as a fault even when it is the line that survived whole, so the
        // list is set from the longest title it has to hold - as far down as three quarters of
        // the row, which is a list that is still read at a glance. Only what does not fit even
        // then is squeezed on its own line, which is what the drawing does by itself.
        final float titleRoom = w - timeColumn - pad * 2;
        float titleSize = rowSize;
        for (int i = 0; i < count; i++) {
            final float width = c.measure(events.get(i).getTitle(), rowSize);
            if (width > titleRoom && width > 0f) {
                // three quarters of the row, and never under the seven pixels the panel can
                // still show: in a quarter of a tag the rows are already at their floor
                titleSize = Math.min(titleSize, Math.max(Math.min(rowSize, 7f),
                        Math.max(rowSize * 0.75f, rowSize * titleRoom / width)));
            }
        }

        for (int i = 0; i < count; i++) {
            final CalendarEvent event = events.get(i);
            final float y = top + i * rowHeight;
            final boolean running = OeplDays.runningNow(event);
            int color = c.ink;
            if (running) {
                c.fillRect(0, y - entry * 0.06f, w, entry, c.accent);
                color = c.paper;
            } else if (i > 0 && rowHeight > entry * 1.35f) {
                // spread out, a list needs its rules back, or it reads as a page of fragments
                final float rule = Math.round(y - (rowHeight - entry) / 2f);
                c.line(pad, rule, w - pad, rule, c.ink, 1f);
            }
            c.textClipped(when(event), pad, y, rowSize, OeplCanvas.LEFT, color,
                    timeColumn - pad);
            c.textClipped(event.getTitle(), pad + timeColumn, y + (rowSize - titleSize) * 0.4f,
                    titleSize, OeplCanvas.LEFT, color, titleRoom);
            // where it is, under what it is, when the row has the height for a second line:
            // on a panel on a wall that is the line somebody walks up to read
            if (withPlace) {
                final String place = place(event);
                if (!place.isEmpty()) {
                    c.textClipped(place, pad + timeColumn, y + c.lineHeight(rowSize),
                            placeSize, OeplCanvas.LEFT, color, titleRoom);
                }
            }
        }
    }

    /**
     * The largest a row is set, however much room there is. Past this a list stops being a
     * list and becomes a poster of five appointments, and the panel it is on holds twenty.
     */
    private static final float MAX_ROW = 34f;

    /** Where an appointment is, if it says: the place, or else the first line of its note. */
    private static String place(final CalendarEvent event) {
        final String location = event.getLocation() == null ? "" : event.getLocation().trim();
        if (!location.isEmpty()) {
            return location;
        }
        final String description = event.getDescription() == null
                ? "" : event.getDescription().trim();
        final int line = description.indexOf('\n');
        return line > 0 ? description.substring(0, line) : description;
    }

    /**
     * A time for anything starting today, a date for anything later, a dash for an all day
     * entry that today falls into.
     * <p>
     * An all day entry is read by the days it is written on rather than by the instants it
     * carries: a holiday that began last week and is still running reads as today's, and one
     * later in the week carries the date it really falls on instead of one a couple of hours
     * out - see {@link OeplDays}.
     */
    private String when(final CalendarEvent event) {
        if (event.isAllDay()) {
            return OeplDays.coversToday(event)
                    ? "—"
                    : new SimpleDateFormat("d.M.", Locale.getDefault())
                    .format(OeplDays.dayOf(event));
        }
        if (OeplDays.beginsToday(event)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.getBegin());
        }
        return new SimpleDateFormat("d.M.", Locale.getDefault()).format(event.getBegin());
    }
}
