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
import android.graphics.Bitmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CalendarContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ClockContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ForecastContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.ImageContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.HourlyWeatherContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.MusicContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.NotificationsContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.DateContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.SplitContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.StatusContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.TextContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.WeatherContent;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarManager;

/**
 * Builds the content a display is configured to show. Adding a new kind of card means writing a
 * {@link CardContent} and adding one case here plus an entry in the settings list.
 */
public final class CardContentFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CardContentFactory.class);

    public static final String WEATHER = "weather";
    public static final String DATE = "date";
    public static final String CALENDAR = "calendar";
    public static final String TEXT = "text";
    public static final String STATUS = "status";
    public static final String WEATHER_CALENDAR = "weather_calendar";
    public static final String WEATHER_DATE = "weather_date";
    public static final String DATE_CALENDAR = "date_calendar";
    public static final String CLOCK = "clock";
    public static final String NOTIFICATIONS = "notifications";
    public static final String MUSIC = "music";
    public static final String HOURLY = "hourly";
    public static final String FORECAST = "forecast";
    public static final String CLOCK_WEATHER = "clock_weather";
    public static final String WEATHER_NOTIFICATIONS = "weather_notifications";
    public static final String CALENDAR_NOTIFICATIONS = "calendar_notifications";
    public static final String DASHBOARD = "dashboard";
    /** A picture chosen on the phone. */
    public static final String IMAGE = "image";
    /** A picture fetched from a URL, the way the access point's image slot works. */
    public static final String IMAGE_URL = "image_url";

    private CardContentFactory() {
    }

    public static CardContent create(final Context context, final GBDevice device,
                                     final SharedPreferences prefs) {
        return create(context, device, prefs, OEPLPrefs.contentType(prefs));
    }

    /**
     * Builds one particular content rather than the configured one, which is what lets the
     * design picker draw all of them side by side with this display's own data.
     */
    public static CardContent create(final Context context, final GBDevice device,
                                     final SharedPreferences prefs, final String id) {
        if (OeplCardFactory.handles(id)) {
            // one of the cards that copies the OpenEPaperLink layouts; it draws the whole panel
            return OeplCardFactory.create(context, device, prefs, id);
        }
        switch (id) {
            case DATE:
                return new DateContent();
            case CALENDAR:
                return calendar(context, device, prefs);
            case TEXT:
                return text(prefs);
            case STATUS:
                return status();
            case WEATHER_CALENDAR:
                return new SplitContent(weather(prefs), calendar(context, device, prefs), 0.55f);
            case WEATHER_DATE:
                return new SplitContent(weather(prefs), new DateContent(), 0.6f);
            case DATE_CALENDAR:
                return new SplitContent(new DateContent(), calendar(context, device, prefs), 0.4f);
            case CLOCK:
                return new ClockContent();
            case NOTIFICATIONS:
                return notifications(context, prefs);
            case MUSIC:
                return new MusicContent(PhoneStateStore.music(), PhoneStateStore.musicPlaying(),
                        context.getString(PhoneStateStore.musicPlaying()
                                ? R.string.oepl_card_music_playing
                                : R.string.oepl_card_music_last));
            case HOURLY:
                return new HourlyWeatherContent(spec(prefs), OEPLPrefs.forecastHours(prefs),
                        context.getString(R.string.oepl_card_curve));
            case FORECAST:
                return new ForecastContent(spec(prefs),
                        Math.max(3, OEPLPrefs.forecastDays(prefs) + 2),
                        context.getString(R.string.oepl_card_forecast));
            case CLOCK_WEATHER:
                return new SplitContent(new ClockContent(), weather(prefs), 0.45f);
            case WEATHER_NOTIFICATIONS:
                return new SplitContent(weather(prefs), notifications(context, prefs), 0.5f);
            case CALENDAR_NOTIFICATIONS:
                return new SplitContent(calendar(context, device, prefs),
                        notifications(context, prefs), 0.5f);
            case DASHBOARD:
                return new SplitContent(weather(prefs),
                        new SplitContent(calendar(context, device, prefs),
                                notifications(context, prefs), 0.5f),
                        0.45f);
            case IMAGE:
            case IMAGE_URL:
                return image(context, device, prefs, id);
            case WEATHER:
            default:
                return weather(prefs);
        }
    }

    /**
     * A picture, reduced to the colours this panel actually has. The palette comes from the
     * display rather than from a constant: dithering a photograph against three colours when the
     * panel has four throws away a quarter of what it could show.
     */
    private static CardContent image(final Context context, final GBDevice device,
                                     final SharedPreferences prefs, final String id) {
        final int[] panel = OEPLCardPreview.panelSize(prefs);
        final Bitmap picture = OEPLImageSource.load(context, prefs, id, device.getAddress(),
                panel[0], panel[1]);
        return new ImageContent(picture, OEPLPrefs.imageDither(prefs),
                OEPLPrefs.imageFit(prefs), palette(prefs, panel[2]),
                OEPLPrefs.imageZoom(prefs), OEPLPrefs.imagePanX(prefs),
                OEPLPrefs.imagePanY(prefs));
    }

    /**
     * The colours this panel can light up, in the order the codec expects them: darkest first,
     * paper last, because that is what tells a grey from a coloured pixel apart.
     */
    private static int[] palette(final SharedPreferences prefs, final int colorCount) {
        return OEPLImageCodec.palette(colorCount,
                "yellow".equals(OEPLPrefs.accentMode(prefs)));
    }

    private static CardContent weather(final SharedPreferences prefs) {
        return new WeatherContent(spec(prefs), OEPLPrefs.forecastDays(prefs));
    }

    /** Our own Open-Meteo forecast when configured, otherwise whatever Gadgetbridge was given. */
    private static WeatherSpec spec(final SharedPreferences prefs) {
        if (OEPLPrefs.useOpenMeteo(prefs)) {
            final WeatherSpec own = OpenMeteoClient.cached();
            if (own != null) {
                return own;
            }
        }
        final List<WeatherSpec> specs = Weather.getWeatherSpecs();
        return specs.isEmpty() ? null : specs.get(0);
    }

    private static CardContent notifications(final Context context,
                                             final SharedPreferences prefs) {
        final List<PhoneStateStore.Note> notes = PhoneStateStore.notifications();
        return new NotificationsContent(notes, OEPLPrefs.notificationCount(prefs),
                context.getString(R.string.oepl_card_notifications, notes.size()));
    }

    private static CardContent calendar(final Context context, final GBDevice device,
                                        final SharedPreferences prefs) {
        List<CalendarEvent> events = Collections.emptyList();
        try {
            events = new CalendarManager(context, device.getAddress()).getCalendarEventList();
        } catch (final Exception e) {
            // typically a missing calendar permission, which must not take the whole card down
            LOG.warn("could not read the calendar", e);
        }
        return new CalendarContent(events, OEPLPrefs.calendarEvents(prefs));
    }

    private static CardContent text(final SharedPreferences prefs) {
        final String raw = OEPLPrefs.customText(prefs);
        final List<String> lines = new ArrayList<>();
        for (final String line : raw.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        String heading = null;
        if (lines.size() > 1) {
            heading = lines.remove(0);
        }
        return new TextContent(heading, lines);
    }

    private static CardContent status() {
        final List<StatusContent.Entry> entries = new ArrayList<>();
        try {
            for (final GBDevice device : GBApplication.app().getDeviceManager().getDevices()) {
                if (device.getType() == nodomain.freeyourgadget.gadgetbridge.model.DeviceType.OEPL_WEATHER_DISPLAY) {
                    continue; // the display itself is already in the status strip
                }
                entries.add(new StatusContent.Entry(device.getAliasOrName(),
                        device.getBatteryLevel(0)));
            }
            final int phone = PhoneStateStore.phoneBattery(GBApplication.getContext());
            if (phone >= 0) {
                entries.add(0, new StatusContent.Entry(
                        GBApplication.getContext().getString(R.string.oepl_card_phone), phone));
            }
        } catch (final Exception e) {
            LOG.warn("could not collect device states", e);
        }
        return new StatusContent(entries);
    }
}
