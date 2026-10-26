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
import java.util.ArrayList;
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
        final float headSize = c.sizeForHeight(Math.min(h * 0.12f, w * 0.07f), h * 0.15f, OeplCanvas.READABLE);
        final float headHeight = headSize > 0f ? c.lineHeight(headSize) * 1.05f : 0f;
        final float nameSize = c.sizeForHeight(Math.min(cell * 0.42f, 13f), cell * 0.5f, OeplCanvas.READABLE);
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
            c.rowEnds(month, OeplCanvas.number(today.get(Calendar.DAY_OF_MONTH)), pad, headSize,
                    c.ink, c.accent, pad, w - pad, pad * 2f);
        }

        // The weekday names in the order this phone counts them: a calendar that starts on
        // Sunday where the reader's week starts on Monday is read wrong at a glance.
        // The short form of a weekday is short in German and a word in Malayalam, and a cell
        // of a month sheet is a seventh of the panel. Every language has a narrower form
        // still - one letter, or one syllable - and that goes in rather than a cut. Where not
        // even the narrowest fits, the sheet goes without its headings: seven cut words say
        // less than nothing, and the room goes to the days instead.
        final String pattern = nameSize > 0f
                ? namePattern(c, today, firstDay, nameSize, cell - 2f) : null;
        if (pattern != null) {
            final Calendar naming = (Calendar) today.clone();
            final SimpleDateFormat weekday = new SimpleDateFormat(pattern, Locale.getDefault());
            for (int i = 0; i < 7; i++) {
                naming.set(Calendar.DAY_OF_WEEK, ((firstDay - 1 + i) % 7) + 1);
                c.textClipped(weekday.format(naming.getTime()), pad + cell * i + cell / 2f,
                        pad + headHeight, nameSize, OeplCanvas.CENTER, c.ink, cell - 2f);
            }
            final float rule = pad + headHeight + c.lineHeight(nameSize) * 1.05f;
            c.line(pad, Math.round(rule), w - pad, Math.round(rule), c.ink, 1f);
        }

        final float numberSize = c.sizeForHeight(Math.min(rowHeight * 0.52f, cell * 0.5f),
                rowHeight * 0.6f, OeplCanvas.READABLE);
        final List<List<String>> onDay = onEachDay(first, days);

        // What a cell holds depends on how large a cell is. On a price tag it is a number and
        // a row of dots - a title at that size is two letters and a mark. On a panel on a
        // wall a cell is the size of a postage stamp and holds what the day actually is: the
        // dots say that something is on, the titles say what, and the whole point of hanging
        // a month on a wall is reading it from the doorway.
        final float titleSize = cell >= 56f && rowHeight >= 46f
                ? c.sizeForHeight(Math.min(rowHeight * 0.17f, cell * 0.17f),
                rowHeight * 0.2f, OeplCanvas.READABLE) : 0f;
        final float numberLine = numberSize > 0f ? c.lineHeight(numberSize) * 0.92f : 0f;
        final int titleRoom = titleSize > 0f
                ? (int) ((rowHeight - numberLine - 3f) / c.lineHeight(titleSize)) : 0;

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
            final List<String> titles = onDay.get(day);
            final int color = isToday ? c.paper : c.ink;
            if (numberSize > 0f) {
                // the number moves into the corner once the cell is a page of its own, so the
                // titles under it get the width rather than being written around it
                final boolean corner = titleRoom >= 1 && !titles.isEmpty();
                c.text(OeplCanvas.number(day), corner ? left + 3f : left + cell / 2f,
                        cellTop + rowHeight * 0.06f, numberSize,
                        corner ? OeplCanvas.LEFT : OeplCanvas.CENTER, color);
            }
            if (titleRoom >= 1 && !titles.isEmpty()) {
                float line = cellTop + rowHeight * 0.06f + numberLine;
                for (int i = 0; i < Math.min(titleRoom, titles.size()); i++) {
                    // the last line that fits says how many are left rather than one more title
                    final boolean last = i == titleRoom - 1 && titles.size() > titleRoom;
                    c.textClipped(last ? "+" + OeplCanvas.number(titles.size() - i)
                                    : titles.get(i), left + 3f, line, titleSize,
                            OeplCanvas.LEFT, color, cell - 6f);
                    line += c.lineHeight(titleSize);
                }
            } else {
                dots(c, titles.size(), left, cellTop, cell, rowHeight, numberSize, color);
            }
        }
    }

    /**
     * The widest weekday format whose names all fit a cell - the short one where it fits, the
     * narrow one otherwise - or null when not even the narrowest does at the smallest size
     * the panel can still show.
     */
    private String namePattern(final OeplCanvas c, final Calendar today, final int firstDay,
                               final float nameSize, final float room) {
        final Calendar naming = (Calendar) today.clone();
        String narrowest = null;
        for (final String pattern : new String[]{"EE", "EEEEE"}) {
            final SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
            float widest = 0f;
            float smallest = 0f;
            for (int i = 0; i < 7; i++) {
                naming.set(Calendar.DAY_OF_WEEK, ((firstDay - 1 + i) % 7) + 1);
                final String name = format.format(naming.getTime());
                widest = Math.max(widest, c.measure(name, nameSize));
                smallest = Math.max(smallest, c.measure(name, OeplCanvas.floorFor(nameSize)));
            }
            if (widest <= room) {
                return pattern;
            }
            if (smallest <= room) {
                narrowest = pattern; // it fits once it is set smaller, which textClipped does
            }
        }
        return narrowest;
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

    /**
     * What is on each day of this month, by the days an entry covers rather than by the moment
     * it begins at.
     * <p>
     * So a holiday over three days is on all three - which is what somebody looking for a free
     * weekend is looking at the sheet for - and an all day entry lands on the day it is
     * written on rather than on whatever day its midnight in UTC falls on here. See
     * {@link OeplDays}. The titles are in the calendar's own order, so the first of a day is
     * the first that day brings.
     */
    private List<List<String>> onEachDay(final Calendar first, final int days) {
        final Calendar cell = (Calendar) first.clone();
        cell.set(Calendar.DAY_OF_MONTH, 1);
        cell.set(Calendar.HOUR_OF_DAY, 0);
        cell.set(Calendar.MINUTE, 0);
        cell.set(Calendar.SECOND, 0);
        cell.set(Calendar.MILLISECOND, 0);
        final List<List<String>> onDay = new ArrayList<>(days + 1);
        onDay.add(Collections.emptyList()); // there is no day zero
        for (int day = 1; day <= days; day++) {
            final List<String> titles = new ArrayList<>();
            for (final CalendarEvent event : events) {
                if (OeplDays.coversDay(event, cell.getTimeInMillis())) {
                    final String title = event.getTitle() == null ? "" : event.getTitle().trim();
                    titles.add(title.isEmpty() ? "-" : title);
                }
            }
            onDay.add(titles);
            cell.add(Calendar.DAY_OF_MONTH, 1);
        }
        return onDay;
    }
}
