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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
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
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplFonts;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplForecastCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGlyphs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGraphCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGridCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplHeroCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplIcons;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplListCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplReading;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplInfoCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplPosterCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplQrCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRainCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRangeCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplSunCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplTilesCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeather;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeatherCard;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * Demo content for the drawing tests: one card of every kind the settings offer, filled with
 * data chosen to put pressure on the layouts rather than to flatter them - a sub zero
 * temperature, a wind that deserves the accent colour, appointment titles that are too long for
 * any panel, a feed headline that has to wrap, a six figure counter.
 * <p>
 * Nothing here talks to the network, the calendar provider or the weather store, so a layout can
 * be looked at without a device anywhere near.
 */
public final class OeplDemo {

    /** Every content id the settings list offers, in the order it is offered. */
    public static final String[] IDS = {
            // the OpenEPaperLink style cards
            "oepl_4", "oepl_8", "oepl_16", "oepl_1", "oepl_29", "oepl_11", "oepl_11w",
            "oepl_9", "oepl_10", "oepl_2", "oepl_3", "oepl_21",
            // our own designs, which own the whole panel as well
            "panel_big", "panel_bars", "panel_sun", "panel_tiles", "panel_board",
            "panel_poster", "panel_grid", "panel_hero", "panel_list", "panel_graph",
            "panel_quad",
            // the cards with the Gadgetbridge frame around them
            "weather", "date", "calendar", "text", "status", "weather_calendar", "weather_date",
            "date_calendar", "clock", "notifications", "music", "hourly", "forecast",
            "clock_weather", "weather_notifications", "calendar_notifications", "dashboard",
            // the two picture cards
            "image", "image_url",
    };

    /** Variants that only differ in their data, to catch what the plain cards do not show. */
    public static final String[] VARIANTS = {
            "oepl_4:night", "oepl_8:ms", "oepl_1:plain", "oepl_9:summary", "oepl_3:big",
            "oepl_11:long", "text:long", "status:many", "notifications:long",
            "image:none", "image:ordered", "image:stretch",
            "panel_sun:night", "panel_poster:one", "panel_bars:week",
            "panel_grid:weather", "panel_hero:noagenda", "panel_list:many",
            "panel_graph:dry", "panel_quad:two", "panel_quad:three",
    };

    /**
     * Every design again, with everything in it as long and as awkward as it plausibly gets.
     * A layout that holds "Hamburg" says nothing about what it does with a place whose name
     * fills the panel, and the long names are where the overlaps are.
     */
    public static final String[] STRESS;

    static {
        STRESS = new String[IDS.length];
        for (int i = 0; i < IDS.length; i++) {
            STRESS[i] = IDS[i] + ":long";
        }
    }

    /** A real place, and one of the longest names Germany has to offer. */
    public static final String LONG_PLACE = "Schmalkalden-Meiningen Nord";
    /** The label a tile gets when somebody names an entity after where it hangs. */
    public static final String LONG_LABEL = "Waschmaschine im Keller hinten links";

    /**
     * Set while a ":long" variant is being built, which is what makes the shared demo data -
     * the weather, the appointments, the batteries - come out in its awkward form. It is
     * assigned on every call to {@link #card}, so it can never leak from one card to the next.
     */
    private static boolean stress;

    private OeplDemo() {
    }

    /**
     * Robolectric runs without the app's resources, so the two bundled faces are loaded straight
     * off disk. Without them every pictogram would silently be skipped and the previews would not
     * show what reaches the display.
     */
    public static void loadFonts() {
        final Typeface text = Typeface.createFromFile(fontFile("oepl_signika.ttf"));
        final Typeface icons = Typeface.createFromFile(fontFile("oepl_weathericons.ttf"));
        OeplFonts.override(text, icons);
    }

    /** The tests may run from the project root or from the app module, so look in both. */
    private static File fontFile(final String name) {
        final File inModule = new File("src/main/res/font/" + name);
        return inModule.isFile() ? inModule : new File("app/src/main/res/font/" + name);
    }

    // ------------------------------------------------------------------ the cards

