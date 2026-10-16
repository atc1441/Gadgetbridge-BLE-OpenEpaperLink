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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather;

import java.util.EnumSet;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;

/**
 * Which options a given card actually reads. There are thirty-seven kinds of content and about
 * as many options between them, but no single card uses more than four, so showing all of them
 * at once says almost nothing about the display in front of you.
 * <p>
 * This is the one place that knows the answer: the settings screen asks it what to show, and the
 * two card factories are the only other things that may need to agree with it. Whenever a card
 * learns to read another preference, it has to be added here as well, which is what the test
 * alongside this class checks.
 */
public final class OEPLContentOptions {

    public enum Option {
        /** A forecast is needed at all, so the source and the place matter. */
        WEATHER,
        /** Beaufort against metres per second - only the OpenEPaperLink cards print wind. */
        WIND_UNIT,
        /** Sunrise and sunset, which only the OpenEPaperLink date card has room for. */
        SUN_TIMES,
        /** How many days of forecast are drawn. */
        FORECAST_DAYS,
        /** How many hours ahead the hourly and rain cards reach. */
        FORECAST_HOURS,
        /** How many single appointments are listed. */
        CALENDAR_EVENTS,
        /** How many days the week view spans. */
        CALENDAR_DAYS,
        /** The heading above the appointment list. */
        CALENDAR_TITLE,
        /** Feed address, heading and whether the item text is drawn. */
        FEED,
        /** What the code encodes, and its caption. */
        QR,
        /** Target moment, caption and the point where the number turns to the accent colour. */
        COUNTER,
        /** How many notifications are listed. */
        NOTIFICATIONS,
        /** The text typed in by hand. */
        TEXT,
        /** The picture, where it comes from and how it is reduced to the panel's colours. */
        IMAGE,
        /** Which groups of values the dashboard cards gather. */
        READINGS,
        /** The address of a Home Assistant instance, its token and the entities to show. */
        HOME_ASSISTANT,
        /** The four slots of the quartered card. */
        QUAD,
        /**
         * The title bar and status strip that {@link CardRenderer} draws around a card. The
         * OpenEPaperLink layouts assume every pixel is theirs and draw neither, so the frame and
         * the footer switch do nothing at all while one of them is selected.
         */
        FRAME,
    }

    private OEPLContentOptions() {
    }

