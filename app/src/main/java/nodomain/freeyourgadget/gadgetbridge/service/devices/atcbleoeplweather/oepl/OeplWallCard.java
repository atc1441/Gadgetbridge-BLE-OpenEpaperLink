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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * The wall sheet: the week, the weather, the days ahead and what is coming up, all on one
 * panel, each in a region of the size its content deserves.
 * <p>
 * This exists because a large display is not a large price tag. Every other design here shows
 * one thing, which is right for a shelf label and wasteful on a seven inch panel on a kitchen
 * wall: a week grid on 960 by 672 leaves half the sheet to empty hours, and a grid of tiles
 * leaves most of it to the frames around them. What a panel that size is for is reading
 * several things at once - the week, the weather now, the days ahead, the next appointments -
 * the way a newspaper page is read.
 * <p>
 * So the panel is divided into regions and each region is handed to the card that already
 * knows how to draw that thing. Nothing here draws anything itself: it is the division that
 * is this design, and the cards do what they do everywhere else. The division follows the
 * shape - columns on a panel wider than it is tall, rows on one standing up - and a region
 * too small to be read is given up rather than filled, so the same design still works its way
 * down to a 2.9 inch tag, where it ends as the week grid alone.
 */
public class OeplWallCard implements CardContent {

    /** Under this a panel cannot hold two things at once, and gets the one that matters. */
    private static final float MIN_COMPOSED_WIDTH = 300f;
    private static final float MIN_COMPOSED_HEIGHT = 170f;
    /** A region narrower or shorter than this is not read, so it is not drawn. */
    private static final float MIN_REGION_WIDTH = 118f;
    private static final float MIN_REGION_HEIGHT = 62f;

    @Nullable
    private final OeplWeather weather;
    private final List<CalendarEvent> events;
    private final List<OeplReading> readings;
    private final int calendarDays;
    private final int agendaEntries;
    private final int forecastDays;
    private final boolean beaufort;
    private final String caption;

    public OeplWallCard(@Nullable final OeplWeather weather, final List<CalendarEvent> events,
                        final List<OeplReading> readings, final int calendarDays,
                        final int agendaEntries, final int forecastDays, final boolean beaufort,
                        final String caption) {
        this.weather = weather;
        this.events = events == null ? Collections.emptyList() : events;
        this.readings = readings == null ? Collections.emptyList() : readings;
        this.calendarDays = calendarDays;
        this.agendaEntries = agendaEntries;
        this.forecastDays = forecastDays;
        this.beaufort = beaufort;
        this.caption = caption;
    }

    @Nullable
    @Override
    public String title() {
        return null;
    }

    @Override
    public boolean fullBleed() {
        return true;
    }

    @Override
    public boolean hasData() {
        return (weather != null && weather.hasData()) || !events.isEmpty() || !readings.isEmpty();
    }

    @Override
    public void draw(final Canvas canvas, final CardStyle style, final RectF area) {
        final List<Region> regions = regions(area);
        for (final Region region : regions) {
            region.content.draw(canvas, style, region.area);
        }
        rules(canvas, style, area, regions);
    }

    /** One piece of the sheet: a card and the rectangle it was given. */
    private static class Region {
        final CardContent content;
        final RectF area;

        Region(final CardContent content, final RectF area) {
            this.content = content;
            this.area = area;
        }

        /**
         * What this region shows, in the terms a reader would use. The week card is two
         * different things depending on the room it has - a grid of days, or the list of what
         * is coming up - and which of them it is decides whether the appointment list beside
         * it is a second view or the same one twice.
         */
        String kind() {
            if (content instanceof OeplCalendarWeekCard) {
                return OeplCalendarWeekCard.fitsAsGrid(area.width(), area.height(),
                        ((OeplCalendarWeekCard) content).days()) ? "week" : "appointments";
            }
            if (content instanceof OeplCalendarListCard) {
                return "appointments";
            }
            if (content instanceof OeplWeatherCard) {
                return "weather";
            }
            if (content instanceof OeplForecastCard) {
                return "forecast";
            }
            return "values";
        }
    }

    /**
     * What the sheet would show on a panel of this size, in the order the regions come. It is
     * here for the test that holds this card to its one rule: whatever it shows, it shows no
     * part of it twice.
     */
    List<String> kinds(final float width, final float height) {
        final List<String> kinds = new ArrayList<>();
        for (final Region region : regions(new RectF(0f, 0f, width, height))) {
            kinds.add(region.kind());
        }
        return kinds;
    }

