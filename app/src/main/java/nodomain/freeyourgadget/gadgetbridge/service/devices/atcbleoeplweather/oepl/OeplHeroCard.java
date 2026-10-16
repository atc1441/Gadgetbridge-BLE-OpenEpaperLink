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

import android.graphics.RectF;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * One value as large as the panel can hold it, the rest as small framed badges above it, and
 * the agenda beside it.
 * <p>
 * This is the layout for a display somebody walks past rather than steps up to: the figure in
 * the middle is readable across a room, and the badges are there for the moment one does step
 * up. Which value gets the big spot is not decided here - it is the first of the readings, and
 * what comes first is decided once, in {@link OeplReadings}, for every dashboard card alike.
 * <p>
 * The panel's shape decides how the two halves sit: a wide tag puts the agenda in a column on
 * the right, a tall one puts it underneath, and a tag too small for both drops the agenda and
 * keeps the figure.
 */
public class OeplHeroCard extends OeplCard {

    private final List<OeplReading> readings;
    private final List<CalendarEvent> events;

    public OeplHeroCard(final List<OeplReading> readings, final List<CalendarEvent> events) {
        super(true);
        this.readings = readings == null ? Collections.emptyList() : readings;
        this.events = events == null ? Collections.emptyList() : events;
    }

    @Override
    public boolean hasData() {
        return !readings.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (readings.isEmpty()) {
            return;
        }
        final float pad = Math.max(2f, Math.min(c.width, c.height) * 0.025f);

        // ------------------------------------------------------------ the badge row
        float top = pad;
        final float badgeH = Math.min(c.height * 0.17f, Math.max(12f, c.width * 0.075f));
        // nothing under eight pixels: a badge that cannot be read is a row of room taken
        // from the figure underneath it, which is the whole point of this card
        final float badgeText = c.sizeForHeight(badgeH * 0.62f, badgeH, 8f);
        if (readings.size() > 1 && badgeText > 0f && c.height >= 90f) {
            final String stamp = new SimpleDateFormat("EE dd. MMM", Locale.getDefault())
                    .format(new Date());
            final float stampSize = c.sizeForHeight(badgeH * 0.6f, badgeH, 8f);
            final float stampWidth = c.measure(stamp, stampSize) + pad;
            c.text(stamp, c.width - pad, top + (badgeH - c.lineHeight(stampSize)) / 2f,
                    stampSize, OeplCanvas.RIGHT, c.ink);

            float x = pad;
            final float limit = c.width - pad - stampWidth;
            for (int i = 1; i < readings.size(); i++) {
                final float used = badge(c, readings.get(i), x, top, badgeH, badgeText, limit);
                if (used <= 0f) {
                    break;
                }
                x += used + pad;
            }
            top += badgeH + pad;
        }

        // ------------------------------------------------------------ the two halves
        final float bodyTop = top;
        final float bodyHeight = c.height - bodyTop - pad;
        final boolean landscape = c.width >= c.height * 1.2f;
        final boolean room = bodyHeight > 44f && (landscape ? c.width > 210f : bodyHeight > 120f);
        final boolean withAgenda = !events.isEmpty() && room;

        RectF hero = new RectF(pad, bodyTop, c.width - pad, bodyTop + bodyHeight);
        RectF agenda = null;
        if (withAgenda) {
            if (landscape) {
                final float split = c.width * 0.52f;
                hero = new RectF(pad, bodyTop, split - pad / 2f, bodyTop + bodyHeight);
                agenda = new RectF(split + pad / 2f, bodyTop, c.width - pad,
                        bodyTop + bodyHeight);
                c.dottedColumn(split, bodyTop, bodyTop + bodyHeight, c.ink, 3f);
            } else {
                final float split = bodyTop + bodyHeight * 0.5f;
                hero = new RectF(pad, bodyTop, c.width - pad, split - pad / 2f);
                agenda = new RectF(pad, split + pad / 2f, c.width - pad, bodyTop + bodyHeight);
                c.line(pad, Math.round(split), c.width - pad, Math.round(split), c.ink, 1f);
            }
        }
        hero(c, readings.get(0), hero);
        if (agenda != null) {
            agenda(c, agenda, pad);
        }
    }

