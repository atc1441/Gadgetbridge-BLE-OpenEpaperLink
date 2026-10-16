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
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;

/**
 * Weather in the shape the cards want it: degrees celsius, metres per second and the raw WMO
 * weather codes, plus the daily rain total and strongest wind that a forecast card shows.
 * <p>
 * Gadgetbridge normally carries weather as a {@link WeatherSpec} in kelvin and OpenWeatherMap
 * codes. That loses the rain total and turns the WMO code into something coarser, so when we
 * fetch the forecast ourselves we keep this richer form alongside, and fall back to converting
 * a WeatherSpec when the user chose a different weather source.
 */
public class OeplWeather {

    public static class Current {
        public float temperature;       // celsius
        public float windSpeed;         // metres per second
        public int windDirection;       // degrees the wind comes from
        public int wmoCode;
        public boolean day = true;
        public int humidity = -1;
    }

    public static class Day {
        public long epochSeconds;       // local midnight of that day
        public int wmoCode;
        public float maxTemperature;    // celsius
        public float minTemperature;    // celsius
        public float precipitation = -1f;  // millimetres, negative when unknown
        public float windSpeed;         // the day's strongest wind, metres per second
        public int windDirection;       // the day's dominant direction
    }

    public static class Hour {
        public long epochSeconds;
        public float temperature;       // celsius
        public int wmoCode;
        public float precipitation = -1f;
    }