    public static CardContent card(final String id) {
        final String kind = id.contains(":") ? id.substring(0, id.indexOf(':')) : id;
        final String variant = id.contains(":") ? id.substring(id.indexOf(':') + 1) : "";
        stress = "long".equals(variant);
        switch (kind) {
            case OeplCardFactory.WEATHER:
                if ("night".equals(variant)) {
                    final OeplWeather night = weather();
                    night.current.day = false;
                    night.current.wmoCode = 1;
                    night.current.temperature = -3.5f;
                    night.current.windSpeed = 14.2f;
                    return new OeplWeatherCard(night, true);
                }
                return new OeplWeatherCard(weather(), true);
            case OeplCardFactory.FORECAST:
                return new OeplForecastCard(weather(), "ms".equals(variant) ? 7 : 5,
                        !"ms".equals(variant));
            case OeplCardFactory.RAIN:
                return new OeplRainCard(weather(), 24);
            case OeplCardFactory.DATE:
                return "plain".equals(variant)
                        ? new OeplDateCard(null, false) : new OeplDateCard(weather(), true);
            case OeplCardFactory.CLOCK:
                return new OeplClockCard();
            case OeplCardFactory.CALENDAR:
                return new OeplCalendarListCard(events(), stress ? 10 : 4,
                        stress ? "Familienkalender, Schule und Werkstatttermine" : "Kalender");
            case OeplCardFactory.CALENDAR_WEEK:
                return new OeplCalendarWeekCard(events(), 3);
            case OeplCardFactory.RSS:
                return new OeplRssCard(feed(),
                        stress ? "Nachrichten aus der Region und dem Umland" : "Nachrichten",
                        "summary".equals(variant));
            case OeplCardFactory.QR:
                return new OeplQrCard("https://codeberg.org/Freeyourgadget/Gadgetbridge",
                        stress ? "Gadgetbridge, Quelltext und Anleitung dazu" : "Gadgetbridge");
            case OeplCardFactory.COUNT_DAYS:
                return new OeplCounterCard(42, 30, "bis Weihnachten");
            case OeplCardFactory.COUNT_HOURS:
                return "big".equals(variant)
                        ? new OeplCounterCard(123456, 10, "Stunden ohne Zwischenfall")
                        : new OeplCounterCard(7, 1000000, null);
            case OeplCardFactory.BIG:
                return new OeplBigTemperatureCard(weather(), true);
            case OeplCardFactory.RANGE:
                return new OeplRangeCard(weather(), "week".equals(variant) ? 7 : 5);
            case OeplCardFactory.SUN:
                if ("night".equals(variant)) {
                    // the sun long down, so the moon takes its place on the arc
                    final OeplWeather night = weather();
                    final long now = System.currentTimeMillis() / 1000L;
                    night.sunrise = now - 10 * 3600L;
                    night.sunset = now - 2 * 3600L;
                    return new OeplSunCard(night);
                }
                return new OeplSunCard(weather());
            case OeplCardFactory.TILES:
                return new OeplTilesCard(weather(), true);
            case OeplCardFactory.BOARD:
                return new OeplBoardCard(events(), 6,
                        stress ? "Termine der ganzen Familie" : "Termine");
            case OeplCardFactory.POSTER:
                if ("one".equals(variant)) {
                    return new OeplPosterCard(Arrays.asList("Zimmer 4"));
                }
                if (stress) {
                    return new OeplPosterCard(Arrays.asList(
                            "Werkstatt und Ersatzteillager im Hinterhof",
                            "!Bitte an der Seitentuer klingeln und warten",
                            "Montag bis Freitag von 8 bis 17 Uhr"));
                }
                return new OeplPosterCard(Arrays.asList("Werkstatt",
                        "!Bitte klingeln", "Mo - Fr 8 bis 17 Uhr"));
            case OeplCardFactory.GRID:
                return new OeplGridCard("weather".equals(variant)
                        ? readings(false, false) : readings(true, true),
                        stress ? "Kueche, Esszimmer und Flur oben" : "Kueche");
            case OeplCardFactory.HERO:
                return new OeplHeroCard(readings(true, true),
                        "noagenda".equals(variant) ? java.util.Collections.emptyList()
                                : events());
            case OeplCardFactory.LIST:
                return new OeplListCard(readings(true, true));
            case OeplCardFactory.GRAPH:
                if ("dry".equals(variant)) {
                    // a forecast without a drop in it, so the curve has the panel to itself
                    final OeplWeather dry = weather();
                    for (final OeplWeather.Hour hour : dry.hours) {
                        hour.precipitation = 0f;
                    }
                    return new OeplGraphCard(dry, 24);
                }
                return new OeplGraphCard(weather(), 18);
            case OeplCardFactory.QUAD: {
                final List<CardContent> parts = new ArrayList<>();
                parts.add(new WeatherContent(spec(), 3));
                parts.add(new CalendarContent(events(), 4));
                if (!"two".equals(variant)) {
                    parts.add(new OeplRssCard(feed(), "Nachrichten", false));
                }
                if (!"two".equals(variant) && !"three".equals(variant)) {
                    parts.add(new OeplListCard(readings(false, true)));
                }
                return new QuadContent(parts);
            }
            case OeplCardFactory.INFO:
                if (stress) {
                    return new OeplInfoCard("Kueche, Esszimmer und Flur oben", Arrays.asList(
                            new OeplInfoCard.Row("Typ", "Hanshow Stellar Pro M3 2.9 BWRY"),
                            new OeplInfoCard.Row("Panel", "384 x 184, 4 Farben, gedreht"),
                            new OeplInfoCard.Row("Firmware", "124 (OpenEPaperLink)"),
                            new OeplInfoCard.Row("Akku", "3,07 V (95%), gemessen 12:40"),
                            new OeplInfoCard.Row("Temperatur", "23 °C am Panel"),
                            new OeplInfoCard.Row("MAC", "FF:FC:4F:05:E4:11"),
                            new OeplInfoCard.Row("Telefon", "64%, laedt nicht"),
                            new OeplInfoCard.Row("Stand", "15.10.2026 22:15:43")));
                }
                return new OeplInfoCard("Kueche", Arrays.asList(
                        new OeplInfoCard.Row("Typ", "HS BWRY 3,5"),
                        new OeplInfoCard.Row("Panel", "384 x 184, 4 Farben"),
                        new OeplInfoCard.Row("Firmware", "124"),
                        new OeplInfoCard.Row("Akku", "3,07 V (95%)"),
                        new OeplInfoCard.Row("Temperatur", "23 °C"),
                        new OeplInfoCard.Row("MAC", "FF:FC:4F:05:E4:11"),
                        new OeplInfoCard.Row("Telefon", "64%"),
                        new OeplInfoCard.Row("Stand", "15.10. 22:15")));

            case CardContentFactory.DATE:
                return new DateContent();
            case CardContentFactory.CALENDAR:
                return new CalendarContent(events(), 4);
            case CardContentFactory.TEXT:
                return "long".equals(variant)
                        ? new TextContent("Einkaufsliste fuer das Wochenende", Arrays.asList(
                        "Milch, Brot, Kaffee, Butter und Marmelade",
                        "!Paket bei der Nachbarin abholen",
                        "Wasser", "Hundefutter", "Blumenerde"))
                        : new TextContent("Einkaufen",
                        Arrays.asList("Milch, Brot, Kaffee", "!Paket abholen", "Wasser"));
            case CardContentFactory.STATUS:
                return "many".equals(variant)
                        ? new StatusContent(Arrays.asList(
                        new StatusContent.Entry("Telefon", 64),
                        new StatusContent.Entry("Pixel Watch 2", 72),
                        new StatusContent.Entry("Mi Band 7", 15),
                        new StatusContent.Entry("Buds Pro", 91),
                        new StatusContent.Entry("Tastatur im Buero", 8),
                        new StatusContent.Entry("Fahrradcomputer", 55)))
                        : new StatusContent(Arrays.asList(
                        new StatusContent.Entry(stress
                                ? "Pixel Watch 2 von der Kollegin aus dem Buero"
                                : "Pixel Watch", 72),
                        new StatusContent.Entry("Mi Band 7", 15),
                        new StatusContent.Entry("Buds", 91)));
            case CardContentFactory.CLOCK:
                return new ClockContent();
            case CardContentFactory.NOTIFICATIONS:
                return new NotificationsContent(notes("long".equals(variant)), 4,
                        "4 notifications");
            case CardContentFactory.MUSIC:
                return new MusicContent(music(), true, "Now playing");
            case CardContentFactory.HOURLY:
                return new HourlyWeatherContent(spec(), 12, "Curve");
            case CardContentFactory.FORECAST:
                return new ForecastContent(spec(), 5, "Forecast");
            case CardContentFactory.WEATHER_CALENDAR:
                return new SplitContent(new WeatherContent(spec(), 3),
                        new CalendarContent(events(), 4), 0.55f);
            case CardContentFactory.WEATHER_DATE:
                return new SplitContent(new WeatherContent(spec(), 3), new DateContent(), 0.6f);
            case CardContentFactory.DATE_CALENDAR:
                return new SplitContent(new DateContent(),
                        new CalendarContent(events(), 4), 0.4f);
            case CardContentFactory.CLOCK_WEATHER:
                return new SplitContent(new ClockContent(),
                        new WeatherContent(spec(), 3), 0.45f);
            case CardContentFactory.WEATHER_NOTIFICATIONS:
                return new SplitContent(new WeatherContent(spec(), 3),
                        new NotificationsContent(notes(false), 4, "4 notifications"), 0.5f);
            case CardContentFactory.CALENDAR_NOTIFICATIONS:
                return new SplitContent(new CalendarContent(events(), 4),
                        new NotificationsContent(notes(false), 4, "4 notifications"), 0.5f);
            case CardContentFactory.DASHBOARD:
                return new SplitContent(new WeatherContent(spec(), 3),
                        new SplitContent(new CalendarContent(events(), 4),
                                new NotificationsContent(notes(false), 3, "3 notifications"),
                                0.5f), 0.45f);
            case CardContentFactory.IMAGE:
            case CardContentFactory.IMAGE_URL:
                return new ImageContent(picture(),
                        "none".equals(variant) ? ImageContent.Dither.NONE
                                : ("ordered".equals(variant) ? ImageContent.Dither.ORDERED
                                : ImageContent.Dither.DIFFUSION),
                        "stretch".equals(variant) ? ImageContent.Fit.STRETCH
                                : (CardContentFactory.IMAGE_URL.equals(kind)
                                ? ImageContent.Fit.COVER : ImageContent.Fit.CONTAIN),
                        palette());
            case CardContentFactory.WEATHER:
            default:
                return new WeatherContent(spec(), 3);
        }
    }

