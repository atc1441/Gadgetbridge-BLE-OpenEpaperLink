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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The pictograms the dashboard cards need and the Weather Icons face does not have: a battery,
 * a door, a lamp, a calendar sheet. They are drawn as shapes rather than set from a font,
 * which has three reasons.
 * <p>
 * A panel is a few hundred pixels across, so an icon ends up twelve to twenty pixels high and
 * then goes through a quantiser that knows two or three colours - at that size a font glyph
 * loses its thin strokes while a shape whose stroke width is derived from its size keeps them.
 * A shape also needs no second icon face bundled for the few symbols that are not weather. And
 * it stays readable on a two colour tag, where a halftoned glyph turns into a smudge.
 * <p>
 * Every shape is described inside a square of the given size centred on a point, so a caller
 * reserves room the same way for all of them.
 */
public final class OeplGlyphs {

    public enum Glyph {
        /** A battery, which draws its charge as the filled part when a level is given. */
        BATTERY,
        PHONE,
        WATCH,
        CALENDAR,
        BELL,
        MUSIC,
        LAMP,
        PLUG,
        BOLT,
        DOOR,
        LOCK,
        HOUSE,
        CLOCK,
        PERSON,
        /** A filled disc, for a state that is on. */
        ON,
        /** An open ring, for a state that is off. */
        OFF,
        /** Anything we have no shape for. */
        DOT,
    }

    /** No level: a battery is drawn as an empty shell. */
    public static final int NO_LEVEL = -1;

    private OeplGlyphs() {
    }

    /**
     * How wide this glyph ends up at a given height, so that a layout can reserve the room
     * beside it. Most are square, the battery and the calendar are wider than they are high.
     */
    public static float width(final Glyph glyph, final float size) {
        switch (glyph) {
            case BATTERY:
                return size * 1.15f;
            case BOLT:
            case PLUG:
                return size * 0.7f;
            default:
                return size;
        }
    }

    public static void draw(final OeplCanvas c, final Glyph glyph, final float cx,
                            final float cy, final float size, final int color) {
        draw(c, glyph, cx, cy, size, color, NO_LEVEL);
    }

    /**
     * Draws one pictogram centred on a point.
     *
     * @param size  the height of the square the shape is drawn inside
     * @param level 0 to 100 to fill a battery to that charge, {@link #NO_LEVEL} otherwise
     */
    public static void draw(final OeplCanvas c, final Glyph glyph, final float cx,
                            final float cy, final float size, final int color, final int level) {
        if (glyph == null || size < 4f) {
            return;
        }
        final Canvas canvas = c.canvas;
        final float stroke = Math.max(1f, Math.round(size * 0.1f));
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(stroke);
        line.setStrokeCap(Paint.Cap.SQUARE);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);

        final float half = size / 2f;
        final float left = cx - half;
        final float top = cy - half;

