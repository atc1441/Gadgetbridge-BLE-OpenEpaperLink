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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Framing for the single read/write/notify characteristic the tag exposes. Every frame starts
 * with a 16 bit big endian command id; everything after it is command specific.
 */
public final class OEPLProtocol {
    public static final UUID UUID_SERVICE = UUID.fromString("00001337-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHARACTERISTIC = UUID.fromString("00001337-0000-1000-8000-00805f9b34fb");

    /** Manufacturer id the tag advertises under. */
    public static final int MANUFACTURER_ID = 0x1337;

    // commands we send
    public static final int CMD_LED = 1;
    public static final int CMD_CONNECTION_FAST = 2;
    public static final int CMD_CONNECTION_SLOW = 3;
    public static final int CMD_SET_TYPE = 4;
    public static final int CMD_GET_INFOS = 5;
    public static final int CMD_OEPL_ENABLE = 6;
    public static final int CMD_OEPL_DISABLE = 7;
    public static final int CMD_BLE_PERIOD = 8;
    public static final int CMD_SET_UNIXTIME = 11;
    public static final int CMD_DISABLE_UNIXTIME = 12;
    public static final int CMD_READ_DYNAMIC_TYPE = 17;
    public static final int CMD_READ_DEBUG = 22;
    /** The twelve byte blink pattern; see {@link #ledPatternArgument}. */
    public static final int CMD_SET_LED = 21;
    public static final int CMD_DEEPSLEEP = 20;
    public static final int CMD_REBOOT = 23;
    public static final int CMD_AVAIL_DATA = 100;
    public static final int CMD_BLOCK_DATA = 101;

    // notifications the tag sends back
    public static final int ACK_COMMAND = 99;
    public static final int ERR_BLOCK_PART = 196;
    public static final int ACK_BLOCK_PART = 197;
    public static final int REQUEST_BLOCK = 198;
    public static final int ACK_TRANSFER = 199;
    public static final int ACK_ALREADY_SHOWN = 200;
    public static final int ACK_FIRMWARE_UPDATED = 201;
    public static final int ACK_DYNAMIC_READ = 205;
    public static final int ERR_FILE_TOO_BIG = 208;
    public static final int ACK_DEBUG_INFO = 209;
    /**
     * The answer to a command the display does not know - the only one that does not start
     * with a zero byte. An older firmware answers this to anything that was added after it.
     */
    public static final int ERR_UNKNOWN_COMMAND = 0xffff;

    public static final int DATATYPE_FIRMWARE = 0x03;
    public static final int DATATYPE_RAW_1BPP = 0x20;
    public static final int DATATYPE_RAW_2BPP = 0x21;
    public static final int DATATYPE_ZLIB = 0x30;

    public static final int BLOCK_SIZE = 4096;
    public static final int CHUNK_PAYLOAD = 230;
    private static final int CHUNK_FRAME = 235;

    private OEPLProtocol() {
    }

    public static byte[] command(final int cmd, final byte... payload) {
        final byte[] frame = new byte[2 + payload.length];
        frame[0] = (byte) ((cmd >> 8) & 0xff);
        frame[1] = (byte) (cmd & 0xff);
        System.arraycopy(payload, 0, frame, 2, payload.length);
        return frame;
    }

    /**
     * The argument of {@link #CMD_SET_UNIXTIME}: the local time as a unix timestamp, lowest
     * byte first. The tag has no time zone of its own, so local is what it wants; and it reads
     * the four bytes back low to high, which is the opposite of every other number in this
     * protocol.
     */
    public static byte[] unixTimeArgument(final long nowMillis, final java.util.TimeZone zone) {
        final long local = (nowMillis + zone.getOffset(nowMillis)) / 1000L;
        return new byte[]{
                (byte) (local & 0xff), (byte) ((local >> 8) & 0xff),
                (byte) ((local >> 16) & 0xff), (byte) ((local >> 24) & 0xff)};
    }

    /** Announces a pending transfer; the tag answers with {@link #REQUEST_BLOCK} per block. */
    public static byte[] availableData(final long dataVersion, final int size, final int dataType) {
        final ByteBuffer buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x00).put((byte) CMD_AVAIL_DATA);
        buf.put((byte) 0xff);                 // AvailDataInfo.checksum, not verified over BLE
        buf.putLong(dataVersion & 0xffffffffL); // dataVer, doubles as the tag side image cache key
        buf.putInt(size);                     // dataSize
        buf.put((byte) dataType);             // dataType
        buf.put((byte) 0x00);                 // dataTypeArgument, selects the drawing LUT
        buf.putShort((short) 0);              // nextCheckIn, only meaningful in OEPL mode
        return buf.array();
    }

