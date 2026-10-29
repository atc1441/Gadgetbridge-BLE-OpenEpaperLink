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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLTagTypes;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OpenMeteoClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.PhoneStateStore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.QuadContent;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarManager;

/**
 * Builds the cards that own the whole panel, with no title bar and no status strip around them.
 * <p>
 * Two families live here. The ids beginning with "oepl_" follow the OpenEPaperLink layouts and
 * carry the content numbers the access point uses, so "oepl_4" is its current weather card and
 * "oepl_8" its forecast; that makes the mapping between the two systems obvious when comparing
 * them side by side. The ids beginning with "panel_" are this project's own designs and follow
 * nothing but themselves - the prefix is what keeps the two apart at a glance.
 */
public final class OeplCardFactory {
    private static final Logger LOG = LoggerFactory.getLogger(OeplCardFactory.class);

    /** The ids that copy an OpenEPaperLink layout, named after its content number. */
    public static final String PREFIX = "oepl_";
    /** The ids of our own full panel designs, which owe the access point nothing. */
    public static final String OWN_PREFIX = "panel_";

    public static final String DATE = "oepl_1";
    public static final String COUNT_DAYS = "oepl_2";
    public static final String COUNT_HOURS = "oepl_3";
    public static final String WEATHER = "oepl_4";
    public static final String FORECAST = "oepl_8";
    public static final String RSS = "oepl_9";
    public static final String QR = "oepl_10";
    public static final String CALENDAR = "oepl_11";
    public static final String CALENDAR_WEEK = "oepl_11w";
    public static final String RAIN = "oepl_16";
    public static final String INFO = "oepl_21";
    public static final String CLOCK = "oepl_29";

    /** Our own designs: the temperature alone, as large as the panel can hold it. */
    public static final String BIG = "panel_big";
    /** The week as bars on one shared temperature scale. */
    public static final String RANGE = "panel_bars";
    /** The sun's way across the sky, with the time of day marked on it. */
    public static final String SUN = "panel_sun";
    /** Today in four tiles of a pictogram and a figure. */
    public static final String TILES = "panel_tiles";
    /** The appointments as a departure board. */
    public static final String BOARD = "panel_board";
    /** Your own text as a poster, for a door sign or a name plate. */
    public static final String POSTER = "panel_poster";
    /** The dashboard grid: one framed tile per value, in the manner of a Home Assistant board. */
    public static final String GRID = "panel_grid";
    /** One value as large as the panel allows, the others as badges, the agenda beside it. */
    public static final String HERO = "panel_hero";
    /** The values as a list of rows, with a bar for everything that is a percentage. */
    public static final String LIST = "panel_list";
    /** The hours ahead as a temperature curve with the rain underneath. */
    public static final String GRAPH = "panel_graph";
    /** The panel in quarters, each holding another card. */
    public static final String QUAD = "panel_quad";
    /** The month as a sheet, with a dot per appointment and today marked. */
    public static final String MONTH = "panel_month";
    /** The week, the weather and what is coming up, each in a region of its own. */
    public static final String WALL = "panel_wall";

    private OeplCardFactory() {
    }

    public static boolean handles(final String id) {
        return id != null && (id.startsWith(PREFIX) || id.startsWith(OWN_PREFIX));
    }

    /** True for the cards that need a weather forecast to say anything. */
    public static boolean needsWeather(final String id) {
        return WEATHER.equals(id) || FORECAST.equals(id) || RAIN.equals(id) || DATE.equals(id)
                || BIG.equals(id) || RANGE.equals(id) || SUN.equals(id) || TILES.equals(id)
                || GRAPH.equals(id) || WALL.equals(id) || needsReadings(id);
    }

    public static boolean needsFeed(final String id) {
        return RSS.equals(id);
    }

    /** The three cards that draw whatever {@link OeplReadings} collected. */
    public static boolean needsReadings(final String id) {
        return GRID.equals(id) || HERO.equals(id) || LIST.equals(id) || WALL.equals(id);
    }

    /** True for the cards that put entity states from a Home Assistant instance on the panel. */
    public static boolean needsHomeAssistant(final String id) {
        return needsReadings(id);
    }

    /** True for the card that is made of other cards, which have to be built as well. */
    public static boolean isQuartered(final String id) {
        return QUAD.equals(id);
    }

    public static CardContent create(final Context context, final GBDevice device,
                                     final SharedPreferences prefs) {
        return create(context, device, prefs, OEPLPrefs.contentType(prefs));
    }

