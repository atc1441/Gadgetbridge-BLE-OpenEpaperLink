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

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * Typing the Home Assistant token once.
 * <p>
 * Every display keeps its own address and its own token, because two instances or a display
 * that should read nothing at all have to stay possible. But there is normally one Home
 * Assistant in a flat, and the token is a long piece of random text that nobody wants to type
 * twice, so the last one that was set is what the next display starts out with.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OeplHomeAssistantDefaultsTest {

    private static final String HALL = "FF:FC:4F:05:E4:12";
    private static final String DESK = "FF:FC:4F:05:E4:13";
    private static final String URL = "http://homeassistant.local:8123";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.not-a-real-one";

    @Before
    public void setUp() {
        GBApplication.getPrefs().getPreferences().edit().clear().apply();
        for (final String address : new String[]{HALL, DESK}) {
            GBApplication.getDeviceSpecificSharedPrefs(address).edit().clear().apply();
        }
    }

    private SharedPreferences display(final String address) {
        return GBApplication.getDeviceSpecificSharedPrefs(address);
    }

    private void type(final String address, final String url, final String token) {
        display(address).edit().putString(OEPLPrefs.PREF_HA_URL, url)
                .putString(OEPLPrefs.PREF_HA_TOKEN, token).apply();
        OEPLPrefs.rememberHomeAssistant(display(address));
    }

    @Test
    public void theSecondDisplayStartsWithWhatTheFirstOneWasTold() {
        type(HALL, URL, TOKEN);

        assertTrue("the new display was left to be typed out again",
                OEPLPrefs.seedHomeAssistant(DESK));
        assertEquals(URL, OEPLPrefs.homeAssistantUrl(display(DESK)));
        assertEquals(TOKEN, OEPLPrefs.homeAssistantToken(display(DESK)));
    }

    /** Still a setting of its own: changing the second display may not touch the first. */
    @Test
    public void whatIsFilledInCanBeChangedForThatDisplayAlone() {
        type(HALL, URL, TOKEN);
        OEPLPrefs.seedHomeAssistant(DESK);

        type(DESK, "http://10.0.0.5:8123", "another-token");

        assertEquals("the first display followed the second one", URL,
                OEPLPrefs.homeAssistantUrl(display(HALL)));
        assertEquals(TOKEN, OEPLPrefs.homeAssistantToken(display(HALL)));
        assertEquals("http://10.0.0.5:8123", OEPLPrefs.homeAssistantUrl(display(DESK)));
    }

    /**
     * A display that was told an address keeps it, and a display whose address was cleared on
     * purpose stays cleared. Filling in a value is a guess at what has not been decided yet,
     * never a correction of what has.
     */
    @Test
    public void aDecisionThatWasAlreadyMadeIsLeftAlone() {
        type(HALL, URL, TOKEN);

        display(DESK).edit().putString(OEPLPrefs.PREF_HA_URL, "http://10.0.0.5:8123").apply();
        OEPLPrefs.seedHomeAssistant(DESK);
        assertEquals("the display's own address was overwritten", "http://10.0.0.5:8123",
                OEPLPrefs.homeAssistantUrl(display(DESK)));
        assertEquals("the token is the one thing it was missing", TOKEN,
                OEPLPrefs.homeAssistantToken(display(DESK)));

        display(DESK).edit().putString(OEPLPrefs.PREF_HA_URL, "").apply();
        assertFalse("a cleared address came back by itself",
                OEPLPrefs.seedHomeAssistant(DESK));
        assertEquals("", OEPLPrefs.homeAssistantUrl(display(DESK)));
    }

    /** Before any display has been told, there is nothing to fill in and nothing is written. */
    @Test
    public void theFirstDisplayOfAllIsLeftEmpty() {
        assertFalse(OEPLPrefs.seedHomeAssistant(HALL));
        assertEquals("", OEPLPrefs.homeAssistantUrl(display(HALL)));
        assertFalse("an empty value was written where there was none",
                display(HALL).contains(OEPLPrefs.PREF_HA_URL));
    }

    /**
     * Clearing one display's address says what that display should read. It must not wipe the
     * memory, or the display paired after it would start out blank again.
     */
    @Test
    public void clearingOneDisplayDoesNotForgetTheToken() {
        type(HALL, URL, TOKEN);
        type(HALL, "", "");

        assertTrue(OEPLPrefs.seedHomeAssistant(DESK));
        assertEquals(URL, OEPLPrefs.homeAssistantUrl(display(DESK)));
        assertEquals(TOKEN, OEPLPrefs.homeAssistantToken(display(DESK)));
    }
}