    /**
     * What the dashboard cards draw: the same four groups the collector builds on a phone, with
     * values chosen to put pressure on the layouts - a house entity whose name is too long for
     * a tile, a door that is open and therefore accented, a battery at fifteen percent.
     *
     * @param house  whether the Home Assistant group is in, as it is when an instance is set up
     * @param others whether the agenda and the batteries are in as well
     */
    public static List<OeplReading> readings(final boolean house, final boolean others) {
        final List<OeplReading> readings = new ArrayList<>();
        final String outside = stress ? "Draussen vor dem Fenster" : "Draussen";
        readings.add(new OeplReading(outside, stress ? LONG_PLACE : "Hamburg", "")
                .withTemperature(stress ? -12.7f : 13.4f, true)
                .withFontGlyph(OeplIcons.weather(61, false)));
        readings.add(new OeplReading(outside, "Heute", "")
                .withRange(9f, 15f)
                .withFontGlyph(String.valueOf(OeplIcons.THERMOMETER)));
        readings.add(new OeplReading(outside, stress ? "Niederschlag heute" : "Regen",
                stress ? "123.4 mm" : "2.3 mm")
                .withFontGlyph(String.valueOf(OeplIcons.RAINDROP))
                .accented(true));
        readings.add(new OeplReading(outside, "Wind", "4")
                .withFontGlyph(OeplIcons.windDirection(225)));
        readings.add(new OeplReading(outside, "Feuchte", "78 %")
                .withFontGlyph(String.valueOf(OeplIcons.UMBRELLA))
                .withPercent(78));
        if (house) {
            final String inside = "Haus";
            readings.add(new OeplReading(inside,
                    stress ? "Wohnzimmer hinten am Fenster" : "Wohnzimmer",
                    stress ? "-12.8 °C" : "21.8 °C")
                    .withFontGlyph(String.valueOf(OeplIcons.THERMOMETER)));
            readings.add(new OeplReading(inside,
                    stress ? "Haustuer und Kellerabgang" : "Haustuer",
                    stress ? "weit offen seit 12:40" : "offen")
                    .withShape(OeplGlyphs.Glyph.DOOR)
                    .accented(true));
            readings.add(new OeplReading(inside,
                    stress ? LONG_LABEL : "Waschmaschine im Keller",
                    stress ? "laeuft im Schleudergang" : "laeuft")
                    .withShape(OeplGlyphs.Glyph.PLUG));
            readings.add(new OeplReading(inside,
                    stress ? "Photovoltaik heute gesamt" : "PV heute",
                    stress ? "1234.5 kWh" : "8.4 kWh")
                    .withShape(OeplGlyphs.Glyph.BOLT));
        }
        if (others) {
            readings.add(new OeplReading("Termine", "Termine", "2")
                    .withShape(OeplGlyphs.Glyph.CALENDAR)
                    .accented(true));
            readings.add(new OeplReading("Geraete", "Telefon", "64 %")
                    .withShape(OeplGlyphs.Glyph.PHONE)
                    .withPercent(64));
            readings.add(new OeplReading("Geraete", "Display", "95 %")
                    .withShape(OeplGlyphs.Glyph.BATTERY)
                    .withPercent(95));
            readings.add(new OeplReading("Geraete",
                    stress ? "Mi Band 7 von der Kollegin" : "Mi Band 7",
                    stress ? "100 %" : "15 %")
                    .withShape(OeplGlyphs.Glyph.WATCH)
                    .withPercent(stress ? 100 : 15)
                    .accented(true));
        }
        return readings;
    }

