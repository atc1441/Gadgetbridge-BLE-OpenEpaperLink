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

import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoeplweather.ATCBLEOEPLWeatherCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;

/**
 * The timing side of the weather display: when the next attempt is armed, how a run that leads
 * nowhere shortens that, and that a run which succeeds puts the full interval back.
 * <p>
 * These used to be WorkManager's business and were not testable at all, which is part of why it
 * took so long to notice that Doze was quietly swallowing the schedule.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class OEPLSchedulingTest {

    private static final String ADDRESS = "FF:FC:4F:05:E4:11";

    private Context context;
    private SharedPreferences prefs;
    private ShadowAlarmManager alarms;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences("oepl-test-" + ADDRESS, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        alarms = Shadows.shadowOf(
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        alarms.getScheduledAlarms().clear();
    }

    // --------------------------------------------------------------- the counters behind the ladder

    @Test
    public void aFailedAttemptCountsUpAndASuccessClearsIt() {
        store().edit().clear().apply();

        OEPLStatusStore.recordFailure(ADDRESS, "not in range");
        OEPLStatusStore.recordFailure(ADDRESS, "connect failed");
        assertEquals(2, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));
        assertEquals(2, store().getInt(OEPLStatusStore.COUNT_FAILURES, 0));

        OEPLStatusStore.recordTransfer(ADDRESS, 17664, 2254, true, 7400, "oepl_4");
        assertEquals(0, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));
        assertTrue(store().getLong(OEPLStatusStore.LAST_RUN_MILLIS, 0L) > 0L);
    }

    @Test
    public void aPictureThePanelAlreadyHasStillCountsAsAFinishedRun() {
        store().edit().clear().apply();
        OEPLStatusStore.recordFailure(ADDRESS, "no answer");

        OEPLStatusStore.recordUnchanged(ADDRESS);

        // nothing went over the air, but the cycle completed, so the schedule may relax again
        assertEquals(0, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));
        assertTrue(store().getLong(OEPLStatusStore.LAST_RUN_MILLIS, 0L) > 0L);
    }

    @Test
    public void resettingTheCountersLeavesTheLastResultAlone() {
        store().edit().clear().apply();
        OEPLStatusStore.recordTransfer(ADDRESS, 100, 50, true, 1000, "oepl_4");
        OEPLStatusStore.resetCounters(ADDRESS);

        assertEquals(0, store().getInt(OEPLStatusStore.COUNT_TRANSFERS, 0));
        assertEquals("ok", OEPLStatusStore.lastResult(ADDRESS));
    }

    @Test
    public void anAttemptThatLeavesNoTraceIsWrittenOffByTheNextOne() {
        store().edit().clear().apply();

        // the first attempt has nothing behind it to judge
        OEPLStatusStore.recordAttempt(ADDRESS);
        assertEquals(0, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));

        // the second one notices that the first produced no finished run at all
        OEPLStatusStore.recordAttempt(ADDRESS);
        assertEquals(1, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));
        assertEquals("no contact", OEPLStatusStore.lastResult(ADDRESS));

        // and an attempt that does finish clears the slate again
        OEPLStatusStore.recordTransfer(ADDRESS, 100, 50, true, 1000, "oepl_4");
        OEPLStatusStore.recordAttempt(ADDRESS);
        assertEquals(0, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));
    }

    // --------------------------------------------------------------- arming

    @Test
    public void theNextAttemptIsArmedOneIntervalAfterTheLastFinishedRun() {
        final long now = System.currentTimeMillis();
        store().edit().clear()
                .putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "30")
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS, now)
                .apply();

        OEPLUpdateScheduler.armNext(context, ADDRESS, false);

        final long due = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L);
        assertEquals(now + 30 * 60_000L, due, 2000d);
        assertEquals(1, alarms.getScheduledAlarms().size());
        assertEquals(AlarmManager.RTC_WAKEUP, alarms.getNextScheduledAlarm().type);
    }

    @Test
    public void anOverdueDisplayIsStillGivenABreathingSpaceSoARunCannotLoop() {
        store().edit().clear()
                .putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "15")
                // last finished a day ago, so the interval alone would put it in the past
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS,
                        System.currentTimeMillis() - 24 * 3600_000L)
                .apply();

        OEPLUpdateScheduler.armNext(context, ADDRESS, false);

        final long due = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L);
        assertTrue("must be in the future", due > System.currentTimeMillis());
    }

    @Test
    public void theRetryLadderBacksOffButNeverPastTheInterval() {
        store().edit().clear().putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "60").apply();

        store().edit().putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 1).apply();
        OEPLUpdateScheduler.armNext(context, ADDRESS, true);
        final long first = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)
                - System.currentTimeMillis();

        store().edit().putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 3).apply();
        OEPLUpdateScheduler.armNext(context, ADDRESS, true);
        final long third = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)
                - System.currentTimeMillis();

        assertTrue("the first retry comes back soon", first < 5 * 60_000L);
        assertTrue("later retries back off", third > first);
        assertTrue("but never beyond the configured interval", third <= 60 * 60_000L);
    }

    @Test
    public void aShortIntervalCapsTheRetryRatherThanStretchingIt() {
        store().edit().clear()
                .putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "5")
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 5)
                .apply();

        OEPLUpdateScheduler.armNext(context, ADDRESS, true);

        final long delay = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)
                - System.currentTimeMillis();
        assertTrue("a five minute display must not wait half an hour", delay <= 5 * 60_000L);
    }

    @Test
    public void everyDisplayGetsItsOwnAlarmRatherThanSharingOne() {
        final String other = "FF:FC:4F:05:E4:22";
        OEPLUpdateScheduler.armNext(context, ADDRESS, false);
        OEPLUpdateScheduler.armNext(context, other, false);

        assertEquals("two displays, two alarms", 2, alarms.getScheduledAlarms().size());
    }

    // --------------------------------------------------------------- the content guarantee

    @Test
    public void withoutAPlaceThereIsNothingToFetchAndNothingToWaitFor() {
        store().edit().clear().putString(OEPLPrefs.PREF_WEATHER_LOCATION, "").apply();

        final long started = System.currentTimeMillis();
        final boolean fresh = OEPLContentSync.freshen(store(), 5000L);

        assertFalse(fresh);
        assertTrue("must not sit on the radio link",
                System.currentTimeMillis() - started < 1500L);
    }

    @Test
    public void theRenderBudgetIsShortEnoughForATagThatHangsUpAfterSevenSeconds() {
        assertTrue(OEPLContentSync.RENDER_BUDGET_MILLIS < 5000L);
    }

    @Test
    public void theFirstRetryComesBackWithinTheMinute() {
        store().edit().clear()
                .putString(OEPLPrefs.PREF_UPDATE_INTERVAL, "60")
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 1)
                .apply();

        OEPLUpdateScheduler.armNext(context, ADDRESS, true);

        final long delay = store().getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L)
                - System.currentTimeMillis();
        assertTrue("a tag that was asleep deserves a quick second look, not two minutes",
                delay <= 65_000L);
        assertTrue("but not so quick that it runs into itself", delay > 30_000L);
    }

    // --------------------------------------------------------------- how hard a run tries

    @Test
    public void aConnectionAttemptLastsLongerThanTheTagSleeps() {
        store().edit().clear().apply();

        // the tag advertises every few seconds and is deaf in between, so Android's default of
        // five seconds is barely one advertising interval and misses it more often than not
        assertTrue("one attempt must span several advertising intervals",
                OEPLPrefs.connectWindowMillis(store()) >= 20_000L);
        assertTrue("and a run must not give up after a single one",
                OEPLPrefs.connectAttempts(store()) >= 2);
    }

    @Test
    public void anAbsurdConnectionSettingIsBroughtBackIntoRange() {
        store().edit().clear()
                .putString(OEPLPrefs.PREF_CONNECT_WINDOW, "3600")
                .putString(OEPLPrefs.PREF_CONNECT_ATTEMPTS, "99")
                .apply();
        assertTrue(OEPLPrefs.connectWindowMillis(store()) <= 120_000L);
        assertTrue(OEPLPrefs.connectAttempts(store()) <= 10);

        store().edit().putString(OEPLPrefs.PREF_CONNECT_WINDOW, "nonsense")
                .putString(OEPLPrefs.PREF_CONNECT_ATTEMPTS, "")
                .apply();
        assertEquals("a broken value falls back to the default, not to zero",
                30_000L, OEPLPrefs.connectWindowMillis(store()));
        assertEquals(3, OEPLPrefs.connectAttempts(store()));
    }

    @Test
    public void theQueueIsToldToWaitAsLongAsTheDisplayIsConfiguredFor() {
        store().edit().clear().putString(OEPLPrefs.PREF_CONNECT_WINDOW, "45").apply();
        final GBDevice device = new GBDevice(ADDRESS, "display", null, null,
                DeviceType.OEPL_WEATHER_DISPLAY);

        assertEquals("the connection window has to reach the Bluetooth queue",
                45_000L, new ATCBLEOEPLWeatherCoordinator().getBleConnectTimeout(device));
    }

    @Test
    public void aDisplayThatWasNeverReachedSaysSoRatherThanNothing() {
        store().edit().clear().apply();

        OEPLStatusStore.recordNoContact(ADDRESS, 3);

        assertEquals("no contact in 3 tries",
                store().getString(OEPLStatusStore.LAST_RESULT, null));
        assertEquals("the ladder climbs once per run, so this must not count on its own",
                0, store().getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0));
    }

    @Test
    public void listeningInTheBackgroundIsOnByDefaultAndScanningFirstIsNot() {
        store().edit().clear().apply();
        assertTrue(OEPLPrefs.opportunisticUpdates(store()));
        // a silent background scan says little, so it must not be what stops an attempt
        assertFalse(OEPLPrefs.requireAdvertisement(store()));
    }

    private SharedPreferences store() {
        return nodomain.freeyourgadget.gadgetbridge.GBApplication
                .getDeviceSpecificSharedPrefs(ADDRESS);
    }
}
