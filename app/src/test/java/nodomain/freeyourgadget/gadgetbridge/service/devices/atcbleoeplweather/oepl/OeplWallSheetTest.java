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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OeplDemo;

/**
 * The one rule the wall sheet has: whatever it shows, it shows no part of it twice.
 * <p>
 * It is a rule and not a detail because the sheet is made of other cards, and two of those
 * cards can turn into the same thing. The week view is a grid of days where there is room for
 * one and the list of what is coming up where there is not - so on a 4.2 inch panel set to
 * seven days, a sheet that puts the appointment list beside the week put the appointment list
 * beside the appointment list. That is how it was reported.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplWallSheetTest {

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    @Test
    public void theSheetNeverShowsTheSameThingTwice() {
        final List<String> doubled = new ArrayList<>();
        for (final int[] shape : new int[][]{{960, 672}, {672, 960}, {800, 480}, {480, 800},
                {400, 300}, {300, 400}, {384, 184}, {184, 384}, {296, 128}, {152, 152},
                {250, 128}, {640, 384}, {1360, 480}, {480, 1360}}) {
            for (final int days : new int[]{1, 2, 3, 4, 5, 7}) {
                final List<String> kinds = sheet(days).kinds(shape[0], shape[1]);
                final Set<String> seen = new HashSet<>();
                for (final String kind : kinds) {
                    if (!seen.add(kind)) {
                        doubled.add(String.format("%dx%d with %d days: %s", shape[0], shape[1],
                                days, kinds));
                        break;
                    }
                }
            }
        }
        assertEquals("sheets that say something twice:\n" + String.join("\n", doubled),
                0, doubled.size());
    }

    /** And it is worth the name: on a panel with room, it is several things rather than one. */
    @Test
    public void aLargePanelGetsSeveralThingsAtOnce() {
        assertTrue("a seven inch panel holds four", sheet(7).kinds(960, 672).size() >= 4);
        assertTrue("a 4.2 inch panel holds three", sheet(3).kinds(400, 300).size() >= 3);
        assertTrue("and one standing up as well", sheet(5).kinds(480, 800).size() >= 3);
    }

    /** A panel too small to divide shows one thing properly rather than three badly. */
    @Test
    public void aSmallPanelGetsOneThing() {
        assertEquals("a shelf label is not a wall sheet", 1, sheet(3).kinds(296, 128).size());
        assertEquals(1, sheet(3).kinds(152, 152).size());
    }

    private OeplWallCard sheet(final int days) {
        return new OeplWallCard(OeplDemo.weather(), OeplDemo.events(),
                OeplDemo.beyondTheWeather(), days, 6, 5, true, "Kalender");
    }
}