    /** Builds one particular card, for the picker that shows them all at once. */
    public static CardContent create(final Context context, final GBDevice device,
                                     final SharedPreferences prefs, final String id) {
        final boolean beaufort = OEPLPrefs.windAsBeaufort(prefs);
        switch (id) {
            case DATE:
                return new OeplDateCard(weather(prefs), OEPLPrefs.showSun(prefs));
            case COUNT_DAYS:
                return counter(prefs, true);
            case COUNT_HOURS:
                return counter(prefs, false);
            case FORECAST:
                // a forecast of fewer than three days is not a forecast, whatever the
                // setting says; the card itself drops days a panel cannot hold
                return new OeplForecastCard(weather(prefs),
                        Math.max(3, OEPLPrefs.forecastDays(prefs)), beaufort);
            case RSS:
                return new OeplRssCard(OeplRssClient.cached(), OEPLPrefs.rssTitle(prefs),
                        OEPLPrefs.rssSummaries(prefs));
            case QR:
                return new OeplQrCard(OEPLPrefs.qrContent(prefs), OEPLPrefs.qrTitle(prefs));
            case CALENDAR:
                return new OeplCalendarListCard(events(context, device),
                        OEPLPrefs.calendarEvents(prefs), caption(context, prefs));
            case CALENDAR_WEEK:
                return new OeplCalendarWeekCard(events(context, device),
                        OEPLPrefs.calendarDays(prefs));
            case RAIN:
                return new OeplRainCard(weather(prefs), OEPLPrefs.forecastHours(prefs));
            case INFO:
                return info(context, device, prefs);
            case CLOCK:
                return new OeplClockCard();
            case BIG:
                return new OeplBigTemperatureCard(weather(prefs), beaufort);
            case RANGE:
                return new OeplRangeCard(weather(prefs),
                        Math.max(3, OEPLPrefs.forecastDays(prefs)));
            case SUN:
                return new OeplSunCard(weather(prefs));
            case TILES:
                return new OeplTilesCard(weather(prefs), beaufort);
            case BOARD:
                return new OeplBoardCard(events(context, device),
                        OEPLPrefs.calendarEvents(prefs), caption(context, prefs));
            case POSTER:
                return new OeplPosterCard(lines(OEPLPrefs.customText(prefs)));
            case GRID:
                return new OeplGridCard(readings(context, device, prefs),
                        device.getAliasOrName());
            case HERO:
                return new OeplHeroCard(readings(context, device, prefs),
                        upcoming(context, device, prefs));
            case LIST:
                return new OeplListCard(readings(context, device, prefs));
            case GRAPH:
                return new OeplGraphCard(weather(prefs), OEPLPrefs.forecastHours(prefs));
            case QUAD:
                return quarters(context, device, prefs);
            case MONTH:
                // the month, so the calendar is read for the whole of it rather than for the
                // week the other cards are configured for
                return new OeplMonthCard(monthEvents(context, device), caption(context, prefs));
            case WALL:
                return new OeplWallCard(weather(prefs), events(context, device),
                        forTheSheet(context, device, prefs), OEPLPrefs.calendarDays(prefs),
                        OEPLPrefs.forecastDays(prefs), beaufort);
            case WEATHER:
            default:
                return new OeplWeatherCard(weather(prefs), beaufort);
        }
    }

