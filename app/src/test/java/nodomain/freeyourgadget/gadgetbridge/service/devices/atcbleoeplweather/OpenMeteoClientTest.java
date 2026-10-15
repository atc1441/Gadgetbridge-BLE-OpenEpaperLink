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

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;

/**
 * Checks the Open-Meteo mapping against a real response captured from the same endpoint the
 * OpenEPaperLink access point calls, so a change in field names or units cannot slip past.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class OpenMeteoClientTest {

    /** Captured from api.open-meteo.com, trimmed to a few slots but otherwise untouched. */
    private static final String RESPONSE =
            "{\"latitude\":53.55673,\"longitude\":9.996149,\"utc_offset_seconds\":7200,\"timezone\":\"Europe/Ber"
            + "lin\",\"timezone_abbreviation\":\"GMT+2\",\"elevation\":11.0,\"current_weather\":{\"time\":1791658800"
            + ",\"interval\":900,\"temperature\":10.9,\"windspeed\":3.76,\"winddirection\":220,\"is_day\":0,\"weathe"
            + "rcode\":61},\"hourly\":{\"time\":[1791583200,1791586800,1791590400,1791594000,1791597600,179160"
            + "1200],\"temperature_2m\":[14.3,14.3,14.3,13.7,12.7,12.3],\"weathercode\":[53,61,80,2,2,3],\"rel"
            + "ativehumidity_2m\":[90,91,87,87,89,90]},\"daily\":{\"time\":[1791583200,1791669600,1791756000],"
            + "\"weathercode\":[80,80,80],\"temperature_2m_max\":[15.1,12.1,13.8],\"temperature_2m_min\":[10.2,"
            + "8.3,6.5],\"sunrise\":[1791610663,1791697172,1791783682],\"sunset\":[1791650125,1791736386,1791"
            + "822646]}}";

    private static WeatherSpec parsed() throws Exception {
        return OpenMeteoClient.parse(new JSONObject(RESPONSE), "Hamburg");
    }

    @Test
    public void mapsCurrentConditions() throws Exception {
        final WeatherSpec spec = parsed();
        assertEquals("Hamburg", spec.getLocation());
        // the API answers in celsius, WeatherSpec carries kelvin
        assertEquals(284, spec.getCurrentTemp());
        assertEquals("m/s becomes km/h", 13.5, spec.getWindSpeed(), 0.05);
        assertEquals(220, spec.getWindDirection());
        assertEquals("WMO 61 maps onto an OpenWeatherMap code",
                OpenMeteoClient.toOwmCode(61), spec.getCurrentConditionCode());
        assertFalse(spec.getCurrentCondition().isEmpty());
    }

    @Test
    public void todayGoesOnTheCurrentConditionsNotIntoTheForecast() throws Exception {
        final WeatherSpec spec = parsed();
        assertEquals("today's high", 288, spec.getTodayMaxTemp());
        assertEquals("today's low", 283, spec.getTodayMinTemp());
        assertEquals("the daily list starts tomorrow", 2, spec.getForecasts().size());
        assertEquals("tomorrow's high", 285, spec.getForecasts().get(0).getMaxTemp());
    }

    @Test
    public void sunriseAndSunsetArriveAsUnixTime() throws Exception {
        final WeatherSpec spec = parsed();
        assertEquals(1791610663, spec.getSunRise());
        assertEquals(1791650125, spec.getSunSet());
        assertTrue("sunset follows sunrise", spec.getSunSet() > spec.getSunRise());
    }

    @Test
    public void hourlySkipsSlotsAlreadyInThePast() throws Exception {
        final WeatherSpec spec = parsed();
        final long now = System.currentTimeMillis() / 1000L;
        for (final WeatherSpec.Hourly h : spec.getHourly()) {
            assertTrue("no slot older than half an hour", h.getTimestamp() >= now - 1800);
        }
        for (int i = 1; i < spec.getHourly().size(); i++) {
            assertTrue("timestamps ascend", spec.getHourly().get(i).getTimestamp()
                    > spec.getHourly().get(i - 1).getTimestamp());
        }
    }

    @Test
    public void translatesTheWmoVocabulary() {
        assertEquals("clear", 800, OpenMeteoClient.toOwmCode(0));
        assertEquals("overcast", 804, OpenMeteoClient.toOwmCode(3));
        assertEquals("fog", 741, OpenMeteoClient.toOwmCode(45));
        assertEquals("heavy rain", 502, OpenMeteoClient.toOwmCode(65));
        assertEquals("heavy snow", 602, OpenMeteoClient.toOwmCode(75));
        assertEquals("thunderstorm", 200, OpenMeteoClient.toOwmCode(95));
        assertEquals("anything unknown stays cloudy", 802, OpenMeteoClient.toOwmCode(123));
    }

    @Test
    public void survivesAThinResponse() throws Exception {
        final WeatherSpec spec = OpenMeteoClient.parse(new JSONObject("{}"), "nowhere");
        assertEquals("nowhere", spec.getLocation());
        assertTrue(spec.getForecasts().isEmpty());
        assertTrue(spec.getHourly().isEmpty());
    }
}
