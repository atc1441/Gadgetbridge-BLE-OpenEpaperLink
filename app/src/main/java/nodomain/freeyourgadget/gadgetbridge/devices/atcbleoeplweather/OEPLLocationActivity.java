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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLUpdateScheduler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OpenMeteoClient;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Picking which of the places with that name is the one meant.
 * <p>
 * A typed place name is looked up and the first answer taken, which is fine until the name is
 * one of the many that repeat: there are Springfields in a dozen states, a Frankfurt on two
 * rivers and a Berlin in several countries. The lookup then picks one by population and says
 * nothing about it, and the display shows a forecast for somewhere else entirely - plausibly
 * enough that it can go unnoticed for days.
 * <p>
 * So every answer is offered, each with the region and country that tell it apart, and the one
 * that is picked is stored as its coordinates rather than as its name. Coordinates cannot be
 * looked up wrongly a second time. The name it was picked under is kept beside them, because
 * that is what the card writes above the forecast.
 */
public class OEPLLocationActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLLocationActivity.class);

    /** Enough to show every namesake worth distinguishing without becoming a list to scroll. */
    private static final int LIMIT = 20;

    /**
     * How long typing has to stop before the name is looked up.
     * <p>
     * The list follows the typing rather than waiting for a button, which is the only way the
     * namesakes are seen at the moment they matter. But a request per keystroke would be eight
     * requests for one town and seven of them thrown away, so the last one in a burst wins.
     * Short enough not to feel like waiting, long enough that a word is typed before it goes.
     */
    private static final long TYPING_PAUSE_MILLIS = 250L;

    /** Below this almost every place on earth matches, which is a list that says nothing. */
    private static final int ENOUGH_TO_LOOK_UP = 2;

    private GBDevice device;
    private EditText query;
    private final Runnable searchSoon = this::search;
    private TextView status;
    private LinearLayout list;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean stopped;
    /** Counts searches so that a slow answer to an old query cannot overwrite a newer one. */
    private int round;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_location);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(R.string.oepl_location_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(device.getAliasOrName());
        }

        query = findViewById(R.id.oepl_location_query);
        status = findViewById(R.id.oepl_location_status);
        list = findViewById(R.id.oepl_location_list);
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                handler.removeCallbacks(searchSoon);
                search();
                return true;
            }
            return false;
        });
        query.addTextChangedListener(new TextWatcher() {
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
                typed();
            }
        });

        // what is configured is the obvious thing to search for, so it is filled in - unless it
        // is already a pair of coordinates, which is nothing to look up
        final String configured = OEPLPrefs.weatherLocation(
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
        final String name = OEPLPrefs.weatherPlaceName(
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
        final String start = !name.isEmpty() ? name : (looksLikeCoordinates(configured)
                ? "" : configured);
        query.setText(start);
        query.setSelection(start.length());
        showCurrent(configured, name);
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * A keystroke. The lookup is put off until the typing stops, and an answer to what was
     * typed before this is no longer wanted: {@link #round} is bumped, so a slow reply to
     * "Ber" cannot land on top of the list for "Berlin".
     */
    private void typed() {
        handler.removeCallbacks(searchSoon);
        round++;
        if (query.getText().toString().trim().length() < ENOUGH_TO_LOOK_UP) {
            list.removeAllViews();
            status.setText(R.string.oepl_location_hint);
            return;
        }
        handler.postDelayed(searchSoon, TYPING_PAUSE_MILLIS);
    }

    /** What this display is set to now, so it is clear what is being replaced. */
    private void showCurrent(final String configured, final String name) {
        if (configured.isEmpty()) {
            status.setText(R.string.oepl_location_hint);
            return;
        }
        status.setText(getString(R.string.oepl_location_current,
                name.isEmpty() ? configured : name + " (" + configured + ")"));
    }

    private static boolean looksLikeCoordinates(final String value) {
        final String[] parts = value.split(",");
        if (parts.length != 2) {
            return false;
        }
        try {
            Double.parseDouble(parts[0].trim());
            Double.parseDouble(parts[1].trim());
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    private void search() {
        final String asked = query.getText().toString().trim();
        if (asked.isEmpty()) {
            return;
        }
        list.removeAllViews();
        status.setText(R.string.oepl_location_searching);
        final int mine = ++round;
        // the geocoder answers in the language it is asked in, so the list reads the way the
        // rest of the app does rather than always in English
        final String language = Locale.getDefault().getLanguage();
        final Thread thread = new Thread(() -> {
            List<OpenMeteoClient.Match> found = new ArrayList<>();
            String error = null;
            try {
                found = OpenMeteoClient.search(asked, LIMIT, language);
            } catch (final Exception e) {
                LOG.warn("could not look up {}", asked, e);
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final List<OpenMeteoClient.Match> result = found;
            final String message = error;
            handler.post(() -> {
                if (stopped || isFinishing() || mine != round) {
                    return;
                }
                if (message != null) {
                    status.setText(getString(R.string.oepl_location_failed, message));
                    return;
                }
                fill(asked, result);
            });
        }, "oepl-geocode");
        thread.setDaemon(true);
        thread.start();
    }

    private void fill(final String asked, final List<OpenMeteoClient.Match> matches) {
        list.removeAllViews();
        if (matches.isEmpty()) {
            status.setText(getString(R.string.oepl_location_none, asked));
            return;
        }
        status.setText(getResources().getQuantityString(R.plurals.oepl_location_found,
                matches.size(), matches.size()));
        for (final OpenMeteoClient.Match match : matches) {
            list.addView(row(match));
        }
    }

    /** One place: what it is called, where it is, and the coordinates that will be stored. */
    private View row(final OpenMeteoClient.Match match) {
        final View row = getLayoutInflater().inflate(R.layout.item_oepl_location, list, false);
        ((TextView) row.findViewById(R.id.oepl_location_name)).setText(match.name);
        final TextView where = row.findViewById(R.id.oepl_location_where);
        final StringBuilder detail = new StringBuilder(match.where());
        if (match.population > 0) {
            if (detail.length() > 0) {
                detail.append(" · ");
            }
            detail.append(getString(R.string.oepl_location_people, match.population));
        }
        if (detail.length() > 0) {
            detail.append(" · ");
        }
        detail.append(match.coordinates());
        where.setText(detail.toString());
        row.setOnClickListener(v -> choose(match));
        return row;
    }

    private void choose(final OpenMeteoClient.Match match) {
        OEPLPrefs.rememberPlace(device.getAddress(), match.coordinates(), match.name);
        GB.toast(getString(R.string.oepl_location_chosen, match.name, match.where()),
                3000, GB.INFO);
        // the forecast for the old place is no longer the one to draw, so the card is made
        // again rather than waiting out the interval with the wrong town on the panel
        OEPLUpdateScheduler.requestUpdateNow(this, device);
        finish();
    }
}