    /** The text card's own lines, trimmed and without the empty ones. */
    private static List<String> lines(final String raw) {
        final List<String> lines = new ArrayList<>();
        for (final String line : (raw == null ? "" : raw).split("\r?\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private static String caption(final Context context, final SharedPreferences prefs) {
        final String title = OEPLPrefs.calendarTitle(prefs);
        return title.isEmpty() ? context.getString(R.string.oepl_card_calendar) : title;
    }

    private static CardContent counter(final SharedPreferences prefs, final boolean days) {
        final long target = OEPLPrefs.counterTarget(prefs);
        final long remaining;
        if (target == 0L) {
            remaining = 0L;
        } else {
            final long millis = target - System.currentTimeMillis();
            remaining = days
                    ? (long) Math.ceil(millis / 86400000.0)
                    : (long) Math.ceil(millis / 3600000.0);
        }
        return new OeplCounterCard(remaining, OEPLPrefs.counterThreshold(prefs),
                OEPLPrefs.counterCaption(prefs));
    }

    /** Weather in the richer form, from our own fetch when configured, else from Gadgetbridge. */
    @Nullable
    public static OeplWeather weather(final SharedPreferences prefs) {
        if (OEPLPrefs.useOpenMeteo(prefs)) {
            final OeplWeather own = OpenMeteoClient.cachedOepl();
            if (own != null && own.hasData()) {
                return own;
            }
        }
        final List<WeatherSpec> specs = Weather.getWeatherSpecs();
        if (specs.isEmpty()) {
            return null;
        }
        return OeplWeather.from(specs.get(0));
    }

    private static List<CalendarEvent> events(final Context context, final GBDevice device) {
        try {
            return new CalendarManager(context, device.getAddress()).getCalendarEventList();
        } catch (final Exception e) {
            // usually a missing calendar permission, which must not take the card down
            LOG.warn("could not read the calendar", e);
            return Collections.emptyList();
        }
    }

    /**
     * Everything the dashboard cards draw, collected once and in one order. All three of them
     * take the same list, so switching between them never changes which values a display shows
     * - only how they are arranged.
     */
    private static List<OeplReading> readings(final Context context, final GBDevice device,
                                              final SharedPreferences prefs) {
        return OeplReadings.collect(context, device, prefs, weather(prefs),
                events(context, device), room(prefs));
    }

    /**
     * How many values the display the card is being built for can actually show.
     * <p>
     * The collector used to gather the same handful whatever it was being gathered for, which
     * left a seven inch panel showing eight values the size of playing cards while the rest of
     * the calendar sat unread on the phone. The display reported its own panel when it was
     * paired, so the number of tiles that panel divides into is known here - and that is how
     * many values are worth collecting.
     */
    private static int room(final SharedPreferences prefs) {
        final int hardware = prefs.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        final OEPLTagTypes.TagType type = hardware >= 0
                ? OEPLTagTypes.byHardwareType(hardware) : null;
        if (type == null || type.width < 64 || type.height < 64) {
            return OeplReadings.SMALL_PANEL; // nothing known about it yet
        }
        return OeplGridCard.maxTiles(type.width, type.height);
    }

    /**
     * The readings the wall sheet should still be given: everything it does not already draw
     * properly somewhere else on the panel.
     * <p>
     * The sheet writes the place and the day's range large across the top, and has the week
     * and the next appointments in regions of their own, so the weather and the agenda are
     * both out. A column repeating either of them is the sheet saying one thing twice, which
     * is the one rule it has.
     */
    private static List<OeplReading> forTheSheet(final Context context, final GBDevice device,
                                                 final SharedPreferences prefs) {
        return OeplReadings.without(readings(context, device, prefs),
                OeplReadings.GROUP_WEATHER, OeplReadings.GROUP_EVENTS);
    }

    /**
     * The appointments of this month, which is a different question from the one the other
     * calendar cards ask: they show what is coming up over the next few days, the month sheet
     * marks the days of a whole month. The lookahead setting is therefore not what decides it.
     */
    private static List<CalendarEvent> monthEvents(final Context context,
                                                   final GBDevice device) {
        try {
            final java.util.Calendar end = java.util.Calendar.getInstance();
            final int left = end.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    - end.get(java.util.Calendar.DAY_OF_MONTH);
            return new CalendarManager(context, device.getAddress())
                    .getCalendarEventList(Math.max(1, left + 1));
        } catch (final Exception e) {
            LOG.warn("could not read the calendar for the month", e);
            return Collections.emptyList();
        }
    }

    /** The appointments, cut to the number the card is configured for. */
    private static List<CalendarEvent> upcoming(final Context context, final GBDevice device,
                                                final SharedPreferences prefs) {
        final List<CalendarEvent> events = events(context, device);
        final int wanted = Math.max(1, OEPLPrefs.calendarEvents(prefs));
        return events.size() <= wanted ? events : events.subList(0, wanted);
    }

    /**
     * The quartered card. Each quarter is built by the ordinary content factory, so a quarter
     * can hold anything the display can show - except another quartered card, which is left out
     * rather than drawn: nesting it would halve the panel again and again.
     */
    private static CardContent quarters(final Context context, final GBDevice device,
                                        final SharedPreferences prefs) {
        final List<String> slots = new ArrayList<>(OEPLPrefs.PREF_QUAD.length);
        for (int i = 0; i < OEPLPrefs.PREF_QUAD.length; i++) {
            final String id = OEPLPrefs.quadSlot(prefs, i);
            if (!id.isEmpty() && !QUAD.equals(id)) {
                slots.add(id);
            }
        }
        // Whether one of the quarters is the calendar, which decides what the dashboard
        // quarters put in their lists. Choosing the calendar for one quarter and the values
        // for another is one decision and not two: the values are a list this project fills,
        // and what it puts at the end of it is the next few appointments.
        boolean calendar = false;
        for (final String id : slots) {
            calendar |= nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather
                    .CardContentFactory.drawsTheCalendar(id);
        }
        final List<CardContent> parts = new ArrayList<>(slots.size());
        for (final String id : slots) {
            final CardContent part = quarter(context, device, prefs, id,
                    calendar && isDashboard(id));
            if (part != null) {
                parts.add(part);
            }
        }
        return new QuadContent(parts);
    }

    /** The three cards that are nothing but a list of readings, in one shape or another. */
    private static boolean isDashboard(final String id) {
        return GRID.equals(id) || HERO.equals(id) || LIST.equals(id);
    }

    /**
     * One quarter, or null when it could not be built - which must not take the other three
     * down with it.
     *
     * @param withoutAppointments true to leave the agenda out of a dashboard quarter, because
     *                            another quarter is the calendar
     */
    @Nullable
    private static CardContent quarter(final Context context, final GBDevice device,
                                       final SharedPreferences prefs, final String id,
                                       final boolean withoutAppointments) {
        try {
            if (withoutAppointments) {
                final List<OeplReading> rest = OeplReadings.without(
                        readings(context, device, prefs), OeplReadings.GROUP_EVENTS);
                if (HERO.equals(id)) {
                    return new OeplHeroCard(rest, upcoming(context, device, prefs));
                }
                if (LIST.equals(id)) {
                    return new OeplListCard(rest);
                }
                return new OeplGridCard(rest, device.getAliasOrName());
            }
            return nodomain.freeyourgadget.gadgetbridge.service.devices
                    .atcbleoeplweather.CardContentFactory.create(context, device, prefs, id);
        } catch (final Exception e) {
            LOG.warn("could not build {} for a quarter of the panel", id, e);
            return null;
        }
    }

    /** What the display itself and the phone have to report. */
    private static CardContent info(final Context context, final GBDevice device,
                                    final SharedPreferences prefs) {
        final List<OeplInfoCard.Row> rows = new ArrayList<>();
        final int hardware = prefs.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        final OEPLTagTypes.TagType type = hardware >= 0
                ? OEPLTagTypes.byHardwareType(hardware) : null;
        if (type != null) {
            rows.add(new OeplInfoCard.Row(context.getString(R.string.oepl_card_info_type),
                    type.name));
            rows.add(new OeplInfoCard.Row(context.getString(R.string.oepl_card_info_panel),
                    type.width + " x " + type.height + ", "
                            + context.getString(R.string.oepl_card_info_colors,
                            type.colorCount)));
        }
        final int firmware = prefs.getInt(OEPLPrefs.DETECTED_FIRMWARE, -1);
        if (firmware > 0) {
            rows.add(new OeplInfoCard.Row(context.getString(R.string.oepl_card_info_firmware),
                    String.valueOf(firmware)));
        }
        final int battery = prefs.getInt(OEPLPrefs.LAST_BATTERY_MV, -1);
        if (battery > 0) {
            rows.add(new OeplInfoCard.Row(context.getString(R.string.oepl_card_info_battery),
                    String.format(Locale.ROOT, "%.2f V (%d%%)",
                            battery / 1000f, OEPLPrefs.batteryPercent(battery))));
        }
        final int temperature = prefs.getInt(OEPLPrefs.LAST_PANEL_TEMPERATURE, -100);
        if (temperature > -100) {
            rows.add(new OeplInfoCard.Row(
                    context.getString(R.string.oepl_card_info_temperature), temperature + " °C"));
        }
        rows.add(new OeplInfoCard.Row("MAC", device.getAddress()));
        final int phone = PhoneStateStore.phoneBattery(GBApplication.getContext());
        if (phone >= 0) {
            rows.add(new OeplInfoCard.Row(context.getString(R.string.oepl_card_phone),
                    phone + "%"));
        }
        rows.add(new OeplInfoCard.Row(context.getString(R.string.oepl_card_info_updated),
                new java.text.SimpleDateFormat(
                "dd.MM. HH:mm", Locale.getDefault()).format(new java.util.Date())));
        return new OeplInfoCard(device.getAliasOrName(), rows);
    }
}
