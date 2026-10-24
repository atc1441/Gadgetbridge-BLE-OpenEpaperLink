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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * The one-off commands: blink, restart, sleep - and the check that stands in front of a
 * firmware transfer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OeplDisplayCommandsTest {

    private static final String ADDRESS = "FF:FC:4F:05:E4:12";

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = GBApplication.getDeviceSpecificSharedPrefs(ADDRESS);
        prefs.edit().clear().apply();
    }

    /**
     * The two that end the connection go last. Asked for in the wrong order, a sleep would cut
     * the link and the blink behind it would never be sent - and the display would be asleep
     * without having shown which one it is.
     */
    @Test
    public void theCommandThatEndsTheConnectionIsSentLast() {
        OEPLActions.arm(prefs, OEPLActions.SLEEP);
        OEPLActions.arm(prefs, OEPLActions.BLINK);
        OEPLActions.arm(prefs, OEPLActions.REBOOT);

        assertEquals(Arrays.asList(OEPLActions.BLINK, OEPLActions.REBOOT, OEPLActions.SLEEP),
                OEPLActions.drain(ADDRESS));
    }

    /** Pressing the button twice while the display is out of reach is one blink, not two. */
    @Test
    public void askingTwiceBeforeItAnswersIsAskingOnce() {
        OEPLActions.arm(prefs, OEPLActions.BLINK);
        OEPLActions.arm(prefs, OEPLActions.BLINK);

        assertEquals(1, OEPLActions.drain(ADDRESS).size());
    }

    /**
     * Written into the display's own settings rather than kept in memory: the service is
     * started and stopped around every connection, so a note in a field would be gone between
     * the button and the display answering.
     */
    @Test
    public void whatWasAskedForSurvivesTheServiceBeingRestarted() {
        assertFalse(OEPLActions.pending(prefs));
        OEPLActions.arm(prefs, OEPLActions.BLINK);
        assertTrue(OEPLActions.pending(
                GBApplication.getDeviceSpecificSharedPrefs(ADDRESS)));

        final List<String> drained = OEPLActions.drain(ADDRESS);
        assertEquals(Arrays.asList(OEPLActions.BLINK), drained);
        assertFalse("carried out twice", OEPLActions.pending(
                GBApplication.getDeviceSpecificSharedPrefs(ADDRESS)));
    }

    @Test
    public void nonsenseIsNotWrittenDown() {
        OEPLActions.arm(prefs, "explode");
        assertFalse(OEPLActions.pending(prefs));
        assertTrue(OEPLActions.drain(ADDRESS).isEmpty());
    }

    /**
     * The blink pattern. It has to end by itself: the tag stays awake for as long as a pattern
     * is running, so one that repeated forever would be a display that never sleeps again and a
     * coin cell gone by the morning.
     */
    @Test
    public void theBlinkPatternStopsByItself() {
        final byte[] pattern = OEPLProtocol.ledPatternArgument(OEPLProtocol.LED_RED, 3, 5);
        assertEquals("the tag reads exactly twelve bytes", 12, pattern.length);
        assertEquals("pattern mode, brightest", (byte) 0xf1, pattern[0]);
        assertEquals("red", (byte) 0b111_000_00, pattern[1]);
        assertEquals("three flashes, one every other tick", (byte) 0x23, pattern[2]);
        assertEquals("five rounds, counted from zero", (byte) 0x04, pattern[10]);

        // 255 in that last byte is what the firmware reads as "keep going", so it may not be
        // reachable however the pattern is asked for
        for (final int repeats : new int[]{1, 5, 255, 1000, -3}) {
            final byte[] forever = OEPLProtocol.ledPatternArgument(OEPLProtocol.LED_WHITE, 9,
                    repeats);
            assertFalse("a pattern that never ends: " + repeats,
                    (forever[10] & 0xff) == 0xff);
        }
    }

    /**
     * The blink is what one panel out of a shelf of identical ones is picked out with, so it
     * has to be tellable apart from its neighbour's: colour, how many flashes, how many rounds
     * of them and how bright, each of them stored for that display alone.
     */
    @Test
    public void theBlinkIsShapedTheWayItWasChosenForThisDisplay() {
        prefs.edit()
                .putString(OEPLPrefs.PREF_LED_COLOR, "green")
                .putString(OEPLPrefs.PREF_LED_FLASHES, "10")
                .putString(OEPLPrefs.PREF_LED_REPEATS, "2")
                .putString(OEPLPrefs.PREF_LED_BRIGHTNESS, "4")
                .apply();

        final byte[] pattern = OEPLProtocol.ledPatternArgument(OEPLPrefs.ledColor(prefs),
                OEPLPrefs.ledFlashes(prefs), OEPLPrefs.ledRepeats(prefs),
                OEPLPrefs.ledBrightness(prefs));

        assertEquals("the tag reads exactly twelve bytes", 12, pattern.length);
        assertEquals("brightness in the top half of the byte, pattern mode in the bottom",
                (byte) 0x31, pattern[0]);
        assertEquals(OEPLProtocol.LED_GREEN, pattern[1] & 0xff);
        assertEquals("ten flashes in the group", 10, pattern[2] & 0x0f);
        assertEquals("two rounds, counted from zero", 1, pattern[10] & 0xff);
    }

    /** What is left alone blinks the way it always did. */
    @Test
    public void aDisplayNobodyChoseForKeepsTheOldBlink() {
        assertEquals(3, OEPLPrefs.ledFlashes(prefs));
        assertEquals(5, OEPLPrefs.ledRepeats(prefs));
        assertEquals(OEPLProtocol.FULL_BRIGHTNESS, OEPLPrefs.ledBrightness(prefs));
        assertEquals("the same pattern as before any of this was choosable",
                (byte) 0xf1,
                OEPLProtocol.ledPatternArgument(OEPLPrefs.ledColor(prefs),
                        OEPLPrefs.ledFlashes(prefs), OEPLPrefs.ledRepeats(prefs),
                        OEPLPrefs.ledBrightness(prefs))[0]);
    }

    /**
     * The firmware's fields are four bits for the flashes and one byte for the rounds, and a
     * figure that does not fit one of them is not a longer blink but a different one.
     */
    @Test
    public void aFigureTheTagCouldNotHoldIsBroughtBackInside() {
        prefs.edit()
                .putString(OEPLPrefs.PREF_LED_FLASHES, "99")
                .putString(OEPLPrefs.PREF_LED_REPEATS, "0")
                .putString(OEPLPrefs.PREF_LED_BRIGHTNESS, "half past three")
                .apply();

        assertEquals(15, OEPLPrefs.ledFlashes(prefs));
        assertEquals(1, OEPLPrefs.ledRepeats(prefs));
        assertEquals("nonsense falls back rather than going dark",
                OEPLProtocol.FULL_BRIGHTNESS, OEPLPrefs.ledBrightness(prefs));
    }

    /**
     * The pattern in full: three groups, each with its own colour, its own number of flashes
     * and its own gap. Two red, one blue and a second of dark is a blink that is told apart
     * from the display beside it across a room, which is the whole reason the LED is there.
     */
    @Test
    public void aPatternCarriesThreeColoursAndSurvivesBeingWrittenDown() {
        final int[] colors = {
                OEPLProtocol.ledColor(7, 0, 0),
                OEPLProtocol.ledColor(0, 0, 3),
                0,                                  // dark: the pause between the rounds
        };
        final int[] flashes = {2, 1, 1};
        final int[] gaps = {3, 3, 10};

        final byte[] pattern = OEPLProtocol.ledPattern(colors, flashes, gaps, 4, 16);

        assertEquals(12, pattern.length);
        assertEquals((byte) 0xf1, pattern[0]);
        assertEquals(OEPLProtocol.LED_RED, pattern[1] & 0xff);
        assertEquals("three ticks apart, two of them", 0x32, pattern[2] & 0xff);
        assertEquals(OEPLProtocol.LED_BLUE, pattern[4] & 0xff);
        assertEquals("nothing lit at all is a pause", 0, pattern[7] & 0xff);
        assertEquals("four rounds, counted from zero", 3, pattern[10] & 0xff);

        final byte[] back = OEPLProtocol.ledPatternFromHex(
                OEPLProtocol.ledPatternToHex(pattern));
        assertNotNull("it has to read back as what was written", back);
        assertArrayEquals(pattern, back);
    }

    /** The colour is three bits of red, three of green and two of blue, and comes back so. */
    @Test
    public void aColourGoesInAndComesOutTheSame() {
        for (int red = 0; red <= 7; red++) {
            for (int blue = 0; blue <= 3; blue++) {
                final int color = OEPLProtocol.ledColor(red, 5, blue);
                assertEquals(red, OEPLProtocol.redOf(color));
                assertEquals(5, OEPLProtocol.greenOf(color));
                assertEquals(blue, OEPLProtocol.blueOf(color));
                assertTrue("one byte and no more", color >= 0 && color <= 0xff);
            }
        }
        assertEquals("asking for more than the leg has is not another colour",
                OEPLProtocol.ledColor(7, 7, 3), OEPLProtocol.ledColor(99, 99, 99));
    }

    /**
     * The one pattern that may never be stored. The rounds byte holds one less than the
     * rounds, and the firmware reads 254 in it as "keep going" - a tag that never sleeps
     * again and a coin cell gone by the morning.
     */
    @Test
    public void aPatternThatWouldNeverEndIsNotReadBack() {
        final byte[] forever = OEPLProtocol.ledPattern(new int[]{255, 0, 0}, new int[]{1, 1, 1},
                new int[]{1, 1, 1}, 255, 16);
        assertNotEquals("the builder may not even make one", 254, forever[10] & 0xff);

        //                                        rounds: fe ---------------------\
        assertNull("and one from anywhere else is refused",
                OEPLProtocol.ledPatternFromHex("f1e02200000000000000fe00"));
        //                            mode 0, which switches the LED off instead of blinking it
        assertNull("so is something that is not a pattern at all",
                OEPLProtocol.ledPatternFromHex("f0e022000000000000000300"));
        assertNull(OEPLProtocol.ledPatternFromHex("nonsense"));
        assertNull(OEPLProtocol.ledPatternFromHex(""));
    }

    /** How long it runs, which is what says whether a pattern is a blink or a light show. */
    @Test
    public void theLengthOfAPatternIsCountedInTenthsOfASecond() {
        final byte[] pattern = OEPLProtocol.ledPattern(new int[]{255, 0, 0},
                new int[]{2, 1, 1}, new int[]{2, 1, 10}, 2, 16);
        // group one flashes twice, two ticks apart; two and three take one and ten ticks
        assertEquals(3.0f, OEPLProtocol.ledPatternSeconds(pattern), 0.01f);
    }

    /** A display nobody has shaped a pattern for keeps the one it always blinked. */
    @Test
    public void theShapedPatternWinsOverThePlainOne() {
        assertArrayEquals("nothing stored: the plain blink",
                OEPLProtocol.ledPatternArgument(OEPLProtocol.LED_RED, 3, 5),
                OEPLPrefs.ledPattern(prefs));

        final byte[] shaped = OEPLProtocol.ledPattern(new int[]{OEPLProtocol.LED_GREEN, 0, 0},
                new int[]{1, 1, 1}, new int[]{5, 5, 5}, 2, 8);
        OEPLPrefs.storeLedPattern(ADDRESS, shaped);
        assertArrayEquals(shaped, OEPLPrefs.ledPattern(prefs));

        OEPLPrefs.forgetLedPattern(ADDRESS);
        assertArrayEquals("and dropping it brings the plain one back",
                OEPLProtocol.ledPatternArgument(OEPLProtocol.LED_RED, 3, 5),
                OEPLPrefs.ledPattern(prefs));
    }

    @Test
    public void everyOfferedColourIsOneTheLedCanMake() {
        for (final String name : new String[]{"red", "green", "blue", "yellow", "white"}) {
            prefs.edit().putString(OEPLPrefs.PREF_LED_COLOR, name).apply();
            final int color = OEPLPrefs.ledColor(prefs);
            assertTrue(name + " is outside the one byte the tag reads",
                    color > 0 && color <= 0xff);
        }
        prefs.edit().putString(OEPLPrefs.PREF_LED_COLOR, "chartreuse").apply();
        assertEquals("an unknown colour has to fall back to the one every tag has",
                OEPLProtocol.LED_RED, OEPLPrefs.ledColor(prefs));
    }

    /**
     * A connection that only carried a command still counts as a display that was reached.
     * <p>
     * Two things hang off that. The scheduler holds a wake lock until a run reports itself
     * finished, and the retry ladder backs off from a display that keeps going quiet - so a
     * blink that was delivered may not look like a failed update. And it may not look like a
     * picture either: the transfer and failure tallies are about pictures.
     */
    @Test
    public void aConnectionThatOnlyCarriedACommandIsNotAFailedUpdate() {
        OEPLStatusStore.recordFailure(ADDRESS, "no answer");
        final long before = System.currentTimeMillis();

        OEPLStatusStore.recordCommand(ADDRESS, OEPLActions.BLINK);

        assertEquals(OEPLActions.BLINK, prefs.getString(OEPLStatusStore.LAST_RESULT, ""));
        assertTrue("the scheduler would wait for a run that never finished",
                prefs.getLong(OEPLStatusStore.LAST_RUN_MILLIS, 0L) >= before);
        assertEquals("the retry ladder climbed for a command that worked", 0,
                prefs.getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, -1));
        assertEquals("counted as a picture", 0,
                prefs.getInt(OEPLStatusStore.COUNT_TRANSFERS, 0));
        assertEquals("counted as a failure", 1,
                prefs.getInt(OEPLStatusStore.COUNT_FAILURES, 0));
    }

    /**
     * What stands between a chosen file and the display's program memory. The display itself
     * checks nothing: it writes what it is handed and runs it, and a display left with rubbish
     * in there has to be opened up and flashed over wires.
     */
    @Test
    public void onlySomethingShapedLikeFirmwareIsEverSent() {
        assertFalse("nothing at all", OEPLProtocol.looksLikeFirmware(null));
        assertFalse("a short file", OEPLProtocol.looksLikeFirmware(new byte[32]));
        assertFalse("a file of zeroes", OEPLProtocol.looksLikeFirmware(new byte[4096]));

        final byte[] jpeg = new byte[4096];
        jpeg[0] = (byte) 0xff;
        jpeg[1] = (byte) 0xd8;
        assertFalse("a photograph", OEPLProtocol.looksLikeFirmware(jpeg));

        assertTrue("a Telink image", OEPLProtocol.looksLikeFirmware(telinkImage(4096)));

        final byte[] truncated = telinkImage(4096);
        final byte[] half = Arrays.copyOf(truncated, 2048);
        assertFalse("half a download is not firmware",
                OEPLProtocol.looksLikeFirmware(half));

        // the displays built around the other radio, whose images carry a different mark
        final byte[] other = new byte[4096];
        other[4] = (byte) 0x93;
        other[5] = (byte) 0x27;
        assertTrue(OEPLProtocol.looksLikeFirmware(other));
    }

    /** A file shaped the way the firmware of these displays is: the mark and its own length. */
    private static byte[] telinkImage(final int length) {
        final byte[] image = new byte[length];
        image[8] = 'K';
        image[9] = 'N';
        image[10] = 'L';
        image[11] = 'T';
        image[24] = (byte) (length & 0xff);
        image[25] = (byte) ((length >> 8) & 0xff);
        image[26] = (byte) ((length >> 16) & 0xff);
        image[27] = (byte) ((length >> 24) & 0xff);
        return image;
    }
}
