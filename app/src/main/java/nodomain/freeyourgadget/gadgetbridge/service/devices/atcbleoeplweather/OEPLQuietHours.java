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

import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

/**
 * The hours in which a display is left alone.
 * <p>
 * The point of it is not the display, which is quiet by nature, but everything around it: the
 * phone waking up for a radio session, a panel that flickers for a second in a dark bedroom, and
 * a weather card that nobody is going to read before morning anyway. So between two times of day
 * the schedule stops, and what fell due in between is not skipped but held: the moment the window
 * ends the display is brought up to date, which is the same promise the rest of the scheduler
 * makes - a picture that was due is shown as soon as there is a chance to show it.
 * <p>
 * A command the user just asked for, a firmware that is being handed out and an update asked for
 * by hand all ignore the window. Somebody standing in front of the display at midnight means it.
 */
public final class OEPLQuietHours {

    public static final String DEFAULT_START = "22:00";
    public static final String DEFAULT_END = "07:00";

    private OEPLQuietHours() {
    }

    /**
     * Whether a window is set at all. A window that starts and ends at the same minute would
     * either mean "never" or "always", and neither is what anybody typed it in for, so it counts
     * as not set.
     */
    public static boolean enabled(final SharedPreferences prefs) {
        return prefs.getBoolean(OEPLPrefs.PREF_QUIET, false)
                && startMinute(prefs) != endMinute(prefs);
    }

    public static int startMinute(final SharedPreferences prefs) {
        return minuteOfDay(prefs.getString(OEPLPrefs.PREF_QUIET_START, DEFAULT_START),
                22 * 60);
    }

    public static int endMinute(final SharedPreferences prefs) {
        return minuteOfDay(prefs.getString(OEPLPrefs.PREF_QUIET_END, DEFAULT_END), 7 * 60);
    }

    /** Whether that moment falls inside the window, which may run across midnight. */
    public static boolean covers(final SharedPreferences prefs, final long millis) {
        if (!enabled(prefs)) {
            return false;
        }
        final int start = startMinute(prefs);
        final int end = endMinute(prefs);
        final Calendar at = Calendar.getInstance();
        at.setTimeInMillis(millis);
        final int minute = at.get(Calendar.HOUR_OF_DAY) * 60 + at.get(Calendar.MINUTE);
        return start < end
                ? minute >= start && minute < end
                : minute >= start || minute < end;
    }

    /**
     * When the window that contains that moment is over, which is the next time the end of the
     * window comes round. Local time rather than a fixed number of hours, so a window that is
     * crossed by a change of clocks still ends at the hour it was set to.
     */
    public static long endOfWindow(final SharedPreferences prefs, final long millis) {
        final int end = endMinute(prefs);
        final Calendar when = Calendar.getInstance();
        when.setTimeInMillis(millis);
        when.set(Calendar.HOUR_OF_DAY, end / 60);
        when.set(Calendar.MINUTE, end % 60);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= millis) {
            when.add(Calendar.DAY_OF_MONTH, 1);
        }
        return when.getTimeInMillis();
    }

    /**
     * The same moment, or the end of the quiet window it falls into. This is the whole of how the
     * window is kept: an attempt is never cancelled, only moved to the first moment it is
     * allowed to happen, so a display whose turn came at three in the morning is updated at
     * seven rather than at three the following afternoon.
     */
    public static long shift(final SharedPreferences prefs, final long due) {
        return covers(prefs, due) ? endOfWindow(prefs, due) : due;
    }

    /** "22:00-07:00", or an empty string while no window is set. */
    public static String describe(final SharedPreferences prefs) {
        if (!enabled(prefs)) {
            return "";
        }
        return asTime(startMinute(prefs)) + "-" + asTime(endMinute(prefs));
    }

    public static String asTime(final int minuteOfDay) {
        final int minutes = ((minuteOfDay % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /**
     * Reads the "HH:mm" a time picker stores. A value that cannot be read falls back rather than
     * throwing: a hand edited backup must not be able to stop every update on the phone.
     */
    private static int minuteOfDay(final String value, final int fallback) {
        if (value == null) {
            return fallback;
        }
        final String[] pieces = value.trim().split(":");
        if (pieces.length != 2) {
            return fallback;
        }
        try {
            final int hour = Integer.parseInt(pieces[0].trim());
            final int minute = Integer.parseInt(pieces[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return fallback;
            }
            return hour * 60 + minute;
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }
}