    /** The colours a three colour tag has, which is what a picture is reduced to. */
    public static int[] palette() {
        return OEPLImageCodec.palette(3, false);
    }

    /**
     * A stand in for a photograph: a diagonal gradient with a disc and a bar across it. Flat
     * colours would say nothing about dithering, which is the whole point of the picture cards.
     */
    public static Bitmap picture() {
        final int w = 240;
        final int h = 160;
        final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        for (int y = 0; y < h; y++) {
            final Paint line = new Paint();
            line.setColor(0xff000000 | (0x010101 * (y * 255 / h)));
            canvas.drawRect(0, y, w, y + 1, line);
        }
        final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        disc.setColor(0xffc02020);
        canvas.drawCircle(w * 0.35f, h * 0.45f, h * 0.3f, disc);
        final Paint bar = new Paint();
        bar.setColor(0xffffffff);
        canvas.drawRect(w * 0.6f, h * 0.15f, w * 0.92f, h * 0.85f, bar);
        return bitmap;
    }

    // ------------------------------------------------------------------ the data behind them

    public static OeplWeather weather() {
        final OeplWeather w = new OeplWeather();
        w.location = stress ? LONG_PLACE : "Hamburg";
        w.current.temperature = 13.4f;
        w.current.windSpeed = 7.2f;      // Beaufort 4
        w.current.windDirection = 225;
        w.current.wmoCode = 61;          // slight rain, so the umbrella shows up
        w.current.day = true;
        w.current.humidity = 78;
        final long midnight = midnight();
        w.sunrise = midnight + 7 * 3600;
        w.sunset = midnight + 19 * 3600 + 1800;
        w.moonPhase = 190;

        final int[][] days = {
                // wmo, max, min, rain in tenths of a millimetre, wind in tenths of m/s, direction
                {61, 15, 9, 23, 72, 225},
                {3, 14, 8, 0, 41, 180},
                {80, 12, 7, 55, 103, 270},
                {75, 2, -4, 18, 61, 45},
                {0, 17, 6, 0, 22, 90},
                {95, 19, 11, 140, 155, 200},
                {1, 16, 10, 0, 33, 160},
        };
        for (int i = 0; i < days.length; i++) {
            final OeplWeather.Day d = new OeplWeather.Day();
            d.epochSeconds = midnight + i * 86400L;
            d.wmoCode = days[i][0];
            d.maxTemperature = days[i][1];
            d.minTemperature = days[i][2];
            d.precipitation = days[i][3] / 10f;
            d.windSpeed = days[i][4] / 10f;
            d.windDirection = days[i][5];
            w.days.add(d);
        }
        final long now = System.currentTimeMillis() / 1000L;
        final float[] rain = {0f, 0.2f, 1.4f, 2.6f, 1.1f, 0f, 0f, 0.4f, 3.8f, 1.2f, 0f, 0f,
                0.1f, 0f, 0f, 0.9f, 2.2f, 0.3f, 0f, 0f, 0f, 0f, 0.6f, 0f};
        for (int i = 0; i < rain.length; i++) {
            final OeplWeather.Hour h = new OeplWeather.Hour();
            h.epochSeconds = now + i * 3600L;
            h.temperature = 13f + (i % 7) - 3;
            h.wmoCode = rain[i] > 1f ? 63 : (rain[i] > 0f ? 51 : 3);
            h.precipitation = rain[i];
            w.hours.add(h);
        }
        return w;
    }

