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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Weather symbols drawn from primitives rather than from bitmaps, so that one icon set stays
 * sharp on a 152x152 price tag and on a 960x768 panel alike. Shapes are outlined rather than
 * filled, which survives the one bit per plane quantisation far better than solid blobs.
 */
public final class WeatherIcons {
    public static final int CLEAR = 0;
    public static final int PARTLY_CLOUDY = 1;
    public static final int CLOUDY = 2;
    public static final int RAIN = 3;
    public static final int SNOW = 4;
    public static final int THUNDER = 5;
    public static final int FOG = 6;

    private WeatherIcons() {
    }

    /** Maps an OpenWeatherMap condition code onto the handful of symbols drawn here. */
    public static int forConditionCode(final int code) {
        if (code >= 200 && code < 300) {
            return THUNDER;
        }
        if (code >= 300 && code < 600) {
            return RAIN;
        }
        if (code >= 600 && code < 700) {
            return SNOW;
        }
        if (code >= 700 && code < 800) {
            return FOG;
        }
        if (code == 800) {
            return CLEAR;
        }
        if (code == 801 || code == 802) {
            return PARTLY_CLOUDY;
        }
        if (code > 802 && code < 900) {
            return CLOUDY;
        }
        return PARTLY_CLOUDY;
    }

    /**
     * Draws the symbol centred on cx/cy inside a box of the given size.
     *
     * @param night  draws a crescent instead of a sun where that makes sense
     * @param accent the panel accent colour, used for the parts that benefit from standing out
     */
    public static void draw(final Canvas canvas, final int icon, final boolean night,
                            final float cx, final float cy, final float size,
                            final int ink, final int accent) {
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, size * 0.07f));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(ink);

        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ink);

        switch (icon) {
            case CLEAR:
                if (night) {
                    crescent(canvas, cx, cy, size * 0.34f, ink);
                } else {
                    sun(canvas, cx, cy, size * 0.26f, stroke, fill, accent);
                }
                break;
            case PARTLY_CLOUDY:
                if (night) {
                    crescent(canvas, cx + size * 0.16f, cy - size * 0.18f, size * 0.20f, ink);
                } else {
                    sun(canvas, cx + size * 0.18f, cy - size * 0.20f, size * 0.15f, stroke, fill, accent);
                }
                cloud(canvas, cx - size * 0.08f, cy + size * 0.10f, size * 0.68f, stroke);
                break;
            case CLOUDY:
                cloud(canvas, cx - size * 0.10f, cy - size * 0.06f, size * 0.58f, stroke);
                cloud(canvas, cx + size * 0.08f, cy + size * 0.12f, size * 0.68f, stroke);
                break;
            case RAIN:
                cloud(canvas, cx, cy - size * 0.12f, size * 0.74f, stroke);
                for (int i = -1; i <= 1; i++) {
                    final float x = cx + i * size * 0.20f;
                    canvas.drawLine(x + size * 0.05f, cy + size * 0.22f,
                            x - size * 0.02f, cy + size * 0.42f, stroke);
                }
                break;
            case SNOW:
                cloud(canvas, cx, cy - size * 0.12f, size * 0.74f, stroke);
                for (int i = -1; i <= 1; i++) {
                    flake(canvas, cx + i * size * 0.20f, cy + size * 0.32f, size * 0.09f, stroke);
                }
                break;
            case THUNDER:
                cloud(canvas, cx, cy - size * 0.14f, size * 0.74f, stroke);
                bolt(canvas, cx, cy + size * 0.28f, size * 0.30f, accent);
                break;
            case FOG:
            default:
                cloud(canvas, cx, cy - size * 0.16f, size * 0.70f, stroke);
                for (int i = 0; i < 3; i++) {
                    final float y = cy + size * (0.20f + i * 0.13f);
                    final float half = size * (i % 2 == 0 ? 0.32f : 0.24f);
                    canvas.drawLine(cx - half, y, cx + half, y, stroke);
                }
                break;
        }
    }

    private static void sun(final Canvas canvas, final float cx, final float cy, final float r,
                            final Paint stroke, final Paint fill, final int accent) {
        final Paint disc = new Paint(fill);
        disc.setColor(accent);
        canvas.drawCircle(cx, cy, r, disc);
        final Paint ray = new Paint(stroke);
        ray.setStrokeWidth(Math.max(1.2f, r * 0.22f));
        for (int i = 0; i < 8; i++) {
            final double a = Math.PI * i / 4.0;
            final float sx = (float) (cx + Math.cos(a) * r * 1.45f);
            final float sy = (float) (cy + Math.sin(a) * r * 1.45f);
            final float ex = (float) (cx + Math.cos(a) * r * 1.95f);
            final float ey = (float) (cy + Math.sin(a) * r * 1.95f);
            canvas.drawLine(sx, sy, ex, ey, ray);
        }
    }

    private static void crescent(final Canvas canvas, final float cx, final float cy,
                                 final float r, final int ink) {
        // a filled disc with a second disc punched out of it, drawn via even-odd winding
        final Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addCircle(cx, cy, r, Path.Direction.CW);
        path.addCircle(cx + r * 0.42f, cy - r * 0.34f, r * 0.88f, Path.Direction.CW);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink);
        canvas.drawPath(path, p);
    }

    private static void cloud(final Canvas canvas, final float cx, final float cy,
                              final float w, final Paint stroke) {
        final float h = w * 0.62f;
        final Path path = new Path();
        final RectF left = new RectF(cx - w * 0.50f, cy - h * 0.18f, cx - w * 0.04f, cy + h * 0.50f);
        final RectF mid = new RectF(cx - w * 0.32f, cy - h * 0.52f, cx + w * 0.22f, cy + h * 0.42f);
        final RectF right = new RectF(cx + w * 0.04f, cy - h * 0.22f, cx + w * 0.50f, cy + h * 0.50f);
        path.addArc(left, 120f, 180f);
        path.addArc(mid, 180f, 190f);
        path.addArc(right, 280f, 170f);
        path.moveTo(cx - w * 0.44f, cy + h * 0.46f);
        path.lineTo(cx + w * 0.44f, cy + h * 0.46f);
        canvas.drawPath(path, stroke);
    }

    private static void flake(final Canvas canvas, final float cx, final float cy,
                              final float r, final Paint stroke) {
        final Paint p = new Paint(stroke);
        p.setStrokeWidth(Math.max(1f, r * 0.42f));
        for (int i = 0; i < 3; i++) {
            final double a = Math.PI * i / 3.0;
            canvas.drawLine((float) (cx - Math.cos(a) * r), (float) (cy - Math.sin(a) * r),
                    (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), p);
        }
    }

    private static void bolt(final Canvas canvas, final float cx, final float cy,
                             final float h, final int accent) {
        final float w = h * 0.55f;
        final Path path = new Path();
        path.moveTo(cx + w * 0.15f, cy - h * 0.5f);
        path.lineTo(cx - w * 0.5f, cy + h * 0.08f);
        path.lineTo(cx - w * 0.05f, cy + h * 0.08f);
        path.lineTo(cx - w * 0.2f, cy + h * 0.5f);
        path.lineTo(cx + w * 0.5f, cy - h * 0.1f);
        path.lineTo(cx + w * 0.02f, cy - h * 0.1f);
        path.close();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        canvas.drawPath(path, p);
    }
}
