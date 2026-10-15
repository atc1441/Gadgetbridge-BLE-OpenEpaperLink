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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCardFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplRssClient;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;
import nodomain.freeyourgadget.gadgetbridge.util.UriHelper;

/**
 * An OpenEPaperLink based e-paper tag used as a weather display.
 * <p>
 * Unlike a watch, this device costs a lot of battery while a connection is up, so it is never
 * held open: {@link OEPLUpdateScheduler} brings the link up when there is something new to show,
 * the picture is pushed compressed, and the link is dropped again as soon as the tag acknowledges
 * it. Everything the panel reports passively - battery, temperature, firmware - is taken from the
 * advertisement instead, which costs no connection at all.
 */
public class ATCBLEOEPLWeatherDeviceSupport extends AbstractBTLESingleDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ATCBLEOEPLWeatherDeviceSupport.class);

    /** Rendering and deflating must not block the Bluetooth queue thread. */
    private final ScheduledExecutorService renderExecutor =
            Executors.newSingleThreadScheduledExecutor();
    /** Coalesces the replies arriving during startup into a single picture update. */
    private ScheduledFuture<?> pendingPush;

    private OEPLDisplayConfig config;
    private byte[] payload;
    private byte[] blockData;
    private int blockCount = -1;
    private int currentBlock = -1;
    private int currentChunk = -1;
    private long pendingCrc;
    private boolean transferIsFirmware;
    /** Set while the user explicitly pushed something, which must not be skipped as unchanged. */
    private boolean forced;
    /** True for the connection made right after pairing. */
    private boolean firstConnection;
    /** True while a configuration read the user asked for is outstanding. */
    private boolean configReadRequested;
    /**
     * Fires if a connection produces no finished transfer. A tag that answers the handshake and
     * then goes quiet used to leave the link half open and the schedule waiting for a completion
     * that never came, so the next picture only went out a whole interval later.
     */
    private ScheduledFuture<?> watchdog;
    /**
     * Whether the status bar currently carries our "sending" notification. It is posted as an
     * ongoing notification, which means nothing but us will ever take it away again.
     */
    private boolean progressShown;

    public ATCBLEOEPLWeatherDeviceSupport() {
        super(LOG);
        addSupportedService(OEPLProtocol.UUID_SERVICE);
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    /**
     * The very first connection has to stay up: Gadgetbridge only writes the device into its
     * database once it reports INITIALIZED, and dropping the link before that would leave the
     * display missing from the device list.
     */
    @Override
    public boolean connectFirstTime() {
        firstConnection = true;
        return super.connectFirstTime();
    }

    /** How long a connection may go without finishing a transfer before it is given up on. */
    private static final long WATCHDOG_MILLIS = 75_000L;

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        armWatchdog();
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        builder.requestMtu(512);
        builder.notify(OEPLProtocol.UUID_CHARACTERISTIC, true);

        // Nothing below here may keep the device out of the list. Gadgetbridge only writes it
        // into its database once it reports INITIALIZED, so that happens before the first
        // command: a tag that answers late, oddly, or not at all must still be usable.
        builder.setDeviceState(GBDevice.State.INITIALIZED);

        builder.sleep(200);
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC, OEPLProtocol.command(OEPLProtocol.CMD_GET_INFOS));
        builder.sleep(150);
        // the hardware description is the authoritative source for geometry and colour count
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.command(OEPLProtocol.CMD_READ_DYNAMIC_TYPE));
        return builder;
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt,
                                           final BluetoothGattCharacteristic characteristic,
                                           final byte[] value) {
        super.onCharacteristicChanged(gatt, characteristic, value);
        if (!OEPLProtocol.UUID_CHARACTERISTIC.equals(characteristic.getUuid())
                || value.length < 2 || value[0] != 0x00) {
            return false;
        }
        final int opcode = value[1] & 0xff;
        LOG.debug("tag -> host: {} ({} bytes)", StringUtils.bytesToHex(new byte[]{value[1]}), value.length);

        switch (opcode) {
            case OEPLProtocol.CMD_GET_INFOS:
                handleConfiguration(value);
                return true;
            case OEPLProtocol.ACK_DYNAMIC_READ:
                handleDynamicConfiguration(value);
                return true;
            case OEPLProtocol.ACK_DEBUG_INFO:
                handleDebugInfo(value);
                return true;
            case OEPLProtocol.REQUEST_BLOCK:
                startBlock(value);
                return true;
            case OEPLProtocol.ACK_BLOCK_PART:
                sendChunk(true);
                return true;
            case OEPLProtocol.ERR_BLOCK_PART:
                sendChunk(false);
                return true;
            case OEPLProtocol.ACK_TRANSFER:
            case OEPLProtocol.ACK_FIRMWARE_UPDATED:
                finishTransfer(true, false);
                return true;
            case OEPLProtocol.ACK_ALREADY_SHOWN:
                finishTransfer(true, true);
                return true;
            case OEPLProtocol.ERR_FILE_TOO_BIG:
                GB.toast(getContext().getString(R.string.oepl_weather_image_too_big),
                        Toast.LENGTH_LONG, GB.ERROR);
                finishTransfer(false, false);
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ configuration

    private void handleConfiguration(final byte[] value) {
        config = OEPLDisplayConfig.parse(value);
        LOG.info("display reports {}", config);
        OEPLStatusStore.recordConnect(getDevice().getAddress());
        OEPLStatusStore.recordConfig(getDevice().getAddress(), config);
        if (!config.valid) {
            LOG.error("could not make sense of the display configuration, raw {}",
                    StringUtils.bytesToHex(value));
        }

        // None of the bookkeeping below may stop the picture from being sent, so each piece is
        // isolated: an exception here used to leave the device stuck half initialized.
        try {
            OEPLPrefs.storePanel(getDevice().getAddress(), config);
        } catch (final Exception e) {
            LOG.error("could not store the panel configuration", e);
        }
        try {
            // keep the periodic refresh alive for as long as this device exists
            OEPLUpdateScheduler.reschedule(getContext(), getDevice());
        } catch (final Exception e) {
            LOG.error("could not schedule the periodic refresh", e);
        }
        try {
            final int millivolts = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress())
                    .getInt(OEPLPrefs.LAST_BATTERY_MV, -1);
            if (millivolts > 0) {
                reportBattery(millivolts);
            }
        } catch (final Exception e) {
            LOG.error("could not report the battery level", e);
        }

        // the hardware read follows right behind and is more accurate, so let that one decide
        pushUpdateIfNeeded();
    }

    // ------------------------------------------------------------------ the update itself

    /**
     * Renders the current weather and pushes it, unless the result is byte for byte what the
     * panel is already showing - in which case the radio goes quiet again immediately.
     * Rendering and compression happen off the Bluetooth queue thread.
     */
    private synchronized void pushUpdateIfNeeded() {
        if (config == null || !config.valid) {
            LOG.warn("no usable panel configuration, nothing to send");
            disconnectIfTransient();
            return;
        }
        // Several replies land in quick succession while connecting, and each of them could ask
        // for a picture. Coalesce them, otherwise a second transfer would overwrite the buffers
        // of the first one mid flight and wedge the connection.
        if (pendingPush != null) {
            pendingPush.cancel(false);
        }
        pendingPush = renderExecutor.schedule(this::renderAndSend, 250, TimeUnit.MILLISECONDS);
    }

    /** When the running transfer started, so the overview can report how long it took. */
    private long transferStarted;
    /** Size of the uncompressed planes, for the compression ratio in the overview. */
    private int transferRawBytes;

    private void renderAndSend() {
        if (payload != null) {
            LOG.debug("a transfer is already running, not starting another one");
            return;
        }
        final String address = getDevice().getAddress();
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);

        // The last word on freshness, and the only one every push goes through. The scheduler
        // normally has the forecast in hand before the radio even comes up, so this returns
        // immediately; it matters for the pushes that come from somewhere else entirely - the
        // user tapping connect, a notification, a changed setting - and after Android has
        // recycled the process and taken the cache with it.
        final boolean fresh = OEPLContentSync.freshen(prefs, OEPLContentSync.RENDER_BUDGET_MILLIS,
                address);
        final long weatherAt = OpenMeteoClient.cachedAtMillis();
        OEPLStatusStore.recordWeatherAge(address,
                weatherAt > 0 ? System.currentTimeMillis() - weatherAt : -1);
        if (!fresh && OEPLPrefs.useOpenMeteo(prefs)) {
            // drawn from what was at hand; come back shortly rather than in a whole interval
            LOG.info("content could not be refreshed in time, scheduling a follow-up");
            OEPLUpdateScheduler.requestFollowUp(getContext(), address, 3 * 60_000L);
        }

        final Bitmap card = renderCard(prefs);
        final byte[] encoded = encode(card, prefs);
        final long crc = crc32(encoded);

        // kept so the app can show what the display was last asked to show, and used as the
        // device's own icon
        OEPLStatusStore.storePreview(getContext(), address, card, rotation(prefs));

        if (!forced && crc == prefs.getLong(OEPLPrefs.LAST_IMAGE_CRC, 0L)) {
            LOG.info("rendered card is unchanged, skipping the transfer entirely");
            OEPLStatusStore.recordUnchanged(address);
            cancelWatchdog();
            OEPLUpdateScheduler.noteSuccess(getContext(), address);
            disconnectIfTransient();
            return;
        }
        pendingCrc = crc;
        transferIsFirmware = false;
        transferStarted = System.currentTimeMillis();
        transferRawBytes = config.planeSizeBytes() * config.planeCount();
        send(encoded, OEPLProtocol.DATATYPE_ZLIB, R.string.sending_image);
    }

    /**
     * The display describing its own hardware. This overrides whatever the stored settings blob
     * claimed, because it is what the firmware actually drives the panel with.
     */
    private void handleDynamicConfiguration(final byte[] value) {
        final OEPLDisplayConfig dynamic = OEPLDisplayConfig.parseDynamic(value);
        if (!dynamic.valid) {
            LOG.warn("dynamic hardware read came back unusable: {}", StringUtils.bytesToHex(value));
            if (configReadRequested) {
                configReadRequested = false;
                GB.toast(getContext().getString(R.string.oepl_weather_config_read_failed),
                        Toast.LENGTH_LONG, GB.WARN);
            }
            return;
        }
        LOG.info("dynamic hardware read: {}", dynamic);
        OEPLStatusStore.recordConfig(getDevice().getAddress(), dynamic);
        final boolean changed = config == null
                || config.bufferWidth != dynamic.bufferWidth
                || config.bufferHeight != dynamic.bufferHeight
                || config.colors != dynamic.colors
                || config.hardwareType != dynamic.hardwareType;
        if (config != null) {
            // the hardware read says nothing about these, so keep what the settings blob gave us
            dynamic.bleAdvertisingInterval = config.bleAdvertisingInterval;
            dynamic.oeplEnabled = config.oeplEnabled;
            dynamic.settingsVersion = config.settingsVersion;
        }
        config = dynamic;

        try {
            OEPLPrefs.storePanel(getDevice().getAddress(), config);
        } catch (final Exception e) {
            LOG.error("could not store the panel configuration", e);
        }

        if (configReadRequested) {
            configReadRequested = false;
            final OEPLTagTypes.TagType type = config.tagType();
            GB.toast(getContext().getString(R.string.oepl_weather_config_read,
                            type != null ? type.name : OEPLTagTypes.nameOf(config.hardwareType),
                            config.bufferWidth, config.bufferHeight, config.colors),
                    Toast.LENGTH_LONG, GB.INFO);
        }
        if (changed) {
            // geometry or palette moved, so whatever the panel shows no longer matches our cache
            GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).edit()
                    .putLong(OEPLPrefs.LAST_IMAGE_CRC, 0L).apply();
            forced = true;
            pushUpdateIfNeeded();
        }
    }

    /** Live battery voltage and panel temperature, far more current than the advertisement. */
    private void handleDebugInfo(final byte[] value) {
        if (value.length < 8) {
            return;
        }
        final int millivolts = ((value[2] & 0xff) << 8) | (value[3] & 0xff);
        final int panelTemperature = value[7]; // signed celsius, as read from the panel itself
        LOG.info("display reports {} mV, panel {} C", millivolts, panelTemperature);
        if (millivolts > 1000 && millivolts < 5000) {
            OEPLPrefs.storeAdvertisement(getDevice().getAddress(), millivolts, panelTemperature);
            reportBattery(millivolts);
        }
    }

    /** Rotation to apply, taking the OpenEPaperLink catalogue as the default. */
    private int rotation(final SharedPreferences prefs) {
        final int configured = OEPLPrefs.rotation(prefs);
        if (configured >= 0) {
            return configured;
        }
        return config != null ? config.autoRotationDegrees() : 0;
    }

    /** Whether to draw accents in yellow, likewise defaulting to the catalogue. */
    private boolean accentIsYellow(final SharedPreferences prefs) {
        final String mode = OEPLPrefs.accentMode(prefs);
        if ("yellow".equals(mode)) {
            return true;
        }
        if ("red".equals(mode)) {
            return false;
        }
        return config != null && config.autoAccentIsYellow();
    }

    @Nullable
    private WeatherSpec currentWeather() {
        final List<WeatherSpec> specs = Weather.getWeatherSpecs();
        return specs.isEmpty() ? null : specs.get(0);
    }

    private Bitmap renderCard(final SharedPreferences prefs) {
        final CardRenderer.Options options = new CardRenderer.Options();
        options.frame = OEPLPrefs.frame(prefs);
        options.accent = config.colorCount() <= 2
                ? OEPLImageCodec.BLACK
                : (accentIsYellow(prefs) ? OEPLImageCodec.YELLOW : OEPLImageCodec.RED);
        options.fahrenheit = GBApplication.getPrefs().getTemperatureUnit()
                == nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit.FAHRENHEIT;
        options.showFooter = OEPLPrefs.showFooter(prefs);
        options.batteryMillivolts = prefs.getInt(OEPLPrefs.LAST_BATTERY_MV, CardRenderer.NO_VALUE);
        options.panelTemperature = prefs.getInt(OEPLPrefs.LAST_PANEL_TEMPERATURE, CardRenderer.NO_VALUE);
        options.emptyHint = emptyHint(prefs);

        final CardContent content = CardContentFactory.create(getContext(), getDevice(), prefs);
        return new CardRenderer(options)
                .render(config.bufferWidth, config.bufferHeight, rotation(prefs), content);
    }

    /** Tells the user which source is missing instead of just leaving the card blank. */
    private String emptyHint(final SharedPreferences prefs) {
        final String content = OEPLPrefs.contentType(prefs);
        if (OeplCardFactory.RSS.equals(content)) {
            return getContext().getString(R.string.oepl_weather_no_feed);
        }
        if (OeplCardFactory.QR.equals(content)) {
            return getContext().getString(R.string.oepl_weather_no_qr);
        }
        if (CardContentFactory.IMAGE.equals(content)
                || CardContentFactory.IMAGE_URL.equals(content)) {
            return getContext().getString(R.string.oepl_image_none);
        }
        if (OeplCardFactory.CALENDAR.equals(content)
                || OeplCardFactory.CALENDAR_WEEK.equals(content)) {
            return getContext().getString(R.string.oepl_weather_no_calendar);
        }
        if (OeplCardFactory.needsWeather(content)) {
            return getContext().getString(R.string.oepl_weather_no_weather);
        }
        if (content.contains(CardContentFactory.NOTIFICATIONS)) {
            return getContext().getString(R.string.oepl_weather_no_notifications);
        }
        if (CardContentFactory.MUSIC.equals(content)) {
            return getContext().getString(R.string.oepl_weather_no_music);
        }
        if (content.contains(CardContentFactory.CALENDAR)) {
            return getContext().getString(R.string.oepl_weather_no_calendar);
        }
        if (CardContentFactory.TEXT.equals(content)) {
            return getContext().getString(R.string.oepl_weather_no_text);
        }
        return getContext().getString(R.string.oepl_weather_no_weather);
    }

    private byte[] encode(final Bitmap bitmap, final SharedPreferences prefs) {
        final boolean yellow = accentIsYellow(prefs);
        final int[] pixels = OEPLImageCodec.pixelsOf(bitmap);
        final int[] palette = OEPLImageCodec.palette(config.colorCount(), yellow);
        OEPLImageCodec.quantize(pixels, palette);
        final byte[] planes = OEPLImageCodec.packPlanes(pixels, config.bufferWidth,
                config.bufferHeight, config.colorCount(), yellow,
                OEPLPrefs.invertImage(prefs), false);
        return OEPLImageCodec.compressedPayload(planes, config.bufferWidth, config.bufferHeight,
                config.planeCount());
    }

    private void send(final byte[] data, final int dataType, final int progressText) {
        payload = data;
        currentBlock = -1;
        currentChunk = -1;
        blockCount = (payload.length + OEPLProtocol.BLOCK_SIZE - 1) / OEPLProtocol.BLOCK_SIZE;
        LOG.info("sending {} bytes as {} block(s), type 0x{}", payload.length, blockCount,
                Integer.toHexString(dataType));

        final TransactionBuilder builder = createTransactionBuilder("announce transfer");
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.availableData(pendingCrc, payload.length, dataType));
        builder.setBusyTask(progressText);
        builder.queue();
    }

    private void startBlock(final byte[] value) {
        if (payload == null || value.length < 12) {
            return;
        }
        armWatchdog(); // progress, so the clock starts over - a firmware image takes minutes
        currentBlock = value[11] & 0xff;
        currentChunk = 0;
        blockData = OEPLProtocol.encodeBlock(payload, currentBlock);
        if (blockData == null) {
            LOG.error("tag asked for block {} which is past the end of the payload", currentBlock);
            finishTransfer(false, false);
            return;
        }
        final TransactionBuilder builder = createTransactionBuilder("block " + currentBlock);
        // a short connection interval for the duration of the transfer; we disconnect right after
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.command(OEPLProtocol.CMD_CONNECTION_FAST));
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.encodeChunk(blockData, currentBlock, currentChunk));
        builder.setProgress(R.string.sending_image, true,
                blockCount > 0 ? (int) (100f * currentBlock / blockCount) : 0);
        progressShown = true;
        builder.queue();
    }

    private void sendChunk(final boolean advance) {
        if (blockData == null) {
            return;
        }
        if (advance) {
            currentChunk++;
        }
        final TransactionBuilder builder = createTransactionBuilder("chunk " + currentChunk);
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.encodeChunk(blockData, currentBlock, currentChunk));
        builder.queue();
    }

    private void finishTransfer(final boolean success, final boolean alreadyShown) {
        if (alreadyShown) {
            LOG.info("the tag already shows this picture");
        }
        if (alreadyShown) {
            OEPLStatusStore.recordUnchanged(getDevice().getAddress());
        }
        if (success && !alreadyShown && !transferIsFirmware) {
            GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).edit()
                    .putLong(OEPLPrefs.LAST_IMAGE_CRC, pendingCrc)
                    .putLong(OEPLPrefs.LAST_UPDATE_MILLIS, System.currentTimeMillis())
                    .apply();
            final SharedPreferences settings =
                    GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
            OEPLStatusStore.recordTransfer(getDevice().getAddress(), transferRawBytes,
                    payload != null ? payload.length : 0, true,
                    transferStarted > 0 ? System.currentTimeMillis() - transferStarted : 0,
                    OEPLPrefs.contentType(settings));
        } else if (!success) {
            OEPLStatusStore.recordFailure(getDevice().getAddress(), "failed");
        }
        cancelWatchdog();
        clearProgress();
        if (success) {
            OEPLUpdateScheduler.noteSuccess(getContext(), getDevice().getAddress());
        } else {
            OEPLUpdateScheduler.noteFailure(getContext(), getDevice().getAddress());
        }
        payload = null;
        blockData = null;
        blockCount = -1;
        currentBlock = -1;
        currentChunk = -1;
        forced = false;

        if (getDevice().isBusy()) {
            getDevice().unsetBusyTask();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
        disconnectIfTransient();
    }

    /**
     * Starts the clock on this connection. A tag that answers the handshake and then stops
     * talking - a weak link, a flat cell, a firmware that lost its place mid transfer - would
     * otherwise hold the link open until Android eventually tore it down, with the schedule
     * still believing an update was in progress.
     */
    private synchronized void armWatchdog() {
        cancelWatchdog();
        watchdog = renderExecutor.schedule(() -> {
            LOG.warn("the display stopped answering, giving this connection up");
            clearProgress();
            final String address = getDevice().getAddress();
            OEPLStatusStore.recordFailure(address, "no answer");
            synchronized (ATCBLEOEPLWeatherDeviceSupport.this) {
                payload = null;
                blockData = null;
                blockCount = -1;
                currentBlock = -1;
                currentChunk = -1;
            }
            if (getDevice().isBusy()) {
                getDevice().unsetBusyTask();
                getDevice().sendDeviceUpdateIntent(getContext());
            }
            OEPLUpdateScheduler.noteFailure(getContext(), address);
            try {
                GBApplication.deviceService(getDevice()).disconnect();
            } catch (final Exception e) {
                LOG.debug("could not drop the link", e);
            }
        }, WATCHDOG_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Takes the "sending picture" notification out of the status bar. The progress notification
     * is ongoing, so it stays there until somebody removes it - and nothing else does. Every way
     * out of a transfer passes through here: the end of a transfer, the watchdog, and the
     * teardown that follows a link dropping halfway through.
     */
    private void clearProgress() {
        if (!progressShown) {
            return;
        }
        progressShown = false;
        try {
            GB.removeNotification(GB.NOTIFICATION_ID_INSTALL, getContext());
            final Intent done = new Intent(GB.ACTION_SET_PROGRESS_BAR);
            done.putExtra(GB.PROGRESS_BAR_PROGRESS, 100);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(done);
        } catch (final Exception e) {
            LOG.debug("could not clear the progress notification", e);
        }
    }

    private synchronized void cancelWatchdog() {
        if (watchdog != null) {
            watchdog.cancel(false);
            watchdog = null;
        }
    }

    /** Drops the link unless the user asked to stay connected, for example while experimenting. */
    private void disconnectIfTransient() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (OEPLPrefs.stayConnected(prefs)) {
            return;
        }
        // The device is written to the database as soon as it reports INITIALIZED, which happens
        // long before this point, so even the very first connection can be dropped. A short
        // grace period lets the pairing screen finish what it is doing.
        final long graceMillis = firstConnection ? 2500L : 0L;
        firstConnection = false;
        LOG.info("work done, dropping the connection in {} ms to save the tag battery", graceMillis);
        renderExecutor.schedule(() -> {
            try {
                GBApplication.deviceService(getDevice()).disconnect();
            } catch (final Exception e) {
                LOG.error("could not disconnect", e);
            }
        }, graceMillis, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ Gadgetbridge hooks

    // The phone hands these over as they happen. The display is asleep most of the time, so
    // they are kept until the next connection rather than acted on immediately.
    @Override
    public void onNotification(@NonNull final nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec spec) {
        PhoneStateStore.remember(spec);
        liveUpdate(CardContentFactory.NOTIFICATIONS);
    }

    @Override
    public void onDeleteNotification(final int id) {
        PhoneStateStore.forget(id);
    }

    @Override
    public void onSetMusicInfo(@NonNull final nodomain.freeyourgadget.gadgetbridge.model.MusicSpec spec) {
        PhoneStateStore.setMusic(spec);
        liveUpdate(CardContentFactory.MUSIC);
    }

    @Override
    public void onSetMusicState(@NonNull final nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec state) {
        PhoneStateStore.setMusicState(state);
        liveUpdate(CardContentFactory.MUSIC);
    }

    /**
     * Pushes right away, but only when the card actually shows what just changed. A display
     * set to the calendar has no reason to redraw because a chat message arrived.
     */
    private void liveUpdate(final String relevantContent) {
        try {
            final SharedPreferences prefs =
                    GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
            final String content = OEPLPrefs.contentType(prefs);
            if (!content.contains(relevantContent)) {
                return;
            }
            if (getDevice().isInitialized()) {
                pushUpdateIfNeeded();
            } else {
                OEPLUpdateScheduler.requestLiveUpdate(getContext(), getDevice());
            }
        } catch (final Exception e) {
            LOG.error("could not trigger a live update", e);
        }
    }

    @Override
    public void onSendWeather() {
        // fresh weather is the whole point of this device, so go and show it
        forced = false;
        if (getDevice().isInitialized()) {
            pushUpdateIfNeeded();
        } else {
            OEPLUpdateScheduler.requestUpdateNow(getContext(), getDevice());
        }
    }

    @Override
    public void onSetTime() {
        if (config == null) {
            return;
        }
        final long now = System.currentTimeMillis() / 1000L;
        final byte[] arg = new byte[]{
                (byte) ((now >> 24) & 0xff), (byte) ((now >> 16) & 0xff),
                (byte) ((now >> 8) & 0xff), (byte) (now & 0xff)};
        final TransactionBuilder builder = createTransactionBuilder("set time");
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.command(OEPLProtocol.CMD_SET_UNIXTIME, arg));
        builder.queue();
    }

    @Override
    public void onSendConfiguration(final String config) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());

        if (OEPLPrefs.PREF_READ_CONFIG.equals(config)) {
            requestConfigurationRead();
            return;
        }

        if (OEPLPrefs.PREF_UPDATE_INTERVAL.equals(config)
                || OEPLPrefs.PREF_OPPORTUNISTIC.equals(config)
                || OEPLPrefs.PREF_REQUIRE_ADVERTISEMENT.equals(config)) {
            // the interval decides how often the controller reports what it heard, so the
            // background listening has to be registered again with the new figure
            OEPLUpdateScheduler.reschedule(getContext(), getDevice());
            return;
        }
        // Everything that only changes what we draw: redraw and send, without touching the tag.
        // The content choice itself lives here, so this list has to grow with every new setting.
        if (PRESENTATION_PREFS.contains(config)) {
            redrawNow(prefs, config);
            return;
        }

        try {
            final TransactionBuilder builder = performInitialized("configuration " + config);
            switch (config) {
                case OEPLPrefs.PREF_MODEL: {
                    final int type = OEPLPrefs.model(prefs);
                    if (type < 0) {
                        return; // automatic, nothing to push to the display
                    }
                    builder.write(OEPLProtocol.UUID_CHARACTERISTIC, OEPLProtocol.command(
                            OEPLProtocol.CMD_SET_TYPE,
                            (byte) ((type >> 8) & 0xff), (byte) (type & 0xff)));
                    break;
                }
                case OEPLPrefs.PREF_ADV_INTERVAL: {
                    final int interval = Integer.parseInt(
                            prefs.getString(OEPLPrefs.PREF_ADV_INTERVAL, "3200"));
                    builder.write(OEPLProtocol.UUID_CHARACTERISTIC, OEPLProtocol.command(
                            OEPLProtocol.CMD_BLE_PERIOD,
                            (byte) ((interval >> 8) & 0xff), (byte) (interval & 0xff)));
                    break;
                }
                case OEPLPrefs.PREF_OEPL_PROTOCOL: {
                    final boolean enable = prefs.getBoolean(OEPLPrefs.PREF_OEPL_PROTOCOL, false);
                    builder.write(OEPLProtocol.UUID_CHARACTERISTIC, OEPLProtocol.command(
                            enable ? OEPLProtocol.CMD_OEPL_ENABLE : OEPLProtocol.CMD_OEPL_DISABLE));
                    break;
                }
                default:
                    return;
            }
            builder.queue();
        } catch (final IOException e) {
            GB.toast("Error setting configuration", Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    /** Settings that change the picture but say nothing to the display itself. */
    private static final java.util.Set<String> PRESENTATION_PREFS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    OEPLPrefs.PREF_CONTENT,
                    OEPLPrefs.PREF_CUSTOM_TEXT,
                    OEPLPrefs.PREF_CALENDAR_EVENTS,
                    OEPLPrefs.PREF_NOTIFICATION_COUNT,
                    OEPLPrefs.PREF_FORECAST_HOURS,
                    OEPLPrefs.PREF_FORECAST_DAYS,
                    OEPLPrefs.PREF_FRAME,
                    OEPLPrefs.PREF_ROTATION,
                    OEPLPrefs.PREF_ACCENT_COLOR,
                    OEPLPrefs.PREF_SHOW_FOOTER,
                    OEPLPrefs.PREF_INVERT,
                    OEPLPrefs.PREF_WEATHER_SOURCE,
                    OEPLPrefs.PREF_WEATHER_LOCATION,
                    OEPLPrefs.PREF_WIND_UNIT,
                    OEPLPrefs.PREF_SHOW_SUN,
                    OEPLPrefs.PREF_COUNTER_TARGET,
                    OEPLPrefs.PREF_COUNTER_THRESHOLD,
                    OEPLPrefs.PREF_COUNTER_CAPTION,
                    OEPLPrefs.PREF_RSS_URL,
                    OEPLPrefs.PREF_RSS_TITLE,
                    OEPLPrefs.PREF_RSS_SUMMARY,
                    OEPLPrefs.PREF_QR_CONTENT,
                    OEPLPrefs.PREF_QR_TITLE,
                    OEPLPrefs.PREF_CALENDAR_TITLE,
                    OEPLPrefs.PREF_CALENDAR_DAYS));

    /**
     * Redraws and sends after a setting changed. The display is disconnected almost all of the
     * time, so this normally goes through the scheduler rather than straight down the link.
     */
    private void redrawNow(final SharedPreferences prefs, final String changedKey) {
        // the cached picture is stale, otherwise the identical-image check would skip the push
        prefs.edit().putLong(OEPLPrefs.LAST_IMAGE_CRC, 0L).apply();
        forced = true;
        if (OEPLPrefs.PREF_WEATHER_SOURCE.equals(changedKey)
                || OEPLPrefs.PREF_WEATHER_LOCATION.equals(changedKey)) {
            OpenMeteoClient.invalidate();
        }
        if (OEPLPrefs.PREF_RSS_URL.equals(changedKey)) {
            OeplRssClient.invalidate();
        }
        if (getDevice().isInitialized() && config != null && config.valid) {
            pushUpdateIfNeeded();
        } else {
            OEPLUpdateScheduler.requestUpdateNow(getContext(), getDevice());
        }
    }

    /** Asks the display to describe its hardware; also reachable from the device settings. */
    public void requestConfigurationRead() {
        configReadRequested = true;
        try {
            final TransactionBuilder builder = performInitialized("read hardware configuration");
            builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                    OEPLProtocol.command(OEPLProtocol.CMD_GET_INFOS));
            builder.sleep(150);
            builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                    OEPLProtocol.command(OEPLProtocol.CMD_READ_DYNAMIC_TYPE));
            builder.queue();
        } catch (final IOException e) {
            configReadRequested = false;
            GB.toast(getContext().getString(R.string.oepl_weather_config_read_failed),
                    Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    /** Lets the user push an arbitrary picture or a firmware image, as with the plain tag. */
    @Override
    public void onInstallApp(final Uri uri, @NonNull final Bundle options) {
        try {
            final UriHelper uriHelper = UriHelper.get(uri, getContext());
            if (uriHelper.getFileName().endsWith(".bin")) {
                try (InputStream in = new BufferedInputStream(uriHelper.openInputStream())) {
                    final byte[] firmware = FileUtils.readAll(in, 262144);
                    if (firmware.length < 12 || firmware[8] != 0x4b || firmware[9] != 0x4e
                            || firmware[10] != 0x4c || firmware[11] != 0x54) {
                        LOG.error("that .bin does not carry the firmware magic");
                        return;
                    }
                    transferIsFirmware = true;
                    pendingCrc = 123; // the tag does not check this for firmware
                    forced = true;
                    send(firmware, OEPLProtocol.DATATYPE_FIRMWARE, R.string.updating_firmware);
                }
                return;
            }
            if (config == null || !config.valid) {
                LOG.error("panel geometry unknown, cannot scale the picture");
                return;
            }
            final Bitmap source = MediaStore.Images.Media.getBitmap(
                    GBApplication.getContext().getContentResolver(), uri);
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
            final Bitmap fitted = letterbox(source, config.bufferWidth, config.bufferHeight,
                    rotation(prefs));

            final boolean yellow = accentIsYellow(prefs);
            final int[] pixels = OEPLImageCodec.pixelsOf(fitted);
            final int[] palette = OEPLImageCodec.palette(config.colorCount(), yellow);
            // a photograph benefits from dithering, unlike the rendered card
            OEPLImageCodec.dither(pixels, palette, config.bufferWidth, config.bufferHeight);
            final byte[] planes = OEPLImageCodec.packPlanes(pixels, config.bufferWidth,
                    config.bufferHeight, config.colorCount(), yellow,
                    OEPLPrefs.invertImage(prefs), false);
            final byte[] encoded = OEPLImageCodec.compressedPayload(planes, config.bufferWidth,
                    config.bufferHeight, config.planeCount());

            transferIsFirmware = false;
            forced = true;
            pendingCrc = crc32(encoded);
            send(encoded, OEPLProtocol.DATATYPE_ZLIB, R.string.sending_image);
        } catch (final IOException e) {
            LOG.error("could not read the file to install", e);
        }
    }

    private static Bitmap letterbox(final Bitmap source, final int width, final int height,
                                    final int rotation) {
        final boolean quarterTurn = rotation == 90 || rotation == 270;
        final int w = quarterTurn ? height : width;
        final int h = quarterTurn ? width : height;

        final Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);

        final float scale = Math.min((float) w / source.getWidth(), (float) h / source.getHeight());
        final int dw = Math.round(source.getWidth() * scale);
        final int dh = Math.round(source.getHeight() * scale);
        final Rect dst = new Rect((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, dst, paint);

        if (rotation == 0) {
            return out;
        }
        final android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(out, 0, 0, w, h, matrix, true);
    }

    /** Publishes what the last advertisement told us, so the card shows a current reading. */
    public void reportBattery(final int millivolts) {
        final GBDeviceEventBatteryInfo event = new GBDeviceEventBatteryInfo();
        event.state = BatteryState.BATTERY_NORMAL;
        event.level = OEPLPrefs.batteryPercent(millivolts);
        event.voltage = millivolts / 1000f;
        evaluateGBDeviceEvent(event);
    }

    @Override
    public void dispose() {
        cancelWatchdog();
        // a link that drops halfway through never reaches finishTransfer
        clearProgress();
        if (pendingPush != null) {
            pendingPush.cancel(false);
        }
        renderExecutor.shutdownNow();
        super.dispose();
    }

    private static long crc32(final byte[] data) {
        final CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
}
