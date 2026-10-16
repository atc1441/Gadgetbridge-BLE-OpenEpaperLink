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
    /** Set once the picture or one of its two options changed, cleared when the display is told. */
    private boolean changed;

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
        // Whose picture this is, in the title: every display keeps its own, and a screen
        // that looks the same for all of them is a screen that invites picking a picture for
        // the display in the other room.
        setTitle(getString(R.string.oepl_image_for, device.getAliasOrName()));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(R.string.pref_oepl_weather_image_pick_title);
        }

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
        installGestures();

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

    /**
     * Sends the new picture once this screen is left, rather than on every radio button: the
     * preview above already shows what was chosen, and the display is better handed the finished
     * answer than each step on the way to it.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (changed) {
            changed = false;
            GBApplication.deviceService(device).onSendConfiguration(OEPLPrefs.PREF_IMAGE_URI);
        }
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    // ------------------------------------------------------------------ framing the picture

    /** True while a finger is on the preview, so a redraw does not fight the gesture. */
    private boolean gesturing;
    /** Set while a preview is being drawn, so the gestures do not queue up renders. */
    private volatile boolean drawing;
    private float lastX;
    private float lastY;
    private android.view.ScaleGestureDetector scaleDetector;

    /**
     * Dragging and pinching on the preview choose the part of the picture the panel shows.
     * <p>
     * The preview is the card itself, so this is direct: what is under the finger is what the
     * display will show. A fit that scales the whole picture into the panel is right for a
     * poster and wrong for a photograph of a person, where the point is the face rather than
     * the edges - and cropping it beforehand in another app for a panel whose proportions
     * nobody remembers is not a reasonable thing to ask.
     */
    private void installGestures() {
        scaleDetector = new android.view.ScaleGestureDetector(this,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(final android.view.ScaleGestureDetector detector) {
                        final SharedPreferences prefs = prefs();
                        final float zoom = clampZoom(OEPLPrefs.imageZoom(prefs)
                                * detector.getScaleFactor());
                        prefs.edit().putFloat(OEPLPrefs.PREF_IMAGE_ZOOM, zoom).apply();
                        changed = true;
                        redrawSoon();
                        return true;
                    }
                });

        final android.view.GestureDetector taps = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(final android.view.MotionEvent event) {
                        // back to the whole picture, which is where everybody starts over from
                        prefs().edit()
                                .putFloat(OEPLPrefs.PREF_IMAGE_ZOOM, 1f)
                                .putFloat(OEPLPrefs.PREF_IMAGE_PAN_X, 0f)
                                .putFloat(OEPLPrefs.PREF_IMAGE_PAN_Y, 0f)
                                .apply();
                        changed = true;
                        refresh();
                        return true;
                    }
                });

        // The screen scrolls, and a finger dragged across a picture inside a scrolling
        // screen scrolls it - the gesture never reaches the picture at all. So for as long as
        // a finger is on the preview, the scrolling parents are asked to keep their hands off
        // it, and asked again on every move because a parent may start intercepting mid
        // gesture. Released again the moment the finger leaves, so the screen scrolls as
        // usual everywhere else.
        preview.setClickable(true);
        preview.setOnTouchListener((view, event) -> {
            scaleDetector.onTouchEvent(event);
            taps.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    holdTheScroll(view, true);
                    gesturing = true;
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    holdTheScroll(view, true);
                    if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                        pan(event.getX() - lastX, event.getY() - lastY, view.getWidth(),
                                view.getHeight());
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    holdTheScroll(view, false);
                    gesturing = false;
                    refresh();
                    view.performClick();
                    return true;
                default:
                    return true;
            }
        });
    }

    /** Keeps the scrolling parents out of a gesture that belongs to the picture. */
    private void holdTheScroll(final View view, final boolean hold) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(hold);
            parent = parent.getParent();
        }
    }

    /**
     * Moves the cut-out by what the finger moved. Dragging to the right brings what is left of
     * the frame into it, which is what every map and every photo viewer does.
     */
    private void pan(final float dx, final float dy, final int width, final int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        final SharedPreferences prefs = prefs();
        final float zoom = OEPLPrefs.imageZoom(prefs);
        final float x = clampPan(OEPLPrefs.imagePanX(prefs) - 2f * dx / width / zoom);
        final float y = clampPan(OEPLPrefs.imagePanY(prefs) - 2f * dy / height / zoom);
        prefs.edit().putFloat(OEPLPrefs.PREF_IMAGE_PAN_X, x)
                .putFloat(OEPLPrefs.PREF_IMAGE_PAN_Y, y).apply();
        changed = true;
        redrawSoon();
    }

    private float clampZoom(final float zoom) {
        return Math.max(1f, Math.min(8f, zoom));
    }

    private float clampPan(final float pan) {
        return Math.max(-1f, Math.min(1f, pan));
    }

    /**
     * Redraws while a finger is still moving, but never more than one drawing at a time: the
     * card goes through the dithering the panel will see, which costs enough that queueing
     * them up would leave the preview lagging seconds behind the hand.
     */
    private void redrawSoon() {
        if (drawing) {
            return;
        }
        refresh();
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
        changed = true;
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
        changed = true;
    }

    /** Draws the card the display would get, on a thread, and shows it. */
    private void refresh() {
        final SharedPreferences prefs = prefs();
        final String content = OEPLPrefs.contentType(prefs);
        final String id = CardContentFactory.IMAGE_URL.equals(content)
                ? CardContentFactory.IMAGE_URL : CardContentFactory.IMAGE;
        note.setText(R.string.oepl_image_drawing);

        drawing = true;
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
            drawing = false;
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
        if (source.isEmpty()) {
            // said plainly: the preview above is the card this display would draw, which looks
            // the same on every display that has no picture of its own
            note.setText(R.string.oepl_image_none_here);
            return;
        }
        // ...and when there is one, which one: two displays showing a picture are told apart
        // by its name, not by a thumbnail of the same size
        final float zoom = OEPLPrefs.imageZoom(prefs());
        final String framing = zoom > 1.001f
                ? getString(R.string.oepl_image_zoom, zoom)
                : getString(R.string.oepl_image_gesture);
        note.setText(getString(R.string.oepl_image_file, name(source), framing));
    }

    /** The last part of a URI or URL, which is the part somebody recognises. */
    private String name(final String source) {
        final String withoutQuery = source.contains("?")
                ? source.substring(0, source.indexOf('?')) : source;
        final int slash = withoutQuery.lastIndexOf('/');
        final String tail = slash >= 0 && slash < withoutQuery.length() - 1
                ? withoutQuery.substring(slash + 1) : withoutQuery;
        try {
            return java.net.URLDecoder.decode(tail, "UTF-8");
        } catch (final Exception ignored) {
            return tail;
        }
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
