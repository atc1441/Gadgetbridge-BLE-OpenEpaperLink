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

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads entity states out of a Home Assistant instance, so that a tag can show what the house
 * is doing and not only what the sky is doing.
 * <p>
 * This is the opposite direction from the usual Home Assistant setup, where an add-on hands
 * pictures to an OpenEPaperLink access point which then talks to the tags. Here the phone is
 * the access point, so the phone is also what fetches the states: one plain
 * <code>GET /api/states/&lt;entity&gt;</code> per configured entity, with a long-lived access
 * token in the Authorization header. That is the whole protocol - no websocket, no add-on, and
 * nothing to install on the Home Assistant side.
 * <p>
 * One request per entity rather than the full <code>/api/states</code> list: that list is every
 * entity an instance has, which is hundreds of kilobytes on a lived-in installation, and it is
 * fetched on a phone that is about to open a radio session. Asking for the six values a card
 * draws costs a fraction of it and says which single entity is misspelled.
 */
public final class HomeAssistantClient {
    private static final Logger LOG = LoggerFactory.getLogger(HomeAssistantClient.class);

    private static final int TIMEOUT_MILLIS = 8000;
    /** Re-asking more often than this gains nothing on a display that redraws every quarter. */
    private static final long MAX_AGE_MILLIS = 2 * 60 * 1000L;
    /** More entities than a panel can show, and more requests than a wake-up should cost. */
    public static final int MAX_ENTITIES = 12;

    /** One entity as the instance reported it, with the label the user gave it. */
    public static class Entity {
        public final String entityId;
        /** What the card calls it: the configured label, else Home Assistant's friendly name. */
        public final String label;
        /** The raw state string, e.g. "21.8", "on", "closed". */
        public final String state;
        /** The unit Home Assistant attached, or an empty string. */
        public final String unit;
        /** The device class, which is what decides the symbol. */
        public final String deviceClass;

        public Entity(final String entityId, final String label, final String state,
                      final String unit, final String deviceClass) {
            this.entityId = entityId;
            this.label = label;
            this.state = state;
            this.unit = unit == null ? "" : unit;
            this.deviceClass = deviceClass == null ? "" : deviceClass;
        }

        /** The state as a number, or NaN for the states that are words. */
        public float number() {
            try {
                return Float.parseFloat(state.trim().replace(',', '.'));
            } catch (final Exception ignored) {
                return Float.NaN;
            }
        }

        /** The value with its unit, which is what goes onto the panel for a measurement. */
        public String formatted() {
            final float number = number();
            if (Float.isNaN(number)) {
                return state;
            }
            // one decimal at most: a panel is read from across the room, and Home Assistant
            // happily reports 21.799999713897705
            final String digits = Math.abs(number) >= 100f || number == Math.round(number)
                    ? String.valueOf(Math.round(number))
                    : String.format(Locale.ROOT, "%.1f", number);
            return unit.isEmpty() ? digits : digits + " " + unit;
        }
    }

    /** One line of the configured entity list: an id and, optionally, a label for it. */
    public static class Spec {
        public final String entityId;
        @Nullable
        public final String label;

        Spec(final String entityId, @Nullable final String label) {
            this.entityId = entityId;
            this.label = label;
        }
    }

    private static List<Entity> cached = Collections.emptyList();
    private static long cachedAt;
    private static String cachedFor = "";
    private static String lastError = "";

    private HomeAssistantClient() {
    }

    public static synchronized List<Entity> cached() {
        return cached;
    }

    /** When the states in hand were fetched, 0 when none ever were. */
    public static synchronized long cachedAtMillis() {
        return cachedAt;
    }

    /** What went wrong the last time, for the overview screen; empty when nothing did. */
    public static synchronized String lastError() {
        return lastError;
    }

    public static synchronized void invalidate() {
        cached = Collections.emptyList();
        cachedAt = 0;
        cachedFor = "";
        lastError = "";
    }

