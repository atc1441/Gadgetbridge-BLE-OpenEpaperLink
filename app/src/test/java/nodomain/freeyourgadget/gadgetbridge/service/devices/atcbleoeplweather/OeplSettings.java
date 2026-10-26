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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CalendarContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ClockContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.DateContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ForecastContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.HourlyWeatherContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ImageContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.MusicContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.NotificationsContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.QuadContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.SplitContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.StatusContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.TextContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.WeatherContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplBigTemperatureCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplBoardCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCalendarListCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCalendarWeekCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplClockCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCounterCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplDateCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplForecastCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGlyphs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGraphCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGridCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplHeroCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplInfoCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplListCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplMonthCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplPosterCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplQrCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplReading;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRainCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRangeCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplSunCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplTilesCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWallCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeather;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeatherCard;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * Every design once for every value its settings can be set to.
 * <p>
 * {@link OeplDemo} builds one card of each kind with data chosen to put pressure on it; this
 * builds the same cards the way a user can actually configure them - two appointments or eight,
 * a forecast of three days or of seven, six hours of rain or twenty four, a caption that is
 * empty or one that fills the panel. The numbers here are the ones the settings screen offers
 * and no others, so a value that is added to a list in arrays.xml belongs in the list here too.
 * <p>
 * It exists because a layout is not one layout. The appointment list at two entries has rows
 * four times the height it has at eight, and the week grid at one day has columns seven times
 * the width it has at seven - which is where its headings came out as a row of single letters.
 * Looking at one setting of a design says nothing about the others.
 */
public final class OeplSettings {

    /** One design, set up one particular way. */
    public static class Case {
        /** The content id, as the settings list offers it. */
        public final String design;
        /** What the settings were set to, for the report and for the failure message. */
        public final String settings;
        /** Whether this case is built on the deliberately overlong demo data. */
        public final boolean stress;
        /**
         * How this case builds its card, for the tables that are easier written as a card than
         * as a setting to be parsed back out of a string. Null for the ones {@link #build}
         * knows how to make from the design and the settings alone.
         */
        private final Supplier<CardContent> supplier;

        Case(final String design, final String settings, final boolean stress) {
            this(design, settings, stress, null);
        }

        Case(final String design, final String settings, final boolean stress,
             final Supplier<CardContent> supplier) {
            this.design = design;
            this.settings = settings;
            this.stress = stress;
            this.supplier = supplier;
        }

        /**
         * The card, built fresh. Cards hold the data they were given, and the demo data is
         * built around the present moment, so a case is never kept from one panel to the next.
         */
        public CardContent card() {
            OeplDemo.stress(stress);
            try {
                return supplier != null ? supplier.get() : build(this);
            } finally {
                OeplDemo.stress(false);
            }
        }

        @Override
        public String toString() {
            return design + " [" + settings + "]";
        }
    }

    private OeplSettings() {
    }

    // the values the settings screen offers, from app/src/main/res/values/arrays.xml
    private static final int[] CALENDAR_EVENTS = {2, 3, 4, 6, 8};
    private static final int[] CALENDAR_DAYS = {1, 2, 3, 4, 5, 7};
    private static final int[] FORECAST_DAYS = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final int[] FORECAST_HOURS = {6, 12, 18, 24};
    private static final int[] NOTIFICATION_COUNT = {2, 3, 4, 6, 8};

    private static final String LONG_CAPTION = "Familienkalender, Schule und Werkstatttermine";

