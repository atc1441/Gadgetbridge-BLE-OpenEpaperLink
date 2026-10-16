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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.HomeAssistantClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLContentSync;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLStatusStore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLTagTypes;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OpenMeteoClient;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils;

/**
 * Everything known about one weather display, on one screen.
 * <p>
 * A display like this is reachable for a few seconds at a time, so the usual "connected" dot
 * says almost nothing. What is worth knowing is how loud it was last heard, what its panel
 * actually is, how much data the last picture took and how long that lasted, how often it was
 * missed, and what it is showing right now - including a preview of the exact card it was
 * handed. All of that is collected while the radio is up and read back here.
 */
public class ATCBLEOEPLWeatherInfoActivity extends AbstractGBActivity {

    private GBDevice device;
    private LinearLayout sections;
    private ImageView preview;
    private TextView previewNote;

    private final BroadcastReceiver deviceChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final GBDevice updated = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
            if (updated != null && updated.getAddress().equals(device.getAddress())) {
                device = updated;
            }
            fill();
        }
    };

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_weather_info);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(device.getAliasOrName());

        sections = findViewById(R.id.oepl_info_sections);
        preview = findViewById(R.id.oepl_info_preview);
        previewNote = findViewById(R.id.oepl_info_preview_note);

        findViewById(R.id.oepl_info_update_now).setOnClickListener(v -> {
            OEPLUpdateScheduler.requestUpdateNow(this, device);
            GB.toast(getString(R.string.oepl_info_queued), 2000, GB.INFO);
        });
        findViewById(R.id.oepl_info_force).setOnClickListener(v -> {
            // forgetting the checksum is what makes the next push go out even if nothing changed
            prefs().edit().putLong(OEPLPrefs.LAST_IMAGE_CRC, 0L).apply();
            // every source, not only the weather: a forced redraw is asked for when something
            // on screen looks wrong, and the headlines or the picture are as likely to be it
            OEPLContentSync.invalidateAll(device.getAddress());
            OEPLUpdateScheduler.requestUpdateNow(this, device);
            GB.toast(getString(R.string.oepl_info_queued), 2000, GB.INFO);
        });
        findViewById(R.id.oepl_info_pick_design).setOnClickListener(v -> {
            final Intent intent = new Intent(this, OEPLDesignPickerActivity.class);
            intent.putExtra(GBDevice.EXTRA_DEVICE, device);
            startActivity(intent);
        });
        findViewById(R.id.oepl_info_read_config).setOnClickListener(v -> {
            if (!device.isInitialized()) {
                GB.toast(getString(R.string.oepl_info_not_connected), 2500, GB.WARN);
                return;
            }
            GBApplication.deviceService(device).onSendConfiguration(OEPLPrefs.PREF_READ_CONFIG);
        });
        findViewById(R.id.oepl_info_reset_stats).setOnClickListener(v -> {
            OEPLStatusStore.resetCounters(device.getAddress());
            fill();
        });
        // Only offered while it would actually change something. Without the exemption, Android
        // is free to hold the update alarm back for as long as it likes, which is the single
        // biggest reason a display sits there with yesterday's weather on it.
        findViewById(R.id.oepl_info_battery_exempt).setOnClickListener(v ->
                PermissionsUtils.requestPermission(this,
                        PermissionsUtils.CUSTOM_PERM_IGNORE_BATT_OPTIM));
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(deviceChanged,
                new IntentFilter(GBDevice.ACTION_DEVICE_CHANGED));
        fill();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(deviceChanged);
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    private void fill() {
        if (sections == null) {
            return;
        }
        sections.removeAllViews();
        final SharedPreferences p = prefs();

        showPreview();
        connection(p);
        schedule(p);
        panel(p);
        lastTransfer(p);
        counters(p);
        contentSection(p);
    }

    private void showPreview() {
        final File file = OEPLStatusStore.previewFile(this, device.getAddress());
        if (!file.exists()) {
            preview.setVisibility(View.GONE);
            previewNote.setText(R.string.oepl_info_no_preview);
            return;
        }
        final Bitmap bitmap = OEPLStatusStore.readPreview(this, device.getAddress());
        if (bitmap == null) {
            preview.setVisibility(View.GONE);
            previewNote.setText(R.string.oepl_info_no_preview);
            return;
        }
        preview.setVisibility(View.VISIBLE);
        preview.setImageBitmap(bitmap);
        previewNote.setText(getString(R.string.oepl_info_preview_note,
                bitmap.getWidth() + "x" + bitmap.getHeight(),
                ago(file.lastModified())));
    }

    private int rotationDegrees() {
        final SharedPreferences p = prefs();
        final int configured = OEPLPrefs.rotation(p);
        if (configured >= 0) {
            return configured;
        }
        final OEPLTagTypes.TagType type = tagType(p);
        return type != null ? type.rotationDegrees() : 0;
    }

    @Nullable
    private OEPLTagTypes.TagType tagType(final SharedPreferences p) {
        final int hardware = p.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        return hardware >= 0 ? OEPLTagTypes.byHardwareType(hardware) : null;
    }

    // ----------------------------------------------------------------- the sections

    private void connection(final SharedPreferences p) {
        header(R.string.oepl_info_section_radio);
        row(getString(R.string.oepl_info_state), String.valueOf(device.getStateString(this)));
        final long seen = p.getLong(OEPLPrefs.LAST_SEEN_MILLIS, 0L);
        row(getString(R.string.oepl_info_last_seen), seen > 0 ? ago(seen) : dash());

        final int rssi = p.getInt(OEPLStatusStore.LAST_RSSI, 0);
        if (rssi != 0) {
            row(getString(R.string.oepl_info_signal), rssi + " dBm " + signalWords(rssi));
        }
        final long scan = p.getLong(OEPLStatusStore.LAST_SCAN_MILLIS, 0L);
        if (scan > 0) {
            final boolean found = p.getBoolean(OEPLStatusStore.LAST_SCAN_FOUND, false);
            final long took = p.getLong(OEPLStatusStore.LAST_SCAN_DURATION, 0L);
            row(getString(R.string.oepl_info_last_scan), ago(scan) + ", "
                    + getString(found ? R.string.oepl_info_scan_found
                    : R.string.oepl_info_scan_missed)
                    + " (" + took / 1000 + " s)");
        }
        final int battery = p.getInt(OEPLPrefs.LAST_BATTERY_MV, -1);
        if (battery > 0) {
            row(getString(R.string.oepl_info_battery), String.format(Locale.ROOT,
                    "%.2f V (%d %%)", battery / 1000f, OEPLPrefs.batteryPercent(battery)));
        }
        final int temperature = p.getInt(OEPLPrefs.LAST_PANEL_TEMPERATURE, -100);
        if (temperature > -100) {
            row(getString(R.string.oepl_info_temperature), temperature + " °C");
        }
        row(getString(R.string.oepl_info_adv_interval),
                p.getString(OEPLPrefs.PREF_ADV_INTERVAL, dash()) + " ms");
        final long connected = p.getLong(OEPLStatusStore.LAST_CONNECT_MILLIS, 0L);
        if (connected > 0) {
            row(getString(R.string.oepl_info_last_connect), ago(connected));
        }
    }

    /**
     * How the display is being kept up to date, which is the part that is hardest to see from the
     * outside: when the next attempt is armed, what set the last one off, whether the Bluetooth
     * controller is listening on our behalf, and whether Android's battery optimisation is
     * allowed to sit on all of it.
     */
    private void schedule(final SharedPreferences p) {
        header(R.string.oepl_info_schedule);

        final int interval = OEPLPrefs.updateIntervalMinutes(p);
        row(getString(R.string.oepl_info_interval), interval + " min");

        final long due = p.getLong(OEPLStatusStore.NEXT_DUE_MILLIS, 0L);
        if (due > 0) {
            final boolean overdue = due < System.currentTimeMillis();
            row(getString(R.string.oepl_info_next_due), when(due)
                    + (overdue ? " (" + getString(R.string.oepl_info_overdue) + ")" : ""));
        } else {
            row(getString(R.string.oepl_info_next_due), dash());
        }

        final String trigger = p.getString(OEPLStatusStore.LAST_TRIGGER, "");
        final long triggeredAt = p.getLong(OEPLStatusStore.LAST_TRIGGER_MILLIS, 0L);
        if (!trigger.isEmpty()) {
            row(getString(R.string.oepl_info_last_trigger), trigger
                    + (triggeredAt > 0 ? ", " + ago(triggeredAt) : ""));
        }

        row(getString(R.string.oepl_info_attempts),
                String.valueOf(p.getInt(OEPLStatusStore.COUNT_ATTEMPTS, 0)));
        final int inARow = p.getInt(OEPLStatusStore.CONSECUTIVE_FAILURES, 0);
        if (inARow > 0) {
            row(getString(R.string.oepl_info_consecutive_failures), String.valueOf(inARow));
        }

        row(getString(R.string.oepl_info_background_listening),
                getString(p.getBoolean(OEPLStatusStore.OPPORTUNISTIC, false)
                        ? R.string.yes : R.string.no));

        final boolean exempt = PermissionsUtils.checkPermission(this,
                PermissionsUtils.CUSTOM_PERM_IGNORE_BATT_OPTIM);
        row(getString(R.string.oepl_info_battery_optimisation),
                getString(exempt ? R.string.oepl_info_battery_optimisation_off
                        : R.string.oepl_info_battery_optimisation_on));
        final View exemptButton = findViewById(R.id.oepl_info_battery_exempt);
        if (exemptButton != null) {
            exemptButton.setVisibility(exempt ? View.GONE : View.VISIBLE);
        }
    }

    private void panel(final SharedPreferences p) {
        header(R.string.oepl_info_section_panel);
        final OEPLTagTypes.TagType type = tagType(p);
        final int hardware = p.getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        row(getString(R.string.oepl_info_model), type != null
                ? type.name + " (0x" + Integer.toHexString(hardware).toUpperCase(Locale.ROOT) + ")"
                : (hardware >= 0 ? "0x" + Integer.toHexString(hardware) : dash()));
        if (type != null) {
            row(getString(R.string.oepl_info_panel_size), type.width + " x " + type.height);
            row(getString(R.string.oepl_info_colors), String.valueOf(type.colorCount));
        }
        final int width = p.getInt(OEPLPrefs.PANEL_WIDTH, -1);
        final int height = p.getInt(OEPLPrefs.PANEL_HEIGHT, -1);
        if (width > 0 && height > 0) {
            row(getString(R.string.oepl_info_buffer), width + " x " + height
                    + " (" + (width * height / 8) + " B/Ebene)");
        }
        row(getString(R.string.oepl_info_rotation), rotationDegrees() + "°");
        final int firmware = p.getInt(OEPLPrefs.DETECTED_FIRMWARE, -1);
        if (firmware > 0) {
            row(getString(R.string.oepl_info_firmware), String.valueOf(firmware));
        }
        final int settings = p.getInt(OEPLStatusStore.SETTINGS_VERSION, -1);
        if (settings > 0) {
            row(getString(R.string.oepl_info_settings_version), String.valueOf(settings));
        }
        final String summary = p.getString(OEPLStatusStore.CONFIG_SUMMARY, null);
        if (summary != null && !summary.isEmpty()) {
            row(getString(R.string.oepl_info_reported), summary);
        }
        row(getString(R.string.oepl_info_address), device.getAddress());
    }

    private void lastTransfer(final SharedPreferences p) {
        header(R.string.oepl_info_section_transfer);
        final String result = p.getString(OEPLStatusStore.LAST_RESULT, null);
        final long at = p.getLong(OEPLStatusStore.LAST_RESULT_MILLIS, 0L);
        row(getString(R.string.oepl_info_result), result == null ? dash()
                : describeResult(result) + (at > 0 ? ", " + ago(at) : ""));

        final int raw = p.getInt(OEPLStatusStore.LAST_BYTES_RAW, 0);
        final int sent = p.getInt(OEPLStatusStore.LAST_BYTES_SENT, 0);
        if (sent > 0) {
            final String ratio = raw > 0
                    ? String.format(Locale.getDefault(), " (%.0f %%)", 100f * sent / raw) : "";
            row(getString(R.string.oepl_info_bytes), (raw > 0 ? raw + " → " : "")
                    + sent + " B" + ratio);
        }
        final long duration = p.getLong(OEPLStatusStore.LAST_DURATION_MILLIS, 0L);
        if (duration > 0) {
            row(getString(R.string.oepl_info_duration),
                    String.format(Locale.ROOT, "%.1f s", duration / 1000f));
        }
        final long lastUpdate = p.getLong(OEPLPrefs.LAST_UPDATE_MILLIS, 0L);
        if (lastUpdate > 0) {
            row(getString(R.string.oepl_info_last_picture), ago(lastUpdate));
        }
        final int interval = OEPLPrefs.updateIntervalMinutes(p);
        row(getString(R.string.oepl_info_interval), interval + " min");
        if (lastUpdate > 0) {
            row(getString(R.string.oepl_info_next_attempt),
                    when(lastUpdate + interval * 60_000L));
        }
    }

    private void counters(final SharedPreferences p) {
        header(R.string.oepl_info_section_counters);
        row(getString(R.string.oepl_info_count_transfers),
                String.valueOf(p.getInt(OEPLStatusStore.COUNT_TRANSFERS, 0)));
        row(getString(R.string.oepl_info_count_unchanged),
                String.valueOf(p.getInt(OEPLStatusStore.COUNT_UNCHANGED, 0)));
        row(getString(R.string.oepl_info_count_failures),
                String.valueOf(p.getInt(OEPLStatusStore.COUNT_FAILURES, 0)));
        final int scans = p.getInt(OEPLStatusStore.COUNT_SCANS, 0);
        final int misses = p.getInt(OEPLStatusStore.COUNT_SCAN_MISSES, 0);
        row(getString(R.string.oepl_info_count_scans), scans + " / " + misses
                + (scans > 0 ? " (" + getString(R.string.oepl_info_reached,
                Math.round(100f * (scans - misses) / scans)) + ")" : ""));
    }

    private void contentSection(final SharedPreferences p) {
        header(R.string.oepl_info_section_content);
        row(getString(R.string.oepl_info_card), contentLabel(OEPLPrefs.contentType(p)));
        row(getString(R.string.oepl_info_frame), OEPLPrefs.frame(p).name());
        row(getString(R.string.oepl_info_weather_source), OEPLPrefs.useOpenMeteo(p)
                ? "Open-Meteo" : "Gadgetbridge");
        final String place = OEPLPrefs.weatherLocation(p);
        row(getString(R.string.oepl_info_place), place.isEmpty() ? dash() : place);

        final nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl
                .OeplWeather weather = OpenMeteoClient.cachedOepl();
        if (weather != null && weather.hasData()) {
            final long fetched = OpenMeteoClient.cachedAtMillis();
            row(getString(R.string.oepl_info_weather_age),
                    ago(fetched > 0 ? fetched : weather.fetchedAt));
            final String error = OpenMeteoClient.lastError();
            if (!error.isEmpty()) {
                row(getString(R.string.oepl_info_weather_source_state), error);
            }
            row(getString(R.string.oepl_info_weather_now), String.format(Locale.ROOT,
                    "%.1f °C, %s", weather.current.temperature,
                    weather.days.isEmpty() ? ""
                            : Math.round(weather.days.get(0).minTemperature) + " / "
                            + Math.round(weather.days.get(0).maxTemperature) + " °C"));
        } else {
            row(getString(R.string.oepl_info_weather_age), dash());
        }
        homeAssistant(p);
    }

    /**
     * Whether the house is being read at all, and what went wrong if it is not. A token that
     * was mistyped fails silently on the panel - the card simply has fewer values on it - so
     * this is the one place that says so.
     */
    private void homeAssistant(final SharedPreferences p) {
        if (!HomeAssistantClient.configured(OEPLPrefs.homeAssistantUrl(p),
                OEPLPrefs.homeAssistantToken(p), OEPLPrefs.homeAssistantEntities(p))) {
            return;
        }
        final int count = HomeAssistantClient.cached().size();
        final long fetched = HomeAssistantClient.cachedAtMillis();
        row(getString(R.string.oepl_info_home_assistant), count > 0 && fetched > 0
                ? getString(R.string.oepl_info_ha_entities, count, ago(fetched))
                : dash());
        final String error = HomeAssistantClient.lastError();
        if (!error.isEmpty()) {
            row(getString(R.string.oepl_info_weather_source_state), error);
        }
    }

    /** Maps the stored content id back onto the label the settings list shows. */
    private String contentLabel(final String id) {
        final String[] values = getResources().getStringArray(R.array.oepl_weather_content_values);
        final String[] labels = getResources().getStringArray(R.array.oepl_weather_content);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(id)) {
                return labels[i];
            }
        }
        return id;
    }

    private String describeResult(final String result) {
        switch (result) {
            case "ok":
                return getString(R.string.oepl_info_result_ok);
            case "unchanged":
                return getString(R.string.oepl_info_result_unchanged);
            default:
                return getString(R.string.oepl_info_result_failed);
        }
    }

    private String signalWords(final int rssi) {
        if (rssi >= -60) {
            return getString(R.string.oepl_info_signal_strong);
        }
        if (rssi >= -80) {
            return getString(R.string.oepl_info_signal_fair);
        }
        return getString(R.string.oepl_info_signal_weak);
    }

    // ----------------------------------------------------------------- small helpers

    private void header(final int titleRes) {
        final TextView view = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_oepl_info_header, (ViewGroup) sections, false);
        view.setText(titleRes);
        sections.addView(view);
    }

    private void row(final String label, final String value) {
        final View view = LayoutInflater.from(this)
                .inflate(R.layout.item_oepl_info_row, (ViewGroup) sections, false);
        ((TextView) view.findViewById(R.id.oepl_info_row_label)).setText(label);
        ((TextView) view.findViewById(R.id.oepl_info_row_value)).setText(value);
        sections.addView(view);
    }

    private String dash() {
        return "–";
    }

    private String when(final long millis) {
        return new SimpleDateFormat("EE HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    /** "vor 3 min", because an absolute time says little about a display checked hourly. */
    private String ago(final long millis) {
        final long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (seconds < 90) {
            return getString(R.string.oepl_info_ago_seconds, seconds);
        }
        if (seconds < 5400) {
            return getString(R.string.oepl_info_ago_minutes, seconds / 60);
        }
        if (seconds < 172800) {
            return getString(R.string.oepl_info_ago_hours, seconds / 3600);
        }
        return new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
