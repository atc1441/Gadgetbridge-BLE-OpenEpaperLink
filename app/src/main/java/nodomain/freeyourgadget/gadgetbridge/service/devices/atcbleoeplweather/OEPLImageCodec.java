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

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.Deflater;

/** Turns a rendered bitmap into the bit plane payload the tag expects, optionally compressed. */
public final class OEPLImageCodec {
    public static final int BLACK = 0x000000;
    public static final int WHITE = 0xffffff;
    public static final int RED = 0xff0000;
    public static final int YELLOW = 0xffff00;

    /** The tag inflates with a 4 KiB dictionary and rejects anything larger. */
    private static final int ZLIB_WINDOW = 4096;

    private OEPLImageCodec() {
    }

    /**
     * The colours a panel can show. Black always comes first and white always last, which
     * {@link #nearest} relies on when deciding what a grey pixel should become.
     *
     * @param colorCount the real number of colours, white included
     */
    public static int[] palette(final int colorCount, final boolean accentIsYellow) {
        if (colorCount <= 2) {
            return new int[]{BLACK, WHITE};
        }
        if (colorCount >= 4) {
            return new int[]{BLACK, RED, YELLOW, WHITE};
        }
        return new int[]{BLACK, accentIsYellow ? YELLOW : RED, WHITE};
    }

