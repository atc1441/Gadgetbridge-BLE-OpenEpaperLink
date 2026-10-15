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
import android.graphics.RectF;

import androidx.annotation.Nullable;

/**
 * One thing a display can show. The card frame draws the title bar and the status strip; a
 * content only fills the area in between, which keeps every content type looking consistent
 * and makes adding a new one a matter of implementing this interface.
 */
public interface CardContent {

    /** What goes into the title bar, or null to leave it to the frame. */
    @Nullable
    String title();

    /**
     * Draws into the area the frame reserved. Implementations must scale everything off the
     * rectangle they are given: the same content runs on panels from 152x152 to 960x768.
     */
    void draw(Canvas canvas, CardStyle style, RectF area);

    /** False when there is nothing worth showing, so the frame can fall back to a hint. */
    default boolean hasData() {
        return true;
    }

    /**
     * True for a content that lays out the whole panel itself, title bar and status line
     * included. The OpenEPaperLink style cards do that, because their layouts assume every
     * pixel is theirs.
     */
    default boolean fullBleed() {
        return false;
    }
}
