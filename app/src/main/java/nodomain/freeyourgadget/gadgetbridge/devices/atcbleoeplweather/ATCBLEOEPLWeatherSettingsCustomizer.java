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
package nodomain.freeyourgadget.gadgetbridge.devices.atcbleoeplweather;

import android.content.Context;
import android.os.Parcel;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.Collections;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.CardContentFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLContentOptions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLContentOptions.Option;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

/**
 * Keeps the display's settings down to what the selected card actually reads.
 * <p>
 * The options are grouped into sub-menus in the XML; this hides a whole menu while the current
 * content would not read a single setting in it, hides the individual options inside a menu the
 * same way, and writes what is configured into each menu's summary so the list reads like a
 * description of the display rather than a pile of switches.
 * <p>
 * Everything in here is driven by {@link OEPLContentOptions}, which is the only place that knows
 * which card reads what.
 */
public class ATCBLEOEPLWeatherSettingsCustomizer implements DeviceSpecificSettingsCustomizer {

    public static final String SCREEN_WEATHER = "pref_oepl_weather_screen_weather";
    public static final String SCREEN_CALENDAR = "pref_oepl_weather_screen_calendar";
    public static final String SCREEN_FEED = "pref_oepl_weather_screen_feed";
    public static final String SCREEN_QR = "pref_oepl_weather_screen_qr";
    public static final String SCREEN_COUNTER = "pref_oepl_weather_screen_counter";
    public static final String SCREEN_NOTIFICATIONS = "pref_oepl_weather_screen_notifications";
    public static final String SCREEN_TEXT = "pref_oepl_weather_screen_text";
    public static final String SCREEN_IMAGE = "pref_oepl_weather_screen_image";
    public static final String SCREEN_LOOK = "pref_oepl_weather_screen_look";
    public static final String SCREEN_UPDATES = "pref_oepl_weather_screen_updates";
    public static final String SCREEN_POWER = "pref_oepl_weather_screen_power";
    public static final String SCREEN_HARDWARE = "pref_oepl_weather_screen_hardware";

    /** The current weather card, same as the preference's own default. */
    private static final String DEFAULT_CONTENT = "oepl_4";

    @Override
    public void onPreferenceChange(final Preference preference,
                                   final DeviceSpecificSettingsHandler handler) {
        if (preference != null && OEPLPrefs.PREF_CONTENT.equals(preference.getKey())
                && preference.getSharedPreferences() != null) {
            apply(handler, preference.getSharedPreferences()
                    .getString(OEPLPrefs.PREF_CONTENT, DEFAULT_CONTENT));
        }
    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs,
                                  final String rootKey) {
        // Picking a different card has to rearrange the menu right away, so this listener is
        // chained onto the one the fragment installed rather than replacing it - the new value is
        // not in the preferences yet when it runs, which is why it is passed along explicitly.
        handler.addPreferenceHandlerFor(OEPLPrefs.PREF_CONTENT, (preference, newValue) -> {
            apply(handler, String.valueOf(newValue));
            return true;
        });
        // The minimum gap is only ever consulted while live updates are on.
        handler.addPreferenceHandlerFor(OEPLPrefs.PREF_LIVE_UPDATES, (preference, newValue) -> {
            show(handler, OEPLPrefs.PREF_MIN_GAP, Boolean.TRUE.equals(newValue));
            return true;
        });

        // Read from the preferences rather than from the picker: inside a sub-menu the picker is
        // not part of the tree at all, and the sub-menu still has to know which card it serves.
        apply(handler, prefs.getString(OEPLPrefs.PREF_CONTENT, DEFAULT_CONTENT));
    }

