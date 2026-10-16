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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The sun's day as a diagram: an arc from where it came up to where it will go down, with a mark
 * where it stands right now, the two times at the ends of the horizon and the length of the day
 * written under it. After dark the mark gives way to the moon in its current phase.
 * <p>
 * The arc is drawn as a chain of short straight lines rather than as a curve. An e-paper panel
 * has no greys to smooth a curve with, so the two come out as the same staircase, and a chain of
 * lines keeps the drawing inside the box it was given without a path to clip.
 */
public class OeplSunCard extends OeplCard {

    private static final int SEGMENTS = 48;

    @Nullable
    private final OeplWeather weather;

    public OeplSunCard(@Nullable final OeplWeather weather) {
        super(true);
        this.weather = weather;
    }

    @Override
    public boolean hasData() {
        return weather != null && weather.sunrise > 0L && weather.sunset > weather.sunrise;
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (!hasData()) {
            return;
        }
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.03f);

        final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
        final String rise = clock.format(new Date(weather.sunrise * 1000L));
        final String set = clock.format(new Date(weather.sunset * 1000L));

        // the times sit on the baseline of the horizon, so their line is reserved first and the
        // arc gets what is left above it
        final float timeSize = c.sizeForHeight(Math.min(h * 0.17f, w * 0.1f), h * 0.26f, 7f);
        final float timeLine = timeSize > 0f ? c.lineHeight(timeSize) : 0f;
        final float lengthSize = c.sizeForHeight(Math.min(h * 0.14f, w * 0.085f), h * 0.22f, 7f);

        // The arc is never taller than it is half wide - on a tall tag a dome drawn to the
        // room available comes out as an egg, which is not the shape of a day. Whatever that
        // leaves over is shared above and below, so the diagram sits in the middle of the
        // panel instead of being pinned to its bottom edge.
        final float cx = w / 2f;
        final float radiusX = (w - pad * 2f) / 2f;
        final float gapAboveTimes = Math.max(1f, h * 0.012f);
        final float arcTop = Math.max(1f, h * 0.04f);
        final float radiusY = Math.max(4f, Math.min(radiusX,
                h - timeLine - gapAboveTimes - arcTop));
        final float block = radiusY + gapAboveTimes + timeLine;
        final float horizon = Math.max(arcTop, (h - block) / 2f) + radiusY;

        c.line(pad, horizon, w - pad, horizon, c.ink, Math.max(1f, h * 0.012f));

        // the arc itself, in the accent colour where the sun has already been
        final long now = System.currentTimeMillis() / 1000L;
        final float progress = clamp((now - weather.sunrise)
                / (float) (weather.sunset - weather.sunrise));
        // after dark nothing on the arc is current any more, so none of it is marked
        final boolean daylight = now >= weather.sunrise && now <= weather.sunset;
        float lastX = cx - radiusX;
        float lastY = horizon;
        for (int i = 1; i <= SEGMENTS; i++) {
            final float f = i / (float) SEGMENTS;
            final float x = cx - radiusX * (float) Math.cos(Math.PI * f);
            final float y = horizon - radiusY * (float) Math.sin(Math.PI * f);
            c.line(lastX, lastY, x, y, daylight && f <= progress ? c.accent : c.ink,
                    Math.max(1f, h * 0.008f));
            lastX = x;
            lastY = y;
        }

        // where the sun stands, or the moon once it has set
        final float markX = cx - radiusX * (float) Math.cos(Math.PI * progress);
        final float markY = horizon - radiusY * (float) Math.sin(Math.PI * progress);
        final float markSize = Math.min(Math.min(h * 0.3f, w * 0.18f), radiusY * 0.9f);
        if (daylight) {
            // the sun at either end of the arc would hang half over the panel edge
            final String sun = OeplIcons.weather(0, false);
            final float half = Math.max(markSize, c.iconWidth(sun, markSize)) / 2f + 1f;
            c.iconCentered(sun, Math.max(half, Math.min(w - half, markX)),
                    Math.max(markY, markSize / 2f + 1f), markSize, c.accent);
        } else if (weather.moonPhase >= 0) {
            c.iconCentered(OeplIcons.moonPhase(OeplIcons.moonAge(weather.moonPhase)),
                    cx, Math.max(arcTop + markSize / 2f, horizon - radiusY * 0.55f),
                    markSize, c.ink);
        }

        if (timeSize <= 0f) {
            return;
        }
        final String riseGlyph = String.valueOf(OeplIcons.SUNRISE);
        final String setGlyph = String.valueOf(OeplIcons.SUNSET);
        final float iconH = timeSize * 0.85f;
        final float riseIcon = c.iconWidth(riseGlyph, iconH);
        final float setIcon = c.iconWidth(setGlyph, iconH);
        final float timeTop = horizon + gapAboveTimes;
        final float middle = timeTop + timeLine * 0.5f;
        final float gap = Math.max(1f, w * 0.01f);

        c.iconCentered(riseGlyph, pad + riseIcon / 2f, middle, iconH, c.ink);
        c.text(rise, pad + riseIcon + gap, timeTop, timeSize, OeplCanvas.LEFT, c.ink);
        final float setTextLeft = w - pad - c.measure(set, timeSize);
        c.iconCentered(setGlyph, setTextLeft - gap - setIcon / 2f, middle, iconH, c.ink);
        c.text(set, w - pad, timeTop, timeSize, OeplCanvas.RIGHT, c.ink);

        // the length of the day goes between the two times, and only when it fits between them
        if (lengthSize <= 0f) {
            return;
        }
        final long minutes = (weather.sunset - weather.sunrise) / 60L;
        final String length = minutes / 60L + ":" + String.format(Locale.ROOT, "%02d",
                minutes % 60L);
        final float freeFrom = pad + riseIcon + gap + c.measure(rise, timeSize);
        final float freeTo = setTextLeft - gap - setIcon;
        // it may be set smaller to fit between the two times, but not so small that it turns
        // into a smudge between them
        if (freeTo - freeFrom > lengthSize * 2.2f) {
            c.textFitted(length, (freeFrom + freeTo) / 2f,
                    timeTop + (timeLine - c.lineHeight(lengthSize)) / 2f, lengthSize,
                    OeplCanvas.CENTER, c.accent, freeTo - freeFrom - gap * 2f);
        }
    }

    private static float clamp(final float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
