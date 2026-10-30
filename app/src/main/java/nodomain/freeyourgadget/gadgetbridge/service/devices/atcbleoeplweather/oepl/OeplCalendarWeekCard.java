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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The week view: a column per day over an hour grid, with each appointment as a box spanning
 * the hours it occupies and all day entries stacked above the grid.
 * <p>
 * The columns are the days still to come, as many as the settings ask for, beginning with
 * today on the left. The grid covers 9 to 17 by default and widens to take in whatever the
 * appointments need. A column is headed the way a week is read - the weekday above the day of
 * the month - with today marked, so that the left hand column is read as today rather than as
 * a Monday. Weekends are named in the accent colour rather than filled with grey, since a tag
 * has no greys to fill with.
 * <p>
 * The hour grid holds what happens inside one day. An appointment that runs from one day into
 * another - from Tuesday morning until Thursday night, say - is not an hour of a day but days
 * being taken, so it goes into the band above the grid beside the all day entries, spanning
 * the columns of the days it covers. As a box in a column it was drawn down the day it began
 * on, cut off at the bottom edge of the panel, and every further day it covered stayed empty.
 */
public class OeplCalendarWeekCard extends OeplCard {

    /** Below this a column holds neither a heading nor a box with a word in it. */
    private static final float MIN_COLUMN = 46f;

    /**
     * Whether an area is large enough for the grid, or whether this card would fall back to
     * the list of what is coming up.
     * <p>
     * It is asked from outside as well: a sheet that puts this card beside an appointment
     * list has to know that it is getting a grid, because what it gets otherwise is the same
     * list twice over - which is how it was reported from a 4.2 inch panel.
     */
    public static boolean fitsAsGrid(final float width, final float height, final int days) {
        final int wanted = Math.max(1, Math.min(7, days));
        return width >= wanted * MIN_COLUMN + 26f && height >= 130f;
    }

    private final List<CalendarEvent> events;
    private final int days;

    public OeplCalendarWeekCard(final List<CalendarEvent> events, final int days) {
        super(true);
        this.events = events;
        this.days = Math.max(1, Math.min(7, days));
    }

    @Override
    public boolean hasData() {
        return events != null;
    }

