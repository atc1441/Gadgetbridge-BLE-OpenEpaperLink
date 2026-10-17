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
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
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
    /** Set once this display has been told to go quiet, so nothing else is offered to it. */
    private boolean goingToSleep;
    /** Set while this connection exists to carry a command rather than a picture. */
    private boolean commandSession;
    /** Whether the hang-up after a command has already happened. */
    private boolean commandSessionOver;
    /** What this connection was asked to carry, while an answer to it is still outstanding. */
    @Nullable
    private String awaitedCommand;
    /** Set once the display has answered that it took the command. */
    private boolean commandAcknowledged;
    /** Set when the command makes the display leave by itself, so we must not pull the link. */
    private boolean commandEndsConnection;
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
        // the support object outlives a single connection, so what the last one was doing may
        // not decide what this one is for
        commandSession = false;
        goingToSleep = false;
        commandEndsConnection = false;
        awaitedCommand = null;
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        builder.requestMtu(512);
        builder.notify(OEPLProtocol.UUID_CHARACTERISTIC, true);

        // Nothing below here may keep the device out of the list. Gadgetbridge only writes it
        // into its database once it reports INITIALIZED, so that happens before the first
        // command: a tag that answers late, oddly, or not at all must still be usable.
        builder.setDeviceState(GBDevice.State.INITIALIZED);

        builder.sleep(200);

        // A connection opened for a command belongs to that command and nothing else: no
        // hardware read, no picture, and the link is dropped as soon as it has been sent. A
        // restart is the clearest case - the display reboots the moment it has the command, so
        // a picture queued behind it is a transfer into a display that is no longer there, and
        // what came back was a failed update rather than a restart that worked.
        final SharedPreferences own =
                GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (OEPLFirmware.pending(own)) {
            // A firmware takes minutes and the whole link; everything else on this connection
            // would be in its way. Started from inside the queue so that the notifications are
            // already switched on when the first block is asked for.
            commandSession = true;
            commandSessionOver = true; // the transfer itself ends this connection
            builder.run(this::startArmedFirmware);
            return builder;
        }
        if (OEPLActions.pending(own)) {
            commandSession = true;
            commandSessionOver = false;
            performPendingActions(builder);
            return builder;
        }

        builder.write(OEPLProtocol.UUID_CHARACTERISTIC, OEPLProtocol.command(OEPLProtocol.CMD_GET_INFOS));
        builder.sleep(150);
        // the hardware description is the authoritative source for geometry and colour count
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.command(OEPLProtocol.CMD_READ_DYNAMIC_TYPE));

        // ...and if neither question is answered in a way we can use, the catalogue answers
        // instead. Not every firmware implements the configuration commands - the two that
        // matter for a picture are the transfer ones - and a display that connects but is
        // never sent anything is the worst of both worlds.
        renderExecutor.schedule(this::drawFromCatalogueIfSilent, 4, TimeUnit.SECONDS);
        return builder;
    }

    /**
     * Carries out whatever the display was asked to do while it was out of reach, and then ends
     * the connection.
     * <p>
     * Nothing else happens on this connection; see {@link #initializeDevice}. The hang-up is
     * asked for twice over, once at the end of the queue and once on a timer, because the two
     * commands that make the display leave can take the rest of the queue with them - and a
     * connection nobody ends is a tag held awake and a watchdog that later reports a failure
     * for a command that worked.
     */
    private void performPendingActions(final TransactionBuilder builder) {
        final List<String> actions = OEPLActions.drain(getDevice().getAddress());
        if (actions.isEmpty()) {
            return;
        }
        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        for (final String action : actions) {
            LOG.info("carrying out {} on {}", action, getDevice().getAddress());
            switch (action) {
                case OEPLActions.BLINK:
                    builder.write(OEPLProtocol.UUID_CHARACTERISTIC, OEPLProtocol.command(
                            OEPLProtocol.CMD_SET_LED,
                            OEPLProtocol.ledPatternArgument(OEPLPrefs.ledColor(prefs), 3, 5)));
                    break;
                case OEPLActions.REBOOT:
                    builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                            OEPLProtocol.command(OEPLProtocol.CMD_REBOOT));
                    commandEndsConnection = true;
                    break;
                case OEPLActions.SLEEP:
                    // the display stops answering after this, so nothing may follow it
                    builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                            OEPLProtocol.command(OEPLProtocol.CMD_DEEPSLEEP));
                    goingToSleep = true;
                    commandEndsConnection = true;
                    break;
                default:
                    break;
            }
            builder.sleep(200);
        }
        final String what = actions.size() == 1 ? actions.get(0)
                : android.text.TextUtils.join(", ", actions);
        awaitedCommand = what;
        commandAcknowledged = false;

        if (commandEndsConnection) {
            // A restart and a sleep are the display's own to carry out, and it leaves the
            // connection by doing so. Dropping the link ourselves first is what can hold them
            // up: as long as the connection is up the display's loop runs through without
            // sleeping and acts on the command at once, while a display back on its own goes
            // into the sleep it keeps between two advertisements and only gets round to the
            // command when it next wakes. So the link is left to the display, with a long stop
            // behind it for the case where nothing happens at all.
            renderExecutor.schedule(() -> commandSessionOver(what), 10, TimeUnit.SECONDS);
            return;
        }
        builder.run(() -> commandSessionOver(what));
        renderExecutor.schedule(() -> commandSessionOver(what), 6, TimeUnit.SECONDS);
    }

    /**
     * Writes the firmware this display was handed, if it is still there to be written.
     * <p>
     * The attempt is counted before anything is sent. A display that takes the file and dies
     * halfway through it would otherwise be handed the same two hundred kilobytes on every
     * connection for the rest of the week; after a few goes the note is dropped and the display
     * keeps the firmware it has, which is what it does anyway until a transfer completes.
     */
    private void startArmedFirmware() {
        final String address = getDevice().getAddress();
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        final String uri = OEPLFirmware.armed(prefs);
        if (uri == null) {
            return;
        }
        final int attempt = OEPLFirmware.noteAttempt(address);
        byte[] firmware = null;
        try (InputStream in = new BufferedInputStream(getContext().getContentResolver()
                .openInputStream(Uri.parse(uri)))) {
            firmware = FileUtils.readAll(in, 262144);
        } catch (final Exception e) {
            LOG.error("could not read the firmware {} for {}", uri, address, e);
        }
        if (firmware == null || !OEPLProtocol.looksLikeFirmware(firmware)) {
            // nothing about this gets better by trying again
            OEPLFirmware.done(address);
            OEPLStatusStore.recordFailure(address, "firmware file unusable");
            disconnectIfTransient();
            return;
        }
        LOG.info("writing {} bytes of firmware to {}, attempt {} of {}",
                firmware.length, address, attempt, OEPLFirmware.MAX_TRIES);
        transferIsFirmware = true;
        pendingCrc = 123; // the display does not check this for firmware
        forced = true;
        send(firmware, OEPLProtocol.DATATYPE_FIRMWARE, R.string.updating_firmware);
    }

    /**
     * The display answering that it has no such command.
     * <p>
     * Which is a fact about that display and not a fault: every one of these commands was added
     * to the firmware at some point, and the restart is the newest of them. A display still
     * running an older build answers this to it, and the way out is the firmware update on the
     * same screen.
     */
    private synchronized boolean refuseCommand() {
        if (awaitedCommand == null) {
            LOG.info("{} refused something we sent", getDevice().getAddress());
            return false;
        }
        LOG.warn("{} does not know the command {}", getDevice().getAddress(), awaitedCommand);
        GB.toast(getContext().getString(R.string.oepl_tools_unknown_command),
                Toast.LENGTH_LONG, GB.WARN);
        final String what = awaitedCommand;
        commandAcknowledged = false;
        commandEndsConnection = false; // it is not going anywhere after all
        commandSessionOver = true;
        cancelWatchdog();
        OEPLStatusStore.recordCommand(getDevice().getAddress(), what + " not supported");
        awaitedCommand = null;
        disconnectIfTransient();
        return true;
    }

    /**
     * The display answering that it took the command.
     * <p>
     * Worth its own handling because it is the only way to tell a command that never arrived
     * from one that arrived and did nothing. A restart in particular is close to invisible from
     * the outside - the panel keeps the picture it had - so without this there is nothing to go
     * on but pressing the button again.
     */
    private synchronized boolean acknowledgeCommand() {
        if (awaitedCommand == null) {
            return false; // an answer to a setting rather than to one of the commands
        }
        commandAcknowledged = true;
        LOG.info("{} took the command: {}", getDevice().getAddress(), awaitedCommand);
        GB.toast(getContext().getString(R.string.oepl_tools_acknowledged),
                Toast.LENGTH_SHORT, GB.INFO);
        if (!commandEndsConnection) {
            commandSessionOver(awaitedCommand);
        }
        return true;
    }

    /**
     * The command has been handed over, so the display is left alone again.
     * <p>
     * Asked for from two places and therefore written to happen once. Counting it as a finished
     * run matters: the scheduler is holding a wake lock waiting for one, and the display *was*
     * reached, so the retry ladder has no business climbing.
     */
    private synchronized void commandSessionOver(final String what) {
        if (commandSessionOver) {
            return;
        }
        commandSessionOver = true;
        cancelWatchdog();
        OEPLStatusStore.recordCommand(getDevice().getAddress(),
                commandAcknowledged ? what + " ok" : what + " unanswered");
        if (!commandAcknowledged) {
            LOG.warn("{} never answered the command {}", getDevice().getAddress(), what);
            GB.toast(getContext().getString(R.string.oepl_tools_no_answer),
                    Toast.LENGTH_LONG, GB.WARN);
        }
        awaitedCommand = null;
        disconnectIfTransient();
    }

    /**
     * The same on a display that is connected already - which is the only case where a command
     * has to push its way past something else. A restart or a sleep ends the connection under a
     * running transfer, so that transfer is given up on here rather than left to time out and be
     * counted as a display that went quiet.
     */
    private void performPendingActionsNow() {
        try {
            if (payload != null) {
                LOG.info("giving up the running transfer to {} for a command",
                        getDevice().getAddress());
                payload = null;
                blockData = null;
                blockCount = -1;
                clearProgress();
            }
            commandSession = true;
            commandSessionOver = false;
            commandEndsConnection = false;
            final TransactionBuilder builder = performInitialized("display command");
            performPendingActions(builder);
            builder.queue();
        } catch (final IOException e) {
            LOG.error("could not reach the display for what it was asked to do", e);
        }
    }

    /**
     * The fallback for a display that did not describe itself: what it is was in its
     * advertisement, and what panel sits behind that hardware type is in the catalogue. The
     * geometry from there is the same geometry the firmware would have reported - it is a
     * property of the hardware, not of the firmware revision.
     */
    private synchronized void drawFromCatalogueIfSilent() {
        if (config != null && config.valid) {
            return;
        }
        if (!isConnected()) {
            return;
        }
        final OEPLDisplayConfig fallback = catalogueConfig();
        if (fallback == null) {
            LOG.warn("the display described neither itself nor its hardware type, "
                    + "so there is nothing to draw against");
            return;
        }
        LOG.info("no usable configuration came from the display, drawing from the catalogue: {}",
                fallback);
        config = fallback;
        OEPLStatusStore.recordConfig(getDevice().getAddress(), fallback);
        pushUpdateIfNeeded();
    }

    /** The panel behind the hardware type the tag advertised, or null when that is unknown. */
    @Nullable
    private OEPLDisplayConfig catalogueConfig() {
        final int hardwareType = GBApplication.getDeviceSpecificSharedPrefs(
                getDevice().getAddress()).getInt(OEPLPrefs.DETECTED_HARDWARE_TYPE, -1);
        if (hardwareType < 0) {
            return null;
        }
        final OEPLDisplayConfig fallback = OEPLDisplayConfig.fromCatalogue(hardwareType);
        return fallback.valid ? fallback : null;
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt,
                                           final BluetoothGattCharacteristic characteristic,
                                           final byte[] value) {
        super.onCharacteristicChanged(gatt, characteristic, value);
        if (!OEPLProtocol.UUID_CHARACTERISTIC.equals(characteristic.getUuid())
                || value.length < 2) {
            return false;
        }
        // The one answer that is not a zero byte followed by an opcode: the display saying it
        // has no idea what it was just sent. Worth catching rather than dropping, because the
        // commands were added to the firmware over time and the newest of them simply do not
        // exist on a display that has not been updated - which looks exactly like a button
        // that does nothing.
        if ((value[0] & 0xff) == 0xff && (value[1] & 0xff) == 0xff) {
            return refuseCommand();
        }
        if (value[0] != 0x00) {
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
            case OEPLProtocol.ACK_COMMAND:
                return acknowledgeCommand();
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
                // The display keeps one slot per picture in its own memory and refuses
                // anything that does not fit. How big a picture turns out is a question of
                // what is on it - a drawn card is a few kilobytes, a dithered photograph can
                // be fifty times that - which is why this looks like a transfer that works
                // one time and not the next. The size goes into the message and into the
                // info screen, because without it there is nothing to go on.
                final int kilobytes = (payload == null ? 0 : payload.length) / 1024;
                GB.toast(getContext().getString(R.string.oepl_weather_image_too_big)
                        + " (" + kilobytes + " kB)", Toast.LENGTH_LONG, GB.ERROR);
                finishTransfer(false, false, "picture too big, " + kilobytes + " kB");
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
            final OEPLDisplayConfig fallback = catalogueConfig();
            if (fallback != null) {
                // no point waiting for the grace period: this answer is the one we got
                LOG.info("falling back to what the catalogue says about 0x{}",
                        Integer.toHexString(fallback.hardwareType));
                config = fallback;
            }
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

        // Some firmwares answer the hardware read with the settings structure as well, so
        // this may be the only answer the user's "read configuration" button ever gets.
        if (configReadRequested && config.valid) {
            configReadRequested = false;
            final OEPLTagTypes.TagType type = config.tagType();
            GB.toast(getContext().getString(R.string.oepl_weather_config_read,
                            type != null ? type.name : OEPLTagTypes.nameOf(config.hardwareType),
                            config.bufferWidth, config.bufferHeight, config.colors),
                    Toast.LENGTH_LONG, GB.INFO);
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
        if (goingToSleep || commandSession) {
            // this connection was opened for a command, and a picture behind it would be a
            // transfer into a display that is rebooting, sleeping or about to be let go - a
            // failure counted against a display that is doing exactly what was asked
            LOG.info("{} is busy with a command, not sending a picture",
                    getDevice().getAddress());
            return;
        }
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

        final String waitingFor = CardContentFactory.waitingForSource(prefs);
        if (waitingFor != null) {
            LOG.info("{} for {}, leaving the panel as it is", waitingFor, address);
            OEPLStatusStore.recordWaiting(address, waitingFor);
            cancelWatchdog();
            disconnectIfTransient();
            return;
        }

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
        options.nameTag = OEPLPrefs.nameTag(getDevice());

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
                || OeplCardFactory.CALENDAR_WEEK.equals(content)
                || OeplCardFactory.BOARD.equals(content)) {
            return calendarHint(prefs);
        }
        if (OeplCardFactory.isQuartered(content)) {
            return getContext().getString(R.string.oepl_weather_no_quarters);
        }
        if (OeplCardFactory.needsReadings(content)) {
            return getContext().getString(R.string.oepl_weather_no_readings);
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
            return calendarHint(prefs);
        }
        if (CardContentFactory.TEXT.equals(content) || OeplCardFactory.POSTER.equals(content)) {
            return getContext().getString(R.string.oepl_weather_no_text);
        }
        return getContext().getString(R.string.oepl_weather_no_weather);
    }

    /**
     * An empty appointment card has two quite different reasons, and the panel should say which
     * one it is: there is nothing in the calendar, or the calendar is not being read at all.
     * Reading it is off until it is asked for, as everywhere else in Gadgetbridge, so a card
     * that only ever says "no appointments" would be telling the wrong story.
     */
    private String calendarHint(final SharedPreferences prefs) {
        return getContext().getString(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SYNC_CALENDAR, false)
                ? R.string.oepl_weather_no_calendar : R.string.oepl_weather_calendar_off);
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
            finishTransfer(false, false, "asked for block " + currentBlock + " of " + blockCount);
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
        finishTransfer(success, alreadyShown, "failed");
    }

    /**
     * Ends the transfer, and says why when it did not work.
     * <p>
     * The reason is what the info screen shows afterwards. "Failed" on its own leaves nothing
     * to do but try again and hope, which is exactly the position a transfer that works every
     * other time puts somebody in.
     */
    private void finishTransfer(final boolean success, final boolean alreadyShown,
                                final String reason) {
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
            OEPLStatusStore.recordFailure(getDevice().getAddress(), reason);
        }
        if (transferIsFirmware) {
            noteFirmwareOutcome(success, reason);
        }
        cancelWatchdog();
        clearProgress();
        if (success) {
            OEPLUpdateScheduler.noteSuccess(getContext(), getDevice().getAddress());
        } else {
            OEPLUpdateScheduler.noteFailure(getContext(), getDevice().getAddress());
        }
        if (transferIsFirmware && success) {
            drawAgainAfterFirmware();
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
     * What became of a firmware transfer.
     * <p>
     * A success is the end of it. A failure is not: a display carried out of range mid transfer
     * is the ordinary case, the display keeps what it had, and the firmware picks the transfer
     * up where it stopped the next time round - so the note stays until the attempts are used
     * up. Either way this is kept out of the picture tallies.
     */
    private void noteFirmwareOutcome(final boolean success, final String reason) {
        final String address = getDevice().getAddress();
        if (success) {
            OEPLFirmware.done(address);
            OEPLStatusStore.recordCommand(address, "firmware written");
            LOG.info("{} is running the new firmware", address);
            return;
        }
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(address);
        if (OEPLFirmware.exhausted(prefs)) {
            OEPLFirmware.done(address);
            OEPLStatusStore.recordCommand(address,
                    "firmware gave up after " + OEPLFirmware.MAX_TRIES + " tries: " + reason);
        } else {
            OEPLStatusStore.recordCommand(address, "firmware " + reason
                    + ", try " + OEPLFirmware.tries(prefs) + " of " + OEPLFirmware.MAX_TRIES);
        }
    }

    /**
     * How long the display is left alone after a firmware has been written, before the card is
     * put back on it. The tag restarts into the new firmware, brings its panel up and starts
     * advertising again, none of which it can do while being talked to.
     */
    private static final long FIRMWARE_SETTLE_MILLIS = 60_000L;

    /**
     * Puts the display's own card back after a firmware update.
     * <p>
     * A tag that has just been written to shows whatever the new firmware put on its panel,
     * which is not the card it is meant to show, and nothing but a new transfer will change
     * that. Two things are needed for it: the cached checksum has to go, because it still
     * describes a picture the panel no longer has and would make the next push decide there was
     * nothing to send, and the schedule has to come back in a minute rather than at the end of
     * the interval - a display set to refresh once a day would otherwise sit there showing the
     * firmware's parting words until tomorrow.
     */
    private void drawAgainAfterFirmware() {
        final String address = getDevice().getAddress();
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putLong(OEPLPrefs.LAST_IMAGE_CRC, 0L).apply();
        LOG.info("{} has the new firmware, putting its card back in {} s", address,
                FIRMWARE_SETTLE_MILLIS / 1000);
        OEPLUpdateScheduler.requestRestore(getContext(), address, FIRMWARE_SETTLE_MILLIS);
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
            // where it stopped, which is the difference between a tag that never took the
            // picture and one that broke off halfway through it
            final String where = blockCount < 0 ? "no answer"
                    : String.format(Locale.ROOT, "no answer at block %d/%d part %d",
                            currentBlock + 1, blockCount, currentChunk);
            OEPLStatusStore.recordFailure(address, where);
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
            if (OEPLQuietHours.covers(prefs, System.currentTimeMillis())) {
                // the quiet hours are kept here as well, because a display that is being held
                // open would otherwise redraw all night on whatever arrives on the phone
                LOG.debug("quiet hours, leaving the panel as it is");
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
        // fresh weather is the whole point of this device, so go and show it - unless the
        // display is in its quiet hours, where a forecast nobody is awake to read can wait
        forced = false;
        if (OEPLQuietHours.covers(
                GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()),
                System.currentTimeMillis())) {
            return;
        }
        if (getDevice().isInitialized()) {
            pushUpdateIfNeeded();
        } else {
            OEPLUpdateScheduler.requestUpdateNow(getContext(), getDevice());
        }
    }

    /**
     * Sets the tag's own clock, which Gadgetbridge asks for when the phone's time or time zone
     * changes.
     * <p>
     * Two things about it are easy to get wrong. The tag has no time zone, so what it wants is
     * the local time as a unix timestamp rather than UTC - an hour out is an hour out on its
     * screen and in its own wake-up timing. And the four bytes go out lowest first, the way
     * the firmware reads them back; sent the other way round the tag sets its clock to some
     * moment tens of thousands of years from here.
     * <p>
     * Setting the time also puts the tag into its clock mode, where it draws a clock of its
     * own over the panel every minute - which on a display whose whole purpose is the picture
     * we push is vandalism. So the clock screen is switched off again in the same breath; the
     * time itself stays set.
     */
    @Override
    public void onSetTime() {
        if (config == null) {
            return;
        }
        final byte[] arg = OEPLProtocol.unixTimeArgument(System.currentTimeMillis(),
                TimeZone.getDefault());
        final TransactionBuilder builder = createTransactionBuilder("set time");
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.command(OEPLProtocol.CMD_SET_UNIXTIME, arg));
        builder.sleep(100);
        builder.write(OEPLProtocol.UUID_CHARACTERISTIC,
                OEPLProtocol.command(OEPLProtocol.CMD_DISABLE_UNIXTIME));
        builder.queue();
    }

    @Override
    public void onSendConfiguration(final String config) {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());

        if (OEPLPrefs.PREF_READ_CONFIG.equals(config)) {
            requestConfigurationRead();
            return;
        }

        if (OEPLPrefs.PREF_ACTION.equals(config)) {
            performPendingActionsNow();
            return;
        }

        if (OEPLPrefs.PREF_UPDATE_INTERVAL.equals(config)
                || OEPLPrefs.PREF_CATCH_UP.equals(config)
                || OEPLPrefs.PREF_QUIET.equals(config)
                || OEPLPrefs.PREF_QUIET_START.equals(config)
                || OEPLPrefs.PREF_QUIET_END.equals(config)
                || OEPLPrefs.PREF_OPPORTUNISTIC.equals(config)
                || OEPLPrefs.PREF_REQUIRE_ADVERTISEMENT.equals(config)) {
            // the interval decides how often the controller reports what it heard, so the
            // background listening has to be registered again with the new figure, and a
            // changed quiet window has to move the alarm that is already armed
            OEPLUpdateScheduler.reschedule(getContext(), getDevice());
            return;
        }

        if (LINK_PREFS.contains(config)) {
            return; // how the link is opened, which the next update reads for itself
        }

        if (HARDWARE_PREFS.contains(config)) {
            sendHardwareSetting(config, prefs);
            if (OEPLPrefs.PREF_MODEL.equals(config)) {
                // a different display type is a different panel, so what was drawn for the old
                // one no longer fits and has to be drawn again
                redrawNow(prefs, config);
            }
            return;
        }

        // Everything else changes what is drawn, so draw it again and offer it to the tag. This
        // is deliberately the default rather than a list of known settings: a setting left off
        // such a list looks broken - it is changed and the panel keeps showing the old picture
        // until the next interval - while a redraw too many costs a drawing and no connection,
        // because an unchanged picture is never sent.
        redrawNow(prefs, config);
    }

    /** The three settings that are the display's own and have to be written to it. */
    private void sendHardwareSetting(final String config, final SharedPreferences prefs) {
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
            GB.toast(getContext().getString(R.string.oepl_config_failed),
                    Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    /** Settings the display itself has to be told about. */
    private static final java.util.Set<String> HARDWARE_PREFS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    OEPLPrefs.PREF_MODEL,
                    OEPLPrefs.PREF_ADV_INTERVAL,
                    OEPLPrefs.PREF_OEPL_PROTOCOL));

    /**
     * Settings about how the link is opened. They change neither the picture nor the schedule:
     * the next update reads them when it runs.
     */
    private static final java.util.Set<String> LINK_PREFS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    OEPLPrefs.PREF_STAY_CONNECTED,
                    OEPLPrefs.PREF_CONNECT_WINDOW,
                    OEPLPrefs.PREF_CONNECT_ATTEMPTS,
                    OEPLPrefs.PREF_LIVE_UPDATES,
                    OEPLPrefs.PREF_MIN_GAP));

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
                    if (!OEPLProtocol.looksLikeFirmware(firmware)) {
                        LOG.error("that .bin does not look like firmware for this display");
                        GB.toast(getContext().getString(
                                        R.string.oepl_tools_firmware_not_firmware),
                                Toast.LENGTH_LONG, GB.ERROR);
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
