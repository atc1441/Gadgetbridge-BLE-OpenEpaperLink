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

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplWeather;

/**
 * Weather straight from Open-Meteo, the same service and endpoints the OpenEPaperLink access
 * point uses - so the display needs neither that access point nor a weather app on the phone.
 * <p>
 * Open-Meteo needs no account and no API key. It answers in WMO weather codes, which are
 * translated to the OpenWeatherMap codes the rest of Gadgetbridge speaks.
 */
public final class OpenMeteoClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenMeteoClient.class);

    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int TIMEOUT_MILLIS = 12000;
    /** Re-fetching more often than this gains nothing on a display that redraws hourly. */
    private static final long MAX_AGE_MILLIS = 25 * 60 * 1000L;
    /** Where the last answer is kept so a restarted process still has a forecast to draw. */
    private static final String CACHE_FILE = "oepl-weather-cache.json";

    @Nullable
    private static WeatherSpec cached;
    /** The same forecast in the richer form the cards draw from. */
    @Nullable
    private static OeplWeather cachedOepl;
    private static long cachedAt;
    private static String cachedFor = "";
    /** Why the last attempt failed, for the overview screen; empty when it worked. */
    private static String lastError = "";
    /** Set once the file cache has been consulted, so a missing file is not read over and over. */
    private static boolean loadedFromDisk;

    private OpenMeteoClient() {
    }

    @Nullable
    public static synchronized WeatherSpec cached() {
        loadIfNeeded();
        return cached;
    }

    /** Celsius, metres per second and raw WMO codes, as the OpenEPaperLink cards want it. */
    @Nullable
    public static synchronized OeplWeather cachedOepl() {
        loadIfNeeded();
        return cachedOepl;
    }

    /** When the forecast now in hand was fetched; 0 when there is none. */
    public static synchronized long cachedAtMillis() {
        loadIfNeeded();
        return cached == null ? 0L : cachedAt;
    }

    /** Which place the forecast in hand is for, spelled as it was asked for. */
    public static synchronized String cachedFor() {
        loadIfNeeded();
        return cachedFor;
    }

    /** What went wrong the last time, for the overview screen; empty when nothing did. */
    public static synchronized String lastError() {
        return lastError;
    }

    /** Drops the cache, on disk as well, so the next refresh really goes out to the network. */
    public static synchronized void invalidate() {
        cached = null;
        cachedOepl = null;
        cachedAt = 0;
        cachedFor = "";
        loadedFromDisk = true; // the file is gone too, so do not read it back in
        lastError = "";
        try {
            final File file = cacheFile();
            if (file != null && file.exists() && !file.delete()) {
                LOG.debug("could not remove the weather cache file");
            }
        } catch (final Exception e) {
            LOG.debug("could not remove the weather cache file", e);
        }
    }

    /**
     * Fetches a new forecast when the cached one aged out or the place changed.
     *
     * @return true when a usable forecast is available afterwards
     */
    public static boolean refreshIfStale(final SharedPreferences prefs) {
        return refreshIfOlderThan(prefs, MAX_AGE_MILLIS);
    }

    /**
     * As {@link #refreshIfStale}, but with the caller saying how old a forecast it is prepared to
     * draw. The picture that is about to go onto the panel asks for a much fresher one than a
     * background warm-up does, because what the panel takes is what the user reads for the next
     * hour; see {@link OEPLContentSync}.
     *
     * @return true when a usable forecast is available afterwards
     */
    public static boolean refreshIfOlderThan(final SharedPreferences prefs,
                                             final long maxAgeMillis) {
        final String place = OEPLPrefs.weatherLocation(prefs);
        if (place.isEmpty()) {
            return false;
        }
        synchronized (OpenMeteoClient.class) {
            loadIfNeeded();
            if (cached != null && place.equals(cachedFor)
                    && System.currentTimeMillis() - cachedAt < maxAgeMillis) {
                return true;
            }
        }
        try {
            final Place resolved = resolve(place);
            if (resolved == null) {
                LOG.warn("Open-Meteo geocoding does not know {}", place);
                synchronized (OpenMeteoClient.class) {
                    lastError = "unknown place";
                    return cached != null;
                }
            }
            // the daily rain total and the dominant wind are what a forecast card shows, and
            // is_day is what decides between a sun and a moon
            final String url = FORECAST
                    + "?latitude=" + resolved.latitude + "&longitude=" + resolved.longitude
                    + "&current_weather=true"
                    + "&hourly=temperature_2m,weathercode,relativehumidity_2m,precipitation"
                    + "&daily=weathercode,temperature_2m_max,temperature_2m_min,sunrise,sunset,"
                    + "precipitation_sum,windspeed_10m_max,winddirection_10m_dominant"
                    + "&windspeed_unit=ms&timeformat=unixtime&timezone=auto";
            final String body = get(url);
            final JSONObject doc = new JSONObject(body);
            final WeatherSpec spec = parse(doc, resolved.name);
            final OeplWeather rich = OeplWeather.parse(doc, resolved.name);
            final long now = System.currentTimeMillis();
            synchronized (OpenMeteoClient.class) {
                cached = spec;
                cachedOepl = rich;
                cachedAt = now;
                cachedFor = place;
                loadedFromDisk = true;
                lastError = "";
            }
            store(place, resolved.name, now, body);
            LOG.info("fetched weather for {}: {} K", spec.getLocation(), spec.getCurrentTemp());
            return true;
        } catch (final Exception e) {
            LOG.error("could not fetch the weather from Open-Meteo", e);
            synchronized (OpenMeteoClient.class) {
                lastError = e.getClass().getSimpleName();
                loadIfNeeded();
                return cached != null; // an old forecast still beats an empty card
            }
        }
    }

    // ------------------------------------------------------------------ surviving a restart

    /**
     * The forecast kept in memory is gone the moment Android recycles the process, which it does
     * readily to an app that spends its life waiting for a timer. Without this, the first card
     * drawn after a restart would be empty whenever the network happens to be unavailable, so the
     * last answer is written out verbatim and read back on demand.
     */
    @Nullable
    private static File cacheFile() {
        final Context context = GBApplication.getContext();
        return context == null ? null : new File(context.getFilesDir(), CACHE_FILE);
    }

    private static void store(final String place, final String name, final long at,
                              final String body) {
        final File file = cacheFile();
        if (file == null) {
            return;
        }
        try {
            final JSONObject envelope = new JSONObject();
            envelope.put("at", at);
            envelope.put("for", place);
            envelope.put("name", name);
            envelope.put("doc", new JSONObject(body));
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(envelope.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception e) {
            LOG.warn("could not keep the forecast for the next process", e);
        }
    }

    /** Reads the forecast back from disk, once, when nothing is in memory yet. */
    private static void loadIfNeeded() {
        if (loadedFromDisk || cached != null) {
            return;
        }
        loadedFromDisk = true;
        final File file = cacheFile();
        if (file == null || !file.exists()) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            final JSONObject envelope = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            final JSONObject doc = envelope.getJSONObject("doc");
            final String name = envelope.optString("name", "");
            cached = parse(doc, name);
            cachedOepl = OeplWeather.parse(doc, name);
            cachedAt = envelope.optLong("at", 0L);
            cachedFor = envelope.optString("for", "");
            LOG.info("recovered the forecast for {} from disk, {} minutes old", cachedFor,
                    (System.currentTimeMillis() - cachedAt) / 60000L);
        } catch (final Exception e) {
            LOG.warn("the stored forecast could not be read back", e);
        }
    }

    static class Place {
        final double latitude;
        final double longitude;
        final String name;

        Place(final double latitude, final double longitude, final String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }
    }

    /** Accepts "lat,lon" directly, otherwise looks the place name up. */
    @Nullable
    private static Place resolve(final String place) throws Exception {
        final String[] parts = place.split(",");
        if (parts.length == 2) {
            try {
                return new Place(Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()), "");
            } catch (final NumberFormatException ignored) {
                // not coordinates, fall through to the lookup
            }
        }
        final String url = GEOCODING + "?count=1&name=" + URLEncoder.encode(place, "UTF-8");
        final JSONObject result = new JSONObject(get(url));
        final JSONArray results = result.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return null;
        }
        final JSONObject first = results.getJSONObject(0);
        return new Place(first.getDouble("latitude"), first.getDouble("longitude"),
                first.optString("name", place));
    }

    /** Split out from the request so the mapping can be tested against canned responses. */
    static WeatherSpec parse(final JSONObject doc, final String name) {
        final WeatherSpec spec = new WeatherSpec();
        spec.setTimestamp((int) (System.currentTimeMillis() / 1000));
        spec.setLocation(name);
        spec.setLatitude((float) doc.optDouble("latitude", 0));
        spec.setLongitude((float) doc.optDouble("longitude", 0));

        final JSONObject current = doc.optJSONObject("current_weather");
        if (current != null) {
            spec.setCurrentTemp(kelvin(current.optDouble("temperature", 0)));
            spec.setFeelsLikeTemp(spec.getCurrentTemp());
            final int wmo = current.optInt("weathercode", 0);
            spec.setCurrentConditionCode(toOwmCode(wmo));
            spec.setCurrentCondition(describe(wmo));
            spec.setWindSpeed((float) (current.optDouble("windspeed", 0) * 3.6)); // m/s to km/h
            spec.setWindDirection(current.optInt("winddirection", 0));
        }

        applyDaily(spec, doc.optJSONObject("daily"));
        applyHourly(spec, doc.optJSONObject("hourly"));
        return spec;
    }

    private static void applyDaily(final WeatherSpec spec, @Nullable final JSONObject daily) {
        if (daily == null) {
            return;
        }
        final JSONArray codes = daily.optJSONArray("weathercode");
        final JSONArray highs = daily.optJSONArray("temperature_2m_max");
        final JSONArray lows = daily.optJSONArray("temperature_2m_min");
        if (codes == null || highs == null || lows == null) {
            return;
        }
        final JSONArray sunrise = daily.optJSONArray("sunrise");
        final JSONArray sunset = daily.optJSONArray("sunset");
        if (sunrise != null && sunrise.length() > 0) {
            spec.setSunRise(sunrise.optInt(0, 0));
        }
        if (sunset != null && sunset.length() > 0) {
            spec.setSunSet(sunset.optInt(0, 0));
        }
        // index 0 is today, which belongs on the current conditions rather than the forecast
        if (highs.length() > 0) {
            spec.setTodayMaxTemp(kelvin(highs.optDouble(0, 0)));
            spec.setTodayMinTemp(kelvin(lows.optDouble(0, 0)));
        }
        final ArrayList<WeatherSpec.Daily> days = new ArrayList<>();
        for (int i = 1; i < highs.length(); i++) {
            final WeatherSpec.Daily d = new WeatherSpec.Daily();
            d.setMaxTemp(kelvin(highs.optDouble(i, 0)));
            d.setMinTemp(kelvin(lows.optDouble(i, 0)));
            d.setConditionCode(toOwmCode(codes.optInt(i, 0)));
            days.add(d);
        }
        spec.setForecasts(days);
    }

    private static void applyHourly(final WeatherSpec spec, @Nullable final JSONObject hourly) {
        if (hourly == null) {
            return;
        }
        final JSONArray times = hourly.optJSONArray("time");
        final JSONArray temps = hourly.optJSONArray("temperature_2m");
        if (times == null || temps == null) {
            return;
        }
        final JSONArray codes = hourly.optJSONArray("weathercode");
        final JSONArray humidity = hourly.optJSONArray("relativehumidity_2m");

        // the hourly arrays start at midnight, so skip everything already behind us
        final long now = System.currentTimeMillis() / 1000L;
        final ArrayList<WeatherSpec.Hourly> result = new ArrayList<>();
        for (int i = 0; i < times.length() && result.size() < 24; i++) {
            final int ts = times.optInt(i, 0);
            if (ts < now - 1800) {
                continue;
            }
            final WeatherSpec.Hourly h = new WeatherSpec.Hourly();
            h.setTimestamp(ts);
            h.setTemp(kelvin(temps.optDouble(i, 0)));
            if (codes != null) {
                h.setConditionCode(toOwmCode(codes.optInt(i, 0)));
            }
            if (humidity != null) {
                h.setHumidity(humidity.optInt(i, 0));
            }
            result.add(h);
        }
        spec.setHourly(result);
    }

    private static int kelvin(final double celsius) {
        return (int) Math.round(celsius + 273.15);
    }

    /**
     * WMO weather code to the OpenWeatherMap code the rest of Gadgetbridge works with, so the
     * icons and every content type keep one single vocabulary.
     */
    static int toOwmCode(final int wmo) {
        switch (wmo) {
            case 0:
                return 800;          // clear
            case 1:
                return 801;          // mainly clear
            case 2:
                return 802;          // partly cloudy
            case 3:
                return 804;          // overcast
            case 45:
            case 48:
                return 741;          // fog
            case 51:
            case 53:
            case 55:
                return 301;          // drizzle
            case 56:
            case 57:
            case 66:
            case 67:
                return 511;          // freezing drizzle or rain
            case 61:
                return 500;          // slight rain
            case 63:
                return 501;
            case 65:
                return 502;          // heavy rain
            case 71:
                return 600;          // slight snow
            case 73:
                return 601;
            case 75:
                return 602;          // heavy snow
            case 77:
                return 601;          // snow grains
            case 80:
                return 520;          // rain showers
            case 81:
                return 521;
            case 82:
                return 522;
            case 85:
                return 620;          // snow showers
            case 86:
                return 622;
            case 95:
                return 200;          // thunderstorm
            case 96:
            case 99:
                return 201;          // thunderstorm with hail
            default:
                return 802;
        }
    }

    static String describe(final int wmo) {
        switch (wmo) {
            case 0:
                return "Klar";
            case 1:
                return "Überwiegend klar";
            case 2:
                return "Teilweise bewölkt";
            case 3:
                return "Bedeckt";
            case 45:
            case 48:
                return "Nebel";
            case 51:
            case 53:
            case 55:
                return "Nieselregen";
            case 56:
            case 57:
                return "Gefrierender Niesel";
            case 61:
                return "Leichter Regen";
            case 63:
                return "Regen";
            case 65:
                return "Starker Regen";
            case 66:
            case 67:
                return "Gefrierender Regen";
            case 71:
                return "Leichter Schneefall";
            case 73:
                return "Schneefall";
            case 75:
                return "Starker Schneefall";
            case 77:
                return "Schneegriesel";
            case 80:
                return "Leichte Schauer";
            case 81:
                return "Schauer";
            case 82:
                return "Kräftige Schauer";
            case 85:
            case 86:
                return "Schneeschauer";
            case 95:
                return "Gewitter";
            case 96:
            case 99:
                return "Gewitter mit Hagel";
            default:
                return "";
        }
    }

    private static String get(final String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            final int status = connection.getResponseCode();
            if (status != 200) {
                throw new IllegalStateException("Open-Meteo answered " + status);
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
