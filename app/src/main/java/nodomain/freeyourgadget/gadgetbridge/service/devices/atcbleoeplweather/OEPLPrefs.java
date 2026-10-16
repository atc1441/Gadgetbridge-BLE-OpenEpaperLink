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

import android.content.SharedPreferences;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ImageContent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/** Preference keys and the few derived values the weather display needs. */
public final class OEPLPrefs {
    // user facing settings
    public static final String PREF_UPDATE_INTERVAL = "pref_oepl_weather_update_interval";
    public static final String PREF_ROTATION = "pref_oepl_weather_rotation";
    public static final String PREF_ACCENT_COLOR = "pref_oepl_weather_accent";
    public static final String PREF_FORECAST_DAYS = "pref_oepl_weather_forecast_days";
    public static final String PREF_SHOW_FOOTER = "pref_oepl_weather_show_footer";
    public static final String PREF_STAY_CONNECTED = "pref_oepl_weather_stay_connected";
    public static final String PREF_MODEL = "pref_oepl_weather_model";
    public static final String PREF_ADV_INTERVAL = "pref_oepl_weather_adv_interval";
    public static final String PREF_OEPL_PROTOCOL = "pref_oepl_weather_oepl_protocol";
    public static final String PREF_READ_CONFIG = "pref_oepl_weather_read_config";
    public static final String PREF_INVERT = "pref_oepl_weather_invert";
    public static final String PREF_CONTENT = "pref_oepl_weather_content";
    public static final String PREF_CUSTOM_TEXT = "pref_oepl_weather_custom_text";
    public static final String PREF_CALENDAR_EVENTS = "pref_oepl_weather_calendar_events";
    public static final String PREF_NOTIFICATION_COUNT = "pref_oepl_weather_notification_count";
    public static final String PREF_FORECAST_HOURS = "pref_oepl_weather_forecast_hours";
    public static final String PREF_FRAME = "pref_oepl_weather_frame";
    public static final String PREF_LIVE_UPDATES = "pref_oepl_weather_live_updates";
    public static final String PREF_MIN_GAP = "pref_oepl_weather_min_gap";
    public static final String PREF_WEATHER_SOURCE = "pref_oepl_weather_source";
    public static final String PREF_WEATHER_LOCATION = "pref_oepl_weather_location";
    // how hard the app tries to reach the display on its own
    public static final String PREF_OPPORTUNISTIC = "pref_oepl_weather_opportunistic";
    public static final String PREF_REQUIRE_ADVERTISEMENT = "pref_oepl_weather_require_adv";
    public static final String PREF_IMAGE_URI = "pref_oepl_weather_image_uri";
    public static final String PREF_IMAGE_URL = "pref_oepl_weather_image_url";
    public static final String PREF_IMAGE_DITHER = "pref_oepl_weather_image_dither";
    public static final String PREF_IMAGE_FIT = "pref_oepl_weather_image_fit";
    public static final String PREF_IMAGE_PICK = "pref_oepl_weather_image_pick";
    public static final String PREF_CONNECT_WINDOW = "pref_oepl_weather_connect_window";
    public static final String PREF_CONNECT_ATTEMPTS = "pref_oepl_weather_connect_attempts";
    // the settings the OpenEPaperLink style cards need
    public static final String PREF_WIND_UNIT = "pref_oepl_weather_wind_unit";
    public static final String PREF_SHOW_SUN = "pref_oepl_weather_show_sun";
    public static final String PREF_COUNTER_TARGET = "pref_oepl_weather_counter_target";
    public static final String PREF_COUNTER_THRESHOLD = "pref_oepl_weather_counter_threshold";
    public static final String PREF_COUNTER_CAPTION = "pref_oepl_weather_counter_caption";
    public static final String PREF_RSS_URL = "pref_oepl_weather_rss_url";
    public static final String PREF_RSS_TITLE = "pref_oepl_weather_rss_title";
    public static final String PREF_RSS_SUMMARY = "pref_oepl_weather_rss_summary";
    public static final String PREF_QR_CONTENT = "pref_oepl_weather_qr_content";
    public static final String PREF_QR_TITLE = "pref_oepl_weather_qr_title";
    public static final String PREF_CALENDAR_TITLE = "pref_oepl_weather_calendar_title";
    public static final String PREF_CALENDAR_DAYS = "pref_oepl_weather_calendar_days";