    /** How many days it was asked for, which decides whether a grid fits an area. */
    public int days() {
        return days;
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;

        final long[] starts = dayStarts(Calendar.getInstance(), days);

        final List<CalendarEvent> timed = new ArrayList<>();
        final List<CalendarEvent> banded = new ArrayList<>();
        int minHour = 9;
        int maxHour = 17;
        final Calendar when = Calendar.getInstance();
        for (final CalendarEvent e : events) {
            if (e.isAllDay()) {
                // by the days it is written on, not by the instants it carries: see OeplDays
                if (OeplDays.ends(e) > starts[0] && OeplDays.begins(e) < starts[days]) {
                    banded.add(e);
                }
                continue;
            }
            if (e.getEnd() <= starts[0] || e.getBegin() >= starts[days]) {
                continue;
            }
            // An appointment that runs from one day into another is not an hour of a day, it
            // is days being taken - so it goes into the band above the grid, where it spans
            // the columns of the days it covers, exactly as an all day entry does. As a box in
            // one column it ran down the day it began on, was cut off at the bottom edge of
            // the panel, and left every further day it covers empty; and a column it filled
            // would have taken the lane the rest of that day's appointments need.
            if (OeplDays.firstDay(e) != OeplDays.lastDay(e)) {
                banded.add(e);
                continue;
            }
            timed.add(e);
            when.setTimeInMillis(e.getBegin());
            minHour = Math.min(minHour, when.get(Calendar.HOUR_OF_DAY));
            // The hour it is over, which for one running until midnight is the twenty fourth
            // and not the nought the clock then reads: read off the clock, the last evening
            // appointment of the day asked for no room at all and was drawn under the panel.
            int endHour = 24;
            if (e.getEnd() < OeplDays.nextDay(OeplDays.firstDay(e))) {
                when.setTimeInMillis(e.getEnd());
                endHour = when.get(Calendar.HOUR_OF_DAY);
                if (when.get(Calendar.MINUTE) > 1) {
                    endHour++;
                }
            }
            maxHour = Math.max(maxHour, Math.min(24, endHour));
        }
        if (maxHour <= minHour) {
            maxHour = minHour + 1;
        }
        final List<Band> bandedRows = pack(banded, starts);

        // A column per day plus the hour gutter needs room. Below that the grid says less than
        // a plain list of what is coming up, so that is what a small tag gets instead - a week
        // view squeezed into a price tag is a pattern of lines, not a calendar.
        if (!fitsAsGrid(w, h, days)) {
            new OeplCalendarListCard(events, 9, heading(starts)).paint(c);
            return;
        }

        // The hour gutter is measured first, from the panel alone: the headings want to know
        // how wide a column is, and the columns are whatever the gutter leaves over.
        final float labelSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.075f, 18f));
        final float gutter = c.measure(OeplCanvas.number(24), labelSize) + 5f;
        final float columnWidth = (w - gutter - 1f) / days;

        // The heading is two lines, the weekday over the day of the month, and both are sized
        // to the column they sit in rather than to the panel. Sized to the panel, a seven
        // column week came out as a row of single letters and ellipses.
        final float headRoom = h * 0.15f;
        final float nameSize = c.sizeForHeight(Math.min(columnWidth * 0.46f, headRoom * 0.52f),
                headRoom * 0.52f, OeplCanvas.READABLE);
        final float numberSize = nameSize * 0.92f;
        final float headerBottom = c.lineHeight(nameSize) + c.lineHeight(numberSize) + 2f;

        // Entries that take whole days go in their own band between the headings and the grid,
        // in as many rows as they need and the band may have without taking the grid's room.
        final float bandRow = Math.max(OeplCanvas.READABLE, Math.min(h * 0.115f, w * 0.07f));
        final int bandRows = Math.max(1, Math.min(4, (int) ((h - headerBottom) * 0.45f / bandRow)));
        int wanted = 0;
        for (final Band band : bandedRows) {
            wanted = Math.max(wanted, band.row + 1);
        }
        final int bands = Math.min(wanted, bandRows);
        final float gridTop = headerBottom + bands * bandRow;

        // An hour row has to stay thick enough to hold a box with a readable label. When the
        // day does not fit, the window shrinks around the first appointment rather than letting
        // the rows collapse into each other.
        final float minRow = Math.max(OeplCanvas.READABLE, h * 0.055f);
        final int maxRows = Math.max(1, (int) ((h - gridTop) / minRow));
        if (maxHour - minHour > maxRows) {
            maxHour = minHour + maxRows;
        }

        headings(c, starts, gutter, columnWidth, nameSize, numberSize, headerBottom);

        // the columns go down before anything is put into them, so that a holiday spanning
        // three days reads as one box rather than as three cut apart by the lines beneath it
        for (int i = 0; i <= days; i++) {
            final float x = Math.min(gutter + i * columnWidth, w - 1f);
            c.line(x, headerBottom, x, h, c.ink, 1f);
        }

        for (final Band band : bandedRows) {
            if (band.row >= bands) {
                continue;
            }
            final float left = gutter + band.from * columnWidth + 2f;
            final float width = Math.max(columnWidth - 4f,
                    (band.to - band.from + 1) * columnWidth - 4f);
            final float top = headerBottom + band.row * bandRow + 1f;
            c.fillRect(left, top, width, bandRow - 2f, c.paper);
            c.strokeRect(left, top, width, bandRow - 2f, c.accent, 1f);
            final float bandSize = c.sizeForHeight(bandRow * 0.82f, bandRow - 2f, OeplCanvas.READABLE);
            if (bandSize > 0f) {
                c.textSqueezed(band.event.getTitle(), left + 2f, top, bandSize, OeplCanvas.LEFT,
                        c.ink, width - 4f, Math.max(OeplCanvas.READABLE, bandSize * 0.45f));
            }
        }
        if (bands > 0) {
            c.line(0, gridTop, w, gridTop, c.ink, 1f);
        }

        final int hours = maxHour - minHour;
        final float hourHeight = (h - gridTop) / hours;
        // a label on every hour turns into a smear once the rows get thin, so thin them out
        final int labelEvery = Math.max(1, (int) Math.ceil(c.lineHeight(labelSize) / hourHeight));
        for (int i = 0; i <= hours; i++) {
            // the last line of the grid falls on the bottom edge, where a stroke would land
            // half a pixel outside the panel
            final float y = Math.min(gridTop + i * hourHeight, h - 1f);
            // The hour reaches across the whole week as a row of dots. Drawn solid it would
            // compete with the appointments standing on it, and drawn as the stubs at the
            // column edges it used to be, the grid had no horizontal lines at all - which is
            // what made a week of empty columns unreadable.
            if (i > 0 || bands == 0) {
                c.dottedRow(y, gutter, w - 1f, c.ink, 3f);
            }
            c.line(0, y, gutter - 2f, y, c.ink, 1f);
            // only label an hour whose number still fits above the bottom edge, otherwise the
            // last one is cut through the middle
            if (i < hours && i % labelEvery == 0 && c.roomFor(y + 1f, labelSize)) {
                c.text(OeplCanvas.number(minHour + i), gutter - 4f, y + 1f, labelSize,
                        OeplCanvas.RIGHT, c.ink);
            }
        }
        c.line(0, h - 1f, w, h - 1f, c.ink, 1f);

        // Appointments that overlap in time share their column, each in a lane of its own,
        // which is what every week view does and what this one used to get wrong: two boxes
        // drawn over each other left two titles in the same place and neither readable.
        final int[] lanes = lanes(timed, starts);
        int columns = 1;
        for (final int lane : lanes) {
            columns = Math.max(columns, lane + 1);
        }
        for (int index = 0; index < timed.size(); index++) {
            final CalendarEvent e = timed.get(index);
            final int day = dayOf(starts, e.getBegin());
            final Calendar start = Calendar.getInstance();
            start.setTimeInMillis(e.getBegin());
            final float startMinutes = start.get(Calendar.HOUR_OF_DAY) * 60f
                    + start.get(Calendar.MINUTE) - minHour * 60f;
            final float duration = Math.max(20f, (e.getEnd() - e.getBegin()) / 60000f);

            final float top = gridTop + startMinutes * hourHeight / 60f;
            final float boxHeight = Math.min(h - top - 1f, duration * hourHeight / 60f);
            if (boxHeight <= 2f || top >= h) {
                continue;
            }
            // a lane only narrows the boxes of the day it is needed on, and only as far as a
            // box still holds something: past that the later appointment is left out, because
            // a column of slivers says less than one box and a missing one
            final float laneWidth = (columnWidth - 4f) / columns;
            final boolean narrow = columns > 1 && laneWidth < 24f;
            if (narrow && lanes[index] > 0) {
                continue;
            }
            final float left = gutter + day * columnWidth + 2f
                    + (narrow ? 0f : lanes[index] * laneWidth);
            final float width = narrow ? columnWidth - 4f : laneWidth;
            c.fillRect(left, top, width, boxHeight, c.paper);
            c.strokeRect(left, top, width, boxHeight, c.ink, 1f);
            // Only label a box that a whole line actually fits into, measured against both
            // the box and the panel edge: the line a label occupies is a quarter taller than
            // its size, which is what used to push the last appointment of the day over the
            // bottom of the panel.
            // eight pixels or no title at all: the box alone still says that the hour is
            // taken, while a line of grey dust inside it says nothing
            final float titleSize = c.sizeForHeight(Math.min(h * 0.085f, columnWidth * 0.3f),
                    Math.min(boxHeight - 2f, h - top - 2f), 12);
            if (titleSize > 0f) {
                // Shrunk to fit the box before it is cut, and shrunk a long way: a column of
                // this grid is fifty pixels wide, which is one word, so the choice is between
                // "Zahnarzt" set half the size of the hour labels and "Zah…". Eight pixels
                // is where the panel's own quantiser stops it.
                c.textSqueezed(e.getTitle(), left + 2f, top + 1f, titleSize, OeplCanvas.LEFT,
                        c.ink, width - 4f, Math.max(OeplCanvas.READABLE, titleSize * 0.45f));
            }
        }
    }

    /** The weekday over the day of the month, with today marked and the weekend named apart. */
    private void headings(final OeplCanvas c, final long[] starts, final float gutter,
                          final float columnWidth, final float nameSize, final float numberSize,
                          final float headerBottom) {
        final SimpleDateFormat weekday = new SimpleDateFormat("EE", Locale.getDefault());
        final Calendar cal = Calendar.getInstance();
        final long today = startOfDay(Calendar.getInstance()).getTimeInMillis();
        for (int i = 0; i < days; i++) {
            cal.setTimeInMillis(starts[i]);
            final int day = cal.get(Calendar.DAY_OF_WEEK);
            final boolean weekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY;
            final boolean isToday = starts[i] == today;
            final float left = gutter + i * columnWidth;
            final float centre = left + columnWidth / 2f;

            if (isToday) {
                // the day one is standing in, filled rather than named apart: on a sheet of
                // seven equal columns it has to be the first thing the eye finds
                c.fillRect(left + 1f, 0f, columnWidth - 2f, headerBottom - 1f, c.accent);
            }
            final int color = isToday ? c.paper : (weekend ? c.accent : c.ink);
            // the name only, without the full stop a German abbreviation carries: in a column
            // this narrow the dot costs a letter of the day it names
            final String name = weekday.format(new Date(starts[i])).replace(".", "");
            c.textSqueezed(name, centre, 0f, nameSize, OeplCanvas.CENTER, color,
                    columnWidth - 3f, 7f);
            c.textSqueezed(OeplCanvas.number(cal.get(Calendar.DAY_OF_MONTH)), centre,
                    c.lineHeight(nameSize), numberSize, OeplCanvas.CENTER, color,
                    columnWidth - 3f, 7f);
        }
        c.line(0, headerBottom, c.width, headerBottom, c.ink, 1f);
    }

    /**
     * The midnight each column starts at, with one more at the end for the end of the window.
     * <p>
     * The window always begins today: a display on a wall has the days that are still to come
     * to show, and a column for a Tuesday that is over is a seventh of the panel saying
     * nothing. So the days set in the settings are the next ones, not the ones of the calendar
     * week around today.
     * <p>
     * They are counted in days rather than in milliseconds: the two days a year on which the
     * clocks change are 23 and 25 hours long, and a window stepped on by a fixed day length
     * lands on the same date twice - which put two Sundays in the week and pushed every
     * appointment after them into the wrong column.
     */
    static long[] dayStarts(final Calendar now, final int days) {
        final Calendar cal = startOfDay((Calendar) now.clone());
        final long[] starts = new long[days + 1];
        for (int i = 0; i <= days; i++) {
            starts[i] = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return starts;
    }

    /**
     * Which column a moment falls into: -1 when it is behind the window, and the number of days
     * when it is still ahead of it.
     */
    static int column(final long[] starts, final long time) {
        final int days = starts.length - 1;
        if (time < starts[0]) {
            return -1;
        }
        for (int i = days - 1; i >= 0; i--) {
            if (time >= starts[i]) {
                return time < starts[days] ? i : days;
            }
        }
        return days;
    }

    /** Which column a moment is drawn in, the edges standing in for what is outside. */
    static int dayOf(final long[] starts, final long time) {
        return Math.max(0, Math.min(starts.length - 2, column(starts, time)));
    }

    /** One entry of the band: the columns it covers and the row it was packed into. */
    private static final class Band {
        final CalendarEvent event;
        final int from;
        final int to;
        int row;

        Band(final CalendarEvent event, final int from, final int to) {
            this.event = event;
            this.from = from;
            this.to = to;
        }
    }

    /**
     * The band, packed into rows: two entries that do not share a day share a row, the way two
     * appointments that do not share an hour share a column in the grid below.
     * <p>
     * One entry per row is what a week of three birthdays and a holiday fills a panel with,
     * and the band only has room for a handful of rows - so the appointment running through
     * that week was left off the sheet altogether. Packed, the same week is one row.
     * <p>
     * Each entry covers the columns of its first and its last day. The midnight that ends an
     * all day entry belongs to the day before it, and without that a holiday ending on Sunday
     * reaches into Monday and takes a column that has nothing on it - see {@link OeplDays}.
     */
    private static List<Band> pack(final List<CalendarEvent> banded, final long[] starts) {
        final int days = starts.length - 1;
        final List<Band> rows = new ArrayList<>(banded.size());
        for (final CalendarEvent e : banded) {
            rows.add(new Band(e, Math.max(0, column(starts, OeplDays.firstDay(e))),
                    Math.min(days - 1, column(starts, OeplDays.lastDay(e)))));
        }
        // earliest first, and where two begin on the same day the longer of them on top: that
        // is the order a reader of a row of days expects, and it packs the tightest
        java.util.Collections.sort(rows, (a, b) -> a.from != b.from
                ? Integer.compare(a.from, b.from) : Integer.compare(b.to, a.to));
        final List<Integer> filledTo = new ArrayList<>();
        for (final Band band : rows) {
            int row = 0;
            while (row < filledTo.size() && filledTo.get(row) >= band.from) {
                row++;
            }
            if (row == filledTo.size()) {
                filledTo.add(band.to);
            } else {
                filledTo.set(row, band.to);
            }
            band.row = row;
        }
        return rows;
    }

    /**
     * Which lane each appointment goes into: the first one whose previous appointment has
     * ended by the time this one starts, counted per day. Nothing is reordered - the lane of
     * an appointment is decided by the ones before it, so the first in the list keeps the left
     * hand side of its column.
     */
    private int[] lanes(final List<CalendarEvent> timed, final long[] starts) {
        final int[] lanes = new int[timed.size()];
        // the end of the last appointment in each lane, per day
        final java.util.Map<Integer, java.util.List<Long>> ends = new java.util.HashMap<>();
        for (int i = 0; i < timed.size(); i++) {
            final CalendarEvent e = timed.get(i);
            final java.util.List<Long> inDay = ends.computeIfAbsent(dayOf(starts, e.getBegin()),
                    key -> new ArrayList<>());
            int lane = 0;
            while (lane < inDay.size() && inDay.get(lane) > e.getBegin()) {
                lane++;
            }
            if (lane == inDay.size()) {
                inDay.add(e.getEnd());
            } else {
                inDay.set(lane, e.getEnd());
            }
            lanes[i] = lane;
        }
        return lanes;
    }

    /** What the list fallback is captioned with: the span the grid would have covered. */
    private String heading(final long[] starts) {
        final SimpleDateFormat day = new SimpleDateFormat("EE", Locale.getDefault());
        if (days <= 1) {
            return day.format(new Date(starts[0]));
        }
        return day.format(new Date(starts[0])) + " - "
                + day.format(new Date(starts[days - 1]));
    }

    private static Calendar startOfDay(final Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }
}
