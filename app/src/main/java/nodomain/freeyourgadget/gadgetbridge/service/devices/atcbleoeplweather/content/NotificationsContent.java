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

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.PhoneStateStore;

/** The most recent notifications the phone passed on. */
public class NotificationsContent implements CardContent {
    private final List<PhoneStateStore.Note> notes;
    private final int maxNotes;

    /**
     * What goes into the title bar. Handed in rather than built here, because a card has no
     * resources of its own and the app's language is what decides the wording.
     */
    private final String heading;

    public NotificationsContent(final List<PhoneStateStore.Note> notes, final int maxNotes,
                                final String heading) {
        this.notes = notes;
        this.maxNotes = maxNotes;
        this.heading = heading;
    }

    @Nullable
    @Override
    public String title() {
        return notes.isEmpty() ? null : heading;
    }

    @Override
    public boolean hasData() {
        return !notes.isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (notes.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, area.width() * 0.025f);
        // A row has to stay readable, so the count follows from the space rather than the
        // other way round - and "readable" is measured against the panel's two or three
        // colours, which turn a thin grey line into paper.
        final float minRow = 26f;
        final int count = Math.max(1, Math.min(Math.min(maxNotes, notes.size()),
                (int) (area.height() / minRow)));
        final float rowH = area.height() / count;
        final boolean twoLines = rowH >= 34f;

        final float cap = area.width() * 0.13f;
        // Three stacked lines take a quarter more room than their sizes add up to, so the
        // sizes are chosen to leave that room: the source, the headline and the body used to
        // be placed at fractions of the row and touched each other on a narrow panel.
        final Paint app = style.paint(style.accent, true,
                Math.max(9f, Math.min(rowH * (twoLines ? 0.24f : 0.30f), cap * 0.8f)));
        final Paint head = style.paint(style.ink, true,
                Math.max(11f, Math.min(rowH * (twoLines ? 0.30f : 0.42f), cap)));
        // the body line is the one that may be left out, so it keeps its own floor low and
        // is simply not drawn when the row cannot hold it
        final Paint sub = style.paint(style.ink, false,
                Math.max(9f, Math.min(rowH * 0.24f, cap * 0.85f)));

        for (int i = 0; i < count; i++) {
            final PhoneStateStore.Note note = notes.get(i);
            final float top = area.top + rowH * i;
            final float left = area.left + pad;
            final float width = area.width() - pad * 2;

            if (twoLines) {
                // source, headline and body stacked by the room each line takes, so that the
                // next one starts where the last one ends rather than where a fraction of the
                // row happens to fall
                final boolean hasBody = note.title != null && note.body != null;
                final float hSource = note.source != null ? style.lineHeight(app) : 0f;
                final float hHead = style.lineHeight(head);
                final float hBody = hasBody ? style.lineHeight(sub) : 0f;
                float y = top + Math.max(0f, (rowH - hSource - hHead - hBody) * 0.35f);

                if (note.source != null) {
                    style.drawTopEllipsised(canvas, note.source, left, y, width, app);
                    y += hSource;
                }
                final String headline = note.title != null ? note.title : note.body;
                style.drawTopEllipsised(canvas, headline, left, y, width, head);
                y += hHead;
                // the body is the line that gives way when the row is too tight for all three
                if (hasBody && style.roomFor(y, hBody, Math.min(top + rowH, area.bottom))) {
                    style.drawTopEllipsised(canvas, note.body, left, y, width, sub);
                }
            } else {
                // one line: source in the accent colour, then the headline after it
                float x = left;
                if (note.source != null) {
                    final String tag = note.source + "  ";
                    canvas.drawText(tag, x, top + rowH * 0.72f, app);
                    x += app.measureText(tag);
                }
                final String headline = note.title != null ? note.title : note.body;
                style.drawEllipsised(canvas, headline, x, top + rowH * 0.72f,
                        area.right - pad - x, head);
            }
        }
    }
}
