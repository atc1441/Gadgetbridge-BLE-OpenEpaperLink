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
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.atcbleoeplweather.ATCBLEOEPLWeatherSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

/**
 * Inflates the real settings file and runs the real customizer over it, which is the only way to
 * be sure that the sub-menus exist under the keys the customizer hides and that a given card
 * leaves exactly the options it reads on screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OEPLSettingsMenuTest {

    private Context context;
    private PreferenceScreen root;
    private StubHandler handler;

    @Before
    public void setUp() {
        final Context base = RuntimeEnvironment.getApplication();
        base.setTheme(R.style.GadgetbridgeTheme);
        // the preference library needs the overlay the fragment would normally apply
        final TypedArray a = base.obtainStyledAttributes(
                new int[]{androidx.preference.R.attr.preferenceTheme});
        final int overlay = a.getResourceId(0, 0);
        a.recycle();
        context = overlay != 0 ? new ContextThemeWrapper(base, overlay) : base;

        final PreferenceManager manager = new PreferenceManager(context);
        manager.setSharedPreferencesName("devicesettings_oepl_menu_test");
        root = manager.inflateFromResource(context,
                R.xml.devicesettings_oepl_weather_display, null);
        handler = new StubHandler(root, context);
    }

    @Test
    public void thePictureChooserSitsAboveTheListOfNamesAndStaysThereForEveryCard() {
        final Preference picker = root.findPreference("pref_oepl_weather_pick_design");
        assertNotNull("the visual chooser has to be in the settings file", picker);

        // it is the way into every design, so no choice of content may ever hide it
        for (final String content : new String[]{"oepl_4", "oepl_29", "oepl_9", "weather",
                "dashboard"}) {
            customize(content);
            assertTrue(content + " must keep the chooser reachable",
                    visible("pref_oepl_weather_pick_design"));
        }
    }

    @Test
    public void everySubMenuTheCustomizerKnowsAboutIsActuallyInTheFile() {
        for (final String key : new String[]{
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_WEATHER,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_CALENDAR,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_FEED,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_QR,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_COUNTER,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_NOTIFICATIONS,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_TEXT,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_LOOK,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_UPDATES,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_POWER,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_HARDWARE,
        }) {
            final Preference screen = root.findPreference(key);
            assertNotNull(key + " is missing from the settings file", screen);
            assertTrue(key + " must be a sub-menu", screen instanceof PreferenceScreen);
        }
    }

    @Test
    public void theWeatherCardLeavesTheWeatherMenuAndNothingElseContentRelated() {
        customize("oepl_4");

        assertTrue(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_WEATHER));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_CALENDAR));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_FEED));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_QR));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_COUNTER));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_NOTIFICATIONS));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_TEXT));

        // and the menus that are about the display itself are never hidden
        assertTrue(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_LOOK));
        assertTrue(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_UPDATES));
        assertTrue(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_POWER));
        assertTrue(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_HARDWARE));
    }

    @Test
    public void insideTheWeatherMenuOnlyWhatTheCardReadsIsLeft() {
        // the current weather card prints wind, but no forecast span and no sun times
        customize("oepl_4");
        assertTrue(visible(OEPLPrefs.PREF_WEATHER_SOURCE));
        assertTrue(visible(OEPLPrefs.PREF_WIND_UNIT));
        assertFalse(visible(OEPLPrefs.PREF_FORECAST_DAYS));
        assertFalse(visible(OEPLPrefs.PREF_FORECAST_HOURS));
        assertFalse(visible(OEPLPrefs.PREF_SHOW_SUN));

        // the rain card is the other way round: hours, and no wind
        customize("oepl_16");
        assertTrue(visible(OEPLPrefs.PREF_FORECAST_HOURS));
        assertFalse(visible(OEPLPrefs.PREF_WIND_UNIT));
        assertFalse(visible(OEPLPrefs.PREF_FORECAST_DAYS));
    }

    @Test
    public void theFeedCardLeavesTheFeedMenuAndTakesTheWeatherOneAway() {
        customize("oepl_9");

        assertTrue(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_FEED));
        assertFalse(visible(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_WEATHER));
        assertFalse(visible(OEPLPrefs.PREF_WEATHER_LOCATION));
    }

    @Test
    public void theFrameOptionsGoAwayForACardThatDrawsItsOwnPanel() {
        customize("oepl_4");
        assertFalse("an OpenEPaperLink card draws no frame", visible(OEPLPrefs.PREF_FRAME));
        assertFalse(visible(OEPLPrefs.PREF_SHOW_FOOTER));
        // the rotation and the accent colour apply to every card
        assertTrue(visible(OEPLPrefs.PREF_ROTATION));
        assertTrue(visible(OEPLPrefs.PREF_ACCENT_COLOR));

        customize("weather");
        assertTrue("a framed card does", visible(OEPLPrefs.PREF_FRAME));
        assertTrue(visible(OEPLPrefs.PREF_SHOW_FOOTER));
    }

    @Test
    public void aCardWithNothingToSetLeavesNoContentMenuAtAll() {
        customize("oepl_29");

        for (final String key : new String[]{
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_WEATHER,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_CALENDAR,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_FEED,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_QR,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_COUNTER,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_NOTIFICATIONS,
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_TEXT,
        }) {
            assertFalse(key + " has nothing the clock card reads", visible(key));
        }
        // the picker itself always stays, or there would be no way back
        assertTrue(visible(OEPLPrefs.PREF_CONTENT));
    }

    @Test
    public void aMenuSaysWhatIsInsideItWithoutBeingOpened() {
        context.getSharedPreferences("devicesettings_oepl_menu_test", Context.MODE_PRIVATE)
                .edit().putString(OEPLPrefs.PREF_RSS_URL, "https://example.org/feed.xml").apply();
        customize("oepl_9");

        final Preference feed = root.findPreference(
                ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_FEED);
        assertNotNull(feed);
        assertEquals("https://example.org/feed.xml", String.valueOf(feed.getSummary()));
    }

    @Test
    public void anEmptyMenuSaysSoRatherThanStayingBlank() {
        customize("oepl_10");

        final Preference qr = root.findPreference(ATCBLEOEPLWeatherSettingsCustomizer.SCREEN_QR);
        assertNotNull(qr);
        assertEquals(context.getString(R.string.oepl_screen_unset),
                String.valueOf(qr.getSummary()));
    }

    private void customize(final String content) {
        context.getSharedPreferences("devicesettings_oepl_menu_test", Context.MODE_PRIVATE)
                .edit().putString(OEPLPrefs.PREF_CONTENT, content).apply();
        new ATCBLEOEPLWeatherSettingsCustomizer().customizeSettings(handler,
                new Prefs(context.getSharedPreferences("devicesettings_oepl_menu_test",
                        Context.MODE_PRIVATE)), null);
    }

    private boolean visible(final String key) {
        final Preference preference = root.findPreference(key);
        assertNotNull(key + " is not in the settings file", preference);
        return preference.isVisible();
    }

    /** Just enough of the fragment for the customizer to work on an inflated tree. */
    private static class StubHandler implements DeviceSpecificSettingsHandler {
        private final PreferenceScreen screen;
        private final Context context;

        StubHandler(final PreferenceScreen screen, final Context context) {
            this.screen = screen;
            this.context = context;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Preference> T findPreference(@NonNull final CharSequence key) {
            return (T) screen.findPreference(key);
        }

        @Override
        public void addPreferenceHandlerFor(final String key) {
        }

        @Override
        public void addPreferenceHandlerFor(final String key,
                                            final Preference.OnPreferenceChangeListener extra) {
        }

        @Override
        public void notifyPreferenceChanged(final String key) {
        }

        @Override
        public void setInputTypeFor(final String key, final int flags) {
        }

        @NonNull
        @Override
        public GBDevice getDevice() {
            return new GBDevice("FF:FC:4F:05:E4:11", "display", null, null,
                    nodomain.freeyourgadget.gadgetbridge.model.DeviceType.OEPL_WEATHER_DISPLAY);
        }

        @Override
        public PreferenceScreen getPreferenceScreen() {
            return screen;
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void navigateToScreen(@NonNull final PreferenceScreen target) {
        }

        @Override
        public void addXmlPreferences(final int resId) {
        }

        @NonNull
        @Override
        public <I, O> ActivityResultLauncher<I> registerForActivityResult(
                @NonNull final ActivityResultContract<I, O> contract,
                @NonNull final ActivityResultCallback<O> callback) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public <I, O> ActivityResultLauncher<I> registerForActivityResult(
                @NonNull final ActivityResultContract<I, O> contract,
                @NonNull final ActivityResultRegistry registry,
                @NonNull final ActivityResultCallback<O> callback) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Every setting in the file reaches the service when it changes. A key the settings screen
     * does not notify about is changed, nothing happens, and the panel keeps showing the old
     * picture until the next scheduled update - which looks exactly like a broken setting.
     * <p>
     * Buttons that act when they are pressed, and the settings Gadgetbridge itself handles for
     * every device, are listed here as the known exceptions.
     */
    @Test
    public void everySettingInTheFileIsOneTheDisplayHearsAbout() {
        final Set<String> handledElsewhere = new HashSet<>(Arrays.asList(
                // buttons: they notify when pressed, there is no value to watch
                OEPLPrefs.PREF_IMAGE_PICK,
                OEPLPrefs.PREF_READ_CONFIG,
                "pref_oepl_weather_pick_design",
                "pref_oepl_weather_info",
                // the shared calendar settings, registered by the fragment for every device
                "sync_calendar",
                "calendar_blacklist",
                "calendar_lookahead_days",
                "sync_birthdays"));

        final List<String> forgotten = new ArrayList<>();
        for (final Preference preference : all(root)) {
            final String key = preference.getKey();
            if (key == null || key.isEmpty() || preference instanceof PreferenceScreen
                    || preference instanceof PreferenceCategory
                    || handledElsewhere.contains(key)
                    || OEPLPrefs.NOTIFYING_KEYS.contains(key)) {
                continue;
            }
            forgotten.add(key);
        }
        assertEquals("settings in the file that the display is never told about: " + forgotten,
                0, forgotten.size());
    }

    /** Every preference in the tree, sub-menus included. */
    private static List<Preference> all(final PreferenceGroup group) {
        final List<Preference> found = new ArrayList<>();
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference preference = group.getPreference(i);
            found.add(preference);
            if (preference instanceof PreferenceGroup) {
                found.addAll(all((PreferenceGroup) preference));
            }
        }
        return found;
    }
}
