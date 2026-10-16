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

import android.graphics.Bitmap;
import android.graphics.RectF;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplForecastCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRangeCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeather;

/**
 * Which day a card calls today.
 * <p>
 * A forecast in hand can be older than it looks: the network was unreachable for a few hours,
 * or overnight, and the display drew the picture anyway from the last answer - which is the
 * right thing to do, except that the first day of that answer is then yesterday. A card that
 * puts yesterday in the column a reader takes for today is worse than one that shows a day
 * less.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplTodayTest {

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    @Test
    public void aDayThatIsOverIsDropped() {
        final OeplWeather stale = forecastFrom(-1, 5);
        assertEquals("yesterday has to go, the other four stay",
                4, stale.upcomingDays().size());
        assertTrue("the first day left is today",
                stale.isToday(stale.upcomingDays().get(0)));
        assertFalse("...and the second one is not",
                stale.isToday(stale.upcomingDays().get(1)));
    }

    @Test
    public void aForecastEntirelyInThePastLeavesNothing() {
        final OeplWeather ancient = forecastFrom(-4, 3);
        assertTrue(ancient.upcomingDays().isEmpty());
        // and the cards then say they have nothing rather than drawing last week
        assertFalse(new OeplForecastCard(ancient, 5, true).hasData());
        assertFalse(new OeplRangeCard(ancient, 5).hasData());
    }

    @Test
    public void aFreshForecastKeepsEveryDayItHas() {
        final OeplWeather fresh = forecastFrom(0, 7);
        assertEquals(7, fresh.upcomingDays().size());
        assertTrue(fresh.isToday(fresh.upcomingDays().get(0)));
    }

    @Test
    public void theColumnForTodaySaysSo() {
        // a forecast that still has yesterday in it, drawn as it would reach the panel
        final String yesterday = new SimpleDateFormat("EE", Locale.getDefault())
                .format(new Date(System.currentTimeMillis() - 86400000L));
        final StringBuilder drawn = new StringBuilder();
        for (final OeplInkRecorder.Run run
                : draw(new OeplForecastCard(forecastFrom(-1, 5), 5, true)).textRuns()) {
            drawn.append(run.what).append('|');
        }
        assertTrue("the column one is standing in has to say so: " + drawn,
                drawn.toString().contains("Today"));
        assertFalse("yesterday was drawn into the forecast: " + drawn,
                drawn.toString().contains(yesterday + "|"));
    }

    // ------------------------------------------------------------------ where the hours are

    @Test
    public void theHoursOfAForecastBelongToThePlaceItIsFor() {
        final OeplWeather weather = forecastFrom(0, 3);
        weather.utcOffsetSeconds = 2 * 3600;   // the place is two hours ahead of UTC
        // 22:00 UTC is midnight there, and midnight is what the card has to print
        final java.util.Date at = new java.util.Date(1792101600L * 1000L);
        assertEquals("00", weather.format("HH").format(at));
        assertEquals("GMT+02:00", weather.zone().getID());

        weather.utcOffsetSeconds = -5 * 3600;  // and five hours behind it
        assertEquals("17", weather.format("HH").format(at));
        assertEquals("GMT-05:00", weather.zone().getID());
    }

    @Test
    public void aForecastWithoutAnOffsetFollowsThePhone() {
        final int phone = java.util.TimeZone.getDefault()
                .getOffset(System.currentTimeMillis()) / 1000;
        assertEquals("the normal case: the weather of the town the display hangs in",
                phone, new OeplWeather().utcOffsetSeconds);
    }

    // ------------------------------------------------------------------ the tag's own clock

    @Test
    public void theTagIsGivenLocalTimeLowestByteFirst() {
        final long millis = 1792101600L * 1000L;           // 22:00 UTC
        final byte[] arg = OEPLProtocol.unixTimeArgument(millis,
                java.util.TimeZone.getTimeZone("GMT+02:00"));
        assertEquals(4, arg.length);
        long value = 0;
        for (int i = 3; i >= 0; i--) {
            value = (value << 8) | (arg[i] & 0xffL);
        }
        assertEquals("the tag has no time zone, so it is given the local moment",
                1792101600L + 7200L, value);
        // and lowest byte first, which is how the firmware reads it back
        assertEquals((byte) ((1792101600L + 7200L) & 0xff), arg[0]);
    }

    /**
     * A forecast whose first day is the given number of days from today.
     *
     * @param firstDayOffset -1 for a forecast fetched yesterday and never refreshed since
     */
    private OeplWeather forecastFrom(final int firstDayOffset, final int days) {
        final OeplWeather weather = new OeplWeather();
        weather.location = "Hamburg";
        weather.current.temperature = 13.4f;
        final java.util.Calendar midnight = java.util.Calendar.getInstance();
        midnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
        midnight.set(java.util.Calendar.MINUTE, 0);
        midnight.set(java.util.Calendar.SECOND, 0);
        midnight.set(java.util.Calendar.MILLISECOND, 0);
        midnight.add(java.util.Calendar.DAY_OF_YEAR, firstDayOffset);
        for (int i = 0; i < days; i++) {
            final OeplWeather.Day day = new OeplWeather.Day();
            day.epochSeconds = midnight.getTimeInMillis() / 1000L + i * 86400L;
            day.wmoCode = 3;
            day.maxTemperature = 15f + i;
            day.minTemperature = 7f + i;
            day.precipitation = 1f;
            day.windSpeed = 4f;
            day.windDirection = 225;
            weather.days.add(day);
        }
        return weather;
    }

    private OeplInkRecorder draw(final CardContent content) {
        final Bitmap sheet = Bitmap.createBitmap(384, 184, Bitmap.Config.ARGB_8888);
        final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
        recorder.drawColor(0xffffffff);
        content.draw(recorder, new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                OEPLImageCodec.RED, false), new RectF(0, 0, 384, 184));
        return recorder;
    }
}
