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

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

/**
 * Blink patterns worth keeping, under the names they were given.
 * <p>
 * Shaping one is a dozen slider pulls, and the result is the thing that tells a shelf of
 * identical panels apart - which means it is wanted again on the next display, and on the one
 * after that. So a pattern is saved once, by name, and handed to any display from a list.
 * <p>
 * The list is kept globally rather than per display, because a pattern that only existed on the
 * display it was made for would have to be made again for every other one, which is exactly the
 * work this is here to avoid. One of them can be the default: a display that has never been
 * shaped by hand blinks that instead of the plain three flashes, so a whole shelf can be given
 * one look without visiting each panel.
 */
public final class OEPLBlinkPresets {

    /**
     * The saved patterns, one per line: a name, a tab, the twelve bytes as hex, and - where
     * the pattern is offered on the device cards - a tab and a one behind them. That last
     * field came after the first patterns were saved, so a line without it reads as a pattern
     * nobody has put on the cards rather than as a broken one.
     */
    static final String SAVED = "oepl_weather_led_presets";
    /** The name of the one that displays fall back on, or absent for the plain blink. */
    static final String DEFAULT_NAME = "oepl_weather_led_preset_default";

    /** Enough to name every blink on a shelf; past this it is a description, not a name. */
    private static final int LONGEST_NAME = 40;
    /** A list rather than an archive: a hundred saved blinks is a menu nobody can read. */
    private static final int MOST = 50;

    public static final class Preset {
        public final String name;
        public final byte[] pattern;
        /** Whether this one is offered on every display's card in the main list. */
        public final boolean onCards;

        Preset(final String name, final byte[] pattern, final boolean onCards) {
            this.name = name;
            this.pattern = pattern;
            this.onCards = onCards;
        }
    }

    private OEPLBlinkPresets() {
    }

    private static SharedPreferences store() {
        return GBApplication.getPrefs().getPreferences();
    }

    /** Everything that was saved, oldest first, skipping anything no longer readable. */
    @NonNull
    public static List<Preset> all() {
        final List<Preset> presets = new ArrayList<>();
        final String stored = store().getString(SAVED, "");
        if (stored == null || stored.isEmpty()) {
            return presets;
        }
        for (final String line : stored.split("\n")) {
            final String[] fields = line.split("\t");
            if (fields.length < 2 || fields[0].isEmpty()) {
                continue;
            }
            final byte[] pattern = OEPLProtocol.ledPatternFromHex(fields[1]);
            if (pattern != null) {
                presets.add(new Preset(fields[0], pattern,
                        fields.length > 2 && "1".equals(fields[2])));
            }
        }
        return presets;
    }

    /**
     * The ones offered on the device cards.
     * <p>
     * A blink is how one panel out of a shelf of identical ones is pointed at, and the shelf is
     * in the main list - so the few patterns worth reaching from there are marked, and every
     * display's card carries a button for each of them. Marking all of them would turn the
     * list into a wall of buttons, which is why it is a choice rather than the rule.
     */
    @NonNull
    public static List<Preset> onCards() {
        final List<Preset> shown = new ArrayList<>();
        for (final Preset preset : all()) {
            if (preset.onCards) {
                shown.add(preset);
            }
        }
        return shown;
    }

    /** Puts this one on the device cards, or takes it off them again. */
    public static void showOnCards(final String name, final boolean show) {
        final StringBuilder out = new StringBuilder();
        for (final Preset preset : all()) {
            append(out, preset.name, OEPLProtocol.ledPatternToHex(preset.pattern),
                    preset.name.equals(name) ? show : preset.onCards);
        }
        store().edit().putString(SAVED, out.toString()).apply();
    }

    /** The pattern saved under this name, or null. */
    @Nullable
    public static byte[] byName(final String name) {
        for (final Preset preset : all()) {
            if (preset.name.equals(name)) {
                return preset.pattern;
            }
        }
        return null;
    }

    /**
     * Saves a pattern under a name, replacing one of the same name.
     *
     * @return the name it was saved under, which is not quite what was asked for when the name
     *         was empty or carried the characters the list is held together with
     */
    public static String save(final String asked, final byte[] pattern) {
        final String name = tidy(asked);
        final List<Preset> presets = all();
        final StringBuilder out = new StringBuilder();
        int kept = 0;
        boolean wasOnCards = false;
        for (final Preset preset : presets) {
            if (preset.name.equals(name)) {
                wasOnCards = preset.onCards;   // replaced, and it keeps its place on the cards
                continue;
            }
            if (kept >= MOST - 1) {
                continue;                      // the oldest, making room for this one
            }
            append(out, preset.name, OEPLProtocol.ledPatternToHex(preset.pattern),
                    preset.onCards);
            kept++;
        }
        append(out, name, OEPLProtocol.ledPatternToHex(pattern), wasOnCards);
        store().edit().putString(SAVED, out.toString()).apply();
        return name;
    }

    public static void remove(final String name) {
        final StringBuilder out = new StringBuilder();
        for (final Preset preset : all()) {
            if (!preset.name.equals(name)) {
                append(out, preset.name, OEPLProtocol.ledPatternToHex(preset.pattern),
                        preset.onCards);
            }
        }
        final SharedPreferences.Editor editor = store().edit().putString(SAVED, out.toString());
        if (name.equals(defaultName())) {
            editor.remove(DEFAULT_NAME);
        }
        editor.apply();
    }

    /** The name of the pattern displays fall back on, or an empty string for none. */
    @NonNull
    public static String defaultName() {
        final String name = store().getString(DEFAULT_NAME, "");
        return name == null ? "" : name;
    }

    /** Makes this one the fallback, or clears it when the name is not a saved one. */
    public static void makeDefault(final String name) {
        if (byName(name) == null) {
            store().edit().remove(DEFAULT_NAME).apply();
            return;
        }
        store().edit().putString(DEFAULT_NAME, name).apply();
    }

    public static void clearDefault() {
        store().edit().remove(DEFAULT_NAME).apply();
    }

    /** The fallback pattern itself, or null while no saved one has been made the default. */
    @Nullable
    public static byte[] defaultPattern() {
        final String name = defaultName();
        return name.isEmpty() ? null : byName(name);
    }

    private static void append(final StringBuilder out, final String name, final String hex,
                               final boolean onCards) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(name).append('\t').append(hex).append('\t').append(onCards ? '1' : '0');
    }

    /**
     * A name the list can hold. The tab and the newline are what separates one saved pattern
     * from the next, so a name carrying either would swallow the one behind it.
     */
    private static String tidy(final String asked) {
        final String name = asked == null ? "" : asked.replace('\t', ' ').replace('\n', ' ')
                .trim();
        if (name.isEmpty()) {
            return "?";
        }
        return name.length() > LONGEST_NAME ? name.substring(0, LONGEST_NAME).trim() : name;
    }
}
