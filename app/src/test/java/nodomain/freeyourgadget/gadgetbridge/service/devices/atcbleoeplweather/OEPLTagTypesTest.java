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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OEPLTagTypesTest {

    @Test
    public void catalogueCoversTheKnownHardware() {
        assertEquals("tag types bundled from OpenEPaperLink", 109, OEPLTagTypes.all().size());
        for (final OEPLTagTypes.TagType t : OEPLTagTypes.all().values()) {
            assertTrue("name for 0x" + Integer.toHexString(t.hardwareType), !t.name.isEmpty());
            assertTrue("rotation of 0x" + Integer.toHexString(t.hardwareType),
                    t.rotateBuffer >= 0 && t.rotateBuffer <= 3);
            // we rotate the card into the buffer, the catalogue states the other direction
            assertEquals("rotation degrees", ((4 - t.rotateBuffer % 4) % 4) * 90, t.rotationDegrees());
        }
    }

    @Test
    public void resolvesTheHardwareOfTheTestDisplay() {
        // 0x6D is the panel the implementation was developed against
        final OEPLTagTypes.TagType t = OEPLTagTypes.byHardwareType(0x6D);
        assertNotNull(t);
        assertEquals(384, t.width);
        assertEquals(184, t.height);
        assertEquals(1, t.rotateBuffer);
        assertTrue("0x6D is a four colour panel", t.hasRed && t.hasYellow);
        assertFalse("with red present the accent is not yellow", t.accentIsYellow());
    }

    @Test
    public void distinguishesYellowOnlyPanels() {
        // 0x60 is the same size but black/white/yellow, which the firmware cannot tell us apart
        final OEPLTagTypes.TagType yellow = OEPLTagTypes.byHardwareType(0x60);
        assertNotNull(yellow);
        assertTrue("0x60 has yellow", yellow.hasYellow);
        assertFalse("0x60 has no red", yellow.hasRed);
        assertTrue("so its accent must be drawn yellow", yellow.accentIsYellow());

        final OEPLTagTypes.TagType red = OEPLTagTypes.byHardwareType(0x56);
        assertNotNull(red);
        assertTrue(red.hasRed);
        assertFalse(red.accentIsYellow());
    }

    @Test
    public void unknownHardwareDoesNotThrow() {
        assertNull(OEPLTagTypes.byHardwareType(0xFFFF));
        assertTrue(OEPLTagTypes.nameOf(0xFFFF).startsWith("Unknown"));
    }

    @Test
    public void dynamicConfigurationDecodesItsFieldOrder() {
        // built in the order the vendor web tool reads it back
        final ByteBuffer buf = ByteBuffer.allocate(2 + 64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x00).put((byte) OEPLProtocol.ACK_DYNAMIC_READ);
        buf.putShort((short) 17);     // screen type
        buf.putShort((short) 0x6D);   // hardware type
        buf.putShort((short) 5);      // screen functions
        buf.put((byte) 0);            // w/h inversed for BLE
        buf.putShort((short) 0);      // w/h inversed
        buf.putShort((short) 384);    // screen_h, the long axis
        buf.putShort((short) 184);    // screen_w
        buf.putShort((short) 0);      // height offset
        buf.putShort((short) 0);      // width offset
        buf.putShort((short) 3);      // colours
        buf.putShort((short) 1);      // black invert
        buf.putShort((short) 0);      // accent invert
        buf.putInt(0x0800);           // epd pinout
        buf.putInt(0x0810);           // led pinout
        buf.putInt(0);                // nfc pinout
        buf.putInt(0x0820);           // flash pinout
        buf.putShort((short) 3);      // adc pin
        buf.putShort((short) 7);      // uart pin

        final OEPLDisplayConfig c = OEPLDisplayConfig.parseDynamic(buf.array());
        assertTrue(c.valid);
        assertTrue(c.fromDynamicRead);
        assertEquals(17, c.screenType);
        assertEquals(0x6D, c.hardwareType);
        // screen_w is 184 and screen_h is 384, so the packed buffer is the portrait one
        assertEquals("buffer width is screen_w", 184, c.bufferWidth);
        assertEquals("buffer height is screen_h", 384, c.bufferHeight);
        assertEquals(3, c.colors);
        assertEquals("four colours in total", 4, c.colorCount());
        assertTrue("this panel inverts its black plane", c.blackInvert);
        assertFalse(c.secondInvert);

        // and the catalogue fills in what the display itself cannot report
        // rotatebuffer 1 means the buffer is a quarter turn ahead of the panel, so the card
        // has to go three quarters the other way
        assertEquals("buffer rotation from the catalogue", 270, c.autoRotationDegrees());
        assertFalse("0x6D carries red, so the accent stays red", c.autoAccentIsYellow());
    }

    /**
     * What a scanned tag is called in the discovery list. The panel size belongs in that line:
     * two tags of the same family differ by it, and it is what decides what can be shown.
     */
    @Test
    public void describesATagByItsModelAndPanel() {
        assertEquals("M3 2.9\" BWRY · 384×168", OEPLTagTypes.describe(0x2B));
        assertEquals("M2 1.54\" · 152×152", OEPLTagTypes.describe(0x00));
        // a type the catalogue has never heard of still has to say something useful
        assertEquals("Unknown (0xF7)", OEPLTagTypes.describe(0xF7));
    }

    /**
     * The diagonal, which the catalogue keeps inside the model name. It is what places a
     * display for a reader - 2.9 inches or 7.5 is the difference between a shelf label and a
     * poster - so the card title asks the catalogue for it.
     */
    @Test
    public void readsTheDiagonalOutOfTheModelName() {
        assertEquals("2.9\"", OEPLTagTypes.inchesOf(0x2B));   // M3 2.9" BWRY
        assertEquals("1.54\"", OEPLTagTypes.inchesOf(0x00));  // M2 1.54"
        assertEquals("5.85\"", OEPLTagTypes.inchesOf(0x41));  // M3 5.85"
        assertEquals("2.2\"", OEPLTagTypes.inchesOf(0x45));   // M3 2.2 Lite"

        // the half of the catalogue that writes its decimals with a comma, which used to be
        // read as the number behind it: three and a half inches announced as five
        assertEquals("3.5\"", OEPLTagTypes.inchesOf(0x6D));   // HS BWRY 3,5"
        assertEquals("2.9\"", OEPLTagTypes.inchesOf(0x6E));   // HS BWRY 2,9"
        // ...and a round number keeps no trailing zeros, which read as another digit
        assertEquals("2\"", OEPLTagTypes.inchesOf(0x6C));     // HS BWRY 2,00"
        assertEquals("2\"", OEPLTagTypes.inchesOf(0x67));     // HS 2.00" BWY
        // not every entry is a panel with a size in its name
        assertNull(OEPLTagTypes.inchesOf(0xFA));             // ConfigMode
        assertNull(OEPLTagTypes.inchesOf(0xE0));             // TFT 320x172
        assertNull(OEPLTagTypes.inchesOf(0x1234));           // not in the catalogue at all

        // whatever the name says, the two have to agree: a name with an inch mark reports one
        for (final OEPLTagTypes.TagType type : OEPLTagTypes.all().values()) {
            if (!type.name.contains("\"")) {
                continue;
            }
            final String inches = type.inches();
            assertNotNull(type.name + " names a diagonal but does not report one", inches);
            // and it has to be a plausible one: no panel sold is a tenth of an inch or forty
            final float value = Float.parseFloat(inches.substring(0, inches.length() - 1));
            assertTrue(type.name + " reported " + inches, value >= 1f && value <= 32f);
        }
    }
}