    /**
     * How the sheet is divided.
     * <p>
     * The week takes the larger share, because it is the thing that is looked at from across
     * the room and the only one that needs room to be a grid at all. The weather now sits at
     * the top of the other share, where the eye goes first; the days ahead under it; and the
     * appointments as a list under that, because a list says what the grid can only mark.
     */
    private List<Region> regions(final RectF area) {
        final List<Region> regions = new ArrayList<>();
        final float w = area.width();
        final float h = area.height();

        // a panel too small to hold two things reads better holding one
        if (w < MIN_COMPOSED_WIDTH || h < MIN_COMPOSED_HEIGHT) {
            regions.add(new Region(week(), new RectF(area)));
            return regions;
        }

        final float gap = Math.max(3f, Math.min(w, h) * 0.015f);
        if (w >= h * 1.15f) {
            // whether the week really comes out as a grid decides what the column beside it
            // holds: a week that falls back to a list of what is coming up is the appointment
            // list, and a sheet that then puts the appointment list beside it says it twice
            // Wider than tall: the week down the left, the weather and what follows it in a
            // column on the right. The grid wants width more than the column does - an hour
            // row across seven days is seven columns - so it gets the larger share.
            final float split = Math.round(area.left + w * (w > 700f ? 0.6f : 0.55f));
            final float weekWidth = split - gap / 2f - area.left;
            regions.add(new Region(week(),
                    new RectF(area.left, area.top, split - gap / 2f, area.bottom)));
            column(regions, new RectF(split + gap / 2f, area.top, area.right, area.bottom), gap,
                    OeplCalendarWeekCard.fitsAsGrid(weekWidth, h, calendarDays));
        } else {
            // Standing up: the weather across the top, where a wide shallow region suits a
            // reading beside a pictogram, then the week, then what is coming up.
            final float top = Math.round(area.top + h * 0.26f);
            final float bottom = Math.round(area.bottom - h * (h > 600f ? 0.3f : 0.26f));
            regions.add(new Region(now(),
                    new RectF(area.left, area.top, area.right, top - gap / 2f)));
            regions.add(new Region(week(),
                    new RectF(area.left, top + gap / 2f, area.right, bottom - gap / 2f)));
            final RectF under = new RectF(area.left, bottom + gap / 2f, area.right, area.bottom);
            // the same rule standing up: the appointments go under the week only when the
            // week is a grid, because otherwise the week is the appointments
            if (fits(under) && OeplCalendarWeekCard.fitsAsGrid(area.width(),
                    bottom - gap / 2f - (top + gap / 2f), calendarDays)) {
                regions.add(new Region(agenda(), under));
            } else {
                regions.get(1).area.bottom = area.bottom;
            }
        }
        return regions;
    }

    /**
     * The right hand column of a wide sheet: the weather now, the days ahead, the appointments
     * and - where the panel is large enough that they would otherwise be left out - the values
     * from the house. Each is dropped rather than squeezed once its share stops being readable,
     * and what is left over goes to the one above it.
     */
    private void column(final List<Region> regions, final RectF column, final float gap,
                        final boolean weekIsAGrid) {
        final List<String> parts = new ArrayList<>();
        parts.add(NOW);
        if (weather != null && weather.hasData() && !weather.upcomingDays().isEmpty()) {
            parts.add(FORECAST);
        }
        if (!events.isEmpty() && weekIsAGrid) {
            parts.add(AGENDA);
        }
        if (!readings.isEmpty() && (column.height() > 420f || !weekIsAGrid)) {
            parts.add(VALUES);
        }

        // as many as the column can hold a readable share of, from the top down
        int count = parts.size();
        while (count > 1 && (column.height() - gap * (count - 1)) / count < MIN_REGION_HEIGHT) {
            count--;
        }
        final float each = (column.height() - gap * (count - 1)) / count;
        float top = column.top;
        for (int i = 0; i < count; i++) {
            final float bottom = i == count - 1 ? column.bottom : Math.round(top + each);
            regions.add(new Region(part(parts.get(i), bottom - top),
                    new RectF(column.left, top, column.right, bottom)));
            top = bottom + gap;
        }
    }

    private static final String NOW = "now";
    private static final String FORECAST = "forecast";
    private static final String AGENDA = "agenda";
    private static final String VALUES = "values";

    /**
     * The card for one region, built once the region's height is known - which the values
     * list needs: handed a dozen readings and a strip at the bottom of a sheet, it would set
     * all twelve in eight pixels, and eight pixels of a three colour panel is a smudge. It is
     * given as many as the strip can show at a size that survives instead.
     */
    private CardContent part(final String kind, final float height) {
        switch (kind) {
            case FORECAST:
                // without its headline: the place and what it is doing now are written large
                // in the region directly above this one
                return new OeplForecastCard(weather, Math.max(3, forecastDays), beaufort, false);
            case AGENDA:
                return agenda();
            case VALUES:
                final int fits = Math.max(1, (int) (height / 26f));
                return new OeplListCard(readings.size() <= fits ? readings
                        : readings.subList(0, fits));
            default:
                return now();
        }
    }

    private boolean fits(final RectF region) {
        return region.width() >= MIN_REGION_WIDTH && region.height() >= MIN_REGION_HEIGHT;
    }

    private CardContent week() {
        return new OeplCalendarWeekCard(events, calendarDays);
    }

    private CardContent agenda() {
        return new OeplCalendarListCard(events, Math.max(2, agendaEntries), caption);
    }

    /** The weather now, or - with no forecast to show - whatever else is worth the room. */
    private CardContent now() {
        if (weather != null && weather.hasData()) {
            return new OeplWeatherCard(weather, beaufort);
        }
        return readings.isEmpty() ? agenda() : new OeplListCard(readings);
    }

    /** Hairlines in the gaps, so the regions read as a sheet and not as one busy picture. */
    private void rules(final Canvas canvas, final CardStyle style, final RectF area,
                       final List<Region> regions) {
        if (regions.size() < 2) {
            return;
        }
        final Paint rule = new Paint();
        rule.setColor(style.ink);
        rule.setStrokeWidth(1f);
        for (int i = 1; i < regions.size(); i++) {
            final RectF previous = regions.get(i - 1).area;
            final RectF region = regions.get(i).area;
            if (region.top > previous.bottom) {
                final float y = Math.round((previous.bottom + region.top) / 2f);
                canvas.drawLine(Math.max(area.left, region.left), y, region.right, y, rule);
            } else if (region.left > previous.right) {
                final float x = Math.round((previous.right + region.left) / 2f);
                canvas.drawLine(x, area.top, x, area.bottom, rule);
            }
        }
    }
}
