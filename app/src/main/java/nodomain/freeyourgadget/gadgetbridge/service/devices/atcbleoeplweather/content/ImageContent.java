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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLImageCodec;

/**
 * A picture on the panel: one chosen from the phone, or one fetched from a URL.
 * <p>
 * A photograph is the one thing a three colour panel cannot show by simply picking the nearest
 * colour per pixel - that turns a face into flat patches. Which is why the picture is reduced to
 * the panel's own colours here, with the dithering the user asked for, before it is drawn:
 * error diffusion for photographs, an ordered pattern for flat artwork that would otherwise
 * crawl with noise, and none at all for line art and logos that are already two colours.
 */
public class ImageContent implements CardContent {

    public enum Dither {
        /** Nearest colour per pixel. Right for logos, QR codes and line art. */
        NONE,
        /** Floyd-Steinberg error diffusion. Right for photographs. */
        DIFFUSION,
        /** A fixed 4x4 pattern. Coarser, but it never smears and it never crawls. */
        ORDERED
    }

    public enum Fit {
        /** The whole picture, with paper beside or above it. */
        CONTAIN,
        /** Fills the panel, cutting what does not fit. */
        COVER,
        /** Stretches the picture to the panel, aspect ratio be damned. */
        STRETCH
    }

    @Nullable
    private final Bitmap picture;
    private final Dither dither;
    private final Fit fit;
    private final int[] palette;

    public ImageContent(@Nullable final Bitmap picture, final Dither dither, final Fit fit,
                        final int[] palette) {
        this.picture = picture;
        this.dither = dither == null ? Dither.DIFFUSION : dither;
        this.fit = fit == null ? Fit.CONTAIN : fit;
        this.palette = palette;
    }

    @Nullable
    @Override
    public String title() {
        return null;
    }

    @Override
    public boolean hasData() {
        return picture != null && !picture.isRecycled()
                && picture.getWidth() > 0 && picture.getHeight() > 0;
    }

    @Override
    public boolean fullBleed() {
        return true;
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (!hasData()) {
            return;
        }
        final int width = Math.max(1, Math.round(area.width()));
        final int height = Math.max(1, Math.round(area.height()));

        // scaled into a surface of exactly the panel's size first: dithering only means
        // anything once the pixels are the ones the panel will actually light up
        final Bitmap scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas into = new Canvas(scaled);
        into.drawColor(style.paper);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        into.drawBitmap(picture, source(width, height), target(width, height), paint);

        final int[] pixels = new int[width * height];
        scaled.getPixels(pixels, 0, width, 0, 0, width, height);
        switch (dither) {
            case DIFFUSION:
                OEPLImageCodec.dither(pixels, palette, width, height);
                break;
            case ORDERED:
                OEPLImageCodec.ditherOrdered(pixels, palette, width, height);
                break;
            case NONE:
            default:
                OEPLImageCodec.quantize(pixels, palette);
                break;
        }
        scaled.setPixels(pixels, 0, width, 0, 0, width, height);
        canvas.drawBitmap(scaled, area.left, area.top, null);
    }

    /** Which part of the picture is used, which is all of it unless it is being cropped. */
    @Nullable
    private Rect source(final int width, final int height) {
        if (fit != Fit.COVER || picture == null) {
            return null;
        }
        final float scale = Math.min(picture.getWidth() / (float) width,
                picture.getHeight() / (float) height);
        final int w = Math.max(1, Math.round(width * scale));
        final int h = Math.max(1, Math.round(height * scale));
        final int left = (picture.getWidth() - w) / 2;
        final int top = (picture.getHeight() - h) / 2;
        return new Rect(left, top, left + w, top + h);
    }

    /** Where it lands on the panel. */
    private RectF target(final int width, final int height) {
        if (picture == null || fit != Fit.CONTAIN) {
            return new RectF(0, 0, width, height);
        }
        final float scale = Math.min(width / (float) picture.getWidth(),
                height / (float) picture.getHeight());
        final float w = picture.getWidth() * scale;
        final float h = picture.getHeight() * scale;
        final float left = (width - w) / 2f;
        final float top = (height - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }
}
