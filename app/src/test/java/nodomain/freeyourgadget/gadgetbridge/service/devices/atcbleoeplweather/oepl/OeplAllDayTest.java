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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLImageCodec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OeplDemo;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OeplInkRecorder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CalendarContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The all day entry, on every card that draws one.
 * <p>
 * The fault was reported from the week grid: a holiday that was over on Sunday evening was
 * still drawn across Monday. The cause is not in the week grid though - Android keeps an all
 * day entry as a date and hands that date over as midnight in UTC, and every card here read
 * those two stamps as moments. That makes the same mistake five more times over: in Berlin an
 * entry begins two hours into its first day and ends an hour into the day after its last, and
 * in Chicago it begins five hours before its first day even starts, so an entry for today is
 * stamped with yesterday's date.
 * <p>
 * So the days are checked here rather than the instants, in a handful of zones on either side
 * of UTC and one that is half an hour off it. The time of day the test happens to run at must
 * not matter: everything asserted holds at three in the morning as well as at noon.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplAllDayTest {

    /**
     * Zones on both sides of UTC, far enough out that a day read as a moment lands on the
     * wrong one, plus a half hour zone for the arithmetic.
     */
    private static final String[] ZONES = {"Europe/Berlin", "UTC", "America/Chicago",
            "Pacific/Auckland", "Asia/Kolkata"};

    private TimeZone zoneBefore;

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
        zoneBefore = TimeZone.getDefault();
    }

    @After
    public void putTheZoneBack() {
        TimeZone.setDefault(zoneBefore);
    }

    // ------------------------------------------------------------------ the days themselves

    /** The days an entry is written on are its days, and the day after is not one of them. */
    @Test
    public void anAllDayEntryCoversTheDaysItIsWrittenOnAndNoOthers() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final CalendarEvent holiday = allDay(1, 3, "Urlaub");

            assertEquals(zone + ": it begins on its first day", midnight(1),
                    OeplDays.firstDay(holiday));
            assertEquals(zone + ": and the third day is its last", midnight(3),
                    OeplDays.lastDay(holiday));
            assertEquals(zone + ": so it is over when the fourth day begins", midnight(4),
                    OeplDays.ends(holiday));

            assertFalse(zone + ": not the day before it", OeplDays.coversDay(holiday, midnight(0)));
            for (int day = 1; day <= 3; day++) {
                assertTrue(zone + ": day " + day + " is one of its own",
                        OeplDays.coversDay(holiday, midnight(day)));
            }
            assertFalse(zone + ": and the day after it is not - this is the fault that was "
                    + "reported", OeplDays.coversDay(holiday, midnight(4)));
        }
    }

    /** A holiday that was over yesterday evening is not running today, whatever the hour. */
    @Test
    public void anAllDayEntryThatIsOverIsNotRunningAnyMore() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));

            assertFalse(zone + ": over yesterday", OeplDays.runningNow(allDay(-2, 2, "Urlaub")));
            assertFalse(zone + ": over yesterday", OeplDays.coversToday(allDay(-1, 1, "Urlaub")));
            assertTrue(zone + ": today's own", OeplDays.runningNow(allDay(0, 1, "Flohmarkt")));
            assertTrue(zone + ": one that runs over today",
                    OeplDays.runningNow(allDay(-1, 3, "Urlaub")));
            assertFalse(zone + ": tomorrow's", OeplDays.runningNow(allDay(1, 1, "Markt")));
            assertTrue(zone + ": today's begins today", OeplDays.beginsToday(allDay(0, 2, "x")));
            assertFalse(zone + ": one that began yesterday does not",
                    OeplDays.beginsToday(allDay(-1, 3, "x")));
        }
    }

    /**
     * An appointment that runs until midnight is over that evening. This is the reader's own
     * words for the fault - "it is a mistake that something running until midnight is shown on
     * the next day as well" - and it holds for an entry with hours of its own too.
     */
    @Test
    public void anAppointmentEndingAtMidnightBelongsToTheEveningBeforeIt() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final long evening = midnight(0) + 20 * 3600_000L;
            final CalendarEvent party = timed(evening, midnight(1), "Feier");

            assertEquals(zone + ": it is written on today", midnight(0), OeplDays.firstDay(party));
            assertEquals(zone + ": and today is the last day it is on", midnight(0),
                    OeplDays.lastDay(party));
            assertFalse(zone + ": tomorrow has nothing of it",
                    OeplDays.coversDay(party, midnight(1)));
        }
    }

    /** The clocks changing makes a day 23 or 25 hours long, and none of this may notice. */
    @Test
    public void theDaysOfAnAllDayEntryHoldThroughTheClockChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        // the 25th of october 2026 is the twenty five hour day
        final CalendarEvent holiday = allDayOn(2026, Calendar.OCTOBER, 24, 3, "Herbstferien");

        final Calendar day = Calendar.getInstance();
        day.clear();
        day.set(2026, Calendar.OCTOBER, 24);
        assertEquals("it begins on the 24th", day.getTimeInMillis(),
                OeplDays.firstDay(holiday));
        day.set(2026, Calendar.OCTOBER, 26);
        assertEquals("and the 26th is its last day", day.getTimeInMillis(),
                OeplDays.lastDay(holiday));
        day.set(2026, Calendar.OCTOBER, 27);
        assertEquals("over when the 27th begins", day.getTimeInMillis(), OeplDays.ends(holiday));

        // the 24th, the 25th and the 26th, counted as days rather than as day lengths: the
        // night in the middle of them is an hour longer than the others
        final List<CalendarEvent> one = Collections.singletonList(holiday);
        for (int date = 23; date <= 28; date++) {
            day.set(2026, Calendar.OCTOBER, date);
            final boolean covered = date >= 24 && date <= 26;
            assertEquals("the 24th to the 26th, not the " + date + "th", covered ? 1 : 0,
                    OeplDays.countOnDay(one, day.getTimeInMillis()));
        }
    }

    // ------------------------------------------------------------------ the cards

    /**
     * Today's holiday reads as today's on every card that has a mark for it. Stamped with a
     * date instead, which is what reading the UTC midnight as a moment gives in Chicago, it is
     * a holiday that looks like it is somewhere else in the week.
     */
    @Test
    public void everyCardReadsTodaysHolidayAsTodays() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final List<CalendarEvent> today = Collections.singletonList(
                    allDay(0, 1, "Flohmarkt"));

            assertTrue(zone + ": the appointment list marks today's holiday",
                    drawn(new OeplCalendarListCard(today, 6, "Kalender"), 400, 300)
                            .contains("—"));
            assertTrue(zone + ": the departure board marks today's holiday",
                    drawn(new OeplBoardCard(today, 6, "Termine"), 400, 300)
                            .contains("——"));
            assertTrue(zone + ": the framed list marks today's holiday",
                    drawn(new CalendarContent(today, 6), 400, 300).contains("—"));

            // the hero card writes the weekday rather than a dash, so that is what is checked
            final String weekday = new SimpleDateFormat("EEE", Locale.getDefault())
                    .format(new java.util.Date(midnight(0)));
            assertTrue(zone + ": the hero card's agenda says " + weekday,
                    drawn(new OeplHeroCard(OeplDemo.readings(true, false), today), 800, 480)
                            .contains(weekday));
        }
    }

    /** And a holiday that was over yesterday is not marked as today's by any of them. */
    @Test
    public void noCardCarriesAFinishedHolidayIntoToday() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final List<CalendarEvent> over = Collections.singletonList(allDay(-3, 2, "Urlaub"));

            assertFalse(zone + ": the appointment list",
                    drawn(new OeplCalendarListCard(over, 6, "Kalender"), 400, 300)
                            .contains("—"));
            assertFalse(zone + ": the departure board",
                    drawn(new OeplBoardCard(over, 6, "Termine"), 400, 300)
                            .contains("——"));
            assertFalse(zone + ": the framed list",
                    drawn(new CalendarContent(over, 6), 400, 300).contains("—"));

            // nothing is running, so nothing may be highlighted either
            assertEquals(zone + ": no row of the list is marked as running", 0,
                    accentBars(new OeplCalendarListCard(over, 6, "Kalender"), 400, 300));
        }
    }

    /**
     * The month sheet puts a dot on every day a holiday covers. One dot for the first day of a
     * fortnight away says nothing about the fortnight, which is the question the sheet exists
     * to answer.
     */
    @Test
    public void theMonthSheetMarksEveryDayAHolidayCovers() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final int first = Math.max(2, Calendar.getInstance()
                    .get(Calendar.DAY_OF_MONTH) > 15 ? 2 : 20);
            final Calendar now = Calendar.getInstance();
            final CalendarEvent holiday = allDayOn(now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH), first, 3, "Urlaub");

            assertEquals(zone + ": three days, three dots", 3,
                    dots(new OeplMonthCard(Collections.singletonList(holiday), "Kalender"),
                            400, 300));
            assertEquals(zone + ": and one day, one dot", 1,
                    dots(new OeplMonthCard(Collections.singletonList(
                            allDayOn(now.get(Calendar.YEAR), now.get(Calendar.MONTH), first, 1,
                                    "Flohmarkt")), "Kalender"), 400, 300));
        }
    }

    /** Today's cell is marked when today's holiday is on, and left alone when it is over. */
    @Test
    public void theMonthSheetMarksTodayOnlyForWhatIsOnToday() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));

            assertEquals(zone + ": today's holiday is in today's cell", 1,
                    dotsInTodaysCell(Collections.singletonList(allDay(0, 1, "Flohmarkt"))));
            assertEquals(zone + ": one that ended yesterday is not", 0,
                    dotsInTodaysCell(Collections.singletonList(allDay(-2, 2, "Urlaub"))));
            assertEquals(zone + ": one that starts tomorrow is not", 0,
                    dotsInTodaysCell(Collections.singletonList(allDay(1, 2, "Urlaub"))));
            assertEquals(zone + ": one that runs over today is", 1,
                    dotsInTodaysCell(Collections.singletonList(allDay(-1, 3, "Urlaub"))));
        }
    }

    /**
     * The week grid, where this was reported: a holiday takes the columns of its own days and
     * not the one after them.
     */
    @Test
    public void theWeekGridKeepsAHolidayInsideItsOwnDays() {
        for (final String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            final long[] starts = OeplCalendarWeekCard.dayStarts(Calendar.getInstance(), 7);

            final CalendarEvent holiday = allDay(1, 3, "Urlaub");
            assertEquals(zone + ": it begins in tomorrow's column", 1,
                    OeplCalendarWeekCard.column(starts, OeplDays.firstDay(holiday)));
            assertEquals(zone + ": and ends three columns along", 3,
                    OeplCalendarWeekCard.column(starts, OeplDays.lastDay(holiday)));

            final CalendarEvent over = allDay(-3, 2, "Urlaub");
            assertEquals(zone + ": one that is over is behind the window", -1,
                    OeplCalendarWeekCard.column(starts, OeplDays.lastDay(over)));

            final CalendarEvent ahead = allDay(9, 1, "Markt");
            assertEquals(zone + ": one beyond the window is ahead of it", 7,
                    OeplCalendarWeekCard.column(starts, OeplDays.firstDay(ahead)));
        }
    }

    // ------------------------------------------------------------------ what was drawn

    /** The text of everything the card drew. */
    private static List<String> drawn(final CardContent card, final int w, final int h) {
        final List<String> text = new ArrayList<>();
        for (final OeplInkRecorder.Run run : record(card, w, h)) {
            if (run.text) {
                text.add(run.what);
            }
        }
        return text;
    }

    /** How many rows were filled with the accent colour, which is how "now" is marked. */
    private static int accentBars(final CardContent card, final int w, final int h) {
        int bars = 0;
        for (final OeplInkRecorder.Run run : record(card, w, h)) {
            if (!run.text && run.filled && run.color == (0xff000000 | OEPLImageCodec.RED)
                    && run.box.width() > w * 0.8f) {
                bars++;
            }
        }
        return bars;
    }

    /** The dots on a month sheet: the small squares, which nothing else on it is. */
    private static int dots(final CardContent card, final int w, final int h) {
        int dots = 0;
        for (final OeplInkRecorder.Run run : record(card, w, h)) {
            if (!run.text && run.filled && run.box.width() <= 8f && run.box.height() <= 8f) {
                dots++;
            }
        }
        return dots;
    }

    /**
     * The dots inside today's cell. Today's cell is the one filled with the accent colour,
     * which is how the sheet marks the day one is standing in, so it can be found without
     * repeating the card's own arithmetic.
     */
    private static int dotsInTodaysCell(final List<CalendarEvent> events) {
        final List<OeplInkRecorder.Run> runs =
                record(new OeplMonthCard(events, "Kalender"), 400, 300);
        RectF today = null;
        for (final OeplInkRecorder.Run run : runs) {
            if (!run.text && run.filled && run.color == (0xff000000 | OEPLImageCodec.RED)
                    && run.box.width() > 8f) {
                today = run.box;
            }
        }
        assertTrue("the sheet has to mark today somewhere", today != null);
        int dots = 0;
        for (final OeplInkRecorder.Run run : runs) {
            if (!run.text && run.filled && run.box.width() <= 8f
                    && today.contains(run.box.centerX(), run.box.centerY())) {
                dots++;
            }
        }
        return dots;
    }

    private static List<OeplInkRecorder.Run> record(final CardContent card, final int w,
                                                    final int h) {
        final Bitmap sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
        card.draw(recorder, new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                OEPLImageCodec.RED, false), new RectF(0f, 0f, w, h));
        return recorder.runs();
    }

    // ------------------------------------------------------------------ the entries

    /** The midnight a day begins with, counted from today in days. */
    private static long midnight(final int daysFromToday) {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, daysFromToday);
        return c.getTimeInMillis();
    }

    /**
     * An all day entry the way the calendar provider hands one over: its first date and the
     * date after its last, both as midnight in UTC.
     */
    private static CalendarEvent allDay(final int daysFromToday, final int length,
                                        final String title) {
        final Calendar local = Calendar.getInstance();
        local.setTimeInMillis(midnight(daysFromToday));
        return allDayOn(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH), length, title);
    }

    private static CalendarEvent allDayOn(final int year, final int month, final int day,
                                          final int length, final String title) {
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month, day, 0, 0, 0);
        final long begin = utc.getTimeInMillis();
        utc.add(Calendar.DAY_OF_MONTH, length);
        return event(begin, utc.getTimeInMillis(), title, true);
    }

    private static CalendarEvent timed(final long begin, final long end, final String title) {
        return event(begin, end, title, false);
    }

    private static CalendarEvent event(final long begin, final long end, final String title,
                                       final boolean allDay) {
        return new CalendarEvent(begin, end, Math.abs(title.hashCode() % 1000), 1, title, "", "",
                "cal", "acc", 0, 0, allDay, "me", "LOCAL", "1", null, 0, 0);
    }
}