    /** Wraps one 4 KiB slice of the payload with its length and checksum. */
    public static byte[] encodeBlock(final byte[] payload, final int blockNr) {
        final int offset = blockNr * BLOCK_SIZE;
        if (offset >= payload.length) {
            return null;
        }
        final int length = Math.min(BLOCK_SIZE, payload.length - offset);
        final byte[] block = new byte[length + 4];
        System.arraycopy(payload, offset, block, 4, length);
        final int checksum = sum(block); // header still zeroed, so this covers the data only
        block[0] = (byte) (length & 0xff);
        block[1] = (byte) ((length >> 8) & 0xff);
        block[2] = (byte) (checksum & 0xff);
        block[3] = (byte) ((checksum >> 8) & 0xff);
        return block;
    }

    /** One BLE sized slice of a block. Always a full frame, zero padded at the end. */
    public static byte[] encodeChunk(final byte[] block, final int blockNr, final int chunkNr) {
        final int offset = chunkNr * CHUNK_PAYLOAD;
        final int length = Math.max(0, Math.min(CHUNK_PAYLOAD, block.length - offset));
        final byte[] frame = new byte[CHUNK_FRAME];
        frame[3] = (byte) blockNr;
        frame[4] = (byte) chunkNr;
        if (length > 0) {
            System.arraycopy(block, offset, frame, 5, length);
        }
        frame[2] = (byte) sum(frame); // checksum is taken before the opcode byte is filled in
        frame[1] = (byte) CMD_BLOCK_DATA;
        return frame;
    }

    public static int chunkCount(final int blockLength) {
        return (blockLength + CHUNK_PAYLOAD - 1) / CHUNK_PAYLOAD;
    }

    /**
     * The colours the tag's three legged LED can make, as the single byte the blink pattern
     * carries: three bits of red, three of green, two of blue.
     */
    public static final int LED_RED = 0b111_000_00;
    public static final int LED_GREEN = 0b000_111_00;
    public static final int LED_BLUE = 0b000_000_11;
    public static final int LED_YELLOW = LED_RED | LED_GREEN;
    public static final int LED_CYAN = LED_GREEN | LED_BLUE;
    public static final int LED_MAGENTA = LED_RED | LED_BLUE;
    public static final int LED_WHITE = LED_RED | LED_GREEN | LED_BLUE;

    /**
     * The argument of {@link #CMD_SET_LED}: a blink pattern the tag runs on its own.
     * <p>
     * This is the only way to make the LED do something that outlives the connection. A plain
     * "switch it on" is cleared the moment the tag goes back to sleep, which is a second or two
     * after the link drops - while a pattern keeps the tag awake until it has finished and
     * switches the LED off itself afterwards. That is also why no pattern offered here runs
     * forever: it would be a tag that never sleeps again and a coin cell gone by the morning.
     * <p>
     * The twelve bytes are three groups of a colour, a count and a gap, then how often the
     * three repeat. Only the first group carries a colour here; the second is a dark pause, so
     * that a run of flashes reads as a run rather than as a flicker.
     */
    public static byte[] ledPatternArgument(final int color, final int flashes,
                                            final int repeats) {
        return ledPatternArgument(color, flashes, repeats, FULL_BRIGHTNESS);
    }