    public String location = "";
    public final Current current = new Current();
    public final List<Day> days = new ArrayList<>();
    public final List<Hour> hours = new ArrayList<>();
    public long sunrise;
    public long sunset;
    /**
     * The place's own offset from UTC, in seconds. Weather belongs to where it falls: a
     * forecast for a place two time zones away is about that place's morning, not about the
     * phone's. Where the two agree - which is the normal case, the weather of the town the
     * display hangs in - nothing about this is visible.
     */
    public int utcOffsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis())
            / 1000;
    /** Phase angle in degrees, or -1 when nothing told us. */
    public int moonPhase = -1;
    public long fetchedAt = System.currentTimeMillis();

    /**
     * The forecast from today on.
     * <p>
     * What is held here can be older than it looks: the network was unreachable for a few
     * hours, or overnight, and the display drew the picture anyway from the last answer -
     * which is the right thing to do, except that the first day of that answer is then
     * yesterday. Nothing on a wall is more confusing than yesterday presented as today, so a
     * day that is over is dropped rather than drawn, and a forecast that is entirely in the
     * past leaves the card with nothing, which is what it honestly has.
     */
    public List<Day> upcomingDays() {
        final long now = System.currentTimeMillis();
        final List<Day> upcoming = new ArrayList<>(days.size());
        for (final Day day : days) {
            // a day is over once its own midnight is a full day behind us; a day without a
            // timestamp cannot be judged and is kept
            if (day.epochSeconds <= 0 || (day.epochSeconds + 86400L) * 1000L > now) {
                upcoming.add(day);
            }
        }
        return upcoming;
    }

    /** As {@link #isToday(Day)}, for a caller that has the offset but not the forecast. */
    public static boolean isToday(final Day day, final int utcOffsetSeconds) {
        if (day == null || day.epochSeconds <= 0) {
            return false;
        }
        // the day runs from its own midnight for twenty four hours, wherever that midnight
        // falls - the offset is only needed because a day's start is given in local terms
        final long start = day.epochSeconds * 1000L;
        final long now = System.currentTimeMillis();
        return now >= start && now < start + 86400000L;
    }

    /** The time zone the forecast's hours and days are counted in. */
    public TimeZone zone() {
        final int minutes = Math.abs(utcOffsetSeconds) / 60;
        return TimeZone.getTimeZone(String.format(Locale.ROOT, "GMT%s%02d:%02d",
                utcOffsetSeconds < 0 ? "-" : "+", minutes / 60, minutes % 60));
    }

    /** A date format that reads the forecast's timestamps in the place's own time. */
    public SimpleDateFormat format(final String pattern) {
        final SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(zone());
        return format;
    }

    /** Whether this day is the one the place is in, which is what a card marks as today. */
    public boolean isToday(final Day day) {
        return isToday(day, utcOffsetSeconds);
    }

    public boolean hasData() {
        return current.temperature != 0f || !days.isEmpty() || !hours.isEmpty();
    }

    /** Reads an Open-Meteo forecast response. */
    public static OeplWeather parse(final JSONObject doc, final String name) {
        final OeplWeather w = new OeplWeather();
        w.location = name == null ? "" : name;

        final JSONObject current = doc.optJSONObject("current_weather");
        if (current != null) {
            w.current.temperature = (float) current.optDouble("temperature", 0);
            w.current.windSpeed = (float) current.optDouble("windspeed", 0);
            w.current.windDirection = current.optInt("winddirection", 0);
            w.current.wmoCode = current.optInt("weathercode", 0);
            w.current.day = current.optInt("is_day", 1) != 0;
        }

        final JSONObject daily = doc.optJSONObject("daily");
        if (daily != null) {
            final JSONArray times = daily.optJSONArray("time");
            final JSONArray codes = daily.optJSONArray("weathercode");
            final JSONArray highs = daily.optJSONArray("temperature_2m_max");
            final JSONArray lows = daily.optJSONArray("temperature_2m_min");
            final JSONArray rain = daily.optJSONArray("precipitation_sum");
            final JSONArray wind = daily.optJSONArray("windspeed_10m_max");
            final JSONArray windDir = daily.optJSONArray("winddirection_10m_dominant");
            final JSONArray sunrise = daily.optJSONArray("sunrise");
            final JSONArray sunset = daily.optJSONArray("sunset");
            if (sunrise != null && sunrise.length() > 0) {
                w.sunrise = sunrise.optLong(0, 0);
            }
            if (sunset != null && sunset.length() > 0) {
                w.sunset = sunset.optLong(0, 0);
            }
            final int count = highs != null ? highs.length() : 0;
            for (int i = 0; i < count; i++) {
                final Day d = new Day();
                d.epochSeconds = times != null ? times.optLong(i, 0) : 0;
                d.wmoCode = codes != null ? codes.optInt(i, 0) : 0;
                d.maxTemperature = (float) highs.optDouble(i, 0);
                d.minTemperature = lows != null ? (float) lows.optDouble(i, 0) : 0f;
                if (rain != null) {
                    d.precipitation = (float) rain.optDouble(i, 0);
                }
                if (wind != null) {
                    d.windSpeed = (float) wind.optDouble(i, 0);
                }
                if (windDir != null) {
                    d.windDirection = windDir.optInt(i, 0);
                }
                w.days.add(d);
            }
        }

        final JSONObject hourly = doc.optJSONObject("hourly");
        if (hourly != null) {
            final JSONArray times = hourly.optJSONArray("time");
            final JSONArray temps = hourly.optJSONArray("temperature_2m");
            final JSONArray codes = hourly.optJSONArray("weathercode");
            final JSONArray rain = hourly.optJSONArray("precipitation");
            final int count = times != null ? times.length() : 0;
            for (int i = 0; i < count; i++) {
                final Hour h = new Hour();
                h.epochSeconds = times.optLong(i, 0);
                h.temperature = temps != null ? (float) temps.optDouble(i, 0) : 0f;
                h.wmoCode = codes != null ? codes.optInt(i, 0) : 0;
                if (rain != null) {
                    h.precipitation = (float) rain.optDouble(i, 0);
                }
                w.hours.add(h);
            }
        }
        return w;
    }

    /**
     * Builds the same shape out of whatever weather Gadgetbridge was handed, for users who keep
     * their existing weather app rather than letting us fetch it.
     */
    public static OeplWeather from(@Nullable final WeatherSpec spec) {
        final OeplWeather w = new OeplWeather();
        if (spec == null) {
            return w;
        }
        w.location = spec.getLocation() == null ? "" : spec.getLocation();
        w.current.temperature = celsius(spec.getCurrentTemp());
        w.current.windSpeed = spec.getWindSpeed() / 3.6f; // km/h back to m/s
        w.current.windDirection = spec.getWindDirection();
        w.current.wmoCode = toWmoCode(spec.getCurrentConditionCode());
        w.current.humidity = spec.getCurrentHumidity();
        w.sunrise = spec.getSunRise();
        w.sunset = spec.getSunSet();
        w.moonPhase = spec.getMoonPhase();

        final long now = System.currentTimeMillis() / 1000L;
        w.current.day = w.sunrise <= 0 || w.sunset <= 0 || (now >= w.sunrise && now < w.sunset);

        // today first, so that index 0 means the same thing as it does in an Open-Meteo answer
        final Day today = new Day();
        today.epochSeconds = startOfDay(now);
        today.wmoCode = w.current.wmoCode;
        today.maxTemperature = celsius(spec.getTodayMaxTemp());
        today.minTemperature = celsius(spec.getTodayMinTemp());
        today.windSpeed = w.current.windSpeed;
        today.windDirection = w.current.windDirection;
        w.days.add(today);

        int index = 1;
        for (final WeatherSpec.Daily d : spec.getForecasts()) {
            final Day day = new Day();
            day.epochSeconds = startOfDay(now) + index * 86400L;
            day.wmoCode = toWmoCode(d.getConditionCode());
            day.maxTemperature = celsius(d.getMaxTemp());
            day.minTemperature = celsius(d.getMinTemp());
            day.windSpeed = d.getWindSpeed() / 3.6f;
            day.windDirection = d.getWindDirection();
            w.days.add(day);
            index++;
        }
        for (final WeatherSpec.Hourly h : spec.getHourly()) {
            final Hour hour = new Hour();
            hour.epochSeconds = h.getTimestamp();
            hour.temperature = celsius(h.getTemp());
            hour.wmoCode = toWmoCode(h.getConditionCode());
            w.hours.add(hour);
        }
        return w;
    }

    private static long startOfDay(final long epochSeconds) {
        final java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(epochSeconds * 1000L);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 1000L;
    }

    private static float celsius(final int kelvin) {
        return kelvin == 0 ? 0f : (float) (kelvin - 273.15);
    }

    /**
     * OpenWeatherMap condition code back to the nearest WMO code, so a forecast that came in
     * through Gadgetbridge picks the same pictogram as one we fetched ourselves.
     */
    public static int toWmoCode(final int owm) {
        if (owm >= 200 && owm < 300) {
            return owm == 200 || owm == 210 || owm == 230 ? 95 : 96;
        }
        if (owm >= 300 && owm < 400) {
            return 51;
        }
        if (owm == 511) {
            return 66;
        }
        if (owm >= 520 && owm < 532) {
            return 80;
        }
        if (owm >= 500 && owm < 600) {
            return owm >= 502 ? 65 : (owm == 501 ? 63 : 61);
        }
        if (owm >= 611 && owm <= 616) {
            return 66;
        }
        if (owm >= 620 && owm < 630) {
            return 85;
        }
        if (owm >= 600 && owm < 700) {
            return owm >= 602 ? 75 : (owm == 601 ? 73 : 71);
        }
        if (owm == 741 || owm == 701 || owm == 721) {
            return 45;
        }
        if (owm >= 700 && owm < 800) {
            return 45;
        }
        switch (owm) {
            case 800:
                return 0;
            case 801:
                return 1;
            case 802:
                return 2;
            case 803:
            case 804:
                return 3;
            default:
                return 3;
        }
    }
}