    /** Everything the given content reads. Unknown ids fall back to the default weather card. */
    public static EnumSet<Option> of(final String content) {
        final String id = content == null ? "" : content;
        switch (id) {
            // ------------------------------------------------- the OpenEPaperLink layouts
            case OeplCardFactory.DATE:
                return EnumSet.of(Option.WEATHER, Option.SUN_TIMES);
            case OeplCardFactory.COUNT_DAYS:
            case OeplCardFactory.COUNT_HOURS:
                return EnumSet.of(Option.COUNTER);
            case OeplCardFactory.WEATHER:
                return EnumSet.of(Option.WEATHER, Option.WIND_UNIT);
            case OeplCardFactory.FORECAST:
                return EnumSet.of(Option.WEATHER, Option.WIND_UNIT, Option.FORECAST_DAYS);
            case OeplCardFactory.RSS:
                return EnumSet.of(Option.FEED);
            case OeplCardFactory.QR:
                return EnumSet.of(Option.QR);
            case OeplCardFactory.CALENDAR:
                return EnumSet.of(Option.CALENDAR_EVENTS, Option.CALENDAR_TITLE);
            case OeplCardFactory.CALENDAR_WEEK:
                return EnumSet.of(Option.CALENDAR_DAYS);
            case OeplCardFactory.MONTH:
                // the month decides its own span, so only the heading is left to configure
                return EnumSet.of(Option.CALENDAR_TITLE);
            case OeplCardFactory.RAIN:
                return EnumSet.of(Option.WEATHER, Option.FORECAST_HOURS);
            case OeplCardFactory.CLOCK:
            case OeplCardFactory.INFO:
                return EnumSet.noneOf(Option.class);

            // ------------------------------------------------- our own full panel designs
            case OeplCardFactory.BIG:
            case OeplCardFactory.TILES:
                return EnumSet.of(Option.WEATHER, Option.WIND_UNIT);
            case OeplCardFactory.RANGE:
                return EnumSet.of(Option.WEATHER, Option.FORECAST_DAYS);
            case OeplCardFactory.SUN:
                return EnumSet.of(Option.WEATHER, Option.SUN_TIMES);
            case OeplCardFactory.BOARD:
                return EnumSet.of(Option.CALENDAR_EVENTS, Option.CALENDAR_TITLE);
            case OeplCardFactory.POSTER:
                return EnumSet.of(Option.TEXT);
            case OeplCardFactory.GRID:
            case OeplCardFactory.HERO:
            case OeplCardFactory.LIST:
                // the dashboard cards draw whatever the chosen groups collected, which is why
                // they read the weather, the calendar and the Home Assistant settings alike
                return EnumSet.of(Option.READINGS, Option.HOME_ASSISTANT, Option.WEATHER,
                        Option.WIND_UNIT, Option.CALENDAR_EVENTS);
            case OeplCardFactory.GRAPH:
                return EnumSet.of(Option.WEATHER, Option.FORECAST_HOURS);
            case OeplCardFactory.QUAD:
                // a quarter can hold any card there is, so any setting may be the one that
                // matters. Hiding a menu here would hide the settings of the quarter that uses
                // it, and this is the one card where nothing can be ruled out in advance. Only
                // the frame is certain: the quartered card lays out the whole panel, so the
                // title bar and the status strip are not drawn whatever a quarter would like.
                return EnumSet.complementOf(EnumSet.of(Option.FRAME));

            // ------------------------------------------------- the framed cards
            case CardContentFactory.DATE:
            case CardContentFactory.CLOCK:
            case CardContentFactory.STATUS:
            case CardContentFactory.MUSIC:
                return EnumSet.of(Option.FRAME);
            case CardContentFactory.CALENDAR:
            case CardContentFactory.DATE_CALENDAR:
                return EnumSet.of(Option.FRAME, Option.CALENDAR_EVENTS);
            case CardContentFactory.TEXT:
                return EnumSet.of(Option.FRAME, Option.TEXT);
            case CardContentFactory.NOTIFICATIONS:
                return EnumSet.of(Option.FRAME, Option.NOTIFICATIONS);
            case CardContentFactory.HOURLY:
                return EnumSet.of(Option.FRAME, Option.WEATHER, Option.FORECAST_HOURS);
            case CardContentFactory.WEATHER_CALENDAR:
                return EnumSet.of(Option.FRAME, Option.WEATHER, Option.FORECAST_DAYS,
                        Option.CALENDAR_EVENTS);
            case CardContentFactory.WEATHER_NOTIFICATIONS:
                return EnumSet.of(Option.FRAME, Option.WEATHER, Option.FORECAST_DAYS,
                        Option.NOTIFICATIONS);
            case CardContentFactory.CALENDAR_NOTIFICATIONS:
                return EnumSet.of(Option.FRAME, Option.CALENDAR_EVENTS, Option.NOTIFICATIONS);
            case CardContentFactory.IMAGE:
            case CardContentFactory.IMAGE_URL:
                return EnumSet.of(Option.IMAGE);
            case CardContentFactory.DASHBOARD:
                return EnumSet.of(Option.FRAME, Option.WEATHER, Option.FORECAST_DAYS,
                        Option.CALENDAR_EVENTS, Option.NOTIFICATIONS);
            case CardContentFactory.WEATHER:
            case CardContentFactory.WEATHER_DATE:
            case CardContentFactory.FORECAST:
            case CardContentFactory.CLOCK_WEATHER:
            default:
                return EnumSet.of(Option.FRAME, Option.WEATHER, Option.FORECAST_DAYS);
        }
    }

    public static boolean uses(final String content, final Option option) {
        return of(content).contains(option);
    }

    /** True when at least one of the options is read, which is how a whole sub-menu is judged. */
    public static boolean usesAny(final String content, final Option... options) {
        final EnumSet<Option> used = of(content);
        for (final Option option : options) {
            if (used.contains(option)) {
                return true;
            }
        }
        return false;
    }
}
