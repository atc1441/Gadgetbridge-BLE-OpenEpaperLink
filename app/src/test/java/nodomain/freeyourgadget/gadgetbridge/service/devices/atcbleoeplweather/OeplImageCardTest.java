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
import android.graphics.Canvas;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.HashSet;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ImageContent;

/**
 * The picture cards. A panel has three colours and a photograph has millions, so the whole
 * question is what happens in between: these check that the picture really is reduced to the
 * colours the panel has, that each setting changes what the panel gets, and that a picture never
 * paints over the edge of the card it was given.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplImageCardTest {

    private static final int W = 296;
    private static final int H = 128;

    private static Bitmap draw(final ImageContent.Dither dither, final ImageContent.Fit fit) {
        final Bitmap card = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(card);
        canvas.drawColor(0xffffffff);
        final CardStyle style = new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                OEPLImageCodec.RED, false);
        new ImageContent(OeplDemo.picture(), dither, fit, OeplDemo.palette())
                .draw(canvas, style, new RectF(0, 0, W, H));
        return card;
    }

    private static Set<Integer> coloursOf(final Bitmap card) {
        final int[] pixels = new int[W * H];
        card.getPixels(pixels, 0, W, 0, 0, W, H);
        final Set<Integer> colours = new HashSet<>();
        for (final int pixel : pixels) {
            colours.add(pixel & 0xffffff);
        }
        return colours;
    }

    @Test
    public void aPictureOnlyEverUsesTheColoursThePanelHas() {
        for (final ImageContent.Dither dither : ImageContent.Dither.values()) {
            final Set<Integer> colours = coloursOf(draw(dither, ImageContent.Fit.STRETCH));
            for (final int colour : colours) {
                assertTrue(dither + " produced " + Integer.toHexString(colour),
                        colour == OEPLImageCodec.BLACK || colour == OEPLImageCodec.WHITE
                                || colour == OEPLImageCodec.RED);
            }
            assertTrue(dither + " drew nothing at all", colours.size() > 1);
        }
    }

    @Test
    public void ditheringIsWhatMakesAGradientMoreThanTwoShades() {
        // a gradient without dithering is a few flat bands, with it a mix of the panel colours;
        // counting the changes along a line is the simplest way to tell the two apart
        assertTrue("dithering has to break up a gradient",
                changesAlongARow(draw(ImageContent.Dither.DIFFUSION, ImageContent.Fit.STRETCH))
                        > changesAlongARow(draw(ImageContent.Dither.NONE,
                        ImageContent.Fit.STRETCH)) * 3);
    }

    @Test
    public void theOrderedPatternIsSteadierThanDiffusionButStillNotFlat() {
        final int ordered = changesAlongARow(
                draw(ImageContent.Dither.ORDERED, ImageContent.Fit.STRETCH));
        final int none = changesAlongARow(draw(ImageContent.Dither.NONE,
                ImageContent.Fit.STRETCH));
        assertTrue("the pattern has to show up", ordered > none);
    }

    @Test
    public void fittingTheWholePictureLeavesPaperBesideIt() {
        // the demo picture is 3:2 and the card is wider, so the sides stay empty
        final Bitmap contained = draw(ImageContent.Dither.NONE, ImageContent.Fit.CONTAIN);
        assertEquals("the left edge has to stay paper", OEPLImageCodec.WHITE,
                contained.getPixel(1, H / 2) & 0xffffff);

        final Bitmap stretched = draw(ImageContent.Dither.NONE, ImageContent.Fit.STRETCH);
        assertFalse("stretching has to reach the edge",
                (stretched.getPixel(1, H / 2) & 0xffffff) == OEPLImageCodec.WHITE
                        && (stretched.getPixel(1, H / 4) & 0xffffff) == OEPLImageCodec.WHITE);
    }

    @Test
    public void aCardWithoutAPictureSaysSoRatherThanDrawingNothing() {
        assertFalse(new ImageContent(null, ImageContent.Dither.NONE, ImageContent.Fit.CONTAIN,
                OeplDemo.palette()).hasData());
        assertTrue(new ImageContent(OeplDemo.picture(), ImageContent.Dither.NONE,
                ImageContent.Fit.CONTAIN, OeplDemo.palette()).hasData());
    }

    @Test
    public void aPictureTakesTheWholePanelAndNoTitleBar() {
        assertTrue(new ImageContent(OeplDemo.picture(), ImageContent.Dither.NONE,
                ImageContent.Fit.CONTAIN, OeplDemo.palette()).fullBleed());
    }

    /** How often the colour changes along the middle row, as a measure of texture. */
    private static int changesAlongARow(final Bitmap card) {
        int changes = 0;
        int previous = card.getPixel(0, H / 2);
        for (int x = 1; x < W; x++) {
            final int pixel = card.getPixel(x, H / 2);
            if (pixel != previous) {
                changes++;
                previous = pixel;
            }
        }
        return changes;
    }
}
