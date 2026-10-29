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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.HomeAssistantClient;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.OEPLPrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.PhoneStateStore;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;

/**
 * Collects everything a dashboard card can show, in the order it should be shown in: the sky
 * first, then the house, then the appointments, then the batteries.
 * <p>
 * The order is the design. A tile grid takes the first eight of these, a list takes as many as
 * the panel is tall, the big-value card takes the first one and puts the next few beside it as
 * badges - and all three agree about which value matters most without any of them having to
 * know where a value came from. Which of the four groups are collected at all is a setting, so
 * a tag in the hall can show the house while one on the desk shows the agenda.
 */
public final class OeplReadings {
    private static final Logger LOG = LoggerFactory.getLogger(OeplReadings.class);

    /** The group names as the setting stores them. */
    public static final String GROUP_WEATHER = "weather";
    public static final String GROUP_HOUSE = "house";
    public static final String GROUP_EVENTS = "events";
    public static final String GROUP_DEVICES = "devices";

    private OeplReadings() {
    }

    /**
     * Everything the configured groups have to say.
     *
     * @param weather the forecast, or null when there is none to be had
     * @param events  the appointments the card was given, already filtered by the calendar
     */
    public static List<OeplReading> collect(final Context context, final GBDevice device,
                                            final SharedPreferences prefs,
                                            @Nullable final OeplWeather weather,
                                            final List<CalendarEvent> events) {
        return collect(context, device, prefs, weather, events, SMALL_PANEL);
    }

    /** What a tag with no room to spare shows: the shape this collector had for years. */
    public static final int SMALL_PANEL = 8;

    /**
     * Everything the dashboard cards draw, collected once and in one order.
     * <p>
     * How much is collected depends on how much the panel can show. A price tag holds eight
     * values and wants the eight that matter; a seven inch panel on a wall holds two dozen,
     * and filling it with the same eight in enormous type wastes most of it. So the parts
     * that can go on - the agenda above all, which is a list rather than a value - are asked
     * for as many rows as there is room for.
     *
     * @param room how many values the card expects to be able to draw
     */
    public static List<OeplReading> collect(final Context context, final GBDevice device,
                                            final SharedPreferences prefs,
                                            @Nullable final OeplWeather weather,
                                            final List<CalendarEvent> events, final int room) {
        final Set<String> groups = OEPLPrefs.readingGroups(prefs);
        final List<OeplReading> readings = new ArrayList<>();
        if (groups.contains(GROUP_WEATHER)) {
            final int from = readings.size();
            weather(context, prefs, weather, readings, room);
            tag(readings, from, GROUP_WEATHER);
        }
        if (groups.contains(GROUP_HOUSE)) {
            final int from = readings.size();
            house(context, readings);
            tag(readings, from, GROUP_HOUSE);
        }
        if (groups.contains(GROUP_EVENTS)) {
            final int from = readings.size();
            events(context, events, readings, room);
            tag(readings, from, GROUP_EVENTS);
        }
        if (groups.contains(GROUP_DEVICES)) {
            final int from = readings.size();
            devices(context, device, prefs, readings);
            tag(readings, from, GROUP_DEVICES);
        }
        return readings;
    }

    /** Marks whatever a group just added as that group's, here rather than in four places. */
    private static void tag(final List<OeplReading> readings, final int from,
                            final String group) {
        for (int i = from; i < readings.size(); i++) {
            readings.get(i).inGroup(group);
        }
    }

    /**
     * The same list without one or two of the groups, for a card that already shows them.
     * <p>
     * A card made of regions draws some of these groups properly: the wall sheet has a week
     * grid and an appointment list on it, the big value card has the agenda beside the figure.
     * A column of values that then repeats one of those groups is the panel saying the same
     * thing twice - and it is worst on the largest panels, because that is where there is room
     * for both. The agenda is a list rather than a value, so the collector hands over as many
     * of the next appointments as the panel can hold: on a 9.7 inch sheet the values column
     * repeated the agenda beside it line for line, while a 2.9 inch tag, which has no values
     * column at all, looked right.
     */
    public static List<OeplReading> without(final List<OeplReading> readings,
                                            final String... groups) {
        final List<OeplReading> rest = new ArrayList<>(readings.size());
        for (final OeplReading reading : readings) {
            boolean drop = false;
            for (final String group : groups) {
                drop |= group.equals(reading.group);
            }
            if (!drop) {
                rest.add(reading);
            }
        }
        return rest;
    }

    // ------------------------------------------------------------------ the sky

