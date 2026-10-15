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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The time, large and centred, with the date under it. An e-paper tag that wakes every few
 * minutes cannot show seconds, so this is a clock in the sense a station board is one.
 */
public class OeplClockCard extends OeplCard {

    public OeplClockCard() {
        super(true);
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final Date now = new Date();
        final String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        final String date = new SimpleDateFormat("EEEE, d. MMMM", Locale.getDefault()).format(now);

        c.textFilling(time, w / 2f, h * 0.37f, w * 0.94f, h * 0.60f, c.ink);
        c.textFilling(date, w / 2f, h * 0.84f, w * 0.94f, h * 0.17f, c.accent);
    }
}
