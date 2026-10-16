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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The month as a sheet: a cell per day, today marked, and a dot for every appointment the day
 * holds.
 * <p>
 * This is the one calendar view that answers "when" rather than "what next" - whether the
 * weekend after next is free, how far away the fourteenth is - and it is the view a paper
 * calendar on a wall has always had. The appointments themselves are not written into the
 * cells: at the size a day gets on a tag, a title is two letters and an ellipsis, while a row
 * of dots says what the eye is actually looking for from across the room.
 * <p>
 * A panel too small for a grid of seven columns falls back to the list of what is coming up,
 * the same way the week view does: a month squeezed into a price tag is a pattern, not a
 * calendar.
 */
public class OeplMonthCard extends OeplCard {

    /** Below this a cell holds neither a number nor a dot anybody can see. */
    private static final float MIN_CELL = 13f;
    /** More than this many dots in one day is a day nobody reads the dots of. */
    private static final int MAX_DOTS = 4;

    private final List<CalendarEvent> events;
    private final String caption;

    public OeplMonthCard(final List<CalendarEvent> events, final String caption) {
        super(true);
        this.events = events == null ? Collections.emptyList() : events;
        this.caption = caption;
    }

    @Override
    public boolean hasData() {
        return true; // a month is worth showing even when nothing is in it
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, Math.min(w, h) * 0.02f);
        final Calendar today = Calendar.getInstance();

        // Everything is measured before anything is drawn. The sheet has a way out - a panel
        // too small for seven columns shows the list of what is coming up instead - and a way
        // out taken halfway through leaves both cards on the panel, one over the other.
        final float cell = (w - pad * 2f) / 7f;
        final float headSize = c.sizeForHeight(Math.min(h * 0.12f, w * 0.07f), h * 0.15f, 8f);
        final float headHeight = headSize > 0f ? c.lineHeight(headSize) * 1.05f : 0f;
        final float nameSize = c.sizeForHeight(Math.min(cell * 0.42f, 13f), cell * 0.5f, 7f);
        final float nameHeight = nameSize > 0f ? c.lineHeight(nameSize) * 1.05f + 2f : 0f;

        final Calendar first = (Calendar) today.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        final int firstDay = first.getFirstDayOfWeek();
        final int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        final int lead = (first.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7;
        final int rows = (int) Math.ceil((lead + days) / 7f);
        final float top = pad + headHeight + nameHeight;
        final float rowHeight = (h - top - pad) / rows;

        if (cell < MIN_CELL || rowHeight < MIN_CELL) {
            // not a month on this panel, so the next few days instead
            new OeplCalendarListCard(events, 9, caption).paint(c);
            return;
        }

        if (headSize > 0f) {
            final String month = new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    .format(today.getTime());
            c.rowEnds(month, String.valueOf(today.get(Calendar.DAY_OF_MONTH)), pad, headSize,
                    c.ink, c.accent, pad, w - pad, pad * 2f);
        }

        // The weekday names in the order this phone counts them: a calendar that starts on
        // Sunday where the reader's week starts on Monday is read wrong at a glance.
        if (nameSize > 0f) {
            final SimpleDateFormat weekday = new SimpleDateFormat("EE", Locale.getDefault());
            final Calendar naming = (Calendar) today.clone();
            for (int i = 0; i < 7; i++) {
                naming.set(Calendar.DAY_OF_WEEK, ((firstDay - 1 + i) % 7) + 1);
                c.textClipped(weekday.format(naming.getTime()), pad + cell * i + cell / 2f,
                        pad + headHeight, nameSize, OeplCanvas.CENTER, c.ink, cell - 2f);
            }
            final float rule = pad + headHeight + c.lineHeight(nameSize) * 1.05f;
            c.line(pad, Math.round(rule), w - pad, Math.round(rule), c.ink, 1f);
        }

        final float numberSize = c.sizeForHeight(Math.min(rowHeight * 0.52f, cell * 0.5f),
                rowHeight * 0.6f, 7f);
        final int thisMonth = first.get(Calendar.MONTH);
        final int thisYear = first.get(Calendar.YEAR);

        for (int day = 1; day <= days; day++) {
            final int index = lead + day - 1;
            final float left = pad + cell * (index % 7);
            final float cellTop = top + rowHeight * (index / 7);
            final boolean isToday = day == today.get(Calendar.DAY_OF_MONTH);

            if (isToday) {
                // the day one is standing in, filled rather than outlined: it has to be the
                // first thing found on a sheet of thirty equal boxes
                c.fillRect(left + 1f, cellTop + 1f, cell - 2f, rowHeight - 2f, c.accent);
            }
            if (numberSize > 0f) {
                c.text(String.valueOf(day), left + cell / 2f, cellTop + rowHeight * 0.08f,
                        numberSize, OeplCanvas.CENTER, isToday ? c.paper : c.ink);
            }
            dots(c, countOn(thisYear, thisMonth, day), left, cellTop, cell, rowHeight,
                    numberSize, isToday ? c.paper : c.ink);
        }
    }

    /** A dot per appointment, along the bottom of the cell, capped at what fits. */
    private void dots(final OeplCanvas c, final int count, final float left, final float top,
                      final float cell, final float rowHeight, final float numberSize,
                      final int color) {
        if (count <= 0) {
            return;
        }
        final int shown = Math.min(count, MAX_DOTS);
        final float size = Math.max(1f, Math.min(cell * 0.1f, rowHeight * 0.12f));
        final float gap = size * 0.8f;
        final float width = shown * size + (shown - 1) * gap;
        final float y = top + rowHeight - size - Math.max(1f, rowHeight * 0.1f);
        if (y < top + numberSize) {
            return; // the number has the cell to itself on a panel this small
        }
        float x = left + (cell - width) / 2f;
        for (int i = 0; i < shown; i++) {
            c.fillRect(x, y, size, size, color);
            x += size + gap;
        }
    }

    /** How many appointments fall on a given day of this month. */
    private int countOn(final int year, final int month, final int day) {
        int count = 0;
        final Calendar when = Calendar.getInstance();
        for (final CalendarEvent event : events) {
            when.setTimeInMillis(event.getBegin());
            if (when.get(Calendar.YEAR) == year && when.get(Calendar.MONTH) == month
                    && when.get(Calendar.DAY_OF_MONTH) == day) {
                count++;
            }
        }
        return count;
    }
}
