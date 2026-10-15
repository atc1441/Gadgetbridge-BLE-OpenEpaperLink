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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that can wake the app on a weather display's behalf arrives here: the update alarm,
 * a batch of advertisements the Bluetooth controller collected while the phone slept, a reboot,
 * and Bluetooth being switched back on.
 * <p>
 * The work is done on a thread while the broadcast is held open with {@code goAsync}, which keeps
 * both the process and the CPU alive for it. That matters because the whole point of this device
 * is that the app is not running: by the time the alarm goes off there is usually no activity, no
 * connection and no service, and a plain {@code onReceive} would be cut off long before the
 * weather had been fetched.
 */
public class OEPLUpdateReceiver extends BroadcastReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLUpdateReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            return;
        }
        final Context application = context.getApplicationContext();

        switch (action) {
            case OEPLUpdateScheduler.ACTION_UPDATE: {
                final String address = intent.getStringExtra(OEPLUpdateScheduler.EXTRA_ADDRESS);
                final String reason = intent.getStringExtra(OEPLUpdateScheduler.EXTRA_REASON);
                if (address == null) {
                    return;
                }
                inBackground(() -> OEPLUpdateScheduler.run(application, address,
                        reason != null ? reason : OEPLUpdateScheduler.REASON_TIMER));
                return;
            }
            case OEPLUpdateScheduler.ACTION_ADVERTISEMENT: {
                final String address = intent.getStringExtra(OEPLUpdateScheduler.EXTRA_ADDRESS);
                if (address == null) {
                    return;
                }
                final int error = intent.getIntExtra(
                        BluetoothLeScanner.EXTRA_ERROR_CODE, 0);
                if (error != 0) {
                    LOG.warn("background listening for {} stopped with error {}", address, error);
                    // the registration is gone, so put it back on the next occasion
                    OEPLStatusStore.recordOpportunistic(address, false);
                    inBackground(() ->
                            OEPLUpdateScheduler.registerAdvertisementScan(application, address));
                    return;
                }
                final List<ScanResult> results = results(intent);
                if (results.isEmpty()) {
                    return;
                }
                inBackground(() ->
                        OEPLUpdateScheduler.onAdvertisements(application, address, results));
                return;
            }
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                inBackground(() -> OEPLUpdateScheduler.rescheduleAll(application,
                        OEPLUpdateScheduler.REASON_BOOT));
                return;
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        == BluetoothAdapter.STATE_ON) {
                    // a scan registration does not survive the adapter going down
                    inBackground(() -> OEPLUpdateScheduler.rescheduleAll(application,
                            OEPLUpdateScheduler.REASON_BLUETOOTH));
                }
                return;
            default:
                LOG.debug("ignoring {}", action);
        }
    }

    private static List<ScanResult> results(final Intent intent) {
        final List<ScanResult> list = intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        return list != null ? list : new ArrayList<>();
    }

    /** Runs the work off the main thread while holding the broadcast open for it. */
    private void inBackground(final Runnable work) {
        final PendingResult pending = goAsync();
        final Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (final Exception e) {
                LOG.error("a display update failed", e);
            } finally {
                pending.finish();
            }
        }, "oepl-update-receiver");
        thread.setDaemon(true);
        thread.start();
    }
}
