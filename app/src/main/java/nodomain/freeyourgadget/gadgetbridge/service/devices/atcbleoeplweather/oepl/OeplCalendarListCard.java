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
        final float headSize = Math.max(8f, Math.min(h * 0.145f, w * 0.11f));
        c.rowEnds(caption, new SimpleDateFormat("EE d.M.", Locale.getDefault())
                        .format(new Date()), h * 0.01f, headSize, c.ink, c.ink,
                pad, w - pad, pad * 2);

        final float top = h * 0.01f + c.lineHeight(headSize) * 1.05f;
        // a row has to fit the width as well: on a narrow tag a row sized off the height alone
        // left nothing but a single letter of the title beside the time
        final float rowSize = Math.max(7f, Math.min(h * 0.125f, w * 0.095f));
        final float rowHeight = c.lineHeight(rowSize) * 1.08f;
        final int fits = (int) ((h - top) / rowHeight);
        final int count = Math.min(Math.min(maxEntries, fits), events.size());
        float timeColumn = 0f;
        for (int i = 0; i < count; i++) {
            timeColumn = Math.max(timeColumn, c.measure(when(events.get(i)), rowSize));
        }
        timeColumn = Math.min(timeColumn + pad * 2, w * 0.42f);
        final long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            final CalendarEvent event = events.get(i);
            final float y = top + i * rowHeight;
            final boolean running = event.getBegin() <= now && event.getEnd() > now;
            int color = c.ink;
            if (running) {
                c.fillRect(0, y - rowHeight * 0.06f, w, rowHeight * 0.98f, c.accent);
                color = c.paper;
            }
            c.textClipped(when(event), pad, y, rowSize, OeplCanvas.LEFT, color,
                    timeColumn - pad);
            c.textClipped(event.getTitle(), pad + timeColumn, y, rowSize, OeplCanvas.LEFT, color,
                    w - timeColumn - pad * 2);
        }
    }

    /** A time for anything starting today, a date for anything later. */
    private String when(final CalendarEvent event) {
        if (event.isAllDay()) {
            return sameDay(event.getBegin())
                    ? "—"
                    : new SimpleDateFormat("d.M.", Locale.getDefault()).format(event.getBegin());
        }
        if (sameDay(event.getBegin())) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(event.getBegin());
        }
        return new SimpleDateFormat("d.M.", Locale.getDefault()).format(event.getBegin());
    }

    private static boolean sameDay(final long millis) {
        final Calendar a = Calendar.getInstance();
        final Calendar b = Calendar.getInstance();
        b.setTimeInMillis(millis);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
