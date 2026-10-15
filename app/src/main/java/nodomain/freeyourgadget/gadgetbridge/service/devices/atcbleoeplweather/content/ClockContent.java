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
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float baseline = area.top + (area.height() - (fm.ascent + fm.descent)) / 2f;
        canvas.drawText(time, area.left + (area.width() - paint.measureText(time)) / 2f,
                baseline, paint);
    }
}
