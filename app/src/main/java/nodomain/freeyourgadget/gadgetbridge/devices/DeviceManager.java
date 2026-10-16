/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniel
    Dakhno, Daniele Gobbetti, Gordon Williams, José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

/**
 * Provides access to the list of devices managed by Gadgetbridge.
 * Changes to the devices (e.g. connection state) or the list of devices
 * are broadcasted via #ACTION_DEVICE_CHANGED
 */
public class DeviceManager {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceManager.class);

    public static final String BLUETOOTH_DEVICE_ACTION_ALIAS_CHANGED = "android.bluetooth.device.action.ALIAS_CHANGED";
    /**
     * Intent action to notify that the list of devices has changed.
     */
    public static final String ACTION_DEVICES_CHANGED
            = "nodomain.freeyourgadget.gadgetbridge.devices.devicemanager.action.devices_changed";
    /**
     * Intent action to notify this class that the list of devices shall be refreshed.
     */
    public static final String ACTION_REFRESH_DEVICELIST
            = "nodomain.freeyourgadget.gadgetbridge.devices.devicemanager.action.set_version";
    private final Context context;
    /**
     * This list is final, it will never be recreated. Only its contents change.
     * This allows direct access to the list from ListAdapters.
     */
    private final List<GBDevice> deviceList = new ArrayList<>();
    private List<GBDevice> selectedDevices = new ArrayList<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case ACTION_REFRESH_DEVICELIST: // fall through
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    refreshPairedDevices();
                    break;
                case BluetoothDevice.ACTION_NAME_CHANGED:
                case BLUETOOTH_DEVICE_ACTION_ALIAS_CHANGED:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String newName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                    if (device!=null)
                      updateDeviceName(device, newName);
                    break;
                case GBDevice.ACTION_DEVICE_CHANGED:
                    GBDevice dev = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
                    if (dev != null && dev.getAddress() != null) {
                        int index = deviceList.indexOf(dev); // search by address
                        if (index >= 0) {
                            deviceList.get(index).copyFromDevice(dev);
                        } else {
                            deviceList.add(dev);
                        }
                        if (dev.isInitialized()) {
                            try (DBHandler dbHandler = GBApplication.acquireDB()) {
                                DBHelper.getDevice(dev, dbHandler.getDaoSession()); // implicitly creates the device in database if not present, and updates device attributes
                            } catch (Exception ignore) {
                            }
                        }
                    }
                    updateSelectedDevice(dev);
                    refreshPairedDevices();
                    break;
            }
        }
    };

    public DeviceManager(Context context) {
        this.context = context;
        IntentFilter filterLocal = new IntentFilter();
        filterLocal.addAction(DeviceManager.ACTION_REFRESH_DEVICELIST);
        filterLocal.addAction(GBDevice.ACTION_DEVICE_CHANGED);
        filterLocal.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, filterLocal);

        IntentFilter filterGlobal = new IntentFilter();
        filterGlobal.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filterGlobal.addAction(BLUETOOTH_DEVICE_ACTION_ALIAS_CHANGED);
        filterGlobal.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        ContextCompat.registerReceiver(context, mReceiver, filterGlobal, ContextCompat.RECEIVER_EXPORTED);

        refreshPairedDevices();
    }

    private void updateDeviceName(BluetoothDevice device, String newName) {
        for (GBDevice dev : deviceList) {
            if (device.getAddress().equals(dev.getAddress())) {
                if (!dev.getName().equals(newName)) {
                    dev.setName(newName);
                    notifyDevicesChanged();
                    return;
                }
            }
        }
    }

    private void updateSelectedDevice(GBDevice dev) {
        selectedDevices.clear();
        for(GBDevice device : deviceList){
            if(device.isInitialized()){
                selectedDevices.add(device);
            }
        }
        GB.updateNotification(selectedDevices, context);
    }

    private void refreshPairedDevices() {
        final Set<GBDevice> availableDevices = DeviceHelper.getInstance().getAvailableDevices();
        deviceList.retainAll(availableDevices);
        for (GBDevice availableDevice : availableDevices) {
            if (!deviceList.contains(availableDevice)) {
                deviceList.add(availableDevice);
            }
        }

        // Snapshot the sort keys up front, instead of re-reading mutable GBDevice
        // fields on every compare() call. GBDevice state can be mutated concurrently by
        // device support threads while this sort runs, and re-reading it mid-sort can
        // make compare() inconsistent between calls, which violates the contract TimSort
        // relies on (IllegalArgumentException: Comparison method violates its general contract!).
        final String order = GBApplication.getPrefs().getDeviceSortOrder();
        final boolean byState = GBPrefs.DEVICE_SORT_DEFAULT.equals(order);
        final boolean byLastUpdate = GBPrefs.DEVICE_SORT_LAST_UPDATE.equals(order);
        final boolean byType = GBPrefs.DEVICE_SORT_TYPE.equals(order);
        final boolean sortByLastConnectedTs = byState
                && GBApplication.getPrefs().getSortByLastConnectedTs();
        final Collator collator = Collator.getInstance();
        final Map<GBDevice, DeviceSortKey> sortKeys = new HashMap<>();
        for (GBDevice device : deviceList) {
            final long timestamp;
            if (byLastUpdate) {
                // what the device last did, which for a display that is woken to be written
                // to is the picture rather than the connection; its coordinator decides
                timestamp = device.getDeviceCoordinator().getLastUpdateMillis(device);
            } else if (sortByLastConnectedTs) {
                timestamp = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress())
                        .getLong(GBPrefs.LAST_CONNECTED_TS, 0L);
            } else {
                timestamp = 0L;
            }
            final String type = byType ? typeNameOf(device) : "";
            sortKeys.put(device, new DeviceSortKey(device.getStateOrdinal(), timestamp,
                    device.getAliasOrName(), type));
        }

        Collections.sort(deviceList, comparator(order, sortByLastConnectedTs, sortKeys,
                collator));
        notifyDevicesChanged();
    }

    /** What kind of device this is, as the list groups it: its own name for its type. */
    private String typeNameOf(final GBDevice device) {
        try {
            return context.getString(device.getDeviceCoordinator().getDeviceNameResource());
        } catch (final Exception e) {
            // a type without a readable name still has to sort somewhere
            return device.getType().name();
        }
    }

    /**
     * The order the list is put in, given the keys of the devices in it.
     * <p>
     * Only the default order puts whatever is connected on top. The other three were chosen
     * for a reason - which came last, what it is called, what kind it is - and a device
     * jumping to the front because it happens to be connected at that second is exactly what
     * they are meant to stop. The name decides every tie, so the order never depends on which
     * device the list happened to hold first.
     */
    static Comparator<GBDevice> comparator(final String order,
                                           final boolean sortByLastConnectedTs,
                                           final Map<GBDevice, DeviceSortKey> keys,
                                           final Collator collator) {
        final boolean byState = GBPrefs.DEVICE_SORT_DEFAULT.equals(order);
        final boolean byLastUpdate = GBPrefs.DEVICE_SORT_LAST_UPDATE.equals(order);
        final boolean byType = GBPrefs.DEVICE_SORT_TYPE.equals(order);
        return (lhs, rhs) -> {
            final DeviceSortKey lhsKey = Objects.requireNonNull(keys.get(lhs));
            final DeviceSortKey rhsKey = Objects.requireNonNull(keys.get(rhs));

            if (byState) {
                final int stateDiff = rhsKey.stateOrdinal - lhsKey.stateOrdinal;
                if (stateDiff != 0) {
                    return stateDiff;
                }
            }
            if (byType) {
                final int typeDiff = collator.compare(lhsKey.type, rhsKey.type);
                if (typeDiff != 0) {
                    return typeDiff;
                }
            }
            if (byLastUpdate || sortByLastConnectedTs) {
                // the most recent first
                final int whenDiff = Long.compare(rhsKey.timestamp, lhsKey.timestamp);
                if (whenDiff != 0) {
                    return whenDiff;
                }
            }
            return collator.compare(lhsKey.aliasOrName, rhsKey.aliasOrName);
        };
    }

    record DeviceSortKey(int stateOrdinal,
                         long timestamp,
                         String aliasOrName,
                         String type) {
    }

    /**
     * The returned list is final, it will never be recreated. Only its contents change.
     * This allows direct access to the list from ListAdapters.
     */
    public List<GBDevice> getDevices() {
        return Collections.unmodifiableList(deviceList);
    }

    @Nullable
    public GBDevice getDeviceByAddress(String address){
        for(GBDevice device : deviceList){
            if(device.getAddress().compareToIgnoreCase(address) == 0){
                return device;
            }
        }
        return null;
    }

    public List<GBDevice> getSelectedDevices() {
        return selectedDevices;
    }

    private void notifyDevicesChanged() {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_DEVICES_CHANGED));
    }
}
