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
    public void askingAgainGivesItAnotherFullSetOfGoes() {
        OEPLFirmware.arm(HALL, FILE);
        for (int attempt = 0; attempt < OEPLFirmware.MAX_TRIES; attempt++) {
            OEPLFirmware.noteAttempt(HALL);
        }
        assertTrue(OEPLFirmware.exhausted(display(HALL)));

        OEPLFirmware.arm(HALL, FILE);
        assertFalse(OEPLFirmware.exhausted(display(HALL)));
        assertEquals(0, OEPLFirmware.tries(display(HALL)));
    }

    /**
     * Recovery, which is the other half of picking a transfer up where it stopped.
     * <p>
     * A display writes an update in blocks and keeps its own place in that sequence. If one
     * block arrives wrong it can be left asking for parts that no longer line up with the file,
     * and then the transfer neither finishes nor fails: it goes on asking on every connection.
     * A note that only ever gives up at the end of a transfer would stand in front of
     * everything else that display is asked to do for ever, so the attempts are read before a
     * connection is spent on them, and five of them are the end of it.
     */
    @Test
    public void aFirmwareThatNeverFinishesIsLetGoOfRatherThanBlockingTheDisplay() {
        assertEquals("five goes, as asked for", 5, OEPLFirmware.MAX_TRIES);
        OEPLFirmware.arm(HALL, FILE);
        display(HALL).edit().putLong(OEPLPrefs.LAST_IMAGE_CRC, 0x1234L).apply();

        for (int attempt = 1; attempt < OEPLFirmware.MAX_TRIES; attempt++) {
            OEPLFirmware.noteAttempt(HALL);
            assertTrue("given up on after " + attempt + " hangs",
                    OEPLFirmware.worthTrying(display(HALL)));
        }
        OEPLFirmware.noteAttempt(HALL);

        assertTrue("the note is still there to be reported on",
                OEPLFirmware.pending(display(HALL)));
        assertFalse("but nothing may be spent on it any more",
                OEPLFirmware.worthTrying(display(HALL)));
    }

    /**
     * Letting go of it has to put the display back to work, and that takes more than removing
     * the note: a transfer that broke off halfway may have left something else on the panel, so
     * the checksum that claims the panel shows the last card has to go too, or the next push
     * decides there is nothing to send and the display keeps showing the wreckage.
     */
    @Test
    public void droppingTheNoteAlsoBringsTheCardBack() {
        OEPLFirmware.arm(HALL, FILE);
        display(HALL).edit().putLong(OEPLPrefs.LAST_IMAGE_CRC, 0x1234L).apply();

        OEPLFirmware.giveUp(HALL);

        assertFalse(OEPLFirmware.pending(display(HALL)));
        assertEquals("the panel is not known to show anything any more",
                0L, display(HALL).getLong(OEPLPrefs.LAST_IMAGE_CRC, -1L));
        assertEquals(0, OEPLFirmware.tries(display(HALL)));
    }

    /**
     * What actually cures the stuck display, found the hard way on a 4.2": a restart. So from
     * the second attempt on, the connection before each firmware attempt is spent on one -
     * once per attempt, never twice for the same one, and never before the first attempt.
     */
    @Test
    public void theDisplayIsRestartedBeforeItIsOfferedTheSameFirmwareAgain() {
        OEPLFirmware.arm(HALL, FILE);
        assertFalse("the first attempt has no reason to be anything but clean",
                OEPLFirmware.wantsRestartFirst(display(HALL)));

        OEPLFirmware.noteAttempt(HALL);
        assertTrue("the second attempt gets a clear head first",
                OEPLFirmware.wantsRestartFirst(display(HALL)));

        OEPLFirmware.notedRestart(HALL);
        assertFalse("and only one restart per attempt, not a loop of them",
                OEPLFirmware.wantsRestartFirst(display(HALL)));

        OEPLFirmware.noteAttempt(HALL);
        assertTrue("the attempt after that one gets its own restart",
                OEPLFirmware.wantsRestartFirst(display(HALL)));

        // asking for the file again starts the whole arrangement over
        OEPLFirmware.arm(HALL, FILE);
        assertFalse(OEPLFirmware.wantsRestartFirst(display(HALL)));
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
                OEPLFirmware.worthTrying(display(HALL)));
    }
}
