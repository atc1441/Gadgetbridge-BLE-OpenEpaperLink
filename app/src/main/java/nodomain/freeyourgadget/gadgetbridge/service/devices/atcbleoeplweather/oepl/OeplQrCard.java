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

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * A QR code with a caption, the OpenEPaperLink way: the caption centred along the top and the
 * code filling whatever is left, at medium error correction.
 * <p>
 * Each module is drawn as a whole number of pixels. An e-paper panel has no greys to smooth an
 * edge with, so a code at a fractional scale would simply not scan.
 */
public class OeplQrCard extends OeplCard {
    private static final Logger LOG = LoggerFactory.getLogger(OeplQrCard.class);

    private final String content;
    private final String caption;

    public OeplQrCard(final String content, final String caption) {
        super(true);
        this.content = content == null ? "" : content.trim();
        this.caption = caption == null ? "" : caption.trim();
    }

    @Override
    public boolean hasData() {
        return !content.isEmpty();
    }

    @Override
    protected void paint(final OeplCanvas c) {
        final float w = c.width;
        final float h = c.height;
        final boolean hasCaption = !caption.isEmpty() && h >= 72;
        final float captionSize = h * 0.16f;
        final float top = hasCaption ? captionSize * 1.25f : Math.max(1f, h * 0.02f);

        if (hasCaption) {
            c.textFitted(caption, w / 2f, h * 0.01f, captionSize, OeplCanvas.CENTER, c.ink,
                    w * 0.96f);
        }

        final ByteMatrix matrix = encode();
        if (matrix == null) {
            c.textFitted(content, w / 2f, h * 0.45f, h * 0.14f, OeplCanvas.CENTER, c.ink,
                    w * 0.94f);
            return;
        }
        final int modules = matrix.getWidth();
        final float room = Math.min(w, h - top);
        final int dot = Math.max(1, (int) (room / modules));
        final float size = dot * modules;
        final float left = (w - size) / 2f;
        final float topLeft = top + (h - top - size) / 2f;

        // a quiet zone in the paper colour, because the panel around it may not be white
        c.fillRect(Math.max(0, left - dot), Math.max(0, topLeft - dot),
                Math.min(w, size + dot * 2), Math.min(h, size + dot * 2), c.paper);
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                if (matrix.get(x, y) == 1) {
                    c.fillRect(left + x * dot, topLeft + y * dot, dot, dot, c.ink);
                }
            }
        }
    }

    private ByteMatrix encode() {
        try {
            final Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            final QRCode code = Encoder.encode(content, ErrorCorrectionLevel.M, hints);
            return code.getMatrix();
        } catch (final Exception e) {
            LOG.warn("could not encode {} as a QR code", content, e);
            return null;
        }
    }
}