    public static List<Case> all() {
        final List<Case> cases = new ArrayList<>();

        // ---------------------------------------------------------- the OpenEPaperLink cards
        cases.add(one(OeplCardFactory.WEATHER, "wind=beaufort"));
        cases.add(one(OeplCardFactory.WEATHER, "wind=m/s"));
        cases.add(stress(OeplCardFactory.WEATHER, "wind=beaufort, long place"));

        for (final int days : FORECAST_DAYS) {
            cases.add(one(OeplCardFactory.FORECAST, "days=" + days + ", wind=beaufort"));
            cases.add(one(OeplCardFactory.FORECAST, "days=" + days + ", wind=m/s"));
        }
        cases.add(stress(OeplCardFactory.FORECAST, "days=7, long place"));

        for (final int hours : FORECAST_HOURS) {
            cases.add(one(OeplCardFactory.RAIN, "hours=" + hours));
        }

        cases.add(one(OeplCardFactory.DATE, "sun=on"));
        cases.add(one(OeplCardFactory.DATE, "sun=off"));
        cases.add(one(OeplCardFactory.DATE, "no weather"));

        cases.add(one(OeplCardFactory.CLOCK, "-"));

        for (final int entries : CALENDAR_EVENTS) {
            cases.add(one(OeplCardFactory.CALENDAR, "entries=" + entries + ", caption=short"));
            cases.add(stress(OeplCardFactory.CALENDAR, "entries=" + entries + ", caption=long"));
        }
        cases.add(one(OeplCardFactory.CALENDAR, "entries=4, caption=empty"));
        cases.add(one(OeplCardFactory.CALENDAR, "nothing but an all day entry"));

        for (final int days : CALENDAR_DAYS) {
            cases.add(one(OeplCardFactory.CALENDAR_WEEK, "days=" + days));
            cases.add(stress(OeplCardFactory.CALENDAR_WEEK, "days=" + days + ", full calendar"));
        }
        cases.add(one(OeplCardFactory.CALENDAR_WEEK, "days=7, nothing in the diary"));
        cases.add(one(OeplCardFactory.CALENDAR_WEEK, "days=3, a holiday over three of them"));
        cases.add(one(OeplCardFactory.CALENDAR_WEEK, "days=3, from six in the morning"));

        cases.add(one(OeplCardFactory.RSS, "summaries=off, title=short"));
        cases.add(one(OeplCardFactory.RSS, "summaries=on, title=short"));
        cases.add(stress(OeplCardFactory.RSS, "summaries=off, title=long"));
        cases.add(stress(OeplCardFactory.RSS, "summaries=on, title=long"));

        cases.add(one(OeplCardFactory.QR, "short link, title=short"));
        cases.add(one(OeplCardFactory.QR, "long link, title=short"));
        cases.add(one(OeplCardFactory.QR, "short link, no title"));
        cases.add(stress(OeplCardFactory.QR, "long link, title=long"));

        cases.add(one(OeplCardFactory.COUNT_DAYS, "42 days, threshold=30, caption=short"));
        cases.add(one(OeplCardFactory.COUNT_DAYS, "0 days, threshold=30"));
        cases.add(one(OeplCardFactory.COUNT_DAYS, "365 days, no caption"));
        cases.add(stress(OeplCardFactory.COUNT_DAYS, "1000 days, caption=long"));
        cases.add(one(OeplCardFactory.COUNT_HOURS, "7 hours, no threshold"));
        cases.add(one(OeplCardFactory.COUNT_HOURS, "123456 hours, threshold=10"));
        cases.add(stress(OeplCardFactory.COUNT_HOURS, "48 hours, caption=long"));

        cases.add(one(OeplCardFactory.INFO, "a paired display"));
        cases.add(stress(OeplCardFactory.INFO, "everything it can report"));

        // ---------------------------------------------------------- our own designs
        cases.add(one(OeplCardFactory.BIG, "wind=beaufort"));
        cases.add(one(OeplCardFactory.BIG, "wind=m/s"));

        for (final int days : new int[]{3, 4, 5, 6, 7}) {
            cases.add(one(OeplCardFactory.RANGE, "days=" + days));
        }

        cases.add(one(OeplCardFactory.SUN, "by day"));
        cases.add(one(OeplCardFactory.SUN, "by night"));

        cases.add(one(OeplCardFactory.TILES, "wind=beaufort"));
        cases.add(one(OeplCardFactory.TILES, "wind=m/s"));

        for (final int entries : CALENDAR_EVENTS) {
            cases.add(one(OeplCardFactory.BOARD, "entries=" + entries + ", caption=short"));
            cases.add(stress(OeplCardFactory.BOARD, "entries=" + entries + ", caption=long"));
        }

        cases.add(one(OeplCardFactory.POSTER, "one line"));
        cases.add(one(OeplCardFactory.POSTER, "three lines"));
        cases.add(one(OeplCardFactory.POSTER, "five lines"));
        cases.add(stress(OeplCardFactory.POSTER, "three long lines"));

        cases.add(one(OeplCardFactory.GRID, "weather only, name=short"));
        cases.add(one(OeplCardFactory.GRID, "everything, name=short"));
        cases.add(one(OeplCardFactory.GRID, "the house only"));
        cases.add(stress(OeplCardFactory.GRID, "everything, name=long"));

        cases.add(one(OeplCardFactory.HERO, "no agenda"));
        cases.add(one(OeplCardFactory.HERO, "agenda=2"));
        cases.add(one(OeplCardFactory.HERO, "agenda=4"));
        cases.add(one(OeplCardFactory.HERO, "agenda=8"));
        cases.add(one(OeplCardFactory.HERO, "weather only, agenda=4"));
        cases.add(stress(OeplCardFactory.HERO, "everything, agenda=8, long labels"));

        cases.add(one(OeplCardFactory.LIST, "everything"));
        cases.add(one(OeplCardFactory.LIST, "weather only"));
        cases.add(stress(OeplCardFactory.LIST, "everything, long labels"));

        for (final int hours : FORECAST_HOURS) {
            cases.add(one(OeplCardFactory.GRAPH, "hours=" + hours + ", rain"));
            cases.add(one(OeplCardFactory.GRAPH, "hours=" + hours + ", dry"));
        }

        cases.add(one(OeplCardFactory.MONTH, "caption=short"));
        cases.add(one(OeplCardFactory.MONTH, "an empty month"));
        cases.add(one(OeplCardFactory.MONTH, "a day with four appointments on it"));
        cases.add(stress(OeplCardFactory.MONTH, "caption=long"));

        for (final int days : new int[]{3, 5, 7}) {
            cases.add(one(OeplCardFactory.WALL, "days=" + days + ", entries=4, forecast=5"));
        }
        cases.add(one(OeplCardFactory.WALL, "days=7, entries=8, forecast=7"));
        cases.add(one(OeplCardFactory.WALL, "days=7, entries=4, forecast=0"));
        cases.add(one(OeplCardFactory.WALL, "an empty diary"));
        cases.add(one(OeplCardFactory.WALL, "no weather at all"));
        cases.add(stress(OeplCardFactory.WALL, "days=7, entries=8, forecast=7, long"));

        cases.add(one(OeplCardFactory.QUAD, "one quarter"));
        cases.add(one(OeplCardFactory.QUAD, "two quarters"));
        cases.add(one(OeplCardFactory.QUAD, "three quarters"));
        cases.add(one(OeplCardFactory.QUAD, "four quarters"));
        cases.add(one(OeplCardFactory.QUAD, "four of the busiest"));
        cases.add(stress(OeplCardFactory.QUAD, "four quarters, long everything"));

        // ---------------------------------------------------------- the framed cards
        for (final int days : new int[]{0, 3, 5, 7}) {
            cases.add(one(CardContentFactory.WEATHER, "forecast=" + days));
        }
        cases.add(stress(CardContentFactory.WEATHER, "forecast=3, long place"));
        cases.add(one(CardContentFactory.DATE, "-"));
        cases.add(one(CardContentFactory.CLOCK, "-"));
        cases.add(one(CardContentFactory.MUSIC, "playing"));
        cases.add(one(CardContentFactory.MUSIC, "paused"));

        for (final int entries : CALENDAR_EVENTS) {
            cases.add(one(CardContentFactory.CALENDAR, "entries=" + entries));
        }
        cases.add(stress(CardContentFactory.CALENDAR, "entries=8, long titles"));

        for (final int count : NOTIFICATION_COUNT) {
            cases.add(one(CardContentFactory.NOTIFICATIONS, "count=" + count));
        }
        cases.add(stress(CardContentFactory.NOTIFICATIONS, "count=4, long notifications"));

        for (final int hours : FORECAST_HOURS) {
            cases.add(one(CardContentFactory.HOURLY, "hours=" + hours));
        }
        for (final int days : new int[]{3, 5, 7}) {
            cases.add(one(CardContentFactory.FORECAST, "days=" + days));
        }

        cases.add(one(CardContentFactory.TEXT, "one line"));
        cases.add(one(CardContentFactory.TEXT, "three lines"));
        cases.add(one(CardContentFactory.TEXT, "six lines"));
        cases.add(stress(CardContentFactory.TEXT, "long lines"));

        cases.add(one(CardContentFactory.STATUS, "one device"));
        cases.add(one(CardContentFactory.STATUS, "three devices"));
        cases.add(one(CardContentFactory.STATUS, "six devices"));
        cases.add(stress(CardContentFactory.STATUS, "three devices, long names"));

        cases.add(one(CardContentFactory.WEATHER_CALENDAR, "entries=4"));
        cases.add(one(CardContentFactory.WEATHER_CALENDAR, "entries=8"));
        cases.add(one(CardContentFactory.WEATHER_DATE, "-"));
        cases.add(one(CardContentFactory.DATE_CALENDAR, "entries=4"));
        cases.add(one(CardContentFactory.CLOCK_WEATHER, "-"));
        cases.add(one(CardContentFactory.WEATHER_NOTIFICATIONS, "count=4"));
        cases.add(one(CardContentFactory.CALENDAR_NOTIFICATIONS, "count=4"));
        cases.add(one(CardContentFactory.DASHBOARD, "-"));
        cases.add(stress(CardContentFactory.DASHBOARD, "long everything"));

        // the picture settings: the three dither modes against the three ways of fitting, and
        // the palettes of a two, three and four colour panel
        for (final ImageContent.Dither dither : ImageContent.Dither.values()) {
            for (final ImageContent.Fit fit : ImageContent.Fit.values()) {
                cases.add(one(CardContentFactory.IMAGE,
                        "dither=" + dither + ", fit=" + fit + ", colors=3"));
            }
        }
        for (final int colors : new int[]{2, 3, 4}) {
            cases.add(one(CardContentFactory.IMAGE,
                    "dither=DIFFUSION, fit=CONTAIN, colors=" + colors));
        }
        cases.add(one(CardContentFactory.IMAGE, "zoomed and panned"));
        cases.add(one(CardContentFactory.IMAGE_URL, "dither=DIFFUSION, fit=COVER, colors=3"));

        return cases;
    }

