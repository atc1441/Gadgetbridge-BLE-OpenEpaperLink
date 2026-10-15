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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;

/**
 * What the phone is doing right now, kept for the display to draw.
 * <p>
 * Gadgetbridge hands notifications and music state to a device as they happen rather than
 * letting it ask, and this display is asleep almost all of the time, so the interesting ones are
 * remembered here until the next connection.
 */
public final class PhoneStateStore {
    private static final int MAX_NOTIFICATIONS = 12;

    public static class Note {
        public final long when;
        public final String source;
        public final String title;
        public final String body;

        Note(final long when, final String source, final String title, final String body) {
            this.when = when;
            this.source = source;
            this.title = title;
            this.body = body;
        }
    }

    private static final Deque<Note> NOTES = new ArrayDeque<>(MAX_NOTIFICATIONS);
    @Nullable
    private static MusicSpec music;
    private static int musicState = MusicStateSpec.STATE_UNKNOWN;

    private PhoneStateStore() {
    }

    public static synchronized void remember(final NotificationSpec spec) {
        final String title = firstNonEmpty(spec.getTitle(), spec.getSubject(), spec.getSender());
        final String body = firstNonEmpty(spec.getBody(), spec.getSubject());
        if (title == null && body == null) {
            return;
        }
        final String source = firstNonEmpty(spec.getSourceName(), spec.getSourceAppId());
        // one entry per app and title, so a chat that keeps updating does not fill the card
        NOTES.removeIf(n -> equal(n.source, source) && equal(n.title, title));
        NOTES.addFirst(new Note(spec.getWhen(), source, title, body));
        while (NOTES.size() > MAX_NOTIFICATIONS) {
            NOTES.removeLast();
        }
    }

    public static synchronized void forget(final int id) {
        // Gadgetbridge only passes the id, which we do not keep; the list is capped anyway.
    }

    public static synchronized List<Note> notifications() {
        return new ArrayList<>(NOTES);
    }

    public static synchronized void setMusic(final MusicSpec spec) {
        music = spec;
    }

    public static synchronized void setMusicState(final MusicStateSpec state) {
        musicState = state.getState();
    }

    @Nullable
    public static synchronized MusicSpec music() {
        return music;
    }

    public static synchronized boolean musicPlaying() {
        return musicState == MusicStateSpec.STATE_PLAYING;
    }

    /** The phone's own battery level, or -1 when it cannot be read. */
    public static int phoneBattery(final Context context) {
        try {
            final Intent status = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (status == null) {
                return -1;
            }
            final int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (level < 0 || scale <= 0) ? -1 : level * 100 / scale;
        } catch (final Exception e) {
            return -1;
        }
    }

    @Nullable
    private static String firstNonEmpty(final String... candidates) {
        for (final String c : candidates) {
            if (c != null && !c.trim().isEmpty()) {
                return c.trim();
            }
        }
        return null;
    }

    private static boolean equal(@Nullable final String a, @Nullable final String b) {
        return a == null ? b == null : a.equals(b);
    }
}
