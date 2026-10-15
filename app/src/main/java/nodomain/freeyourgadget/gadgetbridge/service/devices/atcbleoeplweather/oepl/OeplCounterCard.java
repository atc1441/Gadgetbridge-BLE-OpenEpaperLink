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

import androidx.annotation.Nullable;

/**
 * A countdown, the way OpenEPaperLink draws one: nothing but the number, as large as the panel
 * allows, turning to the accent colour once it climbs past a threshold. An optional caption
 * underneath says what is being counted, which the access point leaves to the tag's name.
 */
public class OeplCounterCard extends OeplCard {

    private final long count;
    private final long thresholdAccent;
    @Nullable
    private final String caption;

    /**
     * @param count            days or hours remaining; a negative value counts up from the past
     * @param thresholdAccent  anything above this is drawn in the accent colour
     */
    public OeplCounterCard(final long count, final long thresholdAccent,
                           @Nullable final String caption) {
        super(true);
        this.count = count;
        this.thresholdAccent = thresholdAccent;
        this.caption = caption;
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final String text = String.valueOf(Math.abs(count));
        final boolean hasCaption = caption != null && !caption.trim().isEmpty() && h >= 80;
        final float captionSize = h * 0.16f;
        final float room = hasCaption ? h - captionSize * 1.3f : h;

        final int color = count > thresholdAccent ? c.accent : c.ink;
        c.textFilling(text, w / 2f, room / 2f, w * 0.94f, room * 0.94f, color);

        if (hasCaption) {
            c.textFitted(caption.trim(), w / 2f, h - captionSize * 1.2f, captionSize,
                    OeplCanvas.CENTER, c.ink, w * 0.94f);
        }
    }
}
