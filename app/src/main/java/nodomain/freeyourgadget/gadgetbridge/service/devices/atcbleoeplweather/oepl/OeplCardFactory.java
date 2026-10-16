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

    private OeplCardFactory() {
    }

    public static boolean handles(final String id) {
        return id != null && (id.startsWith(PREFIX) || id.startsWith(OWN_PREFIX));
    }

    /** True for the cards that need a weather forecast to say anything. */
    public static boolean needsWeather(final String id) {
        return WEATHER.equals(id) || FORECAST.equals(id) || RAIN.equals(id) || DATE.equals(id)
                || BIG.equals(id) || RANGE.equals(id) || SUN.equals(id) || TILES.equals(id);
    }

    public static boolean needsFeed(final String id) {
        return RSS.equals(id);
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
                return new OeplForecastCard(weather(prefs), OEPLPrefs.forecastDays(prefs) + 2,
                        beaufort);
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
                return new OeplRangeCard(weather(prefs), OEPLPrefs.forecastDays(prefs) + 2);
            case SUN:
                return new OeplSunCard(weather(prefs));
            case TILES:
                return new OeplTilesCard(weather(prefs), beaufort);
            case BOARD:
                return new OeplBoardCard(events(context, device),
                        OEPLPrefs.calendarEvents(prefs), caption(context, prefs));
            case POSTER:
                return new OeplPosterCard(lines(OEPLPrefs.customText(prefs)));
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
