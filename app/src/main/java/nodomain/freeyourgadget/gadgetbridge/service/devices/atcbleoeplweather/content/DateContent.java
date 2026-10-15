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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The date in large type. E-paper cannot be refreshed often enough for a ticking clock, so the
 * day is the headline and the update time stays small, in the status strip.
 */
public class DateContent implements CardContent {

    @Nullable
    @Override
    public String title() {
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date());
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        final Date now = new Date();
        final String weekday = new SimpleDateFormat("EEEE", Locale.getDefault()).format(now);
        final String day = new SimpleDateFormat("d", Locale.getDefault()).format(now);
        final String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(now);

        final float pad = area.width() * 0.04f;
        final Paint number = style.paint(style.ink, true, area.height() * 0.68f);
        style.fit(number, day, area.width() * 0.45f);
        final float numberW = number.measureText(day);

        // the two words share whatever the big number leaves over; sizing them off the height
        // alone let them run out over the right hand edge of a narrow panel
        final float column = area.width() - numberW - pad * 3;
        final Paint word = style.paint(style.accent, true,
                Math.min(area.height() * 0.22f, Math.max(9f, column * 0.26f)));
        style.fitNoSmallerThan(word, weekday, column, 9f);
        final Paint sub = style.paint(style.ink, false,
                Math.min(area.height() * 0.17f, Math.max(8f, column * 0.22f)));
        style.fitNoSmallerThan(sub, month, column, 8f);

        final float groupW = numberW + pad
                + Math.max(word.measureText(weekday), sub.measureText(month));
        final float left = area.left + Math.max(pad, (area.width() - groupW) / 2f);
        final float textLeft = left + numberW + pad;

        canvas.drawText(day, left, area.top + area.height() * 0.78f, number);
        style.drawEllipsised(canvas, weekday, textLeft, area.top + area.height() * 0.48f,
                area.right - textLeft - pad, word);
        style.drawEllipsised(canvas, month, textLeft, area.top + area.height() * 0.72f,
                area.right - textLeft - pad, sub);
    }
}
