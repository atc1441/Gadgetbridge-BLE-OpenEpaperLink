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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.widget.RadioGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoeplweather.OEPLDesignPickerActivity;
import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoeplweather.OEPLImageActivity;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;

/**
 * The previews behind the design picker. They go through the same factory and the same renderer
 * as a real push, so if one of the designs in the settings list cannot be drawn, the picker is
 * where the user would find out - unless it is here.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class OEPLCardPreviewTest {

    private static final String ADDRESS = "FF:FC:4F:05:E4:11";

    private Context context;
    private GBDevice device;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        device = new GBDevice(ADDRESS, "display", null, null, DeviceType.OEPL_WEATHER_DISPLAY);
        OeplDemo.loadFonts();
        prefs().edit().clear().apply();
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(ADDRESS);
    }

    @Test
    public void everyDesignTheSettingsListOffersCanBeDrawn() {
        final String[] ids = context.getResources()
                .getStringArray(R.array.oepl_weather_content_values);
        final String[] names = context.getResources()
                .getStringArray(R.array.oepl_weather_content);
        assertEquals("every design needs a name", ids.length, names.length);
        assertTrue("the list should not have shrunk by accident", ids.length >= 29);

        for (final String id : ids) {
            final Bitmap preview = OEPLCardPreview.render(context, device, prefs(), id,
                    384, 184, 4, "no data");
            assertNotNull(id + " could not be drawn", preview);
            assertEquals(id + " width", 384, preview.getWidth());
            assertEquals(id + " height", 184, preview.getHeight());
        }
    }

    @Test
    public void aPreviewIsDrawnAtThePanelTheTagReported() {
        // nothing read from the tag yet: a common shape, so the picker still shows something
        final int[] unknown = OEPLCardPreview.panelSize(prefs());
        assertEquals(OEPLCardPreview.FALLBACK_WIDTH, unknown[0]);
        assertEquals(OEPLCardPreview.FALLBACK_HEIGHT, unknown[1]);

        final OEPLTagTypes.TagType type = OEPLTagTypes.byHardwareType(0x6D);
        assertNotNull("the test display's hardware type has to be in the catalogue", type);
        prefs().edit().putInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, 0x6D).apply();

        final int[] known = OEPLCardPreview.panelSize(prefs());
        assertEquals(type.width, known[0]);
        assertEquals(type.height, known[1]);
        assertEquals(type.colorCount, known[2]);
    }

    @Test
    public void thePickerScreenItselfComesUp() {
        // the layouts behind the picker are only ever inflated on a device, so this is the one
        // place a broken one would be caught before the user taps the entry
        final Intent intent = new Intent(context, OEPLDesignPickerActivity.class)
                .putExtra(GBDevice.EXTRA_DEVICE, device);
        try (ActivityController<OEPLDesignPickerActivity> controller =
                     Robolectric.buildActivity(OEPLDesignPickerActivity.class, intent)) {
            final OEPLDesignPickerActivity activity = controller.setup().get();
            assertNotNull(activity.findViewById(R.id.oepl_design_list));
            assertFalse("the picker needs a display to draw for", activity.isFinishing());
        }
    }

    @Test
    public void thePictureScreenComesUpAndRemembersItsSettings() {
        prefs().edit().putString(OEPLPrefs.PREF_IMAGE_DITHER, "ordered")
                .putString(OEPLPrefs.PREF_IMAGE_FIT, "cover")
                .putString(OEPLPrefs.PREF_CONTENT, "image").apply();

        final Intent intent = new Intent(context, OEPLImageActivity.class)
                .putExtra(GBDevice.EXTRA_DEVICE, device);
        try (ActivityController<OEPLImageActivity> controller =
                     Robolectric.buildActivity(OEPLImageActivity.class, intent)) {
            final OEPLImageActivity activity = controller.setup().get();
            final RadioGroup dither = activity.findViewById(R.id.oepl_image_dither);
            final RadioGroup fit = activity.findViewById(R.id.oepl_image_fit);
            assertEquals("the chosen dithering has to come back",
                    R.id.oepl_image_dither_ordered, dither.getCheckedRadioButtonId());
            assertEquals("and so does the fit",
                    R.id.oepl_image_fit_cover, fit.getCheckedRadioButtonId());

            // switching writes straight through, so the next picture uses it
            dither.check(R.id.oepl_image_dither_none);
            assertEquals("none", prefs().getString(OEPLPrefs.PREF_IMAGE_DITHER, null));
        }
    }

    @Test
    public void aPreviewUsesThePanelColoursRatherThanTheGreysAScreenWouldAllow() {
        final Bitmap preview = OEPLCardPreview.render(context, device, prefs(),
                "oepl_29", 296, 128, 3, "no data");
        final int[] pixels = new int[296 * 128];
        preview.getPixels(pixels, 0, 296, 0, 0, 296, 128);
        for (final int pixel : pixels) {
            final int rgb = pixel & 0xffffff;
            assertTrue("a three colour panel has no greys: " + Integer.toHexString(rgb),
                    rgb == (OEPLImageCodec.BLACK & 0xffffff)
                            || rgb == (OEPLImageCodec.WHITE & 0xffffff)
                            || rgb == (OEPLImageCodec.RED & 0xffffff));
        }
    }
}
