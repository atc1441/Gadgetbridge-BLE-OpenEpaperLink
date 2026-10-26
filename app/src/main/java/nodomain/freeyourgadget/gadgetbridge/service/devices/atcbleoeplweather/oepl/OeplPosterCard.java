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

import java.util.ArrayList;
import java.util.List;

/**
 * Your own words as a poster: the first line as large as the panel can hold it, the rest
 * underneath in one size they all share. This is the card for a door sign, a shelf label or a
 * name plate - the things a display on a wall is actually used for, and the one kind of card
 * that has nothing to do with the weather.
 * <p>
 * A line that begins with an exclamation mark is drawn in the accent colour, the same mark the
 * framed text card uses, and the mark itself is not printed.
 */
public class OeplPosterCard extends OeplCard {

    private final String headline;
    private final boolean headlineAccent;
    private final List<String> lines = new ArrayList<>();
    private final List<Boolean> accents = new ArrayList<>();

    public OeplPosterCard(final List<String> text) {
        super(true);
        String first = "";
        boolean firstAccent = false;
        for (final String raw : text == null ? new ArrayList<String>() : text) {
            final String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            final boolean accent = line.startsWith("!");
            final String body = accent ? line.substring(1).trim() : line;
            if (body.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = body;
                firstAccent = accent;
            } else {
                lines.add(body);
                accents.add(accent);
            }
        }
        this.headline = first;
        this.headlineAccent = firstAccent;
    }

    @Override
    public boolean hasData() {
        return !headline.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (headline.isEmpty()) {
            return;
        }
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.03f);

        // the lines under the headline take their room first, measured as one block, and the
        // headline fills whatever is left over - it is the part that may be any size at all
        float bodySize = c.sizeForHeight(Math.min(h * 0.17f, w * 0.1f), h * 0.22f, OeplCanvas.READABLE);
        int shown = 0;
        float bodyBlock = 0f;
        if (!lines.isEmpty() && bodySize > 0f) {
            final float line = c.lineHeight(bodySize);
            shown = Math.min(lines.size(), Math.max(0, (int) (h * 0.46f / line)));
            bodyBlock = shown * line;
        }

        final float rule = shown > 0 ? Math.max(1f, h * 0.012f) : 0f;
        final float headRoom = h - bodyBlock - rule * 3f - pad;
        // clipped, not merely fitted: a headline of forty characters is still too wide for
        // a price tag at the smallest size that can be read
        c.textFillingClipped(headline, w / 2f, pad * 0.5f + headRoom / 2f, w - pad * 2f,
                headRoom * 0.96f, headlineAccent ? c.accent : c.ink);

        if (shown <= 0) {
            return;
        }
        final float ruleY = h - bodyBlock - rule * 2f;
        c.fillRect(w * 0.3f, ruleY, w * 0.4f, rule, c.ink);

        final float line = c.lineHeight(bodySize);
        for (int i = 0; i < shown; i++) {
            c.textClipped(lines.get(i), w / 2f, h - bodyBlock + i * line, bodySize,
                    OeplCanvas.CENTER, accents.get(i) ? c.accent : c.ink, w - pad * 2f);
        }
    }
}
