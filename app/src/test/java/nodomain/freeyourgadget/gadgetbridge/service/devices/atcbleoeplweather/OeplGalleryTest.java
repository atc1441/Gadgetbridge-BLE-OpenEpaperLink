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
import android.graphics.RectF;
import android.graphics.Typeface;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;

/**
 * Draws every design onto contact sheets, one per panel shape, so that the layouts can be looked
 * at side by side instead of one file at a time. This is the tool the overlap hunt is done with:
 * things that collide are obvious on a sheet and invisible in an assertion.
 * <p>
 * The sheets land in app/build/oepl-gallery.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplGalleryTest {

    /** Canvas shapes, the way a card sees them: width, height, colours, columns on the sheet. */
    private static final int[][] SHAPES = {
            {384, 184, 4, 3},    // the 3.5" BWRY panel this was developed against
            {296, 128, 3, 4},    // the most common 2.9" tag
            {152, 152, 3, 6},    // a small square price tag
            {128, 296, 3, 8},    // the same tag stood on end
            {400, 300, 3, 3},    // a 4.2" panel
            {800, 480, 3, 2},    // a large panel
    };

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    @Test
    public void everyDesignGoesOntoAContactSheet() throws Exception {
        final List<String> ids = new ArrayList<>();
        for (final String id : OeplDemo.IDS) {
            ids.add(id);
        }
        for (final String id : OeplDemo.VARIANTS) {
            ids.add(id);
        }

        for (final int[] shape : SHAPES) {
            final int w = shape[0];
            final int h = shape[1];
            final int colors = shape[2];
            final int cols = shape[3];
            // keep a sheet readable: at most 15 cards on one, then start the next
            final int perSheet = Math.max(cols, cols * (int) Math.max(2, Math.min(6, 2400 / h)));
            int sheet = 1;
            for (int from = 0; from < ids.size(); from += perSheet, sheet++) {
                final List<String> page = ids.subList(from, Math.min(ids.size(), from + perSheet));
                write(contactSheet(page, w, h, colors, cols),
                        String.format("gallery_%dx%d_%d.png", w, h, sheet));
            }
        }
    }

    /**
     * Every design, at every shape, drawn into a sheet of paper that is larger than the card it
     * was given. Anything that lands in the margin is ink the layout put outside its own area -
     * on a real panel that is a smear over the title bar, over the status strip or over the
     * edge. The card frame clips as well, but a layout that needs the clip is a layout that has
     * lost a letter, so this is where it has to be caught.
     */
    @Test
    public void nothingIsDrawnOutsideTheAreaACardWasGiven() {
        final int margin = 12;
        final StringBuilder bad = new StringBuilder();
        for (final int[] shape : SHAPES) {
            final int w = shape[0];
            final int h = shape[1];
            for (final String id : allIds()) {
                final Bitmap sheet = Bitmap.createBitmap(w + margin * 2, h + margin * 2,
                        Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(sheet);
                canvas.drawColor(0xffffffff);
                final CardStyle style = new CardStyle(OEPLImageCodec.BLACK,
                        OEPLImageCodec.WHITE, OEPLImageCodec.RED, false);
                final CardContent content = OeplDemo.card(id);
                if (!content.hasData()) {
                    continue;
                }
                content.draw(canvas, style, new RectF(margin, margin, margin + w, margin + h));

                final StringBuilder where = new StringBuilder();
                final int escaped = inkInMargin(sheet, margin, w, h, where);
                if (escaped > 0) {
                    bad.append(String.format("%s at %dx%d: %d px outside %s%n", id, w, h,
                            escaped, where));
                }
            }
        }
        assertEquals("ink outside the card:\n" + bad, 0, bad.length());
    }

    /** A design that draws nothing, or that floods the panel, is as broken as one that spills. */
    @Test
    public void everyDesignPutsAReadableAmountOfInkOnThePanel() {
        final StringBuilder bad = new StringBuilder();
        for (final int[] shape : SHAPES) {
            for (final String id : allIds()) {
                final Bitmap card = draw(id, shape[0], shape[1], shape[2]);
                final int[] pixels = new int[shape[0] * shape[1]];
                card.getPixels(pixels, 0, shape[0], 0, 0, shape[0], shape[1]);
                int ink = 0;
                for (final int pixel : pixels) {
                    if ((pixel & 0xffffff) != 0xffffff) {
                        ink++;
                    }
                }
                // a photograph may cover the whole panel; everything else is text and lines,
                // and a panel that is three quarters ink is a panel nobody can read
                final float ceiling = id.startsWith("image") ? 1f : 0.72f;
                final float share = ink / (float) pixels.length;
                if (share < 0.004f || share > ceiling) {
                    bad.append(String.format("%s at %dx%d: %.1f%% ink%n", id, shape[0],
                            shape[1], share * 100f));
                }
            }
        }
        assertEquals("unreadable amount of ink:\n" + bad, 0, bad.length());
    }

    private static List<String> allIds() {
        final List<String> ids = new ArrayList<>();
        for (final String id : OeplDemo.IDS) {
            ids.add(id);
        }
        for (final String id : OeplDemo.VARIANTS) {
            ids.add(id);
        }
        return ids;
    }

    /** Counts the pixels that are not paper in the frame around the card. */
    private static int inkInMargin(final Bitmap sheet, final int margin, final int w,
                                   final int h, final StringBuilder where) {
        final int sw = sheet.getWidth();
        final int sh = sheet.getHeight();
        final int[] pixels = new int[sw * sh];
        sheet.getPixels(pixels, 0, sw, 0, 0, sw, sh);
        int escaped = 0;
        for (int y = 0; y < sh; y++) {
            final boolean outsideRow = y < margin || y >= margin + h;
            for (int x = 0; x < sw; x++) {
                if (!outsideRow && x >= margin && x < margin + w) {
                    continue;
                }
                // anti aliasing leaves a hairline of very light grey, which no quantiser keeps
                if ((pixels[y * sw + x] & 0xffffff) < 0xe0e0e0) {
                    escaped++;
                    if (escaped <= 6) {
                        where.append(String.format("(%d,%d)", x - margin, y - margin));
                    }
                }
            }
        }
        return escaped;
    }

    /** Renders one card the way it reaches the panel, including the quantiser. */
    private static Bitmap draw(final String id, final int w, final int h, final int colors) {
        final CardRenderer.Options opt = new CardRenderer.Options();
        opt.accent = colors > 2 ? OEPLImageCodec.RED : OEPLImageCodec.BLACK;
        opt.batteryMillivolts = 3070;
        opt.panelTemperature = 23;

        final CardContent content = OeplDemo.card(id);
        final Bitmap card = new CardRenderer(opt).render(w, h, 0, content);
        final int[] pixels = OEPLImageCodec.pixelsOf(card);
        OEPLImageCodec.quantize(pixels, OEPLImageCodec.palette(colors, false));
        card.setPixels(pixels, 0, w, 0, 0, w, h);
        return card;
    }

    private static Bitmap contactSheet(final List<String> ids, final int w, final int h,
                                       final int colors, final int cols) {
        final int label = 16;
        final int gap = 6;
        final int rows = (ids.size() + cols - 1) / cols;
        final int sheetW = cols * (w + gap) + gap;
        final int sheetH = rows * (h + label + gap) + gap;

        final Bitmap sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xff606060);

        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xffffffff);
        text.setTextSize(12f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        final Paint border = new Paint();
        border.setColor(0xff00c0ff);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);

        for (int i = 0; i < ids.size(); i++) {
            final int col = i % cols;
            final int row = i / cols;
            final float x = gap + col * (w + gap);
            final float y = gap + row * (h + label + gap);
            canvas.drawText(ids.get(i), x, y + 12, text);
            canvas.drawBitmap(draw(ids.get(i), w, h, colors), x, y + label, null);
            canvas.drawRect(x - 0.5f, y + label - 0.5f, x + w + 0.5f, y + label + h + 0.5f,
                    border);
        }
        return sheet;
    }

    private static void write(final Bitmap b, final String name) throws Exception {
        final File dir = new File("build/oepl-gallery");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(dir, name))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
