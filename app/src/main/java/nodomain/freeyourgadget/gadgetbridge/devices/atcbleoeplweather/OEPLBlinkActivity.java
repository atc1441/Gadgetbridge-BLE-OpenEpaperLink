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

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLActions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLBlinkPresets;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLProtocol;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Shaping the blink this display makes.
 * <p>
 * The tag plays a pattern on a tick of a tenth of a second and walks three groups in turn: each
 * group waits out its gap, makes one flash in its own colour, and hands over when its flashes
 * are used up. Then the three start again, as many rounds as were asked for. Three colours,
 * three counts, three gaps - that is the whole machine, and it is more than it sounds: two red
 * flashes, a blue one and a second of dark is a blink nobody will confuse with the display
 * beside it, which is the entire point of having an LED on a shelf of identical panels.
 * <p>
 * Every slider writes the twelve bytes straight back into the settings, so there is nothing to
 * save and nothing to lose by leaving. The button at the bottom sends it to the display, which
 * is the only way to find out what a pattern actually looks like.
 */
public class OEPLBlinkActivity extends AbstractGBActivity {

    /** Rounds the slider offers. Far short of the 255 the firmware reads as "never stop". */
    private static final int MOST_ROUNDS = 50;

    private GBDevice device;
    private TextView total;
    private TextView brightnessLabel;
    private TextView roundsLabel;
    private SeekBar brightness;
    private SeekBar rounds;
    private android.widget.Button save;

    private final int[] colors = new int[OEPLProtocol.GROUPS];
    private final int[] flashes = new int[OEPLProtocol.GROUPS];
    private final int[] gaps = new int[OEPLProtocol.GROUPS];
    private final Group[] groups = new Group[OEPLProtocol.GROUPS];

    /** True while the sliders are being filled in, so that doing so writes nothing back. */
    private boolean loading;

