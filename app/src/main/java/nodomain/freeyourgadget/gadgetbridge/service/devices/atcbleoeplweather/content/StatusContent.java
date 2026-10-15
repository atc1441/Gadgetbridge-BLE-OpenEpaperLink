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

/** Battery levels of the other gadgets paired with Gadgetbridge. */
public class StatusContent implements CardContent {

    public static class Entry {
        public final String name;
        public final int percent; // negative when unknown

        public Entry(final String name, final int percent) {
            this.name = name;
            this.percent = percent;
        }
    }

    private final List<Entry> entries;

    public StatusContent(final List<Entry> entries) {
        this.entries = entries;
    }

    @Nullable
    @Override
    public String title() {
        return "Gadgetbridge";
    }

    @Override
    public boolean hasData() {
        return !entries.isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (entries.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, area.width() * 0.03f);
        final int count = Math.min(entries.size(), Math.max(1, (int) (area.height() / 14f)));
        final float rowH = Math.min(area.height() / count, area.height() * 0.3f);

        // a reading has to fit the width as well, or the name beside it is cut to one letter
        final float textSize = Math.min(rowH * 0.42f, area.width() * 0.13f);
        final Paint name = style.paint(style.ink, false, textSize);
        final Paint value = style.paint(style.ink, true, textSize);
        final float barW = area.width() * 0.34f;
        final float barH = Math.max(4f, rowH * 0.26f);

        for (int i = 0; i < count; i++) {
            final Entry e = entries.get(i);
            final float base = Math.min(area.top + rowH * (i + 0.62f),
                    style.baselineAbove(area.bottom, name));
            final float barLeft = area.right - barW - pad;
            // the name gets what the reading and the bar leave over, so the three can never be
            // drawn through each other on a narrow panel
            final String pct = e.percent >= 0 ? e.percent + "%" : "";
            final float pctWidth = pct.isEmpty() ? 0f : value.measureText(pct) + pad * 2;
            style.drawEllipsised(canvas, e.name, area.left + pad, base,
                    barLeft - pctWidth - area.left - pad * 2, name);

            final float barTop = base - barH * 1.4f;
            final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(Math.max(1f, barH * 0.18f));
            frame.setColor(style.ink);
            canvas.drawRect(barLeft, barTop, barLeft + barW, barTop + barH, frame);

            if (e.percent >= 0) {
                final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                // a nearly empty battery is the one thing worth the accent colour
                fill.setColor(e.percent <= 20 ? style.accent : style.ink);
                canvas.drawRect(barLeft, barTop, barLeft + barW * (e.percent / 100f),
                        barTop + barH, fill);
                canvas.drawText(pct, barLeft - value.measureText(pct) - pad, base, value);
            }
        }
    }
}
