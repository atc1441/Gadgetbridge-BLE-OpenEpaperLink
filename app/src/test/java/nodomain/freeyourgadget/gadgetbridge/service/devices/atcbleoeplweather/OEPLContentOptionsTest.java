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

import org.junit.Test;

import java.util.EnumSet;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLContentOptions.Option;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;

/**
 * The settings screen shows an option group when, and only when, the selected card reads
 * something inside it. That makes {@link OEPLContentOptions} the one place where a card and its
 * settings can drift apart, so these check the cases that would be noticed least.
 */
public class OEPLContentOptionsTest {

    /** Every id the picker offers, in the order of the settings array. */
    private static final String[] ALL = {
            "oepl_4", "oepl_8", "oepl_16", "oepl_1", "oepl_29", "oepl_11", "oepl_11w", "oepl_9",
            "oepl_10", "oepl_2", "oepl_3", "oepl_21",
            "panel_big", "panel_bars", "panel_sun", "panel_tiles", "panel_board",
            "panel_poster", "panel_grid", "panel_hero", "panel_list", "panel_graph",
            "panel_quad", "panel_month",
            "weather", "date", "calendar", "text", "status", "weather_calendar", "weather_date",
            "date_calendar", "clock", "notifications", "music", "hourly", "forecast",
            "clock_weather", "weather_notifications", "calendar_notifications", "dashboard",
            "image", "image_url",
    };

    @Test
    public void aCardThatNeedsAForecastIsTheOnlyOneOfferedAWeatherSource() {
        for (final String id : ALL) {
            final boolean source = OEPLContentOptions.uses(id, Option.WEATHER);
            // the hours and days only have meaning while there is a forecast to cut up
            if (OEPLContentOptions.uses(id, Option.FORECAST_DAYS)
                    || OEPLContentOptions.uses(id, Option.FORECAST_HOURS)
                    || OEPLContentOptions.uses(id, Option.WIND_UNIT)
                    || OEPLContentOptions.uses(id, Option.SUN_TIMES)) {
                assertTrue(id + " reads forecast details without a forecast", source);
            }
        }
    }

    @Test
    public void onlyTheCardsDrawnInsideTheFrameOfferTheFrameOptions() {
        for (final String id : ALL) {
            // the OpenEPaperLink layouts and the picture cards own every pixel of the panel,
            // so a title bar and a status strip are not theirs to offer
            final boolean fullBleed = OeplCardFactory.handles(id)
                    || CardContentFactory.IMAGE.equals(id)
                    || CardContentFactory.IMAGE_URL.equals(id);
            assertEquals(id, !fullBleed, OEPLContentOptions.uses(id, Option.FRAME));
        }
    }

    @Test
    public void aCardWithNothingToConfigureOffersNothing() {
        assertEquals(EnumSet.noneOf(Option.class), OEPLContentOptions.of(OeplCardFactory.CLOCK));
        assertEquals(EnumSet.noneOf(Option.class), OEPLContentOptions.of(OeplCardFactory.INFO));
        // the framed clock still sits inside a frame, so that much stays adjustable
        assertEquals(EnumSet.of(Option.FRAME), OEPLContentOptions.of("clock"));
    }

    @Test
    public void theTwoCalendarCardsAskForDifferentThings() {
        // the list is cut off by a number of appointments, the week view by a number of days
        assertTrue(OEPLContentOptions.uses(OeplCardFactory.CALENDAR, Option.CALENDAR_EVENTS));
        assertTrue(OEPLContentOptions.uses(OeplCardFactory.CALENDAR, Option.CALENDAR_TITLE));
        assertFalse(OEPLContentOptions.uses(OeplCardFactory.CALENDAR, Option.CALENDAR_DAYS));

        assertTrue(OEPLContentOptions.uses(OeplCardFactory.CALENDAR_WEEK, Option.CALENDAR_DAYS));
        assertFalse(OEPLContentOptions.uses(OeplCardFactory.CALENDAR_WEEK,
                Option.CALENDAR_EVENTS));
    }

    @Test
    public void theCombinedCardsAskForEverythingTheirHalvesDo() {
        final EnumSet<Option> dashboard = OEPLContentOptions.of("dashboard");
        assertTrue(dashboard.containsAll(OEPLContentOptions.of("weather")));
        assertTrue(dashboard.containsAll(OEPLContentOptions.of("calendar")));
        assertTrue(dashboard.containsAll(OEPLContentOptions.of("notifications")));
    }

    @Test
    public void noCardOffersMoreThanAHandfulOfOptions() {
        // this is the whole point of the sub-menus: showing all of them at once says nothing
        for (final String id : ALL) {
            if (OeplCardFactory.QUAD.equals(id)) {
                // except for the one card made of other cards, where every menu may belong to
                // a quarter and none of them can be ruled out without reading the slots
                continue;
            }
            assertTrue(id + " got out of hand", OEPLContentOptions.of(id).size() <= 5);
        }
    }

    @Test
    public void anUnknownContentFallsBackToTheWeatherCardRatherThanToNothing() {
        assertEquals(OEPLContentOptions.of("weather"), OEPLContentOptions.of("something else"));
        assertEquals(OEPLContentOptions.of("weather"), OEPLContentOptions.of(null));
    }

    @Test
    public void everyOptionIsReachableBySomeCard() {
        final EnumSet<Option> reachable = EnumSet.noneOf(Option.class);
        for (final String id : ALL) {
            reachable.addAll(OEPLContentOptions.of(id));
        }
        // an option no card reads would be a setting the user can never see
        assertEquals(EnumSet.allOf(Option.class), reachable);
    }
}