    /** The brightest the tag's LED goes; the firmware counts the sixteen steps from one. */
    public static final int FULL_BRIGHTNESS = 16;

    /**
     * As above, dimmable. The top half of the first byte is the brightness and the bottom half
     * the mode, so the two travel together - and a display on a bedside table is a good reason
     * not to send every blink at full.
     */
    public static byte[] ledPatternArgument(final int color, final int flashes,
                                            final int repeats, final int brightness) {
        final int level = Math.max(1, Math.min(FULL_BRIGHTNESS, brightness)) - 1;
        final int group = (Math.max(1, Math.min(15, flashes)) & 0x0f) | (2 << 4);
        return new byte[]{
                (byte) ((level << 4) | 0x01), // how bright, and that this is a pattern
                (byte) color, (byte) group, 0,
                0, (byte) ((8 << 4) | 1), 0, // a dark group: eight ticks of nothing
                0, 0, 0,                     // and a third group that is over at once
                (byte) (Math.max(1, Math.min(MOST_ROUNDS, repeats)) - 1),
                0};
    }

    // ---------------------------------------------------------------- the pattern in full
    //
    // What the twelve bytes are, read off the firmware that plays them. The tag runs the
    // pattern on a tick of a tenth of a second and walks three groups in turn: on each tick a
    // group counts down its gap, and when the gap is up it makes one flash in its own colour.
    // After its flashes are used up the next group takes over, and when the third is done the
    // three start again until the rounds are used up. That is the whole machine - three
    // colours, three counts, three gaps - and it is what lets one display blink twice red,
    // once blue and stay dark for a second while the one beside it does something else.
    //
    //   0   brightness in the top half, the mode in the bottom (1 = play a pattern)
    //   1   colour of the first group        2   its gap in the top half, its flashes in the
    //   3   unused by this firmware              bottom half
    //   4   colour of the second group       5   likewise
    //   6   unused
    //   7   colour of the third group        8   likewise
    //   9   unused
    //  10   rounds, counted from zero       11   spare
    //
    // A colour is three bits of red, three of green and two of blue, which the tag renders by
    // how much of each flash it leaves that leg switched on.

    /** Groups in a pattern: the firmware walks exactly three of them. */
    public static final int GROUPS = 3;
    /** The largest flash count and gap a group can hold: both are four bit fields. */
    public static final int MOST_PER_GROUP = 15;

    /**
     * The most rounds that may be asked for.
     * <p>
     * The byte holds one less than the number of rounds, and the firmware reads 255 rounds -
     * byte 254 - as "keep going until somebody stops you". That is a tag that never sleeps
     * again and a coin cell gone by the morning, so the figure is kept well short of it.
     */
    public static final int MOST_ROUNDS = 200;

    /** A colour byte: three bits of red, three of green, two of blue. */
    public static int ledColor(final int red, final int green, final int blue) {
        return ((Math.max(0, Math.min(7, red)) & 0x07) << 5)
                | ((Math.max(0, Math.min(7, green)) & 0x07) << 2)
                | (Math.max(0, Math.min(3, blue)) & 0x03);
    }

    public static int redOf(final int color) {
        return (color >> 5) & 0x07;
    }

    public static int greenOf(final int color) {
        return (color >> 2) & 0x07;
    }

    public static int blueOf(final int color) {
        return color & 0x03;
    }

