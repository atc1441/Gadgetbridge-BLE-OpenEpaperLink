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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * Keeps the record of what happened with a display, so the app can show more than "connected"
 * or "not connected".
 * <p>
 * A weather tag is out of reach almost all of the time, which makes everything that happened
 * during the last few seconds of radio the only thing there is to go on: how loud it was heard,
 * how many bytes went over, how long that took, whether the picture was accepted, and what it
 * looked like. All of it lands in the device's own preferences, plus one PNG of the last card in
 * the cache directory, and the overview screen reads it back from there.
 */
public final class OEPLStatusStore {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLStatusStore.class);

    // what the last transfer did
    public static final String LAST_RESULT = "oepl_stat_last_result";
    public static final String LAST_RESULT_MILLIS = "oepl_stat_last_result_millis";
    public static final String LAST_BYTES_RAW = "oepl_stat_bytes_raw";
    public static final String LAST_BYTES_SENT = "oepl_stat_bytes_sent";
    public static final String LAST_DURATION_MILLIS = "oepl_stat_duration";
    public static final String LAST_COMPRESSED = "oepl_stat_compressed";
    public static final String LAST_CONTENT = "oepl_stat_content";

    // how often things went which way
    public static final String COUNT_TRANSFERS = "oepl_stat_transfers";
    public static final String COUNT_UNCHANGED = "oepl_stat_unchanged";
    public static final String COUNT_FAILURES = "oepl_stat_failures";
    public static final String COUNT_SCANS = "oepl_stat_scans";
    public static final String COUNT_SCAN_MISSES = "oepl_stat_scan_misses";
    public static final String COUNT_ATTEMPTS = "oepl_stat_attempts";

    // how the schedule is doing
    /** When the next picture is due, as the alarm is currently armed. */
    public static final String NEXT_DUE_MILLIS = "oepl_stat_next_due";
    /** When a run last got all the way through, whether or not anything was sent. */
    public static final String LAST_RUN_MILLIS = "oepl_stat_last_run";
    /** When a connection was last opened, finished or not. */
    public static final String LAST_ATTEMPT_MILLIS = "oepl_stat_last_attempt";
    /** Attempts in a row that did not end with the panel taking a picture. */
    public static final String CONSECUTIVE_FAILURES = "oepl_stat_consecutive_failures";
    /** What set the last attempt off: the timer, an advertisement, a notification, the user. */
    public static final String LAST_TRIGGER = "oepl_stat_last_trigger";
    public static final String LAST_TRIGGER_MILLIS = "oepl_stat_last_trigger_millis";
    /** Whether the controller is currently listening for this display on our behalf. */
    public static final String OPPORTUNISTIC = "oepl_stat_opportunistic";
    /** How old the forecast was that went onto the panel, in minutes. */
    public static final String LAST_WEATHER_AGE_MINUTES = "oepl_stat_weather_age";

    // the last time we listened for the advertisement
    public static final String LAST_SCAN_MILLIS = "oepl_stat_scan_millis";
    public static final String LAST_SCAN_FOUND = "oepl_stat_scan_found";
    public static final String LAST_SCAN_DURATION = "oepl_stat_scan_duration";
    public static final String LAST_RSSI = "oepl_stat_rssi";
    public static final String LAST_COUNTER = "oepl_stat_counter";

    // what the display told us about itself, beyond what OEPLPrefs already keeps
    public static final String CONFIG_SUMMARY = "oepl_stat_config";
    public static final String CAPABILITIES = "oepl_stat_capabilities";
    public static final String SETTINGS_VERSION = "oepl_stat_settings_version";
    public static final String LAST_CONNECT_MILLIS = "oepl_stat_connect_millis";

    private OEPLStatusStore() {
    }

    private static SharedPreferences prefs(final String address) {
        return GBApplication.getDeviceSpecificSharedPrefs(address);
    }

    public static void recordScan(final String address, final boolean found, final int rssi,
                                  final long durationMillis) {
        final SharedPreferences p = prefs(address);
        final SharedPreferences.Editor e = p.edit();
        e.putLong(LAST_SCAN_MILLIS, System.currentTimeMillis())
                .putBoolean(LAST_SCAN_FOUND, found)
                .putLong(LAST_SCAN_DURATION, durationMillis)
                .putInt(COUNT_SCANS, p.getInt(COUNT_SCANS, 0) + 1);
        if (found) {
            if (rssi != 0) {
                e.putInt(LAST_RSSI, rssi);
            }
        } else {
            e.putInt(COUNT_SCAN_MISSES, p.getInt(COUNT_SCAN_MISSES, 0) + 1);
        }
        e.apply();
    }

    /** What set this attempt off, so the overview can say why the panel changed. */
    public static void recordTrigger(final String address, final String reason) {
        prefs(address).edit()
                .putString(LAST_TRIGGER, reason == null ? "" : reason)
                .putLong(LAST_TRIGGER_MILLIS, System.currentTimeMillis())
                .apply();
    }

    /**
     * A connection is about to be opened. This is also where an attempt that simply never
     * arrived gets written off: a connection that is refused outright, or one the tag never
     * answers, produces no callback anybody could hang a failure on, so the next attempt is what
     * notices that the previous one left no trace. Without this the retry ladder would never
     * climb and a display out of range would be dialled every couple of minutes for ever.
     */
    public static void recordAttempt(final String address) {
        final SharedPreferences p = prefs(address);
        final long now = System.currentTimeMillis();
        final long previous = p.getLong(LAST_ATTEMPT_MILLIS, 0L);
        final SharedPreferences.Editor e = p.edit()
                .putInt(COUNT_ATTEMPTS, p.getInt(COUNT_ATTEMPTS, 0) + 1)
                .putLong(LAST_ATTEMPT_MILLIS, now);
        if (previous > 0 && p.getLong(LAST_RUN_MILLIS, 0L) < previous) {
            e.putInt(CONSECUTIVE_FAILURES, p.getInt(CONSECUTIVE_FAILURES, 0) + 1)
                    .putInt(COUNT_FAILURES, p.getInt(COUNT_FAILURES, 0) + 1);
            if (!"no contact".equals(p.getString(LAST_RESULT, ""))) {
                e.putString(LAST_RESULT, "no contact").putLong(LAST_RESULT_MILLIS, previous);
            }
        }
        e.apply();
    }

    /** Whether the Bluetooth controller is listening for this display in the background. */
    public static void recordOpportunistic(final String address, final boolean running) {
        prefs(address).edit().putBoolean(OPPORTUNISTIC, running).apply();
    }

    /** How old the content was that went onto the panel, so a stale picture is visible as one. */
    public static void recordWeatherAge(final String address, final long ageMillis) {
        prefs(address).edit()
                .putInt(LAST_WEATHER_AGE_MINUTES, ageMillis <= 0 ? -1
                        : (int) (ageMillis / 60000L))
                .apply();
    }

    public static void recordAdvertisementCounter(final String address, final int counter) {
        if (counter >= 0) {
            prefs(address).edit().putInt(LAST_COUNTER, counter).apply();
        }
    }

    public static void recordConnect(final String address) {
        prefs(address).edit().putLong(LAST_CONNECT_MILLIS, System.currentTimeMillis()).apply();
    }

    /** What the display answered to the configuration read, condensed into one line each. */
    public static void recordConfig(final String address, final OEPLDisplayConfig config) {
        if (config == null || !config.valid) {
            return;
        }
        prefs(address).edit()
                .putString(CONFIG_SUMMARY, config.describe())
                .putInt(SETTINGS_VERSION, config.settingsVersion)
                .apply();
    }

    public static void recordCapabilities(final String address, final int capabilities) {
        prefs(address).edit().putInt(CAPABILITIES, capabilities).apply();
    }

    /** A transfer that the display acknowledged. */
    public static void recordTransfer(final String address, final int rawBytes,
                                      final int sentBytes, final boolean compressed,
                                      final long durationMillis, final String content) {
        final SharedPreferences p = prefs(address);
        p.edit().putString(LAST_RESULT, "ok")
                .putLong(LAST_RESULT_MILLIS, System.currentTimeMillis())
                .putInt(LAST_BYTES_RAW, rawBytes)
                .putInt(LAST_BYTES_SENT, sentBytes)
                .putBoolean(LAST_COMPRESSED, compressed)
                .putLong(LAST_DURATION_MILLIS, durationMillis)
                .putString(LAST_CONTENT, content == null ? "" : content)
                .putInt(COUNT_TRANSFERS, p.getInt(COUNT_TRANSFERS, 0) + 1)
                .putLong(LAST_RUN_MILLIS, System.currentTimeMillis())
                .putInt(CONSECUTIVE_FAILURES, 0)
                .apply();
    }

    /**
     * A connection that existed to carry out a command rather than a picture.
     * <p>
     * It counts as a finished run even though nothing was drawn: the display was reached, which
     * is what the retry ladder is about, and the scheduler is waiting for exactly this to stop
     * holding its wake lock. It is kept out of the transfer and failure tallies, which are
     * about pictures.
     */
    public static void recordCommand(final String address, final String what) {
        prefs(address).edit().putString(LAST_RESULT, what)
                .putLong(LAST_RESULT_MILLIS, System.currentTimeMillis())
                .putLong(LAST_RUN_MILLIS, System.currentTimeMillis())
                .putInt(CONSECUTIVE_FAILURES, 0)
                .apply();
    }

    /** The display already had this picture, so nothing was sent. */
    public static void recordUnchanged(final String address) {
        final SharedPreferences p = prefs(address);
        p.edit().putString(LAST_RESULT, "unchanged")
                .putLong(LAST_RESULT_MILLIS, System.currentTimeMillis())
                .putInt(COUNT_UNCHANGED, p.getInt(COUNT_UNCHANGED, 0) + 1)
                .putLong(LAST_RUN_MILLIS, System.currentTimeMillis())
                .putInt(CONSECUTIVE_FAILURES, 0)
                .apply();
    }

    /**
     * The display could not be reached, with the number of attempts that were made. Only the
     * visible result is written: the failure counters are left to {@link #recordAttempt}, which
     * notices a run that never finished, so that the retry ladder climbs once per run rather than
     * once per piece of bookkeeping.
     */
    public static void recordNoContact(final String address, final int tries) {
        final String result = tries > 1 ? "no contact in " + tries + " tries" : "no contact";
        prefs(address).edit().putString(LAST_RESULT, result)
                .putLong(LAST_RESULT_MILLIS, System.currentTimeMillis())
                .apply();
    }

    /**
     * Nothing was sent because there is nothing to send yet - a picture card without a picture
     * - which is neither a success nor a failure. It goes into the last result so the info
     * screen can say what the display is waiting for, and into no tally at all: counting it as
     * a failure would make the scheduler back off from a display that is working perfectly.
     */
    public static void recordWaiting(final String address, final String reason) {
        prefs(address).edit().putString(LAST_RESULT, reason)
                .putLong(LAST_RESULT_MILLIS, System.currentTimeMillis())
                .apply();
    }

    public static void recordFailure(final String address, final String reason) {
        final SharedPreferences p = prefs(address);
        p.edit().putString(LAST_RESULT, reason == null ? "failed" : reason)
                .putLong(LAST_RESULT_MILLIS, System.currentTimeMillis())
                .putInt(COUNT_FAILURES, p.getInt(COUNT_FAILURES, 0) + 1)
                .putInt(CONSECUTIVE_FAILURES, p.getInt(CONSECUTIVE_FAILURES, 0) + 1)
                .apply();
    }

    /**
     * Where the picture of what the display is showing is kept. Not in the cache: it is what
     * the device's own icon is drawn from, and an icon that disappears whenever Android tidies
     * up the cache would be worse than no icon at all.
     */
    public static File previewFile(final Context context, final String address) {
        final String safe = address == null ? "unknown" : address.replace(":", "");
        return new File(context.getFilesDir(), "oepl-preview-" + safe + ".png");
    }

    /**
     * Stores the card that was handed to the display, turned back into the orientation it is
     * read in. The panel buffer is a quarter turn away from that on most tags, and a picture
     * kept the way the hardware wants it is unrecognisable everywhere it is shown.
     */
    public static void storePreview(final Context context, final String address,
                                    final Bitmap bitmap, final int rotationDegrees) {
        if (bitmap == null) {
            return;
        }
        Bitmap upright = bitmap;
        if (rotationDegrees != 0) {
            try {
                final Matrix matrix = new Matrix();
                matrix.postRotate(-rotationDegrees);
                upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
            } catch (final Exception ignored) {
                upright = bitmap;
            }
        }
        final File file = previewFile(context, address);
        try (FileOutputStream out = new FileOutputStream(file)) {
            upright.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (final Exception e) {
            LOG.warn("could not keep a preview of the card", e);
        }
        synchronized (PREVIEW_LOCK) {
            cachedPreview = upright;
            cachedPreviewFor = address;
            cachedPreviewAt = file.lastModified();
        }
    }

    private static final Object PREVIEW_LOCK = new Object();
    private static Bitmap cachedPreview;
    private static String cachedPreviewFor;
    private static long cachedPreviewAt;

    /**
     * What the display is showing, as the eye sees it, or null while nothing has been sent yet.
     * Decoded once and then kept, because the device list asks for it on every redraw.
     */
    @Nullable
    public static Bitmap readPreview(final Context context, final String address) {
        final File file = previewFile(context, address);
        if (!file.isFile()) {
            return null;
        }
        synchronized (PREVIEW_LOCK) {
            if (cachedPreview != null && file.lastModified() == cachedPreviewAt
                    && address != null && address.equals(cachedPreviewFor)) {
                return cachedPreview;
            }
        }
        final Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            return null;
        }
        synchronized (PREVIEW_LOCK) {
            cachedPreview = bitmap;
            cachedPreviewFor = address;
            cachedPreviewAt = file.lastModified();
        }
        return bitmap;
    }

    @Nullable
    public static String lastResult(final String address) {
        final String value = prefs(address).getString(LAST_RESULT, null);
        return value == null || value.isEmpty() ? null : value;
    }

    /** Clears the tallies, leaving the description of the last contact alone. */
    public static void resetCounters(final String address) {
        prefs(address).edit()
                .remove(COUNT_TRANSFERS).remove(COUNT_UNCHANGED).remove(COUNT_FAILURES)
                .remove(COUNT_SCANS).remove(COUNT_SCAN_MISSES).remove(COUNT_ATTEMPTS)
                .remove(CONSECUTIVE_FAILURES).remove(LAST_ATTEMPT_MILLIS)
                .apply();
    }
}
