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
import android.graphics.RectF;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.QuadContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGlyphs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplGridCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplHeroCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplListCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplReading;

/**
 * The dashboard designs and the Home Assistant feed behind them.
 * <p>
 * The drawing tests here are all about one thing: size. A panel has two or three colours and a
 * quantiser that turns a thin grey stroke into paper, so text that is merely small is text that
 * is gone - which is exactly what these cards produced when they tried to show every value they
 * were given. A figure has to come out large enough to survive that, and the way to make sure
 * of it is to measure what was actually drawn.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplDashboardTest {

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    // ------------------------------------------------------------------ the tiles

    @Test
    public void aTileFigureStaysBigEnoughToSurviveThePanel() {
        // the twelve values of a fully configured display, on the shapes a tag really has
        for (final int[] shape : new int[][]{{384, 184}, {296, 128}, {400, 300}, {800, 480}}) {
            final float biggest = biggestFigure("panel_grid", shape[0], shape[1]);
            assertTrue(String.format("the figures on a %dx%d grid came out %.1f px",
                    shape[0], shape[1], biggest), biggest >= shape[1] * 0.1f);

            // and with half the values a sentence rather than a figure, which is what a
            // Home Assistant state often is: the sentences are cut, the figures stay large
            final float withProse = biggestFigure("panel_grid:long", shape[0], shape[1]);
            assertTrue(String.format("one long state shrank the %dx%d grid to %.1f px",
                    shape[0], shape[1], withProse),
                    withProse >= Math.max(9f, shape[1] * 0.08f));
        }
    }

    @Test
    public void aFigureIsToldApartFromASentence() {
        assertTrue(OeplGridCard.isFigure("13.4°"));
        assertTrue(OeplGridCard.isFigure("1234.5 kWh"));
        assertTrue(OeplGridCard.isFigure("offen"));
        assertFalse(OeplGridCard.isFigure("weit offen seit 12:40"));
        assertFalse(OeplGridCard.isFigure("laeuft im Schleudergang"));
        assertFalse(OeplGridCard.isFigure(null));
    }

    @Test
    public void aSmallPanelShowsFewerValuesRatherThanSmallerOnes() {
        final int large = tiles(800, 480);
        final int small = tiles(296, 128);
        assertTrue("a large panel should hold more tiles than a shelf label",
                large >= small);
        assertTrue("no panel shows more than a glance can take in", large <= 8);
        assertTrue("even a price tag shows something", tiles(152, 152) >= 1);
    }

    /** How many tiles a panel ends up with, counted from the rounded frames around them. */
    private int tiles(final int width, final int height) {
        int frames = 0;
        for (final OeplInkRecorder.Run run : draw("panel_grid", width, height).runs()) {
            if ("roundrect".equals(run.what)) {
                frames++;
            }
        }
        return frames;
    }

    // ------------------------------------------------------------------ the list

    @Test
    public void theListDropsRowsItCannotSetLargeEnough() {
        // a short panel cannot hold twelve rows at a readable size, so it must hold fewer
        final int onATag = rows("panel_list", 296, 128);
        final int onAPoster = rows("panel_list", 800, 480);
        assertTrue("the list drew nothing at all", onATag > 0);
        assertTrue("a poster should list more than a shelf label does", onAPoster > onATag);

        for (final OeplInkRecorder.Run run : draw("panel_list", 296, 128).textRuns()) {
            assertTrue("a row came out " + run.box.height() + " px high and would be lost",
                    run.box.height() >= 4f);
        }
    }

    private int rows(final String id, final int width, final int height) {
        int count = 0;
        for (final OeplInkRecorder.Run run : draw(id, width, height).textRuns()) {
            if (run.box.left < width * 0.3f) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ the quarters

    @Test
    public void theQuarteredCardDropsPanesRatherThanSqueezingThem() {
        final List<CardContent> four = Arrays.asList(OeplDemo.card("weather"),
                OeplDemo.card("calendar"), OeplDemo.card("oepl_9"), OeplDemo.card("clock"));
        assertTrue(new QuadContent(four).hasData());

        // a price tag cannot hold four cards; what it must not do is draw four unreadable ones
        final OeplInkRecorder small = drawContent(new QuadContent(four), 152, 152);
        final OeplInkRecorder large = drawContent(new QuadContent(four), 800, 480);
        assertTrue("a small panel should show less than a large one",
                small.textRuns().size() < large.textRuns().size());
        for (final OeplInkRecorder.Run run : small.textRuns()) {
            assertTrue("a quarter wrote outside the panel",
                    run.box.right <= 152 + 1f && run.box.bottom <= 152 + 1f);
        }
    }

    @Test
    public void aQuarteredCardWithNothingConfiguredSaysSo() {
        assertFalse(new QuadContent(new ArrayList<>()).hasData());
        assertFalse(new QuadContent(null).hasData());
    }

    // ------------------------------------------------------------------ the values themselves

    @Test
    public void aTemperatureIsFormattedWhenTheCardKnowsItsUnit() {
        final OeplReading reading = new OeplReading("x", "Hamburg", "")
                .withTemperature(13.44f, true);
        assertEquals("13.4°", reading.display(false));
        assertEquals("56.2°", reading.display(true));

        final OeplReading range = new OeplReading("x", "Heute", "").withRange(-2.4f, 15.6f);
        assertEquals("-2/16°", range.display(false));
        // the point is a point in every language: this is a figure on a panel, not a string in
        // a user interface, and a comma sits under the digits around it
        assertFalse(range.display(true).contains(","));
    }

    // ------------------------------------------------------------------ Home Assistant

    @Test
    public void theEntityListTakesWhatSomebodyWouldActuallyType() {
        final List<HomeAssistantClient.Spec> specs = HomeAssistantClient.parseEntities(
                "sensor.wohnzimmer_temperatur | Wohnzimmer\n"
                        + "  binary_sensor.haustuer  \n"
                        + "\n"
                        + "# eine Notiz\n"
                        + "kein eintrag\n");
        assertEquals(2, specs.size());
        assertEquals("sensor.wohnzimmer_temperatur", specs.get(0).entityId);
        assertEquals("Wohnzimmer", specs.get(0).label);
        assertEquals("binary_sensor.haustuer", specs.get(1).entityId);
        assertNotNull(specs.get(1));
    }

    @Test
    public void theEntityListIsCappedSoAWakeUpStaysShort() {
        final StringBuilder many = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            many.append("sensor.number_").append(i).append('\n');
        }
        assertEquals(HomeAssistantClient.MAX_ENTITIES,
                HomeAssistantClient.parseEntities(many.toString()).size());
    }

    @Test
    public void anAddressIsTakenHoweverItWasTyped() {
        assertEquals("http://homeassistant.local:8123",
                HomeAssistantClient.normalise("homeassistant.local:8123"));
        assertEquals("http://192.168.1.4:8123",
                HomeAssistantClient.normalise("  http://192.168.1.4:8123/  "));
        assertEquals("https://example.duckdns.org",
                HomeAssistantClient.normalise("https://example.duckdns.org///"));
    }

    @Test
    public void anEntityStateIsShownTheWayItWouldBeRead() {
        assertEquals("21.8 °C", new HomeAssistantClient.Entity("sensor.x", "x",
                "21.799999713897705", "°C", "temperature").formatted());
        assertEquals("8 kWh", new HomeAssistantClient.Entity("sensor.y", "y", "8.0", "kWh",
                "energy").formatted());
        assertEquals("on", new HomeAssistantClient.Entity("switch.z", "z", "on", "", "")
                .formatted());
    }

    @Test
    public void anEntityGetsTheSymbolItsOwnerWouldExpect() {
        assertEquals(OeplGlyphs.Glyph.BATTERY,
                OeplGlyphs.forEntity("sensor.phone_battery", "battery", "%"));
        assertEquals(OeplGlyphs.Glyph.DOOR,
                OeplGlyphs.forEntity("binary_sensor.front_door", "door", ""));
        assertEquals(OeplGlyphs.Glyph.LAMP, OeplGlyphs.forEntity("light.kitchen", "", ""));
        assertEquals(OeplGlyphs.Glyph.BOLT,
                OeplGlyphs.forEntity("sensor.solar_now", "power", "W"));
        assertEquals(OeplGlyphs.Glyph.PERSON, OeplGlyphs.forEntity("person.anna", "", ""));
        // nothing to go by is still a symbol rather than a gap
        assertEquals(OeplGlyphs.Glyph.DOT, OeplGlyphs.forEntity("sensor.whatever", "", ""));
    }

    // ------------------------------------------------------------------ symbols and text

    /**
     * A symbol and the text beside it, over every panel the catalogue knows.
     * <p>
     * The layout audit compares text against text, which leaves the pictograms out: the ones
     * drawn as shapes rather than set from the icon face are not text runs. So this draws the
     * dashboard cards with readings whose symbols are all shapes - the layout does not care
     * which symbol it is, only how wide it comes out - and checks that none of them lands on
     * the figure or the caption it belongs to.
     */
    @Test
    public void aSymbolNeverLandsOnTheTextBesideIt() {
        final List<String> problems = new ArrayList<>();
        for (final int[] shape : OeplLayoutAuditTest.shapes()) {
            for (final CardContent card : shapeOnlyCards()) {
                final OeplInkRecorder recorder = drawContent(card, shape[0], shape[1]);
                for (final OeplInkRecorder.Run icon : recorder.runs()) {
                    if (!isShapeGlyph(icon)) {
                        continue;
                    }
                    for (final OeplInkRecorder.Run text : recorder.textRuns()) {
                        final float shared = overlap(icon.box, text.box);
                        final float smaller = Math.min(icon.box.width() * icon.box.height(),
                                text.area());
                        if (shared >= 2f && shared > smaller * 0.05f) {
                            problems.add(String.format("%dx%d  %s on %s", shape[0], shape[1],
                                    icon.what, text));
                        }
                    }
                }
            }
        }
        assertEquals("a symbol landed on the text beside it: "
                        + String.join(" | ",
                        problems.subList(0, Math.min(10, problems.size()))),
                0, problems.size());
    }

    /** The three dashboard cards, filled with readings whose symbols are all drawn shapes. */
    private List<CardContent> shapeOnlyCards() {
        final List<OeplReading> readings = new ArrayList<>();
        final OeplGlyphs.Glyph[] shapes = {OeplGlyphs.Glyph.BELL, OeplGlyphs.Glyph.BOLT,
                OeplGlyphs.Glyph.PERSON, OeplGlyphs.Glyph.LAMP, OeplGlyphs.Glyph.CLOCK,
                OeplGlyphs.Glyph.LOCK, OeplGlyphs.Glyph.ON, OeplGlyphs.Glyph.HOUSE};
        for (int i = 0; i < shapes.length; i++) {
            final OeplReading reading = new OeplReading(i < 4 ? "Haus" : "Geraete",
                    i % 2 == 0 ? "Waschmaschine im Keller hinten links" : "PV",
                    i % 2 == 0 ? "1234.5 kWh" : "8")
                    .withShape(shapes[i]);
            if (i % 3 == 0) {
                reading.withPercent(100);
            }
            readings.add(reading);
        }
        return Arrays.asList(new OeplGridCard(readings, "Kueche, Esszimmer und Flur oben"),
                new OeplListCard(readings), new OeplHeroCard(readings, OeplDemo.events()));
    }

    private boolean isShapeGlyph(final OeplInkRecorder.Run run) {
        // the ops only a pictogram uses: a frame is a roundrect, a bar a rect, a rule a line
        return "circle".equals(run.what) || "arc".equals(run.what) || "path".equals(run.what);
    }

    private float overlap(final RectF a, final RectF b) {
        final float width = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        final float height = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        return width <= 0 || height <= 0 ? 0f : width * height;
    }

    // ------------------------------------------------------------------ helpers

    private OeplInkRecorder draw(final String id, final int width, final int height) {
        return drawContent(OeplDemo.card(id), width, height);
    }

    private OeplInkRecorder drawContent(final CardContent content, final int width,
                                        final int height) {
        final Bitmap sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
        recorder.drawColor(0xffffffff);
        content.draw(recorder, new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                OEPLImageCodec.RED, false), new RectF(0, 0, width, height));
        return recorder;
    }

    /** The tallest piece of text the card drew, which is the figure a tile is there for. */
    private float biggestFigure(final String id, final int width, final int height) {
        float biggest = 0f;
        for (final OeplInkRecorder.Run run : draw(id, width, height).textRuns()) {
            biggest = Math.max(biggest, run.box.height());
        }
        return biggest;
    }
}
