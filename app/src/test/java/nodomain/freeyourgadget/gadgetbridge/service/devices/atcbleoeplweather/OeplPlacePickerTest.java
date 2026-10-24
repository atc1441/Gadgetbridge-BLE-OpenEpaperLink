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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * Telling two places of the same name apart.
 * <p>
 * The reason this screen exists at all: a name is not an address. Asking a geocoder for
 * "Springfield" is asking it to choose, and it chooses by population - which is a perfectly
 * good guess and the wrong town often enough that a display can show somebody else's weather
 * for weeks without anything looking broken.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplPlacePickerTest {

    private static final String ADDRESS = "00:11:22:33:44:55";

    /** Shaped like a geocoding answer: three namesakes, one of them with no region at all. */
    private static final String ANSWER =
            "{\"results\":["
            + "{\"id\":1,\"name\":\"Springfield\",\"latitude\":39.80172,\"longitude\":-89.64371,"
            + "\"country\":\"United States\",\"admin1\":\"Illinois\",\"admin2\":\"Sangamon\","
            + "\"population\":116250},"
            + "{\"id\":2,\"name\":\"Springfield\",\"latitude\":37.21533,\"longitude\":-93.29824,"
            + "\"country\":\"United States\",\"admin1\":\"Missouri\",\"population\":167319},"
            + "{\"id\":3,\"name\":\"Springfield\",\"latitude\":-37.0,\"longitude\":174.9,"
            + "\"country\":\"New Zealand\"}"
            + "]}";

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = GBApplication.getDeviceSpecificSharedPrefs(ADDRESS);
        prefs.edit().clear().apply();
    }

    @Test
    public void everyNamesakeIsOfferedRatherThanTheBiggestOne() throws Exception {
        final List<OpenMeteoClient.Match> matches =
                OpenMeteoClient.parseMatches(new JSONObject(ANSWER), "Springfield");

        assertEquals("all three, in the order they came back", 3, matches.size());
        assertEquals("Sangamon, Illinois, United States", matches.get(0).where());
        assertEquals("Missouri, United States", matches.get(1).where());
        assertEquals("a place the service knows no region for is still a place",
                "New Zealand", matches.get(2).where());
    }

    /**
     * What is written down is the position, not the name. A name would be looked up again on
     * the next forecast and could come back as the other town, which is the whole problem.
     */
    @Test
    public void whatIsStoredIsThePositionAndNotTheNameAgain() {
        final List<OpenMeteoClient.Match> matches;
        try {
            matches = OpenMeteoClient.parseMatches(new JSONObject(ANSWER), "Springfield");
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
        final OpenMeteoClient.Match missouri = matches.get(1);
        assertEquals("37.2153,-93.2982", missouri.coordinates());

        OEPLPrefs.rememberPlace(ADDRESS, missouri.coordinates(), missouri.name);

        assertEquals(missouri.coordinates(), OEPLPrefs.weatherLocation(prefs));
        assertEquals("and the card still has a place name to write",
                "Springfield", OEPLPrefs.weatherPlaceName(prefs));
    }

    /**
     * Typing a different place over a picked one has to take its name with it. Otherwise the
     * panel heads the new town's forecast with the old town's name, which is the kind of wrong
     * that reads as right.
     */
    @Test
    public void theNameDoesNotOutliveThePlaceItBelongedTo() {
        OEPLPrefs.rememberPlace(ADDRESS, "37.2153,-93.2982", "Springfield");
        assertEquals("Springfield", OEPLPrefs.weatherPlaceName(prefs));

        prefs.edit().putString(OEPLPrefs.PREF_WEATHER_LOCATION, "Hamburg").apply();

        assertEquals("the name belongs to the coordinates, not to the field",
                "", OEPLPrefs.weatherPlaceName(prefs));
        assertEquals("Hamburg", OEPLPrefs.weatherLocation(prefs));
    }

    /** Two namesakes may not end up as the same stored place. */
    @Test
    public void twoPlacesOfOneNameAreTwoDifferentThingsToStore() throws Exception {
        final List<OpenMeteoClient.Match> matches =
                OpenMeteoClient.parseMatches(new JSONObject(ANSWER), "Springfield");
        assertNotEquals(matches.get(0).coordinates(), matches.get(1).coordinates());
        assertNotEquals(matches.get(1).coordinates(), matches.get(2).coordinates());
        for (final OpenMeteoClient.Match match : matches) {
            assertTrue("a stored place has to read back as coordinates: " + match.coordinates(),
                    match.coordinates().matches("-?\\d+\\.\\d{4},-?\\d+\\.\\d{4}"));
        }
    }

    /** An answer with nothing in it is an empty list rather than a crash or a guess. */
    @Test
    public void anUnknownNameOffersNothingAtAll() throws Exception {
        assertTrue(OpenMeteoClient.parseMatches(new JSONObject("{}"), "Atlantis").isEmpty());
        assertTrue(OpenMeteoClient.parseMatches(
                new JSONObject("{\"results\":[]}"), "Atlantis").isEmpty());
    }
}
