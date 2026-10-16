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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;

/**
 * What happens between choosing "picture" and choosing the picture.
 * <p>
 * Those are two steps, and in between the card carries nothing but the words "no picture".
 * Sending that wipes whatever the panel was showing, costs a connection and a full refresh of
 * the display, and is replaced a few seconds later - so a card whose content is handed over by
 * hand waits until it has been, and the panel keeps what it had.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OeplWaitingForSourceTest {

    private static final String ADDRESS = "FF:FC:4F:05:E4:12";

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = GBApplication.getDeviceSpecificSharedPrefs(ADDRESS);
        prefs.edit().clear().apply();
    }

    private void set(final String key, final String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Test
    public void aPictureCardWithoutAPictureWaits() {
        set(OEPLPrefs.PREF_CONTENT, CardContentFactory.IMAGE);
        assertNotNull("the panel would be wiped for a message saying nothing",
                CardContentFactory.waitingForSource(prefs));

        set(OEPLPrefs.PREF_IMAGE_URI, "content://media/external/images/media/42");
        assertNull("the picture is there, so there is something to send",
                CardContentFactory.waitingForSource(prefs));
    }

    @Test
    public void aPictureFromTheWebWaitsForItsAddress() {
        set(OEPLPrefs.PREF_CONTENT, CardContentFactory.IMAGE_URL);
        assertNotNull(CardContentFactory.waitingForSource(prefs));

        set(OEPLPrefs.PREF_IMAGE_URL, "https://example.invalid/panel.png");
        assertNull(CardContentFactory.waitingForSource(prefs));
    }

    @Test
    public void theOtherCardsThatAreHandedTheirContentWaitTheSameWay() {
        set(OEPLPrefs.PREF_CONTENT, OeplCardFactory.QR);
        assertNotNull("an empty code is not worth a refresh",
                CardContentFactory.waitingForSource(prefs));
        set(OEPLPrefs.PREF_QR_CONTENT, "https://example.invalid");
        assertNull(CardContentFactory.waitingForSource(prefs));

        set(OEPLPrefs.PREF_CONTENT, OeplCardFactory.RSS);
        assertNotNull(CardContentFactory.waitingForSource(prefs));
        set(OEPLPrefs.PREF_RSS_URL, "https://example.invalid/feed.xml");
        assertNull(CardContentFactory.waitingForSource(prefs));

        set(OEPLPrefs.PREF_CONTENT, CardContentFactory.TEXT);
        assertNotNull(CardContentFactory.waitingForSource(prefs));
        set(OEPLPrefs.PREF_CUSTOM_TEXT, "   ");
        assertNotNull("blank is as unset as empty",
                CardContentFactory.waitingForSource(prefs));
        set(OEPLPrefs.PREF_CUSTOM_TEXT, "Milch holen");
        assertNull(CardContentFactory.waitingForSource(prefs));
    }

    /**
     * Only a source that was never set holds an update back. A card that draws itself from
     * something the display already knows must never wait, or a display would simply stop
     * updating - and a feed that does not answer is a different thing entirely: there the user
     * has said what they want, and the card saying it did not arrive is the useful answer.
     */
    @Test
    public void everyOtherCardIsSentStraightAway() {
        for (final String content : new String[]{"weather", "date", "calendar", "clock",
                "status", "notifications", "music", "hourly", "forecast", "dashboard",
                "oepl_4", "panel_grid", "panel_month", "panel_quad"}) {
            set(OEPLPrefs.PREF_CONTENT, content);
            assertNull(content + " would never be sent",
                    CardContentFactory.waitingForSource(prefs));
        }
    }
}
