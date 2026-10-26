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

import java.util.ArrayList;
import java.util.List;

/**
 * A plain table of labels and values, under a heading. OpenEPaperLink uses this shape for the
 * card an access point draws about itself; here it reports on the phone and on the display, so
 * a tag can be made to show what it is and how it is doing.
 */
public class OeplInfoCard extends OeplCard {

    public static class Row {
        final String label;
        final String value;

        public Row(final String label, final String value) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
        }
    }

    private final String heading;
    private final List<Row> rows;

    public OeplInfoCard(final String heading, final List<Row> rows) {
        super(true);
        this.heading = heading == null ? "" : heading;
        this.rows = rows == null ? new ArrayList<Row>() : rows;
    }

    @Override
    public boolean hasData() {
        return !rows.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.017f);

        final float headSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.17f, w * 0.14f));
        c.textClipped(heading, pad, 0, headSize, OeplCanvas.LEFT, c.accent, w - pad * 2);

        final float top = c.lineHeight(headSize) * 1.05f;
        final float rowSize = Math.max(OeplCanvas.READABLE, Math.min(Math.min(h * 0.125f, w * 0.085f),
                (h - top) / Math.max(1, rows.size()) * 0.82f));
        final float rowHeight = Math.max(c.lineHeight(rowSize),
                (h - top) / Math.max(1, Math.min(rows.size(),
                        (int) ((h - top) / c.lineHeight(rowSize)))));

        float labelWidth = 0;
        for (final Row row : rows) {
            labelWidth = Math.max(labelWidth, c.measure(row.label, rowSize));
        }
        labelWidth = Math.min(labelWidth + pad * 2, w * 0.55f);

        int index = 0;
        for (final Row row : rows) {
            final float y = top + index * rowHeight;
            if (!c.roomFor(y, rowSize)) {
                break;
            }
            c.textClipped(row.label, pad, y, rowSize, OeplCanvas.LEFT, c.ink, labelWidth - pad);
            c.textClipped(row.value, pad + labelWidth, y, rowSize, OeplCanvas.LEFT, c.ink,
                    w - labelWidth - pad * 2);
            index++;
        }
    }
}
