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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;

import java.util.Collection;

import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoeplweather.ATCBLEOEPLWeatherCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;

/**
 * Which tags the discovery list picks up.
 * <p>
 * By what a tag advertises, never by what it calls itself: the same firmware answers to ATC_ on
 * one board and RTL_ on another, and a tag whose name has not been read yet has none at all.
 * 0x1337 is what they have in common - as the service in the advertisement, and as the
 * manufacturer id of the data block with the hardware type and the battery voltage in it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class OEPLDiscoveryTest {

    private final ATCBLEOEPLWeatherCoordinator coordinator = new ATCBLEOEPLWeatherCoordinator();

    @Test
    public void aTagIsRecognisedByItsServiceWhateverItIsCalled() {
        assertTrue("the Realtek tags call themselves RTL_",
                coordinator.supports(candidate("RTL_1A2B3C", true, false)));
        assertTrue(coordinator.supports(candidate("ATC_8F3A21", true, false)));
        // a tag whose name has not been read yet still advertises what it is
        assertTrue(coordinator.supports(candidate(null, true, false)));
        assertTrue("a name nobody has seen before is still an OpenEPaperLink tag",
                coordinator.supports(candidate("Kuechendisplay", true, false)));
    }

    @Test
    public void theManufacturerBlockIsProofAsWell() {
        // some firmwares put 0x1337 only into the manufacturer data, not into the service list
        assertTrue(coordinator.supports(candidate("RTL_1A2B3C", false, true)));
        assertTrue(coordinator.supports(candidate(null, false, true)));
    }

    @Test
    public void theNameIsOnlyTheFallback() {
        // nothing advertised at all, which is what a candidate restored from the database or
        // one seen by a scan somebody else started looks like
        assertTrue(coordinator.supports(candidate("ATC_8F3A21", false, false)));
        assertTrue(coordinator.supports(candidate("RTL_1A2B3C", false, false)));
        assertFalse(coordinator.supports(candidate("Mi Band 7", false, false)));
        assertFalse(coordinator.supports(candidate(null, false, false)));
    }

    @Test
    public void theScanLooksForBothFormsOfTheSameNumber() {
        final Collection<? extends ScanFilter> filters = coordinator.createBLEScanFilters();
        assertEquals("one filter for the service, one for the manufacturer data",
                2, filters.size());
        boolean service = false;
        boolean manufacturer = false;
        for (final ScanFilter filter : filters) {
            if (filter.getServiceUuid() != null
                    && OEPLProtocol.UUID_SERVICE.equals(filter.getServiceUuid().getUuid())) {
                service = true;
            }
            if (filter.getManufacturerId() == OEPLProtocol.MANUFACTURER_ID) {
                manufacturer = true;
            }
        }
        assertTrue("no filter on the 0x1337 service", service);
        assertTrue("no filter on the 0x1337 manufacturer data", manufacturer);
    }

    private GBDeviceCandidate candidate(@Nullable final String name, final boolean withService,
                                        final boolean withManufacturerData) {
        final BluetoothDevice device = BluetoothAdapter.getDefaultAdapter()
                .getRemoteDevice("FF:FC:4F:05:E4:11");
        if (name != null) {
            Shadows.shadowOf(device).setName(name);
        }
        final ParcelUuid[] uuids = withService
                ? new ParcelUuid[]{new ParcelUuid(OEPLProtocol.UUID_SERVICE)}
                : new ParcelUuid[0];
        final SparseArray<byte[]> manufacturerData = new SparseArray<>();
        if (withManufacturerData) {
            manufacturerData.put(OEPLProtocol.MANUFACTURER_ID, new byte[]{1, 2, 3, 4});
        }
        final GBDeviceCandidate candidate =
                new GBDeviceCandidate(device, (short) -60, uuids, manufacturerData);
        candidate.refreshNameIfUnknown();
        return candidate;
    }
}
