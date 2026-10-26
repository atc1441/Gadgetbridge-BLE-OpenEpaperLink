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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * Which day an appointment belongs to, for every card that puts one on a panel.
 * <p>
 * An all day entry does not carry a moment, it carries a date, and Android hands that date over
 * as midnight in UTC rather than in the time zone one is standing in. Read as a moment it
 * therefore starts two hours into its first day in Berlin, five hours before its first day in
 * Chicago, and ends an hour into the day after its last one - so a holiday that was over on
 * Sunday evening was drawn across Monday as well, and in half the world an entry for today was
 * stamped with yesterday's date.
 * <p>
 * Here an all day entry is placed by its date: the date it carries in UTC, moved to the local
 * midnight of the same calendar day. Anything that carries a time of its own is left alone. The
 * days themselves are counted in days rather than in milliseconds, because on the two days a
 * year the clocks change a day is 23 or 25 hours long.
 */
public final class OeplDays {

    private OeplDays() {
    }

    /** The midnight the day of a moment begins with, in the local time zone. */
    public static long startOfDay(final long millis) {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Today's midnight. */
    public static long today() {
        return startOfDay(System.currentTimeMillis());
    }

    /** The midnight after one, which is not always 24 hours later. */
    public static long nextDay(final long midnight) {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(midnight);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /**
     * When an entry begins, as a local moment: an all day entry at the midnight of the date it
     * is written on, everything else at the moment it carries.
     */
    public static long begins(final CalendarEvent event) {
        return event.isAllDay() ? localMidnightOf(event.getBegin()) : event.getBegin();
    }

    /**
     * When an entry is over, as a local moment, exclusive. The end of an all day entry is the
     * midnight after its last day, and that midnight belongs to the day before it.
     */
    public static long ends(final CalendarEvent event) {
        return event.isAllDay() ? nextDay(lastDay(event)) : event.getEnd();
    }

    /** The midnight the first day of an entry begins with. */
    public static long firstDay(final CalendarEvent event) {
        return event.isAllDay() ? localMidnightOf(event.getBegin())
                : startOfDay(event.getBegin());
    }

    /**
     * The midnight the last day of an entry begins with. The moment an entry ends does not
     * belong to it - an appointment running until midnight is over that evening, not on the
     * next day - so the last day is the day the moment before its end falls in.
     */
    public static long lastDay(final CalendarEvent event) {
        if (event.isAllDay()) {
            return localMidnightOf(Math.max(event.getBegin(), event.getEnd() - 1L));
        }
        return startOfDay(Math.max(event.getBegin(), event.getEnd() - 1L));
    }

    /** Whether any part of the entry falls on the day a midnight begins. */
    public static boolean coversDay(final CalendarEvent event, final long midnight) {
        return firstDay(event) <= midnight && lastDay(event) >= midnight;
    }

    /** Whether any part of the entry falls on today. */
    public static boolean coversToday(final CalendarEvent event) {
        return coversDay(event, today());
    }

    /** Whether the entry begins today, whatever else it covers. */
    public static boolean beginsToday(final CalendarEvent event) {
        return firstDay(event) == today();
    }

    /**
     * Whether the entry is happening at this moment. An all day entry has no hours of its own,
     * so it counts for the whole of the days it is written on - and for no part of the day
     * after, which is where reading its end as a moment used to leave it.
     */
    public static boolean runningNow(final CalendarEvent event) {
        if (event.isAllDay()) {
            return coversToday(event);
        }
        final long now = System.currentTimeMillis();
        return event.getBegin() <= now && event.getEnd() > now;
    }

    /** The first day of the entry, for a date or a weekday to be formatted from. */
    public static Date dayOf(final CalendarEvent event) {
        return new Date(begins(event));
    }

    /** How many of the entries fall on the day a midnight begins. */
    public static int countOnDay(final List<CalendarEvent> events, final long midnight) {
        if (events == null) {
            return 0;
        }
        int count = 0;
        for (final CalendarEvent event : events) {
            if (coversDay(event, midnight)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The local midnight of the calendar day a UTC moment falls on - the one conversion all of
     * this turns on, and the one every card here used to leave out.
     */
    private static long localMidnightOf(final long utcMillis) {
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        final Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return local.getTimeInMillis();
    }
}