    /** The same weather in the shape Gadgetbridge normally carries it. */
    public static WeatherSpec spec() {
        final WeatherSpec spec = new WeatherSpec();
        spec.setLocation(stress ? LONG_PLACE : "Hamburg");
        spec.setCurrentTemp(287);
        spec.setCurrentConditionCode(802);
        spec.setCurrentCondition("Stark bewoelkt");
        spec.setTodayMaxTemp(289);
        spec.setTodayMinTemp(281);
        spec.setWindSpeed(26f);
        spec.setWindDirection(225);
        spec.setSunRise((int) (System.currentTimeMillis() / 1000) - 3600 * 6);
        spec.setSunSet((int) (System.currentTimeMillis() / 1000) + 3600 * 4);
        spec.setTimestamp((int) (System.currentTimeMillis() / 1000));

        final ArrayList<WeatherSpec.Daily> days = new ArrayList<>();
        for (final int[] f : new int[][]{{290, 282, 500}, {288, 281, 803}, {286, 280, 200},
                {275, 268, 601}, {293, 284, 800}}) {
            final WeatherSpec.Daily d = new WeatherSpec.Daily();
            d.setMaxTemp(f[0]);
            d.setMinTemp(f[1]);
            d.setConditionCode(f[2]);
            days.add(d);
        }
        spec.setForecasts(days);

        final ArrayList<WeatherSpec.Hourly> hourly = new ArrayList<>();
        final int base = (int) (System.currentTimeMillis() / 1000);
        final int[] curve = {287, 288, 290, 292, 293, 292, 290, 287, 285, 283, 282, 281};
        for (int i = 0; i < curve.length; i++) {
            final WeatherSpec.Hourly hr = new WeatherSpec.Hourly();
            hr.setTimestamp(base + i * 3600);
            hr.setTemp(curve[i]);
            hr.setConditionCode(i % 4 == 0 ? 500 : 802);
            hourly.add(hr);
        }
        spec.setHourly(hourly);
        return spec;
    }

