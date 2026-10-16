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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The day as a curve: the temperature of the hours ahead as a line, and the rain of the same
 * hours as a strip of blocks under it.
 * <p>
 * This is the one thing a panel can show that no arrangement of tiles can. A figure says it is
 * thirteen degrees; a line says it will stay there until four and then fall, which is what
 * somebody deciding whether to take a jacket actually wants. The rain sits in the same hours
 * underneath, so the two are read against each other without a second axis.
 * <p>
 * Everything freezing is drawn in the accent colour - the one piece of information that decides
 * whether the road is icy, on a panel that has two colours to spend.
 */
public class OeplGraphCard extends OeplCard {

    @Nullable
    private final OeplWeather weather;
    private final int hours;

    public OeplGraphCard(@Nullable final OeplWeather weather, final int hours) {
        super(true);
        this.weather = weather;
        this.hours = Math.max(6, Math.min(48, hours));
    }

    @Override
    public boolean hasData() {
        return weather != null && upcoming().size() >= 3;
    }

    /** The hours from now on, as many as asked for and as the forecast has. */
    private List<OeplWeather.Hour> upcoming() {
        final List<OeplWeather.Hour> result = new ArrayList<>();
        if (weather == null) {
            return result;
        }
        final long from = System.currentTimeMillis() / 1000L - 1800L;
        for (final OeplWeather.Hour hour : weather.hours) {
            if (hour.epochSeconds >= from) {
                result.add(hour);
            }
            if (result.size() >= hours) {
                break;
            }
        }
        return result;
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final List<OeplWeather.Hour> slots = upcoming();
        if (slots.size() < 3) {
            return;
        }
        final float pad = Math.max(2f, Math.min(c.width, c.height) * 0.02f);

        float top = pad;
        // eight pixels or nothing, here as everywhere: the curve says more on its own than
        // it does under a line of grey dust
        final float headSize = c.sizeForHeight(Math.min(c.height * 0.14f, c.width * 0.07f),
                c.height * 0.18f, 8f);
        if (headSize > 0f && c.height >= 90f) {
            final String now = tempFine(weather.current.temperature) + "°";
            c.rowEnds(weather.location, now, top, headSize, c.ink,
                    weather.current.temperature < 0f ? c.accent : c.ink, pad, c.width - pad,
                    pad * 2f);
            top += c.lineHeight(headSize) * 1.05f;
        }

        // what the axes need: hour labels along the bottom, two degree labels down the left
        final float labelSize = c.sizeForHeight(Math.min(c.height * 0.11f, c.width * 0.055f),
                c.height * 0.14f, 8f);
        final float labelHeight = labelSize > 0f ? c.lineHeight(labelSize) : 0f;

        float peakRain = 0f;
        for (final OeplWeather.Hour hour : slots) {
            peakRain = Math.max(peakRain, Math.max(0f, hour.precipitation));
        }
        final boolean withRain = peakRain > 0f && c.height - top - labelHeight > 46f;
        final float rainHeight = withRain
                ? Math.max(4f, Math.min(c.height * 0.16f, 22f)) : 0f;

        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (final OeplWeather.Hour hour : slots) {
            low = Math.min(low, hour.temperature);
            high = Math.max(high, hour.temperature);
        }
        // whole degrees, and never a flat scale: a day that stays at thirteen would otherwise
        // be a line through the middle with no room above or below it
        float floor = (float) Math.floor(low);
        float ceiling = (float) Math.ceil(high);
        if (ceiling - floor < 2f) {
            floor -= 1f;
            ceiling += 1f;
        }

        // with the degree sign: a bare number up the side of a chart whose bottom edge
        // counts in hours is a number nobody can place
        final String highLabel = temp(ceiling) + "°";
        final String lowLabel = temp(floor) + "°";
        final float gutter = labelSize > 0f
                ? Math.max(c.measure(highLabel, labelSize), c.measure(lowLabel, labelSize)) + pad
                : 0f;

        final float plotLeft = pad + gutter;
        final float plotRight = c.width - pad;
        final float plotTop = top + (labelSize > 0f ? labelSize * 0.5f : 0f);
        final float plotBottom = c.height - pad - labelHeight - rainHeight
                - (withRain ? pad : 0f);
        if (plotBottom - plotTop < 16f || plotRight - plotLeft < 24f) {
            return;
        }

        // the scale: a dotted guide and a label at each end, one more in the middle
        for (int i = 0; i <= 2; i++) {
            final float y = Math.round(plotBottom - (plotBottom - plotTop) * i / 2f);
            for (float x = plotLeft; x < plotRight; x += 4f) {
                c.fillRect(Math.round(x), y, 1f, 1f, c.ink);
            }
            if (labelSize > 0f && i != 1) {
                final String label = i == 0 ? lowLabel : highLabel;
                c.text(label, pad, y - c.lineHeight(labelSize) / 2f, labelSize,
                        OeplCanvas.LEFT, c.ink);
            }
        }

        final float step = (plotRight - plotLeft) / (slots.size() - 1);
        final float thickness = Math.max(1.5f, c.height * 0.012f);
        float previousX = plotLeft;
        float previousY = valueY(slots.get(0).temperature, floor, ceiling, plotTop, plotBottom);
        for (int i = 1; i < slots.size(); i++) {
            final float x = plotLeft + step * i;
            final float y = valueY(slots.get(i).temperature, floor, ceiling, plotTop, plotBottom);
            final boolean freezing = slots.get(i).temperature < 0f
                    || slots.get(i - 1).temperature < 0f;
            c.line(previousX, previousY, x, y, freezing ? c.accent : c.ink, thickness);
            previousX = x;
            previousY = y;
        }

        // ------------------------------------------------------------ the rain underneath
        if (withRain) {
            final float scale = Math.max(1f, (float) Math.ceil(peakRain));
            final float barWidth = Math.max(1f, step * 0.7f);
            final float base = c.height - pad - labelHeight;
            c.line(plotLeft, Math.round(base), plotRight, Math.round(base), c.ink, 1f);
            for (int i = 0; i < slots.size(); i++) {
                final float millimetres = Math.max(0f, slots.get(i).precipitation);
                if (millimetres <= 0f) {
                    continue;
                }
                final float fraction = Math.min(1f, millimetres / scale);
                final float h = Math.max(1f, fraction * rainHeight);
                final float x = plotLeft + step * i - barWidth / 2f;
                c.fillRect(Math.max(plotLeft, x), base - h,
                        Math.min(barWidth, plotRight - Math.max(plotLeft, x)), h,
                        fraction > 0.5f ? c.accent : c.ink);
            }
        }

        // ------------------------------------------------------------ the hours
        if (labelSize > 0f) {
            // The bottom edge counts in hours, which is read as a date the moment the numbers
            // run past 12 - "15, 18, 21" looks like three days. Midnight therefore carries the
            // name of the day that starts there, and a dotted rule under it, so that the row
            // can only be read as a clock.
            final SimpleDateFormat clock = weather.format("HH");
            final SimpleDateFormat weekday = weather.format("EE");
            final java.util.Calendar when = java.util.Calendar.getInstance(weather.zone());
            final int every = Math.max(1,
                    (int) Math.ceil(c.measure("00", labelSize) * 2.4f / Math.max(1f, step)));
            for (int i = 0; i < slots.size(); i += every) {
                final Date at = new Date(slots.get(i).epochSeconds * 1000L);
                when.setTime(at);
                final boolean midnight = when.get(java.util.Calendar.HOUR_OF_DAY) < every;
                final String stamp = midnight ? weekday.format(at) : clock.format(at);
                if (midnight && i > 0) {
                    c.dottedColumn(Math.round(plotLeft + step * i), plotTop,
                            c.height - pad - labelHeight, c.ink, 3f);
                }
                // kept inside the plot, not merely inside the panel: the first hour centred
                // under its own column reaches into the gutter and lands on the degree label
                // sitting there
                final float half = c.measure(stamp, labelSize) / 2f + 1f;
                final float cx = Math.max(plotLeft + half,
                        Math.min(plotRight - half, plotLeft + step * i));
                c.text(stamp, cx, c.height - pad - labelHeight, labelSize, OeplCanvas.CENTER,
                        c.ink);
            }
        }
    }

    private float valueY(final float value, final float floor, final float ceiling,
                         final float top, final float bottom) {
        final float fraction = (value - floor) / Math.max(0.1f, ceiling - floor);
        return bottom - Math.max(0f, Math.min(1f, fraction)) * (bottom - top);
    }
}
