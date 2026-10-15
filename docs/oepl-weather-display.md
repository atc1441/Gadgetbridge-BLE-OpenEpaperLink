# OEPL Weather Display

The device type this fork adds. A BLE electronic shelf label running the ATC_BLE_OEPL firmware,
driven entirely by the phone: the phone fetches the content, draws the card, compresses it and
pushes it over Bluetooth LE. There is no access point and no server anywhere in the picture.

## Pairing

1. Flash [ATC_BLE_OEPL](https://gadgetbridge.org/gadgets/displays/atc_ble_oepl/) onto the tag.
2. Search for devices. The tag advertises as `ATC_xxxxxx` and will be offered twice: once as
   **ATC_BLE_OEPL** (the upstream device type, for sending a picture by hand) and once as
   **OEPL Weather Display**. Pick the latter.
3. If the wrong one was picked, long press the entry in the discovery list and force the type.

The tag already tells you a lot before it is ever connected to: its hardware type, firmware
version, battery voltage and panel temperature are in its advertisement, so the first card is
drawn with a real battery reading and the right panel geometry.

## The cards

Thirty-one of them. The ids of the first twelve carry the content numbers an OpenEPaperLink
access point uses, so the two can be compared side by side.

| Id | Card | Reads |
| --- | --- | --- |
| `oepl_4` | Current weather (default) | weather, wind unit |
| `oepl_8` | Forecast, one column per day | weather, wind unit, days |
| `oepl_16` | Rain graph for the coming hours | weather, hours |
| `oepl_1` | Date, with sunrise, sunset and moon | weather, sun times |
| `oepl_29` | Clock | — |
| `oepl_11` | Appointment list | calendar, caption |
| `oepl_11w` | Week view, falls back to a list on a small panel | calendar days |
| `oepl_9` | News feed (RSS or Atom) | feed |
| `oepl_10` | QR code | QR content |
| `oepl_2` | Countdown in days | countdown |
| `oepl_3` | Countdown in hours | countdown |
| `oepl_21` | Display info | — |
| `weather` | Weather with a short forecast | weather, days |
| `date`, `clock` | Date, clock | — |
| `calendar` | Appointments | calendar |
| `text` | Your own lines of text | text |
| `status` | Battery of every paired gadget | — |
| `notifications` | The last notifications | count |
| `music` | What is playing | — |
| `hourly` | Temperature curve | weather, hours |
| `forecast` | Forecast week | weather |
| `weather_calendar`, `weather_date`, `date_calendar`, `clock_weather`, `weather_notifications`, `calendar_notifications` | two of the above, side by side | both halves |
| `dashboard` | weather, calendar and notifications at once | all three |
| `image` | A picture from the phone | picture, dithering, fit |
| `image_url` | A picture from a URL | picture, dithering, fit |

The twelve `oepl_*` cards and the two picture cards own the whole panel. The others are drawn
inside a frame with a title bar and a status strip, which is what the *Appearance* settings
apply to.

### Picking one

*Content → Choose a design* draws all of them with your own data, at your panel's size and in
your panel's colours, and sends the one you tap straight away. The same screen is on the
device's status page.

## Settings

The settings are grouped so that only what the chosen card actually reads is on screen — a
clock card offers nothing to configure, a feed card offers the feed and nothing else. The map
from a card to its options is in one place,
`service/devices/atcbleoeplweather/OEPLContentOptions.java`, and a unit test checks that the two
cannot drift apart.

| Screen | What is in it |
| --- | --- |
| **Weather** | source (Open-Meteo or Gadgetbridge), location, forecast days, hours, wind unit, sun times |
| **Calendar** | appointments shown, days in the week view, caption |
| **News feed** | feed address, caption, summaries |
| **QR code** | content, caption |
| **Countdown** | target date, caption, accent threshold |
| **Notifications** | how many |
| **Own text** | the text |
| **Picture** | choose a picture, picture address, colour reduction, fit |
| **Appearance** | frame, accent colour, rotation, status strip, invert |
| **Updates** | interval, live updates and their minimum gap, connection attempts, reachability |
| **Power and radio** | keep connection open, advertising interval, OEPL protocol flag |
| **Hardware** | read the configuration from the display, display type |

## How updates are scheduled

A tag is awake for a few seconds at a time and asleep in between, and Android is not keen on
letting an app do anything in the background. The schedule is built around both:

- **An exact alarm per display**, armed with `setAndAllowWhileIdle`, rather than WorkManager.
  Deferrable background work is how updates used to arrive hours late or not at all.
- **The content is refreshed before the radio comes up**, never after. The tag hangs up by
  itself after a few seconds of silence and will not wait for an HTTP request.
- **Several connection attempts per update.** A connection cannot be opened at an arbitrary
  moment: the radio has to keep trying until the tag advertises. One attempt gets 30 seconds by
  default (Android's own default of five is barely one advertising interval), and an update
  makes three attempts before it gives up.
- **A wake lock is held across the attempt and the transfer**, so the phone cannot fall asleep
  halfway through and strand the picture.
- **A retry ladder** of 60 s, 2, 5, 10, 20, 30 minutes for runs that lead nowhere, never longer
  than the configured interval.
- **An opportunistic background scan**, registered with the Bluetooth controller so it is the
  chip and not the app that watches for the tag. An overdue picture goes out the moment the tag
  is heard. Only used where the controller can batch scans in hardware.
- **Live updates**: a notification or a track change pushes immediately instead of waiting for
  the interval, coalesced and rate limited so a burst cannot thrash the panel.
- A **checksum of the last picture** means an unchanged card is never sent twice.

Android's battery optimisation will hold alarms back whatever the app does, so the status page
has a button to exclude the app from it. It is the single most effective thing you can change.

## The protocol, as the tag speaks it

Everything here was observed on the air; it is written down because the next person should not
have to watch it again.

- Service and characteristic `00001337-0000-1000-8000-00805f9b34fb`, manufacturer id `0x1337`,
  MTU 247 in practice.
- Commands used here: `5` get infos, `17` read the dynamic hardware type, `11` set the unix
  time, `100` announce a transfer, `101` send a block, `4` set the display type, `8` set the
  advertising interval, `6`/`7` enable and disable the OEPL protocol, `20` deep sleep, `23`
  reboot. Replies arrive on the same characteristic.
- The picture is packed into one or two 1 bpp planes, MSB first, row major. The accent plane
  selects the second colour; on a four colour panel both planes together address black, white,
  red and yellow.
- Data type `0x30` is the compressed one: raw deflate with a full flush every 4096 bytes, an
  Adler-32 at the end and a two byte header the firmware expects. On the 384×184 four colour
  panel this is the difference between 17 664 bytes in 13 seconds and 2 254 bytes in 7.4
  seconds.
- The buffer geometry is not the panel geometry: on most tags the buffer is a quarter turn away
  from what you read. The catalogue in `OEPLTagTypes.java` holds both, and the hardware read
  (`17`) is the authoritative source for a tag that is connected.

## The code

```
service/devices/atcbleoeplweather/
├── ATCBLEOEPLWeatherDeviceSupport.java   the connection, the transfer, the watchdog
├── OEPLUpdateScheduler.java              alarms, attempts, the background scan
├── OEPLContentSync.java                  refreshes weather, feed and picture before a push
├── OEPLImageCodec.java                   quantising, dithering, plane packing, deflate
├── OEPLImageSource.java                  the picture behind the two image cards
├── OEPLTagTypes.java                     the panel catalogue
├── OEPLPrefs.java                        every setting, in one place
├── OEPLContentOptions.java               which card reads which setting
├── OEPLStatusStore.java                  what the status page shows
├── CardRenderer.java                     frame, title bar, status strip, rotation
├── CardContentFactory.java               id → content
├── content/                              the framed cards
└── oepl/                                 the full bleed cards, the canvas and the fonts

devices/atcbleoeplweather/
├── ATCBLEOEPLWeatherCoordinator.java     registration, defaults, device picture
├── ATCBLEOEPLWeatherInfoActivity.java    the status page
├── OEPLDesignPickerActivity.java         the visual design picker
├── OEPLImageActivity.java                picture, dithering and preview
└── ATCBLEOEPLWeatherSettingsCustomizer.java   shows only what the card reads
```

### Adding a card

1. Write a `CardContent` (framed) or an `OeplCard` (full bleed). Scale everything off the
   rectangle you are given — the same card runs from 122×250 to 960×768.
2. Add one case to `CardContentFactory` or `OeplCardFactory`.
3. Add the id and its name to `oepl_weather_content` / `oepl_weather_content_values`, in both
   `values/` and `values-de/`.
4. Say which settings it reads in `OEPLContentOptions`.
5. Add it to `OeplDemo.IDS` in the tests. That alone puts it into the contact sheets, the
   out-of-bounds check and the design picker test.

### The drawing tests

`OeplGalleryTest` renders every design at six panel shapes and writes contact sheets to
`app/build/oepl-gallery/`. Two guards run alongside it:

- **Nothing may be drawn outside the area a card was given.** Each card is drawn onto a larger
  sheet and any ink in the margin fails the test. This catches the class of bug where a layout
  paints over the title bar, the status strip or the panel edge.
- **Every design has to put a readable amount of ink on the panel** — not blank, and not a
  solid block (the picture cards are exempt from the upper bound, since a photograph may well
  cover everything).

Overlaps between two pieces of text are not something an assertion catches well, which is what
the contact sheets are for. Look at them after changing a layout.
