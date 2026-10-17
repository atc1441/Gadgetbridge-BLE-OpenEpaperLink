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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * The one-off things a display can be asked to do: blink its LED, restart, go to sleep.
 * <p>
 * None of them is a setting, and none of them can simply be sent: the display is unreachable
 * almost all of the time and answers for a few seconds when it feels like it. So an action is
 * written down here and carried out the next time the display is on the air, which is either
 * right now - it happens to be connected - or in the next few seconds, because asking for one
 * also asks the scheduler to go and fetch the display.
 * <p>
 * Written down rather than held in memory on purpose: the service is started and stopped around
 * every connection, and an action that only existed in a field would be lost between pressing
 * the button and the display answering.
 */
public final class OEPLActions {

    /** Flash the LED, so that one display out of a shelf full of them can be pointed at. */
    public static final String BLINK = "blink";
    /** Restart, which is also how a display that was sent to sleep is brought back. */
    public static final String REBOOT = "reboot";
    /** Stop the radio and the screen until the display is woken by hand. */
    public static final String SLEEP = "sleep";

    /** Written into the display's own settings, so it survives the service being restarted. */
    static final String PENDING = "oepl_weather_pending_actions";

    /**
     * The order they are carried out in, whatever order they were asked for in: the two that
     * end the connection go last, or everything behind them would be lost.
     */
    private static final List<String> ORDER =
            Collections.unmodifiableList(Arrays.asList(BLINK, REBOOT, SLEEP));

    private OEPLActions() {
    }

    /**
     * Asks a display to do something, now or as soon as it answers.
     *
     * @return true when the display is connected and it is being done straight away.
     */
    public static boolean request(final Context context, final GBDevice device,
                                  final String action) {
        arm(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()), action);
        if (device.isInitialized()) {
            GBApplication.deviceService(device).onSendConfiguration(OEPLPrefs.PREF_ACTION);
            return true;
        }
        OEPLUpdateScheduler.requestUpdateNow(context, device);
        return false;
    }

    /** Writes the action down, without deciding when it is carried out. */
    static void arm(final SharedPreferences prefs, final String action) {
        if (!ORDER.contains(action)) {
            return;
        }
        final List<String> pending = read(prefs);
        if (pending.contains(action)) {
            return; // asked for twice before the display answered once
        }
        pending.add(action);
        prefs.edit().putString(PENDING, join(pending)).apply();
    }

    /** Whether this display is waiting to be fetched for something other than a picture. */
    public static boolean pending(final SharedPreferences prefs) {
        return !read(prefs).isEmpty();
    }

    /** Takes the list and clears it, in the order the commands have to be sent in. */
    public static List<String> drain(final String address) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final List<String> pending = read(prefs);
        if (pending.isEmpty()) {
            return pending;
        }
        prefs.edit().remove(PENDING).apply();
        final List<String> sorted = new ArrayList<>();
        for (final String action : ORDER) {
            if (pending.contains(action)) {
                sorted.add(action);
            }
        }
        return sorted;
    }

    /** Forgets what a display was asked to do, after it has answered or been given up on. */
    public static void forget(final String address) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit().remove(PENDING).apply();
    }

    private static List<String> read(final SharedPreferences prefs) {
        final String stored = prefs.getString(PENDING, "");
        final List<String> actions = new ArrayList<>();
        for (final String action : stored.split(",")) {
            if (ORDER.contains(action)) {
                actions.add(action);
            }
        }
        return actions;
    }

    private static String join(final List<String> actions) {
        final StringBuilder out = new StringBuilder();
        for (final String action : actions) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(action);
        }
        return out.toString();
    }
}