    /**
     * Every setting of this display that the service has to hear about when it changes. The
     * settings screen notifies the device for the keys on this list and for no others, and
     * {@code ATCBLEOEPLWeatherDeviceSupport.onSendConfiguration} then decides what to do with
     * each - which for almost all of them means drawing the card again and offering it to the
     * tag, so that a setting can be judged by looking at the panel instead of by reading the
     * option. A new setting belongs here; {@code OEPLSettingsMenuTest} fails if one is left out.
     * <p>
     * The picture chooser and the hardware read are not values but buttons, and notify
     * themselves when they are pressed.
     */
    public static final List<String> NOTIFYING_KEYS = Collections.unmodifiableList(Arrays.asList(
            PREF_CONTENT,
            PREF_WEATHER_SOURCE,
            PREF_WEATHER_LOCATION,
            PREF_FORECAST_DAYS,
            PREF_FORECAST_HOURS,
            PREF_WIND_UNIT,
            PREF_SHOW_SUN,
            PREF_CALENDAR_EVENTS,
            PREF_CALENDAR_DAYS,
            PREF_CALENDAR_TITLE,
            PREF_NOTIFICATION_COUNT,
            PREF_CUSTOM_TEXT,
            PREF_COUNTER_TARGET,
            PREF_COUNTER_THRESHOLD,
            PREF_COUNTER_CAPTION,
            PREF_RSS_URL,
            PREF_RSS_TITLE,
            PREF_RSS_SUMMARY,
            PREF_QR_CONTENT,
            PREF_QR_TITLE,
            PREF_IMAGE_URL,
            PREF_IMAGE_DITHER,
            PREF_IMAGE_FIT,
            PREF_FRAME,
            PREF_SHOW_FOOTER,
            PREF_ACCENT_COLOR,
            PREF_ROTATION,
            PREF_INVERT,
            PREF_UPDATE_INTERVAL,
            PREF_LIVE_UPDATES,
            PREF_MIN_GAP,
            PREF_CONNECT_WINDOW,
            PREF_CONNECT_ATTEMPTS,
            PREF_OPPORTUNISTIC,
            PREF_REQUIRE_ADVERTISEMENT,
            PREF_STAY_CONNECTED,
            PREF_ADV_INTERVAL,
            PREF_OEPL_PROTOCOL,
            PREF_MODEL));

    // values cached between connections so that the scheduler can work without a link
    public static final String PANEL_WIDTH = "oepl_weather_panel_width";
    public static final String PANEL_HEIGHT = "oepl_weather_panel_height";
    public static final String PANEL_COLORS = "oepl_weather_panel_colors";
    public static final String LAST_IMAGE_CRC = "oepl_weather_last_crc";
    public static final String LAST_UPDATE_MILLIS = "oepl_weather_last_update";
    public static final String LAST_BATTERY_MV = "oepl_weather_battery_mv";
    public static final String LAST_PANEL_TEMPERATURE = "oepl_weather_panel_temperature";
    public static final String LAST_SEEN_MILLIS = "oepl_weather_last_seen";

    /** Rough end points of the coin cell these tags ship with. */
    private static final int BATTERY_EMPTY_MV = 2400;
    private static final int BATTERY_FULL_MV = 3100;

    private OEPLPrefs() {
    }