    public static long midnight() {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 1000L;
    }

    public static List<CalendarEvent> events() {
        final long base = midnight() * 1000L;
        final long now = System.currentTimeMillis();
        final List<CalendarEvent> events = new ArrayList<>();
        // one running right now, one later today, one with a title no panel can hold, one
        // tomorrow, one all day
        if (stress) {
            // every title too long, and two on the same morning, so that nothing is placed by
            // chance: this is the shape a real calendar has in a family of four
            events.add(event(now - 600_000L, now + 1800_000L, 0,
                    "Jahreshauptversammlung des Kleingartenvereins", false));
            events.add(event(now + 300_000L, now + 3600_000L, 1,
                    "Elterngespraech mit Frau Hohenschwangau-Reisinger", false));
            events.add(event(base + 13 * 3600_000L, base + 14 * 3600_000L, 2,
                    "Zahnarzt Dr. Mueller-Luedenscheidt, Praxis am Hauptbahnhof", false));
            events.add(event(base + 86400_000L + 10 * 3600_000L,
                    base + 86400_000L + 12 * 3600_000L, 3,
                    "Werkstatttermin: Inspektion, Reifen und Klimaanlage", false));
            events.add(event(base + 86400_000L, base + 3 * 86400_000L, 4,
                    "Betriebsausflug in den Harz mit Uebernachtung", true));
            return events;
        }
        events.add(event(now - 600_000L, now + 1800_000L, 0, "Standup", false));
        events.add(event(base + 13 * 3600_000L, base + 14 * 3600_000L, 1, "Zahnarzt", false));
        events.add(event(base + 16 * 3600_000L, base + 18 * 3600_000L, 2,
                "Einkaufen mit der ganzen Familie im Baumarkt", false));
        events.add(event(base + 86400_000L + 10 * 3600_000L,
                base + 86400_000L + 11 * 3600_000L, 3, "Werkstatt", false));
        events.add(event(base + 86400_000L, base + 2 * 86400_000L, 4, "Urlaub", true));
        events.add(event(base + 2 * 86400_000L + 9 * 3600_000L,
                base + 2 * 86400_000L + 10 * 3600_000L, 5, "Elternabend", false));
        return events;
    }