    /**
     * One badge: a framed pill with a symbol and a value in it.
     *
     * @return how wide it ended up, or 0 when it did not fit before the limit
     */
    private float badge(final OeplCanvas c, final OeplReading reading, final float x,
                        final float y, final float h, final float textSize, final float limit) {
        final String value = reading.display(fahrenheit);
        final float iconH = h * 0.56f;
        final float iconW = reading.hasIcon() ? reading.iconWidth(c, iconH) : 0f;
        final float inner = Math.max(2f, h * 0.18f);
        final float width = inner * 2f + iconW + (iconW > 0f ? inner * 0.6f : 0f)
                + c.measure(value, textSize);
        if (x + width > limit) {
            return 0f;
        }
        final int color = reading.accent ? c.accent : c.ink;
        final android.graphics.Paint stroke = new android.graphics.Paint();
        stroke.setColor(c.ink);
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        stroke.setStrokeWidth(1f);
        c.canvas.drawRoundRect(new RectF(x, y, x + width, y + h), h / 2f, h / 2f, stroke);

        float cursor = x + inner;
        if (iconW > 0f) {
            reading.drawIcon(c, cursor + iconW / 2f, y + h / 2f, iconH, color);
            cursor += iconW + inner * 0.6f;
        }
        c.text(value, cursor, y + (h - c.lineHeight(textSize)) / 2f, textSize,
                OeplCanvas.LEFT, color);
        return width;
    }

    /** The big one: symbol on the left, figure as large as the room allows, caption under it. */
    private void hero(final OeplCanvas c, final OeplReading reading, final RectF box) {
        final String value = reading.display(fahrenheit);
        final int color = reading.accent ? c.accent : c.ink;
        final float labelSize = c.sizeForHeight(box.height() * 0.2f, box.height() * 0.26f, 8f);
        final float labelHeight = labelSize > 0f ? c.lineHeight(labelSize) : 0f;
        final float figureHeight = box.height() - labelHeight;

        final float iconH = Math.min(figureHeight * 0.82f, box.width() * 0.3f);
        final float iconW = reading.hasIcon() ? reading.iconWidth(c, iconH) : 0f;
        final float gap = iconW > 0f ? Math.max(3f, box.width() * 0.03f) : 0f;
        if (iconW > 0f) {
            reading.drawIcon(c, box.left + iconW / 2f, box.top + figureHeight / 2f, iconH,
                    color);
        }
        final float figureLeft = box.left + iconW + gap;
        // a value that is a sentence rather than a figure - "weit offen seit 12:40" - has no
        // size at which it fits, so it is cut rather than drawn over the edge
        c.textFillingClipped(value, (figureLeft + box.right) / 2f,
                box.top + figureHeight / 2f, box.right - figureLeft, figureHeight * 0.96f,
                color);
        if (labelSize > 0f) {
            c.textClipped(reading.label, box.left, box.top + figureHeight, labelSize,
                    OeplCanvas.LEFT, c.ink, box.width());
        }
    }

    /** The next appointments, a time and a title per line, for as many lines as there is room. */
    private void agenda(final OeplCanvas c, final RectF box, final float pad) {
        // sized by the column as well as by the height: on a panel that is tall for its width
        // the height alone asks for sixty pixel type in a column six characters wide, and
        // every appointment then reads "16:56 ..."
        final float size = Math.min(c.sizeForHeight(box.height() * 0.2f,
                box.height() * 0.24f, 8f), box.width() / 6.5f);
        if (size <= 0f) {
            return;
        }
        final float lineHeight = c.lineHeight(size) * 1.1f;
        final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
        final SimpleDateFormat day = new SimpleDateFormat("EEE", Locale.getDefault());
        float y = box.top;
        for (final CalendarEvent event : events) {
            if (y + c.lineHeight(size) > box.bottom + 0.5f) {
                break;
            }
            final String when = event.isAllDay()
                    ? day.format(new Date(event.getBegin()))
                    : clock.format(new Date(event.getBegin()));
            final float stampWidth = c.measure(when, size) + pad;
            c.text(when, box.left, y, size, OeplCanvas.LEFT, c.ink);
            final String title = event.getTitle() == null ? "" : event.getTitle().trim();
            c.textClipped(title, box.left + stampWidth, y, size, OeplCanvas.LEFT, c.ink,
                    box.width() - stampWidth);
            y += lineHeight;
        }
    }
}
