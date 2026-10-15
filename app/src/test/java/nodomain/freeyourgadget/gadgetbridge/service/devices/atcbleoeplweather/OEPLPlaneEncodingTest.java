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

import org.junit.Test;

/**
 * Decodes the packed planes the way the tag firmware does and checks that the colours come back.
 * <p>
 * The tag reads two bits per pixel as: accent/black 00 white, 01 black, 10 red, 11 yellow.
 * The planes go out in exactly that polarity - the same as the vendor web tool sends - and the
 * optional invert flags here are the manual override, not a compensation for the panel.
 */
public class OEPLPlaneEncodingTest {

    private static final int W = 16;
    private static final int H = 2;

    /** The tag side of the contract, written out literally. */
    private static int[] decodeLikeFirmware(final byte[] planes, final int colorCount,
                                            final boolean accentIsYellow,
                                            final boolean blackInvert, final boolean secondInvert) {
        final int planeBytes = (W * H) / 8;
        final int[] out = new int[W * H];
        final boolean twoPlanes = colorCount > 2;
        for (int i = 0; i < W * H; i++) {
            final int byteIndex = i / 8;
            final int mask = 1 << (7 - (i % 8));

            int black = planes[byteIndex] & 0xff;
            if (blackInvert) {
                black = ~black;
            }
            int accent = twoPlanes ? planes[planeBytes + byteIndex] & 0xff : 0x00;
            if (secondInvert && twoPlanes) {
                accent = ~accent;
            }
            final boolean b1 = (black & mask) != 0;
            final boolean b2 = (accent & mask) != 0;

            if (colorCount >= 4) {
                if (!b2 && !b1) {
                    out[i] = OEPLImageCodec.WHITE;
                } else if (!b2) {
                    out[i] = OEPLImageCodec.BLACK;
                } else if (!b1) {
                    out[i] = OEPLImageCodec.RED;
                } else {
                    out[i] = OEPLImageCodec.YELLOW;
                }
            } else if (colorCount == 3) {
                out[i] = b2 ? (accentIsYellow ? OEPLImageCodec.YELLOW : OEPLImageCodec.RED)
                        : (b1 ? OEPLImageCodec.BLACK : OEPLImageCodec.WHITE);
            } else {
                out[i] = b1 ? OEPLImageCodec.BLACK : OEPLImageCodec.WHITE;
            }
        }
        return out;
    }

    private static int[] pattern(final int[] palette) {
        final int[] pixels = new int[W * H];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = palette[i % palette.length];
        }
        return pixels;
    }

    private void roundTrip(final int colorCount, final boolean accentIsYellow,
                           final boolean blackInvert, final boolean secondInvert) {
        final int[] palette = OEPLImageCodec.palette(colorCount, accentIsYellow);
        final int[] pixels = pattern(palette);
        final byte[] planes = OEPLImageCodec.packPlanes(pixels.clone(), W, H, colorCount,
                accentIsYellow, blackInvert, secondInvert);

        assertEquals("plane bytes", (W * H / 8) * (colorCount > 2 ? 2 : 1), planes.length);

        final int[] decoded = decodeLikeFirmware(planes, colorCount, accentIsYellow,
                blackInvert, secondInvert);
        for (int i = 0; i < pixels.length; i++) {
            assertEquals(String.format(
                            "colours=%d yellowAccent=%s blackInvert=%s secondInvert=%s pixel %d",
                            colorCount, accentIsYellow, blackInvert, secondInvert, i),
                    String.format("%06x", pixels[i] & 0xffffff),
                    String.format("%06x", decoded[i] & 0xffffff));
        }
    }

    @Test
    public void everyPanelFlavourSurvivesTheRoundTrip() {
        for (final boolean invertBlack : new boolean[]{false, true}) {
            for (final boolean invertAccent : new boolean[]{false, true}) {
                roundTrip(2, false, invertBlack, invertAccent);
                roundTrip(3, false, invertBlack, invertAccent);
                roundTrip(3, true, invertBlack, invertAccent);
                roundTrip(4, false, invertBlack, invertAccent);
            }
        }
    }

    /** The polarity that actually reaches the wire, with the override left off. */
    @Test
    public void defaultPolarityMatchesTheVendorTool() {
        final int[] pixels = {
                OEPLImageCodec.WHITE, OEPLImageCodec.BLACK,
                OEPLImageCodec.RED, OEPLImageCodec.YELLOW,
        };
        final int[] padded = new int[W * H];
        java.util.Arrays.fill(padded, OEPLImageCodec.WHITE);
        System.arraycopy(pixels, 0, padded, 0, pixels.length);

        final byte[] planes = OEPLImageCodec.packPlanes(padded, W, H, 4, false, false, false);
        final int planeBytes = (W * H) / 8;
        // first nibble of the first byte: white 0, black 1, red 0, yellow 1 in the black plane
        assertEquals("black plane", 0b0101, (planes[0] & 0xff) >> 4);
        // and white 0, black 0, red 1, yellow 1 in the accent plane
        assertEquals("accent plane", 0b0011, (planes[planeBytes] & 0xff) >> 4);
    }

    @Test
    public void firmwareColourCountIsOneLessThanTheColours() {
        final OEPLDisplayConfig c = new OEPLDisplayConfig();
        c.colors = 1;
        assertEquals("black and white", 2, c.colorCount());
        c.colors = 2;
        assertEquals("one accent colour", 3, c.colorCount());
        c.colors = 3;
        assertEquals("black, white, red and yellow", 4, c.colorCount());
    }
}