        switch (glyph) {
            case BATTERY: {
                // lying on its side, the way a battery symbol is read, and a nub on the right
                final float w = size * 1.15f;
                final float h = size * 0.62f;
                final float x = cx - w / 2f;
                final float y = cy - h / 2f;
                final float body = w - stroke * 1.6f;
                canvas.drawRect(new RectF(x, y, x + body, y + h), line);
                canvas.drawRect(new RectF(x + body + stroke * 0.4f, cy - h * 0.22f,
                        x + w, cy + h * 0.22f), fill);
                if (level >= 0) {
                    final float inset = stroke;
                    final float room = body - inset * 2f;
                    final float filled = room * Math.min(100, Math.max(0, level)) / 100f;
                    if (filled > 0.5f) {
                        canvas.drawRect(new RectF(x + inset, y + inset, x + inset + filled,
                                y + h - inset), fill);
                    }
                }
                break;
            }
            case PHONE: {
                final float w = size * 0.62f;
                final RectF body = new RectF(cx - w / 2f, top, cx + w / 2f, top + size);
                canvas.drawRoundRect(body, size * 0.12f, size * 0.12f, line);
                // the earpiece, which is what makes it a telephone and not a slab
                canvas.drawRect(new RectF(cx - w * 0.18f, top + stroke * 1.4f,
                        cx + w * 0.18f, top + stroke * 2.2f), fill);
                break;
            }
            case WATCH: {
                final float w = size * 0.74f;
                final float h = size * 0.66f;
                canvas.drawRoundRect(new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f,
                        cy + h / 2f), size * 0.14f, size * 0.14f, line);
                canvas.drawRect(new RectF(cx - w * 0.22f, top, cx + w * 0.22f, cy - h / 2f),
                        fill);
                canvas.drawRect(new RectF(cx - w * 0.22f, cy + h / 2f, cx + w * 0.22f,
                        top + size), fill);
                break;
            }
            case CALENDAR: {
                final float w = size * 1.02f;
                final float x = cx - w / 2f;
                final float y = top + size * 0.12f;
                final float h = size * 0.88f;
                canvas.drawRect(new RectF(x, y, x + w, y + h), line);
                // the band across the top, which is the whole difference to a plain box
                canvas.drawRect(new RectF(x, y, x + w, y + h * 0.28f), fill);
                // the two rings above it
                canvas.drawRect(new RectF(x + w * 0.22f, top, x + w * 0.22f + stroke,
                        y + stroke * 0.5f), fill);
                canvas.drawRect(new RectF(x + w * 0.72f, top, x + w * 0.72f + stroke,
                        y + stroke * 0.5f), fill);
                break;
            }
            case BELL: {
                final Path path = new Path();
                final float w = size * 0.78f;
                path.moveTo(cx - w / 2f, cy + size * 0.22f);
                path.cubicTo(cx - w * 0.36f, cy + size * 0.1f, cx - w * 0.42f, top,
                        cx, top + size * 0.06f);
                path.cubicTo(cx + w * 0.42f, top, cx + w * 0.36f, cy + size * 0.1f,
                        cx + w / 2f, cy + size * 0.22f);
                path.close();
                canvas.drawPath(path, line);
                canvas.drawCircle(cx, cy + size * 0.36f, stroke * 0.9f, fill);
                break;
            }
            case MUSIC: {
                final float stem = size * 0.62f;
                canvas.drawRect(new RectF(cx + size * 0.1f, top, cx + size * 0.1f + stroke,
                        top + stem), fill);
                canvas.drawCircle(cx - size * 0.08f, top + stem, size * 0.2f, fill);
                // the flag, so it reads as a note rather than as a lollipop
                canvas.drawRect(new RectF(cx + size * 0.1f, top, cx + size * 0.36f,
                        top + stroke), fill);
                break;
            }
            case LAMP: {
                canvas.drawCircle(cx, cy - size * 0.1f, size * 0.33f, line);
                canvas.drawRect(new RectF(cx - size * 0.16f, cy + size * 0.24f,
                        cx + size * 0.16f, cy + size * 0.32f), fill);
                canvas.drawRect(new RectF(cx - size * 0.12f, cy + size * 0.4f,
                        cx + size * 0.12f, cy + size * 0.48f), fill);
                break;
            }
            case PLUG: {
                final float w = size * 0.6f;
                canvas.drawRect(new RectF(cx - w / 2f, cy - size * 0.1f, cx + w / 2f,
                        cy + size * 0.34f), line);
                canvas.drawRect(new RectF(cx - w * 0.3f, top, cx - w * 0.3f + stroke,
                        cy - size * 0.1f), fill);
                canvas.drawRect(new RectF(cx + w * 0.3f - stroke, top, cx + w * 0.3f,
                        cy - size * 0.1f), fill);
                break;
            }
            case BOLT: {
                final Path path = new Path();
                path.moveTo(cx + size * 0.22f, top);
                path.lineTo(cx - size * 0.26f, cy + size * 0.06f);
                path.lineTo(cx + size * 0.02f, cy + size * 0.06f);
                path.lineTo(cx - size * 0.14f, top + size);
                path.lineTo(cx + size * 0.3f, cy - size * 0.04f);
                path.lineTo(cx + size * 0.02f, cy - size * 0.04f);
                path.close();
                canvas.drawPath(path, fill);
                break;
            }
            case DOOR: {
                final float w = size * 0.7f;
                canvas.drawRect(new RectF(cx - w / 2f, top, cx + w / 2f, top + size), line);
                canvas.drawCircle(cx + w * 0.26f, cy, Math.max(1f, stroke * 0.8f), fill);
                break;
            }
            case LOCK: {
                final float w = size * 0.8f;
                canvas.drawRect(new RectF(cx - w / 2f, cy - size * 0.06f, cx + w / 2f,
                        top + size), line);
                final RectF shackle = new RectF(cx - w * 0.28f, top + stroke * 0.5f,
                        cx + w * 0.28f, cy + size * 0.1f);
                canvas.drawArc(shackle, 180f, 180f, false, line);
                break;
            }
            case HOUSE: {
                final Path roof = new Path();
                roof.moveTo(cx - half, cy);
                roof.lineTo(cx, top + size * 0.08f);
                roof.lineTo(cx + half, cy);
                canvas.drawPath(roof, line);
                canvas.drawRect(new RectF(cx - size * 0.32f, cy, cx + size * 0.32f,
                        top + size - stroke * 0.5f), line);
                break;
            }
            case CLOCK: {
                canvas.drawCircle(cx, cy, half - stroke * 0.5f, line);
                canvas.drawRect(new RectF(cx - stroke * 0.5f, cy - size * 0.3f,
                        cx + stroke * 0.5f, cy + stroke * 0.5f), fill);
                canvas.drawRect(new RectF(cx - stroke * 0.5f, cy - stroke * 0.5f,
                        cx + size * 0.24f, cy + stroke * 0.5f), fill);
                break;
            }
            case PERSON: {
                canvas.drawCircle(cx, top + size * 0.24f, size * 0.22f, line);
                final RectF shoulders = new RectF(cx - size * 0.38f, cy,
                        cx + size * 0.38f, top + size * 1.3f);
                canvas.drawArc(shoulders, 180f, 180f, false, line);
                break;
            }
            case ON:
                canvas.drawCircle(cx, cy, half * 0.8f, fill);
                break;
            case OFF:
                canvas.drawCircle(cx, cy, half * 0.8f - stroke * 0.5f, line);
                break;
            case DOT:
            default:
                canvas.drawCircle(cx, cy, Math.max(1.5f, size * 0.22f), fill);
                break;
        }
    }

    /**
     * The shape that fits a Home Assistant entity, guessed from its id and the device class
     * the instance reports. Home Assistant picks its icons the same way; the names below are
     * the domains and device classes it uses, so an entity gets the symbol its owner expects
     * without anything having to be configured.
     */
    public static Glyph forEntity(final String entityId, final String deviceClass,
                                  final String unit) {
        final String id = entityId == null ? "" : entityId.toLowerCase(java.util.Locale.ROOT);
        final String kind = deviceClass == null ? "" : deviceClass.toLowerCase(java.util.Locale.ROOT);
        final String domain = id.contains(".") ? id.substring(0, id.indexOf('.')) : id;

        switch (kind) {
            case "battery":
                return Glyph.BATTERY;
            case "door":
            case "garage_door":
            case "opening":
                return Glyph.DOOR;
            case "window":
                return Glyph.DOOR;
            case "lock":
                return Glyph.LOCK;
            case "power":
            case "energy":
            case "current":
            case "voltage":
                return Glyph.BOLT;
            case "outlet":
                return Glyph.PLUG;
            case "timestamp":
            case "duration":
                return Glyph.CLOCK;
            case "presence":
            case "occupancy":
            case "motion":
                return Glyph.PERSON;
            default:
                break;
        }
        switch (domain) {
            case "light":
                return Glyph.LAMP;
            case "switch":
            case "fan":
                return Glyph.PLUG;
            case "lock":
                return Glyph.LOCK;
            case "calendar":
                return Glyph.CALENDAR;
            case "media_player":
                return Glyph.MUSIC;
            case "person":
            case "device_tracker":
                return Glyph.PERSON;
            case "zone":
                return Glyph.HOUSE;
            case "binary_sensor":
                return Glyph.ON;
            default:
                break;
        }
        if ("%".equals(unit) && id.contains("batter")) {
            return Glyph.BATTERY;
        }
        if (unit != null && (unit.endsWith("W") || unit.endsWith("Wh"))) {
            return Glyph.BOLT;
        }
        return Glyph.DOT;
    }
}
