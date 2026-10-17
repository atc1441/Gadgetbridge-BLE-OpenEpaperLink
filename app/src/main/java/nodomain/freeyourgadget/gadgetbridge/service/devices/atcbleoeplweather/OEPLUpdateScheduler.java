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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils;

/**
 * Decides when the link to a weather tag comes up, and is built around the fact that the phone
 * spends most of its time asleep with the app not running at all.
 * <p>
 * Two things drive an update. The first is an {@link AlarmManager} alarm, armed with
 * {@code setAndAllowWhileIdle} so that Doze cannot sit on it the way it sits on a periodic
 * WorkManager job - which is exactly why updates used to arrive hours late or not at all. The
 * second, and the one that makes this autonomous rather than merely punctual, is a batched
 * background scan registered with the Bluetooth controller: the tag is heard whenever it
 * advertises, the phone is woken for it, and an overdue picture is pushed at that moment instead
 * of at some arbitrary later time when the tag may be out of reach again.
 * <p>
 * A run refreshes the weather before it brings the radio up, never after, because the tag hangs
 * up by itself after a few seconds of silence and will not wait for an HTTP request.
 * {@link ATCBLEOEPLWeatherDeviceSupport} then renders, pushes and drops the link.
 */
public final class OEPLUpdateScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLUpdateScheduler.class);

    public static final String ACTION_UPDATE =
            "nodomain.freeyourgadget.gadgetbridge.action.OEPL_WEATHER_UPDATE";
    public static final String ACTION_ADVERTISEMENT =
            "nodomain.freeyourgadget.gadgetbridge.action.OEPL_WEATHER_ADVERTISEMENT";
    public static final String EXTRA_ADDRESS = "oepl_address";
    public static final String EXTRA_REASON = "oepl_reason";

    public static final String REASON_TIMER = "timer";
    public static final String REASON_ADVERTISEMENT = "advertisement";
    public static final String REASON_LIVE = "live";
    public static final String REASON_MANUAL = "manual";
    public static final String REASON_BOOT = "boot";
    public static final String REASON_BLUETOOTH = "bluetooth";
    public static final String REASON_FOLLOW_UP = "follow-up";
    /** Putting the card back on a display whose firmware has just been replaced. */
    public static final String REASON_RESTORE = "restore";

    /**
     * How long a run may spend fetching content before the radio comes up. Everything happens
     * inside a broadcast that has been kept alive on purpose, so this has to stay well under the
     * ten seconds Android grants one.
     */
    private static final long WARMUP_BUDGET_MILLIS = 7500L;
    /** Gives a burst of notifications a moment to settle before the radio comes up. */
    private static final long LIVE_COALESCE_MILLIS = 12_000L;
    /** Nothing is ever armed closer than this, so a run cannot trigger itself in a loop. */
    private static final long MIN_DELAY_MILLIS = 45_000L;
    /**
     * After a run that led nowhere, try again on this ladder instead of waiting a whole cycle.
     * It starts within the minute, because the usual reason for a run leading nowhere is that the
     * tag happened to be asleep, and climbs from there, because the other reason is that it is
     * not in range at all and there is nothing to be gained from asking every minute all day.
     */
    private static final int[] RETRY_SECONDS = {60, 120, 300, 600, 1200, 1800};
    /**
     * After this many fruitless attempts in a row, a probe is made with a single connection
     * attempt rather than the configured number.
     * <p>
     * Several attempts in one run are there for a display that is present but was between two
     * advertisements, and they are worth their cost for exactly that. A display that has been
     * missing for twenty minutes is in all likelihood not in the flat at all, and since an
     * attempt that finds nobody costs the full connection window, three of them cost three
     * times as much battery for the same answer. The gap between probes does not change, so
     * nothing comes back any later; the first one that gets through clears the counter and the
     * full number of attempts is back.
     */
    private static final int LONE_ATTEMPT_AFTER = 4;
    /** Only used when the tag has to be heard before connecting, which is not the default. */
    private static final long SCAN_MILLIS = 20_000L;
    /** Lets the Bluetooth stack settle between two connection attempts. */
    private static final long ATTEMPT_PAUSE_MILLIS = 4000L;
    /** Added to the connection window, so the timeout inside the stack always fires first. */
    private static final long CONNECT_GRACE_MILLIS = 4000L;
    /** How long a connected tag is given to take a picture before the attempt is let go of. */
    private static final long TRANSFER_BUDGET_MILLIS = 95_000L;
    private static final long POLL_MILLIS = 400L;

    /**
     * Displays that are currently being connected to. A run that arrives while one of these is
     * still being tried would otherwise cut the attempt short by disconnecting underneath it.
     */
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private OEPLUpdateScheduler() {
    }

    // ------------------------------------------------------------------ arming

    /**
     * Arms the next update for a display and makes sure the controller is listening for it.
     * Safe to call repeatedly; the alarm is replaced rather than duplicated.
     */
    public static void reschedule(final Context context, final GBDevice device) {
        if (device == null) {
            return;
        }
        armNext(context, device.getAddress(), false);
        registerAdvertisementScan(context, device.getAddress());
    }

    /** Which of the three questions an arming answers; see {@link #dueAt}. */
    private enum When {
        /** A cycle finished, so the next one is one interval after it. */
        PLANNED,
        /** An attempt led nowhere, so come back on the ladder. */
        RETRY,
        /** There was nothing to send at all, so do not keep asking. */
        IDLE
    }

    /**
     * Works out when the next picture is due and arms the alarm for it.
     *
     * @param afterFailure true to use the retry ladder instead of the configured interval
     */
    public static void armNext(final Context context, final String address,
                               final boolean afterFailure) {
        armNext(context, address, afterFailure ? When.RETRY : When.PLANNED);
    }

    /**
     * Comes back after a whole interval and no sooner, for a run that found nothing to send: a
     * picture card without a picture is not a display that is failing, and asking it again every
     * minute would cost the phone's battery for a display that is working perfectly.
     */
    public static void armIdle(final Context context, final String address) {
        armNext(context, address, When.IDLE);
    }

    private static void armNext(final Context context, final String address, final When when) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final long now = System.currentTimeMillis();
        final long planned = dueAt(prefs, now, when);
        // and never inside the quiet window: what fell due in the night waits for the morning
        final long due = OEPLQuietHours.shift(prefs, planned);

        final AlarmManager alarms =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due,
                    updateIntent(context, address, REASON_TIMER));
            prefs.edit().putLong(OEPLStatusStore.NEXT_DUE_MILLIS, due).apply();
            LOG.info("{} is due again in {} s ({}{})", address, (due - now) / 1000,
                    when.name().toLowerCase(java.util.Locale.ROOT),
                    due != planned ? ", after the quiet hours" : "");
        } catch (final Exception e) {
            LOG.error("could not arm the update alarm for {}", address, e);
        }
    }

    /**
     * When the next attempt should happen, before the quiet window gets a say.
     * <p>
     * The retry ladder is the interesting part. It starts within the minute, because the usual
     * reason an attempt leads nowhere is that the tag was between two advertisements, and climbs
     * from there - but it stops climbing at the catch-up gap rather than at the interval. That
     * is what lets the interval be a day: a display that owes a picture is asked again every
     * quarter of an hour until it takes one, instead of being written off until tomorrow, while
     * a display that is simply up to date is not touched at all for twenty-four hours. The
     * interval still caps it as well, so a five minute display is never left waiting longer
     * than its own interval.
     */
    private static long dueAt(final SharedPreferences prefs, final long now, final When when) {
        final long interval = OEPLPrefs.updateIntervalMillis(prefs);
        if (when == When.IDLE) {
            return now + Math.max(MIN_DELAY_MILLIS, interval);
        }
        if (when == When.PLANNED) {
            final long lastRun = prefs.getLong(OEPLStatusStore.LAST_RUN_MILLIS, 0L);
            return Math.max(now + MIN_DELAY_MILLIS, lastRun + interval);
        }
        final long catchUp = OEPLPrefs.catchUpMillis(prefs);
        final int failures = prefs.getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 1);
        final int step = Math.min(Math.max(1, failures), RETRY_SECONDS.length) - 1;
        // the top of the ladder is the catch-up gap itself, so that is what it settles on; the
        // steps below it are the quick first looks, and none of them is ever slower than it
        final long ladder = step >= RETRY_SECONDS.length - 1
                ? catchUp
                : Math.min(RETRY_SECONDS[step] * 1000L, catchUp);
        return now + Math.max(MIN_DELAY_MILLIS, Math.min(ladder, interval));
    }

    /**
     * Asks for a refresh triggered by something that just happened on the phone. Pushes are
     * coalesced and rate limited: e-paper takes seconds to redraw and every push costs a radio
     * session, so a burst of notifications must not turn into a burst of updates. The alarm is
     * the Doze-proof kind as well, otherwise a message arriving while the phone sleeps would
     * show up on the panel at some unrelated later time.
     */
    public static void requestLiveUpdate(final Context context, final GBDevice device) {
        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        if (!OEPLPrefs.liveUpdates(prefs)) {
            return;
        }
        if (OEPLQuietHours.covers(prefs, System.currentTimeMillis())) {
            // a message arriving at two in the morning is not worth a panel flashing in a dark
            // room; the alarm for the end of the window is already armed and will carry it
            LOG.debug("{} is in its quiet hours, not pushing a live update",
                    device.getAddress());
            return;
        }
        final long since = System.currentTimeMillis()
                - prefs.getLong(OEPLPrefs.LAST_UPDATE_MILLIS, 0L);
        final long gap = OEPLPrefs.minGapMinutes(prefs) * 60_000L;
        final long delay = Math.max(LIVE_COALESCE_MILLIS, gap - since);
        armIn(context, device.getAddress(), delay, REASON_LIVE, true);
    }

    /** The user asked for it, so go now and do not wait for anything. */
    public static void requestUpdateNow(final Context context, final GBDevice device) {
        final String address = device.getAddress();
        final Thread thread = new Thread(() -> run(context, address, REASON_MANUAL),
                "oepl-update-now");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Comes back shortly for another try. Used when a picture had to be drawn from content that
     * was already stale because the network did not answer in time.
     */
    public static void requestFollowUp(final Context context, final String address,
                                       final long delayMillis) {
        armIn(context, address, Math.max(MIN_DELAY_MILLIS, delayMillis), REASON_FOLLOW_UP, true);
    }

    /**
     * Comes back to put the card on a display that has just been written a firmware.
     * <p>
     * This one is not held by the quiet hours. Replacing a firmware is something somebody asked
     * for, it leaves the panel showing whatever the new firmware drew on it, and a display left
     * like that until seven in the morning would be a worse night than the one the quiet hours
     * are meant to protect.
     */
    public static void requestRestore(final Context context, final String address,
                                      final long delayMillis) {
        armIn(context, address, Math.max(MIN_DELAY_MILLIS, delayMillis), REASON_RESTORE, false);
    }

    private static void armIn(final Context context, final String address, final long delayMillis,
                              final String reason, final boolean quietMatters) {
        final AlarmManager alarms =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final long asked = System.currentTimeMillis() + Math.max(1000L, delayMillis);
        final long due = quietMatters ? OEPLQuietHours.shift(prefs, asked) : asked;
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due,
                    updateIntent(context, address, reason));
            // the same pending intent as the interval alarm, so this one has replaced it and is
            // now when the display is next due - which is also what tells an advertisement
            // heard in the meantime that it is worth pushing straight away
            prefs.edit().putLong(OEPLStatusStore.NEXT_DUE_MILLIS, due).apply();
            LOG.debug("{} update for {} armed in {} ms", reason, address, delayMillis);
        } catch (final Exception e) {
            LOG.error("could not arm a {} update for {}", reason, address, e);
        }
    }

    public static void cancel(final Context context, final String address) {
        final AlarmManager alarms =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) {
            try {
                alarms.cancel(updateIntent(context, address, REASON_TIMER));
            } catch (final Exception e) {
                LOG.debug("could not cancel the alarm for {}", address, e);
            }
        }
        unregisterAdvertisementScan(context, address);
    }

    /**
     * Restores every display's schedule, after a reboot, after Bluetooth came back, or when the
     * app was started again. Nothing here assumes the app was running before.
     */
    public static void rescheduleAll(final Context context, final String reason) {
        try {
            final List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
            for (final GBDevice device : devices) {
                if (device.getType() != DeviceType.OEPL_WEATHER_DISPLAY) {
                    continue;
                }
                LOG.info("restoring the schedule of {} after {}", device.getAddress(), reason);
                armNext(context, device.getAddress(), false);
                registerAdvertisementScan(context, device.getAddress());
            }
        } catch (final Exception e) {
            LOG.error("could not restore the display schedules", e);
        }
    }

    private static PendingIntent updateIntent(final Context context, final String address,
                                              final String reason) {
        final Intent intent = new Intent(ACTION_UPDATE)
                .setPackage(BuildConfig.APPLICATION_ID)
                // the data makes one pending intent per display, otherwise they would replace
                // each other and only the last display would ever be updated
                .setData(Uri.parse("oepl-update://" + address))
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_REASON, reason);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------------------ the run itself

    /**
     * One update attempt, start to finish. Blocks for as long as the content refresh takes, so
     * it is called either from a broadcast that has been kept alive or from its own thread.
     */
    public static void run(final Context context, final String address, final String reason) {
        final GBDevice device = GBApplication.app().getDeviceManager().getDeviceByAddress(address);
        if (device == null) {
            LOG.info("{} is no longer paired, dropping its schedule", address);
            cancel(context, address);
            return;
        }
        OEPLStatusStore.recordTrigger(address, reason);

        if (device.isConnected()) {
            LOG.debug("{} is already connected, letting that run its course", address);
            armNext(context, address, false);
            return;
        }
        if (IN_FLIGHT.contains(address)) {
            // a previous run is still working through its attempts; cutting in would only
            // disconnect underneath it, and it arms the next alarm itself when it is done
            LOG.debug("{} is still being tried, letting that attempt finish", address);
            armNext(context, address, true);
            return;
        }
        if (!bluetoothReady(context)) {
            LOG.info("Bluetooth is off, nothing to do for {} yet", address);
            OEPLStatusStore.recordFailure(address, "bluetooth off");
            armNext(context, address, true);
            return;
        }

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);

        if (onlyTheClockAsksForThis(reason, prefs)
                && OEPLQuietHours.covers(prefs, System.currentTimeMillis())) {
            // the window is normally kept by the arming itself, so this catches what arrives
            // from the outside while it lasts: an advertisement heard across the flat, a
            // notification, an alarm that was armed before the window was moved
            final long until = OEPLQuietHours.endOfWindow(prefs, System.currentTimeMillis());
            LOG.info("{} is in its quiet hours, {} min to go", address,
                    (until - System.currentTimeMillis()) / 60_000);
            OEPLStatusStore.recordWaiting(address, "quiet until "
                    + OEPLQuietHours.asTime(OEPLQuietHours.endMinute(prefs)));
            armNext(context, address, false);
            return;
        }

        final String waitingFor = CardContentFactory.waitingForSource(prefs);
        if (waitingFor != null && !OEPLActions.pending(prefs)
                && !OEPLFirmware.worthTrying(prefs)) {
            // not even worth waking the display for: there is nothing to put on it yet. Unless
            // it has been asked to blink or to go to sleep - then the connection is the point
            // and the picture is not.
            LOG.info("{} for {}, skipping this update", waitingFor, address);
            OEPLStatusStore.recordWaiting(address, waitingFor);
            armIdle(context, address);
            return;
        }

        // Fetched before the link comes up, never after: the tag hangs up by itself after a few
        // seconds of silence, so a picture must be ready to send the moment it answers. A run
        // that only carries a command sends no picture, so it skips all of this and gets on
        // with it - somebody is standing in front of the display waiting for it to blink.
        if (!OEPLActions.pending(prefs) && !OEPLFirmware.worthTrying(prefs)) {
            try {
                OEPLContentSync.freshen(prefs, WARMUP_BUDGET_MILLIS, address);
            } catch (final Exception e) {
                LOG.warn("content refresh failed, showing what we have", e);
            }
        }

        // Listening first costs a few seconds and tells us whether the tag is there at all, but
        // a background scan is throttled hard by Android and regularly hears nothing even when
        // the tag is right there - which is why this is off unless asked for, and why a silent
        // scan no longer stops the attempt.
        if (OEPLPrefs.requireAdvertisement(prefs) && hasScanPermission(context)) {
            final long started = System.currentTimeMillis();
            final Advertisement seen = listenFor(context, address, SCAN_MILLIS);
            OEPLStatusStore.recordScan(address, seen != null, seen != null ? seen.rssi : 0,
                    System.currentTimeMillis() - started);
            if (seen == null) {
                LOG.info("{} did not show up within {} s, trying again later",
                        address, SCAN_MILLIS / 1000);
                OEPLStatusStore.recordFailure(address, "not in range");
                armNext(context, address, true);
                return;
            }
            storeAdvertisement(address, seen);
        }

        OEPLStatusStore.recordAttempt(address);
        // armed as a retry straight away: should the attempts below lead nowhere, or the process
        // be recycled before they finish, this is what brings us back. A run that does finish
        // re-arms the proper interval through noteSuccess.
        armNext(context, address, true);
        connectAndPush(context, device, reason);
    }

    /**
     * Whether this run is one the quiet window may hold back.
     * <p>
     * Only the automatic ones are: the timer, an advertisement that was heard, a notification
     * that arrived, the schedule being restored. An update asked for by hand, a command and a
     * firmware are not - somebody who presses a button at midnight means it, and a display that
     * has been promised a new firmware should not have to wait for the morning to get it.
     */
    private static boolean onlyTheClockAsksForThis(final String reason,
                                                   final SharedPreferences prefs) {
        return !REASON_MANUAL.equals(reason)
                && !REASON_RESTORE.equals(reason)
                && !OEPLActions.pending(prefs)
                && !OEPLFirmware.worthTrying(prefs);
    }

    // ------------------------------------------------------------------ reaching the tag

    /**
     * Tries to get the picture onto the panel, with as many attempts as the display is configured
     * for, and keeps the CPU awake for all of it.
     * <p>
     * A tag is asleep between two advertisements, so a connection cannot be opened at an
     * arbitrary moment: the radio has to keep trying until the tag speaks up. That is why a
     * single attempt is given a window of its own, and why a failed attempt is simply repeated -
     * waiting minutes for the next alarm because the tag happened to be in a gap is exactly what
     * made updates go missing. The wake lock matters just as much: without it the phone can go
     * back to sleep in the middle of an attempt, and on a tag that hangs up by itself a few
     * seconds later that means the picture is lost.
     */
    private static void connectAndPush(final Context context, final GBDevice device,
                                       final String reason) {
        final String address = device.getAddress();
        if (!IN_FLIGHT.add(address)) {
            LOG.debug("{} is already being connected to", address);
            return;
        }
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final long window = OEPLPrefs.connectWindowMillis(prefs);
        final int attempts = probeAttempts(prefs);
        final long budget = attempts * (window + CONNECT_GRACE_MILLIS + ATTEMPT_PAUSE_MILLIS)
                + TRANSFER_BUDGET_MILLIS + 10_000L;

        final Thread thread = new Thread(() -> {
            final PowerManager.WakeLock lock =
                    AndroidUtils.acquirePartialWakeLock(context, "oepl-" + address, budget);
            try {
                attemptLoop(context, device, prefs, reason, window, attempts);
            } catch (final Exception e) {
                LOG.error("the attempt to reach {} failed", address, e);
                armNext(context, address, true);
            } finally {
                IN_FLIGHT.remove(address);
                release(lock);
            }
        }, "oepl-connect");
        thread.setDaemon(true);
        thread.start();
    }

    /** How hard this particular run tries; see {@link #LONE_ATTEMPT_AFTER}. */
    static int probeAttempts(final SharedPreferences prefs) {
        final int attempts = OEPLPrefs.connectAttempts(prefs);
        return prefs.getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0) >= LONE_ATTEMPT_AFTER
                ? 1 : attempts;
    }

    private static void attemptLoop(final Context context, final GBDevice device,
                                    final SharedPreferences prefs, final String reason,
                                    final long window, final int attempts) {
        final String address = device.getAddress();
        int tried = 0;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (!bluetoothReady(context)) {
                LOG.info("Bluetooth went away while trying to reach {}", address);
                break;
            }
            final GBDevice current =
                    GBApplication.app().getDeviceManager().getDeviceByAddress(address);
            if (current == null) {
                LOG.info("{} is no longer paired", address);
                return;
            }
            if (current.getState().equalsOrHigherThan(GBDevice.State.CONNECTED)) {
                awaitTransfer(context, address, prefs);
                return;
            }
            tried = attempt;
            if (current.getState().equalsOrHigherThan(GBDevice.State.CONNECTING)) {
                LOG.debug("a connection to {} is already coming up, waiting for it", address);
            } else {
                LOG.info("attempt {} of {} to reach {} ({}), waiting up to {} s",
                        attempt, attempts, address, reason, window / 1000);
                try {
                    // the support class renders, pushes and disconnects again on its own
                    GBApplication.deviceService(device).connect();
                } catch (final Exception e) {
                    LOG.error("could not start a connection to {}", address, e);
                    OEPLStatusStore.recordFailure(address, "connect failed");
                    armNext(context, address, true);
                    return;
                }
            }
            if (awaitConnection(address, window + CONNECT_GRACE_MILLIS)) {
                LOG.info("{} answered on attempt {} of {}", address, attempt, attempts);
                awaitTransfer(context, address, prefs);
                return;
            }
            LOG.info("{} stayed quiet for {} s on attempt {} of {}",
                    address, (window + CONNECT_GRACE_MILLIS) / 1000, attempt, attempts);
            // the stack is still holding a half open attempt, and a fresh connect would be
            // ignored while it does
            try {
                GBApplication.deviceService(device).disconnect();
            } catch (final Exception e) {
                LOG.debug("could not drop the attempt to {}", address, e);
            }
            if (attempt < attempts) {
                sleep(ATTEMPT_PAUSE_MILLIS);
            }
        }
        OEPLStatusStore.recordNoContact(address, tried);
        armNext(context, address, true);
    }

    /** Waits for the tag to pick up, which is the one thing a run cannot force. */
    private static boolean awaitConnection(final String address, final long millis) {
        final long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            final GBDevice current =
                    GBApplication.app().getDeviceManager().getDeviceByAddress(address);
            if (current == null) {
                return false;
            }
            if (current.getState().equalsOrHigherThan(GBDevice.State.CONNECTED)) {
                return true;
            }
            if (!sleep(POLL_MILLIS)) {
                return false;
            }
        }
        return false;
    }

    /**
     * Holds the CPU until the panel reports that it has the picture, which is the moment the
     * status store records a finished run. Letting the phone sleep before that would strand a
     * transfer halfway through.
     */
    private static void awaitTransfer(final Context context, final String address,
                                      final SharedPreferences prefs) {
        final long started = System.currentTimeMillis();
        final long deadline = started + TRANSFER_BUDGET_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (prefs.getLong(OEPLStatusStore.LAST_RUN_MILLIS, 0L) >= started) {
                LOG.info("{} took the picture after {} ms", address,
                        System.currentTimeMillis() - started);
                return;
            }
            if (!sleep(POLL_MILLIS)) {
                return;
            }
        }
        LOG.info("{} did not report a finished transfer within {} s",
                address, TRANSFER_BUDGET_MILLIS / 1000);
    }

    private static boolean sleep(final long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void release(final PowerManager.WakeLock lock) {
        try {
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        } catch (final Exception e) {
            LOG.debug("could not release the wake lock", e);
        }
    }

    /** Called once a picture has been accepted, or found to be the one already on the panel. */
    public static void noteSuccess(final Context context, final String address) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putLong(OEPLStatusStore.LAST_RUN_MILLIS, System.currentTimeMillis())
                .putInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0)
                .apply();
        armNext(context, address, false);
    }

    /** Called when an attempt ended without the panel taking a picture. */
    public static void noteFailure(final Context context, final String address) {
        armNext(context, address, true);
    }

    // ------------------------------------------------------------------ hearing the tag

    /**
     * Registers a batched scan for one display with the Bluetooth controller, delivered by
     * broadcast. This is what the system is for: the controller collects what it hears while the
     * phone sleeps and hands it over in one go, so the app learns the tag is reachable without
     * ever having run a scan itself, and an overdue picture goes out at that moment.
     * <p>
     * Only used when the controller can do the batching in hardware. Without that, a permanent
     * scan would have to be run in software and would cost the phone's battery all day, which is
     * not a trade worth making for a display that also has a timer.
     */
    @SuppressLint("MissingPermission")
    public static void registerAdvertisementScan(final Context context, final String address) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // broadcast delivery of scan results needs Oreo
        }
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        if (!OEPLPrefs.opportunisticUpdates(prefs)) {
            unregisterAdvertisementScan(context, address);
            return;
        }
        if (!hasScanPermission(context)) {
            LOG.info("no scan permission, cannot listen for {} in the background", address);
            return;
        }
        final BluetoothLeScanner scanner = scanner(context);
        final BluetoothAdapter adapter = adapter(context);
        if (scanner == null || adapter == null) {
            return;
        }
        if (!adapter.isOffloadedScanBatchingSupported()) {
            LOG.info("this phone cannot batch scans in hardware, staying with the timer alone");
            OEPLStatusStore.recordOpportunistic(address, false);
            return;
        }

        // A quarter of the update interval, but never slower than the catch-up gap: often enough
        // that a picture is never much later than it should be, rare enough that the phone is
        // barely woken for it. The catch-up gap is what matters on a display set to once a day -
        // this listening is how an owed picture goes out minutes after the front door, rather
        // than at whatever hour the interval happens to come round.
        final long interval = OEPLPrefs.updateIntervalMillis(prefs);
        final long base = Math.min(interval / 4, OEPLPrefs.catchUpMillis(prefs));
        final long reportDelay = Math.max(2 * 60_000L, Math.min(10 * 60_000L, base));

        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(reportDelay)
                .build();
        final ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(address).build();
        try {
            final int status = scanner.startScan(Collections.singletonList(filter), settings,
                    advertisementIntent(context, address));
            final boolean running = status == 0;
            OEPLStatusStore.recordOpportunistic(address, running);
            LOG.info("background listening for {} {} (batch every {} s)", address,
                    running ? "registered" : "refused with " + status, reportDelay / 1000);
        } catch (final Exception e) {
            LOG.error("could not register background listening for {}", address, e);
            OEPLStatusStore.recordOpportunistic(address, false);
        }
    }

    @SuppressLint("MissingPermission")
    public static void unregisterAdvertisementScan(final Context context, final String address) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !hasScanPermission(context)) {
            return;
        }
        final BluetoothLeScanner scanner = scanner(context);
        if (scanner == null) {
            return;
        }
        try {
            scanner.stopScan(advertisementIntent(context, address));
        } catch (final Exception e) {
            LOG.debug("could not stop background listening for {}", address, e);
        }
        OEPLStatusStore.recordOpportunistic(address, false);
    }

    private static PendingIntent advertisementIntent(final Context context, final String address) {
        final Intent intent = new Intent(ACTION_ADVERTISEMENT)
                .setPackage(BuildConfig.APPLICATION_ID)
                .setData(Uri.parse("oepl-advertisement://" + address))
                .putExtra(EXTRA_ADDRESS, address);
        // mutable, because the system fills the scan results into it before delivering
        final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /**
     * A batch of advertisements the controller collected for us. Everything the tag broadcasts is
     * recorded, and if a picture was due while nobody was listening it goes out now, which is the
     * moment we know for certain the tag can hear us.
     */
    public static void onAdvertisements(final Context context, final String address,
                                        final List<ScanResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        Advertisement best = null;
        for (final ScanResult result : results) {
            if (result.getScanRecord() == null) {
                continue;
            }
            final SparseArray<byte[]> data = result.getScanRecord().getManufacturerSpecificData();
            final byte[] raw = data != null ? data.get(OEPLProtocol.MANUFACTURER_ID) : null;
            Advertisement parsed = Advertisement.parse(raw);
            if (parsed == null) {
                parsed = new Advertisement(); // in range, payload unreadable
            }
            parsed.rssi = result.getRssi();
            best = parsed; // the last one is the most recent
        }
        if (best == null) {
            return;
        }
        OEPLStatusStore.recordScan(address, true, best.rssi, 0);
        storeAdvertisement(address, best);

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final long due = prefs.getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L);
        final long now = System.currentTimeMillis();
        // a minute of slack, so a batch arriving just before the alarm is used instead of
        // waking the phone a second time for the same picture
        if (due > 0 && now + 60_000L >= due) {
            LOG.info("{} is in range and a picture is due, pushing it now", address);
            run(context, address, REASON_ADVERTISEMENT);
        }
    }

    private static void storeAdvertisement(final String address, final Advertisement seen) {
        LOG.info("{} is in range: {} mV, {} C, firmware {}, rssi {}", address,
                seen.batteryMillivolts, seen.temperature, seen.firmwareVersion, seen.rssi);
        OEPLPrefs.storeFromAdvertisement(address, seen);
        OEPLStatusStore.recordAdvertisementCounter(address, seen.counter);
    }

    // ------------------------------------------------------------------ bluetooth plumbing

    @Nullable
    private static BluetoothAdapter adapter(final Context context) {
        final BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    @Nullable
    private static BluetoothLeScanner scanner(final Context context) {
        final BluetoothAdapter adapter = adapter(context);
        return adapter == null || !adapter.isEnabled() ? null : adapter.getBluetoothLeScanner();
    }

    public static boolean bluetoothReady(final Context context) {
        final BluetoothAdapter adapter = adapter(context);
        return adapter != null && adapter.isEnabled();
    }

    public static boolean hasScanPermission(final Context context) {
        final String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? Manifest.permission.BLUETOOTH_SCAN
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** A scan run by the app itself, for the case where the tag has to be heard first. */
    @SuppressLint("MissingPermission")
    @Nullable
    private static Advertisement listenFor(final Context context, final String address,
                                           final long millis) {
        final BluetoothLeScanner scanner = scanner(context);
        if (scanner == null) {
            return null;
        }
        final Advertisement[] result = new Advertisement[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(final int type, final ScanResult scanResult) {
                if (scanResult.getScanRecord() == null) {
                    return;
                }
                final SparseArray<byte[]> data =
                        scanResult.getScanRecord().getManufacturerSpecificData();
                final byte[] raw = data != null ? data.get(OEPLProtocol.MANUFACTURER_ID) : null;
                result[0] = Advertisement.parse(raw);
                if (result[0] == null) {
                    result[0] = new Advertisement(); // in range but unreadable payload
                }
                result[0].rssi = scanResult.getRssi();
                latch.countDown();
            }

            @Override
            public void onScanFailed(final int errorCode) {
                LOG.warn("scan for {} failed with {}", address, errorCode);
                latch.countDown();
            }
        };

        final ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(address).build();
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        try {
            scanner.startScan(Collections.singletonList(filter), settings, callback);
            if (!latch.await(millis, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (final Exception e) {
            LOG.warn("could not scan for {}", address, e);
            return null;
        } finally {
            try {
                scanner.stopScan(callback);
            } catch (final Exception e) {
                LOG.debug("could not stop the scan", e);
            }
        }
        return result[0];
    }

    /**
     * What the tag broadcasts without being asked:
     * version, hardware type, firmware, capabilities, battery, temperature and a counter.
     */
    public static class Advertisement {
        public static final int CAPABILITY_COMPRESSION = 0x02;

        public int version = -1;
        public int hardwareType = -1;
        public int firmwareVersion = -1;
        public int capabilities;
        public int batteryMillivolts = CardRenderer.NO_VALUE;
        public int temperature = CardRenderer.NO_VALUE;
        /** Signal strength of the advertisement we heard, in dBm; 0 when unknown. */
        public int rssi;
        public int counter = -1;

        public boolean supportsCompression() {
            return (capabilities & CAPABILITY_COMPRESSION) != 0;
        }

        public static Advertisement parse(final byte[] raw) {
            if (raw == null || raw.length < 11) {
                return null;
            }
            final Advertisement a = new Advertisement();
            a.version = raw[0] & 0xff;
            a.hardwareType = u16(raw, 1);
            a.firmwareVersion = u16(raw, 3);
            a.capabilities = u16(raw, 5);
            a.batteryMillivolts = u16(raw, 7);
            a.temperature = raw[9]; // signed, degrees celsius
            a.counter = raw[10] & 0xff;
            return a;
        }

        private static int u16(final byte[] b, final int at) {
            return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8);
        }
    }
}