    /** Snaps every pixel to its nearest palette entry. Keeps rendered text crisp. */
    public static void quantize(final int[] pixels, final int[] palette) {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xff000000 | nearest(pixels[i], palette);
        }
    }

    /**
     * An ordered 4x4 Bayer dither. Coarser than error diffusion, but the pattern is fixed: a
     * picture that changes a little changes a little on the panel too, where diffusion would
     * redraw half the panel and cost a full refresh.
     */
    public static void ditherOrdered(final int[] pixels, final int[] palette, final int width,
                                     final int height) {
        // the classic 4x4 matrix, scaled to the distance between two palette steps
        final int[][] matrix = {
                {0, 8, 2, 10},
                {12, 4, 14, 6},
                {3, 11, 1, 9},
                {15, 7, 13, 5},
        };
        final int strength = 255 / Math.max(2, palette.length - 1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int idx = y * width + x;
                final int p = pixels[idx];
                final int bias = (matrix[y & 3][x & 3] - 7) * strength / 16;
                final int r = clamp(((p >> 16) & 0xff) + bias);
                final int g = clamp(((p >> 8) & 0xff) + bias);
                final int b = clamp((p & 0xff) + bias);
                pixels[idx] = 0xff000000 | nearest((r << 16) | (g << 8) | b, palette);
            }
        }
    }

    /** Floyd-Steinberg, for photographs where banding would look worse than noise. */
    public static void dither(final int[] pixels, final int[] palette, final int width, final int height) {
        final int stride = (width + 2) * 3;
        final int[] err = new int[stride * 2];
        int rowCur = 0;
        for (int y = 0; y < height; y++) {
            final int rowNext = (rowCur == 0) ? 1 : 0;
            Arrays.fill(err, rowNext * stride, (rowNext + 1) * stride, 0);
            for (int x = 0; x < width; x++) {
                final int idx = y * width + x;
                final int p = pixels[idx];
                final int base = rowCur * stride + (x + 1) * 3;
                final int r = clamp(((p >> 16) & 0xff) + err[base]);
                final int g = clamp(((p >> 8) & 0xff) + err[base + 1]);
                final int b = clamp((p & 0xff) + err[base + 2]);
                final int chosen = nearest((r << 16) | (g << 8) | b, palette);
                pixels[idx] = 0xff000000 | chosen;
                spread(err, stride, rowCur, rowNext, x,
                        r - ((chosen >> 16) & 0xff), g - ((chosen >> 8) & 0xff), b - (chosen & 0xff));
            }
            rowCur = rowNext;
        }
    }

    private static void spread(final int[] err, final int stride, final int rowCur, final int rowNext,
                               final int x, final int er, final int eg, final int eb) {
        add(err, rowCur * stride + (x + 2) * 3, er, eg, eb, 7);
        add(err, rowNext * stride + x * 3, er, eg, eb, 3);
        add(err, rowNext * stride + (x + 1) * 3, er, eg, eb, 5);
        add(err, rowNext * stride + (x + 2) * 3, er, eg, eb, 1);
    }

    private static void add(final int[] err, final int at, final int er, final int eg, final int eb, final int w) {
        err[at] += er * w / 16;
        err[at + 1] += eg * w / 16;
        err[at + 2] += eb * w / 16;
    }

    private static int clamp(final int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Below this chroma a pixel counts as grey and may only become black or white. */
    private static final int CHROMA_THRESHOLD = 60;

    private static int nearest(final int pixel, final int[] palette) {
        final int r = (pixel >> 16) & 0xff;
        final int g = (pixel >> 8) & 0xff;
        final int b = pixel & 0xff;
        final int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        final int luminance = (3 * r + 6 * g + b) / 10;

        // Antialiased edges are grey, and plain euclidean distance puts mid grey almost exactly
        // between white and a saturated accent. Decide greys on brightness alone so that text
        // edges never bleed into the accent colour.
        if (chroma < CHROMA_THRESHOLD) {
            return luminance < 128 ? palette[0] : palette[palette.length - 1];
        }

        int best = palette[0];
        int bestDist = Integer.MAX_VALUE;
        for (final int c : palette) {
            final int dr = r - ((c >> 16) & 0xff);
            final int dg = g - ((c >> 8) & 0xff);
            final int db = b - (c & 0xff);
            final int dist = 3 * dr * dr + 6 * dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    /**
     * Packs quantized pixels into one or two 1 bpp planes, MSB first, row major.
     * <p>
     * The tag combines the two planes into the panel colour like this, after it has applied its
     * own inversion flags:
     * <pre>
     *   accent black   colour
     *     0     0      white
     *     0     1      black
     *     1     0      red
     *     1     1      yellow
     * </pre>
     * On a panel with a single accent colour the accent plane simply selects that colour.
     * <p>
     * The planes go out in exactly this polarity, which is what the vendor web tool sends and
     * what the panels actually show. The tag also carries screen_color_black_invert flags, but
     * compensating for those on top turns the picture into a negative, so they are left alone;
     * the two parameters here are the manual override for a panel that disagrees.
     *
     * @param colorCount real number of colours, white included
     */
    public static byte[] packPlanes(final int[] pixels, final int width, final int height,
                                    final int colorCount, final boolean accentIsYellow,
                                    final boolean invertBlackPlane, final boolean invertAccentPlane) {
        final int planes = colorCount > 2 ? 2 : 1;
        final int planeBytes = (width * height) / 8;
        final byte[] out = new byte[planeBytes * planes];
        final boolean fourColor = colorCount >= 4;
        final int accent = accentIsYellow ? YELLOW : RED;

        int blackAt = 0;
        int accentAt = planeBytes;
        for (int y = 0; y < height; y++) {
            int bit = 0;
            int blackByte = 0;
            int accentByte = 0;
            for (int x = 0; x < width; x++) {
                final int px = pixels[y * width + x] & 0xffffff;
                final boolean blackBit;
                final boolean accentBit;
                if (fourColor) {
                    blackBit = px == BLACK || px == YELLOW;
                    accentBit = px == RED || px == YELLOW;
                } else {
                    blackBit = px == BLACK;
                    accentBit = planes > 1 && px == accent;
                }
                final int mask = 1 << (7 - bit);
                if (blackBit != invertBlackPlane) {
                    blackByte |= mask;
                }
                if (accentBit != invertAccentPlane) {
                    accentByte |= mask;
                }
                if (++bit == 8) {
                    out[blackAt++] = (byte) blackByte;
                    if (planes > 1) {
                        out[accentAt++] = (byte) accentByte;
                    }
                    bit = 0;
                    blackByte = 0;
                    accentByte = 0;
                }
            }
        }
        return out;
    }

    /** Prefixes the plane data with the small header the tag decompressor expects, then deflates. */
    public static byte[] compressedPayload(final byte[] planeData, final int width, final int height,
                                           final int planes) {
        final byte[] raw = new byte[6 + planeData.length];
        raw[0] = 6;
        raw[1] = (byte) (width & 0xff);
        raw[2] = (byte) ((width >> 8) & 0xff);
        raw[3] = (byte) (height & 0xff);
        raw[4] = (byte) ((height >> 8) & 0xff);
        raw[5] = (byte) planes;
        System.arraycopy(planeData, 0, raw, 6, planeData.length);

        final byte[] compressed = deflate4k(raw);
        final byte[] out = new byte[4 + compressed.length];
        out[0] = (byte) (raw.length & 0xff);
        out[1] = (byte) ((raw.length >> 8) & 0xff);
        out[2] = (byte) ((raw.length >> 16) & 0xff);
        out[3] = (byte) ((raw.length >> 24) & 0xff);
        System.arraycopy(compressed, 0, out, 4, compressed.length);
        return out;
    }

    /**
     * A zlib stream the tag can inflate with its 4 KiB dictionary.
     * {@link Deflater} always advertises a 32 KiB window and offers no way to shrink it, so the
     * compression history is reset with FULL_FLUSH every 4096 input bytes - no back reference can
     * then reach further than the window - and the two header bytes are written by hand.
     */
    public static byte[] deflate4k(final byte[] raw) {
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
        out.write(0x48); // CM = 8 (deflate), CINFO = 4, so the window is 256 << 4 = 4096
        out.write(0x0d); // FCHECK chosen so that (CMF << 8 | FLG) % 31 == 0
        final byte[] buf = new byte[4096];
        try {
            for (int off = 0; off < raw.length; off += ZLIB_WINDOW) {
                deflater.setInput(raw, off, Math.min(ZLIB_WINDOW, raw.length - off));
                int n;
                do {
                    n = deflater.deflate(buf, 0, buf.length, Deflater.FULL_FLUSH);
                    out.write(buf, 0, n);
                } while (n == buf.length);
            }
            deflater.finish();
            while (!deflater.finished()) {
                out.write(buf, 0, deflater.deflate(buf, 0, buf.length));
            }
        } finally {
            deflater.end();
        }
        final Adler32 adler = new Adler32();
        adler.update(raw);
        final long sum = adler.getValue();
        out.write((int) (sum >> 24) & 0xff);
        out.write((int) (sum >> 16) & 0xff);
        out.write((int) (sum >> 8) & 0xff);
        out.write((int) sum & 0xff);
        return out.toByteArray();
    }

    public static int[] pixelsOf(final Bitmap bitmap) {
        final int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }
}