    /**
     * Builds the twelve bytes from three groups and the two figures that apply to all of them.
     *
     * @param colors  one colour byte per group
     * @param flashes how many flashes each group makes
     * @param gaps    tenths of a second between the flashes of each group
     */
    public static byte[] ledPattern(final int[] colors, final int[] flashes, final int[] gaps,
                                    final int repeats, final int brightness) {
        final int level = Math.max(1, Math.min(FULL_BRIGHTNESS, brightness)) - 1;
        final byte[] pattern = new byte[12];
        pattern[0] = (byte) ((level << 4) | 0x01);
        for (int group = 0; group < GROUPS; group++) {
            final int count = Math.max(0, Math.min(MOST_PER_GROUP, flashes[group]));
            final int gap = Math.max(0, Math.min(MOST_PER_GROUP, gaps[group]));
            pattern[1 + group * 3] = (byte) (colors[group] & 0xff);
            pattern[2 + group * 3] = (byte) ((gap << 4) | count);
        }
        pattern[10] = (byte) (Math.max(1, Math.min(MOST_ROUNDS, repeats)) - 1);
        return pattern;
    }

    /** How long a pattern takes, give or take the tenth of a second it is counted in. */
    public static float ledPatternSeconds(final byte[] pattern) {
        if (pattern == null || pattern.length != 12) {
            return 0f;
        }
        int ticks = 0;
        for (int group = 0; group < GROUPS; group++) {
            final int shape = pattern[2 + group * 3] & 0xff;
            // a group with no flashes still makes one, and no gap means one flash per tick
            ticks += Math.max(1, shape & 0x0f) * Math.max(1, (shape >> 4) & 0x0f);
        }
        return ticks * ((pattern[10] & 0xff) + 1) / 10f;
    }

    /** The pattern as it is written into the settings, so that twelve bytes survive a restart. */
    public static String ledPatternToHex(final byte[] pattern) {
        final StringBuilder hex = new StringBuilder(24);
        for (final byte value : pattern) {
            hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    /** Reads one back, or null for anything that is not a pattern this firmware would play. */
    @androidx.annotation.Nullable
    public static byte[] ledPatternFromHex(final String hex) {
        if (hex == null || hex.length() != 24) {
            return null;
        }
        final byte[] pattern = new byte[12];
        for (int i = 0; i < 12; i++) {
            try {
                pattern[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            } catch (final NumberFormatException e) {
                return null;
            }
        }
        // the bottom half of the first byte says what the tag should do with the rest, and
        // anything but "play a pattern" would switch the LED off instead of blinking it
        if ((pattern[0] & 0x0f) != 0x01) {
            return null;
        }
        return (pattern[10] & 0xff) == 254 ? null : pattern;
    }

    /**
     * Whether a file could be firmware for one of these displays.
     * <p>
     * This matters more than most checks in here: a display writes what it is handed into its
     * own program memory and runs it, it verifies nothing itself, and a display left with
     * rubbish in there has to be opened up and flashed over wires. So the question is asked
     * before a single byte goes out.
     * <p>
     * Three shapes are accepted, which are the three the browser based uploader accepts as
     * well. The ordinary one carries the mark the Telink toolchain leaves eight bytes in and
     * states its own length a little further on; the other two are the images for the displays
     * built around a different radio, which are recognised by the word their own loader looks
     * for. A file that is none of the three is refused rather than tried.
     */
    public static boolean looksLikeFirmware(final byte[] firmware) {
        if (firmware == null || firmware.length < 64) {
            return false;
        }
        if (firmware[8] == 'K' && firmware[9] == 'N' && firmware[10] == 'L'
                && firmware[11] == 'T') {
            final long claimed = (firmware[24] & 0xffL) | ((firmware[25] & 0xffL) << 8)
                    | ((firmware[26] & 0xffL) << 16) | ((firmware[27] & 0xffL) << 24);
            if (claimed == firmware.length) {
                return true;
            }
        }
        if ((firmware[4] & 0xff) == 0x93 && (firmware[5] & 0xff) == 0x27) {
            return true;
        }
        return firmware.length > 440 && (firmware[438] & 0xff) == 0x93
                && (firmware[439] & 0xff) == 0x27;
    }

    private static int sum(final byte[] buffer) {
        int sum = 0;
        for (final byte b : buffer) {
            sum += b & 0xff;
        }
        return sum;
    }
}
