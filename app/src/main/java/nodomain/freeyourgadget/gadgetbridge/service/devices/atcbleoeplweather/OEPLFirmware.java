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

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * A firmware waiting to be written into one display.
 * <p>
 * Noted down per display rather than carried out on the spot, for the same reason as the other
 * commands: a display answers for a few seconds when it feels like it. The difference is the
 * length - a firmware takes minutes and dozens of blocks - so a transfer that breaks off is
 * expected rather than exceptional, and the note stays until the display confirms the new
 * firmware or the attempts run out. The display keeps its old firmware either way; a broken
 * transfer is picked up where it stopped, which is the firmware's own doing.
 * <p>
 * This is also what makes writing one firmware to a shelf full of displays possible: every one
 * of them is handed the same note, and each is flashed as it comes within reach.
 */
public final class OEPLFirmware {

    /** The file to write, as a content URI the chooser handed over. */
    static final String PENDING_URI = "oepl_weather_firmware_uri";
    /** How often it has been tried, so a file this display will not take is given up on. */
    static final String PENDING_TRIES = "oepl_weather_firmware_tries";
    /** The attempt number the display was last restarted at; see {@link #wantsRestartFirst}. */
    static final String RESTARTED_AT = "oepl_weather_firmware_restarted_at";

    /**
     * Five goes. A display out of range or short of battery deserves another attempt; a file
     * its loader keeps refusing deserves to stop - rather than every connection for the rest of
     * the week being spent on the same two hundred kilobytes, and rather than the note standing
     * in front of everything else this display is asked to do.
     */
    public static final int MAX_TRIES = 5;

    private OEPLFirmware() {
    }

    /** Notes the file down for this display, to be written the next time it is reachable. */
    public static void arm(final String address, final String uri) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putString(PENDING_URI, uri)
                .putInt(PENDING_TRIES, 0)
                .remove(RESTARTED_AT)
                .apply();
    }

    /**
     * Whether the display should be restarted before it is offered the firmware again.
     * <p>
     * An update is written in blocks, and the display keeps its own place in that sequence so a
     * transfer that breaks off can be picked up where it stopped. That is the right design and
     * it is also where this gets stuck: if one block arrives wrong, the display can be left
     * asking for parts that no longer line up with the file, and it will go on asking for them
     * on every connection from then on. The transfer never finishes and never quite fails
     * either, which is the state a display has to be restarted out of - taking the battery out
     * cures it, and so does the restart command, which is the one thing that costs nothing to
     * try.
     * <p>
     * So from the second attempt on, the connection before each firmware attempt is spent on a
     * restart instead. Once per attempt, never twice for the same one, and never before the
     * first attempt, which has no reason to be anything but clean.
     */
    public static boolean wantsRestartFirst(final SharedPreferences prefs) {
        final int tries = tries(prefs);
        return tries >= 1 && prefs.getInt(RESTARTED_AT, 0) < tries;
    }

    /** Remembers that the display was restarted for the attempt it has behind it. */
    public static void notedRestart(final String address) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        prefs.edit().putInt(RESTARTED_AT, tries(prefs)).apply();
    }

    @Nullable
    public static String armed(final SharedPreferences prefs) {
        final String uri = prefs.getString(PENDING_URI, "");
        return uri.isEmpty() ? null : uri;
    }

    public static boolean pending(final SharedPreferences prefs) {
        return armed(prefs) != null;
    }

    /** Forgets the note: the firmware is on the display, or it never will be. */
    public static void done(final String address) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .remove(PENDING_URI)
                .remove(PENDING_TRIES)
                .apply();
    }

    /** Counts this attempt and says which one it was. */
    public static int noteAttempt(final String address) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final int tries = prefs.getInt(PENDING_TRIES, 0) + 1;
        prefs.edit().putInt(PENDING_TRIES, tries).apply();
        return tries;
    }

    public static int tries(final SharedPreferences prefs) {
        return prefs.getInt(PENDING_TRIES, 0);
    }

    public static boolean exhausted(final SharedPreferences prefs) {
        return tries(prefs) >= MAX_TRIES;
    }

    /**
     * Whether this display is still owed a firmware that is worth trying. A note whose attempts
     * are used up is not: it is on its way out, and until it is gone it must not be allowed to
     * decide what a connection is for.
     */
    public static boolean worthTrying(final SharedPreferences prefs) {
        return pending(prefs) && !exhausted(prefs);
    }

    /**
     * Stops trying, and lets the display go back to being a display.
     * <p>
     * Two things have to happen for that, and the second is easy to miss. The note goes, so the
     * next connection is an ordinary one again - and the cached checksum goes with it, because
     * a transfer that broke off halfway may have left something else on the panel entirely. The
     * checksum would still claim the panel shows the last card, the next push would decide
     * there was nothing to send, and the display would sit there showing the wreckage of an
     * update that never finished.
     */
    public static void giveUp(final String address) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .remove(PENDING_URI)
                .remove(PENDING_TRIES)
                .remove(RESTARTED_AT)
                .putLong(OEPLPrefs.LAST_IMAGE_CRC, 0L)
                .apply();
    }

    /**
     * The firmware this display last reported, as it writes it on its own panel: a plain
     * number, counted up with every build. It comes out of the advertisement, so it is known
     * without a connection - and it is the one thing that says whether a display still needs
     * what the others already have.
     */
    public static String version(final SharedPreferences prefs) {
        final int version = prefs.getInt(OEPLPrefs.DETECTED_FIRMWARE, -1);
        return version > 0 ? "v" + version : "";
    }
}
