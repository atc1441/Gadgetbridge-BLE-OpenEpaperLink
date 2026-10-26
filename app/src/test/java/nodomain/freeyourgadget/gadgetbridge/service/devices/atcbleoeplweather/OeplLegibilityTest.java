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

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCanvas;

/**
 * Whether small text is still text once the panel has had it.
 * <p>
 * Everything else here measures where the ink lands. This measures what is left of it: a
 * display has two or three colours and a quantiser that snaps every pixel to one of them, and
 * an anti aliased letter is mostly grey at the sizes a tag writes its captions in. Smoothed
 * and then quantised, between two fifths and two thirds of the ink of a line is thrown away -
 * and what goes is the thin strokes, which is the difference between a caption and gravel.
 * <p>
 * So small text is drawn hard, and this is where that is held to. The numbers below were
 * measured rather than chosen: with smoothing on, a seven pixel line of the bundled face
 * loses 69 per cent of its ink and keeps 55 strokes; drawn hard it loses none and keeps 88.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplLegibilityTest {

    /** A line with thin strokes, round shapes, figures and a colon, in two faces' worth. */
    private static final String SAMPLE = "Werkstatt 13:00 Mo Elternabend";

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    /**
     * Nothing a card draws small may be lost to the quantiser. A tenth is the slack for the
     * odd round corner; two fifths, which is what smoothing cost, is a card that reaches the
     * panel as a different card.
     */
    @Test
    public void smallTextSurvivesThePanelsOwnQuantiser() {
        for (final float size : new float[]{7f, 8f, 9f, 10f, 11f, 12f}) {
            final float lost = lostToTheQuantiser(size);
            assertTrue(String.format("%.0f px text loses %.0f%% of its ink on the panel",
                    size, lost * 100f), lost < 0.1f);
        }
    }

    /** And the shapes of the letters survive with it: a line of text is not a line of ink. */
    @Test
    public void smallTextKeepsTheGapsBetweenItsStrokes() {
        for (final float size : new float[]{7f, 8f, 9f, 10f, 12f}) {
            final int strokes = strokesPerRow(size);
            assertTrue(String.format("%.0f px text came out of the quantiser as %d strokes a "
                    + "row, which is a smear rather than a word", size, strokes), strokes >= 8);
        }
    }

    /**
     * The rule itself, so that a paint built somewhere else cannot quietly go back to
     * smoothing the captions.
     */
    @Test
    public void aCardDrawsSmallTextHardAndLargeTextSmooth() {
        final OeplCanvas canvas = canvas(200, 100);
        assertTrue("a caption is drawn hard",
                !canvas.textPaint(OeplCanvas.SMOOTH_FROM - 1f, 0xff000000).isAntiAlias());
        assertTrue("and so is a pictogram beside it",
                !canvas.iconPaint(OeplCanvas.SMOOTH_FROM - 1f, 0xff000000).isAntiAlias());
        assertTrue("a figure large enough to carry its own edges is smoothed",
                canvas.textPaint(OeplCanvas.SMOOTH_FROM, 0xff000000).isAntiAlias());

        final CardStyle style = new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                OEPLImageCodec.RED, false);
        assertTrue("the framed cards follow the same rule",
                !style.paint(0xff000000, false, OeplCanvas.SMOOTH_FROM - 1f).isAntiAlias());
        assertTrue("both ways",
                style.paint(0xff000000, true, OeplCanvas.SMOOTH_FROM + 2f).isAntiAlias());
    }

    /**
     * And the whole way through: a card drawn on the smallest panels, quantised the way the
     * display quantises it, with every line of text it wrote still holding ink afterwards.
     */
    @Test
    public void everyLineOfARealCardIsStillThereAfterTheQuantiser() {
        final List<String> gone = new ArrayList<>();
        for (final String id : new String[]{"oepl_11", "panel_board", "oepl_11w", "panel_list",
                "panel_grid", "oepl_21", "oepl_9", "panel_month", "panel_wall"}) {
            for (final int[] shape : new int[][]{{296, 128}, {384, 184}, {152, 152},
                    {400, 300}}) {
                check(id, shape[0], shape[1], gone);
            }
        }
        assertTrue("lines the panel swallowed:\n" + String.join("\n", gone), gone.isEmpty());
    }

    private void check(final String id, final int w, final int h, final List<String> gone) {
        final Bitmap sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
        recorder.drawColor(0xffffffff);
        final CardContent content = OeplDemo.card(id);
        if (!content.hasData()) {
            return;
        }
        content.draw(recorder, new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                OEPLImageCodec.RED, false), new RectF(0, 0, w, h));

        final int[] pixels = new int[w * h];
        sheet.getPixels(pixels, 0, w, 0, 0, w, h);
        OEPLImageCodec.quantize(pixels, OEPLImageCodec.palette(3, false));

        for (final OeplInkRecorder.Run run : recorder.textRuns()) {
            if (run.icon || run.box.width() < 2f || run.box.height() < 2f) {
                continue;
            }
            final float share = inkShare(pixels, w, h, run.box);
            // a fifteenth of the box: a comma on its own is about that, and anything less is
            // a line that reached the panel as paper
            if (share < 0.065f) {
                gone.add(String.format("%s %dx%d  \"%s\" at %.1f px kept %.1f%% of its box",
                        id, w, h, run.what, run.size, share * 100f));
            }
        }
        sheet.recycle();
    }

    // ------------------------------------------------------------------ the measuring

    /** How much of the ink a line is drawn with the quantiser throws away. */
    private float lostToTheQuantiser(final float size) {
        final int[][] both = draw(size);
        int before = 0;
        int after = 0;
        for (int i = 0; i < both[0].length; i++) {
            if ((both[0][i] & 0xff) < 250) {
                before++;
            }
            if ((both[1][i] & 0xffffff) != 0xffffff) {
                after++;
            }
        }
        return before == 0 ? 1f : Math.max(0f, (before - after) / (float) before);
    }

    /** How many separate strokes a row of the quantised line holds, averaged over its rows. */
    private int strokesPerRow(final float size) {
        final int[][] both = draw(size);
        final int w = both[2][0];
        final int h = both[2][1];
        int strokes = 0;
        int rows = 0;
        for (int y = 0; y < h; y++) {
            int inRow = 0;
            boolean previous = false;
            for (int x = 0; x < w; x++) {
                final boolean dark = (both[1][y * w + x] & 0xffffff) != 0xffffff;
                if (dark && !previous) {
                    inRow++;
                }
                previous = dark;
            }
            if (inRow > 0) {
                strokes += inRow;
                rows++;
            }
        }
        return rows == 0 ? 0 : strokes / rows;
    }

    /** The sample at a size, before and after the quantiser, with its size alongside. */
    private int[][] draw(final float size) {
        final OeplCanvas measuring = canvas(8, 8);
        final Paint paint = measuring.textPaint(size, 0xff000000);
        final Rect ink = new Rect();
        paint.getTextBounds(SAMPLE, 0, SAMPLE.length(), ink);
        final int w = Math.max(2, ink.width() + 8);
        final int h = Math.max(2, ink.height() + 8);
        final Bitmap sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xffffffff);
        canvas.drawText(SAMPLE, 4 - ink.left, 4 - ink.top, paint);
        final int[] before = new int[w * h];
        sheet.getPixels(before, 0, w, 0, 0, w, h);
        final int[] after = before.clone();
        OEPLImageCodec.quantize(after, OEPLImageCodec.palette(3, false));
        sheet.recycle();
        return new int[][]{before, after, {w, h}};
    }

    private float inkShare(final int[] pixels, final int w, final int h, final RectF box) {
        final int left = Math.max(0, (int) box.left);
        final int top = Math.max(0, (int) box.top);
        final int right = Math.min(w, (int) Math.ceil(box.right));
        final int bottom = Math.min(h, (int) Math.ceil(box.bottom));
        if (right <= left || bottom <= top) {
            return 1f;
        }
        int ink = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if ((pixels[y * w + x] & 0xffffff) != 0xffffff) {
                    ink++;
                }
            }
        }
        return ink / (float) ((right - left) * (bottom - top));
    }

    private OeplCanvas canvas(final int w, final int h) {
        final Bitmap sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        return new OeplCanvas(new Canvas(sheet), w, h, 0xff000000 | OEPLImageCodec.BLACK,
                0xff000000 | OEPLImageCodec.WHITE, 0xff000000 | OEPLImageCodec.RED);
    }
}
