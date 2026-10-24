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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLActions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLFirmware;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLProtocol;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * The things a display can be told to do once, as opposed to the settings it is given.
 * <p>
 * All four of them exist because a wall of identical panels needs them: pointing at one of them
 * with its own LED, restarting one that has gone odd, putting one to sleep before it is packed
 * away, and writing a new firmware into one. They are kept away from the settings list on
 * purpose - a settings screen is a place where every line can be tried out and undone, and two
 * of these cannot.
 */
public class OEPLToolsActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLToolsActivity.class);

    /** The largest firmware the display's own update area takes, with room to spare. */
    private static final int FIRMWARE_LIMIT = 262144;

    private GBDevice device;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Chosen while the display was out of reach, written as soon as it answers. */
    @Nullable
    private Uri armedFirmware;

    private final ActivityResultLauncher<String[]> pick = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::chosen);

    private final BroadcastReceiver deviceChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final GBDevice updated = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
            if (updated == null || !updated.getAddress().equals(device.getAddress())) {
                return;
            }
            device = updated;
            if (armedFirmware != null && device.isInitialized()) {
                final Uri firmware = armedFirmware;
                armedFirmware = null;
                write(firmware);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_tools);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(device.getAliasOrName());

        new Choice(R.id.oepl_tools_color, OEPLPrefs.PREF_LED_COLOR, "red",
                R.array.oepl_weather_led_colors, R.array.oepl_weather_led_colors_values,
                R.string.oepl_tools_color, R.string.oepl_tools_color_title);
        findViewById(R.id.oepl_tools_pattern).setOnClickListener(v -> {
            final Intent intent = new Intent(this, OEPLBlinkActivity.class);
            intent.putExtra(GBDevice.EXTRA_DEVICE, device);
            startActivity(intent);
        });

        findViewById(R.id.oepl_tools_blink).setOnClickListener(v ->
                ask(OEPLActions.BLINK));
        findViewById(R.id.oepl_tools_reboot).setOnClickListener(v ->
                ask(OEPLActions.REBOOT));
        findViewById(R.id.oepl_tools_sleep).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_tools_sleep)
                .setMessage(R.string.oepl_tools_sleep_confirm)
                .setPositiveButton(R.string.oepl_tools_sleep_go, (d, w) -> ask(OEPLActions.SLEEP))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        findViewById(R.id.oepl_tools_firmware_many).setOnClickListener(v ->
                startActivity(new Intent(this, OEPLFirmwareActivity.class)));
        findViewById(R.id.oepl_tools_firmware).setOnClickListener(v -> {
            try {
                // Not "application/octet-stream": phones disagree about what a .bin is, and a
                // chooser that shows nothing at all is worse than one that shows too much.
                pick.launch(new String[]{"*/*"});
            } catch (final Exception e) {
                LOG.warn("no file chooser on this phone", e);
                GB.toast(getString(R.string.oepl_image_no_picker), 3000, GB.ERROR);
            }
        });
        findViewById(R.id.oepl_tools_firmware_forget).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.oepl_firmware_forget)
                        .setMessage(R.string.oepl_firmware_forget_confirm)
                        .setPositiveButton(R.string.oepl_firmware_forget_go,
                                (d, w) -> forgetFirmware())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
        showFirmwareNote();
    }

    /**
     * The way out of a firmware that will not go through.
     * <p>
     * A noted firmware takes precedence over a picture, because a transfer that was broken off
     * is meant to be picked up again rather than abandoned - which is right until the display
     * is one that will not take this file at all. Then the note is in the way of everything
     * else, and this is the button that removes it. Five failed attempts remove it as well, but
     * a display that is only reachable now and then can take days to get through five, and
     * nobody should have to wait that out.
     */
    private void forgetFirmware() {
        OEPLFirmware.giveUp(device.getAddress());
        showFirmwareNote();
        GB.toast(getString(R.string.oepl_firmware_forgotten), 4000, GB.INFO);
        // and the card goes back on the panel rather than at the end of the interval
        OEPLUpdateScheduler.requestUpdateNow(this, device);
    }

    /** The button only exists while there is a note to drop. */
    private void showFirmwareNote() {
        final boolean pending = OEPLFirmware.pending(prefs());
        final int visibility = pending ? android.view.View.VISIBLE : android.view.View.GONE;
        findViewById(R.id.oepl_tools_firmware_forget).setVisibility(visibility);
        findViewById(R.id.oepl_tools_firmware_forget_hint).setVisibility(visibility);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(deviceChanged,
                new IntentFilter(GBDevice.ACTION_DEVICE_CHANGED));
        showFirmwareNote();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(deviceChanged);
    }

    /** Hands the action over and says whether it happened or was written down for later. */
    private void ask(final String action) {
        final boolean now = OEPLActions.request(this, device, action);
        GB.toast(getString(now ? R.string.oepl_tools_sent : R.string.oepl_tools_queued),
                2500, GB.INFO);
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    /**
     * One of the four things that shape the blink: which colour, how many flashes, how many
     * rounds of them, how bright.
     * <p>
     * They sit here rather than in the settings list because they belong to the button they
     * shape - the blink is a thing done once, not a property of the display - and because that
     * is where they are wanted: a shelf of identical panels is told apart by giving each one a
     * blink of its own, and that is decided with a finger on the button, not three menus away.
     */
    private final class Choice {
        private final Button button;
        private final String key;
        private final String fallback;
        private final int names;
        private final int values;
        private final int label;
        private final int title;

        Choice(final int view, final String key, final String fallback, final int names,
               final int values, final int label, final int title) {
            this.button = findViewById(view);
            this.key = key;
            this.fallback = fallback;
            this.names = names;
            this.values = values;
            this.label = label;
            this.title = title;
            show();
            button.setOnClickListener(v -> ask());
        }

        private String[] names() {
            return getResources().getStringArray(names);
        }

        private String[] values() {
            return getResources().getStringArray(values);
        }

        /** Where the stored value sits in the list, or the first entry when it is not in it. */
        private int at() {
            final int at = Arrays.asList(values()).indexOf(prefs().getString(key, fallback));
            return at < 0 ? 0 : at;
        }

        private void show() {
            button.setText(getString(label, names()[at()]));
        }

        private void ask() {
            final String[] values = values();
            new AlertDialog.Builder(OEPLToolsActivity.this)
                    .setTitle(title)
                    .setSingleChoiceItems(names(), at(), (dialog, which) -> {
                        prefs().edit().putString(key, values[which]).apply();
                        show();
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    /**
     * Checks the chosen file before anything is sent.
     * <p>
     * A display writes whatever it is handed into its own program memory and runs it, so a file
     * that is not firmware for this chip is a display that has to be opened and flashed over
     * wires. The tag itself checks nothing. What can be checked from here is the mark the
     * Telink toolchain leaves eight bytes in, and the length the image claims to have, which is
     * exactly what the browser based uploader looks at as well.
     */
    private void chosen(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        // Read away from the main thread. The chooser is free to hand over a file that does
        // not sit on the phone at all - one out of a cloud folder is fetched over the network
        // when it is opened - and reading that on the main thread freezes the screen for as
        // long as it takes. Which is exactly long enough to press back, and the confirmation
        // then had no window left to open in and took the app down with it.
        final Thread thread = new Thread(() -> {
            byte[] read = null;
            try (InputStream in =
                         new BufferedInputStream(getContentResolver().openInputStream(uri))) {
                read = nodomain.freeyourgadget.gadgetbridge.util.FileUtils
                        .readAll(in, FIRMWARE_LIMIT);
            } catch (final Exception e) {
                LOG.warn("could not read {}", uri, e);
            }
            final byte[] firmware = read;
            handler.post(() -> offer(uri, firmware));
        }, "oepl-firmware-read");
        thread.setDaemon(true);
        thread.start();
    }

    /** Back on the main thread, and only while there is still a screen to ask on. */
    private void offer(final Uri uri, @Nullable final byte[] firmware) {
        if (gone()) {
            return;
        }
        if (firmware == null || !OEPLProtocol.looksLikeFirmware(firmware)) {
            GB.toast(getString(R.string.oepl_tools_firmware_not_firmware), 4000, GB.ERROR);
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (final Exception e) {
            LOG.info("no lasting permission for {}", uri, e);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_tools_firmware)
                .setMessage(getString(R.string.oepl_tools_firmware_confirm,
                        firmware.length / 1024))
                .setPositiveButton(R.string.oepl_tools_firmware_go, (d, w) -> write(uri))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Whether this screen is on its way out, and nothing may be shown on it any more. */
    private boolean gone() {
        return isFinishing() || isDestroyed();
    }

    /**
     * Hands the file to the transfer, which needs the display on the line for minutes rather
     * than seconds - so unlike the other three this one opens the connection itself and waits
     * for it instead of leaving a note for the next time the display happens to answer.
     */
    private void write(final Uri uri) {
        if (device.isInitialized()) {
            GBApplication.deviceService(device).onInstallApp(uri, new Bundle());
            return;
        }
        armedFirmware = uri;
        GB.toast(getString(R.string.oepl_tools_firmware_connecting), 3000, GB.INFO);
        GBApplication.deviceService(device).connect();
    }
}