    /**
     * How often a new picture is drawn and offered to the tag. Fifteen minutes by default: an
     * update only costs a connection when the picture has actually changed and the display is
     * in range, so a short interval mostly buys a clock and a current reading that are right,
     * and a card that waits an hour for a changed appointment is of little use.
     */
    public static int updateIntervalMinutes(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_UPDATE_INTERVAL, "15"), 15);
    }

    /** Rotation in degrees, or -1 when it should be taken from the display type. */
    public static int rotation(final SharedPreferences prefs) {
        final int value = parseInt(prefs.getString(PREF_ROTATION, "-1"), -1);
        if (value < 0) {
            return -1;
        }
        return ((value % 360) + 360) % 360 / 90 * 90;
    }

    /** One of "auto", "red" or "yellow". */
    public static String accentMode(final SharedPreferences prefs) {
        return prefs.getString(PREF_ACCENT_COLOR, "auto");
    }

    /** The OpenEPaperLink hardware type the user forced, or -1 for automatic. */
    public static int model(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_MODEL, "-1"), -1);
    }

    public static int forecastDays(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_FORECAST_DAYS, "3"), 3);
    }

    /** Which card the display shows; see CardContentFactory for the known values. */
    public static String contentType(final SharedPreferences prefs) {
        return prefs.getString(PREF_CONTENT, "oepl_4");
    }

    public static String customText(final SharedPreferences prefs) {
        return prefs.getString(PREF_CUSTOM_TEXT, "");
    }

    public static int calendarEvents(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_CALENDAR_EVENTS, "4"), 4);
    }

    /** Push a new picture as soon as something arrives, instead of waiting for the interval. */
    public static boolean liveUpdates(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_LIVE_UPDATES, true);
    }

    /** Shortest time between two pushes, so a burst of notifications cannot thrash the panel. */
    public static int minGapMinutes(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_MIN_GAP, "5"), 5);
    }

    /**
     * Let the Bluetooth controller listen for the display in the background and push an overdue
     * picture the moment it is heard, rather than waiting for the next timer.
     */
    public static boolean opportunisticUpdates(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_OPPORTUNISTIC, true);
    }

    /**
     * Whether an advertisement has to be heard before a connection is attempted. Off by default:
     * Android throttles what a background app may scan for, so a silent scan says much less
     * about the display than it seems to, and skipping the attempt because of one is the main
     * reason updates used to go missing.
     */
    public static boolean requireAdvertisement(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_REQUIRE_ADVERTISEMENT, false);
    }

    /**
     * How long a single connection attempt may take, in milliseconds. The tag only advertises
     * every few seconds and is asleep in between, so a connection cannot be established at an
     * arbitrary moment: the radio has to keep trying until one of those advertisements comes
     * along. Android's default of five seconds is barely more than one advertising interval,
     * which is why an attempt used to fail while the display was sitting right next to the phone.
     */
    public static long connectWindowMillis(final SharedPreferences prefs) {
        final int seconds = parseInt(prefs.getString(PREF_CONNECT_WINDOW, "30"), 30);
        return Math.max(5, Math.min(120, seconds)) * 1000L;
    }

    /** How many times in a row a run may try to reach the display before giving up on it. */
    public static int connectAttempts(final SharedPreferences prefs) {
        final int attempts = parseInt(prefs.getString(PREF_CONNECT_ATTEMPTS, "3"), 3);
        return Math.max(1, Math.min(10, attempts));
    }

    /** The picture chosen on the phone, as a content URI, or empty when there is none. */
    public static String imageUri(final SharedPreferences prefs) {
        final String value = prefs.getString(PREF_IMAGE_URI, "");
        return value == null ? "" : value.trim();
    }

    public static String imageUrl(final SharedPreferences prefs) {
        final String value = prefs.getString(PREF_IMAGE_URL, "");
        return value == null ? "" : value.trim();
    }

    /**
     * How a picture is reduced to the panel's colours. Error diffusion by default: the whole
     * point of putting a photograph on a three colour panel is that it still looks like one.
     */
    public static ImageContent.Dither imageDither(final SharedPreferences prefs) {
        final String value = prefs.getString(PREF_IMAGE_DITHER, "diffusion");
        if ("none".equals(value)) {
            return ImageContent.Dither.NONE;
        }
        if ("ordered".equals(value)) {
            return ImageContent.Dither.ORDERED;
        }
        return ImageContent.Dither.DIFFUSION;
    }

    public static ImageContent.Fit imageFit(final SharedPreferences prefs) {
        final String value = prefs.getString(PREF_IMAGE_FIT, "contain");
        if ("cover".equals(value)) {
            return ImageContent.Fit.COVER;
        }
        if ("stretch".equals(value)) {
            return ImageContent.Fit.STRETCH;
        }
        return ImageContent.Fit.CONTAIN;
    }

    /** Fetch the forecast ourselves, the way the OpenEPaperLink access point does. */
    public static boolean useOpenMeteo(final SharedPreferences prefs) {
        return "openmeteo".equals(prefs.getString(PREF_WEATHER_SOURCE, "openmeteo"));
    }

    public static String weatherLocation(final SharedPreferences prefs) {
        return prefs.getString(PREF_WEATHER_LOCATION, "").trim();
    }

    /** Beaufort by default, the way an OpenEPaperLink tag reports wind. */
    public static boolean windAsBeaufort(final SharedPreferences prefs) {
        return !"ms".equals(prefs.getString(PREF_WIND_UNIT, "beaufort"));
    }

    /** Whether the date card carries sunrise, sunset and the moon along its bottom. */
    public static boolean showSun(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SHOW_SUN, true);
    }

    /**
     * The moment a countdown counts towards, as milliseconds, or 0 when nothing is set.
     * Accepts a plain date or a date with a time, which is what people actually type.
     */
    public static long counterTarget(final SharedPreferences prefs) {
        final String raw = prefs.getString(PREF_COUNTER_TARGET, "").trim();
        if (raw.isEmpty()) {
            return 0L;
        }
        final String[] patterns = {"yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd.MM.yyyy HH:mm",
                "dd.MM.yyyy", "dd.MM."};
        for (final String pattern : patterns) {
            try {
                final java.text.SimpleDateFormat format =
                        new java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT);
                format.setLenient(false);
                final java.util.Date parsed = format.parse(raw);
                if (parsed == null) {
                    continue;
                }
                final java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTime(parsed);
                if (!pattern.contains("yyyy")) {
                    // a day and month alone means the next time that date comes round
                    final java.util.Calendar now = java.util.Calendar.getInstance();
                    c.set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR));
                    if (c.getTimeInMillis() < now.getTimeInMillis()) {
                        c.add(java.util.Calendar.YEAR, 1);
                    }
                }
                return c.getTimeInMillis();
            } catch (final java.text.ParseException ignored) {
                // try the next shape
            }
        }
        return 0L;
    }

    public static int counterThreshold(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_COUNTER_THRESHOLD, "1000000"), 1000000);
    }

    public static String counterCaption(final SharedPreferences prefs) {
        return prefs.getString(PREF_COUNTER_CAPTION, "").trim();
    }

    public static String rssUrl(final SharedPreferences prefs) {
        return prefs.getString(PREF_RSS_URL, "").trim();
    }

    public static String rssTitle(final SharedPreferences prefs) {
        return prefs.getString(PREF_RSS_TITLE, "").trim();
    }

    public static boolean rssSummaries(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_RSS_SUMMARY, false);
    }

    public static String qrContent(final SharedPreferences prefs) {
        return prefs.getString(PREF_QR_CONTENT, "").trim();
    }

    public static String qrTitle(final SharedPreferences prefs) {
        return prefs.getString(PREF_QR_TITLE, "").trim();
    }

    public static String calendarTitle(final SharedPreferences prefs) {
        return prefs.getString(PREF_CALENDAR_TITLE, "").trim();
    }

    public static int calendarDays(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_CALENDAR_DAYS, "3"), 3);
    }

    public static int notificationCount(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_NOTIFICATION_COUNT, "4"), 4);
    }

    public static int forecastHours(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_FORECAST_HOURS, "12"), 12);
    }

    /** Which frame the card is drawn in. */
    public static CardRenderer.Frame frame(final SharedPreferences prefs) {
        try {
            return CardRenderer.Frame.valueOf(
                    prefs.getString(PREF_FRAME, "BARS").toUpperCase(java.util.Locale.ROOT));
        } catch (final Exception ignored) {
            return CardRenderer.Frame.BARS;
        }
    }

    /** Last resort for a panel whose black plane runs the other way round. */
    public static boolean invertImage(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_INVERT, false);
    }

    public static boolean showFooter(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SHOW_FOOTER, true);
    }

    public static boolean stayConnected(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_STAY_CONNECTED, false);
    }

    public static final String DETECTED_HARDWARE_TYPE = "oepl_weather_detected_hw_type";
    public static final String DETECTED_FIRMWARE = "oepl_weather_detected_firmware";

    public static void storePanel(final String address, final OEPLDisplayConfig config) {
        if (!config.valid) {
            return;
        }
        final SharedPreferences.Editor editor =
                GBApplication.getDeviceSpecificSharedPrefs(address).edit();
        editor.putInt(PANEL_WIDTH, config.bufferWidth)
                .putInt(PANEL_HEIGHT, config.bufferHeight)
                .putInt(PANEL_COLORS, config.colors)
                .putInt(DETECTED_HARDWARE_TYPE, config.hardwareType);
        if (config.bleAdvertisingInterval >= 100 && config.bleAdvertisingInterval <= 300000) {
            editor.putString(PREF_ADV_INTERVAL, String.valueOf(config.bleAdvertisingInterval));
            editor.putBoolean(PREF_OEPL_PROTOCOL, config.oeplEnabled);
        }
        editor.apply();
    }

    /** What the advertisement told us, stored so the rendered card can show it. */
    public static void storeAdvertisement(final String address, final int batteryMillivolts,
                                          final int temperature) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putInt(LAST_BATTERY_MV, batteryMillivolts)
                .putInt(LAST_PANEL_TEMPERATURE, temperature)
                .putLong(LAST_SEEN_MILLIS, System.currentTimeMillis())
                .apply();
    }

    /**
     * Everything the advertisement carries. The hardware type resolves against the
     * OpenEPaperLink catalogue, which gives geometry, palette and buffer rotation without a
     * single connection.
     */
    public static void storeFromAdvertisement(final String address,
                                              final OEPLUpdateScheduler.Advertisement ad) {
        final SharedPreferences.Editor editor =
                GBApplication.getDeviceSpecificSharedPrefs(address).edit();
        if (ad.batteryMillivolts > 1000 && ad.batteryMillivolts < 5000) {
            editor.putInt(LAST_BATTERY_MV, ad.batteryMillivolts);
        }
        if (ad.temperature > -60 && ad.temperature < 90) {
            editor.putInt(LAST_PANEL_TEMPERATURE, ad.temperature);
        }
        if (ad.hardwareType > 0) {
            editor.putInt(DETECTED_HARDWARE_TYPE, ad.hardwareType);
            final OEPLTagTypes.TagType type = OEPLTagTypes.byHardwareType(ad.hardwareType);
            if (type != null) {
                // a sensible geometry to fall back on if the display never answers
                editor.putInt(PANEL_WIDTH, type.width);
                editor.putInt(PANEL_HEIGHT, type.height);
                editor.putInt(PANEL_COLORS, Math.max(1, type.colorCount - 1));
            }
        }
        if (ad.firmwareVersion > 0) {
            editor.putInt(DETECTED_FIRMWARE, ad.firmwareVersion);
        }
        editor.putLong(LAST_SEEN_MILLIS, System.currentTimeMillis());
        editor.apply();
    }

    public static int batteryPercent(final int millivolts) {
        if (millivolts <= 0) {
            return 0;
        }
        final int pct = (millivolts - BATTERY_EMPTY_MV) * 100 / (BATTERY_FULL_MV - BATTERY_EMPTY_MV);
        return Math.max(0, Math.min(100, pct));
    }

    private static int parseInt(final String value, final int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }
}