    /**
     * Applies the whole arrangement for a content id. Every lookup is allowed to come up empty:
     * the same method runs on the root screen, where the entire file is inflated, and on each
     * sub-screen, where only that one subtree exists.
     */
    private void apply(final DeviceSpecificSettingsHandler handler, final String content) {
        show(handler, SCREEN_WEATHER, OEPLContentOptions.usesAny(content, Option.WEATHER,
                Option.WIND_UNIT, Option.SUN_TIMES, Option.FORECAST_DAYS, Option.FORECAST_HOURS));
        show(handler, SCREEN_CALENDAR, OEPLContentOptions.usesAny(content, Option.CALENDAR_EVENTS,
                Option.CALENDAR_DAYS, Option.CALENDAR_TITLE));
        show(handler, SCREEN_FEED, OEPLContentOptions.uses(content, Option.FEED));
        show(handler, SCREEN_QR, OEPLContentOptions.uses(content, Option.QR));
        show(handler, SCREEN_COUNTER, OEPLContentOptions.uses(content, Option.COUNTER));
        show(handler, SCREEN_NOTIFICATIONS,
                OEPLContentOptions.uses(content, Option.NOTIFICATIONS));
        show(handler, SCREEN_TEXT, OEPLContentOptions.uses(content, Option.TEXT));
        show(handler, SCREEN_IMAGE, OEPLContentOptions.uses(content, Option.IMAGE));

        // the two picture cards differ in one thing only: where the picture comes from
        show(handler, OEPLPrefs.PREF_IMAGE_PICK,
                CardContentFactory.IMAGE.equals(content));
        show(handler, OEPLPrefs.PREF_IMAGE_URL,
                CardContentFactory.IMAGE_URL.equals(content));

        show(handler, OEPLPrefs.PREF_WEATHER_SOURCE,
                OEPLContentOptions.uses(content, Option.WEATHER));
        show(handler, OEPLPrefs.PREF_WEATHER_LOCATION,
                OEPLContentOptions.uses(content, Option.WEATHER));
        show(handler, OEPLPrefs.PREF_FORECAST_DAYS,
                OEPLContentOptions.uses(content, Option.FORECAST_DAYS));
        show(handler, OEPLPrefs.PREF_FORECAST_HOURS,
                OEPLContentOptions.uses(content, Option.FORECAST_HOURS));
        show(handler, OEPLPrefs.PREF_WIND_UNIT,
                OEPLContentOptions.uses(content, Option.WIND_UNIT));
        show(handler, OEPLPrefs.PREF_SHOW_SUN,
                OEPLContentOptions.uses(content, Option.SUN_TIMES));
        show(handler, OEPLPrefs.PREF_CALENDAR_EVENTS,
                OEPLContentOptions.uses(content, Option.CALENDAR_EVENTS));
        show(handler, OEPLPrefs.PREF_CALENDAR_DAYS,
                OEPLContentOptions.uses(content, Option.CALENDAR_DAYS));
        show(handler, OEPLPrefs.PREF_CALENDAR_TITLE,
                OEPLContentOptions.uses(content, Option.CALENDAR_TITLE));
        // The OpenEPaperLink layouts draw the whole panel themselves, title bar and status strip
        // included, so neither the frame nor the footer switch would change anything.
        show(handler, OEPLPrefs.PREF_FRAME, OEPLContentOptions.uses(content, Option.FRAME));
        show(handler, OEPLPrefs.PREF_SHOW_FOOTER,
                OEPLContentOptions.uses(content, Option.FRAME));

        final Preference live = handler.findPreference(OEPLPrefs.PREF_LIVE_UPDATES);
        if (live != null && live.getSharedPreferences() != null) {
            show(handler, OEPLPrefs.PREF_MIN_GAP,
                    OEPLPrefs.liveUpdates(live.getSharedPreferences()));
        }

        summaries(handler, content);
    }

