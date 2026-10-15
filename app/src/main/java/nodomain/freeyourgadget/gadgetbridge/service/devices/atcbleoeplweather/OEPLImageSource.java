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
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The picture behind the two image cards: one chosen from the phone, one fetched from a URL.
 * <p>
 * Both are loaded at roughly the size the panel needs rather than at full resolution - a photo
 * from a phone camera is twenty times the pixels of the largest tag there is, and decoding one
 * in a broadcast receiver is how a process gets killed for using too much memory.
 */
public final class OEPLImageSource {
    private static final Logger LOG = LoggerFactory.getLogger(OEPLImageSource.class);

    /** How long a downloaded picture is used before it is fetched again. */
    private static final long MAX_AGE_MILLIS = 15 * 60 * 1000L;
    private static final int TIMEOUT_MILLIS = 15000;
    /** A downloaded file larger than this is refused rather than decoded. */
    private static final long MAX_BYTES = 12L * 1024 * 1024;

    private OEPLImageSource() {
    }

    /** Where a downloaded picture is kept, one file per display. */
    public static File downloadFile(final Context context, final String address) {
        final String safe = address == null ? "unknown" : address.replace(":", "");
        return new File(context.getFilesDir(), "oepl-image-" + safe + ".bin");
    }

    /**
     * The picture a card should draw, already scaled down towards the panel, or null when none
     * is configured or it could not be read.
     */
    @Nullable
    public static Bitmap load(final Context context, final SharedPreferences prefs,
                              final String contentId, final String address,
                              final int panelWidth, final int panelHeight) {
        try {
            if (CardContentFactory.IMAGE_URL.equals(contentId)) {
                final File file = downloadFile(context, address);
                return file.isFile() ? decode(file, panelWidth, panelHeight) : null;
            }
            final String uri = OEPLPrefs.imageUri(prefs);
            if (uri.isEmpty()) {
                return null;
            }
            return decode(context, Uri.parse(uri), panelWidth, panelHeight);
        } catch (final Exception e) {
            LOG.warn("could not load the picture for {}", address, e);
            return null;
        } catch (final OutOfMemoryError e) {
            LOG.warn("the picture for {} is too large to decode", address);
            return null;
        }
    }

    /**
     * Fetches the configured URL unless the copy on disk is still fresh. Runs on the content
     * sync's own thread with a deadline, like the news feed.
     *
     * @return true when a usable picture is on disk afterwards
     */
    public static boolean refreshIfStale(final Context context, final String address,
                                         final String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        final File file = downloadFile(context, address);
        if (file.isFile() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS) {
            return true;
        }
        HttpURLConnection connection = null;
        final File temporary = new File(file.getAbsolutePath() + ".part");
        try {
            connection = (HttpURLConnection) new URL(url.trim()).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "image/*");
            final int status = connection.getResponseCode();
            if (status / 100 != 2) {
                LOG.warn("the picture URL answered {}", status);
                return file.isFile();
            }
            long written = 0;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(temporary)) {
                final byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    written += read;
                    if (written > MAX_BYTES) {
                        LOG.warn("the picture at {} is larger than {} bytes", url, MAX_BYTES);
                        return file.isFile();
                    }
                    out.write(buffer, 0, read);
                }
            }
            // only replace the picture the panel is using once the new one is complete
            if (temporary.length() > 0) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                if (temporary.renameTo(file)) {
                    LOG.info("fetched {} bytes of picture from {}", written, url);
                    return true;
                }
            }
            return file.isFile();
        } catch (final Exception e) {
            LOG.warn("could not fetch the picture from {}", url, e);
            return file.isFile();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Throws the downloaded copy away, so the next run fetches it again. */
    public static void invalidate(final Context context, final String address) {
        //noinspection ResultOfMethodCallIgnored
        downloadFile(context, address).delete();
    }

    // ------------------------------------------------------------------ decoding

    @Nullable
    private static Bitmap decode(final File file, final int width, final int height) {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds, width, height);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    @Nullable
    private static Bitmap decode(final Context context, final Uri uri, final int width,
                                 final int height) throws Exception {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds, width, height);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    /**
     * Halves the picture until it is only a little larger than the panel. Staying above the
     * panel size matters: the scaling that follows looks far better coming down from more
     * pixels than up from fewer.
     */
    private static int sampleSize(final BitmapFactory.Options bounds, final int width,
                                  final int height) {
        int sample = 1;
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || width <= 0 || height <= 0) {
            return sample;
        }
        while (bounds.outWidth / (sample * 2) >= width * 2
                && bounds.outHeight / (sample * 2) >= height * 2) {
            sample *= 2;
        }
        return sample;
    }
}
