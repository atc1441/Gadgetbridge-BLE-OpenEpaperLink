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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.CardContentFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLCardPreview;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLImageSource;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Picks the picture a display shows and says what it will look like on it.
 * <p>
 * A three colour panel cannot show a photograph the way a screen does, and no description of
 * dithering is as useful as seeing it: the preview here is the real card, drawn at the panel's
 * size and through the panel's palette, and it is redrawn whenever one of the settings changes.
 */
public class OEPLImageActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLImageActivity.class);

    private GBDevice device;
    private ImageView preview;
    private TextView note;
    private RadioGroup dither;
    private RadioGroup fit;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean stopped;

    private final ActivityResultLauncher<String[]> pick = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::chosen);

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_image);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(R.string.pref_oepl_weather_image_pick_title);

        preview = findViewById(R.id.oepl_image_preview);
        note = findViewById(R.id.oepl_image_note);
        dither = findViewById(R.id.oepl_image_dither);
        fit = findViewById(R.id.oepl_image_fit);

        findViewById(R.id.oepl_image_choose).setOnClickListener(v -> {
            try {
                pick.launch(new String[]{"image/*"});
            } catch (final Exception e) {
                LOG.warn("no picture chooser on this phone", e);
                GB.toast(getString(R.string.oepl_image_no_picker), 3000, GB.ERROR);
            }
        });
        findViewById(R.id.oepl_image_use).setOnClickListener(v -> apply());

        restore();
        dither.setOnCheckedChangeListener((group, id) -> {
            store();
            refresh();
        });
        fit.setOnCheckedChangeListener((group, id) -> {
            store();
            refresh();
        });
        refresh();
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    /** Keeps the right to read the picture after the chooser is gone. */
    private void chosen(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (final Exception e) {
            // some providers do not offer a persistable grant; the picture then works until the
            // phone is restarted, which is better than refusing it outright
            LOG.info("no lasting permission for {}", uri, e);
        }
        prefs().edit().putString(OEPLPrefs.PREF_IMAGE_URI, uri.toString()).apply();
        refresh();
    }

    private void restore() {
        final SharedPreferences prefs = prefs();
        switch (prefs.getString(OEPLPrefs.PREF_IMAGE_DITHER, "diffusion")) {
            case "none":
                dither.check(R.id.oepl_image_dither_none);
                break;
            case "ordered":
                dither.check(R.id.oepl_image_dither_ordered);
                break;
            default:
                dither.check(R.id.oepl_image_dither_diffusion);
                break;
        }
        switch (prefs.getString(OEPLPrefs.PREF_IMAGE_FIT, "contain")) {
            case "cover":
                fit.check(R.id.oepl_image_fit_cover);
                break;
            case "stretch":
                fit.check(R.id.oepl_image_fit_stretch);
                break;
            default:
                fit.check(R.id.oepl_image_fit_contain);
                break;
        }
    }

    private void store() {
        final String ditherValue;
        final int checked = dither.getCheckedRadioButtonId();
        if (checked == R.id.oepl_image_dither_none) {
            ditherValue = "none";
        } else if (checked == R.id.oepl_image_dither_ordered) {
            ditherValue = "ordered";
        } else {
            ditherValue = "diffusion";
        }
        final String fitValue;
        final int checkedFit = fit.getCheckedRadioButtonId();
        if (checkedFit == R.id.oepl_image_fit_cover) {
            fitValue = "cover";
        } else if (checkedFit == R.id.oepl_image_fit_stretch) {
            fitValue = "stretch";
        } else {
            fitValue = "contain";
        }
        prefs().edit()
                .putString(OEPLPrefs.PREF_IMAGE_DITHER, ditherValue)
                .putString(OEPLPrefs.PREF_IMAGE_FIT, fitValue)
                .apply();
    }

    /** Draws the card the display would get, on a thread, and shows it. */
    private void refresh() {
        final SharedPreferences prefs = prefs();
        final String content = OEPLPrefs.contentType(prefs);
        final String id = CardContentFactory.IMAGE_URL.equals(content)
                ? CardContentFactory.IMAGE_URL : CardContentFactory.IMAGE;
        note.setText(R.string.oepl_image_drawing);

        final Thread thread = new Thread(() -> {
            Bitmap card = null;
            try {
                if (CardContentFactory.IMAGE_URL.equals(id)) {
                    // the preview is worth a fetch; without it the screen would stay empty
                    // until the next scheduled update had run
                    OEPLImageSource.refreshIfStale(this, device.getAddress(),
                            OEPLPrefs.imageUrl(prefs));
                }
                final int[] panel = OEPLCardPreview.panelSize(prefs);
                card = OEPLCardPreview.render(this, device, prefs, id, panel[0], panel[1],
                        panel[2], getString(R.string.oepl_image_none));
            } catch (final Exception e) {
                LOG.warn("could not draw the picture preview", e);
            }
            final Bitmap drawn = card;
            handler.post(() -> show(drawn));
        }, "oepl-image-preview");
        thread.setDaemon(true);
        thread.start();
    }

    private void show(@Nullable final Bitmap card) {
        if (stopped || isFinishing()) {
            return;
        }
        if (card == null) {
            preview.setVisibility(View.GONE);
            note.setText(R.string.oepl_image_none);
            return;
        }
        preview.setVisibility(View.VISIBLE);
        preview.setImageBitmap(card);
        final String source = CardContentFactory.IMAGE_URL.equals(
                OEPLPrefs.contentType(prefs()))
                ? OEPLPrefs.imageUrl(prefs()) : OEPLPrefs.imageUri(prefs());
        note.setText(source.isEmpty() ? getString(R.string.oepl_image_none)
                : getString(R.string.oepl_image_note, card.getWidth() + "x" + card.getHeight()));
    }

    /** Makes the picture the thing this display shows, and sends it straight away. */
    private void apply() {
        store();
        final String content = OEPLPrefs.contentType(prefs());
        if (!CardContentFactory.IMAGE.equals(content)
                && !CardContentFactory.IMAGE_URL.equals(content)) {
            prefs().edit().putString(OEPLPrefs.PREF_CONTENT, CardContentFactory.IMAGE).apply();
        }
        OEPLUpdateScheduler.requestUpdateNow(this, device);
        GB.toast(getString(R.string.oepl_image_applied), 2500, GB.INFO);
        finish();
    }
}
