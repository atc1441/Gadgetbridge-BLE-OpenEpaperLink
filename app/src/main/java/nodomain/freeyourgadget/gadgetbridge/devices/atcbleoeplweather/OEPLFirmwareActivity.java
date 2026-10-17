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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLFirmware;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLProtocol;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLStatusStore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLTagTypes;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * One firmware onto as many displays as you tick.
 * <p>
 * A shelf of these displays is not one kind of display: they are built around more than one
 * radio and bought at different times, so the file that belongs on one of them is wrong for
 * the next. That is the whole reason this is a list with boxes rather than a single button -
 * every display shows what it is and which firmware it is running, and the choice of which of
 * them get the file stays with the reader.
 * <p>
 * They are written one after another, because a firmware takes the whole link for minutes. A
 * display that is out of reach when its turn comes is not skipped: it keeps the note and is
 * flashed the next time it answers, whether or not this screen is still open.
 */
public class OEPLFirmwareActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLFirmwareActivity.class);

    /** How long one display may take before the queue moves on without it. */
    private static final long PER_DISPLAY_MILLIS = 6 * 60_000L;
    private static final long TICK_MILLIS = 1500L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout list;
    private TextView chosen;
    private Button distribute;
    private Button forget;

    /** The displays on screen, by address, in the order they are listed. */
    private final Map<String, GBDevice> displays = new LinkedHashMap<>();
    private final Map<String, CheckBox> boxes = new LinkedHashMap<>();
    private final Map<String, TextView> details = new LinkedHashMap<>();

    @Nullable
    private Uri firmware;
    private int firmwareBytes;

    /** The addresses still to be done, and which one is being worked on. */
    private final List<String> queue = new ArrayList<>();
    private int at = -1;
    private long startedAt;

    private final ActivityResultLauncher<String[]> pick = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::chosenFile);

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_firmware);

        list = findViewById(R.id.oepl_firmware_list);
        chosen = findViewById(R.id.oepl_firmware_chosen);
        distribute = findViewById(R.id.oepl_firmware_distribute);
        forget = findViewById(R.id.oepl_firmware_forget);
        forget.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_firmware_forget)
                .setMessage(R.string.oepl_firmware_forget_confirm)
                .setPositiveButton(R.string.oepl_firmware_forget_go, (d, w) -> forgetAll())
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        findViewById(R.id.oepl_firmware_pick).setOnClickListener(v -> {
            try {
                pick.launch(new String[]{"*/*"});
            } catch (final Exception e) {
                LOG.warn("no file chooser on this phone", e);
                GB.toast(getString(R.string.oepl_image_no_picker), 3000, GB.ERROR);
            }
        });
        findViewById(R.id.oepl_firmware_all).setOnClickListener(v -> {
            final boolean anyUnticked = anyUnticked();
            for (final CheckBox box : boxes.values()) {
                box.setChecked(anyUnticked);
            }
            updateButton();
        });
        distribute.setOnClickListener(v -> confirm());

        fillList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    // ------------------------------------------------------------------ the list

    private void fillList() {
        final LayoutInflater inflater = LayoutInflater.from(this);
        for (final GBDevice device : GBApplication.app().getDeviceManager().getDevices()) {
            if (device.getType() != DeviceType.OEPL_WEATHER_DISPLAY) {
                continue;
            }
            final String address = device.getAddress();
            displays.put(address, device);

            final View row = inflater.inflate(R.layout.item_oepl_firmware_display, list, false);
            final CheckBox box = row.findViewById(R.id.oepl_firmware_row_check);
            box.setText(device.getAliasOrName());
            box.setOnCheckedChangeListener((button, checked) -> updateButton());
            boxes.put(address, box);
            details.put(address, row.findViewById(R.id.oepl_firmware_row_details));
            list.addView(row);
        }
        if (displays.isEmpty()) {
            final TextView empty = new TextView(this);
            empty.setText(R.string.oepl_firmware_no_displays);
            list.addView(empty);
        }
        refresh();
    }

    /** What a display is and what it is running, which is what the choice is made on. */
    private String describe(final String address) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final StringBuilder out = new StringBuilder();
        final int hardware = prefs.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        out.append(hardware >= 0 ? OEPLTagTypes.describe(hardware)
                : getString(R.string.oepl_screen_unset));
        final String version = OEPLFirmware.version(prefs);
        out.append(" · ").append(getString(R.string.oepl_firmware_version,
                version.isEmpty() ? "?" : version));
        if (OEPLFirmware.pending(prefs)) {
            out.append(" · ").append(getString(R.string.oepl_firmware_queued));
        }
        final String result = prefs.getString(OEPLStatusStore.LAST_RESULT, "");
        if (!result.isEmpty()) {
            out.append(" · ").append(result);
        }
        return out.toString();
    }

    private void refresh() {
        for (final Map.Entry<String, TextView> entry : details.entrySet()) {
            entry.getValue().setText(describe(entry.getKey()));
        }
        forget.setVisibility(waiting().isEmpty() ? View.GONE : View.VISIBLE);
        updateButton();
    }

    /** The displays that are still owed a firmware, whether or not this screen handed it out. */
    private List<String> waiting() {
        final List<String> left = new ArrayList<>();
        for (final String address : displays.keySet()) {
            if (OEPLFirmware.pending(GBApplication.getDeviceSpecificSharedPrefs(address))) {
                left.add(address);
            }
        }
        return left;
    }

    /**
     * Takes the note off every display that is still carrying one, and stops the rollout.
     * <p>
     * The counterpart to handing the file out: a file that turns out to be the wrong one for
     * these displays would otherwise be offered to each of them for five more connections, with
     * their cards standing still in the meantime.
     */
    private void forgetAll() {
        handler.removeCallbacksAndMessages(null);
        at = -1;
        startedAt = 0L;
        final List<String> waiting = waiting();
        for (final String address : waiting) {
            OEPLFirmware.giveUp(address);
            final GBDevice display = displays.get(address);
            if (display != null) {
                OEPLUpdateScheduler.armNext(this, address, false);
            }
        }
        for (final CheckBox box : boxes.values()) {
            box.setEnabled(true);
        }
        LOG.info("dropped the firmware note of {} displays", waiting.size());
        GB.toast(getString(R.string.oepl_firmware_forgotten), 4000, GB.INFO);
        refresh();
    }

    private boolean anyUnticked() {
        for (final CheckBox box : boxes.values()) {
            if (!box.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private List<String> ticked() {
        final List<String> picked = new ArrayList<>();
        for (final Map.Entry<String, CheckBox> entry : boxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                picked.add(entry.getKey());
            }
        }
        return picked;
    }

    private void updateButton() {
        final int count = ticked().size();
        distribute.setEnabled(firmware != null && count > 0 && at < 0);
        distribute.setText(getString(R.string.oepl_firmware_distribute, count));
    }

    // ------------------------------------------------------------------ the file

    private void chosenFile(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        byte[] content = null;
        try (InputStream in = new BufferedInputStream(getContentResolver().openInputStream(uri))) {
            content = FileUtils.readAll(in, 262144);
        } catch (final Exception e) {
            LOG.warn("could not read {}", uri, e);
        }
        if (content == null || !OEPLProtocol.looksLikeFirmware(content)) {
            GB.toast(getString(R.string.oepl_tools_firmware_not_firmware), 4000, GB.ERROR);
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (final Exception e) {
            // without a lasting grant the file is still readable for as long as this screen
            // lives, which covers a rollout that is watched rather than left running
            LOG.info("no lasting permission for {}", uri, e);
        }
        firmware = uri;
        firmwareBytes = content.length;
        chosen.setText(getString(R.string.oepl_firmware_chosen, uri.getLastPathSegment(),
                firmwareBytes / 1024));
        updateButton();
    }

    // ------------------------------------------------------------------ the rollout

    private void confirm() {
        final List<String> picked = ticked();
        if (firmware == null || picked.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_firmware_title)
                .setMessage(getString(R.string.oepl_firmware_confirm, picked.size(),
                        firmwareBytes / 1024))
                .setPositiveButton(R.string.oepl_tools_firmware_go, (d, w) -> start(picked))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void start(final List<String> picked) {
        queue.clear();
        queue.addAll(picked);
        for (final String address : queue) {
            OEPLFirmware.arm(address, firmware.toString());
        }
        at = 0;
        startedAt = 0L;
        for (final CheckBox box : boxes.values()) {
            box.setEnabled(false);
        }
        refresh();
        tick();
    }

    /**
     * Drives the queue and the screen from one place, once every second and a half.
     * <p>
     * A display is done when its note is gone - the firmware is on it, or the attempts ran out -
     * and the next one is sent for. Nothing here does the flashing: that is the display service,
     * which is why closing this screen does not undo a rollout. The displays left over keep
     * their note and are flashed as they come within reach.
     */
    private void tick() {
        handler.removeCallbacksAndMessages(null);
        if (at < 0 || at >= queue.size()) {
            finishRollout();
            return;
        }
        final String address = queue.get(at);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final GBDevice device = displays.get(address);

        if (!OEPLFirmware.pending(prefs)) {
            at++;
            startedAt = 0L;
        } else if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
            if (device != null) {
                LOG.info("sending for {} to write the firmware", address);
                OEPLUpdateScheduler.requestUpdateNow(this, device);
            }
        } else if (System.currentTimeMillis() - startedAt > PER_DISPLAY_MILLIS) {
            LOG.info("{} is taking too long, moving on - it keeps the note", address);
            at++;
            startedAt = 0L;
        }
        refresh();
        handler.postDelayed(this::tick, TICK_MILLIS);
    }

    private void finishRollout() {
        at = -1;
        for (final CheckBox box : boxes.values()) {
            box.setEnabled(true);
        }
        int left = 0;
        for (final String address : queue) {
            if (OEPLFirmware.pending(GBApplication.getDeviceSpecificSharedPrefs(address))) {
                left++;
            }
        }
        final String done = left == 0
                ? getString(R.string.oepl_firmware_all_done)
                : getString(R.string.oepl_firmware_left, left);
        GB.toast(done, 4000, GB.INFO);
        refresh();
    }
}
