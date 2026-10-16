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
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplReadings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssClient;

/**
 * Brings everything a card draws from up to date, and is meant to be called immediately before
 * the picture is rendered rather than at some earlier point in the cycle.
 * <p>
 * The refresh used to hang off the scan that precedes a connection, which left two ways for the
 * panel to end up with yesterday's numbers: a connection that came from anywhere else - the user
 * tapping connect, a notification, a changed setting - skipped it entirely, and a process that
 * Android had recycled in the meantime started with an empty cache. Putting the refresh in the
 * one place that every push goes through closes both.
 * <p>
 * Network goes on its own thread with a deadline. The radio link to the tag may already be up by
 * the time this runs, and holding that link open while a slow mobile connection times out would
 * cost far more coin cell than a slightly older forecast does.
 */
public final class OEPLContentSync {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLContentSync.class);

    /** One thread, so two displays updating at once share a single fetch. */
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "oepl-content-sync");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * How long the picture may be held back waiting for fresh content. Deliberately small: by
     * the time this runs the link to the tag is usually already up, and the tag hangs up by
     * itself after a few seconds of silence. The scheduler has normally fetched everything
     * before connecting, in which case this costs nothing at all and only serves as the
     * guarantee that no picture is ever drawn from content nobody checked.
     */
    public static final long RENDER_BUDGET_MILLIS = 2500L;
    /** Never let the panel show a forecast older than this when the network is there. */
    private static final long WEATHER_MAX_AGE_FLOOR_MILLIS = 2 * 60 * 1000L;
    private static final long WEATHER_MAX_AGE_CEILING_MILLIS = 10 * 60 * 1000L;

    private OEPLContentSync() {
    }

    /**
     * Refreshes the weather and, when the card needs it, the news feed.
     *
     * @param budgetMillis how long the caller is prepared to wait in total
     * @return true when the sources the card needs answered, so the card will not be blank
     */
    public static boolean freshen(final SharedPreferences prefs, final long budgetMillis) {
        return freshen(prefs, budgetMillis, null);
    }

    /**
     * @param address the display the content is for, needed by the sources that keep one copy
     *                per display rather than one for the whole app
     */
    public static boolean freshen(final SharedPreferences prefs, final long budgetMillis,
                                  final String address) {
        final long deadline = System.currentTimeMillis() + budgetMillis;
        boolean ok = true;
        if (OEPLPrefs.useOpenMeteo(prefs)) {
            ok = run(() -> OpenMeteoClient.refreshIfOlderThan(prefs, maxWeatherAge(prefs)),
                    deadline, "weather");
        }
        // the quartered card draws four other cards, and each of them wants its own source
        // refreshed - keying off the selected content alone would leave three quarters stale
        final List<String> contents = effectiveContents(prefs);
        if (contents.contains(OeplCardFactory.RSS)) {
            final String url = OEPLPrefs.rssUrl(prefs);
            ok &= run(() -> OeplRssClient.refreshIfStale(url), deadline, "news feed");
        }
        if (contents.contains(CardContentFactory.IMAGE_URL)) {
            final String url = OEPLPrefs.imageUrl(prefs);
            final Context context = GBApplication.getContext();
            ok &= run(() -> OEPLImageSource.refreshIfStale(context, address, url), deadline,
                    "picture");
        }
        if (needsHomeAssistant(prefs, contents)) {
            final String url = OEPLPrefs.homeAssistantUrl(prefs);
            final String token = OEPLPrefs.homeAssistantToken(prefs);
            final String entities = OEPLPrefs.homeAssistantEntities(prefs);
            ok &= run(() -> HomeAssistantClient.refreshIfStale(url, token, entities), deadline,
                    "home assistant");
        }
        return ok;
    }

    /**
     * The cards whose sources have to be fresh: the selected one, or the four quarters when
     * what is selected is the quartered card.
     */
    private static List<String> effectiveContents(final SharedPreferences prefs) {
        final String content = OEPLPrefs.contentType(prefs);
        if (!OeplCardFactory.isQuartered(content)) {
            return Collections.singletonList(content);
        }
        final List<String> slots = new ArrayList<>(OEPLPrefs.PREF_QUAD.length);
        for (int i = 0; i < OEPLPrefs.PREF_QUAD.length; i++) {
            final String slot = OEPLPrefs.quadSlot(prefs, i);
            if (!slot.isEmpty()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** Only when a card shows the house and there is something configured to ask. */
    private static boolean needsHomeAssistant(final SharedPreferences prefs,
                                              final List<String> contents) {
        if (!OEPLPrefs.readingGroups(prefs).contains(OeplReadings.GROUP_HOUSE)) {
            return false;
        }
        for (final String content : contents) {
            if (OeplCardFactory.needsHomeAssistant(content)) {
                return HomeAssistantClient.configured(OEPLPrefs.homeAssistantUrl(prefs),
                        OEPLPrefs.homeAssistantToken(prefs),
                        OEPLPrefs.homeAssistantEntities(prefs));
            }
        }
        return false;
    }

    /**
     * Throws every cached source away, so that the next {@link #freshen} really asks the
     * network again. The caches exist so the same question is not asked twice within minutes,
     * which is right for a timed update and wrong for a refresh somebody asked for by hand.
     *
     * @param address the display whose picture cache goes as well, may be null
     */
    public static void invalidateAll(@Nullable final String address) {
        OpenMeteoClient.invalidate();
        OeplRssClient.invalidate();
        HomeAssistantClient.invalidate();
        final Context context = GBApplication.getContext();
        if (context != null && address != null) {
            OEPLImageSource.invalidate(context, address);
        }
    }

    /**
     * How stale a forecast may be before the next picture waits for a new one. A display set to
     * refresh every quarter of an hour wants the weather of this quarter hour; one set to refresh
     * twice a day still should not draw something from three hours ago, so there is a ceiling as
     * well as a floor.
     */
    private static long maxWeatherAge(final SharedPreferences prefs) {
        final long interval = OEPLPrefs.updateIntervalMinutes(prefs) * 60_000L;
        return Math.max(WEATHER_MAX_AGE_FLOOR_MILLIS,
                Math.min(WEATHER_MAX_AGE_CEILING_MILLIS, interval / 2));
    }

    private static boolean run(final java.util.concurrent.Callable<Boolean> task,
                               final long deadline, final String what) {
        final long left = deadline - System.currentTimeMillis();
        if (left <= 0) {
            LOG.info("no time left to refresh the {}, drawing what we have", what);
            return false;
        }
        final Future<Boolean> future = NETWORK.submit(task);
        try {
            return Boolean.TRUE.equals(future.get(left, TimeUnit.MILLISECONDS));
        } catch (final TimeoutException e) {
            // let it finish in the background, the next picture will profit from it
            LOG.info("the {} did not arrive within {} ms, drawing what we have", what, left);
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (final Exception e) {
            LOG.warn("could not refresh the {}", what, e);
            return false;
        }
    }
}
