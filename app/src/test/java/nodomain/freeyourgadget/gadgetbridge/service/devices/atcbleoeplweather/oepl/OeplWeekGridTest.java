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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.RectF;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLImageCodec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OeplDemo;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OeplInkRecorder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The week grid: which day each column stands for, and whether a reader can tell.
 * <p>
 * Both faults this guards against were on the panel at once. The columns were stepped on by a
 * fixed day length, so on the two days a year the clocks change one date was headed twice and
 * every appointment behind it sat under the wrong heading. And the headings themselves were
 * sized to the panel rather than to the column they go in, so a week on a 400 by 300 display
 * came out as a row of single letters and ellipses - "S…" twice over, which is how the display
 * reported it.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplWeekGridTest {

    /** The night the clocks go back in Germany: that Sunday is twenty five hours long. */
    private static Calendar theDayTheClocksGoBack() {
        final Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"),
                Locale.GERMANY);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 25, 12, 0, 0);
        return c;
    }

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    @Test
    public void everyColumnOfTheWeekIsItsOwnDayWhenTheClocksChange() {
        final long[] starts = OeplCalendarWeekCard.dayStarts(theDayTheClocksGoBack(), 7);
        assertEquals("seven columns and the end of the window", 8, starts.length);

        final Calendar when = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"),
                Locale.GERMANY);
        final Set<Integer> dates = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            when.setTimeInMillis(starts[i]);
            assertEquals("a column starts at midnight", 0, when.get(Calendar.HOUR_OF_DAY));
            dates.add(when.get(Calendar.DAY_OF_MONTH));
        }
        assertEquals("no date may be headed twice", 7, dates.size());
    }

    /**
     * Whatever the settings ask for, those are the next days: a display on a wall shows what
     * is still to come, so a full week is today and the six days after it rather than the
     * calendar week today happens to fall in.
     */
    @Test
    public void everyWindowStartsToday() {
        final Calendar sunday = theDayTheClocksGoBack();
        final Calendar when = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"),
                Locale.GERMANY);

        for (final int days : new int[]{1, 3, 7}) {
            final long[] starts = OeplCalendarWeekCard.dayStarts(sunday, days);
            when.setTimeInMillis(starts[0]);
            assertEquals(days + " days begin today", 25, when.get(Calendar.DAY_OF_MONTH));

            final Calendar end = (Calendar) sunday.clone();
            end.set(Calendar.HOUR_OF_DAY, 0);
            end.set(Calendar.MINUTE, 0);
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
            end.add(Calendar.DAY_OF_MONTH, days);
            assertEquals("and end " + days + " days later", end.getTimeInMillis(),
                    starts[days]);
        }

        final long[] three = OeplCalendarWeekCard.dayStarts(sunday, 3);
        when.setTimeInMillis(three[2]);
        assertEquals("the day after the long night is the 27th", 27,
                when.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * The appointment and the heading above it have to agree. Stepped on by a fixed day length,
     * the column after the long night was headed with the date before it while already holding
     * the next day's appointments.
     */
    @Test
    public void anAppointmentSitsUnderTheHeadingOfItsOwnDay() {
        final Calendar sunday = theDayTheClocksGoBack();
        final long[] starts = OeplCalendarWeekCard.dayStarts(sunday, 3);

        final Calendar monday = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"),
                Locale.GERMANY);
        monday.clear();
        monday.set(2026, Calendar.OCTOBER, 26, 9, 0, 0);

        final int column = OeplCalendarWeekCard.dayOf(starts, monday.getTimeInMillis());
        final Calendar heading = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"),
                Locale.GERMANY);
        heading.setTimeInMillis(starts[column]);
        assertEquals("the column it lands in is the one headed monday", 26,
                heading.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * An all day entry covers the columns of the days it is written on, and the midnight that
     * ends the last of them belongs to that day rather than to the next one.
     * <p>
     * Android hands such an entry over as midnights in UTC, so in this time zone it looks like
     * it starts at two in the morning and ends at one the morning after - which had a holiday
     * that was over on Sunday evening drawn across Monday as well. {@link OeplDays} is what
     * turns those two stamps back into days; {@link OeplAllDayTest} checks it on its own and on
     * the other cards, and this is the week grid's own share of it.
     */
    @Test
    public void anAllDayEntryEndsWithItsLastDay() {
        final TimeZone before = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        try {
            final long[] starts = OeplCalendarWeekCard.dayStarts(theDayTheClocksGoBack(), 7);

            // a single day, the sunday the window starts on
            final CalendarEvent sunday = allDayInOctober(25, 1);
            assertEquals(0, OeplCalendarWeekCard.column(starts, OeplDays.firstDay(sunday)));
            assertEquals("and it ends with that sunday", 0,
                    OeplCalendarWeekCard.column(starts, OeplDays.lastDay(sunday)));

            // three days in the middle of the window
            final CalendarEvent holiday = allDayInOctober(28, 3);
            assertEquals(3, OeplCalendarWeekCard.column(starts, OeplDays.firstDay(holiday)));
            assertEquals(5, OeplCalendarWeekCard.column(starts, OeplDays.lastDay(holiday)));

            // one that was over before the window began, and one still to come
            assertEquals("behind us", -1, OeplCalendarWeekCard.column(starts,
                    OeplDays.lastDay(allDayInOctober(23, 1))));
            assertEquals("ahead of us", 7, OeplCalendarWeekCard.column(starts,
                    OeplDays.firstDay(allDayInOctober(32, 2))));
        } finally {
            TimeZone.setDefault(before);
        }
    }

    /** An all day entry in october 2026, as android stores one: midnights in UTC. */
    private static CalendarEvent allDayInOctober(final int day, final int length) {
        final Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.GERMANY);
        c.clear();
        c.set(2026, Calendar.OCTOBER, day, 0, 0, 0);
        final long begin = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, length);
        return event(begin, c.getTimeInMillis(), day, "Urlaub", true);
    }

    /**
     * Seven columns on a 400 by 300 panel, each headed with a day somebody can read. This is
     * the panel the fault was reported from.
     */
    @Test
    public void everyColumnIsHeadedWithADayThatCanBeRead() {
        final Locale before = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            final List<OeplInkRecorder.Run> runs = draw(400, 300, 7);

            final SimpleDateFormat weekday = new SimpleDateFormat("EE", Locale.getDefault());
            final long[] starts = OeplCalendarWeekCard.dayStarts(Calendar.getInstance(), 7);
            for (int i = 0; i < 7; i++) {
                final String name = weekday.format(new Date(starts[i])).replace(".", "");
                int found = 0;
                for (final OeplInkRecorder.Run run : runs) {
                    if (run.text && name.equals(run.what)) {
                        found++;
                    }
                }
                assertEquals(name + " heads exactly one column", 1, found);
            }

            for (final OeplInkRecorder.Run run : runs) {
                assertFalse("nothing on this panel has to be cut: " + run.what,
                        run.text && run.what.contains("…"));
            }
        } finally {
            Locale.setDefault(before);
        }
    }

    /** Everything stays inside the panel, headings and hour gutter included. */
    @Test
    public void nothingReachesOverTheEdge() {
        for (final int[] panel : new int[][]{{400, 300}, {800, 480}, {384, 184}}) {
            for (final int days : new int[]{1, 3, 7}) {
                for (final OeplInkRecorder.Run run : draw(panel[0], panel[1], days)) {
                    assertTrue(run.what + " at " + panel[0] + "x" + panel[1]
                                    + " reaches outside the panel",
                            run.wanted.left >= -1f && run.wanted.top >= -1f
                                    && run.wanted.right <= panel[0] + 1f
                                    && run.wanted.bottom <= panel[1] + 1f);
                }
            }
        }
    }

    /**
     * An appointment from early on one day until late on another covers every day in between,
     * and the reader of the grid has to see all of them. This is the fault as it was reported:
     * Tuesday four in the morning until Thursday eleven at night, drawn down the Tuesday
     * column, cut off at the foot of the panel, and Wednesday and Thursday left empty.
     * <p>
     * An appointment like that is not an hour of a day but days being taken, so it is drawn
     * the way an all day entry is: one box in the band above the grid, spanning the columns of
     * the days it covers.
     */
    @Test
    public void anAppointmentOverSeveralDaysSpansEachDayItCovers() {
        final long start = midnight();
        final List<CalendarEvent> events = new ArrayList<>();
        events.add(event(start + 86400_000L + 4 * 3600_000L,
                start + 3 * 86400_000L + 23 * 3600_000L, 1, "Messe", false));
        // and the days it crosses keep their own appointments, which a box filling a column
        // would have pushed into a second lane and off a grid this narrow
        events.add(event(start + 2 * 86400_000L + 11 * 3600_000L,
                start + 2 * 86400_000L + 12 * 3600_000L, 2, "Markt", false));

        final List<OeplInkRecorder.Run> runs = draw(800, 480, 7, events);
        final float column = (800f - 26f) / 7f;

        assertEquals("the appointment is named once", 1, named(runs, "Messe"));
        assertEquals("and so is the one on a day it covers", 1, named(runs, "Markt"));

        final RectF box = widest(runs);
        assertTrue("one box has to span the three days it covers, not one: " + box,
                box != null && box.width() > column * 2.5f);
        final RectF title = boxOf(runs, "Messe");
        assertTrue("and the name of it belongs in that box: " + title,
                title != null && title.left >= box.left - 1f && title.right <= box.right + 1f);
    }

    /**
     * The same for an appointment that only crosses one midnight: an evening that ends the
     * next morning is two days, and the second of them showed nothing at all.
     */
    @Test
    public void anAppointmentThatRunsPastMidnightReachesTheNextDay() {
        final long start = midnight();
        final List<CalendarEvent> events = new ArrayList<>();
        events.add(event(start + 22 * 3600_000L, start + 86400_000L + 2 * 3600_000L, 1,
                "Nacht", false));

        final List<OeplInkRecorder.Run> runs = draw(800, 480, 3, events);
        final float column = (800f - 26f) / 3f;
        final RectF box = widest(runs);
        assertEquals("the night is named once", 1, named(runs, "Nacht"));
        assertTrue("and its box covers both of its days: " + box,
                box != null && box.width() > column * 1.5f);
    }

    /**
     * An appointment running until midnight ends in the twenty fourth hour of its day, not in
     * the nought the clock reads then. Taken off the clock, the last appointment of the day
     * asked the grid for no room at all and was drawn under the bottom edge of the panel.
     */
    @Test
    public void anAppointmentEndingAtMidnightStillHasItsHours() {
        final long start = midnight();
        final List<CalendarEvent> events = new ArrayList<>();
        events.add(event(start + 21 * 3600_000L, start + 86400_000L, 1, "Spaet", false));

        final List<OeplInkRecorder.Run> runs = draw(800, 480, 3, events);
        assertEquals("the late shift is on the panel", 1, named(runs, "Spaet"));
        final RectF box = boxOf(runs, "Spaet");
        assertTrue("inside the panel: " + box, box != null && box.bottom <= 480f + 1f);
    }

    /**
     * The band is where everything that takes whole days goes, and a week of birthdays fills
     * it. Stacked one entry per row it held three of them and the appointment running through
     * that week was left off the panel altogether, which is how this was reported. Two entries
     * that do not share a day share a row, as two appointments that do not share an hour share
     * a column in the grid below.
     */
    @Test
    public void aBandFullOfWholeDaysKeepsTheAppointmentRunningThroughThem() {
        final long start = midnight();
        final List<CalendarEvent> events = new ArrayList<>();
        events.add(allDay(0, 1, 11, "Oma"));
        events.add(allDay(1, 1, 12, "Muell"));
        events.add(allDay(2, 1, 13, "Putztag"));
        events.add(event(start + 4 * 86400_000L + 4 * 3600_000L,
                start + 6 * 86400_000L + 23 * 3600_000L, 1, "Messe", false));

        for (final int[] panel : new int[][]{{960, 672}, {800, 480}, {400, 300}}) {
            final List<OeplInkRecorder.Run> runs = draw(panel[0], panel[1], 7, events);
            assertEquals("the appointment running through the week is on the "
                            + panel[0] + "x" + panel[1] + " panel", 1, named(runs, "Messe"));
            assertEquals("and so is the first of the days taken", 1, named(runs, "Oma"));
        }
    }

    /** An all day entry as android stores one, counted in days from today. */
    private static CalendarEvent allDay(final int from, final int length, final int id,
                                        final String title) {
        final Calendar local = Calendar.getInstance();
        local.add(Calendar.DAY_OF_MONTH, from);
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        final long begin = utc.getTimeInMillis();
        utc.add(Calendar.DAY_OF_MONTH, length);
        return event(begin, utc.getTimeInMillis(), id, title, true);
    }

    /** How often a title was written, whole or squeezed. */
    private static int named(final List<OeplInkRecorder.Run> runs, final String title) {
        int found = 0;
        for (final OeplInkRecorder.Run run : runs) {
            if (run.text && run.what != null && run.what.contains(title)) {
                found++;
            }
        }
        return found;
    }

    /** Where a title was written. */
    private static RectF boxOf(final List<OeplInkRecorder.Run> runs, final String title) {
        for (final OeplInkRecorder.Run run : runs) {
            if (run.text && run.what != null && run.what.contains(title)) {
                return run.wanted;
            }
        }
        return null;
    }

    /** The widest box on the panel, which is the one an appointment spanning days is drawn as. */
    private static RectF widest(final List<OeplInkRecorder.Run> runs) {
        RectF widest = null;
        for (final OeplInkRecorder.Run run : runs) {
            if (!run.text && "rect".equals(run.what)
                    && (widest == null || run.wanted.width() > widest.width())) {
                widest = run.wanted;
            }
        }
        return widest;
    }

    private static long midnight() {
        final Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    private static List<OeplInkRecorder.Run> draw(final int w, final int h, final int days) {
        return draw(w, h, days, events());
    }

    private static List<OeplInkRecorder.Run> draw(final int w, final int h, final int days,
                                                  final List<CalendarEvent> events) {
        final Bitmap sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
        new OeplCalendarWeekCard(events, days).draw(recorder,
                new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE, OEPLImageCodec.RED,
                        false),
                new RectF(0f, 0f, w, h));
        return recorder.runs();
    }

    /** Days still to come, with something on them, all of it short enough to be drawn whole. */
    private static List<CalendarEvent> events() {
        final Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        final long start = day.getTimeInMillis();
        final List<CalendarEvent> events = new ArrayList<>();
        events.add(event(start + 9 * 3600_000L, start + 10 * 3600_000L, 1, "Sport", false));
        events.add(event(start + 13 * 3600_000L, start + 15 * 3600_000L, 2, "Arzt", false));
        events.add(event(start + 3 * 86400_000L + 11 * 3600_000L,
                start + 3 * 86400_000L + 12 * 3600_000L, 3, "Markt", false));
        return events;
    }

    private static CalendarEvent event(final long begin, final long end, final int id,
                                       final String title, final boolean allDay) {
        return new CalendarEvent(begin, end, id, id, title, "", "", "cal", "acc", 0, 0,
                allDay, "me", "LOCAL", "1", null, 0, 0);
    }
}
