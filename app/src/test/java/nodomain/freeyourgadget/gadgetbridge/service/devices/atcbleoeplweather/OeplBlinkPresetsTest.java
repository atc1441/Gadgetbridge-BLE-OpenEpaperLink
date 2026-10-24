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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * Keeping a blink pattern and handing it to the next display.
 * <p>
 * Shaping one is a dozen slider pulls, so it is worth keeping - and worth keeping where every
 * display can reach it, because a pattern that lived only on the panel it was made for would
 * have to be made again for each of its neighbours.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OeplBlinkPresetsTest {

    private static final String ONE = "AA:BB:CC:DD:EE:01";
    private static final String TWO = "AA:BB:CC:DD:EE:02";

    private static byte[] pattern(final int red, final int flashes) {
        return OEPLProtocol.ledPattern(
                new int[]{OEPLProtocol.ledColor(red, 0, 0), 0, 0},
                new int[]{flashes, 1, 1}, new int[]{2, 2, 8}, 3, 16);
    }

    @Before
    public void setUp() {
        GBApplication.getPrefs().getPreferences().edit()
                .remove(OEPLBlinkPresets.SAVED)
                .remove(OEPLBlinkPresets.DEFAULT_NAME)
                .apply();
        for (final String address : new String[]{ONE, TWO}) {
            GBApplication.getDeviceSpecificSharedPrefs(address).edit().clear().apply();
        }
    }

    @Test
    public void aKeptPatternIsThereForEveryDisplayAfterwards() {
        final byte[] hall = pattern(7, 2);
        OEPLBlinkPresets.save("Hall", hall);

        final List<OEPLBlinkPresets.Preset> all = OEPLBlinkPresets.all();
        assertEquals(1, all.size());
        assertEquals("Hall", all.get(0).name);
        assertArrayEquals(hall, all.get(0).pattern);

        // the display it was made on knows nothing about it until it is handed over
        assertNotEquals("", OEPLProtocol.ledPatternToHex(
                OEPLPrefs.ledPattern(GBApplication.getDeviceSpecificSharedPrefs(TWO))));
        OEPLPrefs.storeLedPattern(TWO, OEPLBlinkPresets.byName("Hall"));
        assertArrayEquals(hall,
                OEPLPrefs.ledPattern(GBApplication.getDeviceSpecificSharedPrefs(TWO)));
    }

    /** Keeping one under a name that is taken replaces it rather than listing it twice. */
    @Test
    public void aNameUsedTwiceIsOnePattern() {
        OEPLBlinkPresets.save("Kitchen", pattern(7, 2));
        OEPLBlinkPresets.save("Kitchen", pattern(0, 5));

        assertEquals(1, OEPLBlinkPresets.all().size());
        assertArrayEquals(pattern(0, 5), OEPLBlinkPresets.byName("Kitchen"));
    }

    /**
     * The default: what a display blinks when nobody has shaped one for it. That is what makes
     * a shelf of panels look alike without visiting each of them.
     */
    @Test
    public void theDefaultStandsInForEveryDisplayNobodyShaped() {
        final SharedPreferences fresh = GBApplication.getDeviceSpecificSharedPrefs(ONE);
        final byte[] plain = OEPLPrefs.ledPattern(fresh);

        OEPLBlinkPresets.save("Shelf", pattern(0, 4));
        assertArrayEquals("saving alone changes nothing", plain, OEPLPrefs.ledPattern(fresh));

        OEPLBlinkPresets.makeDefault("Shelf");
        assertArrayEquals(pattern(0, 4), OEPLPrefs.ledPattern(fresh));

        // a display that was shaped by hand keeps what it was given
        OEPLPrefs.storeLedPattern(ONE, pattern(7, 1));
        assertArrayEquals(pattern(7, 1), OEPLPrefs.ledPattern(fresh));

        OEPLBlinkPresets.clearDefault();
        OEPLPrefs.forgetLedPattern(ONE);
        assertArrayEquals(plain, OEPLPrefs.ledPattern(fresh));
    }

    /** Throwing away the one that was the default has to take the default with it. */
    @Test
    public void deletingTheDefaultLeavesNoDisplayPointingAtNothing() {
        OEPLBlinkPresets.save("Gone", pattern(7, 3));
        OEPLBlinkPresets.makeDefault("Gone");
        assertEquals("Gone", OEPLBlinkPresets.defaultName());

        OEPLBlinkPresets.remove("Gone");

        assertTrue(OEPLBlinkPresets.all().isEmpty());
        assertEquals("", OEPLBlinkPresets.defaultName());
        assertNull(OEPLBlinkPresets.defaultPattern());
        assertArrayEquals("and the display goes back to the plain blink",
                OEPLProtocol.ledPatternArgument(OEPLProtocol.LED_RED, 3, 5),
                OEPLPrefs.ledPattern(GBApplication.getDeviceSpecificSharedPrefs(ONE)));
    }

    /**
     * The few patterns worth reaching from the main list are marked for it, and the mark
     * belongs to the pattern rather than to a display - the same button is wanted on every
     * card, which is what makes it a way of pointing at one panel among them.
     */
    @Test
    public void onlyTheMarkedOnesReachTheDeviceCards() {
        OEPLBlinkPresets.save("Shelf", pattern(7, 2));
        OEPLBlinkPresets.save("Alarm", pattern(0, 6));
        assertTrue("nothing is on the cards until it is put there",
                OEPLBlinkPresets.onCards().isEmpty());

        OEPLBlinkPresets.showOnCards("Alarm", true);

        final List<OEPLBlinkPresets.Preset> shown = OEPLBlinkPresets.onCards();
        assertEquals(1, shown.size());
        assertEquals("Alarm", shown.get(0).name);
        assertArrayEquals(pattern(0, 6), shown.get(0).pattern);

        OEPLBlinkPresets.showOnCards("Alarm", false);
        assertTrue(OEPLBlinkPresets.onCards().isEmpty());
    }

    /** Changing a pattern and keeping it under its own name leaves it where it was. */
    @Test
    public void workingOnOneKeepsItsPlaceOnTheCards() {
        OEPLBlinkPresets.save("Alarm", pattern(7, 2));
        OEPLBlinkPresets.showOnCards("Alarm", true);

        OEPLBlinkPresets.save("Alarm", pattern(0, 6));

        assertEquals(1, OEPLBlinkPresets.all().size());
        assertArrayEquals("the new shape", pattern(0, 6), OEPLBlinkPresets.byName("Alarm"));
        assertEquals("and still on the cards", 1, OEPLBlinkPresets.onCards().size());
    }

    /**
     * A pattern sent from a device card blinks once and is not taken on by the display:
     * looking for a panel may not quietly change what it blinks from then on.
     */
    @Test
    public void aBlinkFromTheListDoesNotBecomeTheDisplaysOwn() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(ONE);
        OEPLPrefs.storeLedPattern(ONE, pattern(7, 1));

        OEPLPrefs.blinkOnceWith(ONE, pattern(0, 9));

        assertArrayEquals("the one-off is what goes out", pattern(0, 9),
                OEPLPrefs.takeLedPatternOnce(prefs, ONE));
        assertArrayEquals("and once is once", pattern(7, 1),
                OEPLPrefs.takeLedPatternOnce(prefs, ONE));
        assertArrayEquals("the display kept its own all along", pattern(7, 1),
                OEPLPrefs.ledPattern(prefs));
    }

    /**
     * The list is held together by a tab and a newline, so a name carrying either would
     * swallow whatever was saved behind it.
     */
    @Test
    public void aNameCannotTearTheListApart() {
        final String saved = OEPLBlinkPresets.save("one\ttwo\nthree", pattern(7, 1));
        OEPLBlinkPresets.save("Second", pattern(0, 2));

        assertEquals("one two three", saved);
        assertEquals(2, OEPLBlinkPresets.all().size());
        assertArrayEquals(pattern(0, 2), OEPLBlinkPresets.byName("Second"));

        assertEquals("something has to be left to read the list by", "?",
                OEPLBlinkPresets.save("   ", pattern(7, 1)));
    }
}
