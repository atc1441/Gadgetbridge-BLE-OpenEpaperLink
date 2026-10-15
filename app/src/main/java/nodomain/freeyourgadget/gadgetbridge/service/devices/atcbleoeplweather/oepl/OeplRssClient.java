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

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches a news feed for the headline card, the way the OpenEPaperLink access point does with
 * its RSS content. Both RSS and Atom are accepted, since a feed URL rarely says which it is.
 * <p>
 * The result is cached so that a redraw does not mean another request; a feed that stops
 * answering leaves the last headlines on the display rather than blanking it.
 */
public final class OeplRssClient {
    private static final Logger LOG = LoggerFactory.getLogger(OeplRssClient.class);
    private static final int TIMEOUT_MILLIS = 15000;
    private static final long MAX_AGE_MILLIS = 20 * 60 * 1000L;
    private static final int MAX_ITEMS = 16;

    public static class Item {
        public final String title;
        public final String description;

        Item(final String title, final String description) {
            this.title = title;
            this.description = description;
        }
    }

    private static List<Item> cached = Collections.emptyList();
    private static long cachedAt;
    private static String cachedUrl = "";

    private OeplRssClient() {
    }

    public static synchronized List<Item> cached() {
        return cached;
    }

    public static synchronized void invalidate() {
        cached = Collections.emptyList();
        cachedAt = 0;
        cachedUrl = "";
    }

    /** @return true when headlines are available afterwards */
    public static boolean refreshIfStale(@Nullable final String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        final String feed = url.trim();
        synchronized (OeplRssClient.class) {
            if (!cached.isEmpty() && feed.equals(cachedUrl)
                    && System.currentTimeMillis() - cachedAt < MAX_AGE_MILLIS) {
                return true;
            }
        }
        try {
            final List<Item> items = parse(get(feed));
            synchronized (OeplRssClient.class) {
                if (!items.isEmpty()) {
                    cached = items;
                    cachedAt = System.currentTimeMillis();
                    cachedUrl = feed;
                }
                return !cached.isEmpty();
            }
        } catch (final Exception e) {
            LOG.warn("could not fetch the feed {}", feed, e);
            synchronized (OeplRssClient.class) {
                return !cached.isEmpty();
            }
        }
    }

    /** Split out from the request so the parsing can be tested on a canned feed. */
    public static List<Item> parse(final String xml) {
        final List<Item> items = new ArrayList<>();
        final Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        for (final Element element : doc.select("item, entry")) {
            final String title = text(element, "title");
            if (title.isEmpty()) {
                continue;
            }
            String description = text(element, "description");
            if (description.isEmpty()) {
                description = text(element, "summary");
            }
            items.add(new Item(title, description));
            if (items.size() >= MAX_ITEMS) {
                break;
            }
        }
        return items;
    }

    /** Feed text often carries markup and entities, neither of which belongs on a tag. */
    private static String text(final Element parent, final String tag) {
        final Element child = parent.selectFirst("> " + tag);
        if (child == null) {
            return "";
        }
        final String raw = child.text();
        return Jsoup.parse(Parser.unescapeEntities(raw, false)).text().trim();
    }

    private static String get(final String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, */*");
            final int status = connection.getResponseCode();
            if (status != 200) {
                throw new IllegalStateException("the feed answered " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) > 0) {
                    out.write(buf, 0, read);
                    if (out.size() > 512 * 1024) {
                        break; // headlines never need more than this
                    }
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