    private static CalendarEvent event(final long begin, final long end, final int id,
                                       final String title, final boolean allDay) {
        return new CalendarEvent(begin, end, id, id, title, "", "", "cal", "acc", 0, 0,
                allDay, "me", "LOCAL", "1", null, 0, 0);
    }

    public static List<OeplRssClient.Item> feed() {
        return OeplRssClient.parse(
                "<rss><channel>"
                        + "<item><title>Hochwasserlage an der Elbe entspannt sich langsam</title>"
                        + "<description>Die Pegel sinken, die Sperrungen bleiben vorerst.</description></item>"
                        + "<item><title>Neue Fahrplaene ab Dezember</title>"
                        + "<description>Mehr Zuege nach Berlin, dafuer weniger am Wochenende.</description></item>"
                        + "<item><title>Stadt sucht Namen fuer die neue Faehre</title>"
                        + "<description>Vorschlaege koennen bis Ende des Monats eingereicht werden.</description></item>"
                        + "<item><title>Wochenmarkt zieht um</title>"
                        + "<description>Ab Mai auf dem Rathausplatz.</description></item>"
                        + "</channel></rss>");
    }

    public static List<PhoneStateStore.Note> notes(final boolean long_) {
        final String[][] data = long_
                ? new String[][]{
                {"Signal", "Anna Lindemann-Schmitt", "Bis gleich, ich bringe den Kuchen mit!"},
                {"Mail", "Ihre Rechnung fuer Oktober liegt bereit", "Bitte bis zum 30. zahlen"},
                {"Kalender", "Standup in 10 Minuten", ""},
                {"Messenger", "Tom", "Schaffst du 18 Uhr?"}}
                : new String[][]{
                {"Signal", "Anna", "Bis gleich!"},
                {"Mail", "Rechnung Mai", "Ihre Rechnung liegt bereit"},
                {"Kalender", "Standup in 10 min", ""},
                {"Messenger", "Tom", "Schaffst du 18 Uhr?"}};
        // built directly rather than pushed through the store, so one card's demo data cannot
        // leak into the next one's
        final List<PhoneStateStore.Note> notes = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (int i = 0; i < data.length; i++) {
            notes.add(new PhoneStateStore.Note(now - i * 600_000L, data[i][0], data[i][1],
                    data[i][2].isEmpty() ? null : data[i][2]));
        }
        return notes;
    }

    public static MusicSpec music() {
        final MusicSpec m = new MusicSpec();
        m.setTrack("Weightless");
        m.setArtist("Marconi Union");
        m.setAlbum("Ambient Transmissions Vol. 2");
        return m;
    }
}
