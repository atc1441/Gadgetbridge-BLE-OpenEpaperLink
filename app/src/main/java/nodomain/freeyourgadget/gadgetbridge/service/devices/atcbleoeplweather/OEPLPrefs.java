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

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

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
    /** How often an update that could not be delivered is tried again until it works. */
    public static final String PREF_CATCH_UP = "pref_oepl_weather_catch_up";
    /** The hours in which the display is left alone; see {@link OEPLQuietHours}. */
    public static final String PREF_QUIET = "pref_oepl_weather_quiet";
    public static final String PREF_QUIET_START = "pref_oepl_weather_quiet_start";
    public static final String PREF_QUIET_END = "pref_oepl_weather_quiet_end";
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
    /** How far the picture is magnified beyond the fit, 1 for none. */
    public static final String PREF_IMAGE_ZOOM = "pref_oepl_weather_image_zoom";
    /** Where the magnified picture sits, -1 to 1 across the room it has to move in. */
    public static final String PREF_IMAGE_PAN_X = "pref_oepl_weather_image_pan_x";
    public static final String PREF_IMAGE_PAN_Y = "pref_oepl_weather_image_pan_y";
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

    /** Which groups of values the dashboard cards collect: the sky, the house, agenda, batteries. */
    public static final String PREF_READING_GROUPS = "pref_oepl_weather_reading_groups";
    public static final String PREF_HA_URL = "pref_oepl_weather_ha_url";
    public static final String PREF_HA_TOKEN = "pref_oepl_weather_ha_token";
    public static final String PREF_HA_ENTITIES = "pref_oepl_weather_ha_entities";
    /** Opens the picker that lists what the instance has, rather than typing ids out. */
    public static final String PREF_HA_PICK = "pref_oepl_weather_ha_pick";
    /** Opens the place search; see {@link #rememberPlace}. */
    public static final String PREF_PLACE_PICK = "pref_oepl_weather_location_pick";
    /** Opens the screen with the one-off commands: blink, restart, sleep, new firmware. */
    public static final String PREF_TOOLS = "pref_oepl_weather_tools";
    /**
     * Not a setting but the word the service is notified with when a display has been asked to
     * do something; see {@link OEPLActions}.
     */
    public static final String PREF_ACTION = "pref_oepl_weather_action";
    /**
     * Likewise not a setting but the word the service is notified with when a display that is
     * on the line right now has just been handed a firmware; see {@link OEPLFirmware}.
     */
    public static final String PREF_FIRMWARE_START = "pref_oepl_weather_firmware_start";
    /** Which colour this display blinks in, so that two of them can be told apart. */
    public static final String PREF_LED_COLOR = "pref_oepl_weather_led_color";
    /** How the blink is shaped; all three live on the commands screen, not in the list. */
    public static final String PREF_LED_FLASHES = "pref_oepl_weather_led_flashes";
    public static final String PREF_LED_REPEATS = "pref_oepl_weather_led_repeats";
    public static final String PREF_LED_BRIGHTNESS = "pref_oepl_weather_led_brightness";
    /** The whole twelve byte pattern as hex, once it has been shaped by hand. */
    public static final String PREF_LED_PATTERN = "pref_oepl_weather_led_pattern";
    /** A pattern for the next blink only; see {@link #takeLedPatternOnce}. */
    static final String LED_PATTERN_ONCE = "oepl_weather_led_once";
    /** The four slots of the quartered card, each holding the id of another card. */
    public static final String[] PREF_QUAD = {
            "pref_oepl_weather_quad_1", "pref_oepl_weather_quad_2",
            "pref_oepl_weather_quad_3", "pref_oepl_weather_quad_4"};

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
            PREF_READING_GROUPS,
            PREF_HA_URL,
            PREF_HA_TOKEN,
            PREF_HA_ENTITIES,
            PREF_QUAD[0],
            PREF_QUAD[1],
            PREF_QUAD[2],
            PREF_QUAD[3],
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
            PREF_CATCH_UP,
            PREF_QUIET,
            PREF_QUIET_START,
            PREF_QUIET_END,
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

    /** The place a picked location was called, and the coordinates it belongs to. */
    public static final String PLACE_NAME = "oepl_weather_place_name";
    public static final String PLACE_FOR = "oepl_weather_place_for";

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
     * What the identification mode writes into the corner of this display, or null while the
     * mode is off.
     * <p>
     * The Bluetooth name rather than the alias: the alias is what the user called the display
     * and is already on the card in the device list, while the name is what a scan shows and
     * what the list has to be matched against when a panel and its entry have been mixed up.
     * An unnamed display falls back to its address, which is the only thing left to go by.
     */
    @Nullable
    public static String nameTag(final GBDevice device) {
        if (!GBApplication.getPrefs().getShowDisplayNameTags()) {
            return null;
        }
        final String name = device.getName();
        return name == null || name.trim().isEmpty() ? device.getAddress() : name.trim();
    }

    /**
     * How often a new picture is drawn and offered to the tag. An hour by default: a weather
     * card, a forecast and an agenda are all things that change on the scale of hours, an
     * update only costs a connection when the picture has actually changed, and what makes a
     * display feel current is not a short interval but the catch-up gap below - a picture goes
     * out the moment the display is within reach, whichever hour that happens to be.
     */
    public static int updateIntervalMinutes(final SharedPreferences prefs) {
        return parseInt(prefs.getString(PREF_UPDATE_INTERVAL, "60"), 60);
    }

    /** The same figure as a duration, never zero, so it can be added to a point in time. */
    public static long updateIntervalMillis(final SharedPreferences prefs) {
        return Math.max(1, updateIntervalMinutes(prefs)) * 60_000L;
    }

    /**
     * How long the scheduler waits between two attempts while an update is owed and has not
     * got through - because the display was out of range, because the phone was elsewhere, or
     * because the night was quiet and the morning has only just started.
     * <p>
     * This is what makes a long interval usable. A display set to once a day must not mean that
     * a day in town costs the picture until tomorrow: the moment it is owed, the gap between
     * attempts stops following the interval and follows this instead, which is why fifteen
     * minutes is the default. The ladder in the scheduler still starts within the minute, so a
     * tag that was merely asleep is caught at once; this is only the floor it settles on.
     */
    public static int catchUpMinutes(final SharedPreferences prefs) {
        final int minutes = parseInt(prefs.getString(PREF_CATCH_UP, "15"), 15);
        return Math.max(1, Math.min(240, minutes));
    }

    public static long catchUpMillis(final SharedPreferences prefs) {
        return catchUpMinutes(prefs) * 60_000L;
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

    /**
     * How many days of forecast a card draws - the number on the setting, not a number derived
     * from it. Five by default, which is what the cards showed before this was the days
     * themselves, and up to the seven Open-Meteo delivers: a week on a panel large enough to
     * hold it is most of the reason to hang a large panel.
     */
    public static int forecastDays(final SharedPreferences prefs) {
        final int days = parseInt(prefs.getString(PREF_FORECAST_DAYS, "5"), 5);
        return Math.max(0, Math.min(7, days));
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

    /** How far into the picture the panel looks; 1 is the whole of it, as the fit chose it. */
    public static float imageZoom(final SharedPreferences prefs) {
        return Math.max(1f, Math.min(8f, prefs.getFloat(PREF_IMAGE_ZOOM, 1f)));
    }

    /** Which part of it: -1 is hard left or top, 1 hard right or bottom, 0 the middle. */
    public static float imagePanX(final SharedPreferences prefs) {
        return Math.max(-1f, Math.min(1f, prefs.getFloat(PREF_IMAGE_PAN_X, 0f)));
    }

    public static float imagePanY(final SharedPreferences prefs) {
        return Math.max(-1f, Math.min(1f, prefs.getFloat(PREF_IMAGE_PAN_Y, 0f)));
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

    /**
     * What the place picked from the list was called, for a location that is stored as
     * coordinates.
     * <p>
     * It only answers while it still belongs to what is in the location field. Somebody who
     * types a different place over a picked one gets the name they typed, not the name of the
     * town they just replaced - which is otherwise exactly the kind of wrong that is hard to
     * notice, because the panel keeps heading its forecast with a plausible place name.
     */
    public static String weatherPlaceName(final SharedPreferences prefs) {
        final String location = weatherLocation(prefs);
        if (location.isEmpty()
                || !location.equals(prefs.getString(PLACE_FOR, ""))) {
            return "";
        }
        return prefs.getString(PLACE_NAME, "");
    }

    /** Remembers a picked place: the coordinates it is stored as, and what it is called. */
    public static void rememberPlace(final String address, final String coordinates,
                                     final String name) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putString(PREF_WEATHER_LOCATION, coordinates)
                .putString(PLACE_FOR, coordinates)
                .putString(PLACE_NAME, name)
                .apply();
    }

    /** Beaufort by default, the way an OpenEPaperLink tag reports wind. */
    public static boolean windAsBeaufort(final SharedPreferences prefs) {
        return !"ms".equals(prefs.getString(PREF_WIND_UNIT, "beaufort"));
    }

    /**
     * Which groups of values the dashboard cards gather. All four by default: a display that
     * was just paired should show what it can, and switching a group off is how a tag in the
     * hall is told to leave the step counts to the one on the desk.
     */
    public static java.util.Set<String> readingGroups(final SharedPreferences prefs) {
        final java.util.Set<String> all = new java.util.LinkedHashSet<>(Arrays.asList(
                "weather", "house", "events", "devices"));
        try {
            final java.util.Set<String> chosen = prefs.getStringSet(PREF_READING_GROUPS, null);
            return chosen == null ? all : chosen;
        } catch (final Exception ignored) {
            // a value of a different type under that key, which a hand edited backup can carry
            return all;
        }
    }

    public static String homeAssistantUrl(final SharedPreferences prefs) {
        return prefs.getString(PREF_HA_URL, "").trim();
    }

    public static String homeAssistantToken(final SharedPreferences prefs) {
        return prefs.getString(PREF_HA_TOKEN, "").trim();
    }

    /**
     * The colour this display blinks in. Red by default, which is the one colour every one of
     * these tags can make - the green and blue legs are not fitted on all of them.
     */
    public static int ledColor(final SharedPreferences prefs) {
        switch (prefs.getString(PREF_LED_COLOR, "red")) {
            case "green":
                return OEPLProtocol.LED_GREEN;
            case "blue":
                return OEPLProtocol.LED_BLUE;
            case "yellow":
                return OEPLProtocol.LED_YELLOW;
            case "white":
                return OEPLProtocol.LED_WHITE;
            default:
                return OEPLProtocol.LED_RED;
        }
    }

    /**
     * How the blink is shaped, and why these are stored per display rather than once.
     * <p>
     * Pointing at one display out of a shelf full of them is what the LED is for, and a shelf
     * is exactly where one blink has to be told apart from the next: three red flashes on one
     * and ten green ones on its neighbour. The figures are clamped where the firmware's own
     * fields end - four bits for the flashes in a group, one byte for the rounds - so a value
     * that was typed into the preferences by hand cannot make a pattern the tag reads as
     * something else.
     */
    public static int ledFlashes(final SharedPreferences prefs) {
        return clamped(prefs, PREF_LED_FLASHES, 3, 1, 15);
    }

    /** Rounds of the whole pattern. Never 256, which the firmware reads as "keep going". */
    public static int ledRepeats(final SharedPreferences prefs) {
        return clamped(prefs, PREF_LED_REPEATS, 5, 1, 200);
    }

    public static int ledBrightness(final SharedPreferences prefs) {
        return clamped(prefs, PREF_LED_BRIGHTNESS, OEPLProtocol.FULL_BRIGHTNESS, 1,
                OEPLProtocol.FULL_BRIGHTNESS);
    }

    private static int clamped(final SharedPreferences prefs, final String key,
                               final int fallback, final int least, final int most) {
        return Math.max(least, Math.min(most,
                parseInt(prefs.getString(key, String.valueOf(fallback)), fallback)));
    }

    /**
     * The blink this display makes, as the twelve bytes the tag plays.
     * <p>
     * A pattern that was shaped by hand is stored whole, because the three groups it is made
     * of cannot be said in a colour and two counts. Where there is none, the simple choices
     * are built into the shape they always had - which is what every display that nobody has
     * opened the editor for keeps blinking.
     */
    public static byte[] ledPattern(final SharedPreferences prefs) {
        final byte[] stored =
                OEPLProtocol.ledPatternFromHex(prefs.getString(PREF_LED_PATTERN, ""));
        if (stored != null) {
            return stored;
        }
        // a saved pattern that was made the default stands in for every display nobody has
        // shaped by hand, so a whole shelf can be given one look without visiting each panel
        final byte[] fallback = OEPLBlinkPresets.defaultPattern();
        if (fallback != null) {
            return fallback;
        }
        return OEPLProtocol.ledPatternArgument(ledColor(prefs), ledFlashes(prefs),
                ledRepeats(prefs), ledBrightness(prefs));
    }

    /**
     * A pattern for the next blink and no longer.
     * <p>
     * The buttons on the device cards blink a saved pattern at a display without making it
     * that display's own: pointing at a panel from the main list is a thing done once, and a
     * display whose blink quietly changed because somebody looked for it would be a setting
     * nobody asked to change.
     */
    public static void blinkOnceWith(final String address, final byte[] pattern) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putString(LED_PATTERN_ONCE, OEPLProtocol.ledPatternToHex(pattern))
                .apply();
    }

    /** The one-off pattern if there is one, consumed; otherwise this display's own. */
    public static byte[] takeLedPatternOnce(final SharedPreferences prefs, final String address) {
        final byte[] once =
                OEPLProtocol.ledPatternFromHex(prefs.getString(LED_PATTERN_ONCE, ""));
        if (once == null) {
            return ledPattern(prefs);
        }
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .remove(LED_PATTERN_ONCE)
                .apply();
        return once;
    }

    public static void storeLedPattern(final String address, final byte[] pattern) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putString(PREF_LED_PATTERN, OEPLProtocol.ledPatternToHex(pattern))
                .apply();
    }

    /** Back to the plain three-flashes blink, whatever was shaped on top of it. */
    public static void forgetLedPattern(final String address) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .remove(PREF_LED_PATTERN)
                .apply();
    }

    public static String homeAssistantEntities(final SharedPreferences prefs) {
        return prefs.getString(PREF_HA_ENTITIES, "");
    }

    /**
     * The address and the token last typed into any display, kept so that the next display
     * starts out filled in.
     * <p>
     * Every display keeps its own address and its own token - two instances, or a display that
     * is meant to read nothing at all, have to stay possible - but there is normally one Home
     * Assistant in a flat and the token is a hundred and eighty characters of random text that
     * nobody types twice. So the first one that is set is remembered here and written into the
     * next display as its starting value, which it can then be changed away from like any
     * other setting.
     */
    private static final String LAST_HA_URL = "oepl_weather_last_ha_url";
    private static final String LAST_HA_TOKEN = "oepl_weather_last_ha_token";

    /**
     * Remembers this display's address and token as the starting point for the next one.
     * <p>
     * An emptied field is not remembered: clearing the address on the display in the hall says
     * what that display should read, not that the next display should start out blank.
     */
    public static void rememberHomeAssistant(@Nullable final SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        final SharedPreferences.Editor editor = GBApplication.getPrefs().getPreferences().edit();
        final String url = homeAssistantUrl(prefs);
        final String token = homeAssistantToken(prefs);
        if (!url.isEmpty()) {
            editor.putString(LAST_HA_URL, url);
        }
        if (!token.isEmpty()) {
            editor.putString(LAST_HA_TOKEN, token);
        }
        editor.apply();
    }

    /**
     * Gives a display that has never been told about Home Assistant the last known address and
     * token, and says whether it got anything.
     * <p>
     * Only a field that was never set is filled, which is what keeps this from undoing a
     * decision: a display whose address was deliberately cleared holds an empty value rather
     * than no value, and is left alone from then on.
     */
    public static boolean seedHomeAssistant(@Nullable final SharedPreferences prefs) {
        if (prefs == null) {
            return false;
        }
        final SharedPreferences remembered = GBApplication.getPrefs().getPreferences();
        final SharedPreferences.Editor editor = prefs.edit();
        boolean filled = false;
        if (!prefs.contains(PREF_HA_URL)) {
            final String url = remembered.getString(LAST_HA_URL, "");
            if (!url.isEmpty()) {
                editor.putString(PREF_HA_URL, url);
                filled = true;
            }
        }
        if (!prefs.contains(PREF_HA_TOKEN)) {
            final String token = remembered.getString(LAST_HA_TOKEN, "");
            if (!token.isEmpty()) {
                editor.putString(PREF_HA_TOKEN, token);
                filled = true;
            }
        }
        if (filled) {
            editor.apply();
        }
        return filled;
    }

    /** As {@link #seedHomeAssistant(SharedPreferences)}, for a display that is not on screen. */
    public static boolean seedHomeAssistant(final String address) {
        return seedHomeAssistant(GBApplication.getDeviceSpecificSharedPrefs(address));
    }

    /** The card in one quarter of the quartered design, or an empty string for none. */
    public static String quadSlot(final SharedPreferences prefs, final int index) {
        if (index < 0 || index >= PREF_QUAD.length) {
            return "";
        }
        return prefs.getString(PREF_QUAD[index], "").trim();
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
