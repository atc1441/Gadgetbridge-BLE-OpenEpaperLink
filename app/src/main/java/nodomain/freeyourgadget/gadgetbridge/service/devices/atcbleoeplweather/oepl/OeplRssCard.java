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

import java.util.List;

/**
 * Headlines from a news feed: a caption on a rule across the top, then as many wrapped
 * headlines as the panel takes. This is the OpenEPaperLink RSS card, where a tag on a wall
 * turns into a small news board.
 */
public class OeplRssCard extends OeplCard {

    private final List<OeplRssClient.Item> items;
    private final String caption;
    private final boolean withSummaries;

    public OeplRssCard(final List<OeplRssClient.Item> items, final String caption,
                       final boolean withSummaries) {
        super(true);
        this.items = items;
        this.caption = caption == null || caption.trim().isEmpty() ? "RSS" : caption.trim();
        this.withSummaries = withSummaries;
    }

    @Override
    public boolean hasData() {
        return items != null && !items.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final float pad = Math.max(2f, w * 0.014f);

        final float headSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.15f, w * 0.13f));
        c.textClipped(caption, pad, 0, headSize, OeplCanvas.LEFT, c.accent, w - pad * 2);
        final float ruleY = c.lineHeight(headSize) * 0.98f;
        c.line(0, ruleY, w, ruleY, c.ink, Math.max(1f, h * 0.008f));

        final float titleSize = Math.max(OeplCanvas.READABLE, Math.min(h * 0.105f, w * 0.075f));
        // the summary is set smaller than its headline, but not thinner than the panel can
        // show: six pixels of grey under a headline is a smudge, not a sentence
        final float summarySize = Math.max(OeplCanvas.READABLE, titleSize * 0.85f);
        final float foot = Math.max(1f, h * 0.01f);
        float y = ruleY + Math.max(2f, h * 0.025f);

        for (final OeplRssClient.Item item : items) {
            // a headline is only started when a whole line of it still fits, otherwise the last
            // one hangs over the bottom edge and is cut through the middle of its letters
            if (!c.roomFor(y, titleSize)) {
                break;
            }
            y = c.textBox(item.title, pad, y, w - pad * 2, h - y - foot, titleSize,
                    c.ink, 1f, OeplCanvas.LEFT);
            if (withSummaries && item.description != null && !item.description.isEmpty()
                    && c.roomFor(y, summarySize)) {
                y = c.textBox(item.description, pad, y, w - pad * 2,
                        Math.min(summarySize * 2.4f, h - y - foot), summarySize, c.ink, 1f,
                        OeplCanvas.LEFT);
            }
            y += titleSize * 0.35f;
        }
    }
}
