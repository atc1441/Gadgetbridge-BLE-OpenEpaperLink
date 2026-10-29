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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.util.Arrays;
import java.util.Calendar;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;

/**
 * A long interval and the night off, which only work together with the two rules that make them
 * bearable: an update that is owed is retried on its own short gap rather than once per interval,
 * and what falls due inside the quiet window is shown the moment the window ends.
 * <p>
 * The whole point of the arrangement is that a display is not a clock. It is up to date when it
 * shows the latest thing, which normally means the moment its owner comes back within reach, and
 * that moment has nothing to do with the hour the interval happens to come round.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OeplScheduleWindowTest {

    private static final String ADDRESS = "FF:FC:4F:05:E4:31";

    private Context context;
    private ShadowAlarmManager alarms;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store().edit().clear().apply();
        alarms = Shadows.shadowOf(
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        alarms.getScheduledAlarms().clear();
    }

    private SharedPreferences store() {
        return GBApplication.getDeviceSpecificSharedPrefs(ADDRESS);
    }

    private long armedIn(final boolean afterFailure) {
        OEPLUpdateScheduler.armNext(context, ADDRESS, afterFailure);
        return store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L) - System.currentTimeMillis();
    }

    // --------------------------------------------------------------- how long an interval may be

    /** A day between two pictures is an offered setting, and an hour is what a new display gets. */
    @Test
    public void aWholeDayIsOnTheList() {
        final String[] values = context.getResources()
                .getStringArray(R.array.oepl_weather_update_interval_values);
        final String[] labels = context.getResources()
                .getStringArray(R.array.oepl_weather_update_interval);
        assertEquals("every value needs its own label", values.length, labels.length);
        assertTrue("a daily display has to be possible: " + Arrays.toString(values),
                Arrays.asList(values).contains("1440"));

        assertEquals("a new display refreshes hourly", 60,
                OEPLPrefs.updateIntervalMinutes(store()));
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "1440").apply();
        assertEquals(24 * 3600_000L, OEPLPrefs.updateIntervalMillis(store()));
    }

    /**
     * The reason a day-long interval is usable at all: an update that is owed is not postponed
     * until tomorrow but tried again on the catch-up gap, which is a quarter of an hour by
     * default and is the only figure the retry ladder flattens out at.
     */
    @Test
    public void anOwedPictureIsRetriedOnItsOwnGapRatherThanOncePerDay() {
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "1440")
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 1).apply();
        final long first = armedIn(true);

        store().edit().putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 99).apply();
        final long late = armedIn(true);

        assertTrue("the first look must come straight back", first <= 65_000L);
        assertTrue("and the hundredth must still be minutes, not hours, away: " + late / 60_000,
                late <= OEPLPrefs.catchUpMillis(store()));
        assertTrue("without turning into a poll", late >= 10 * 60_000L);
    }

    /**
     * A firmware somebody handed out cannot be buried by the ordinary schedule.
     * <p>
     * This is what made a firmware "never happen" after a pull to refresh on the device list:
     * the refresh found the link already up, stepped aside as it should - and armed the next
     * attempt one whole interval away, which on a daily display is tomorrow. With the alarm
     * that far out the display being heard in the meantime does not help either, because an
     * advertisement is only acted on when something is due. So while a display is owed a
     * firmware, nothing arms further out than the catch-up gap.
     */
    @Test
    public void aFirmwareThatIsOwedIsNeverPutOffForAWholeInterval() {
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "1440")
                // a picture went out just now, so the next one is a whole day off
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS, System.currentTimeMillis()).apply();
        final long ordinary = armedIn(false);
        assertTrue("a display with nothing owed keeps its own long interval: " + ordinary,
                ordinary > 12 * 3600_000L);

        OEPLFirmware.arm(ADDRESS, "content://downloads/ATC_BLE_OEPL.bin");
        final long owed = armedIn(false);

        assertTrue("a promised firmware may not wait until tomorrow: " + owed / 60_000 + " min",
                owed <= OEPLPrefs.catchUpMillis(store()) + 1000L);
        assertTrue("and not turn into a poll either", owed >= 45_000L);

        OEPLFirmware.done(ADDRESS);
        assertTrue("and the long interval comes back once it is written", armedIn(false)
                > 12 * 3600_000L);
    }

    /**
     * Nor by the night. A run carrying a firmware is not held back by the quiet window - that is
     * decided elsewhere and deliberately - so arming one for the end of the window would be a
     * promise nothing keeps: seven in the morning would come, the run would go ahead anyway, and
     * the hours in between would have been spent waiting for nothing.
     */
    @Test
    public void aFirmwareIsNotArmedForTheEndOfTheQuietHours() {
        final Calendar now = Calendar.getInstance();
        final int minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        store().edit().putBoolean(OEPLPrefs.PREF_QUIET, true)
                .putString(OEPLPrefs.PREF_QUIET_START,
                        OEPLQuietHours.asTime(minuteOfDay - 60))
                .putString(OEPLPrefs.PREF_QUIET_END, OEPLQuietHours.asTime(minuteOfDay + 240))
                .apply();
        assertTrue("the window has to cover now for this to mean anything",
                OEPLQuietHours.covers(store(), System.currentTimeMillis()));
        assertTrue("an ordinary update waits for the morning", armedIn(false) > 3600_000L);

        OEPLFirmware.arm(ADDRESS, "content://downloads/ATC_BLE_OEPL.bin");

        assertTrue("a firmware does not wait for the morning: " + armedIn(false),
                armedIn(false) <= OEPLPrefs.catchUpMillis(store()) + 1000L);
    }

    /** The gap is the user's to set, and a value that makes no sense is brought into range. */
    @Test
    public void theCatchUpGapIsWhateverWasChosen() {
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "1440")
                .putString(OEPLPrefs.PREF_CATCH_UP, "60")
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 99).apply();
        assertEquals(3600_000L, OEPLPrefs.catchUpMillis(store()));
        final long armed = armedIn(true);
        assertTrue("an hourly catch-up was asked for", armed > 30 * 60_000L);
        assertTrue(armed <= 3600_000L);

        store().edit().putString(OEPLPrefs.PREF_CATCH_UP, "nonsense").apply();
        assertEquals("a broken value falls back rather than stopping the schedule",
                15, OEPLPrefs.catchUpMinutes(store()));
    }

    /** A short interval is still its own ceiling, so catching up can never be slower than it. */
    @Test
    public void aFiveMinuteDisplayIsNeverHeldForAQuarterOfAnHour() {
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "5")
                .putString(OEPLPrefs.PREF_CATCH_UP, "60")
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 99).apply();

        assertTrue(armedIn(true) <= 5 * 60_000L);
    }

    /** A finished update starts the interval again from zero, counted from that moment. */
    @Test
    public void theIntervalStartsOverWhenThePictureGotThrough() {
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "1440")
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 7)
                // owed since yesterday, so the catch-up gap is what is running
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS,
                        System.currentTimeMillis() - 2 * 24 * 3600_000L).apply();
        assertTrue(armedIn(true) <= 15 * 60_000L);

        OEPLUpdateScheduler.noteSuccess(context, ADDRESS);

        final long due = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L);
        assertEquals("a full day from now, not from whenever it was last supposed to be",
                System.currentTimeMillis() + 24 * 3600_000L, due, 5000d);
        assertEquals(0, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, -1));
    }

    /**
     * A display with nothing to show - a picture card without a picture - is left alone for a
     * whole interval instead of being asked again within the minute, which is what the catch-up
     * gap would otherwise do to a display that is not failing at all.
     */
    @Test
    public void nothingToSendIsNotAFailureAndDoesNotTurnIntoAPoll() {
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "60")
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS,
                        System.currentTimeMillis() - 24 * 3600_000L).apply();

        OEPLUpdateScheduler.armIdle(context, ADDRESS);

        final long armed = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)
                - System.currentTimeMillis();
        assertEquals(3600_000L, armed, 5000d);
    }

    /**
     * A display that has been missing for a while is probed with one connection attempt rather
     * than three. The gap between probes is unchanged, so it comes back just as quickly; what
     * changes is that twenty minutes of searching costs a third of the radio.
     */
    @Test
    public void aDisplayThatHasBeenGoneForAWhileIsProbedMoreCheaply() {
        store().edit().putString(OEPLPrefs.PREF_CONNECT_ATTEMPTS, "3").apply();
        assertEquals("a display that is merely asleep gets every attempt it was given",
                3, OEPLUpdateScheduler.probeAttempts(store()));

        store().edit().putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 2).apply();
        assertEquals("two misses is still worth trying properly",
                3, OEPLUpdateScheduler.probeAttempts(store()));

        store().edit().putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 9).apply();
        assertEquals("but a display that has been gone for twenty minutes only gets a look",
                1, OEPLUpdateScheduler.probeAttempts(store()));

        // and the setting itself is untouched, so the next display that answers gets all three
        assertEquals(3, OEPLPrefs.connectAttempts(store()));
    }

    // --------------------------------------------------------------- the quiet hours

    /** Off until it is switched on, and then it is the two times that say what it means. */
    @Test
    public void theNightOffHasToBeAskedFor() {
        assertFalse(OEPLQuietHours.enabled(store()));
        assertEquals("", OEPLQuietHours.describe(store()));

        store().edit().putBoolean(OEPLPrefs.PREF_QUIET, true).apply();
        assertTrue(OEPLQuietHours.enabled(store()));
        assertEquals("22:00-07:00", OEPLQuietHours.describe(store()));

        // a window with no width would mean either nothing or everything, so it means nothing
        store().edit().putString(OEPLPrefs.PREF_QUIET_START, "08:00")
                .putString(OEPLPrefs.PREF_QUIET_END, "08:00").apply();
        assertFalse(OEPLQuietHours.enabled(store()));

        // and a value a hand edited backup broke must not be able to stop every update
        store().edit().putString(OEPLPrefs.PREF_QUIET_START, "oops")
                .putString(OEPLPrefs.PREF_QUIET_END, "07:00").apply();
        assertEquals("22:00-07:00", OEPLQuietHours.describe(store()));
    }

    /** The window runs across midnight, which is the whole reason it needs its own arithmetic. */
    @Test
    public void aWindowAcrossMidnightCoversBothSidesOfIt() {
        store().edit().putBoolean(OEPLPrefs.PREF_QUIET, true)
                .putString(OEPLPrefs.PREF_QUIET_START, "22:00")
                .putString(OEPLPrefs.PREF_QUIET_END, "07:00").apply();

        assertTrue("half past eleven at night", OEPLQuietHours.covers(store(), at(23, 30)));
        assertTrue("three in the morning", OEPLQuietHours.covers(store(), at(3, 0)));
        assertTrue("the minute it starts", OEPLQuietHours.covers(store(), at(22, 0)));
        assertFalse("the minute it ends", OEPLQuietHours.covers(store(), at(7, 0)));
        assertFalse("the middle of the afternoon", OEPLQuietHours.covers(store(), at(15, 0)));

        // and one that does not cross midnight is read the ordinary way round
        store().edit().putString(OEPLPrefs.PREF_QUIET_START, "13:00")
                .putString(OEPLPrefs.PREF_QUIET_END, "14:00").apply();
        assertTrue(OEPLQuietHours.covers(store(), at(13, 30)));
        assertFalse(OEPLQuietHours.covers(store(), at(23, 30)));
    }

    /**
     * What the quiet hours are for, and the part the user asked for twice: an update that comes
     * due in the night is not dropped and not postponed by a whole interval, it is held until
     * the window ends and goes out then.
     */
    @Test
    public void whatFallsDueInTheNightGoesOutTheMomentTheNightIsOver() {
        store().edit().putBoolean(OEPLPrefs.PREF_QUIET, true)
                .putString(OEPLPrefs.PREF_QUIET_START, "22:00")
                .putString(OEPLPrefs.PREF_QUIET_END, "07:00").apply();

        final long inTheNight = at(2, 30);
        final long moved = OEPLQuietHours.shift(store(), inTheNight);
        assertEquals("held until the morning", OEPLQuietHours.endOfWindow(store(), inTheNight),
                moved);
        assertEquals(7, hourOf(moved));
        assertTrue("and not into the following week", moved - inTheNight < 24 * 3600_000L);

        final long inTheAfternoon = at(15, 0);
        assertEquals("an hour nobody is asleep in is left exactly where it was",
                inTheAfternoon, OEPLQuietHours.shift(store(), inTheAfternoon));
    }

    /** The same thing through the alarm, which is where it actually has to happen. */
    @Test
    public void anAlarmIsNeverArmedInsideTheWindow() {
        quietAroundNow();
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "60")
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS,
                        System.currentTimeMillis() - 60 * 60_000L).apply();

        OEPLUpdateScheduler.armNext(context, ADDRESS, false);

        final long due = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L);
        assertFalse("armed in the middle of the quiet hours",
                OEPLQuietHours.covers(store(), due));
        assertEquals("and armed for the end of them, not for the next interval",
                OEPLQuietHours.endOfWindow(store(), System.currentTimeMillis()), due);
        assertEquals(1, alarms.getScheduledAlarms().size());
    }

    /** A retry during the window is held as well, rather than hammering through the night. */
    @Test
    public void aRetryDoesNotWorkItsWayThroughTheNightEither() {
        quietAroundNow();
        store().edit().putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 2).apply();

        OEPLUpdateScheduler.armNext(context, ADDRESS, true);

        assertFalse(OEPLQuietHours.covers(store(),
                store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)));
    }

    /**
     * Something the user is standing in front of the display for is not held back. A blink, a
     * restart or a firmware handed out at midnight means midnight.
     */
    @Test
    public void aCommandIgnoresTheQuietHours() {
        quietAroundNow();
        assertTrue(OEPLQuietHours.covers(store(), System.currentTimeMillis()));

        OEPLActions.arm(store(), OEPLActions.BLINK);
        assertTrue("the display was asked to do something, which is not an update",
                OEPLActions.pending(store()));

        OEPLFirmware.arm(ADDRESS, "content://downloads/ATC_BLE_OEPL.bin");
        assertTrue(OEPLFirmware.pending(store()));
    }

    /**
     * A display that has just been written a firmware is showing whatever the new firmware drew
     * on it, so the card is put back within the minute rather than at the end of the interval -
     * and the quiet hours do not hold that one back either, because a panel left showing the
     * firmware's parting words all night is worse than one update at midnight.
     */
    @Test
    public void afterAFirmwareTheCardComesBackAtOnce() {
        quietAroundNow();
        store().edit().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "1440").apply();

        OEPLUpdateScheduler.requestRestore(context, ADDRESS, 60_000L);

        assertEquals(1, alarms.getScheduledAlarms().size());
        final long armed = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)
                - System.currentTimeMillis();
        assertEquals("the alarm and what the info screen says have to agree",
                alarms.getNextScheduledAlarm().triggerAtTime,
                store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L));
        assertTrue("not in a day and not in the morning, in a minute: " + armed,
                armed <= 70_000L);
        assertTrue("but far enough out for the display to have restarted", armed > 30_000L);
    }

    // --------------------------------------------------------------- helpers

    /** A quiet window that is certain to contain this moment, whenever the test is run. */
    private void quietAroundNow() {
        final Calendar now = Calendar.getInstance();
        final int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        store().edit().putBoolean(OEPLPrefs.PREF_QUIET, true)
                .putString(OEPLPrefs.PREF_QUIET_START, OEPLQuietHours.asTime(minute - 30))
                .putString(OEPLPrefs.PREF_QUIET_END, OEPLQuietHours.asTime(minute + 90))
                .apply();
    }

    private static long at(final int hour, final int minute) {
        final Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, hour);
        when.set(Calendar.MINUTE, minute);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        return when.getTimeInMillis();
    }

    private static int hourOf(final long millis) {
        final Calendar when = Calendar.getInstance();
        when.setTimeInMillis(millis);
        return when.get(Calendar.HOUR_OF_DAY);
    }
}
