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


import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.util.List;

/** Free text the user typed into the device settings, one line per line. */
public class TextContent implements CardContent {
    private final String heading;
    private final List<String> lines;

    public TextContent(@Nullable final String heading, final List<String> lines) {
        this.heading = heading;
        this.lines = lines;
    }

    @Nullable
    @Override
    public String title() {
        return heading == null || heading.trim().isEmpty() ? null : heading.trim();
    }

    @Override
    public boolean hasData() {
        return !lines.isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (lines.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, area.width() * 0.03f);
        final float lineH = area.height() / lines.size();
        final Paint paint = style.paint(style.ink, lines.size() <= 2, lineH * 0.72f);

        // one size for every line, so the block reads as a block - but no smaller than a panel
        // can still be read from across the room; what does not fit then is cut instead
        final float floor = Math.max(9f, Math.min(lineH * 0.5f, area.height() * 0.08f));
        for (final String line : lines) {
            style.fitNoSmallerThan(paint, line, area.width() - pad * 2, floor);
        }
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            // a line starting with ! is drawn in the accent colour
            final boolean highlight = line.startsWith("!");
            final String text = highlight ? line.substring(1).trim() : line;
            final Paint p = highlight ? style.paint(style.accent, true, paint.getTextSize()) : paint;
            style.drawEllipsised(canvas, text, area.left + pad,
                    Math.min(area.top + lineH * (i + 0.78f), style.baselineAbove(area.bottom, p)),
                    area.width() - pad * 2, p);
        }
    }
}