    /**
     * Every design in every quarter of the quartered panel.
     * <p>
     * A quarter is a panel of its own shape, and which quarter a card lands in decides where
     * its origin is - so a card that lays itself out from the panel rather than from the area
     * it was handed only shows it in the quarters it was not tried in. There are four of them
     * and a design for each, so this is the whole table rather than a sample of it.
     */
    public static List<Case> quadCases() {
        final List<Case> cases = new ArrayList<>();
        for (final String design : OeplDemo.IDS) {
            if (OeplCardFactory.QUAD.equals(design)) {
                continue; // a quarter of the panel holding quarters of itself is not offered
            }
            for (int quarter = 0; quarter < 4; quarter++) {
                cases.add(one(OeplCardFactory.QUAD,
                        "quarter " + (quarter + 1) + " = " + design));
            }
        }
        return cases;
    }

    /**
     * Every setting the picture card has: the three ways of reducing a photograph to two or
     * three colours, the three ways of fitting it to a panel, the palettes of a two, three and
     * four colour tag, and pictures whose own shape fights the panel's.
     */
    public static List<Case> pictureCases() {
        final List<Case> cases = new ArrayList<>();
        for (final ImageContent.Dither dither : ImageContent.Dither.values()) {
            for (final ImageContent.Fit fit : ImageContent.Fit.values()) {
                for (final int colors : new int[]{2, 3, 4}) {
                    cases.add(one(CardContentFactory.IMAGE, "dither=" + dither + ", fit=" + fit
                            + ", colors=" + colors));
                }
            }
        }
        for (final String shape : new String[]{"wide", "tall", "square", "tiny", "huge"}) {
            for (final ImageContent.Fit fit : ImageContent.Fit.values()) {
                cases.add(one(CardContentFactory.IMAGE, "a " + shape + " picture, fit=" + fit
                        + ", dither=DIFFUSION, colors=3"));
            }
        }
        cases.add(one(CardContentFactory.IMAGE, "zoomed and panned"));
        cases.add(one(CardContentFactory.IMAGE, "zoomed and panned the other way"));
        cases.add(one(CardContentFactory.IMAGE, "zoomed far in"));
        cases.add(one(CardContentFactory.IMAGE, "no picture at all"));
        return cases;
    }

    private static Case one(final String design, final String settings) {
        return new Case(design, settings, false);
    }

    private static Case stress(final String design, final String settings) {
        return new Case(design, settings, true);
    }

    // ------------------------------------------------------------------ building the cards

