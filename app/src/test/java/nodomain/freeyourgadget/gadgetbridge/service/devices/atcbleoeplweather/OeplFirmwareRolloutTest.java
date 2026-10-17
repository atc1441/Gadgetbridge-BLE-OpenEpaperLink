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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * Writing one firmware to a shelf full of displays.
 * <p>
 * The note per display is what makes it work: the displays are flashed one after another as
 * they come within reach, rather than all at once while they are mostly asleep.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OeplFirmwareRolloutTest {

    private static final String HALL = "FF:FC:4F:05:E4:12";
    private static final String DESK = "FF:FC:4F:05:E4:13";
    private static final String FILE = "content://media/external/download/ATC_BLE_OEPL.bin";

    @Before
    public void setUp() {
        for (final String address : new String[]{HALL, DESK}) {
            GBApplication.getDeviceSpecificSharedPrefs(address).edit().clear().apply();
        }
    }

    private SharedPreferences display(final String address) {
        return GBApplication.getDeviceSpecificSharedPrefs(address);
    }

    /** Each display is handed the same file, and each keeps its own note about it. */
    @Test
    public void everyTickedDisplayKeepsItsOwnNote() {
        assertFalse(OEPLFirmware.pending(display(HALL)));

        OEPLFirmware.arm(HALL, FILE);
        OEPLFirmware.arm(DESK, FILE);

        assertEquals(FILE, OEPLFirmware.armed(display(HALL)));
        assertEquals(FILE, OEPLFirmware.armed(display(DESK)));

        OEPLFirmware.done(HALL);
        assertNull("one display finishing cleared the other one too",
                OEPLFirmware.armed(display(HALL)));
        assertEquals(FILE, OEPLFirmware.armed(display(DESK)));
    }

    /**
     * The note survives a transfer that breaks off, because that is the ordinary case: the
     * display was carried away, the battery was weak, the link was poor. It keeps the firmware
     * it has and is handed the file again next time - but not forever, or a file this display
     * will not take would cost it every connection for the rest of the week.
     */
    @Test
    public void aFileTheDisplayWillNotTakeIsGivenUpOn() {
        OEPLFirmware.arm(HALL, FILE);

        for (int attempt = 1; attempt < OEPLFirmware.MAX_TRIES; attempt++) {
            assertEquals(attempt, OEPLFirmware.noteAttempt(HALL));
            assertFalse("gave up after " + attempt + " of " + OEPLFirmware.MAX_TRIES,
                    OEPLFirmware.exhausted(display(HALL)));
            assertTrue("the note was dropped after a single failure",
                    OEPLFirmware.pending(display(HALL)));
        }
        assertEquals(OEPLFirmware.MAX_TRIES, OEPLFirmware.noteAttempt(HALL));
        assertTrue("it would be tried again forever", OEPLFirmware.exhausted(display(HALL)));
    }

    /** Arming again starts the attempts over: the user asking a second time means try again. */
    @Test
    public void askingAgainGivesItAnotherThreeGoes() {
        OEPLFirmware.arm(HALL, FILE);
        OEPLFirmware.noteAttempt(HALL);
        OEPLFirmware.noteAttempt(HALL);
        OEPLFirmware.noteAttempt(HALL);
        assertTrue(OEPLFirmware.exhausted(display(HALL)));

        OEPLFirmware.arm(HALL, FILE);
        assertFalse(OEPLFirmware.exhausted(display(HALL)));
        assertEquals(0, OEPLFirmware.tries(display(HALL)));
    }

    /**
     * The version each display reports, which is what the list is read off. It comes from the
     * advertisement, so it is there without a connection - and a display that has never been
     * heard from says nothing rather than saying zero.
     */
    @Test
    public void theVersionIsWhatTheDisplayItselfBroadcast() {
        assertEquals("", OEPLFirmware.version(display(HALL)));

        display(HALL).edit().putInt(OEPLPrefs.DETECTED_FIRMWARE, 124).apply();
        assertEquals("v124", OEPLFirmware.version(display(HALL)));
    }

    /**
     * A display waiting for a firmware has to be fetched even when there is no picture for it -
     * otherwise a display whose card is still waiting for a chosen picture could never be
     * updated at all.
     */
    @Test
    public void aFirmwareIsReasonEnoughToGoAndGetTheDisplay() {
        display(HALL).edit().putString(OEPLPrefs.PREF_CONTENT, CardContentFactory.IMAGE).apply();
        assertTrue("without a picture there is nothing to send",
                CardContentFactory.waitingForSource(display(HALL)) != null);
        assertFalse(OEPLFirmware.pending(display(HALL)));

        OEPLFirmware.arm(HALL, FILE);
        assertTrue("the connection is the point, not the picture",
                OEPLFirmware.pending(display(HALL)));
    }
}
