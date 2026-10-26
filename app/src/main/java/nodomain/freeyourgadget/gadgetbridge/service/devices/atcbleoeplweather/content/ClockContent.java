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

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCanvas;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The time, as large as the panel allows. */
public class ClockContent implements CardContent {

    @Nullable
    @Override
    public String title() {
        return new SimpleDateFormat("EEEE d. MMMM", Locale.getDefault()).format(new Date());
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        final String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        final Paint paint = style.paint(style.ink, true, area.height() * 1.05f);
        style.fit(paint, time, area.width() * 0.94f);
        // Fitted to the height by the ink the figures really leave rather than by the face's
        // metrics, and centred on that ink. A language whose digits are taller than the
        // bundled face's - Burmese writes its own - overflowed a box fitted to the metrics by
        // a fifth of the panel.
        final Rect ink = new Rect();
        paint.getTextBounds(time, 0, time.length(), ink);
        int guard = 0;
        while (ink.height() > area.height() * 0.96f
                && paint.getTextSize() > OeplCanvas.READABLE && guard++ < 64) {
            paint.setTextSize(Math.max(OeplCanvas.READABLE, paint.getTextSize() * 0.94f));
            paint.getTextBounds(time, 0, time.length(), ink);
        }
        final float baseline = area.top + (area.height() - ink.height()) / 2f - ink.top;
        canvas.drawText(time, area.left + (area.width() - paint.measureText(time)) / 2f,
                baseline, paint);
    }
}
