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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.HomeAssistantClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Picking the entities a display shows, out of what the instance actually has.
 * <p>
 * Entity ids are not something anybody knows by heart, and the alternative - copying them one
 * by one out of the Home Assistant web interface on another screen - is exactly the kind of
 * work that stops a feature from being used. So the list is fetched, grouped the way Home
 * Assistant groups it (by area, falling back to the domain where an instance has no areas),
 * and ticked off. The text field underneath stays: anything typed there by hand survives this
 * screen, including a label somebody wrote for an entity.
 */
public class OEPLHomeAssistantActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLHomeAssistantActivity.class);

    private GBDevice device;
    private TextView status;
    private EditText filter;
    private LinearLayout list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean stopped;

    /** Everything the instance has, as it came back. */
    private List<HomeAssistantClient.Listed> entities = new ArrayList<>();
    /** The ids that are ticked, in the order they were picked. */
    private final Set<String> chosen = new LinkedHashSet<>();
    /** The labels somebody typed for an entity, kept across a visit to this screen. */
    private final Map<String, String> labels = new LinkedHashMap<>();

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_home_assistant);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(getString(R.string.oepl_ha_pick_for, device.getAliasOrName()));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(R.string.pref_oepl_weather_screen_ha);
        }

        status = findViewById(R.id.oepl_ha_status);
        filter = findViewById(R.id.oepl_ha_filter);
        list = findViewById(R.id.oepl_ha_list);
        findViewById(R.id.oepl_ha_save).setOnClickListener(v -> save());
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int a, final int b,
                                          final int c) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int a, final int b,
                                      final int c) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                fill();
            }
        });

        readConfigured();
        load();
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    /** What this display is set to show already, so those come back ticked. */
    private void readConfigured() {
        for (final HomeAssistantClient.Spec spec
                : HomeAssistantClient.parseEntities(OEPLPrefs.homeAssistantEntities(prefs()))) {
            chosen.add(spec.entityId);
            if (spec.label != null) {
                labels.put(spec.entityId, spec.label);
            }
        }
    }

    private void load() {
        final SharedPreferences prefs = prefs();
        final String url = OEPLPrefs.homeAssistantUrl(prefs);
        final String token = OEPLPrefs.homeAssistantToken(prefs);
        if (url.isEmpty() || token.isEmpty()) {
            status.setText(R.string.oepl_ha_not_configured);
            return;
        }
        status.setText(R.string.oepl_ha_loading);
        final Thread thread = new Thread(() -> {
            List<HomeAssistantClient.Listed> found = new ArrayList<>();
            String error = null;
            try {
                found = HomeAssistantClient.browse(url, token);
            } catch (final Exception e) {
                LOG.warn("could not read the entity list", e);
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final List<HomeAssistantClient.Listed> result = found;
            final String message = error;
            handler.post(() -> {
                if (stopped || isFinishing()) {
                    return;
                }
                entities = result;
                if (message != null) {
                    status.setText(getString(R.string.oepl_ha_failed, message));
                    return;
                }
                filter.setVisibility(View.VISIBLE);
                fill();
            });
        }, "oepl-ha-browse");
        thread.setDaemon(true);
        thread.start();
    }

    /** Builds the list: a heading per area, a checkbox per entity, the chosen ones ticked. */
    private void fill() {
        list.removeAllViews();
        final String needle = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        String heading = null;
        int shown = 0;
        for (final HomeAssistantClient.Listed entity : entities) {
            if (!needle.isEmpty()
                    && !entity.name.toLowerCase(Locale.ROOT).contains(needle)
                    && !entity.entityId.toLowerCase(Locale.ROOT).contains(needle)
                    && !entity.area.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            final String group = entity.area.isEmpty() ? entity.domain() : entity.area;
            if (!group.equals(heading)) {
                heading = group;
                list.addView(headingView(group));
            }
            list.addView(entityView(entity));
            shown++;
        }
        status.setText(getString(R.string.oepl_ha_found, shown, chosen.size(),
                HomeAssistantClient.MAX_ENTITIES));
    }

    private TextView headingView(final String text) {
        final TextView view = new TextView(this);
        view.setText(text.toUpperCase(Locale.getDefault()));
        view.setPadding(0, dp(12), 0, dp(2));
        view.setTextSize(12f);
        view.setGravity(Gravity.START);
        return view;
    }

    private CheckBox entityView(final HomeAssistantClient.Listed entity) {
        final CheckBox box = new CheckBox(this);
        // what it is called, and what it is doing right now - which is what tells two sensors
        // of the same name in two rooms apart
        box.setText(getString(R.string.oepl_ha_entity, entity.name, entity.state));
        box.setChecked(chosen.contains(entity.entityId));
        box.setOnCheckedChangeListener((view, checked) -> {
            if (checked) {
                if (chosen.size() >= HomeAssistantClient.MAX_ENTITIES) {
                    box.setChecked(false);
                    GB.toast(getString(R.string.oepl_ha_too_many,
                            HomeAssistantClient.MAX_ENTITIES), 2500, GB.WARN);
                    return;
                }
                chosen.add(entity.entityId);
            } else {
                chosen.remove(entity.entityId);
            }
            status.setText(getString(R.string.oepl_ha_found, list.getChildCount(),
                    chosen.size(), HomeAssistantClient.MAX_ENTITIES));
        });
        return box;
    }

    /** Writes the ticked entities back as the list the card reads, labels and all. */
    private void save() {
        final StringBuilder lines = new StringBuilder();
        for (final String id : chosen) {
            lines.append(id);
            final String label = labels.get(id);
            if (label != null && !label.isEmpty()) {
                lines.append(" | ").append(label);
            }
            lines.append('\n');
        }
        prefs().edit().putString(OEPLPrefs.PREF_HA_ENTITIES, lines.toString()).apply();
        GBApplication.deviceService(device).onSendConfiguration(OEPLPrefs.PREF_HA_ENTITIES);
        GB.toast(getString(R.string.oepl_ha_saved, chosen.size()), 2500, GB.INFO);
        finish();
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
