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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Plays the part of the tag: takes the frames the encoder produces, checks them the way the
 * firmware does and reassembles the payload. Catches offset and checksum mistakes in the
 * transfer path without needing a display, which a Bluetooth-less emulator cannot provide.
 */
public class OEPLTransferTest {

    private static byte[] samplePayload(final int length) {
        // image-like: long runs of one value with structure on top
        final byte[] data = new byte[length];
        final Random random = new Random(1337);
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((i / 64) % 5 == 0 ? 0xff : (random.nextInt(16) == 0 ? 0x0f : 0x00));
        }
        return data;
    }

    /** Reassembles exactly as the firmware does: per block, per chunk, verifying both checksums. */
    private static byte[] transfer(final byte[] payload) {
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        final int blocks = (payload.length + OEPLProtocol.BLOCK_SIZE - 1) / OEPLProtocol.BLOCK_SIZE;

        for (int blockNr = 0; blockNr < blocks; blockNr++) {
            final byte[] block = OEPLProtocol.encodeBlock(payload, blockNr);
            assertTrue("block " + blockNr + " should exist", block != null);

            final int declaredLength = (block[0] & 0xff) | ((block[1] & 0xff) << 8);
            final int declaredSum = (block[2] & 0xff) | ((block[3] & 0xff) << 8);
            assertEquals("declared block length", block.length - 4, declaredLength);

            int dataSum = 0;
            for (int i = 4; i < block.length; i++) {
                dataSum += block[i] & 0xff;
            }
            assertEquals("block checksum", dataSum & 0xffff, declaredSum);

            // now the chunks that carry this block
            final ByteArrayOutputStream blockBuffer = new ByteArrayOutputStream();
            final int chunks = OEPLProtocol.chunkCount(block.length);
            for (int chunkNr = 0; chunkNr < chunks; chunkNr++) {
                final byte[] frame = OEPLProtocol.encodeChunk(block, blockNr, chunkNr);
                assertEquals("frame size", 235, frame.length);
                assertEquals("opcode", OEPLProtocol.CMD_BLOCK_DATA, frame[1] & 0xff);
                assertEquals("block id", blockNr, frame[3] & 0xff);
                assertEquals("chunk id", chunkNr, frame[4] & 0xff);

                // the firmware recomputes the checksum with the opcode and checksum byte cleared
                final byte[] copy = frame.clone();
                final int claimed = copy[2] & 0xff;
                copy[1] = 0;
                copy[2] = 0;
                int sum = 0;
                for (final byte b : copy) {
                    sum += b & 0xff;
                }
                assertEquals("chunk checksum for " + blockNr + "/" + chunkNr, sum & 0xff, claimed);

                final int offset = chunkNr * OEPLProtocol.CHUNK_PAYLOAD;
                final int length = Math.min(OEPLProtocol.CHUNK_PAYLOAD, block.length - offset);
                blockBuffer.write(frame, 5, length);
            }
            // the chunks carry the block header too, so the rebuilt block is four bytes longer
            final byte[] rebuiltBlock = blockBuffer.toByteArray();
            assertEquals("rebuilt block length", declaredLength + 4, rebuiltBlock.length);
            received.write(rebuiltBlock, 4, declaredLength);
        }
        return received.toByteArray();
    }

    @Test
    public void payloadSurvivesBlockAndChunkFraming() {
        // sizes around the block and chunk boundaries are where off by one errors live
        final int[] sizes = {1, 229, 230, 231, 4095, 4096, 4097, 8192, 17664, 30000, 96000};
        for (final int size : sizes) {
            final byte[] payload = samplePayload(size);
            assertArrayEquals("payload of " + size + " bytes", payload, transfer(payload));
        }
    }

    @Test
    public void announcementMatchesTheFirmwareStructure() {
        final byte[] frame = OEPLProtocol.availableData(0xdeadbeefL, 17664, OEPLProtocol.DATATYPE_ZLIB);
        assertEquals(19, frame.length);
        assertEquals(0x00, frame[0]);
        assertEquals(OEPLProtocol.CMD_AVAIL_DATA, frame[1] & 0xff);
        assertEquals(0xff, frame[2] & 0xff);

        final ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(3);
        assertEquals("dataVer", 0xdeadbeefL, buf.getLong());
        assertEquals("dataSize", 17664, buf.getInt());
        assertEquals("dataType", OEPLProtocol.DATATYPE_ZLIB, buf.get() & 0xff);
        assertEquals("dataTypeArgument", 0, buf.get());
        assertEquals("nextCheckIn", 0, buf.getShort());
    }

    @Test
    public void compressedPayloadInflatesWithTheFourKilobyteWindow() throws DataFormatException {
        final int width = 384;
        final int height = 184;
        final int planes = 2;
        final byte[] planeData = samplePayload(width * height / 8 * planes);

        final byte[] out = OEPLImageCodec.compressedPayload(planeData, width, height, planes);

        // the first four bytes carry the uncompressed size, the rest is the zlib stream
        final int declared = (out[0] & 0xff) | ((out[1] & 0xff) << 8)
                | ((out[2] & 0xff) << 16) | ((out[3] & 0xff) << 24);
        assertEquals("declared uncompressed size", 6 + planeData.length, declared);

        // a window larger than 4 KiB is refused by the tag, so the header has to claim exactly that
        assertEquals("CMF", 0x48, out[4] & 0xff);
        assertEquals("CINFO means 256 << 4 == 4096", 4096, 0x100 << ((out[4] & 0xff) >> 4));
        assertEquals("header checksum", 0, (((out[4] & 0xff) << 8) | (out[5] & 0xff)) % 31);

        final Inflater inflater = new Inflater();
        inflater.setInput(out, 4, out.length - 4);
        final byte[] inflated = new byte[declared];
        final int produced = inflater.inflate(inflated);
        inflater.end();

        assertEquals("inflated size", declared, produced);
        assertEquals("header length marker", 6, inflated[0]);
        assertEquals("width low", width & 0xff, inflated[1] & 0xff);
        assertEquals("width high", (width >> 8) & 0xff, inflated[2] & 0xff);
        assertEquals("height low", height & 0xff, inflated[3] & 0xff);
        assertEquals("height high", (height >> 8) & 0xff, inflated[4] & 0xff);
        assertEquals("plane count", planes, inflated[5]);
        for (int i = 0; i < planeData.length; i++) {
            assertEquals("plane byte " + i, planeData[i], inflated[6 + i]);
        }
        assertTrue("compression should pay off", out.length < planeData.length);
    }

    @Test
    public void advertisementDecodesTheRealCapture() {
        // captured from a tag: hw 0x6D, firmware 124, 3070 mV, 23 C
        final byte[] raw = {0x02, 0x6d, 0x00, 0x7c, 0x00, (byte) 0xc3, 0x01,
                (byte) 0xfe, 0x0b, 0x17, (byte) 0xdb};
        final OEPLUpdateScheduler.Advertisement a = OEPLUpdateScheduler.Advertisement.parse(raw);
        assertEquals(2, a.version);
        assertEquals(0x6d, a.hardwareType);
        assertEquals(124, a.firmwareVersion);
        assertEquals(3070, a.batteryMillivolts);
        assertEquals(23, a.temperature);
        assertTrue("this tag advertises compression support", a.supportsCompression());
        assertEquals(95, OEPLPrefs.batteryPercent(a.batteryMillivolts));
    }
}
