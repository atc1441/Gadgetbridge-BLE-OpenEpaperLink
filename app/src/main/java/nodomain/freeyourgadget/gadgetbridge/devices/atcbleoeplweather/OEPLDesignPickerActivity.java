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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLCardPreview;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Picks what the display shows by showing it: every design is drawn with this display's own
 * data, at this display's own panel size and colours, and choosing one is a tap on the picture
 * rather than a guess from a list of names.
 * <p>
 * The cards are drawn on a background thread and handed to the list one at a time, so the screen
 * fills up as they come in rather than waiting for all of them - a QR code and a week grid take
 * noticeably longer than a clock.
 */
public class OEPLDesignPickerActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLDesignPickerActivity.class);

    private GBDevice device;
    private LinearLayout list;
    private View progress;
    private volatile boolean stopped;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_design_picker);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(R.string.pref_oepl_weather_pick_design_title);

        list = findViewById(R.id.oepl_design_list);
        progress = findViewById(R.id.oepl_design_progress);

        final Thread thread = new Thread(this::renderAll, "oepl-design-previews");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    /** Draws every design with this display's data and posts them to the list as they finish. */
    private void renderAll() {
        final SharedPreferences prefs = prefs();
        final String[] ids = getResources().getStringArray(R.array.oepl_weather_content_values);
        final String[] names = getResources().getStringArray(R.array.oepl_weather_content);
        final String current = OEPLPrefs.contentType(prefs);
        final Handler handler = new Handler(Looper.getMainLooper());

        final int[] panel = OEPLCardPreview.panelSize(prefs);

        for (int i = 0; i < ids.length && !stopped; i++) {
            final String id = ids[i];
            final String name = i < names.length ? names[i] : id;
            Bitmap preview = null;
            try {
                preview = OEPLCardPreview.render(this, device, prefs, id, panel[0], panel[1],
                        panel[2], getString(R.string.oepl_design_no_data));
            } catch (final Exception e) {
                LOG.warn("could not draw a preview of {}", id, e);
            }
            final Bitmap drawn = preview;
            handler.post(() -> add(id, name, drawn, id.equals(current)));
        }
        handler.post(() -> progress.setVisibility(View.GONE));
    }

    private void add(final String id, final String name, @Nullable final Bitmap preview,
                     final boolean current) {
        if (isFinishing() || list == null) {
            return;
        }
        final View row = LayoutInflater.from(this)
                .inflate(R.layout.oepl_design_row, list, false);
        final TextView label = row.findViewById(R.id.oepl_design_name);
        final TextView mark = row.findViewById(R.id.oepl_design_current);
        final ImageView image = row.findViewById(R.id.oepl_design_preview);

        label.setText(name);
        mark.setVisibility(current ? View.VISIBLE : View.GONE);
        if (preview != null) {
            image.setImageBitmap(preview);
        } else {
            image.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> choose(id, name));
        list.addView(row);
    }

    /** Stores the choice and asks for it to go onto the panel straight away. */
    private void choose(final String id, final String name) {
        prefs().edit().putString(OEPLPrefs.PREF_CONTENT, id).apply();
        // the picture on the panel is now the wrong one, so do not wait for the next timer
        OEPLUpdateScheduler.requestUpdateNow(this, device);
        GB.toast(getString(R.string.oepl_design_chosen, name), 2500, GB.INFO);
        finish();
    }
}
