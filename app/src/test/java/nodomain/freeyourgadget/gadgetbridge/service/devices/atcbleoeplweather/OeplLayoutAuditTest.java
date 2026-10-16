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
import android.graphics.RectF;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;

/**
 * Every design, on every panel geometry the tag catalogue knows, in both orientations - checked
 * for the two faults a layout written for one shape keeps producing on another: ink outside the
 * area the card was given, and two pieces of text in the same place.
 * <p>
 * The contact sheets in {@link OeplGalleryTest} are for looking at a handful of shapes; this is
 * the sweep across all of them, which is far more than anybody can look at. It works on the
 * boxes the glyphs really occupy rather than on pixels, because overlapping ink and ordinary
 * ink are the same pixels, and because a clip hides an overflow from a pixel check while the
 * letter it ate is still missing.
 * <p>
 * The full report is written to app/build/oepl-gallery/layout-audit.txt.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplLayoutAuditTest {

    /** A panel smaller than this is a segment display, not something a card is drawn on. */
    private static final int SMALLEST_PANEL = 64;

    /** How far outside its area a run may reach before it counts: anti aliasing, rounding. */
    private static final float SLACK = 1.0f;

    /**
     * Two boxes may touch - a descender reaching into the line below is normal typography - but
     * once they share this much of the smaller one, the two pieces of text are unreadable.
     * Both numbers are deliberately close to nothing: every design currently passes at a single
     * square pixel, and the little that is allowed here is for the rounding a different build
     * of a font face brings with it, not for a collision anybody could see.
     */
    private static final float OVERLAP_SHARE = 0.02f;
    private static final float OVERLAP_PIXELS = 2.0f;

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    /**
     * Every geometry in the catalogue, in both orientations. The card is drawn at the panel's
     * own size and turned afterwards to land in the buffer, so these are the shapes a layout is
     * really handed - and the rotation setting lets a user hand it the transpose as well.
     */
    static List<int[]> shapes() {
        final Set<String> seen = new LinkedHashSet<>();
        final List<int[]> shapes = new ArrayList<>();
        for (final OEPLTagTypes.TagType type : OEPLTagTypes.all().values()) {
            if (type.width < SMALLEST_PANEL || type.height < SMALLEST_PANEL) {
                continue;
            }
            for (final int[] shape : new int[][]{{type.width, type.height},
                    {type.height, type.width}}) {
                if (seen.add(shape[0] + "x" + shape[1])) {
                    shapes.add(shape);
                }
            }
        }
        return shapes;
    }

    static List<String> designs() {
        final List<String> ids = new ArrayList<>();
        for (final String id : OeplDemo.IDS) {
            ids.add(id);
        }
        for (final String id : OeplDemo.VARIANTS) {
            ids.add(id);
        }
        return ids;
    }

    @Test
    public void noDesignLeavesItsAreaOrOverlapsItselfOnAnyPanel() throws Exception {
        final List<int[]> shapes = shapes();
        final List<String> designs = designs();
        assertTrue("the catalogue should cover far more than a handful of shapes",
                shapes.size() > 40);

        final List<String> escapes = new ArrayList<>();
        final List<String> collisions = new ArrayList<>();
        int drawn = 0;

        for (final int[] shape : shapes) {
            for (final String id : designs) {
                final CardContent content = OeplDemo.card(id);
                if (!content.hasData()) {
                    continue;
                }
                // an area that does not start at the origin, so a content that forgets to
                // translate is caught here rather than on a user's panel
                final float ox = 7f;
                final float oy = 5f;
                final Bitmap sheet = Bitmap.createBitmap((int) ox + shape[0] + 9,
                        (int) oy + shape[1] + 9, Bitmap.Config.ARGB_8888);
                final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
                recorder.drawColor(0xffffffff);
                final RectF area = new RectF(ox, oy, ox + shape[0], oy + shape[1]);
                final CardStyle style = new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE,
                        OEPLImageCodec.RED, false);
                content.draw(recorder, style, area);
                drawn++;

                final String where = String.format("%-18s %4dx%-4d", id, shape[0], shape[1]);
                for (final OeplInkRecorder.Run run : recorder.runs()) {
                    final String side = outside(run.wanted, area);
                    if (side != null) {
                        escapes.add(where + "  " + side + "  " + run);
                    }
                }
                final List<OeplInkRecorder.Run> texts = recorder.textRuns();
                for (int i = 0; i < texts.size(); i++) {
                    for (int j = i + 1; j < texts.size(); j++) {
                        final float shared = overlap(texts.get(i).box, texts.get(j).box);
                        final float smaller = Math.min(texts.get(i).area(), texts.get(j).area());
                        if (shared >= OVERLAP_PIXELS && shared > smaller * OVERLAP_SHARE) {
                            collisions.add(String.format("%s  %.0f px²  %s  <>  %s", where,
                                    shared, texts.get(i), texts.get(j)));
                        }
                    }
                }
                sheet.recycle();
            }
        }

        report(shapes, designs, drawn, escapes, collisions);
        assertEquals(summary("ink outside the card", escapes), 0, escapes.size());
        assertEquals(summary("text on top of text", collisions), 0, collisions.size());
    }

    /**
     * The same two faults, but on the composed card: a framed content is given the body between
     * the title bar and the status strip, and all three frames leave a different body behind.
     * Fewer shapes, because what differs here is the frame rather than the geometry.
     */
    @Test
    public void noFramedDesignWritesIntoTheTitleBarOrTheStatusStrip() {
        final int[][] shapes = {{384, 184}, {296, 128}, {152, 152}, {128, 296}, {800, 480},
                {160, 80}};
        final List<String> problems = new ArrayList<>();

        for (final CardRenderer.Frame frame : CardRenderer.Frame.values()) {
            for (final int[] shape : shapes) {
                for (final String id : designs()) {
                    final CardContent content = OeplDemo.card(id);
                    if (!content.hasData() || content.fullBleed()) {
                        continue;
                    }
                    final CardRenderer.Options options = new CardRenderer.Options();
                    options.frame = frame;
                    options.batteryMillivolts = 3070;
                    options.panelTemperature = 23;
                    final RectF body = new CardRenderer(options).bodyArea(shape[0], shape[1]);

                    final Bitmap sheet = Bitmap.createBitmap(shape[0], shape[1],
                            Bitmap.Config.ARGB_8888);
                    final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
                    recorder.drawColor(0xffffffff);
                    // the content alone, clipped to its body the way the renderer clips it, so
                    // that the frame's own title and status text are not mistaken for its ink
                    recorder.save();
                    recorder.clipRect(body);
                    final CardStyle style = new CardStyle(OEPLImageCodec.BLACK,
                            OEPLImageCodec.WHITE, OEPLImageCodec.RED, false);
                    content.draw(recorder, style, body);
                    recorder.restore();

                    for (final OeplInkRecorder.Run run : recorder.runs()) {
                        final String side = outside(run.wanted, body);
                        if (side != null) {
                            problems.add(String.format("%-14s %-5s %4dx%-4d %s  %s", id, frame,
                                    shape[0], shape[1], side, run));
                        }
                    }
                    sheet.recycle();
                }
            }
        }
        assertEquals(summary("content outside its body", problems), 0, problems.size());
    }

    /** Which edge a box crosses, or null when it stays inside. */
    private static String outside(final RectF box, final RectF area) {
        final StringBuilder sb = new StringBuilder();
        if (box.left < area.left - SLACK) {
            sb.append("left by ").append(Math.round(area.left - box.left)).append(' ');
        }
        if (box.top < area.top - SLACK) {
            sb.append("top by ").append(Math.round(area.top - box.top)).append(' ');
        }
        if (box.right > area.right + SLACK) {
            sb.append("right by ").append(Math.round(box.right - area.right)).append(' ');
        }
        if (box.bottom > area.bottom + SLACK) {
            sb.append("bottom by ").append(Math.round(box.bottom - area.bottom)).append(' ');
        }
        return sb.length() == 0 ? null : sb.toString().trim();
    }

    private static float overlap(final RectF a, final RectF b) {
        final float w = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        final float h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        return w <= 0f || h <= 0f ? 0f : w * h;
    }

    private static String summary(final String what, final List<String> found) {
        final StringBuilder sb = new StringBuilder(found.size() + " x " + what + ":\n");
        for (int i = 0; i < Math.min(40, found.size()); i++) {
            sb.append("  ").append(found.get(i)).append('\n');
        }
        if (found.size() > 40) {
            sb.append("  ... and ").append(found.size() - 40)
                    .append(" more, see app/build/oepl-gallery/layout-audit.txt\n");
        }
        return sb.toString();
    }

    private static void report(final List<int[]> shapes, final List<String> designs,
                               final int drawn, final List<String> escapes,
                               final List<String> collisions) throws Exception {
        final File dir = new File("build/oepl-gallery");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (PrintWriter out = new PrintWriter(new File(dir, "layout-audit.txt"), "UTF-8")) {
            out.printf("%d designs x %d panel geometries, %d cards drawn%n", designs.size(),
                    shapes.size(), drawn);
            final StringBuilder sb = new StringBuilder();
            for (final int[] shape : shapes) {
                sb.append(shape[0]).append('x').append(shape[1]).append(' ');
            }
            out.println("geometries: " + sb.toString().trim());
            out.println();
            out.println(escapes.size() + " runs of ink outside the card:");
            for (final String line : escapes) {
                out.println("  " + line);
            }
            out.println();
            out.println(collisions.size() + " collisions between two pieces of text:");
            for (final String line : collisions) {
                out.println("  " + line);
            }
        }
    }
}