    /** What each menu currently holds, so the list says something without being opened. */
    private void summaries(final DeviceSpecificSettingsHandler handler, final String content) {
        final Context context = handler.getContext();
        if (context == null) {
            return;
        }
        final String unset = context.getString(R.string.oepl_screen_unset);

        summary(handler, SCREEN_WEATHER, join(entry(handler, OEPLPrefs.PREF_WEATHER_SOURCE),
                text(handler, OEPLPrefs.PREF_WEATHER_LOCATION, unset)));
        summary(handler, SCREEN_CALENDAR, OEPLContentOptions.uses(content, Option.CALENDAR_DAYS)
                ? count(context, handler, OEPLPrefs.PREF_CALENDAR_DAYS, R.string.oepl_screen_days)
                : count(context, handler, OEPLPrefs.PREF_CALENDAR_EVENTS,
                        R.string.oepl_screen_events));
        summary(handler, SCREEN_FEED, text(handler, OEPLPrefs.PREF_RSS_URL, unset));
        summary(handler, SCREEN_QR, text(handler, OEPLPrefs.PREF_QR_CONTENT, unset));
        summary(handler, SCREEN_COUNTER, text(handler, OEPLPrefs.PREF_COUNTER_TARGET, unset));
        summary(handler, SCREEN_NOTIFICATIONS, count(context, handler,
                OEPLPrefs.PREF_NOTIFICATION_COUNT, R.string.oepl_screen_notifications));
        summary(handler, SCREEN_TEXT, firstLine(text(handler, OEPLPrefs.PREF_CUSTOM_TEXT, unset)));

        summary(handler, SCREEN_LOOK, OEPLContentOptions.uses(content, Option.FRAME)
                ? join(entry(handler, OEPLPrefs.PREF_FRAME),
                        entry(handler, OEPLPrefs.PREF_ROTATION))
                : join(entry(handler, OEPLPrefs.PREF_ACCENT_COLOR),
                        entry(handler, OEPLPrefs.PREF_ROTATION)));

        final Preference interval = handler.findPreference(OEPLPrefs.PREF_UPDATE_INTERVAL);
        if (interval != null && interval.getSharedPreferences() != null) {
            final String every = context.getString(R.string.oepl_screen_every,
                    OEPLPrefs.updateIntervalMinutes(interval.getSharedPreferences()));
            final String tries = context.getString(R.string.oepl_screen_attempts,
                    OEPLPrefs.connectAttempts(interval.getSharedPreferences()),
                    OEPLPrefs.connectWindowMillis(interval.getSharedPreferences()) / 1000);
            summary(handler, SCREEN_UPDATES, join(every, context.getString(
                    OEPLPrefs.liveUpdates(interval.getSharedPreferences())
                            ? R.string.oepl_screen_live_on : R.string.oepl_screen_live_off),
                    tries));
        }
        summary(handler, SCREEN_POWER, entry(handler, OEPLPrefs.PREF_ADV_INTERVAL));
        summary(handler, SCREEN_HARDWARE, entry(handler, OEPLPrefs.PREF_MODEL));
    }

    private void show(final DeviceSpecificSettingsHandler handler, final String key,
                      final boolean visible) {
        final Preference preference = handler.findPreference(key);
        if (preference != null) {
            preference.setVisible(visible);
        }
    }

    private void summary(final DeviceSpecificSettingsHandler handler, final String key,
                         final String value) {
        final Preference preference = handler.findPreference(key);
        if (preference != null && value != null && !value.isEmpty()) {
            preference.setSummary(value);
        }
    }

    /** The label of a list preference's current choice, not its stored value. */
    private String entry(final DeviceSpecificSettingsHandler handler, final String key) {
        final ListPreference preference = handler.findPreference(key);
        if (preference == null || preference.getEntry() == null) {
            return null;
        }
        return preference.getEntry().toString();
    }

    private String text(final DeviceSpecificSettingsHandler handler, final String key,
                        final String fallback) {
        final Preference preference = handler.findPreference(key);
        if (preference == null || preference.getSharedPreferences() == null) {
            return null;
        }
        final String value = preference.getSharedPreferences().getString(key, "");
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String count(final Context context, final DeviceSpecificSettingsHandler handler,
                         final String key, final int unitRes) {
        final String value = entry(handler, key);
        return value == null ? null : value + " " + context.getString(unitRes);
    }

    private String firstLine(final String value) {
        if (value == null) {
            return null;
        }
        final int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline).trim();
    }

    private String join(final String... parts) {
        final StringBuilder line = new StringBuilder();
        for (final String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(part);
        }
        return line.toString();
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }

    public static final Creator<ATCBLEOEPLWeatherSettingsCustomizer> CREATOR =
            new Creator<ATCBLEOEPLWeatherSettingsCustomizer>() {
                @Override
                public ATCBLEOEPLWeatherSettingsCustomizer createFromParcel(final Parcel in) {
                    return new ATCBLEOEPLWeatherSettingsCustomizer();
                }

                @Override
                public ATCBLEOEPLWeatherSettingsCustomizer[] newArray(final int size) {
                    return new ATCBLEOEPLWeatherSettingsCustomizer[size];
                }
            };
}
