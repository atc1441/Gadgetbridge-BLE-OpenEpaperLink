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

import android.bluetooth.le.ScanFilter;
import android.graphics.Bitmap;
import android.content.SharedPreferences;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoepl.ATCBLEOEPLInstallHandler;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.ATCBLEOEPLWeatherDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLContentSync;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLProtocol;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLStatusStore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLTagTypes;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;

/**
 * An OpenEPaperLink tag driven as a weather display: connected only to push a new picture and
 * disconnected straight after, so that the coin cell is not drained by an idle link.
 */
public class ATCBLEOEPLWeatherCoordinator extends AbstractDeviceCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(ATCBLEOEPLWeatherCoordinator.class);


    /**
     * The whole point of this device is that the link stays down. Gadgetbridge would otherwise
     * reconnect on every Bluetooth state change and hold the connection open, which is exactly
     * what drains the coin cell.
     */
    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences,
                                           final SharedPreferences.Editor editor) {
        editor.putBoolean(GBPrefs.DEVICE_AUTO_RECONNECT, false);
        editor.putBoolean(GBPrefs.DEVICE_CONNECT_BACK, false);
        editor.putBoolean(DeviceSettingsPreferenceConst.PREF_CONNECTION_PRIORITY_LOW_POWER, false);
        inheritFromAConfiguredDisplay(editor);
    }

    /**
     * What a second display starts with: the settings of the first one.
     * <p>
     * The place the weather is for, which calendars are read, the news feed, the Home
     * Assistant instance, how often a picture goes out - none of that is about the panel, it
     * is about the household. Typing it in again for every tag on the wall is work nobody
     * should be asked to do twice, and getting it subtly different is worse than that: two
     * displays showing the weather of two different towns look like a bug.
     * <p>
     * What is copied is an ordinary setting afterwards. The new display can be pointed
     * somewhere else, and nothing remembers where its values came from. Deliberately left out
     * is everything that belongs to the panel rather than to the household - what it shows,
     * its picture, its rotation, its colours - so a second display is not a copy of the first,
     * only as ready to use.
     */
    private void inheritFromAConfiguredDisplay(final SharedPreferences.Editor editor) {
        try {
            final GBDevice source = configuredDisplay();
            if (source == null) {
                return;
            }
            final SharedPreferences from =
                    GBApplication.getDeviceSpecificSharedPrefs(source.getAddress());
            int taken = 0;
            for (final String key : INHERITED_SETTINGS) {
                final Object value = from.getAll().get(key);
                if (value == null) {
                    continue;
                }
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof java.util.Set) {
                    //noinspection unchecked
                    editor.putStringSet(key, new java.util.LinkedHashSet<>(
                            (java.util.Set<String>) value));
                } else {
                    continue;
                }
                taken++;
            }
            if (taken == 0) {
                return;
            }
            LOG.info("new display takes {} settings from {}", taken, source.getAddress());
            final Context context = GBApplication.getContext();
            if (context != null) {
                GB.toast(context.getString(R.string.oepl_inherited, source.getAliasOrName()),
                        3000, GB.INFO);
            }
        } catch (final Exception e) {
            // a fresh display with stock settings is a perfectly good outcome
            LOG.warn("could not take the settings of another display", e);
        }
    }

    /**
     * Another display that somebody has already set up, or null when this is the first one.
     * "Set up" means it has been told where it is - a place for the weather, a feed, or a Home
     * Assistant instance; a display that was only ever paired has nothing worth copying.
     */
    @Nullable
    private GBDevice configuredDisplay() {
        final List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
        // backwards: the display set up most recently is the one whose settings are current
        for (int i = devices.size() - 1; i >= 0; i--) {
            final GBDevice device = devices.get(i);
            if (device.getType() != DeviceType.OEPL_WEATHER_DISPLAY) {
                continue;
            }
            final SharedPreferences prefs =
                    GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
            if (prefs == null) {
                continue;
            }
            if (!OEPLPrefs.weatherLocation(prefs).isEmpty()
                    || !OEPLPrefs.rssUrl(prefs).isEmpty()
                    || !OEPLPrefs.homeAssistantUrl(prefs).isEmpty()) {
                return device;
            }
        }
        return null;
    }

    /**
     * The settings that belong to the household rather than to one panel, and are therefore
     * worth handing to the next display that is paired.
     */
    private static final List<String> INHERITED_SETTINGS = Collections.unmodifiableList(
            Arrays.asList(
                    // where the weather is for, and how much of it is drawn
                    OEPLPrefs.PREF_WEATHER_SOURCE,
                    OEPLPrefs.PREF_WEATHER_LOCATION,
                    OEPLPrefs.PREF_FORECAST_DAYS,
                    OEPLPrefs.PREF_FORECAST_HOURS,
                    OEPLPrefs.PREF_WIND_UNIT,
                    OEPLPrefs.PREF_SHOW_SUN,
                    // which calendars are read and how far ahead
                    "sync_calendar",
                    "calendar_blacklist",
                    "calendar_lookahead_days",
                    "sync_birthdays",
                    OEPLPrefs.PREF_CALENDAR_EVENTS,
                    OEPLPrefs.PREF_CALENDAR_DAYS,
                    OEPLPrefs.PREF_CALENDAR_TITLE,
                    // the news feed and the house
                    OEPLPrefs.PREF_RSS_URL,
                    OEPLPrefs.PREF_RSS_TITLE,
                    OEPLPrefs.PREF_RSS_SUMMARY,
                    OEPLPrefs.PREF_HA_URL,
                    OEPLPrefs.PREF_HA_TOKEN,
                    OEPLPrefs.PREF_HA_ENTITIES,
                    OEPLPrefs.PREF_READING_GROUPS,
                    OEPLPrefs.PREF_NOTIFICATION_COUNT,
                    // how the cards are framed, which is taste rather than hardware
                    OEPLPrefs.PREF_FRAME,
                    OEPLPrefs.PREF_SHOW_FOOTER,
                    // and how often a picture goes out at all
                    OEPLPrefs.PREF_UPDATE_INTERVAL,
                    OEPLPrefs.PREF_LIVE_UPDATES,
                    OEPLPrefs.PREF_MIN_GAP,
                    OEPLPrefs.PREF_CONNECT_WINDOW,
                    OEPLPrefs.PREF_CONNECT_ATTEMPTS,
                    OEPLPrefs.PREF_OPPORTUNISTIC,
                    OEPLPrefs.PREF_REQUIRE_ADVERTISEMENT,
                    OEPLPrefs.PREF_STAY_CONNECTED));

    /**
     * The plain ATC_BLE_OEPL coordinator matches the very same tags. Device type resolution
     * takes the first match in priority order, so without this the weather display could never
     * be picked from the discovery list. The plain entry stays reachable by long pressing the
     * discovered device and forcing its type.
     */
    @Override
    public int getOrderPriority() {
        return -1;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_oepl_weather_display;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_bluetooth_printer;
    }

    @Override
    public String getManufacturer() {
        return "atc1441";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return ATCBLEOEPLWeatherDeviceSupport.class;
    }

    /**
     * The names these tags give themselves: ATC_ and four characters of the MAC address on the
     * OpenEPaperLink firmware, RTL_ on the Realtek based ones, and whatever the next generation
     * picks. Only used where no advertisement is at hand, see {@link #supports}.
     */
    private static final Pattern KNOWN_NAMES = Pattern.compile("(ATC|RTL|OEPL)_.*");

    @Override
    protected Pattern getSupportedDeviceName() {
        return KNOWN_NAMES;
    }

    /**
     * Recognised by what the tag advertises rather than by what it calls itself.
     * <p>
     * The name is the weakest thing about these displays: the same firmware answers to ATC_ on
     * one board and RTL_ on another, and a tag that has not been queried for its name yet has
     * none at all. What every OpenEPaperLink tag does carry is 0x1337 - as the service in its
     * advertisement, and as the manufacturer id of the data block that holds the hardware type,
     * the battery voltage and the panel temperature. Either of those is proof enough; the name
     * is left as a fallback for a candidate that reached us without an advertisement, which is
     * what a device restored from the database looks like.
     */
    @Override
    public boolean supports(@NonNull final GBDeviceCandidate candidate) {
        if (candidate.supportsService(OEPLProtocol.UUID_SERVICE)) {
            return true;
        }
        final SparseArray<byte[]> manufacturerData = candidate.getManufacturerSpecificData();
        if (manufacturerData != null
                && manufacturerData.get(OEPLProtocol.MANUFACTURER_ID) != null) {
            return true;
        }
        final String name = candidate.getName();
        return name != null && KNOWN_NAMES.matcher(name).matches();
    }

    /**
     * What the scan looks for: 0x1337, in either of the two places a tag puts it. Most
     * advertise the service, some only the manufacturer data block - and a filter on the name
     * would miss every tag that calls itself something other than ATC_, which the Realtek
     * based ones do (RTL_). Both filters are offered to Android, which ORs them.
     */
    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        final ParcelUuid service = new ParcelUuid(OEPLProtocol.UUID_SERVICE);
        return Arrays.asList(
                new ScanFilter.Builder().setServiceUuid(service).build(),
                // empty data and mask: any advertisement carrying that manufacturer id at all
                new ScanFilter.Builder()
                        .setManufacturerData(OEPLProtocol.MANUFACTURER_ID, new byte[0],
                                new byte[0])
                        .build());
    }

    /**
     * The tag advertises every few seconds and sleeps in between, so the radio has to keep
     * trying until one of those advertisements comes along. Android's default window of five
     * seconds is barely longer than a single advertising interval and misses the tag far more
     * often than it catches it; the picture then never goes out although the display is in reach.
     */
    @Override
    public long getBleConnectTimeout(@NonNull final GBDevice device) {
        return OEPLPrefs.connectWindowMillis(
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
    }

    /**
     * The device's own picture is the picture it is showing. A weather display is the one kind
     * of device where that is both knowable and the most useful thing an icon could say: the
     * card in the device list is then a small copy of what hangs on the wall.
     */
    @Nullable
    @Override
    public Bitmap getDevicePicture(@NonNull final GBDevice device, @NonNull final Context context) {
        return OEPLStatusStore.readPreview(context, device.getAddress());
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_LAZY;
    }

    /**
     * The card's title, while the display has no name of its own: the tag's Bluetooth name
     * followed by the diagonal and the resolution of the panel it carries, as in
     * {@code ATC_8F3A21 · 2.9" · 384×168}.
     * <p>
     * The Bluetooth name of these tags is "ATC_" and four characters of the MAC address, which
     * places nothing at all once there is more than one of them on the wall. What matters about
     * a display is how big it is and how many pixels it has, and both are known from the moment
     * it is paired - the advertisement carries the hardware type, and the catalogue the rest.
     * A name the user typed in is left alone.
     */
    @Nullable
    @Override
    public String getDeviceDisplayName(@NonNull final Context context,
                                       @NonNull final GBDevice device) {
        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        final int hardware = prefs.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        final OEPLTagTypes.TagType type = hardware >= 0
                ? OEPLTagTypes.byHardwareType(hardware) : null;
        final String inches = type != null ? type.inches() : null;

        // The panel as it is sold and seen, not the buffer it is written through. On most
        // tags the two are a quarter turn apart, so taking the stored buffer announced one
        // display as 384x168 and the one beside it as 184x384 although both are read the same
        // way round. The catalogue has the panel; the stored pair is the fallback for a tag
        // the catalogue has never heard of, and is then put long side first for the same
        // reason.
        int width = type != null ? type.width : prefs.getInt(OEPLPrefs.PANEL_WIDTH, 0);
        int height = type != null ? type.height : prefs.getInt(OEPLPrefs.PANEL_HEIGHT, 0);
        if (width > 0 && height > width) {
            final int swap = width;
            width = height;
            height = swap;
        }

        final StringBuilder name = new StringBuilder(device.getName());
        if (inches != null) {
            name.append(" · ").append(inches);
        }
        if (width > 0 && height > 0) {
            name.append(" · ").append(width).append('×').append(height);
        }
        // nothing known yet: the plain name, rather than a title that says only half of it
        return name.length() > device.getName().length() ? name.toString() : null;
    }

    /**
     * When this display last showed something new. Its connections last a few seconds and
     * happen every quarter of an hour; what somebody wants to know about a display on a wall
     * is when the picture on it was last brought up to date.
     */
    @Override
    public long getLastUpdateMillis(@NonNull final GBDevice device) {
        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        final long drawn = prefs.getLong(OEPLPrefs.LAST_UPDATE_MILLIS, 0L);
        return drawn > 0 ? drawn : super.getLastUpdateMillis(device);
    }

    /**
     * The battery of a display that is asleep, which is almost always. Every advertisement it
     * sends carries its voltage, and every connection reads it again, so the figure on the
     * card is at most one wake-up old - while Gadgetbridge's own battery reading is cleared
     * the moment the link drops, which for this device is a few seconds after it was made.
     */
    @Override
    public int getRememberedBatteryLevel(@NonNull final GBDevice device,
                                         final int batteryIndex) {
        if (batteryIndex != 0) {
            return GBDevice.BATTERY_UNKNOWN;
        }
        final int millivolts = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress())
                .getInt(OEPLPrefs.LAST_BATTERY_MV, -1);
        return millivolts > 0 ? OEPLPrefs.batteryPercent(millivolts) : GBDevice.BATTERY_UNKNOWN;
    }

    /**
     * A pull down on the device list refreshes this display. The gesture asks for what is on
     * screen to be brought up to date, and for a panel that is woken for a few seconds at a
     * time that means: throw the cached weather, headlines and pictures away, draw the card
     * again, and go to the display with it - but only if the picture actually came out
     * different, because a redraw costs the tag seconds of radio and battery either way.
     */
    @Override
    public boolean onPullToRefresh(@NonNull final Context context,
                                   @NonNull final GBDevice device) {
        OEPLContentSync.invalidateAll(device.getAddress());
        redrawNow(context, device, OEPLPrefs.PREF_CONTENT);
        GB.toast(context.getString(R.string.oepl_info_refreshing, device.getAliasOrName()),
                2000, GB.INFO);
        return true;
    }

    /**
     * The name tag mode is one switch for every display at once, so each of them has to be
     * drawn again and offered the new picture - the one that is turned off is exactly the one
     * whose tag somebody is looking for, and waiting a quarter of an hour for it would make
     * the switch look broken.
     */
    @Override
    public boolean onGlobalPreferenceChanged(@NonNull final Context context,
                                             @NonNull final GBDevice device,
                                             @NonNull final String key) {
        if (!GBPrefs.DISPLAY_NAME_TAGS_KEY.equals(key)) {
            return false;
        }
        redrawNow(context, device, key);
        return true;
    }

    /**
     * Draws this display's card again straight away, by whichever route is open.
     * <p>
     * The scheduler deliberately steps aside for a link that is already up - cutting in would
     * disconnect underneath whatever is using it - so a display that happens to be connected
     * has to be reached through its own service instead. Going only through the scheduler is
     * how a setting could change nothing at all on the one display that was awake.
     */
    private void redrawNow(final Context context, final GBDevice device, final String reason) {
        if (device.isInitialized()) {
            GBApplication.deviceService(device).onSendConfiguration(reason);
        } else {
            OEPLUpdateScheduler.requestUpdateNow(context, device);
        }
    }

    /**
     * No companion association, and no hint asking for one. It would be there to let
     * Gadgetbridge reconnect by itself and watch for the device in the background - which for
     * this display is not a missing feature but the thing it is built to avoid: the link is
     * opened for a few seconds to hand over a picture and dropped again, because an idle
     * connection is what empties the coin cell. The schedule here is an alarm, not a standing
     * connection, so the association would buy nothing and the hint only asks to be dismissed.
     */
    @Override
    public boolean suggestsCompanionPairing(@NonNull final GBDevice device) {
        return false;
    }

    /**
     * The tag broadcasts its hardware type, firmware, battery and temperature in the manufacturer
     * data, so all of that is known before a connection is ever opened. Picking it up here means
     * the very first rendered card already carries a real battery reading, and the panel geometry
     * is known from the OpenEPaperLink catalogue even if the display never answers.
     */
    @Override
    public GBDevice createDevice(final GBDeviceCandidate candidate, final DeviceType deviceType) {
        final GBDevice device = super.createDevice(candidate, deviceType);
        final OEPLUpdateScheduler.Advertisement ad = advertisementOf(candidate);
        if (ad != null) {
            OEPLPrefs.storeFromAdvertisement(device.getAddress(), ad);
        }
        // and whatever Home Assistant was last reached at, so that the second display of a
        // shelf full of them does not have to be told the address and the token again
        OEPLPrefs.seedHomeAssistant(device.getAddress());
        return device;
    }

    /** The tag's own broadcast, or null when this scan result carried none we understand. */
    @Nullable
    private OEPLUpdateScheduler.Advertisement advertisementOf(final GBDeviceCandidate candidate) {
        try {
            final SparseArray<byte[]> data = candidate.getManufacturerSpecificData();
            final byte[] raw = data != null ? data.get(OEPLProtocol.MANUFACTURER_ID) : null;
            return OEPLUpdateScheduler.Advertisement.parse(raw);
        } catch (final Exception e) {
            LOG.error("could not read the advertisement of {}", candidate.getMacAddress(), e);
            return null;
        }
    }

    /**
     * What kind of tag this is, in the discovery list, before anything has been paired. Every
     * one of these displays calls itself ATC_ and the four characters after that are a piece of
     * the MAC address, so a shelf full of them is a list of identical entries - while the panel
     * they carry, which is the one thing that decides what can be put on them, is in every
     * advertisement they send. The battery comes along because it costs nothing here and tells
     * a dead tag from a live one while still searching.
     */
    @Nullable
    @Override
    public String getCandidateDetails(@NonNull final Context context,
                                      @NonNull final GBDeviceCandidate candidate) {
        final OEPLUpdateScheduler.Advertisement ad = advertisementOf(candidate);
        if (ad == null || ad.hardwareType < 0) {
            return null;
        }
        final StringBuilder details = new StringBuilder(OEPLTagTypes.describe(ad.hardwareType));
        if (ad.batteryMillivolts > 0) {
            details.append(String.format(Locale.ROOT, " · %.2f V",
                    ad.batteryMillivolts / 1000f));
        }
        return details.toString();
    }

    @Override
    public boolean supportsWeather(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public InstallHandler findInstallHandler(final Uri uri, final Bundle options, final Context context) {
        final ATCBLEOEPLInstallHandler handler = new ATCBLEOEPLInstallHandler(uri, context);
        return handler.isValid() ? handler : null;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(final GBDevice device) {
        return new int[]{
                R.xml.devicesettings_oepl_weather_display,
        };
    }

    /**
      * Hides the option groups the selected card would not read, so the settings read as a
      * description of this one display rather than as the union of everything it could show.
      */
    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(
            @NonNull final GBDevice device) {
        return new ATCBLEOEPLWeatherSettingsCustomizer();
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull final GBDevice device) {
        return DeviceCoordinator.DeviceKind.SMART_DISPLAY;
    }

    @Override
    protected void deleteDevice(@NonNull final GBDevice gbDevice, @NonNull final Device device,
                                @NonNull final DaoSession session) {
        OEPLUpdateScheduler.cancel(GBApplication.getContext(), gbDevice.getAddress());
    }

    /**
     * Two shortcuts on the device card. Both stay available while the display is out of reach,
     * which is nearly always: the overview reads what was recorded during the last contact, and
     * a refresh is a request that the scheduler picks up as soon as the tag answers again.
     */
    @Override
    public List<DeviceCardAction> getCustomActions() {
        final List<DeviceCardAction> actions = new ArrayList<>();
        actions.add(new DeviceCardAction() {
            @NonNull
            @Override
            public String getId() {
                return "oepl_weather_info";
            }

            @Override
            public int getIcon(@NonNull final GBDevice device) {
                return R.drawable.ic_info;
            }

            @NonNull
            @Override
            public String getDescription(@NonNull final GBDevice device,
                                         @NonNull final Context context) {
                return context.getString(R.string.oepl_info_title);
            }

            @Override
            public boolean isVisible(@NonNull final GBDevice device) {
                return true; // the whole point is to work while the tag is asleep
            }

            @Override
            public void onClick(@NonNull final GBDevice device, @NonNull final Context context) {
                final android.content.Intent intent =
                        new android.content.Intent(context, ATCBLEOEPLWeatherInfoActivity.class);
                intent.putExtra(GBDevice.EXTRA_DEVICE, device);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });
        actions.add(new DeviceCardAction() {
            @NonNull
            @Override
            public String getId() {
                return "oepl_weather_refresh";
            }

            @Override
            public int getIcon(@NonNull final GBDevice device) {
                return R.drawable.ic_refresh;
            }

            @NonNull
            @Override
            public String getDescription(@NonNull final GBDevice device,
                                         @NonNull final Context context) {
                return context.getString(R.string.oepl_info_action_update);
            }

            @Override
            public boolean isVisible(@NonNull final GBDevice device) {
                return true;
            }

            @Override
            public void onClick(@NonNull final GBDevice device, @NonNull final Context context) {
                OEPLUpdateScheduler.requestUpdateNow(context, device);
                nodomain.freeyourgadget.gadgetbridge.util.GB.toast(
                        context.getString(R.string.oepl_info_queued), 2000,
                        nodomain.freeyourgadget.gadgetbridge.util.GB.INFO);
            }
        });
        return actions;
    }
}
