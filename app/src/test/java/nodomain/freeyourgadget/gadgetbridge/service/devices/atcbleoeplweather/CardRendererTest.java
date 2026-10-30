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
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.PhoneStateStore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CalendarContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ClockContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ForecastContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.HourlyWeatherContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.MusicContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.NotificationsContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.DateContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.SplitContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.StatusContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.TextContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.WeatherContent;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * Renders every content type at every panel geometry the firmware supports, runs the result
 * through the real encoder, and writes the quantized previews to app/build/oepl-preview.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class CardRendererTest {

    /** Buffer geometries and colour counts taken from the display types the firmware knows. */
    private static final int[][] PANELS = {
            {152, 152, 3}, {200, 200, 3}, {128, 250, 3}, {128, 256, 3},
            {128, 296, 3}, {152, 296, 3}, {184, 384, 4}, {300, 400, 3},
            {480, 800, 3}, {672, 960, 4}, {122, 250, 2}, {296, 128, 3},
            {384, 184, 3}, {400, 300, 3},
            // the big panels that are written the way they are read, long axis first: the 9.7"
            // and the 7.5" wall panel. Only ever listed the other way round, the sweep never
            // drew what those two actually get handed.
            {960, 672, 3}, {800, 480, 3},
    };

    private static WeatherSpec demoSpec() {
        final WeatherSpec spec = new WeatherSpec();
        spec.setLocation("Hamburg");
        spec.setCurrentTemp(287);
        spec.setCurrentConditionCode(802);
        spec.setCurrentCondition("Stark bewolkt");
        spec.setTodayMaxTemp(289);
        spec.setTodayMinTemp(281);
        spec.setSunRise((int) (System.currentTimeMillis() / 1000) - 3600 * 6);
        spec.setSunSet((int) (System.currentTimeMillis() / 1000) + 3600 * 4);
        spec.setTimestamp((int) (System.currentTimeMillis() / 1000));
        final ArrayList<WeatherSpec.Daily> days = new ArrayList<>();
        for (final int[] f : new int[][]{{290, 282, 500}, {288, 281, 803}, {286, 280, 200}}) {
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

    private static List<CalendarEvent> demoEvents() {
        final long now = System.currentTimeMillis();
        final List<CalendarEvent> events = new ArrayList<>();
        final String[] titles = {"Standup", "Zahnarzt", "Einkaufen mit Familie", "Kino"};
        for (int i = 0; i < titles.length; i++) {
            final long begin = now + (i + 1) * 3600_000L * 5;
            events.add(new CalendarEvent(begin, begin + 3600_000L, i, i, titles[i],
                    "", "", "cal", "acc", 0, 0, false, "me", "LOCAL", "1", null, 0, 0));
        }
        return events;
    }

    private static java.util.List<PhoneStateStore.Note> demoNotes() {
        final java.util.List<nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec> specs =
                new ArrayList<>();
        final String[][] data = {
                {"Signal", "Anna", "Bis gleich!"},
                {"Mail", "Rechnung Mai", "Ihre Rechnung liegt bereit"},
                {"Kalender", "Standup in 10 min", ""},
                {"Messenger", "Tom", "Schaffst du 18 Uhr?"},
        };
        for (final String[] d : data) {
            final nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec n =
                    new nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec();
            n.setSourceName(d[0]);
            n.setTitle(d[1]);
            n.setBody(d[2].isEmpty() ? null : d[2]);
            specs.add(n);
        }
        // feed them through the real store so ordering and de-duplication are exercised too
        for (int i = specs.size() - 1; i >= 0; i--) {
            PhoneStateStore.remember(specs.get(i));
        }
        return PhoneStateStore.notifications();
    }

    private static nodomain.freeyourgadget.gadgetbridge.model.MusicSpec demoMusic() {
        final nodomain.freeyourgadget.gadgetbridge.model.MusicSpec m =
                new nodomain.freeyourgadget.gadgetbridge.model.MusicSpec();
        m.setTrack("Weightless");
        m.setArtist("Marconi Union");
        m.setAlbum("Ambient Transmissions Vol. 2");
        return m;
    }

    private static CardContent content(final String kind) {
        switch (kind) {
            case "date":
                return new DateContent();
            case "calendar":
                return new CalendarContent(demoEvents(), 4);
            case "text":
                return new TextContent("Einkaufen", Arrays.asList(
                        "Milch, Brot, Kaffee", "!Paket abholen", "Wasser"));
            case "status":
                return new StatusContent(Arrays.asList(
                        new StatusContent.Entry("Pixel Watch", 72),
                        new StatusContent.Entry("Mi Band 7", 15),
                        new StatusContent.Entry("Buds", 91)));
            case "weather_calendar":
                return new SplitContent(new WeatherContent(demoSpec(), 3),
                        new CalendarContent(demoEvents(), 4), 0.55f);
            case "clock":
                return new ClockContent();
            case "hourly":
                return new HourlyWeatherContent(demoSpec(), 12, "Curve");
            case "forecast":
                return new ForecastContent(demoSpec(), 5, "Forecast");
            case "notifications":
                return new NotificationsContent(demoNotes(), 4, "4 notifications");
            case "music":
                return new MusicContent(demoMusic(), true, "Now playing");
            case "dashboard":
                return new SplitContent(new WeatherContent(demoSpec(), 3),
                        new SplitContent(new CalendarContent(demoEvents(), 4),
                                new NotificationsContent(demoNotes(), 3, "3 notifications"),
                                0.5f), 0.45f);
            case "weather":
            default:
                return new WeatherContent(demoSpec(), 3);
        }
    }

    private static Bitmap renderAndEncode(final int w, final int h, final int colors,
                                          final CardContent c) {
        final CardRenderer.Options opt = new CardRenderer.Options();
        opt.accent = OEPLImageCodec.RED;
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
    public void everyPanelGeometryRenders() throws Exception {
        for (final int[] panel : PANELS) {
            final Bitmap b = renderAndEncode(panel[0], panel[1], panel[2], content("weather"));
            write(b, String.format("panel_%04dx%04d_c%d.png", panel[0], panel[1], panel[2]));
        }
    }

    @Test
    public void everyContentTypeRenders() throws Exception {
        // the panel this was developed against, drawn the way it reaches the display
        for (final String kind : new String[]{
                "weather", "date", "clock", "calendar", "text", "status",
                "notifications", "music", "hourly", "forecast",
                "weather_calendar", "dashboard"}) {
            final Bitmap b = renderAndEncode(384, 184, 4, content(kind));
            write(b, "content_" + kind + ".png");
        }
    }

    @Test
    public void everyFrameStyleRenders() throws Exception {
        for (final CardRenderer.Frame frame : CardRenderer.Frame.values()) {
            final CardRenderer.Options opt = new CardRenderer.Options();
            opt.frame = frame;
            opt.accent = OEPLImageCodec.RED;
            opt.batteryMillivolts = 3070;
            opt.panelTemperature = 23;
            final Bitmap b = new CardRenderer(opt).render(384, 184, 0, content("weather"));
            assertEquals(384, b.getWidth());
            write(b, "frame_" + frame.name().toLowerCase(java.util.Locale.ROOT) + ".png");
        }
    }

    @Test
    public void rotationKeepsBufferGeometry() {
        for (final int rotation : new int[]{0, 90, 180, 270}) {
            final Bitmap b = new CardRenderer(new CardRenderer.Options())
                    .render(184, 384, rotation, content("weather"));
            assertEquals("width at " + rotation, 184, b.getWidth());
            assertEquals("height at " + rotation, 384, b.getHeight());
        }
    }

    /**
     * What goes out is packed to the row length it is announced with, and that row length is
     * the panel's own.
     * <p>
     * A 9.7" drawn from the catalogue used to be built as 672 by 960 and announced as 672 wide,
     * although that panel is written the way it is read: 960 across. The display laid the same
     * bytes out in rows of the length it was told, which is a picture lying on its side and
     * sheared into noise - and only on the connections where the display had not described
     * itself yet, so the next refresh put it right again.
     */
    @Test
    public void aPanelGetsThePictureInTheRowLengthItIsWrittenIn() {
        for (final int hardwareType : new int[]{0x2E, 0xD5, 0x36, 0x2D, 0x53, 0x6D}) {
            final OEPLDisplayConfig panel = OEPLDisplayConfig.fromCatalogue(hardwareType);
            final OEPLTagTypes.TagType type = OEPLTagTypes.byHardwareType(hardwareType);
            final String what = type.name + " (0x" + Integer.toHexString(hardwareType) + ")";

            final CardRenderer.Options opt = new CardRenderer.Options();
            opt.accent = OEPLImageCodec.RED;
            final Bitmap card = new CardRenderer(opt).render(panel.bufferWidth,
                    panel.bufferHeight, panel.autoRotationDegrees(), content("weather"));
            assertEquals(what, panel.bufferWidth, card.getWidth());
            assertEquals(what, panel.bufferHeight, card.getHeight());
            assertEquals(what, panel.planeSizeBytes() * panel.planeCount(),
                    OEPLImageCodec.packPlanes(OEPLImageCodec.pixelsOf(card), card.getWidth(),
                            card.getHeight(), panel.colorCount(), false, false, false).length);

            // and turned back by the rotation it was given, it is the panel as it is read
            final boolean quarterTurn = panel.autoRotationDegrees() % 180 != 0;
            assertEquals(what + " is read " + type.width + " across",
                    type.width, quarterTurn ? card.getHeight() : card.getWidth());
        }
    }

    @Test
    public void emptyContentFallsBackToAHint() {
        final CardRenderer.Options opt = new CardRenderer.Options();
        opt.emptyHint = "no weather data yet";
        final Bitmap b = new CardRenderer(opt).render(296, 128, 0, new WeatherContent(null, 3));
        assertEquals(296, b.getWidth());
        assertEquals(128, b.getHeight());
    }
}
