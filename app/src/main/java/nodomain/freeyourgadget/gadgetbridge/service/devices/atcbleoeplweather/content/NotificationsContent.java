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
        // a row has to stay readable, so the count follows from the space, not the other way round
        final float minRow = 22f;
        final int count = Math.max(1, Math.min(Math.min(maxNotes, notes.size()),
                (int) (area.height() / minRow)));
        final float rowH = area.height() / count;
        final boolean twoLines = rowH >= 34f;

        final float cap = area.width() * 0.13f;
        final Paint app = style.paint(style.accent, true,
                Math.min(rowH * (twoLines ? 0.26f : 0.30f), cap * 0.8f));
        final Paint head = style.paint(style.ink, true,
                Math.min(rowH * (twoLines ? 0.34f : 0.42f), cap));
        final Paint sub = style.paint(style.ink, false, Math.min(rowH * 0.28f, cap * 0.85f));

        for (int i = 0; i < count; i++) {
            final PhoneStateStore.Note note = notes.get(i);
            final float top = area.top + rowH * i;
            final float left = area.left + pad;
            final float width = area.width() - pad * 2;

            if (twoLines) {
                // source, headline, body - each on its own baseline, the last one kept inside
                if (note.source != null) {
                    style.drawEllipsised(canvas, note.source, left, top + rowH * 0.28f, width, app);
                }
                final String headline = note.title != null ? note.title : note.body;
                style.drawEllipsised(canvas, headline, left, top + rowH * 0.62f, width, head);
                if (note.title != null && note.body != null) {
                    style.drawEllipsised(canvas, note.body, left,
                            Math.min(top + rowH * 0.92f, style.baselineAbove(area.bottom, sub)),
                            width, sub);
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
