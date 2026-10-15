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

/**
 * Two contents sharing one card. The area is split along its longer edge, so the same pairing
 * works on a wide shelf label and on a tall one.
 */
public class SplitContent implements CardContent {
    /**
     * Below this a pane holds nothing anybody could read from across a room. Two unreadable
     * halves are worse than one readable whole, so a card that small shows its first content
     * alone - which is also what makes the stacked cards survive on a price tag.
     */
    private static final float MIN_PANE = 46f;

    private final CardContent first;
    private final CardContent second;
    private final float firstShare;

    public SplitContent(final CardContent first, final CardContent second, final float firstShare) {
        this.first = first;
        this.second = second;
        this.firstShare = firstShare;
    }

    @Nullable
    @Override
    public String title() {
        return first.title() != null ? first.title() : second.title();
    }

    @Override
    public boolean hasData() {
        return first.hasData() || second.hasData();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (!first.hasData()) {
            second.draw(canvas, style, area);
            return;
        }
        if (!second.hasData()) {
            first.draw(canvas, style, area);
            return;
        }
        final Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(style.ink);
        rule.setStrokeWidth(Math.max(1f, Math.min(area.width(), area.height()) * 0.008f));

        if (area.width() >= area.height() * 1.6f) {
            final float x = area.left + area.width() * firstShare;
            if (x - area.left < MIN_PANE || area.right - x < MIN_PANE) {
                first.draw(canvas, style, area);
                return;
            }
            first.draw(canvas, style, new RectF(area.left, area.top, x - 4, area.bottom));
            canvas.drawLine(x, area.top, x, area.bottom, rule);
            second.draw(canvas, style, new RectF(x + 6, area.top, area.right, area.bottom));
        } else {
            final float y = area.top + area.height() * firstShare;
            if (y - area.top < MIN_PANE || area.bottom - y < MIN_PANE) {
                first.draw(canvas, style, area);
                return;
            }
            first.draw(canvas, style, new RectF(area.left, area.top, area.right, y - 3));
            canvas.drawLine(area.left, y, area.right, y, rule);
            second.draw(canvas, style, new RectF(area.left, y + 5, area.right, area.bottom));
        }
    }
}