    /**
     * The saved pattern the sliders were last filled from, so that changing it and keeping it
     * again lands on the same name instead of making a second, nearly identical entry.
     */
    private String editing = "";

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oepl_blink);

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            finish();
            return;
        }
        setTitle(R.string.oepl_blink_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(device.getAliasOrName());
        }

        total = findViewById(R.id.oepl_blink_total);
        brightnessLabel = findViewById(R.id.oepl_blink_brightness_label);
        roundsLabel = findViewById(R.id.oepl_blink_rounds_label);
        brightness = findViewById(R.id.oepl_blink_brightness);
        rounds = findViewById(R.id.oepl_blink_rounds);
        rounds.setMax(MOST_ROUNDS - 1);
        watch(brightness);
        watch(rounds);

        final LinearLayout holder = findViewById(R.id.oepl_blink_groups);
        final LayoutInflater inflater = LayoutInflater.from(this);
        for (int group = 0; group < OEPLProtocol.GROUPS; group++) {
            final View view = inflater.inflate(R.layout.item_oepl_blink_group, holder, false);
            groups[group] = new Group(view, group);
            holder.addView(view);
        }

        findViewById(R.id.oepl_blink_saved).setOnClickListener(v -> askSaved());
        save = findViewById(R.id.oepl_blink_save);
        save.setOnClickListener(v -> askName());
        findViewById(R.id.oepl_blink_presets).setOnClickListener(v -> askPreset());
        findViewById(R.id.oepl_blink_reset).setOnClickListener(v -> reset());
        findViewById(R.id.oepl_blink_test).setOnClickListener(v -> test());

        read();
    }

    /** Fills the sliders from what is stored, without that counting as a change. */
    private void read() {
        final byte[] pattern = OEPLPrefs.ledPattern(prefs());
        loading = true;
        brightness.setProgress(((pattern[0] & 0xff) >> 4) & 0x0f);
        rounds.setProgress(Math.min(MOST_ROUNDS - 1, pattern[10] & 0xff));
        for (int group = 0; group < OEPLProtocol.GROUPS; group++) {
            final int shape = pattern[2 + group * 3] & 0xff;
            colors[group] = pattern[1 + group * 3] & 0xff;
            flashes[group] = shape & 0x0f;
            gaps[group] = (shape >> 4) & 0x0f;
            groups[group].read();
        }
        loading = false;
        describe();
    }

    /** Writes the twelve bytes back and tells the labels what they now say. */
    private void changed() {
        if (loading) {
            return;
        }
        OEPLPrefs.storeLedPattern(device.getAddress(), pattern());
        describe();
    }

    private byte[] pattern() {
        return OEPLProtocol.ledPattern(colors, flashes, gaps, rounds.getProgress() + 1,
                brightness.getProgress() + 1);
    }

    private void describe() {
        brightnessLabel.setText(getString(R.string.oepl_blink_brightness,
                brightness.getProgress() + 1, OEPLProtocol.FULL_BRIGHTNESS));
        roundsLabel.setText(getString(R.string.oepl_blink_rounds, rounds.getProgress() + 1));
        for (final Group group : groups) {
            group.describe();
        }
        save.setText(editing.isEmpty() ? getString(R.string.oepl_blink_save)
                : getString(R.string.oepl_blink_save_over, editing));
        total.setText(getString(R.string.oepl_blink_total,
                String.format(Locale.getDefault(), "%.1f",
                        OEPLProtocol.ledPatternSeconds(pattern()))));
    }

    /**
     * Sends it, which is the only honest preview. A phone screen can draw the colour but not
     * what a tenth of a second of it looks like across a room.
     */
    private void test() {
        final boolean now = OEPLActions.request(this, device, OEPLActions.BLINK);
        GB.toast(getString(now ? R.string.oepl_tools_sent : R.string.oepl_tools_queued),
                2500, GB.INFO);
    }

    /**
     * Keeping this pattern, so that the next display can be handed it instead of being shaped
     * from scratch. The name is what the list is read by afterwards, so it is worth one.
     */
    private void askName() {
        final EditText field = new EditText(this);
        field.setHint(R.string.oepl_blink_save_hint);
        field.setSingleLine(true);
        field.setText(editing);
        field.setSelection(editing.length());
        final FrameLayout room = new FrameLayout(this);
        final int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        room.setPadding(pad, pad / 2, pad, 0);
        room.addView(field);

        new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_blink_save)
                .setView(room)
                .setPositiveButton(R.string.oepl_blink_save_go, (d, w) -> {
                    final String name = OEPLBlinkPresets.save(
                            field.getText().toString(), pattern());
                    editing = name;
                    describe();
                    GB.toast(getString(R.string.oepl_blink_saved_as, name), 3000, GB.INFO);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * The saved patterns. Picking one shapes this display with it; what else can be done with
     * it is a second question rather than a long press, because a list of blinks is read with
     * a finger already on the screen and a long press there is an accident waiting to happen.
     */
    private void askSaved() {
        final List<OEPLBlinkPresets.Preset> saved = OEPLBlinkPresets.all();
        if (saved.isEmpty()) {
            GB.toast(getString(R.string.oepl_blink_none_saved), 4000, GB.INFO);
            return;
        }
        final String standard = OEPLBlinkPresets.defaultName();
        final String[] names = new String[saved.size()];
        for (int i = 0; i < saved.size(); i++) {
            final OEPLBlinkPresets.Preset preset = saved.get(i);
            final StringBuilder marks = new StringBuilder();
            if (preset.name.equals(standard)) {
                marks.append(getString(R.string.oepl_blink_mark_default));
            }
            if (preset.onCards) {
                if (marks.length() > 0) {
                    marks.append(", ");
                }
                marks.append(getString(R.string.oepl_blink_mark_on_cards));
            }
            names[i] = marks.length() == 0 ? preset.name
                    : getString(R.string.oepl_blink_marked, preset.name, marks.toString());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_blink_saved)
                .setItems(names, (dialog, which) -> askWhat(saved.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Use it here, make it the one every unshaped display blinks, or throw it away. */
    private void askWhat(final OEPLBlinkPresets.Preset preset) {
        final boolean isDefault = preset.name.equals(OEPLBlinkPresets.defaultName());
        final String[] what = {
                getString(R.string.oepl_blink_use_here),
                getString(preset.onCards
                        ? R.string.oepl_blink_off_cards : R.string.oepl_blink_on_cards),
                getString(isDefault ? R.string.oepl_blink_undefault : R.string.oepl_blink_default),
                getString(R.string.oepl_blink_delete),
        };
        new AlertDialog.Builder(this)
                .setTitle(preset.name)
                .setItems(what, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            // onto the display and into the sliders at once: a pattern that
                            // is being worked on is one that wants trying out, and the button
                            // at the bottom blinks what this display has
                            OEPLPrefs.storeLedPattern(device.getAddress(), preset.pattern);
                            editing = preset.name;
                            read();
                            GB.toast(getString(R.string.oepl_blink_taken, preset.name),
                                    2500, GB.INFO);
                            break;
                        case 1:
                            OEPLBlinkPresets.showOnCards(preset.name, !preset.onCards);
                            GB.toast(getString(preset.onCards
                                            ? R.string.oepl_blink_taken_off_cards
                                            : R.string.oepl_blink_put_on_cards, preset.name),
                                    3500, GB.INFO);
                            break;
                        case 2:
                            if (isDefault) {
                                OEPLBlinkPresets.clearDefault();
                            } else {
                                OEPLBlinkPresets.makeDefault(preset.name);
                            }
                            GB.toast(getString(isDefault
                                            ? R.string.oepl_blink_undefaulted
                                            : R.string.oepl_blink_defaulted, preset.name),
                                    3500, GB.INFO);
                            break;
                        default:
                            if (preset.name.equals(editing)) {
                                editing = "";
                                describe();
                            }
                            OEPLBlinkPresets.remove(preset.name);
                            GB.toast(getString(R.string.oepl_blink_deleted, preset.name),
                                    2500, GB.INFO);
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The plain blinks, as a way back to something recognisable after a few sliders. */
    private void askPreset() {
        final String[] names = getResources().getStringArray(R.array.oepl_weather_led_colors);
        new AlertDialog.Builder(this)
                .setTitle(R.string.oepl_blink_presets)
                .setItems(names, (dialog, which) -> {
                    final String[] values = getResources()
                            .getStringArray(R.array.oepl_weather_led_colors_values);
                    prefs().edit().putString(OEPLPrefs.PREF_LED_COLOR, values[which]).apply();
                    OEPLPrefs.forgetLedPattern(device.getAddress());
                    editing = "";
                    read();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reset() {
        OEPLPrefs.forgetLedPattern(device.getAddress());
        editing = "";
        read();
        GB.toast(getString(R.string.oepl_blink_was_reset), 2500, GB.INFO);
    }

    private android.content.SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
    }

    private void watch(final SeekBar bar) {
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                changed();
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
            }
        });
    }

    /** One of the three groups: a colour mixed from its three legs, a count and a gap. */
    private final class Group {
        private final int index;
        private final View swatch;
        private final TextView title;
        private final TextView summary;
        private final TextView redLabel;
        private final TextView greenLabel;
        private final TextView blueLabel;
        private final TextView flashesLabel;
        private final TextView gapLabel;
        private final SeekBar red;
        private final SeekBar green;
        private final SeekBar blue;
        private final SeekBar count;
        private final SeekBar gap;

        Group(final View view, final int index) {
            this.index = index;
            swatch = view.findViewById(R.id.oepl_blink_swatch);
            title = view.findViewById(R.id.oepl_blink_group_title);
            summary = view.findViewById(R.id.oepl_blink_group_summary);
            redLabel = view.findViewById(R.id.oepl_blink_red_label);
            greenLabel = view.findViewById(R.id.oepl_blink_green_label);
            blueLabel = view.findViewById(R.id.oepl_blink_blue_label);
            flashesLabel = view.findViewById(R.id.oepl_blink_flashes_label);
            gapLabel = view.findViewById(R.id.oepl_blink_gap_label);
            red = view.findViewById(R.id.oepl_blink_red);
            green = view.findViewById(R.id.oepl_blink_green);
            blue = view.findViewById(R.id.oepl_blink_blue);
            count = view.findViewById(R.id.oepl_blink_flashes);
            gap = view.findViewById(R.id.oepl_blink_gap);

            title.setText(getString(R.string.oepl_blink_group, index + 1));
            for (final SeekBar bar : new SeekBar[]{red, green, blue, count, gap}) {
                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                                  final boolean fromUser) {
                        take();
                        changed();
                    }

                    @Override
                    public void onStartTrackingTouch(final SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(final SeekBar seekBar) {
                    }
                });
            }
        }

        /** Picks up what the sliders now say. */
        private void take() {
            colors[index] = OEPLProtocol.ledColor(red.getProgress(), green.getProgress(),
                    blue.getProgress());
            flashes[index] = count.getProgress();
            gaps[index] = gap.getProgress();
        }

        private void read() {
            red.setProgress(OEPLProtocol.redOf(colors[index]));
            green.setProgress(OEPLProtocol.greenOf(colors[index]));
            blue.setProgress(OEPLProtocol.blueOf(colors[index]));
            count.setProgress(flashes[index]);
            gap.setProgress(gaps[index]);
        }

        private void describe() {
            redLabel.setText(getString(R.string.oepl_blink_red, red.getProgress(), 7));
            greenLabel.setText(getString(R.string.oepl_blink_green, green.getProgress(), 7));
            blueLabel.setText(getString(R.string.oepl_blink_blue, blue.getProgress(), 3));
            flashesLabel.setText(getString(R.string.oepl_blink_flashes, count.getProgress()));
            gapLabel.setText(getString(R.string.oepl_blink_gap,
                    String.format(Locale.getDefault(), "%.1f", gap.getProgress() / 10f)));
            swatch.setBackgroundColor(onScreen(colors[index]));
            // a group with no colour at all is the pause everybody needs for a pattern to read
            // as a pattern, so it says so rather than looking like a mistake
            summary.setText(colors[index] == 0
                    ? getString(R.string.oepl_blink_group_dark)
                    : getString(R.string.oepl_blink_group_lit));
        }

        /**
         * The colour as a screen can show it. The tag mixes three bits of red, three of green
         * and two of blue by how long it leaves each leg on during a flash, so this is an
         * impression of it rather than a match - a phone screen and a 2 mm LED behind frosted
         * plastic were never going to agree.
         */
        private int onScreen(final int color) {
            return Color.rgb(OEPLProtocol.redOf(color) * 255 / 7,
                    OEPLProtocol.greenOf(color) * 255 / 7,
                    OEPLProtocol.blueOf(color) * 255 / 3);
        }
    }
}