    private static void weather(final Context context, final SharedPreferences prefs,
                                @Nullable final OeplWeather weather,
                                final List<OeplReading> out, final int room) {
        if (weather == null || !weather.hasData()) {
            return;
        }
        final String section = context.getString(R.string.oepl_section_weather);
        final OeplWeather.Current now = weather.current;

        // the place as the label, because a display that shows two places has to say which
        final String where = weather.location == null || weather.location.trim().isEmpty()
                ? section : weather.location.trim();
        out.add(new OeplReading(section, where, "")
                .withTemperature(now.temperature, true)
                .withFontGlyph(OeplIcons.weather(now.wmoCode, !now.day))
                .accented(now.temperature < 0f));

        if (!weather.upcomingDays().isEmpty()) {
            final OeplWeather.Day today = weather.upcomingDays().get(0);
            out.add(new OeplReading(section, context.getString(R.string.oepl_reading_today), "")
                    .withRange(today.minTemperature, today.maxTemperature)
                    .withFontGlyph(String.valueOf(OeplIcons.THERMOMETER)));
            if (today.precipitation >= 0f) {
                out.add(new OeplReading(section, context.getString(R.string.oepl_reading_rain),
                        String.format(Locale.ROOT, "%.1f mm", today.precipitation))
                        .withFontGlyph(String.valueOf(OeplIcons.RAINDROP))
                        .accented(today.precipitation >= 1f));
            }
        }
        out.add(new OeplReading(section, context.getString(R.string.oepl_reading_wind),
                windValue(prefs, now.windSpeed))
                .withFontGlyph(OeplIcons.windDirection(now.windDirection))
                .accented(OeplIcons.beaufort(now.windSpeed) > 4));
        if (now.humidity >= 0) {
            out.add(new OeplReading(section, context.getString(R.string.oepl_reading_humidity),
                    now.humidity + " %")
                    .withFontGlyph(String.valueOf(OeplIcons.UMBRELLA))
                    .withPercent(now.humidity));
        }

        // What a larger panel gets on top: the day after this one, and when the sun does its
        // two things. Neither is worth a tile on a price tag, and both are worth one on a wall.
        if (room >= SMALL_PANEL + 4) {
            if (weather.upcomingDays().size() > 1) {
                final OeplWeather.Day next = weather.upcomingDays().get(1);
                out.add(new OeplReading(section,
                        context.getString(R.string.oepl_reading_tomorrow), "")
                        .withRange(next.minTemperature, next.maxTemperature)
                        .withFontGlyph(OeplIcons.weather(next.wmoCode, false)));
            }
            if (weather.sunrise > 0L && weather.sunset > 0L) {
                final SimpleDateFormat clock = weather.format("HH:mm");
                out.add(new OeplReading(section, context.getString(R.string.oepl_reading_sun),
                        clock.format(new java.util.Date(weather.sunrise * 1000L)) + " - "
                                + clock.format(new java.util.Date(weather.sunset * 1000L)))
                        .withFontGlyph(String.valueOf(OeplIcons.SUNRISE)));
            }
        }
    }

    private static String windValue(final SharedPreferences prefs, final float metresPerSecond) {
        return OEPLPrefs.windAsBeaufort(prefs)
                ? String.valueOf(OeplIcons.beaufort(metresPerSecond))
                : Math.round(metresPerSecond) + " m/s";
    }

    // ------------------------------------------------------------------ the house

    private static void house(final Context context, final List<OeplReading> out) {
        final String section = context.getString(R.string.oepl_section_house);
        for (final HomeAssistantClient.Entity entity : HomeAssistantClient.cached()) {
            final OeplReading reading = new OeplReading(section, entity.label,
                    stateText(context, entity));
            // the two sensor kinds every instance has plenty of get the weather face's own
            // symbols, which are better drawn than anything a shape of ours could be
            if ("temperature".equals(entity.deviceClass)) {
                reading.withFontGlyph(String.valueOf(OeplIcons.THERMOMETER));
            } else if ("humidity".equals(entity.deviceClass)) {
                reading.withFontGlyph(String.valueOf(OeplIcons.RAINDROP));
            } else {
                reading.withShape(OeplGlyphs.forEntity(entity.entityId, entity.deviceClass,
                        entity.unit));
            }
            final float number = entity.number();
            if ("%".equals(entity.unit) && !Float.isNaN(number)) {
                reading.withPercent(Math.round(number));
            }
            // a door left open, a lock left unlocked and an empty battery are the three states
            // worth a second colour on a panel that is otherwise black on white
            reading.accented(isAlarming(entity));
            out.add(reading);
        }
    }

