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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;

/**
 * The identification mode: the display's own Bluetooth name in the corner of every panel.
 * <p>
 * It exists for one situation - a shelf of panels that look exactly alike, one of which is not
 * updating - so the things worth checking are the ones that would make it useless there: that
 * a design cannot paint over it, that the corner is the one the reader is looking at rather
 * than the first corner of the panel buffer, and that a long name stays on the panel instead
 * of running off its edge.
 * <p>
 * Most cases draw over a content that paints every pixel black, so anything white in the
 * result is the tag and nothing else.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplNameTagTest {

    private static final int WHITE = 0xffffffff;
    private static final int BLACK = 0xff000000;
    private static final String NAME = "ATC_1A2B3C";

    /** A design of the kind that fills its whole area and leaves no corner to anybody. */
    private static class Blackout implements CardContent {
        private final boolean fullBleed;

        Blackout(final boolean fullBleed) {
            this.fullBleed = fullBleed;
        }

        @Nullable
        @Override
        public String title() {
            return "Blackout";
        }

        @Override
        public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
            final Paint fill = new Paint();
            fill.setColor(style.ink);
            canvas.drawRect(area, fill);
        }

        @Override
        public boolean fullBleed() {
            return fullBleed;
        }
    }

    private Bitmap render(@Nullable final String name, final int bufferWidth,
                          final int bufferHeight, final int rotation) {
        final CardRenderer.Options options = new CardRenderer.Options();
        options.nameTag = name;
        return new CardRenderer(options)
                .render(bufferWidth, bufferHeight, rotation, new Blackout(true));
    }

    /** The box every white pixel of the bitmap fits into, or null when there is none. */
    @Nullable
    private static Rect whiteBox(final Bitmap bitmap) {
        Rect box = null;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) != WHITE) {
                    continue;
                }
                if (box == null) {
                    box = new Rect(x, y, x + 1, y + 1);
                } else {
                    box.union(x, y, x + 1, y + 1);
                }
            }
        }
        return box;
    }

    /**
     * Pixels inside a box that are not white, which over a white block are the letters. Not
     * "black": the text is drawn with antialiasing, so at small sizes a glyph can consist of
     * nothing but greys.
     */
    private static int inkInside(final Bitmap bitmap, final Rect box) {
        int found = 0;
        for (int y = box.top; y < box.bottom; y++) {
            for (int x = box.left; x < box.right; x++) {
                if (bitmap.getPixel(x, y) != WHITE) {
                    found++;
                }
            }
        }
        return found;
    }

    @Test
    public void withTheModeOffNothingIsAddedToThePanel() {
        assertNull("the panel must be untouched while the mode is off",
                whiteBox(render(null, 384, 184, 0)));
        assertNull("an empty name is the same as no name",
                whiteBox(render("   ", 384, 184, 0)));
    }

    /**
     * A small box in the corner, over a design that lays out every pixel of the panel itself.
     * Small is the point: this is a note to whoever is standing in front of the display, so it
     * may cover the corner it sits in and nothing else.
     */
    @Test
    public void theBoxSitsInTheCornerOfADesignThatOwnsEveryPixel() {
        final Bitmap card = render(NAME, 384, 184, 0);
        final Rect box = whiteBox(card);
        assertNotNull("the box has to survive a design that fills the panel", box);
        assertTrue("left edge, was " + box.left, box.left <= 4);
        assertTrue("top edge, was " + box.top, box.top <= 4);
        assertEquals("a black border all around", BLACK, card.getPixel(0, 0));
        assertTrue("a note may not take half the panel: " + box,
                box.width() < 384 * 0.5f && box.height() < 184 * 0.25f);
        assertTrue("the letters themselves are missing", inkInside(card, box) > 20);

        // the rest of the design is left alone
        assertEquals("the box reaches across the panel", BLACK, card.getPixel(384 - 2, 2));
    }

    @Test
    public void theCornerIsTheOneTheReaderSeesAndNotTheFirstOneOfTheBuffer() {
        // this panel is read a quarter turn away from its buffer, which is the usual case
        final Bitmap card = render(NAME, 184, 384, 90);
        assertEquals(184, card.getWidth());
        assertEquals(384, card.getHeight());
        final Rect box = whiteBox(card);
        assertNotNull(box);
        // the reader's top left corner is the buffer's top right one once the card is turned
        assertTrue("the tag was drawn after the rotation: " + box, box.left > 184 / 2);
        assertTrue("and not against that edge: " + box, box.right >= 184 - 4);
        assertTrue("nor at the top of it: " + box, box.top <= 4);
    }

    @Test
    public void aNameFarTooLongForThePanelStaysOnIt() {
        // the smallest panels in the catalogue, and a name nobody would choose
        for (final int[] panel : new int[][]{{152, 152}, {122, 250}, {296, 128}}) {
            final String where = panel[0] + "x" + panel[1];
            final Bitmap card = render("Wohnzimmer_Fensterbank_Links_ATC_1A2B3C",
                    panel[0], panel[1], 0);
            final Rect box = whiteBox(card);
            assertNotNull("no tag at all on " + where, box);
            assertTrue("ran off the right edge of " + where + ": " + box,
                    box.right <= panel[0]);
            assertTrue("took more than a third of " + where + ": " + box,
                    box.height() < panel[1] / 3f);
            assertTrue("nothing readable left on " + where, inkInside(card, box) > 10);
        }
    }

    /**
     * Whatever the frame does, the box changes the card inside its own corner and nowhere
     * else: a tag that moved a card around would be a tag nobody dares leave switched on.
     */
    @Test
    public void everyFrameStyleGetsTheSameBoxAndNothingElseMoves() {
        for (final CardRenderer.Frame frame : CardRenderer.Frame.values()) {
            final Rect box = boxOf(296, 128);
            final Bitmap plain = framed(frame, null);
            final Bitmap tagged = framed(frame, NAME);
            int changedInside = 0;
            for (int y = 0; y < 128; y++) {
                // the clock lives in the top right corner and turns over on its own
                for (int x = 0; x < 296 * 0.6f; x++) {
                    if (plain.getPixel(x, y) == tagged.getPixel(x, y)) {
                        continue;
                    }
                    assertTrue("the " + frame + " frame changed outside the box at "
                            + x + "," + y, box.contains(x, y));
                    changedInside++;
                }
            }
            assertTrue("no box at all with the " + frame + " frame", changedInside > 20);
        }
    }

    /**
     * The one thing the tag may never do. A name the reader cannot match against the device
     * list - "Hamb…" where the display is called something longer - is worse than no tag at
     * all, so the layout has to put every character on the panel at every geometry the
     * catalogue knows, however long the name and however small the panel.
     */
    @Test
    public void theNameIsNeverShortened() {
        final StringBuilder grown = new StringBuilder();
        for (final OEPLTagTypes.TagType type : OEPLTagTypes.all().values()) {
            if (type.width < 64 || type.height < 64) {
                continue; // a segment display or a placeholder, not a panel anything is drawn on
            }
            for (final String name : new String[]{"A", "ATC_1A2B3C", "Hamburg",
                    "Hamburg Wohnzimmer", "Wohnzimmer_Fensterbank_Links",
                    // the longest a Bluetooth name can be in an advertisement
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ012"}) {
                for (final int[] panel : new int[][]{{type.width, type.height},
                        {type.height, type.width}}) {
                    final CardRenderer.Options options = new CardRenderer.Options();
                    options.nameTag = name;
                    final CardRenderer.NameTag tag = new CardRenderer(options)
                            .measureNameTag(panel[0], panel[1]);
                    assertNotNull(name + " on " + type.name, tag);
                    assertEquals("cut on " + type.name + " " + panel[0] + "x" + panel[1],
                            name, tag.written());
                    assertTrue("wider than " + type.name + ": " + tag.width,
                            tag.width <= panel[0] + 0.5f);
                    assertTrue("taller than half of " + type.name + ": " + tag.height,
                            tag.height <= panel[1] / 2f);
                }
            }
        }
        // and a name nobody could have, on the smallest panel there is
        grown.append("x");
        while (grown.length() < 64) {
            grown.append("x");
        }
        final CardRenderer.Options options = new CardRenderer.Options();
        options.nameTag = grown.toString();
        final CardRenderer.NameTag tag =
                new CardRenderer(options).measureNameTag(122, 122);
        assertNotNull(tag);
        assertEquals("even 64 characters have to fit somehow", grown.toString(), tag.written());
    }

    /** The corner the box covers, the way the renderer works it out. */
    private Rect boxOf(final int w, final int h) {
        final CardRenderer.Options options = new CardRenderer.Options();
        options.nameTag = NAME;
        final CardRenderer.NameTag tag = new CardRenderer(options).measureNameTag(w, h);
        assertNotNull(tag);
        return new Rect(0, 0, (int) Math.ceil(tag.width) + 1, (int) Math.ceil(tag.height) + 1);
    }

    private Bitmap framed(final CardRenderer.Frame frame, @Nullable final String name) {
        final CardRenderer.Options options = new CardRenderer.Options();
        options.frame = frame;
        options.nameTag = name;
        return new CardRenderer(options).render(296, 128, 0, new Blackout(false));
    }
}
