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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reading the settings structure a tag answers with - across firmwares that fill in different
 * parts of it.
 * <p>
 * The panel is the only thing in there a picture depends on. Everything behind it is a
 * setting, and a firmware that has never had one written leaves it at zero; treating that as
 * a broken answer is what used to leave such a display connected, understood as unusable, and
 * let go again without ever being sent a picture.
 */
public class OEPLDisplayConfigTest {

    @Test
    public void aSettingsBlockWithoutAnySettingsStillDescribesThePanel() {
        // the shape a firmware sends that fills in its panel and leaves the rest at zero
        final OEPLDisplayConfig c = OEPLDisplayConfig.parse(
                settings(false, 0x53, 224, 480, 3, true, 0));
        assertTrue("the panel is there, so the answer is usable", c.valid);
        assertEquals(0x53, c.hardwareType);
        assertEquals("width and height are swapped for BLE on this one", 224, c.bufferWidth);
        assertEquals(480, c.bufferHeight);
        assertEquals(4, c.colorCount());
        assertFalse("an interval of zero is not a setting", c.advertisingIntervalKnown);
        assertEquals(0, c.bleAdvertisingInterval);
    }

    @Test
    public void aFirmwareThatSetsAnIntervalIsReadWithTheRotationByte() {
        // the layout with the rotation byte in it, and an interval behind it that reads back
        // sanely - which is exactly what tells the two layouts apart
        final OEPLDisplayConfig c = OEPLDisplayConfig.parse(
                settings(true, 0x6D, 384, 184, 3, false, 2000));
        assertTrue(c.valid);
        assertEquals(0x6D, c.hardwareType);
        assertEquals(184, c.bufferWidth);
        assertEquals(384, c.bufferHeight);
        assertTrue(c.advertisingIntervalKnown);
        assertEquals(2000, c.bleAdvertisingInterval);
    }

    @Test
    public void nonsenseIsStillRejected() {
        assertFalse(OEPLDisplayConfig.parse(new byte[]{0x00, 0x05}).valid);
        assertFalse(OEPLDisplayConfig.parse(null).valid);
        // a panel of four pixels is not a panel
        assertFalse(OEPLDisplayConfig.parse(settings(false, 0x53, 2, 2, 3, false, 0)).valid);
        // and neither is one with more colours than the format can hold
        assertFalse(OEPLDisplayConfig.parse(settings(false, 0x53, 224, 480, 9, false, 0)).valid);
    }

    @Test
    public void theCatalogueAnswersForAFirmwareThatSaysNothing() {
        final OEPLDisplayConfig c = OEPLDisplayConfig.fromCatalogue(0x53);
        assertTrue("0x53 is in the catalogue, so its panel is known", c.valid);
        assertTrue(c.fromCatalogue);
        assertEquals("a 480 by 224 panel written a quarter turn round, so the short axis first",
                224, c.bufferWidth);
        assertEquals(480, c.bufferHeight);
        assertEquals(4, c.colorCount());

        final OEPLDisplayConfig known = OEPLDisplayConfig.fromCatalogue(0x6D);
        assertEquals(184, known.bufferWidth);
        assertEquals(384, known.bufferHeight);

        assertFalse("a type nobody has heard of cannot be filled in",
                OEPLDisplayConfig.fromCatalogue(0x1234).valid);
    }

    /**
     * The panel that is written the way it is read. Its buffer is not the short axis first, and
     * taking it for granted that every buffer is cost the 9.7" its picture: 960 by 672 pixels
     * packed as 672 wide and announced as 672 wide is the same bytes with every row a different
     * length than the panel lays out, which the display shows as a picture on its side. It only
     * happened on the connections where the display did not describe itself in time, so the
     * refresh after it looked right again.
     */
    @Test
    public void aPanelWrittenTheWayItIsReadKeepsItsLongAxisFirst() {
        final OEPLDisplayConfig big = OEPLDisplayConfig.fromCatalogue(0x2E);  // M3 9.7"
        assertTrue(big.valid);
        assertEquals(960, big.bufferWidth);
        assertEquals(672, big.bufferHeight);
        assertEquals("half a turn is still no turn of the axes", 180, big.autoRotationDegrees());

        final OEPLDisplayConfig gfsk = OEPLDisplayConfig.fromCatalogue(0xD5); // the same panel
        assertEquals(960, gfsk.bufferWidth);
        assertEquals(672, gfsk.bufferHeight);
        assertEquals(0, gfsk.autoRotationDegrees());
    }

    /**
     * And the rule behind both, over the whole catalogue: a card is drawn in the orientation it
     * is read in and then turned into the buffer, so the buffer is the panel turned by exactly
     * that much. A quarter turn transposes the axes; half a turn and no turn leave them alone.
     */
    @Test
    public void everyCatalogueBufferIsThePanelTurnedByItsOwnRotation() {
        for (final OEPLTagTypes.TagType type : OEPLTagTypes.all().values()) {
            final OEPLDisplayConfig c = OEPLDisplayConfig.fromCatalogue(type.hardwareType);
            if (!c.valid) {
                continue;   // a panel of a few pixels, like the thermometer's own display
            }
            final int degrees = type.rotationDegrees();
            final boolean quarterTurn = degrees == 90 || degrees == 270;
            final String what = type.name + " at " + degrees + "°";
            assertEquals(what, quarterTurn ? type.height : type.width, c.bufferWidth);
            assertEquals(what, quarterTurn ? type.width : type.height, c.bufferHeight);
        }
    }

    /**
     * The settings structure as a tag sends it back.
     *
     * @param withRotation      whether this firmware has the screen rotation byte in it
     * @param screenH           the long axis, as the structure calls it
     * @param screenW           the short one
     * @param swapped           the "width and height are inversed for BLE" flag
     * @param advertisingMillis the BLE wake-up interval, or 0 for a firmware that has none
     */
    private byte[] settings(final boolean withRotation, final int hardwareType,
                            final int screenH, final int screenW, final int colors,
                            final boolean swapped, final int advertisingMillis) {
        final ByteBuffer buf = ByteBuffer.allocate(120).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x00).put((byte) OEPLProtocol.CMD_GET_INFOS);
        buf.putInt(0x53535353);              // magic
        buf.putInt(1);                       // settings version
        buf.putInt(100);                     // declared length
        buf.putShort((short) hardwareType);
        buf.put((byte) 1);                   // screen available
        buf.putShort((short) 17);            // screen type
        buf.putShort((short) 0);             // screen functions
        buf.put((byte) (swapped ? 1 : 0));   // w/h inversed for BLE
        buf.putShort((short) 0);             // w/h inversed
        buf.putShort((short) screenH);
        buf.putShort((short) screenW);
        buf.putShort((short) 0);             // height offset
        buf.putShort((short) 0);             // width offset
        buf.put((byte) colors);
        buf.put((byte) 1);                   // black invert
        buf.put((byte) 0);                   // accent invert
        if (withRotation) {
            buf.put((byte) 0);               // screen rotation, newer firmwares only
        }
        buf.put((byte) 1);                   // OEPL enabled
        buf.putInt(600);                     // OEPL wake-up seconds
        buf.put((byte) 1);                   // BLE enabled
        buf.putInt(advertisingMillis);
        return buf.array();
    }
}
