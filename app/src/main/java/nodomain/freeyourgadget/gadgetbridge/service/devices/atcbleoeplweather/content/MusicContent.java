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

import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;

/** Whatever the phone is playing. */
public class MusicContent implements CardContent {
    @Nullable
    private final MusicSpec spec;
    private final boolean playing;

    /** The title bar wording, in the app's language rather than in the card's. */
    private final String heading;

    public MusicContent(@Nullable final MusicSpec spec, final boolean playing,
                        final String heading) {
        this.spec = spec;
        this.playing = playing;
        this.heading = heading;
    }

    @Nullable
    @Override
    public String title() {
        return heading;
    }

    @Override
    public boolean hasData() {
        return spec != null && (spec.getTrack() != null || spec.getArtist() != null);
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        if (spec == null) {
            return;
        }
        final float pad = Math.max(2f, area.width() * 0.04f);
        final float left = area.left + pad;
        final float width = area.width() - pad * 2;

        // a title is sized by the room it has in both directions, otherwise a tall narrow
        // panel shows one huge letter and an ellipsis
        final Paint track = style.paint(style.ink, true,
                Math.min(area.height() * 0.30f, width * 0.17f));
        final Paint artist = style.paint(style.accent, true,
                Math.min(area.height() * 0.21f, width * 0.13f));
        final Paint album = style.paint(style.ink, false,
                Math.min(area.height() * 0.17f, width * 0.11f));

        float y = area.top + area.height() * 0.34f;
        style.drawEllipsised(canvas, spec.getTrack(), left, y, width, track);
        y += area.height() * 0.27f;
        style.drawEllipsised(canvas, spec.getArtist(), left, y, width, artist);
        if (spec.getAlbum() != null && area.height() > 60) {
            y += area.height() * 0.22f;
            style.drawEllipsised(canvas, spec.getAlbum(), left, y, width, album);
        }
    }
}
