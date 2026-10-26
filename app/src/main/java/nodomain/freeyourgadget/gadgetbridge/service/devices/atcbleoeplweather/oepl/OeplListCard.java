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

import java.util.Collections;
import java.util.List;

/**
 * The values as a list: a heading per group, then one row each of symbol, name and value, with
 * a bar for everything that is a percentage.
 * <p>
 * This is the layout that scales. A tile grid on a large panel is a wall of enormous figures
 * with air between them, while a list simply gets longer: eight rows on a 2.9 inch tag, twenty
 * on a 7.5 inch poster, with the type staying the size it needs to be read. The values are
 * right aligned on one column and joined to their names with leader dots, because a column of
 * numbers is read by running down it and loses the eye without them.
 */
public class OeplListCard extends OeplCard {

    private final List<OeplReading> readings;

    public OeplListCard(final List<OeplReading> readings) {
        super(true);
        this.readings = readings == null ? Collections.emptyList() : readings;
    }

    @Override
    public boolean hasData() {
        return !readings.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (readings.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, Math.min(c.width, c.height) * 0.025f);
        final float x0 = pad;
        final float x1 = c.width - pad;

        // The type size comes from how many rows there are to show - but there is a floor to
        // it, and the list simply ends where the panel does. Text under about eight pixels is
        // not small, it is gone: the panel has two or three colours and a quantiser that turns
        // thin grey strokes into paper, so a row that does not fit is dropped rather than set
        // in a size nobody can read.
        final int sections = (int) countSections();
        final float floor = Math.max(8f, Math.min(c.height * 0.055f, 11f));
        final float wanted = (c.height - pad * 2f) / (readings.size() + sections * 0.8f);
        // and sized by the width as well: on a panel that is tall for its width a row sized
        // off the height alone came out seventy pixels high on a panel 152 wide, where the
        // value then grew back over the pictogram in front of it
        final float size = Math.max(floor, Math.min(Math.max(wanted * 0.8f, floor),
                Math.min(c.height * 0.14f, c.width * 0.15f)));
        final float rowHeight = c.lineHeight(size) * 1.12f;
        final float headingSize = Math.max(8f, size * 0.82f);
        final float headingHeight = c.lineHeight(headingSize) * 1.25f;

        float y = pad;
        String section = null;
        boolean first = true;
        for (final OeplReading reading : readings) {
            final boolean newSection = reading.section != null
                    && !reading.section.equals(section);
            // a heading is only worth its room when a row of its own can follow it
            if (newSection && y + headingHeight + rowHeight <= c.height - pad + 0.5f) {
                if (!first) {
                    y += headingHeight * 0.2f;
                }
                c.textClipped(reading.section.toUpperCase(java.util.Locale.getDefault()), x0, y,
                        headingSize, OeplCanvas.LEFT, c.ink, x1 - x0);
                final float rule = y + c.lineHeight(headingSize) * 0.95f;
                c.line(x0, Math.round(rule), x1, Math.round(rule), c.ink, 1f);
                y += headingHeight;
                section = reading.section;
            } else if (newSection) {
                section = reading.section;
            }
            if (y + c.lineHeight(size) > c.height - pad + 0.5f) {
                break; // out of panel, and a half drawn row is worse than a missing one
            }
            row(c, reading, y, size, rowHeight, x0, x1);
            y += rowHeight;
            first = false;
        }
    }

    private long countSections() {
        return readings.stream().map(r -> r.section).distinct().count();
    }

    private void row(final OeplCanvas c, final OeplReading reading, final float y,
                     final float size, final float rowHeight, final float x0, final float x1) {
        final int color = reading.accent ? c.accent : c.ink;
        final float centre = y + c.lineHeight(size) / 2f;
        float left = x0;
        if (reading.hasIcon()) {
            final float iconH = size * 0.95f;
            final float iconW = reading.iconWidth(c, iconH);
            reading.drawIcon(c, left + iconW / 2f, centre, iconH, color);
            left += iconW + size * 0.4f;
        }

        // The value is cut before it is drawn, and it may never take more than half the
        // row: a state Home Assistant words rather than numbers - "weit offen seit 12:40" -
        // is as long as a sentence, and right aligned text that is not clipped grows to the
        // left, straight over the name beside it and out of the panel.
        final String value = reading.display(fahrenheit);
        // never wider than what is actually left beside the pictogram: a room measured from
        // the type size alone reached back over the icon rather than being cut
        final float valueRoom = Math.min(x1 - left,
                Math.max(size * 2f, (x1 - left) * 0.5f));
        c.textClipped(value, x1, y, size, OeplCanvas.RIGHT, color, valueRoom);
        final float valueWidth = Math.min(c.measure(value, size), valueRoom);
        float right = x1 - valueWidth - size * 0.4f;

        // A bar where there is a percentage and room for it, which says more at a distance
        // than the number beside it does - but the name comes first. The bar is therefore only
        // drawn while the whole name still fits beside it at the smallest size it may be set
        // in: a row reduced to two letters and a bar says less than one without the bar, and
        // the percentage stands in the value column either way.
        final float barRoom = Math.min(c.width * 0.26f, (right - left) * 0.45f);
        final float nameRoom = c.measure(reading.label, Math.max(7f, size * 0.5f));
        if (reading.percent >= 0 && right - left - barRoom > Math.max(size * 4f, nameRoom)) {
            final float barW = barRoom;
            final float barH = Math.max(2f, size * 0.52f);
            final float barX = right - barW;
            final float barY = centre - barH / 2f;
            c.strokeRect(barX, barY, barW, barH, c.ink, 1f);
            final float filled = (barW - 2f) * Math.min(100, Math.max(0, reading.percent)) / 100f;
            if (filled >= 1f) {
                c.fillRect(barX + 1f, barY + 1f, filled, barH - 2f, color);
            }
            right = barX - size * 0.4f;
        }

        final float labelWidth = right - left;
        if (labelWidth < size) {
            // nothing of the name would be left but an ellipsis, and the value is the half
            // of the row worth keeping
            return;
        }
        final float used = Math.min(labelWidth, c.measure(reading.label, size));
        c.textClipped(reading.label, left, y, size, OeplCanvas.LEFT, c.ink, labelWidth);

        // leader dots across whatever gap is left between the name and its value
        final float gapStart = left + used + size * 0.3f;
        if (right - gapStart > size * 0.8f) {
            final float dotY = Math.round(centre + size * 0.28f);
            for (float x = gapStart; x < right; x += Math.max(2f, size * 0.32f)) {
                c.fillRect(Math.round(x), dotY, 1f, 1f, c.ink);
            }
        }
    }
}
