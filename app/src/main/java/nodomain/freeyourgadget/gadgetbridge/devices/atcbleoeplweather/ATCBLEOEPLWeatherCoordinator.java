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
import java.util.Collection;
import java.util.List;
import java.util.Collections;
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
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLProtocol;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLStatusStore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
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
    }

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

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("ATC_.*");
    }

    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        final ParcelUuid service = new ParcelUuid(OEPLProtocol.UUID_SERVICE);
        return Collections.singletonList(new ScanFilter.Builder().setServiceUuid(service).build());
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
     * The tag broadcasts its hardware type, firmware, battery and temperature in the manufacturer
     * data, so all of that is known before a connection is ever opened. Picking it up here means
     * the very first rendered card already carries a real battery reading, and the panel geometry
     * is known from the OpenEPaperLink catalogue even if the display never answers.
     */
    @Override
    public GBDevice createDevice(final GBDeviceCandidate candidate, final DeviceType deviceType) {
        final GBDevice device = super.createDevice(candidate, deviceType);
        try {
            final SparseArray<byte[]> data = candidate.getManufacturerSpecificData();
            final byte[] raw = data != null ? data.get(OEPLProtocol.MANUFACTURER_ID) : null;
            final OEPLUpdateScheduler.Advertisement ad = OEPLUpdateScheduler.Advertisement.parse(raw);
            if (ad != null) {
                OEPLPrefs.storeFromAdvertisement(device.getAddress(), ad);
            }
        } catch (final Exception e) {
            LOG.error("could not read the advertisement of {}", candidate.getMacAddress(), e);
        }
        return device;
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
