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

/**
 * Picks pictograms out of the Weather Icons face, keyed by the WMO weather codes that Open-Meteo
 * answers with. An OpenEPaperLink access point draws from the same face and makes the same
 * choices, so a card drawn here carries the symbols a user of that system already knows.
 */
public final class OeplIcons {

    // Weather Icons code points, named after what they depict.
    private static final char SUN = '';
    private static final char SUN_HAZE = '';
    private static final char SUN_CLOUD = '';
    private static final char CLOUDS = '';
    private static final char FOG = '';
    private static final char DRIZZLE = '';
    private static final char SLEET = '';
    private static final char RAIN = '';
    private static final char HAIL = '';
    private static final char SNOW = '';
    private static final char SNOW_GRAINS = '';
    private static final char SNOW_SHOWER = '';
    private static final char THUNDER = '';
    private static final char THUNDER_HAIL = '';
    private static final char MOON = '';
    private static final char MOON_HAZE = '';
    private static final char MOON_CLOUD = '';

    public static final char UMBRELLA = '';
    public static final char SUNRISE = '';
    public static final char SUNSET = '';
    public static final char THERMOMETER = '';
    public static final char RAINDROP = '';
    public static final char WIND = '';
    /** The first of the 28 moon phase glyphs; add the moon's age in days. */
    private static final char MOON_PHASE_FIRST = '';

    /** Eight arrows, starting at "coming from the north". */
    private static final char[] WIND_FROM = {
            '', '', '', '', '', '', '', ''};

    /** Upper bounds of the Beaufort scale in metres per second. */
    private static final float[] BEAUFORT = {
            0.3f, 1.5f, 3.3f, 5.5f, 8f, 10.8f, 13.9f, 17.2f, 20.8f, 24.5f, 28.5f, 32.7f};

    private OeplIcons() {
    }

    /**
     * The pictogram for a WMO weather code.
     *
     * @param wmoCode as Open-Meteo reports it: 0 clear, 3 overcast, 45 fog, 61 rain, 95 storm...
     * @param night   the clear and lightly clouded skies get a moon instead of a sun
     */
    public static String weather(final int wmoCode, final boolean night) {
        if (night) {
            switch (wmoCode) {
                case 0:
                    return String.valueOf(MOON);
                case 1:
                    return String.valueOf(MOON_HAZE);
                case 2:
                    return String.valueOf(MOON_CLOUD);
                default:
                    break;
            }
        }
        final char glyph;
        switch (wmoCode) {
            case 0:
                glyph = SUN;
                break;
            case 1:
                glyph = SUN_HAZE;
                break;
            case 2:
                glyph = SUN_CLOUD;
                break;
            case 3:
                glyph = CLOUDS;
                break;
            case 45:
            case 48:
                glyph = FOG;
                break;
            case 51:
            case 53:
            case 55:
                glyph = DRIZZLE;
                break;
            case 56:
            case 57:
                glyph = SLEET;
                break;
            case 61:
            case 63:
            case 65:
                glyph = RAIN;
                break;
            case 66:
            case 67:
                glyph = HAIL;
                break;
            case 71:
            case 73:
            case 75:
                glyph = SNOW;
                break;
            case 77:
                glyph = SNOW_GRAINS;
                break;
            case 80:
            case 81:
            case 82:
                glyph = DRIZZLE;
                break;
            case 85:
            case 86:
                glyph = SNOW_SHOWER;
                break;
            case 96:
                glyph = THUNDER_HAIL;
                break;
            case 95:
            case 99:
                glyph = THUNDER;
                break;
            default:
                glyph = CLOUDS;
                break;
        }
        return String.valueOf(glyph);
    }

    /** True for the codes that deserve the accent colour: the heavy and the dangerous ones. */
    public static boolean isSevere(final int wmoCode) {
        switch (wmoCode) {
            case 55:
            case 65:
            case 75:
            case 82:
            case 86:
            case 95:
            case 96:
            case 99:
                return true;
            default:
                return false;
        }
    }

    /** Anything from drizzle upwards earns the little umbrella. */
    public static boolean isWet(final int wmoCode) {
        return wmoCode >= 51;
    }

    /** An arrow pointing the way the wind blows, from the direction it comes from. */
    public static String windDirection(final int degrees) {
        int index = ((degrees % 360) + 360) % 360;
        index = (index + 22) / 45;
        if (index >= WIND_FROM.length) {
            index = 0;
        }
        return String.valueOf(WIND_FROM[index]);
    }

    /** One of the 28 moon glyphs, by the moon's age in days. */
    public static String moonPhase(final int ageInDays) {
        final int age = Math.max(0, Math.min(27, ageInDays));
        return String.valueOf((char) (MOON_PHASE_FIRST + age));
    }

    /** The moon's age in days, from a phase angle in degrees. */
    public static int moonAge(final int phaseDegrees) {
        final int deg = ((phaseDegrees % 360) + 360) % 360;
        return (int) Math.round(deg / 360.0 * 27.0);
    }

    public static int beaufort(final float metresPerSecond) {
        int scale = 0;
        for (int i = 0; i < BEAUFORT.length; i++) {
            if (metresPerSecond >= BEAUFORT[i]) {
                scale = i + 1;
            }
        }
        return scale;
    }
}