    private static CardContent build(final Case c) {
        final String s = c.settings;
        switch (c.design) {
            case OeplCardFactory.WEATHER:
                return new OeplWeatherCard(OeplDemo.weather(), !s.contains("m/s"));
            case OeplCardFactory.FORECAST:
                // fewer than three days is not a forecast, and the factory says so too
                return new OeplForecastCard(OeplDemo.weather(), Math.max(3, number(s, "days=")),
                        !s.contains("m/s"));
            case OeplCardFactory.RAIN:
                return new OeplRainCard(OeplDemo.weather(), number(s, "hours="));
            case OeplCardFactory.DATE:
                return s.contains("no weather") ? new OeplDateCard(null, false)
                        : new OeplDateCard(OeplDemo.weather(), s.contains("sun=on"));
            case OeplCardFactory.CLOCK:
                return new OeplClockCard();
            case OeplCardFactory.CALENDAR:
                if (s.contains("all day")) {
                    return new OeplCalendarListCard(holiday(), 4, "Kalender");
                }
                return new OeplCalendarListCard(OeplDemo.events(), number(s, "entries="),
                        caption(s));
            case OeplCardFactory.CALENDAR_WEEK:
                if (s.contains("nothing in the diary")) {
                    return new OeplCalendarWeekCard(Collections.emptyList(), 7);
                }
                if (s.contains("holiday")) {
                    return new OeplCalendarWeekCard(holiday(), 3);
                }
                if (s.contains("six in the morning")) {
                    return new OeplCalendarWeekCard(earlyAndLate(), 3);
                }
                return new OeplCalendarWeekCard(OeplDemo.events(), number(s, "days="));
            case OeplCardFactory.RSS:
                return new OeplRssCard(OeplDemo.feed(),
                        s.contains("title=long")
                                ? "Nachrichten aus der Region und dem Umland" : "Nachrichten",
                        s.contains("summaries=on"));
            case OeplCardFactory.QR:
                return new OeplQrCard(s.contains("long link")
                        ? "https://codeberg.org/Freeyourgadget/Gadgetbridge/src/branch/master/"
                        + "app/src/main/java/nodomain/freeyourgadget/gadgetbridge"
                        : "https://gadgetbridge.org",
                        s.contains("no title") ? ""
                                : (s.contains("title=long")
                                ? "Gadgetbridge, Quelltext und Anleitung dazu" : "Gadgetbridge"));
            case OeplCardFactory.COUNT_DAYS:
            case OeplCardFactory.COUNT_HOURS:
                return new OeplCounterCard(number(s, ""), s.contains("threshold=")
                        ? number(s, "threshold=") : 1000000,
                        s.contains("no caption") ? null
                                : (s.contains("caption=long")
                                ? "Stunden ohne Zwischenfall in der Werkstatt" : "bis dahin"));
            case OeplCardFactory.INFO:
                return OeplDemo.card(OeplCardFactory.INFO
                        + (s.contains("everything") ? ":long" : ""));
            case OeplCardFactory.BIG:
                return new OeplBigTemperatureCard(OeplDemo.weather(), !s.contains("m/s"));
            case OeplCardFactory.RANGE:
                return new OeplRangeCard(OeplDemo.weather(), number(s, "days="));
            case OeplCardFactory.SUN:
                return new OeplSunCard(s.contains("night") ? night() : OeplDemo.weather());
            case OeplCardFactory.TILES:
                return new OeplTilesCard(OeplDemo.weather(), !s.contains("m/s"));
            case OeplCardFactory.BOARD:
                return new OeplBoardCard(OeplDemo.events(), number(s, "entries="), caption(s));
            case OeplCardFactory.POSTER:
                return new OeplPosterCard(lines(s));
            case OeplCardFactory.GRID:
                return new OeplGridCard(readings(s), s.contains("name=long")
                        ? "Kueche, Esszimmer und Flur oben" : "Kueche");
            case OeplCardFactory.HERO:
                return new OeplHeroCard(readings(s), s.contains("no agenda")
                        ? Collections.emptyList() : take(number(s, "agenda=")));
            case OeplCardFactory.LIST:
                return new OeplListCard(readings(s));
            case OeplCardFactory.GRAPH:
                return new OeplGraphCard(s.contains("dry") ? dry() : OeplDemo.weather(),
                        number(s, "hours="));
            case OeplCardFactory.MONTH:
                if (s.contains("empty month")) {
                    return new OeplMonthCard(Collections.emptyList(), "Kalender");
                }
                if (s.contains("four appointments")) {
                    return new OeplMonthCard(crowdedDay(), "Kalender");
                }
                return new OeplMonthCard(OeplDemo.events(), caption(s));
            case OeplCardFactory.WALL:
                return new OeplWallCard(
                        s.contains("no weather") ? null : OeplDemo.weather(),
                        s.contains("empty diary") ? Collections.emptyList() : OeplDemo.events(),
                        OeplDemo.beyondTheWeather(),
                        s.contains("days=") ? number(s, "days=") : 7,
                        s.contains("entries=") ? number(s, "entries=") : 4,
                        s.contains("forecast=") ? number(s, "forecast=") : 5,
                        true, "Kalender");
            case OeplCardFactory.QUAD:
                return new QuadContent(quarters(s));

            case CardContentFactory.WEATHER:
                return new WeatherContent(OeplDemo.spec(), number(s, "forecast="));
            case CardContentFactory.DATE:
                return new DateContent();
            case CardContentFactory.CLOCK:
                return new ClockContent();
            case CardContentFactory.MUSIC:
                return new MusicContent(OeplDemo.music(), s.contains("playing"), "Now playing");
            case CardContentFactory.CALENDAR:
                return new CalendarContent(OeplDemo.events(), number(s, "entries="));
            case CardContentFactory.NOTIFICATIONS:
                return new NotificationsContent(OeplDemo.notes(s.contains("long")),
                        number(s, "count="), number(s, "count=") + " notifications");
            case CardContentFactory.HOURLY:
                return new HourlyWeatherContent(OeplDemo.spec(), number(s, "hours="), "Curve");
            case CardContentFactory.FORECAST:
                return new ForecastContent(OeplDemo.spec(), number(s, "days="), "Forecast");
            case CardContentFactory.TEXT:
                return new TextContent("Einkaufen", lines(s));
            case CardContentFactory.STATUS:
                return new StatusContent(devices(s));
            case CardContentFactory.WEATHER_CALENDAR:
                return new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                        new CalendarContent(OeplDemo.events(), number(s, "entries=")), 0.55f);
            case CardContentFactory.WEATHER_DATE:
                return new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                        new DateContent(), 0.6f);
            case CardContentFactory.DATE_CALENDAR:
                return new SplitContent(new DateContent(),
                        new CalendarContent(OeplDemo.events(), number(s, "entries=")), 0.4f);
            case CardContentFactory.CLOCK_WEATHER:
                return new SplitContent(new ClockContent(),
                        new WeatherContent(OeplDemo.spec(), 3), 0.45f);
            case CardContentFactory.WEATHER_NOTIFICATIONS:
                return new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                        new NotificationsContent(OeplDemo.notes(false), number(s, "count="),
                                "4 notifications"), 0.5f);
            case CardContentFactory.CALENDAR_NOTIFICATIONS:
                return new SplitContent(new CalendarContent(OeplDemo.events(), 4),
                        new NotificationsContent(OeplDemo.notes(false), number(s, "count="),
                                "4 notifications"), 0.5f);
            case CardContentFactory.DASHBOARD:
                return new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                        new SplitContent(new CalendarContent(OeplDemo.events(), 4),
                                new NotificationsContent(OeplDemo.notes(false), 3,
                                        "3 notifications"), 0.5f), 0.45f);
            case CardContentFactory.IMAGE:
            case CardContentFactory.IMAGE_URL:
                if (s.contains("no picture")) {
                    return new ImageContent(null, ImageContent.Dither.DIFFUSION,
                            ImageContent.Fit.CONTAIN, OEPLImageCodec.palette(3, false));
                }
                if (s.contains("zoomed far in")) {
                    return new ImageContent(OeplDemo.picture(), ImageContent.Dither.ORDERED,
                            ImageContent.Fit.COVER, OEPLImageCodec.palette(3, false), 6f,
                            -0.45f, 0.45f);
                }
                if (s.contains("the other way")) {
                    return new ImageContent(OeplDemo.picture(), ImageContent.Dither.NONE,
                            ImageContent.Fit.CONTAIN, OEPLImageCodec.palette(2, false), 1.6f,
                            -0.3f, 0.25f);
                }
                if (s.contains("zoomed")) {
                    return new ImageContent(OeplDemo.picture(), ImageContent.Dither.DIFFUSION,
                            ImageContent.Fit.COVER, OEPLImageCodec.palette(3, false), 2.4f,
                            0.3f, -0.2f);
                }
                return new ImageContent(pictureFor(s), dither(s), fit(s),
                        OEPLImageCodec.palette(number(s, "colors="), false));
            default:
                throw new IllegalArgumentException("no case for " + c);
        }
    }

    // ------------------------------------------------------------------ the data behind them

    /** The first number after a marker, or the first number in the text when there is none. */
    private static int number(final String settings, final String marker) {
        final int at = marker.isEmpty() ? 0 : settings.indexOf(marker);
        if (at < 0) {
            throw new IllegalArgumentException("no " + marker + " in " + settings);
        }
        int i = at + marker.length();
        while (i < settings.length() && !Character.isDigit(settings.charAt(i))) {
            i++;
        }
        int end = i;
        while (end < settings.length() && Character.isDigit(settings.charAt(end))) {
            end++;
        }
        return Integer.parseInt(settings.substring(i, end));
    }

    private static String caption(final String settings) {
        if (settings.contains("caption=empty")) {
            return "";
        }
        return settings.contains("caption=long") ? LONG_CAPTION : "Kalender";
    }

    private static List<String> lines(final String settings) {
        if (settings.contains("one line")) {
            return Collections.singletonList("Zimmer 4");
        }
        if (settings.contains("long lines")) {
            return Arrays.asList("Werkstatt und Ersatzteillager im Hinterhof",
                    "!Bitte an der Seitentuer klingeln und warten",
                    "Montag bis Freitag von 8 bis 17 Uhr");
        }
        if (settings.contains("three long lines")) {
            return Arrays.asList("Werkstatt und Ersatzteillager im Hinterhof",
                    "!Bitte an der Seitentuer klingeln", "Mo - Fr 8 bis 17 Uhr");
        }
        if (settings.contains("five lines")) {
            return Arrays.asList("Werkstatt", "!Bitte klingeln", "Mo - Fr 8 bis 17 Uhr",
                    "Samstag geschlossen", "Telefon 0431 12345");
        }
        if (settings.contains("six lines")) {
            return Arrays.asList("Milch", "Brot", "!Paket abholen", "Kaffee", "Wasser",
                    "Hundefutter");
        }
        return Arrays.asList("Werkstatt", "!Bitte klingeln", "Mo - Fr 8 bis 17 Uhr");
    }

    private static List<StatusContent.Entry> devices(final String settings) {
        if (settings.contains("one device")) {
            return Collections.singletonList(new StatusContent.Entry("Telefon", 64));
        }
        if (settings.contains("six devices")) {
            return Arrays.asList(new StatusContent.Entry("Telefon", 64),
                    new StatusContent.Entry("Pixel Watch 2", 72),
                    new StatusContent.Entry("Mi Band 7", 15),
                    new StatusContent.Entry("Buds Pro", 91),
                    new StatusContent.Entry("Tastatur im Buero", 8),
                    new StatusContent.Entry("Fahrradcomputer", 55));
        }
        final String first = settings.contains("long names")
                ? "Pixel Watch 2 von der Kollegin aus dem Buero" : "Pixel Watch";
        return Arrays.asList(new StatusContent.Entry(first, 72),
                new StatusContent.Entry("Mi Band 7", 15),
                new StatusContent.Entry("Buds", 91));
    }

    /** The reading groups the dashboard settings can be set to. */
    private static List<OeplReading> readings(final String settings) {
        if (settings.contains("weather only")) {
            return OeplDemo.readings(false, false);
        }
        if (settings.contains("the house only")) {
            return OeplDemo.readings(true, false);
        }
        return OeplDemo.readings(true, true);
    }

    private static List<CalendarEvent> take(final int count) {
        final List<CalendarEvent> events = new ArrayList<>(OeplDemo.events());
        while (events.size() < count) {
            events.addAll(OeplDemo.events());
        }
        return events.subList(0, count);
    }

    /** A holiday over three days and nothing else, as the calendar provider hands one over. */
    private static List<CalendarEvent> holiday() {
        final java.util.Calendar utc = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("UTC"));
        final java.util.Calendar local = java.util.Calendar.getInstance();
        utc.clear();
        utc.set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH),
                local.get(java.util.Calendar.DAY_OF_MONTH), 0, 0, 0);
        final long begin = utc.getTimeInMillis();
        utc.add(java.util.Calendar.DAY_OF_MONTH, 3);
        return Collections.singletonList(new CalendarEvent(begin, utc.getTimeInMillis(), 77, 77,
                "Herbstferien", "", "", "cal", "acc", 0, 0, true, "me", "LOCAL", "1", null, 0, 0));
    }

    /** A day that begins before six and ends after ten, which widens the week grid's hours. */
    private static List<CalendarEvent> earlyAndLate() {
        final long midnight = OeplDemo.midnight() * 1000L;
        final List<CalendarEvent> events = new ArrayList<>();
        events.add(new CalendarEvent(midnight + 5 * 3600_000L, midnight + 6 * 3600_000L, 11, 11,
                "Zug nach Hamburg", "", "", "cal", "acc", 0, 0, false, "me", "LOCAL", "1", null,
                0, 0));
        events.add(new CalendarEvent(midnight + 21 * 3600_000L, midnight + 23 * 3600_000L, 12, 12,
                "Chorprobe", "", "", "cal", "acc", 0, 0, false, "me", "LOCAL", "1", null, 0, 0));
        return events;
    }

    /** Four appointments on one day, which is more dots than a month cell can hold. */
    private static List<CalendarEvent> crowdedDay() {
        final long midnight = OeplDemo.midnight() * 1000L;
        final List<CalendarEvent> events = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final long begin = midnight + (8 + i * 2) * 3600_000L;
            events.add(new CalendarEvent(begin, begin + 3600_000L, 20 + i, 20 + i,
                    "Termin " + (i + 1), "", "", "cal", "acc", 0, 0, false, "me", "LOCAL", "1",
                    null, 0, 0));
        }
        return events;
    }

    private static OeplWeather night() {
        final OeplWeather w = OeplDemo.weather();
        final long now = System.currentTimeMillis() / 1000L;
        w.sunrise = now - 10 * 3600L;
        w.sunset = now - 2 * 3600L;
        w.current.day = false;
        return w;
    }

    private static OeplWeather dry() {
        final OeplWeather w = OeplDemo.weather();
        for (final OeplWeather.Hour hour : w.hours) {
            hour.precipitation = 0f;
        }
        return w;
    }

    private static List<CardContent> quarters(final String settings) {
        final List<CardContent> parts = new ArrayList<>();
        if (settings.startsWith("quarter ")) {
            // the design under test in one quarter and a steady filler in the other three, so
            // that whatever goes wrong belongs to the design rather than to its neighbours
            final int quarter = settings.charAt(8) - '1';
            final String design = settings.substring(settings.indexOf('=') + 2);
            for (int i = 0; i < 4; i++) {
                parts.add(i == quarter ? OeplDemo.card(design) : new ClockContent());
            }
            return parts;
        }
        if (settings.contains("busiest")) {
            parts.add(new OeplCalendarWeekCard(OeplDemo.events(), 3));
            parts.add(new OeplMonthCard(OeplDemo.events(), "Kalender"));
            parts.add(new OeplGridCard(OeplDemo.readings(true, true), "Kueche"));
            parts.add(new OeplBoardCard(OeplDemo.events(), 6, "Termine"));
            return parts;
        }
        parts.add(new WeatherContent(OeplDemo.spec(), 3));
        if (!settings.contains("one quarter")) {
            parts.add(new CalendarContent(OeplDemo.events(), 4));
        }
        if (settings.contains("three quarters") || settings.contains("four quarters")) {
            parts.add(new OeplRssCard(OeplDemo.feed(), "Nachrichten", false));
        }
        if (settings.contains("four quarters")) {
            parts.add(new OeplListCard(OeplDemo.readings(false, true)));
        }
        return parts;
    }

    /** The picture a case asks for: wide by default, and any shape it names. */
    private static android.graphics.Bitmap pictureFor(final String settings) {
        if (settings.contains("a tall picture")) {
            return OeplDemo.picture(160, 240);
        }
        if (settings.contains("a square picture")) {
            return OeplDemo.picture(200, 200);
        }
        if (settings.contains("a tiny picture")) {
            return OeplDemo.picture(24, 16);
        }
        if (settings.contains("a huge picture")) {
            return OeplDemo.picture(1600, 1200);
        }
        return OeplDemo.picture();
    }

    // ------------------------------------------------------------------ the rest of the cards

    /**
     * Every remaining design against the whole of its own input, not merely against the values
     * its settings offer.
     * <p>
     * A setting is only half of what a card is handed. The other half is the data: a weather
     * card laid out around "13°" is a different card when the reading is "-18°" and a different
     * one again at "107°F"; an appointment list with four entries says nothing about one with
     * twenty; a dashboard tile that holds "78 %" has to hold "weit offen seit 12:40" as well.
     * This is that half - for each design, the shapes its data really comes in, including the
     * empty one and the overfull one.
     */
    public static List<Case> deepCases() {
        final List<Case> cases = new ArrayList<>();

        // ---------------------------------------------------------- what the weather can be
        for (final String sky : SKIES) {
            cases.add(made(OeplCardFactory.WEATHER, sky,
                    () -> new OeplWeatherCard(sky(sky), true)));
            cases.add(made(OeplCardFactory.BIG, sky,
                    () -> new OeplBigTemperatureCard(sky(sky), true)));
            cases.add(made(OeplCardFactory.TILES, sky,
                    () -> new OeplTilesCard(sky(sky), false)));
            cases.add(made(OeplCardFactory.DATE, sky, () -> new OeplDateCard(sky(sky), true)));
        }
        for (final String sky : new String[]{"an ordinary day", "bitter cold", "a heatwave",
                "a flat week", "bone dry", "soaked through", "nothing but the current reading"}) {
            cases.add(made(OeplCardFactory.RANGE, sky, () -> new OeplRangeCard(sky(sky), 7)));
            cases.add(made(OeplCardFactory.FORECAST, sky,
                    () -> new OeplForecastCard(sky(sky), 7, true)));
        }
        for (final String sky : new String[]{"an ordinary day", "a heatwave", "bone dry",
                "soaked through", "nothing but the current reading", "a flat week"}) {
            cases.add(made(OeplCardFactory.GRAPH, sky, () -> new OeplGraphCard(sky(sky), 24)));
            cases.add(made(OeplCardFactory.RAIN, sky, () -> new OeplRainCard(sky(sky), 24)));
        }
        for (final String sky : new String[]{"an ordinary day", "at night", "a storm",
                "nothing but the current reading"}) {
            cases.add(made(OeplCardFactory.SUN, sky, () -> new OeplSunCard(sky(sky))));
        }

        // ---------------------------------------------------------- what a diary can hold
        for (final String diary : DIARIES) {
            cases.add(made(OeplCardFactory.CALENDAR, diary,
                    () -> new OeplCalendarListCard(diary(diary), 8, "Kalender")));
            cases.add(made(OeplCardFactory.BOARD, diary,
                    () -> new OeplBoardCard(diary(diary), 8, "Termine")));
            cases.add(made(CardContentFactory.CALENDAR, diary,
                    () -> new CalendarContent(diary(diary), 8)));
        }
        for (final String diary : DIARIES) {
            cases.add(made(OeplCardFactory.CALENDAR_WEEK, diary + ", days=3",
                    () -> new OeplCalendarWeekCard(diary(diary), 3)));
            cases.add(made(OeplCardFactory.CALENDAR_WEEK, diary + ", days=7",
                    () -> new OeplCalendarWeekCard(diary(diary), 7)));
            cases.add(made(OeplCardFactory.MONTH, diary,
                    () -> new OeplMonthCard(diary(diary), "Kalender")));
        }

        // ---------------------------------------------------------- what a dashboard can hold
        for (final int count : new int[]{1, 2, 3, 4, 6, 8, 12, 16}) {
            cases.add(made(OeplCardFactory.GRID, count + " values",
                    () -> new OeplGridCard(values(count, "plain"), "Kueche")));
            cases.add(made(OeplCardFactory.LIST, count + " values",
                    () -> new OeplListCard(values(count, "plain"))));
            cases.add(made(OeplCardFactory.HERO, count + " values",
                    () -> new OeplHeroCard(values(count, "plain"), diary("five appointments"))));
        }
        for (final String kind : new String[]{"every value accented", "every value a percentage",
                "no percentages at all", "values worded rather than counted", "one word each"}) {
            cases.add(made(OeplCardFactory.GRID, kind,
                    () -> new OeplGridCard(values(8, kind), "Kueche")));
            cases.add(made(OeplCardFactory.LIST, kind, () -> new OeplListCard(values(8, kind))));
            cases.add(made(OeplCardFactory.HERO, kind,
                    () -> new OeplHeroCard(values(8, kind), diary("two appointments"))));
        }

        // ---------------------------------------------------------- the text and feed cards
        for (final String qr : new String[]{"a short link", "a link of a hundred characters",
                "a wifi credential", "three hundred characters of text", "umlauts and sharp s",
                "a single word"}) {
            cases.add(made(OeplCardFactory.QR, qr + ", with a title",
                    () -> new OeplQrCard(code(qr), "Gadgetbridge")));
            cases.add(made(OeplCardFactory.QR, qr + ", no title",
                    () -> new OeplQrCard(code(qr), "")));
        }
        for (final long count : new long[]{0L, 1L, 9L, 42L, 99L, 365L, 1000L, 99999L, 123456L,
                9999999L, -5L}) {
            cases.add(made(OeplCardFactory.COUNT_DAYS, count + " left",
                    () -> new OeplCounterCard(count, 30L, "bis dahin")));
        }
        cases.add(made(OeplCardFactory.COUNT_HOURS, "no caption at all",
                () -> new OeplCounterCard(48L, 1000000L, null)));
        cases.add(made(OeplCardFactory.COUNT_HOURS, "a caption of its own line",
                () -> new OeplCounterCard(7L, 10L, "Stunden ohne Zwischenfall in der Werkstatt")));

        for (int lines = 1; lines <= 6; lines++) {
            final int n = lines;
            cases.add(made(OeplCardFactory.POSTER, n + " lines", () -> new OeplPosterCard(
                    poster(n, false))));
        }
        cases.add(made(OeplCardFactory.POSTER, "every line accented",
                () -> new OeplPosterCard(poster(4, true))));
        cases.add(made(OeplCardFactory.POSTER, "one word no panel holds",
                () -> new OeplPosterCard(Collections.singletonList(
                        "Donaudampfschifffahrtsgesellschaftskapitaen"))));

        for (final int items : new int[]{1, 2, 5, 10}) {
            cases.add(made(OeplCardFactory.RSS, items + " headlines, with summaries",
                    () -> new OeplRssCard(feed(items, true), "Nachrichten", true)));
            cases.add(made(OeplCardFactory.RSS, items + " headlines, without",
                    () -> new OeplRssCard(feed(items, true), "Nachrichten", false)));
        }
        cases.add(made(OeplCardFactory.RSS, "headlines without a summary between them",
                () -> new OeplRssCard(feed(4, false), "Nachrichten", true)));
        cases.add(made(OeplCardFactory.RSS, "one headline the length of a paragraph",
                () -> new OeplRssCard(feed(1, true), "Nachrichten", true), true));

        for (final int rows : new int[]{1, 2, 4, 6, 8}) {
            cases.add(made(OeplCardFactory.INFO, rows + " rows",
                    () -> new OeplInfoCard("Kueche", info(rows, false))));
        }
        cases.add(made(OeplCardFactory.INFO, "rows with everything spelled out",
                () -> new OeplInfoCard("Kueche, Esszimmer und Flur oben", info(7, true))));

        for (final int count : new int[]{1, 2, 3, 5, 8}) {
            cases.add(made(CardContentFactory.NOTIFICATIONS, count + " notifications",
                    () -> new NotificationsContent(OeplDemo.notes(false), count,
                            count + " notifications")));
        }
        cases.add(made(CardContentFactory.NOTIFICATIONS, "notifications with long names",
                () -> new NotificationsContent(OeplDemo.notes(true), 4, "4 notifications")));

        cases.add(made(CardContentFactory.MUSIC, "playing",
                () -> new MusicContent(OeplDemo.music(), true, "Now playing")));
        cases.add(made(CardContentFactory.MUSIC, "paused",
                () -> new MusicContent(OeplDemo.music(), false, "Now playing")));
        cases.add(made(CardContentFactory.MUSIC, "a title no panel holds",
                () -> new MusicContent(OeplDemo.music(), true, "Now playing"), true));

        for (final int devices : new int[]{1, 2, 4, 6, 8}) {
            cases.add(made(CardContentFactory.STATUS, devices + " devices",
                    () -> new StatusContent(batteries(devices, "mixed"))));
        }
        cases.add(made(CardContentFactory.STATUS, "every battery full",
                () -> new StatusContent(batteries(4, "full"))));
        cases.add(made(CardContentFactory.STATUS, "every battery empty",
                () -> new StatusContent(batteries(4, "empty"))));
        cases.add(made(CardContentFactory.STATUS, "a device without a reading",
                () -> new StatusContent(batteries(3, "unknown"))));

        for (int lines = 1; lines <= 6; lines++) {
            final int n = lines;
            cases.add(made(CardContentFactory.TEXT, n + " lines",
                    () -> new TextContent("Einkaufen", poster(n, false))));
        }
        cases.add(made(CardContentFactory.TEXT, "no heading at all",
                () -> new TextContent(null, poster(4, false))));

        // ---------------------------------------------------------- the two part cards
        // half a panel is the hardest room a content ever gets, so the halves are given the
        // same extremes as the whole cards above
        for (final String diary : new String[]{"an empty diary", "twenty appointments",
                "titles that fill a line", "a week of holiday"}) {
            cases.add(made(CardContentFactory.WEATHER_CALENDAR, diary,
                    () -> new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                            new CalendarContent(diary(diary), 8), 0.55f)));
            cases.add(made(CardContentFactory.DATE_CALENDAR, diary,
                    () -> new SplitContent(new DateContent(),
                            new CalendarContent(diary(diary), 8), 0.4f)));
            cases.add(made(CardContentFactory.CALENDAR_NOTIFICATIONS, diary,
                    () -> new SplitContent(new CalendarContent(diary(diary), 8),
                            new NotificationsContent(OeplDemo.notes(true), 4,
                                    "4 notifications"), 0.5f)));
            cases.add(made(CardContentFactory.DASHBOARD, diary,
                    () -> new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                            new SplitContent(new CalendarContent(diary(diary), 4),
                                    new NotificationsContent(OeplDemo.notes(true), 3,
                                            "3 notifications"), 0.5f), 0.45f)));
        }
        cases.add(made(CardContentFactory.WEATHER_NOTIFICATIONS, "eight notifications",
                () -> new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                        new NotificationsContent(OeplDemo.notes(true), 8, "8 notifications"),
                        0.5f)));
        cases.add(made(CardContentFactory.CLOCK_WEATHER, "a long place name",
                () -> new SplitContent(new ClockContent(),
                        new WeatherContent(OeplDemo.spec(), 3), 0.45f), true));
        cases.add(made(CardContentFactory.WEATHER_DATE, "a long place name",
                () -> new SplitContent(new WeatherContent(OeplDemo.spec(), 3),
                        new DateContent(), 0.6f), true));

        return cases;
    }

    /** The shapes the weather comes in, from a winter night to a heatwave. */
    private static final String[] SKIES = {"an ordinary day", "bitter cold", "a heatwave",
            "the freezing point", "a storm", "dead calm", "at night", "a flat week", "bone dry",
            "soaked through", "nothing but the current reading", "a long place name"};

    /** The shapes a diary comes in, from an empty one to one nobody could keep up with. */
    private static final String[] DIARIES = {"an empty diary", "one appointment",
            "two appointments", "five appointments", "ten appointments", "twenty appointments",
            "three at the same hour", "one all day entry", "a week of holiday",
            "one that runs past midnight", "titles that fill a line", "early and late"};

    private static Case made(final String design, final String settings,
                             final Supplier<CardContent> supplier) {
        return new Case(design, settings, false, supplier);
    }

    private static Case made(final String design, final String settings,
                             final Supplier<CardContent> supplier, final boolean stress) {
        return new Case(design, settings, stress, supplier);
    }

    // ------------------------------------------------------------------ the data itself

    /** The demo weather, bent into one of the shapes a real one comes in. */
    static OeplWeather sky(final String variant) {
        final OeplWeather w = OeplDemo.weather();
        switch (variant) {
            case "bitter cold":
                w.current.temperature = -18.5f;
                for (final OeplWeather.Day d : w.days) {
                    d.maxTemperature -= 28f;
                    d.minTemperature -= 28f;
                }
                for (final OeplWeather.Hour h : w.hours) {
                    h.temperature -= 28f;
                }
                break;
            case "a heatwave":
                // three digits once the panel is set to Fahrenheit, which is where a column
                // sized around two of them runs into its neighbour
                w.current.temperature = 41.7f;
                for (final OeplWeather.Day d : w.days) {
                    d.maxTemperature += 27f;
                    d.minTemperature += 27f;
                }
                for (final OeplWeather.Hour h : w.hours) {
                    h.temperature += 27f;
                }
                break;
            case "the freezing point":
                w.current.temperature = 0f;
                for (final OeplWeather.Day d : w.days) {
                    d.maxTemperature = 0f;
                    d.minTemperature = -0.4f;
                }
                break;
            case "a storm":
                w.current.windSpeed = 33.5f;
                w.current.wmoCode = 95;
                for (final OeplWeather.Day d : w.days) {
                    d.windSpeed = 30f;
                }
                break;
            case "dead calm":
                w.current.windSpeed = 0f;
                for (final OeplWeather.Day d : w.days) {
                    d.windSpeed = 0f;
                }
                break;
            case "at night":
                w.current.day = false;
                w.current.wmoCode = 1;
                w.sunrise = w.sunrise - 20 * 3600L;
                w.sunset = w.sunset - 20 * 3600L;
                break;
            case "a flat week":
                for (final OeplWeather.Day d : w.days) {
                    d.maxTemperature = 14f;
                    d.minTemperature = 13f;
                }
                for (final OeplWeather.Hour h : w.hours) {
                    h.temperature = 13.5f;
                }
                break;
            case "bone dry":
                for (final OeplWeather.Day d : w.days) {
                    d.precipitation = 0f;
                }
                for (final OeplWeather.Hour h : w.hours) {
                    h.precipitation = 0f;
                    h.wmoCode = 0;
                }
                break;
            case "soaked through":
                for (final OeplWeather.Day d : w.days) {
                    d.precipitation = 38.4f;
                    d.wmoCode = 95;
                }
                for (final OeplWeather.Hour h : w.hours) {
                    h.precipitation = 9.2f;
                    h.wmoCode = 63;
                }
                break;
            case "nothing but the current reading":
                w.days.clear();
                w.hours.clear();
                w.current.humidity = -1;
                w.sunrise = 0L;
                w.sunset = 0L;
                w.moonPhase = -1;
                break;
            case "a long place name":
                w.location = OeplDemo.LONG_PLACE;
                break;
            default:
                break;
        }
        return w;
    }

    /** A diary in one of the shapes a real one comes in. */
    static List<CalendarEvent> diary(final String variant) {
        final long midnight = OeplDemo.midnight() * 1000L;
        final List<CalendarEvent> events = new ArrayList<>();
        switch (variant) {
            case "an empty diary":
                return events;
            case "three at the same hour":
                for (int i = 0; i < 3; i++) {
                    events.add(timed(midnight + 10 * 3600_000L,
                            midnight + 11 * 3600_000L + i * 600_000L, 60 + i,
                            new String[]{"Standup", "Telefonat", "Lieferung"}[i]));
                }
                return events;
            case "one all day entry":
                events.add(wholeDay(0, 1, 61, "Flohmarkt"));
                events.add(timed(midnight + 14 * 3600_000L, midnight + 15 * 3600_000L, 62,
                        "Zahnarzt"));
                return events;
            case "a week of holiday":
                events.add(wholeDay(0, 7, 63, "Herbstferien"));
                return events;
            case "one that runs past midnight":
                events.add(timed(midnight + 22 * 3600_000L, midnight + 25 * 3600_000L, 64,
                        "Nachtschicht"));
                events.add(timed(midnight + 20 * 3600_000L, midnight + 24 * 3600_000L, 65,
                        "Chorprobe"));
                return events;
            case "titles that fill a line":
                events.add(timed(midnight + 9 * 3600_000L, midnight + 10 * 3600_000L, 66,
                        "Jahreshauptversammlung des Kleingartenvereins Nord"));
                events.add(timed(midnight + 13 * 3600_000L, midnight + 14 * 3600_000L, 67,
                        "Zahnarzt Dr. Mueller-Luedenscheidt, Praxis am Hauptbahnhof"));
                return events;
            case "early and late":
                events.add(timed(midnight + 5 * 3600_000L, midnight + 6 * 3600_000L, 68,
                        "Zug nach Hamburg"));
                events.add(timed(midnight + 21 * 3600_000L, midnight + 23 * 3600_000L, 69,
                        "Chorprobe"));
                return events;
            default:
                break;
        }
        final int count = variant.startsWith("one ") ? 1
                : variant.startsWith("two") ? 2
                : variant.startsWith("five") ? 5
                : variant.startsWith("ten") ? 10 : 20;
        final String[] titles = {"Standup", "Zahnarzt", "Werkstatt", "Elternabend", "Sport",
                "Einkaufen", "Probe", "Besprechung", "Arzt", "Kino"};
        for (int i = 0; i < count; i++) {
            final long begin = midnight + (i / 4) * 86400_000L + (8 + (i % 4) * 3) * 3600_000L;
            events.add(timed(begin, begin + 3600_000L, 70 + i, titles[i % titles.length]));
        }
        return events;
    }

    private static CalendarEvent timed(final long begin, final long end, final int id,
                                       final String title) {
        return new CalendarEvent(begin, end, id, id, title, "", "", "cal", "acc", 0, 0, false,
                "me", "LOCAL", "1", null, 0, 0);
    }

    /** An all day entry as the provider hands one over: midnights in UTC. */
    private static CalendarEvent wholeDay(final int from, final int length, final int id,
                                          final String title) {
        final Calendar local = Calendar.getInstance();
        local.add(Calendar.DAY_OF_MONTH, from);
        final Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        final long begin = utc.getTimeInMillis();
        utc.add(Calendar.DAY_OF_MONTH, length);
        return new CalendarEvent(begin, utc.getTimeInMillis(), id, id, title, "", "", "cal",
                "acc", 0, 0, true, "me", "LOCAL", "1", null, 0, 0);
    }

    /** Readings in the shapes a dashboard really collects them in. */
    static List<OeplReading> values(final int count, final String kind) {
        final String[] labels = {"Wohnzimmer", "Draussen", "Haustuer", "Waschmaschine",
                "Telefon", "Uhr", "Heizung", "Keller", "Dachboden", "Garage", "Buero", "Bad",
                "Kueche", "Flur", "Garten", "Schuppen"};
        final String[] worded = {"weit offen seit 12:40", "laeuft noch 48 Minuten",
                "nicht erreichbar", "zuletzt gesehen gestern 22:10"};
        final List<OeplReading> readings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String section = i < count / 2 ? "Haus" : "Draussen";
            final String label = "one word each".equals(kind) ? labels[i % labels.length]
                    : labels[i % labels.length] + (i >= labels.length ? " " + i : "");
            final String value = "values worded rather than counted".equals(kind)
                    ? worded[i % worded.length] : (18 + i) + ",5 °C";
            OeplReading reading = new OeplReading(section, label, value);
            if (!"no percentages at all".equals(kind)
                    && ("every value a percentage".equals(kind) || i % 3 == 0)) {
                reading = reading.withPercent((i * 17) % 101);
            }
            reading = reading.withShape(i % 2 == 0 ? OeplGlyphs.Glyph.HOUSE
                    : OeplGlyphs.Glyph.BATTERY);
            readings.add(reading.accented("every value accented".equals(kind) || i % 4 == 0));
        }
        return readings;
    }

    /** The lines of a poster or a note, with the accent marker on some of them. */
    private static List<String> poster(final int lines, final boolean allAccented) {
        final String[] text = {"Werkstatt", "Bitte klingeln", "Mo - Fr 8 bis 17 Uhr",
                "Samstag geschlossen", "Telefon 0431 12345", "Paket bei Nr. 7 abgeben"};
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            out.add((allAccented || i == 1 ? "!" : "") + text[i % text.length]);
        }
        return out;
    }

    /** What goes into a QR code, from a short link to a paragraph. */
    private static String code(final String kind) {
        switch (kind) {
            case "a link of a hundred characters":
                return "https://codeberg.org/Freeyourgadget/Gadgetbridge/src/branch/master/app/"
                        + "src/main/java/nodomain";
            case "a wifi credential":
                return "WIFI:T:WPA;S:Gaestenetz Werkstatt;P:sehr-langes-passwort-2026;;";
            case "three hundred characters of text":
                final StringBuilder sb = new StringBuilder();
                while (sb.length() < 300) {
                    sb.append("Die Werkstatt ist von Montag bis Freitag geoeffnet. ");
                }
                return sb.toString();
            case "umlauts and sharp s":
                return "Grosse Strasse 7, Kueche, Buero, Masseinheit";
            case "a single word":
                return "Gadgetbridge";
            default:
                return "https://gadgetbridge.org";
        }
    }

    /** A feed of a given number of headlines, with or without summaries under them. */
    private static List<OeplRssClient.Item> feed(final int items, final boolean summaries) {
        final StringBuilder xml = new StringBuilder("<rss><channel>");
        for (int i = 0; i < items; i++) {
            xml.append("<item><title>");
            xml.append(items == 1
                    ? "Der Deich bei Brunsbuettel wird auf ganzer Laenge erhoeht, die "
                    + "Arbeiten dauern bis in den Herbst"
                    : "Schlagzeile " + (i + 1) + " aus der Region");
            xml.append("</title>");
            if (summaries) {
                xml.append("<description>Die Pegel sinken, die Sperrwerke bleiben vorerst "
                        + "geschlossen.</description>");
            }
            xml.append("</item>");
        }
        return OeplRssClient.parse(xml.append("</channel></rss>").toString());
    }

    /** The rows the display can report about itself. */
    private static List<OeplInfoCard.Row> info(final int rows, final boolean spelledOut) {
        final String[][] all = {{"Typ", spelledOut ? "Hanshow Stellar Pro M3 2.9 BWRY" : "HS M3"},
                {"Panel", spelledOut ? "384 x 184, 4 Farben, gedreht" : "384 x 184"},
                {"Firmware", spelledOut ? "124 (OpenEPaperLink)" : "124"},
                {"Akku", spelledOut ? "3,07 V (95%), gemessen 12:40" : "3,07 V"},
                {"Temperatur", spelledOut ? "23 °C am Panel" : "23 °C"},
                {"MAC", "FF:FC:4F:05:E4:11"},
                {"Telefon", spelledOut ? "64%, laedt nicht" : "64%"},
                {"Stand", spelledOut ? "26.10.2026 22:15:43" : "26.10. 22:15"}};
        final List<OeplInfoCard.Row> out = new ArrayList<>();
        for (int i = 0; i < Math.min(rows, all.length); i++) {
            out.add(new OeplInfoCard.Row(all[i][0], all[i][1]));
        }
        return out;
    }

    /** The batteries of a phone and whatever is paired with it. */
    private static List<StatusContent.Entry> batteries(final int count, final String kind) {
        final String[] names = {"Telefon", "Pixel Watch 2", "Mi Band 7", "Buds Pro",
                "Tastatur im Buero", "Fahrradcomputer", "Maus", "Kopfhoerer"};
        final List<StatusContent.Entry> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int percent = "full".equals(kind) ? 100
                    : "empty".equals(kind) ? 0
                    : "unknown".equals(kind) && i == 1 ? -1
                    : (17 + i * 23) % 101;
            out.add(new StatusContent.Entry(names[i % names.length], percent));
        }
        return out;
    }

    private static ImageContent.Dither dither(final String settings) {
        for (final ImageContent.Dither dither : ImageContent.Dither.values()) {
            if (settings.contains("dither=" + dither)) {
                return dither;
            }
        }
        return ImageContent.Dither.DIFFUSION;
    }

    private static ImageContent.Fit fit(final String settings) {
        for (final ImageContent.Fit fit : ImageContent.Fit.values()) {
            if (settings.contains("fit=" + fit)) {
                return fit;
            }
        }
        return ImageContent.Fit.CONTAIN;
    }
}
