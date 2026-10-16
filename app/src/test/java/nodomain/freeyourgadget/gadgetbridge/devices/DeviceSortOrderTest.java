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
package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

/**
 * What decides which device the list puts at the top.
 * <p>
 * The point of the orders other than the default is that the connection state does not come
 * into it: a shelf of displays that are asleep nearly all of the time is a list in which the
 * state says nothing, while when each of them last showed something new says everything. A
 * device must therefore not jump to the front for being connected at that second - which is
 * exactly what the default order is for, and only that one.
 */
public class DeviceSortOrderTest {

    private final Collator collator = Collator.getInstance(Locale.ENGLISH);
    private final Map<GBDevice, DeviceManager.DeviceSortKey> keys = new HashMap<>();

    /** Three devices: one connected, one updated minutes ago, one updated last week. */
    private final GBDevice connected = device("Watch", 0);
    private final GBDevice fresh = device("Kitchen", 1);
    private final GBDevice stale = device("Hall", 2);

    private GBDevice device(final String name, final int index) {
        return new GBDevice("00:11:22:33:44:0" + index, name, null, null,
                DeviceType.TEST);
    }

    private List<GBDevice> sorted(final String order, final boolean sortByLastConnected) {
        final List<GBDevice> devices = new ArrayList<>(
                Arrays.asList(stale, connected, fresh));
        Collections.sort(devices,
                DeviceManager.comparator(order, sortByLastConnected, keys, collator));
        return devices;
    }

    private void keysFor() {
        // state 10 is "connected" as far as the comparator cares, 0 is not
        keys.put(connected, new DeviceManager.DeviceSortKey(10, 1_000L, "Watch", "Watch"));
        keys.put(fresh, new DeviceManager.DeviceSortKey(0, 9_000L, "Kitchen", "Display"));
        keys.put(stale, new DeviceManager.DeviceSortKey(0, 2_000L, "Hall", "Display"));
    }

    @Test
    public void theDefaultOrderStillPutsWhatIsConnectedFirst() {
        keysFor();
        assertEquals(Arrays.asList(connected, stale, fresh), sorted("default", false));
    }

    @Test
    public void theLastUpdateOrderIgnoresWhatHappensToBeConnected() {
        keysFor();
        // the kitchen display was written to last, so it is the one at the top
        assertEquals(Arrays.asList(fresh, stale, connected),
                sorted(GBPrefs.DEVICE_SORT_LAST_UPDATE, false));
    }

    @Test
    public void theNameOrderIsNothingButTheName() {
        keysFor();
        assertEquals(Arrays.asList(stale, fresh, connected),
                sorted(GBPrefs.DEVICE_SORT_NAME, false));
    }

    @Test
    public void theTypeOrderKeepsOneKindTogether() {
        keysFor();
        // the two displays before the watch, and inside a kind by name
        assertEquals(Arrays.asList(stale, fresh, connected),
                sorted(GBPrefs.DEVICE_SORT_TYPE, false));
    }

    @Test
    public void theOldSwitchStillWorksInsideTheDefaultOrder() {
        keysFor();
        // connected first as ever, and the rest by when they last connected
        assertEquals(Arrays.asList(connected, fresh, stale), sorted("default", true));
    }
}
