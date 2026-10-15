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
 * The grid covers 9 to 17 by default and widens to take in whatever the appointments need,
 * exactly as the OpenEPaperLink week view does. Weekends are marked by their heading in the
 * accent colour rather than by a grey fill, because a tag has no greys to fill with.
 */
public class OeplCalendarWeekCard extends OeplCard {

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

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;

        final long midnight = midnight();
        final long windowEnd = midnight + days * 86400L * 1000L;

        final List<CalendarEvent> timed = new ArrayList<>();
        final List<CalendarEvent> allDay = new ArrayList<>();
        int minHour = 9;
        int maxHour = 17;
        for (final CalendarEvent e : events) {
            if (e.getEnd() <= midnight || e.getBegin() >= windowEnd) {
                continue;
            }
            if (e.isAllDay()) {
                allDay.add(e);
                continue;
            }
            timed.add(e);
            final Calendar start = Calendar.getInstance();
            start.setTimeInMillis(e.getBegin());
            minHour = Math.min(minHour, start.get(Calendar.HOUR_OF_DAY));
            final Calendar end = Calendar.getInstance();
            end.setTimeInMillis(e.getEnd());
            int endHour = end.get(Calendar.HOUR_OF_DAY);
            if (end.get(Calendar.MINUTE) > 1) {
                endHour++;
            }
            maxHour = Math.max(maxHour, Math.min(24, endHour));
        }
        if (maxHour <= minHour) {
            maxHour = minHour + 1;
        }

        // A column per day plus the hour gutter needs room. Below that the grid says less than
        // a plain list of what is coming up, so that is what a small tag gets instead - a week
        // view squeezed into a price tag is a pattern of lines, not a calendar.
        if (w < days * 46f + 26f || h < 130f) {
            new OeplCalendarListCard(events, 9, heading(midnight, days)).paint(c);
            return;
        }

        final float headSize = Math.max(7f, Math.min(h * 0.12f, w * 0.075f));
        final float headerBottom = c.lineHeight(headSize) * 1.02f;

        // all day entries stack into their own band between the headings and the grid
        final float bandRow = Math.max(7f, Math.min(h * 0.115f, w * 0.07f));
        final int bands = Math.min(allDay.isEmpty() ? 0 : 2, allDay.size());
        final float gridTop = headerBottom + bands * bandRow;

        // An hour row has to stay thick enough to hold a box with a readable label. When the
        // day does not fit, the window shrinks around the first appointment rather than letting
        // the rows collapse into each other.
        final float minRow = Math.max(7f, h * 0.055f);
        final int maxRows = Math.max(1, (int) ((h - gridTop) / minRow));
        if (maxHour - minHour > maxRows) {
            maxHour = minHour + maxRows;
        }
        final float labelSize = Math.max(7f, Math.min(h * 0.1f,
                (h - gridTop) / Math.max(1, maxHour - minHour) * 1.4f));
        final float gutter = c.measure("24", labelSize) + 4f;
        final float columnWidth = (w - gutter - 1f) / days;

        final SimpleDateFormat heading = new SimpleDateFormat("EE d.", Locale.getDefault());
        for (int i = 0; i < days; i++) {
            final long dayStart = midnight + i * 86400L * 1000L;
            final Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dayStart);
            final int weekday = cal.get(Calendar.DAY_OF_WEEK);
            final boolean weekend = weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY;
            final float left = gutter + i * columnWidth;
            c.line(left, headerBottom, left, h, c.ink, 1f);
            c.textClipped(heading.format(new Date(dayStart)), left + columnWidth / 2f, 0,
                    headSize, OeplCanvas.CENTER, weekend ? c.accent : c.ink, columnWidth - 2);
        }
        c.line(gutter + days * columnWidth, headerBottom, gutter + days * columnWidth, h,
                c.ink, 1f);
        c.line(0, headerBottom, w, headerBottom, c.ink, 1f);

        int row = 0;
        for (final CalendarEvent e : allDay) {
            if (row >= bands) {
                break;
            }
            final int firstDay = clampDay((e.getBegin() - midnight) / 86400000L);
            final int lastDay = clampDay((e.getEnd() - 1 - midnight) / 86400000L);
            final float left = gutter + firstDay * columnWidth + 2f;
            final float width = Math.max(columnWidth - 4f,
                    (lastDay - firstDay + 1) * columnWidth - 4f);
            final float top = headerBottom + row * bandRow + 1f;
            c.fillRect(left, top, width, bandRow - 2f, c.paper);
            c.strokeRect(left, top, width, bandRow - 2f, c.accent, 1f);
            c.textClipped(e.getTitle(), left + 2f, top, bandRow * 0.82f, OeplCanvas.LEFT,
                    c.ink, width - 4f);
            row++;
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
            c.line(0, y, gutter + 4f, y, c.ink, 1f);
            for (int d = 1; d <= days; d++) {
                // the tick of the last column would otherwise reach past the panel edge
                c.line(gutter + d * columnWidth - 4f, y,
                        Math.min(w - 1f, gutter + d * columnWidth + 4f), y, c.ink, 1f);
            }
            // only label an hour whose number still fits above the bottom edge, otherwise the
            // last one is cut through the middle
            if (i < hours && i % labelEvery == 0 && c.roomFor(y + 1f, labelSize)) {
                c.text(String.valueOf(minHour + i), gutter - 2f, y + 1f, labelSize,
                        OeplCanvas.RIGHT, c.ink);
            }
        }
        c.line(0, h - 1f, w, h - 1f, c.ink, 1f);

        for (final CalendarEvent e : timed) {
            final int day = clampDay((e.getBegin() - midnight) / 86400000L);
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
            final float left = gutter + day * columnWidth + 2f;
            final float width = columnWidth - 4f;
            c.fillRect(left, top, width, boxHeight, c.paper);
            c.strokeRect(left, top, width, boxHeight, c.ink, 1f);
            // only label a box that something legible actually fits into
            final float titleSize = Math.max(7f, Math.min(h * 0.085f, boxHeight - 2f));
            if (boxHeight >= titleSize) {
                c.textClipped(e.getTitle(), left + 2f, top + 1f, titleSize, OeplCanvas.LEFT,
                        c.ink, width - 4f);
            }
        }
    }

    /** What the list fallback is captioned with: the span the grid would have covered. */
    private String heading(final long midnight, final int days) {
        final SimpleDateFormat day = new SimpleDateFormat("EE", Locale.getDefault());
        if (days <= 1) {
            return day.format(new Date(midnight));
        }
        return day.format(new Date(midnight)) + " - "
                + day.format(new Date(midnight + (days - 1) * 86400L * 1000L));
    }

    private int clampDay(final long day) {
        return (int) Math.max(0, Math.min(days - 1, day));
    }

    private static long midnight() {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