    /**
     * The state in words. Home Assistant answers in its own vocabulary - on, off, open,
     * closed, home, not_home - which is not what somebody wants to read off a wall, so the
     * handful of states that appear everywhere are translated and everything else is passed
     * through as it came.
     */
    private static String stateText(final Context context,
                                    final HomeAssistantClient.Entity entity) {
        switch (entity.state == null ? "" : entity.state.toLowerCase(Locale.ROOT)) {
            case "on":
                return context.getString(R.string.oepl_state_on);
            case "off":
                return context.getString(R.string.oepl_state_off);
            case "open":
                return context.getString(R.string.oepl_state_open);
            case "closed":
                return context.getString(R.string.oepl_state_closed);
            case "locked":
                return context.getString(R.string.oepl_state_locked);
            case "unlocked":
                return context.getString(R.string.oepl_state_unlocked);
            case "home":
                return context.getString(R.string.oepl_state_home);
            case "not_home":
            case "away":
                return context.getString(R.string.oepl_state_away);
            case "unavailable":
            case "unknown":
            case "":
                return "–";
            default:
                return entity.formatted();
        }
    }

    private static boolean isAlarming(final HomeAssistantClient.Entity entity) {
        final String state = entity.state == null ? "" : entity.state.toLowerCase(Locale.ROOT);
        if ("open".equals(state) || "unlocked".equals(state)) {
            return true;
        }
        final float number = entity.number();
        return "battery".equals(entity.deviceClass) && !Float.isNaN(number) && number <= 20f;
    }

    // ------------------------------------------------------------------ the agenda

    private static void events(final Context context, final List<CalendarEvent> events,
                               final List<OeplReading> out, final int room) {
        if (events == null || events.isEmpty()) {
            return;
        }
        final String section = context.getString(R.string.oepl_section_events);
        // by the days an entry covers: a holiday that runs over today is one of today's, and
        // an all day entry carries a date rather than a moment - see OeplDays
        final int today = OeplDays.countOnDay(events, OeplDays.today());
        out.add(new OeplReading(section, context.getString(R.string.oepl_reading_events),
                String.valueOf(today))
                .withShape(OeplGlyphs.Glyph.CALENDAR)
                .accented(today > 0));

        // The next one by name, which is the single most useful line on a hall display -
        // and on a panel with room for them, the ones after it as well. An appointment is the
        // one thing on this card that is a list rather than a value: "3" says that the day is
        // busy, the three lines say what it is busy with.
        final int wanted = Math.max(1, Math.min((room - SMALL_PANEL) / 2 + 1, events.size()));
        for (int i = 0; i < wanted; i++) {
            final CalendarEvent event = events.get(i);
            out.add(new OeplReading(section, time(event), title(event))
                    .withShape(OeplGlyphs.Glyph.CLOCK));
        }
    }

    private static String title(final CalendarEvent event) {
        final String title = event.getTitle();
        return title == null || title.trim().isEmpty() ? "–" : title.trim();
    }

    private static String time(final CalendarEvent event) {
        if (event.isAllDay()) {
            return new java.text.SimpleDateFormat("EEE", Locale.getDefault())
                    .format(OeplDays.dayOf(event));
        }
        return new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new java.util.Date(event.getBegin()));
    }

    // ------------------------------------------------------------------ the batteries

    private static void devices(final Context context, final GBDevice display,
                                final SharedPreferences prefs, final List<OeplReading> out) {
        final String section = context.getString(R.string.oepl_section_devices);
        final int phone = PhoneStateStore.phoneBattery(context);
        if (phone >= 0) {
            out.add(new OeplReading(section, context.getString(R.string.oepl_card_phone),
                    phone + " %")
                    .withShape(OeplGlyphs.Glyph.PHONE)
                    .withPercent(phone)
                    .accented(phone <= 15));
        }
        final int millivolts = prefs.getInt(OEPLPrefs.LAST_BATTERY_MV, -1);
        if (millivolts > 0) {
            final int percent = OEPLPrefs.batteryPercent(millivolts);
            out.add(new OeplReading(section,
                    context.getString(R.string.oepl_reading_display), percent + " %")
                    .withShape(OeplGlyphs.Glyph.BATTERY)
                    .withPercent(percent)
                    .accented(percent <= 15));
        }
        try {
            for (final GBDevice device : GBApplication.app().getDeviceManager().getDevices()) {
                if (device.getType() == DeviceType.OEPL_WEATHER_DISPLAY) {
                    continue; // every display reports its own battery, above
                }
                final int level = device.getBatteryLevel(0);
                if (level < 0) {
                    continue; // a device that never reported one would only take up a row
                }
                out.add(new OeplReading(section, device.getAliasOrName(), level + " %")
                        .withShape(OeplGlyphs.Glyph.WATCH)
                        .withPercent(level)
                        .accented(level <= 15));
            }
        } catch (final Exception e) {
            // no application around, which is what a unit test looks like
            LOG.debug("could not collect the device batteries", e);
        }
    }
}
