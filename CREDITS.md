# Credits

This fork stands on a lot of other people's work. Everything below is theirs, not mine.

## Gadgetbridge

The whole application. A cloudless, copylefted replacement for the vendor apps of Bluetooth
gadgets, developed by the Gadgetbridge community since 2015.

- Project: <https://codeberg.org/Freeyourgadget/Gadgetbridge>
- Homepage: <https://gadgetbridge.org>
- Licence: AGPL-3.0-or-later
- Contributors: [CONTRIBUTORS.rst](CONTRIBUTORS.rst) in this repository lists them, and the
  copyright headers of every upstream file name the people who wrote it.

## ATC_BLE_OEPL support in Gadgetbridge

This fork started from the ATC_BLE_OEPL device support that already exists in Gadgetbridge, and
still ships it unchanged next to the new weather display. It is the reason the protocol, the
plane packing and the transfer handshake did not have to be guessed from scratch.

- **Copyright (C) 2025 Andreas Shimokawa**, per the headers of
  `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/atcbleoepl/*.java` and
  `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/atcbleoepl/ATCBLEOEPLDeviceSupport.java`
- Shipped as "Experimental support for ATC_BLE_OEPL" in Gadgetbridge 0.87.0, see
  [CHANGELOG.md](CHANGELOG.md)
- Device page: <https://gadgetbridge.org/gadgets/displays/atc_ble_oepl/>

The new device type in this fork (`OEPL Weather Display`) is separate code, but it would not
exist without that work to build on.

## ATC_BLE_OEPL firmware

The firmware that makes the tags speak Bluetooth LE in the first place, by **atc1441**
(<https://github.com/atc1441>), hosted at <https://atc1441.github.io/ATC_OEPL_Flasher.html>.
Gadgetbridge's device page describes it as forked from OpenEPaperLink with BLE enabled.

The firmware is not part of this repository and is not redistributed here. It was used the way
anyone uses a device's firmware: by watching what it does on the air.

## OpenEPaperLink

The project that gave these tags a life beyond the supermarket shelf, and whose access point
defines what a tag "should" show. The card designs in this fork follow the same idea — a
weather card, a forecast card, a week view — and use the same content numbers in their ids
(`oepl_4` is the current weather, `oepl_8` the forecast) so the two can be compared side by
side.

- Project: <https://github.com/jjwbruijn/OpenEPaperLink> by **jjwbruijn** and contributors
- Homepage: <https://openepaperlink.de>
- Licence: CC BY-NC-SA 4.0

**No OpenEPaperLink code, layout file or asset is used here.** Its licence is not compatible
with the AGPL, so every layout in this fork was written from scratch against the published
screenshots and descriptions. If anything here still looks too close to the original, that is a
bug worth reporting.

## Fonts

Bundled in `app/src/main/assets/oepl/`, both under the SIL Open Font License 1.1 — the full
licence text is in
[FONTS_LICENSE.txt](app/src/main/assets/oepl/FONTS_LICENSE.txt).

- **Signika**, Copyright 2018 The Signika Project Authors —
  <https://github.com/googlefonts/Signika>. The SemiBold instance shipped here was generated
  from the variable font with fontTools and subset to Latin; no outline was altered.
- **Weather Icons**, Copyright 2013 Erik Flowers —
  <https://github.com/erikflowers/weather-icons>, byte for byte unchanged.

## Libraries and services

- **ZXing core**, Apache-2.0 — <https://github.com/zxing/zxing>. Draws the QR card.
- **Open-Meteo**, free for non-commercial use, data under CC BY 4.0 —
  <https://open-meteo.com>. The forecast and the geocoding behind the weather cards. No API key
  and no account, which is why it is the default.
- Everything else comes with upstream Gadgetbridge; see its own dependency list in
  `gradle/libs.versions.toml` and `app/build.gradle`.

## This fork

The OEPL weather display device type, its cards, its scheduler and its screens were written for
this repository. They are published under the same AGPL-3.0-or-later as the rest.