    /**
     * Reads the configured entity list. One entity per line, with an optional label after a
     * vertical bar, which is what a card shows instead of Home Assistant's friendly name:
     * <pre>
     * sensor.wohnzimmer_temperatur | Wohnzimmer
     * binary_sensor.haustuer
     * </pre>
     */
    public static List<Spec> parseEntities(@Nullable final String raw) {
        final List<Spec> specs = new ArrayList<>();
        if (raw == null) {
            return specs;
        }
        for (final String line : raw.split("\r?\n")) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            final int bar = trimmed.indexOf('|');
            final String id = (bar < 0 ? trimmed : trimmed.substring(0, bar)).trim();
            final String label = bar < 0 ? null : trimmed.substring(bar + 1).trim();
            // an entity id is always "domain.object_id"; anything else is a typo, and a typo
            // must not cost a request
            if (!id.contains(".") || id.contains(" ")) {
                LOG.warn("ignoring {}, which is not an entity id", id);
                continue;
            }
            specs.add(new Spec(id, label == null || label.isEmpty() ? null : label));
            if (specs.size() >= MAX_ENTITIES) {
                break;
            }
        }
        return specs;
    }

    /** True once there is enough configured to try at all. */
    public static boolean configured(@Nullable final String baseUrl, @Nullable final String token,
                                     @Nullable final String entities) {
        return baseUrl != null && !baseUrl.trim().isEmpty()
                && token != null && !token.trim().isEmpty()
                && !parseEntities(entities).isEmpty();
    }

    /**
     * Fetches the configured entities when what is in hand aged out or the configuration
     * changed.
     *
     * @return true when states are available afterwards, fresh or not
     */
    public static boolean refreshIfStale(@Nullable final String baseUrl,
                                         @Nullable final String token,
                                         @Nullable final String entities) {
        if (!configured(baseUrl, token, entities)) {
            return false;
        }
        final String base = normalise(baseUrl);
        final List<Spec> specs = parseEntities(entities);
        final String signature = base + "\n" + entities;
        synchronized (HomeAssistantClient.class) {
            if (!cached.isEmpty() && signature.equals(cachedFor)
                    && System.currentTimeMillis() - cachedAt < MAX_AGE_MILLIS) {
                return true;
            }
        }

        final List<Entity> fetched = new ArrayList<>(specs.size());
        String error = "";
        for (final Spec spec : specs) {
            try {
                final JSONObject doc = new JSONObject(
                        get(base + "/api/states/" + URLEncoder.encode(spec.entityId, "UTF-8"),
                                token.trim()));
                final JSONObject attributes = doc.optJSONObject("attributes");
                final String friendly = attributes == null ? null
                        : attributes.optString("friendly_name", null);
                fetched.add(new Entity(spec.entityId,
                        spec.label != null ? spec.label
                                : (friendly != null ? friendly : shortName(spec.entityId)),
                        doc.optString("state", ""),
                        attributes == null ? "" : attributes.optString("unit_of_measurement", ""),
                        attributes == null ? "" : attributes.optString("device_class", "")));
            } catch (final Exception e) {
                // one missing entity must not cost the others: the panel shows what answered
                LOG.warn("could not read {} from Home Assistant", spec.entityId, e);
                if (error.isEmpty()) {
                    error = spec.entityId + ": " + e.getMessage();
                }
            }
        }

        synchronized (HomeAssistantClient.class) {
            if (!fetched.isEmpty()) {
                cached = fetched;
                cachedAt = System.currentTimeMillis();
                cachedFor = signature;
            }
            lastError = error;
            return !cached.isEmpty();
        }
    }

    /** "sensor.wohnzimmer_temperatur" with nothing to go by becomes "Wohnzimmer temperatur". */
    private static String shortName(final String entityId) {
        final String tail = entityId.substring(entityId.indexOf('.') + 1).replace('_', ' ');
        return tail.isEmpty() ? entityId
                : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }

    /** Accepts what a user types: with or without a scheme, with or without a trailing slash. */
    static String normalise(final String baseUrl) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            // a Home Assistant on the local network is reached over http, which is also what
            // its own "Internal URL" field usually holds
            base = "http://" + base;
        }
        return base;
    }

    private static String get(final String url, final String token) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            final int status = connection.getResponseCode();
            if (status == 401) {
                throw new IllegalStateException("token rejected");
            }
            if (status == 404) {
                throw new IllegalStateException("no such entity");
            }
            if (status != 200) {
                throw new IllegalStateException("answered " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) > 0) {
                    out.write(buf, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
