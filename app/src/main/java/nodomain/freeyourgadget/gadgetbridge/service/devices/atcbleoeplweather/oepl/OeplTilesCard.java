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

import java.util.ArrayList;
import java.util.List;

/**
 * Today in four tiles, each a pictogram and a figure: the sky and the temperature now, the range
 * the day will cover, how much rain is expected and how hard the wind blows. The grid rearranges
 * itself to the panel - four tiles in a square, two side by side on a narrow strip, one above
 * the other on a tall one.
 * <p>
 * Every tile says what it is with its pictogram instead of a caption, which is the one thing on
 * a card that needs no translation and costs no room.
 */
public class OeplTilesCard extends OeplCard {

    private static class Tile {
        final String glyph;
        final String value;
        final boolean accent;

        Tile(final String glyph, final String value, final boolean accent) {
            this.glyph = glyph;
            this.value = value;
            this.accent = accent;
        }
    }

    @Nullable
    private final OeplWeather weather;

    public OeplTilesCard(@Nullable final OeplWeather weather, final boolean beaufort) {
        super(beaufort);
        this.weather = weather;
    }

    @Override
    public boolean hasData() {
        return weather != null && weather.hasData();
    }

    /** Big enough to be recognised, small enough to leave the figure beside it the room. */
    private static float iconHeight(final float tileW, final float tileH, final float pad) {
        return Math.min((tileH - pad * 2f) * 0.72f, tileW * 0.3f);
    }

    @Override
    protected void paint(final OeplCanvas c) {
        if (weather == null) {
            return;
        }
        final OeplWeather.Current now = weather.current;
        final List<Tile> tiles = new ArrayList<>(4);
        // whole degrees: a tile is read in passing, and a decimal only makes the figure
        // narrower than the others for nothing
        tiles.add(new Tile(OeplIcons.weather(now.wmoCode, !now.day),
                temp(now.temperature) + "°", now.temperature < 0f));
        if (!weather.days.isEmpty()) {
            final OeplWeather.Day today = weather.days.get(0);
            tiles.add(new Tile(String.valueOf(OeplIcons.THERMOMETER),
                    temp(today.minTemperature) + "/" + temp(today.maxTemperature), false));
            if (today.precipitation >= 0f) {
                tiles.add(new Tile(String.valueOf(OeplIcons.RAINDROP),
                        Math.round(today.precipitation) + "mm", today.precipitation > 10f));
            }
        }
        tiles.add(new Tile(OeplIcons.windDirection(now.windDirection), wind(now.windSpeed),
                windy(now.windSpeed)));

        final float w = c.width;
        final float h = c.height;

        // A tile holds a pictogram beside a figure, so it wants to be about twice as wide as
        // it is high. The grid is chosen by how close it gets to that rather than by the
        // panel's orientation: four tiles in a row across a wide tag are four slivers.
        int cols = 1;
        float best = Float.MAX_VALUE;
        for (final int candidate : new int[]{1, 2, tiles.size()}) {
            if (candidate < 1 || candidate > tiles.size()) {
                continue;
            }
            final int lines = (tiles.size() + candidate - 1) / candidate;
            if (h / lines < 24f) {
                continue; // a row this thin cannot be read
            }
            final float ratio = (w / candidate) / (h / lines);
            final float distance = Math.abs((float) Math.log(ratio / 2.2f));
            if (distance < best) {
                best = distance;
                cols = candidate;
            }
        }
        int rows = (tiles.size() + cols - 1) / cols;
        while (rows > 1 && h / rows < 24f) {
            rows--;
            cols = (tiles.size() + rows - 1) / rows;
        }
        final int shown = Math.min(tiles.size(), cols * rows);

        final float tileW = w / cols;
        final float tileH = h / rows;
        final float pad = Math.max(2f, Math.min(tileW, tileH) * 0.08f);

        // one size for every figure on the card, taken from the tile that can hold the least:
        // four numbers each as large as its own tile allows cannot be compared at a glance
        float valueSize = Float.MAX_VALUE;
        for (int i = 0; i < shown; i++) {
            final float iconW = c.iconWidth(tiles.get(i).glyph, iconHeight(tileW, tileH, pad));
            valueSize = Math.min(valueSize, c.fillingSize(tiles.get(i).value,
                    Math.max(4f, tileW - pad * 3f - iconW), (tileH - pad * 2f) * 0.8f));
        }

        for (int i = 0; i < shown; i++) {
            final Tile tile = tiles.get(i);
            final float left = (i % cols) * tileW;
            final float top = (i / cols) * tileH;

            // hairlines between the tiles, never on the panel edge where half of one is lost
            if (i % cols != 0) {
                c.line(left, top + pad, left, top + tileH - pad, c.ink, 1f);
            }
            if (i >= cols) {
                c.line(left + pad, top, left + tileW - pad, top, c.ink, 1f);
            }

            final float iconH = iconHeight(tileW, tileH, pad);
            final float iconW = c.iconWidth(tile.glyph, iconH);
            c.iconCentered(tile.glyph, left + pad + iconW / 2f, top + tileH / 2f, iconH,
                    tile.accent ? c.accent : c.ink);

            final float textLeft = left + pad + iconW + pad;
            c.textFillingAt(tile.value, (textLeft + left + tileW - pad) / 2f, top + tileH / 2f,
                    valueSize, tile.accent ? c.accent : c.ink);
        }
    }
}
