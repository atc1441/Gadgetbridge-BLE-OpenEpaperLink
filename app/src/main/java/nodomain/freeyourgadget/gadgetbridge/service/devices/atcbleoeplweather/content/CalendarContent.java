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
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplDays;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/** The next few appointments, with today's ones marked in the accent colour. */
public class CalendarContent implements CardContent {
    private final List<CalendarEvent> events;
    private final int maxEvents;

    public CalendarContent(final List<CalendarEvent> events, final int maxEvents) {
        this.events = events;
        this.maxEvents = maxEvents;
    }

    @Nullable
    @Override
    public String title() {
        return new SimpleDateFormat("EEEE d. MMMM", Locale.getDefault()).format(new Date());
    }

    @Override
    public boolean hasData() {
        return !events.isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (events.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, area.width() * 0.025f);
        // A row below this height is no longer legible on e-paper, so show fewer entries.
        // The type is sized off the row, and the quantiser of a two colour panel leaves
        // nothing but dust of a line under about ten pixels - which is what the combination
        // cards, where this content gets a third of the panel, used to be full of.
        final float minRow = 24f;
        final int count = Math.max(1, Math.min(Math.min(maxEvents, events.size()),
                (int) (area.height() / minRow)));
        final float rowH = Math.min(area.height() / count, area.height() * 0.34f);

        // sized by the width too: a card with few entries has tall rows, and type scaled off
        // those alone leaves nothing but the first letter of every title
        // ...and never below what the panel can still show. The combination cards hand this
        // content a third of the panel, where the row height alone would set the titles in
        // nine pixels of a regular face - which the quantiser leaves as grey dust. Under
        // thirteen pixels the bold face goes on instead, for the same reason.
        final float stampSize = Math.max(10f, Math.min(rowH * 0.38f, area.width() * 0.11f));
        final float titleSize = Math.max(11f, Math.min(rowH * 0.42f, area.width() * 0.12f));
        final Paint when = style.paint(style.accent, true, stampSize);
        final Paint what = style.paint(style.ink, titleSize < 13f, titleSize);
        final Paint bullet = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullet.setColor(style.ink);

        // The column has to fit the widest label actually drawn, not a placeholder: with a
        // weekday prefix the stamps are far wider and would run into the titles. And it is
        // measured by the ink rather than by the advance the stamp is laid out with - those
        // differ by a few pixels, which is exactly the margin by which "Sun 10:00" used to
        // touch the appointment next to it.
        final float maxColumn = (area.width() - pad * 5) * 0.45f;
        float timeColumn = widest(when, events, count);
        while (timeColumn > maxColumn && when.getTextSize() > 6f) {
            when.setTextSize(Math.max(6f, when.getTextSize() * 0.94f));
            timeColumn = widest(when, events, count);
        }

        for (int i = 0; i < count; i++) {
            final CalendarEvent event = events.get(i);
            final float top = area.top + rowH * i;
            final float base = top + rowH * 0.68f;

            // the dot grows with the row, so it has to be kept clear of the left edge
            final float dot = Math.max(1.5f, Math.min(rowH * 0.07f, pad * 0.8f));
            canvas.drawCircle(area.left + pad, base - rowH * 0.18f, dot, bullet);

            canvas.drawText(label(event), area.left + pad * 3, base, when);

            final float textLeft = area.left + pad * 3 + timeColumn + pad;
            style.drawEllipsised(canvas, event.getTitle(), textLeft, base,
                    area.right - textLeft - pad, what);
        }
    }

    /** The widest stamp of the rows that are drawn, by the room its ink actually takes. */
    private float widest(final Paint paint, final List<CalendarEvent> events, final int count) {
        final Rect ink = new Rect();
        float widest = 0f;
        for (int i = 0; i < count; i++) {
            final String stamp = label(events.get(i));
            paint.getTextBounds(stamp, 0, stamp.length(), ink);
            widest = Math.max(widest, Math.max(paint.measureText(stamp), ink.right));
        }
        return widest;
    }

    /**
     * Today's events show the time, later ones the weekday so the day is obvious. An all day
     * entry is read by the days it is written on rather than by the instants it carries, which
     * are midnights in UTC - see {@link OeplDays}.
     */
    private String label(final CalendarEvent event) {
        if (event.isAllDay()) {
            return OeplDays.coversToday(event) ? "—"
                    : new SimpleDateFormat("EE", Locale.getDefault())
                    .format(OeplDays.dayOf(event));
        }
        final String pattern = OeplDays.beginsToday(event) ? "HH:mm" : "EE HH:mm";
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(event.getBegin()));
    }
}
