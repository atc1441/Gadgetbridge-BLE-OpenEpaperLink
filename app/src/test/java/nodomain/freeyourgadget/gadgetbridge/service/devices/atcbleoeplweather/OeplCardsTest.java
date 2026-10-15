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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Typeface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCalendarListCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCalendarWeekCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplClockCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCounterCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplDateCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplFonts;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplForecastCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplIcons;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplInfoCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplQrCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRainCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeather;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeatherCard;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * Draws every card that follows the OpenEPaperLink layouts, at every panel geometry the
 * firmware knows, and puts the results through the real encoder. The previews land in
 * app/build/oepl-preview so that the layouts can be looked at rather than only asserted on.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplCardsTest {

    private static final int[][] PANELS = {
            {152, 152, 3}, {200, 200, 3}, {128, 250, 3}, {128, 296, 3},
            {184, 384, 4}, {296, 128, 3}, {384, 184, 4}, {300, 400, 3},
            {400, 300, 3}, {480, 800, 3}, {672, 960, 4}, {122, 250, 2},
    };

    /**
     * Robolectric runs without the app's resources, so the two bundled faces are loaded
     * straight off disk. Without them every pictogram would silently be skipped and these
     * previews would not show what reaches the display.
     */
    @Before
    public void loadFonts() {
        final File text = fontFile("oepl_signika.ttf");
        final File icons = fontFile("oepl_weathericons.ttf");
        assertTrue("the text face has to be on disk: " + text, text.isFile());
        assertTrue("the icon face has to be on disk: " + icons, icons.isFile());
        final Typeface textFace = Typeface.createFromFile(text);
        final Typeface iconFace = Typeface.createFromFile(icons);
        assertNotNull("the text face has to load", textFace);
        assertNotNull("the icon face has to load", iconFace);
        OeplFonts.override(textFace, iconFace);
    }

    /** The tests may run from the project root or from the app module, so look in both. */
    private static File fontFile(final String name) {
        final File inModule = new File("src/main/res/font/" + name);
        return inModule.isFile() ? inModule : new File("app/src/main/res/font/" + name);
    }

    private static OeplWeather demoWeather() {
        final OeplWeather w = new OeplWeather();
        w.location = "Hamburg";
        w.current.temperature = 13.4f;
        w.current.windSpeed = 7.2f;      // Beaufort 4
        w.current.windDirection = 225;
        w.current.wmoCode = 61;          // slight rain, so the umbrella shows up
        w.current.day = true;
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

    private static long midnight() {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 1000L;
    }

    private static List<CalendarEvent> demoEvents() {
        final long base = midnight() * 1000L;
        final List<CalendarEvent> events = new ArrayList<>();
        // one running right now, one later today, one tomorrow, one all day
        final long now = System.currentTimeMillis();
        events.add(event(now - 600_000L, now + 1800_000L, 0, "Standup", false));
        events.add(event(base + 13 * 3600_000L, base + 14 * 3600_000L, 1, "Zahnarzt", false));
        events.add(event(base + 16 * 3600_000L, base + 18 * 3600_000L, 2,
                "Einkaufen mit der Familie", false));
        events.add(event(base + 86400_000L + 10 * 3600_000L,
                base + 86400_000L + 11 * 3600_000L, 3, "Werkstatt", false));
        events.add(event(base + 86400_000L, base + 2 * 86400_000L, 4, "Urlaub", true));
        return events;
    }

    private static CalendarEvent event(final long begin, final long end, final int id,
                                       final String title, final boolean allDay) {
        return new CalendarEvent(begin, end, id, id, title, "", "", "cal", "acc", 0, 0,
                allDay, "me", "LOCAL", "1", null, 0, 0);
    }

    private static List<OeplRssClient.Item> demoFeed() {
        return OeplRssClient.parse(
                "<rss><channel>"
                        + "<item><title>Hochwasserlage an der Elbe entspannt sich langsam</title>"
                        + "<description>Die Pegel sinken, die Sperrungen bleiben vorerst.</description></item>"
                        + "<item><title>Neue Fahrplaene ab Dezember</title>"
                        + "<description>Mehr Zuege nach Berlin, dafuer weniger am Wochenende.</description></item>"
                        + "<item><title>Stadt sucht Namen fuer die neue Faehre</title>"
                        + "<description>Vorschlaege koennen bis Ende des Monats eingereicht werden.</description></item>"
                        + "<item><title>Wochenmarkt zieht um</title><description>Ab Mai auf dem Rathausplatz.</description></item>"
                        + "</channel></rss>");
    }

    private static CardContent card(final String kind) {
        switch (kind) {
            case "date":
                return new OeplDateCard(demoWeather(), true);
            case "date_plain":
                return new OeplDateCard(null, false);
            case "clock":
                return new OeplClockCard();
            case "forecast":
                return new OeplForecastCard(demoWeather(), 5, true);
            case "forecast_ms":
                return new OeplForecastCard(demoWeather(), 7, false);
            case "rain":
                return new OeplRainCard(demoWeather(), 24);
            case "calendar":
                return new OeplCalendarListCard(demoEvents(), 10, "Kalender");
            case "calendar_week":
                return new OeplCalendarWeekCard(demoEvents(), 3);
            case "rss":
                return new OeplRssCard(demoFeed(), "Nachrichten", false);
            case "rss_summary":
                return new OeplRssCard(demoFeed(), "Nachrichten", true);
            case "qr":
                return new OeplQrCard("https://codeberg.org/Freeyourgadget/Gadgetbridge", "WLAN");
            case "count_days":
                return new OeplCounterCard(42, 30, "bis Weihnachten");
            case "count_hours":
                return new OeplCounterCard(7, 1000000, null);
            case "info":
                return new OeplInfoCard("Kueche", java.util.Arrays.asList(
                        new OeplInfoCard.Row("Typ", "HS BWRY 3,5"),
                        new OeplInfoCard.Row("Panel", "384 x 184, 4 Farben"),
                        new OeplInfoCard.Row("Firmware", "124"),
                        new OeplInfoCard.Row("Akku", "3,07 V (95%)"),
                        new OeplInfoCard.Row("Temperatur", "23 C"),
                        new OeplInfoCard.Row("Stand", "10.10. 22:15")));
            case "weather_night":
                final OeplWeather night = demoWeather();
                night.current.day = false;
                night.current.wmoCode = 1;
                night.current.temperature = -3.5f;  // sub zero, so it goes to the accent colour
                return new OeplWeatherCard(night, true);
            case "weather":
            default:
                return new OeplWeatherCard(demoWeather(), true);
        }
    }

    private static final String[] KINDS = {
            "weather", "weather_night", "forecast", "forecast_ms", "rain", "date", "date_plain",
            "clock", "calendar", "calendar_week", "rss", "rss_summary", "qr", "count_days",
            "count_hours", "info"};

    private static Bitmap renderAndEncode(final int w, final int h, final int colors,
                                          final CardContent c) {
        final CardRenderer.Options opt = new CardRenderer.Options();
        opt.accent = colors > 2 ? OEPLImageCodec.RED : OEPLImageCodec.BLACK;
        opt.batteryMillivolts = 3070;
        opt.panelTemperature = 23;

        final Bitmap card = new CardRenderer(opt).render(w, h, 0, c);
        assertEquals("width", w, card.getWidth());
        assertEquals("height", h, card.getHeight());

        final int[] pixels = OEPLImageCodec.pixelsOf(card);
        final int[] palette = OEPLImageCodec.palette(colors, false);
        OEPLImageCodec.quantize(pixels, palette);
        final byte[] planes =
                OEPLImageCodec.packPlanes(pixels, w, h, colors, false, false, false);
        assertEquals("plane bytes", (w * h / 8) * (colors > 2 ? 2 : 1), planes.length);
        final byte[] compressed = OEPLImageCodec.compressedPayload(planes, w, h,
                colors > 2 ? 2 : 1);
        assertTrue("compression should help", compressed.length < planes.length);

        card.setPixels(pixels, 0, w, 0, 0, w, h);
        return card;
    }

    private static void write(final Bitmap b, final String name) throws Exception {
        final File dir = new File("build/oepl-preview");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(dir, name))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    @Test
    public void everyCardRendersOnTheTestPanel() throws Exception {
        for (final String kind : KINDS) {
            // drawn at the panel orientation, which is how it is read off the display
            final Bitmap b = renderAndEncode(384, 184, 4, card(kind));
            write(b, "oepl_" + kind + ".png");
        }
    }

    @Test
    public void everyCardSurvivesEveryGeometry() throws Exception {
        for (final int[] panel : PANELS) {
            for (final String kind : KINDS) {
                final Bitmap b = renderAndEncode(panel[0], panel[1], panel[2], card(kind));
                if ("weather".equals(kind) || "forecast".equals(kind)) {
                    write(b, String.format("oepl_%s_%04dx%04d.png", kind, panel[0], panel[1]));
                }
            }
        }
    }

    @Test
    public void theCardsTakeTheWholePanel() {
        for (final String kind : KINDS) {
            assertTrue(kind + " has to be drawn full bleed", card(kind).fullBleed());
        }
    }

    @Test
    public void emptySourcesAreReportedRatherThanDrawnBlank() {
        assertFalse(new OeplWeatherCard(null, true).hasData());
        assertFalse(new OeplForecastCard(null, 5, true).hasData());
        assertFalse(new OeplRainCard(null, 24).hasData());
        assertFalse(new OeplRssCard(new ArrayList<>(), "x", false).hasData());
        assertFalse(new OeplQrCard("", "x").hasData());
        assertFalse(new OeplCalendarListCard(new ArrayList<>(), 4, "x").hasData());
        // a date and a clock never have nothing to say
        assertTrue(new OeplDateCard(null, false).hasData());
        assertTrue(new OeplClockCard().hasData());
    }

    @Test
    public void windAndWeatherCodesMapTheWayTheAccessPointMapsThem() {
        assertEquals("calm", 0, OeplIcons.beaufort(0.1f));
        assertEquals("light breeze", 2, OeplIcons.beaufort(2f));
        assertEquals("fresh breeze", 5, OeplIcons.beaufort(9f));
        assertEquals("hurricane", 12, OeplIcons.beaufort(40f));

        // the clear sky differs between day and night, the rain does not
        assertFalse(OeplIcons.weather(0, false).equals(OeplIcons.weather(0, true)));
        assertEquals(OeplIcons.weather(61, false), OeplIcons.weather(61, true));

        assertTrue("heavy rain is severe", OeplIcons.isSevere(65));
        assertFalse("light rain is not", OeplIcons.isSevere(61));
        assertTrue("drizzle is wet", OeplIcons.isWet(51));
        assertFalse("overcast is dry", OeplIcons.isWet(3));

        // eight arrows, and north wraps round from both sides
        assertEquals(OeplIcons.windDirection(0), OeplIcons.windDirection(359));
        assertFalse(OeplIcons.windDirection(0).equals(OeplIcons.windDirection(90)));
    }

    @Test
    public void weatherConvertedFromGadgetbridgeKeepsItsMeaning() {
        final nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec spec =
                new nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec();
        spec.setLocation("Hamburg");
        spec.setCurrentTemp(283);          // 9.85 celsius
        spec.setCurrentConditionCode(502); // heavy rain
        spec.setWindSpeed(18f);            // km/h, so 5 m/s
        spec.setWindDirection(270);
        spec.setTodayMaxTemp(288);
        spec.setTodayMinTemp(280);
        final ArrayList<nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec.Daily> days =
                new ArrayList<>();
        final nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec.Daily d =
                new nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec.Daily();
        d.setMaxTemp(290);
        d.setMinTemp(281);
        d.setConditionCode(800);
        days.add(d);
        spec.setForecasts(days);

        final OeplWeather w = OeplWeather.from(spec);
        assertEquals("Hamburg", w.location);
        assertEquals(9.85f, w.current.temperature, 0.05f);
        assertEquals("km/h back to m/s", 5f, w.current.windSpeed, 0.05f);
        assertEquals("heavy rain maps onto WMO 65", 65, w.current.wmoCode);
        assertEquals("today plus one forecast day", 2, w.days.size());
        assertEquals("today's high", 14.85f, w.days.get(0).maxTemperature, 0.05f);
        assertEquals("a clear tomorrow", 0, w.days.get(1).wmoCode);
        assertTrue("a converted forecast is usable", w.hasData());
    }

    @Test
    public void owmAndWmoCodesRoundTripThroughTheIconTable() {
        // every WMO code Open-Meteo answers with has to survive the detour through OWM
        for (final int wmo : new int[]{0, 1, 2, 3, 45, 51, 55, 61, 65, 71, 75, 80, 85, 95}) {
            final int owm = OpenMeteoClient.toOwmCode(wmo);
            final int back = OeplWeather.toWmoCode(owm);
            assertEquals("the pictogram has to stay the same for WMO " + wmo,
                    OeplIcons.weather(wmo, false), OeplIcons.weather(back, false));
        }
    }

    @Test
    public void theFeedParserTakesRssAndAtom() {
        final List<OeplRssClient.Item> rss = demoFeed();
        assertEquals(4, rss.size());
        assertEquals("Wochenmarkt zieht um", rss.get(3).title);

        final List<OeplRssClient.Item> atom = OeplRssClient.parse(
                "<feed xmlns='http://www.w3.org/2005/Atom'>"
                        + "<entry><title>Erster Eintrag</title><summary>Kurz &amp; knapp</summary></entry>"
                        + "<entry><title>Zweiter Eintrag</title></entry></feed>");
        assertEquals(2, atom.size());
        assertEquals("Erster Eintrag", atom.get(0).title);
        assertEquals("entities are resolved", "Kurz & knapp", atom.get(0).description);
        assertTrue("a missing summary is simply empty", atom.get(1).description.isEmpty());

        assertTrue("nonsense yields nothing rather than throwing",
                OeplRssClient.parse("not xml at all").isEmpty());
    }
}
