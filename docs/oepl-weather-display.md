# OEPL Weather Display

The device type this fork adds. A BLE electronic shelf label running the ATC_BLE_OEPL firmware,
driven entirely by the phone: the phone fetches the content, draws the card, compresses it and
pushes it over Bluetooth LE. There is no access point and no server anywhere in the picture.

## Pairing

1. Flash [ATC_BLE_OEPL](https://gadgetbridge.org/gadgets/displays/atc_ble_oepl/) onto the tag.
2. Search for devices. A tag calling itself `ATC_xxxxxx` is offered twice: once as
   **ATC_BLE_OEPL** (the upstream device type, for sending a picture by hand) and once as
   **OEPL Weather Display**. Pick the latter.
3. If the wrong one was picked, long press the entry in the discovery list and force the type.

**A display is drawn for even when its firmware says little about itself.** The settings
structure a tag answers with is read for the panel - its size, its colours - and everything
behind that in the structure is a setting rather than a property: a firmware that has never
had a BLE interval written leaves it at zero, which says nothing about the panel and must not
count as a broken answer. Should the answer be unreadable, or not come at all, the hardware
type from the advertisement is looked up in the tag catalogue and the picture is drawn against
that. A display that connects and is then let go without being sent anything is the one
outcome worth ruling out.

**A tag is recognised by what it advertises, not by what it calls itself.** The same firmware
answers to `ATC_` on one board and `RTL_` on another, and a tag whose name has not been read
yet has none at all - so the scan filters on 0x1337 instead, which every OpenEPaperLink tag
carries twice over: as the service in its advertisement and as the manufacturer id of the data
block that holds the hardware type, the battery voltage and the panel temperature. Either is
enough to be offered as a display; the name is only consulted when neither is at hand.

Each entry in the discovery list names the panel the tag carries, for example
`M3 2.9" BWRY · 384×168 · 2.61 V`, together with its battery. These displays call themselves
`ATC_` or `RTL_` plus four characters of their MAC address, so a shelf of them is otherwise a
list of identical entries - while the panel, which decides what can be shown on it, is in every
advertisement they send. An unknown type still shows its code, as `Unknown (0x4F)`.

That is also why the tag already tells you a lot before it is ever connected to: its hardware
type, firmware version, battery voltage and panel temperature are in its advertisement, so the
first card is drawn with a real battery reading and the right panel geometry.

## On the device card

- **The title** is the tag's Bluetooth name with the panel behind it, as in
  `ATC_8F3A21 · 2.9" · 384×168`. "ATC_" plus four characters of the MAC address places
  nothing once a second display is on the wall, while the diagonal and the resolution say what
  kind of display this is. Give the device a name of your own and that name is used instead,
  untouched.
- **The battery** is the one from the last visit, and it is shown whether or not the link is
  up. Gadgetbridge forgets a device's battery the moment the link drops, which is right for a
  watch that reports while it is worn and wrong for a tag that is awake for a few seconds a
  day: the card would show a question mark for all but those seconds, although the voltage is
  in every advertisement the tag sends. The reading on the card is therefore at most one
  wake-up old.
- **The icon** is the picture the display is showing, and it stays in its own colours whether or
  not the link is up. Gadgetbridge greys out the icon of a disconnected device, which reads
  correctly for a symbol but not for a photograph of a panel - least of all here, where being
  disconnected is the normal state.
- **The order of the list** is a setting of its own, under *Settings → Order of the device
  list*: connected first (as before), **last update first**, by name, or by kind of device -
  which groups the displays of one type together. The other three deliberately ignore the
  connection state: a shelf of tags that are asleep nearly all of the time is a list in which
  that state says nothing.
- **The time beside the state** on each card is when the device last did what it is there for
  - for a display, when its picture was last brought up to date, not when it last connected.
  "Not connected · 16:42" says a display is working; "Not connected" on its own says nothing.
- **Pulling the list down** refreshes the display. Every cached source is thrown away, so the
  weather, the headlines and a picture from a URL are fetched again, the card is drawn, and the
  tag is contacted - but only if the new picture differs from the one it already shows. The
  gesture normally means "fetch the recorded data of what is connected" and would otherwise
  only report that nothing is connected, which for a tag that is woken for a few seconds at a
  time is almost always true.

## What a card may show

Two rules every card follows, which are worth knowing because they are what a display does
when something goes wrong rather than when everything works:

- **Nothing from the past.** The forecast in hand can be older than it looks - the network was
  unreachable for a few hours, or overnight, and the display drew the picture anyway from the
  last answer, which is the right thing to do. The first day of that answer is then yesterday,
  so days that are over are dropped before anything is drawn, and the hourly curve starts at
  the current hour. A forecast that lies entirely in the past leaves the card saying it has no
  weather, which is the truth.
- **An hour is not a date.** The curves count along their bottom edge in hours, and a row
  reading "15, 18, 21" is taken for a date the moment the numbers run past twelve. Midnight
  therefore carries the name of the day that starts there, with a dotted rule under it, and
  the temperature scale up the side carries its degree sign.
- **Today says so.** In the forecast cards the column or row for today carries the word
  *Today* in the accent colour instead of its weekday - and where the column is too narrow for
  the word, the weekday stays but the colour does not. Which day one is standing in is the
  first thing read off a forecast and the easiest to get wrong.

### Language and time zones

Everything drawn on the panel comes from the app's string resources, so the display follows
**Gadgetbridge's language setting** - which is the system language unless it was changed in the
app. That includes the weekday and month names, which are formatted in the same locale, and the
description of the sky ("light rain"), which is taken from Gadgetbridge's own translated names
for the weather condition codes rather than written out here: the panel speaks every language
the app does, not only the two it was written in.

Times follow two different clocks on purpose:

- **The phone's** for everything about the phone - the clock card, appointments, notifications,
  the stamp in the header of the tile grid.
- **The weather location's** for everything about the weather - the hours along the curves,
  sunrise and sunset, the weekdays of the forecast and which of them is today. A forecast for a
  place two time zones away is about that place's morning. Where the two clocks agree, which is
  the normal case, none of this is visible.

The tag's own clock is a separate thing again: it has no time zone, so it is given the local
moment as a plain timestamp, and the tag's clock screen is switched off again right after -
the firmware would otherwise draw a clock of its own over the picture every minute.

## The cards

Forty-three of them, in four families, which the ids say apart:

- `oepl_*` — the OpenEPaperLink layouts. The number is the content number that access point
  uses, so the two can be compared side by side.
- `panel_*` — this project's own full panel designs. They owe the access point nothing; the
  prefix is there so that nothing of ours is mistaken for one of its layouts.
- `image`, `image_url` — the two picture cards, which also take the whole panel.
- everything else — drawn inside the Gadgetbridge frame, with a title bar and a status strip.

| Id | Card | Reads |
| --- | --- | --- |
| `oepl_4` | Current weather (default) | weather, wind unit |
| `oepl_8` | Forecast, one column per day, with the current reading in its header | weather, wind unit, days |
| `oepl_16` | Rain graph for the coming hours | weather, hours |
| `oepl_1` | Date, with sunrise, sunset and moon | weather, sun times |
| `oepl_29` | Clock | — |
| `oepl_11` | Appointment list | calendar, caption |
| `oepl_11w` | Week view, falls back to a list on a small panel | calendar days |
| `panel_month` | The month as a sheet, today filled, a dot per appointment | caption |
| `oepl_9` | News feed (RSS or Atom) | feed |
| `oepl_10` | QR code | QR content |
| `oepl_2` | Countdown in days | countdown |
| `oepl_3` | Countdown in hours | countdown |
| `oepl_21` | Display info | — |
| `panel_big` | The temperature at poster size, with the day's range and the wind | weather, wind unit |
| `panel_bars` | The week as bars on one shared scale; figures only on a narrow tag | weather, days |
| `panel_sun` | The sun's way across the sky, the moon after dark | weather, sun times |
| `panel_tiles` | Today in tiles of a pictogram and a figure | weather, wind unit |
| `panel_board` | The appointments as a departure board | calendar, caption |
| `panel_poster` | Your own text as a poster, for a door sign | text |
| `panel_grid` | Dashboard tiles: a symbol, a figure and a caption per value | which values, Home Assistant |
| `panel_hero` | One value at poster size, the rest as badges, the agenda beside it | which values, Home Assistant |
| `panel_list` | The values as rows, with a bar for every percentage | which values, Home Assistant |
| `panel_graph` | The hours ahead as a temperature curve with the rain under it | weather, hours |
| `panel_quad` | The panel in quarters, each holding another card | the four slots |
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

The twelve `oepl_*` cards, the twelve `panel_*` cards and the two picture cards own the
whole panel. The others are drawn inside a frame with a title bar and a status strip, which is what
the *Appearance* settings apply to.

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
| **Weather** | source (Open-Meteo or Gadgetbridge), location, forecast days (up to seven), hours, wind unit, sun times |
| **Calendar** | read the calendar on or off, which calendars, how far ahead, birthdays, appointments shown, days in the week view, caption |
| **News feed** | feed address, caption, summaries |
| **QR code** | content, caption |
| **Countdown** | target date, caption, accent threshold |
| **Notifications** | how many |
| **Own text** | the text |
| **Picture** | choose a picture, picture address, colour reduction, fit |
| **Dashboard values** | which of the four groups the dashboard cards collect |
| **Home Assistant** | address, access token, the entities to show |
| **Four quarters** | which card goes into each of the four slots |
| **Appearance** | frame, accent colour, rotation, status strip, invert |
| **Updates** | interval, live updates and their minimum gap, connection attempts, reachability |
| **Power and radio** | keep connection open, advertising interval, OEPL protocol flag |
| **Hardware** | read the configuration from the display, display type |

### The month sheet

`panel_month` is the view the other calendar cards do not have: it answers *when* rather than
*what next*. A cell per day, today filled in the accent colour, and a dot for every appointment
that day holds - at the size a day gets on a tag, a title would be two letters and an ellipsis,
while a row of dots is read from across the room. The month decides its own span, so the
*calendar lookahead* setting does not limit it: the card asks for the days that are left in the
month however the other cards are configured. A panel too small for seven columns shows the
list of what is coming up instead.

### How many days

*Forecast days* is the number of days a card draws, from today on, and it goes up to the seven
Open-Meteo delivers. Five by default. A card never draws more than its panel can hold at a
readable size: the columns of the forecast card are as narrow as two temperatures beside each
other allow and no narrower, so a shelf label shows the first three or four days of the week
and a poster shows all seven. Asking for seven on a small tag is therefore not a mistake - it
is simply as much of the week as fits.

### The dashboard cards

`panel_grid`, `panel_hero` and `panel_list` are three arrangements of one and the same list of
values. What goes into that list is decided once, by group, under *Dashboard values*:

- **Weather** — the temperature now, the day's range, rain, wind, humidity.
- **House** — the Home Assistant entities, see below.
- **Appointments** — how many are left today, and the next one by name.
- **Batteries** — the phone, the display itself and every other paired gadget.

The order is fixed and is the same for all three cards: the sky, the house, the agenda, the
batteries. That is what makes switching between the three arrangements a matter of taste rather
than of content - and it is also how a card decides what to leave out when the panel is full.
A tile grid shows as many tiles as the panel can hold **at a readable size** and drops the rest,
which is why a shelf label shows four values where a poster shows eight. Nothing is ever shrunk
below what survives a two colour panel: thin grey strokes come out of the quantiser as paper.

### Home Assistant

The panel can show entity states from your own instance. The usual Home Assistant setup for
e-paper tags runs the other way round - an add-on renders pictures and hands them to an
OpenEPaperLink access point - but here the phone *is* the access point, so the phone fetches
the states itself. Nothing is installed on the Home Assistant side.

1. In Home Assistant, open your own profile, scroll to the bottom and create a
   **long-lived access token**. Copy it; it is shown once.
2. In the display's settings, under *Content → Home Assistant*, enter the **address** the phone
   can reach, e.g. `http://homeassistant.local:8123` or `http://192.168.1.4:8123`. A plain host
   name with a port is enough - `http://` is filled in when it is missing.
3. Paste the token into **Access token**.
4. Tap **Choose entities**. The display asks the instance what it has and lists it the way
   Home Assistant groups it - by room, with each entity's current state beside its name, and a
   search field above. Tick what the panel should show. (The rooms come from one template
   query, because the area registry is not reachable over the plain REST interface; an
   instance that does not answer it is simply grouped by domain instead.)

   The list underneath stays editable for anything the picker cannot do - above all a name of
   your own after a vertical bar. One entity per line:

   ```
   sensor.wohnzimmer_temperatur | Wohnzimmer
   binary_sensor.haustuer | Haustuer
   sensor.pv_heute | PV heute
   switch.waschmaschine
   ```

   Without a name, the entity's own friendly name is used. A name typed here survives the
   picker. Up to twelve entities are read; the
   card then shows as many of them as the panel can hold.
5. Choose `panel_grid`, `panel_hero` or `panel_list`, and leave **House** switched on under
   *Dashboard values*.

What it does with what it gets: a number keeps its unit (`21.8 °C`, `8.4 kWh`), a percentage
also gets a bar, and the states Home Assistant words rather than numbers - `on`, `off`, `open`,
`closed`, `locked`, `home` - are translated. A door left open, an unlocked lock and a battery
at twenty percent or less are drawn in the accent colour. The symbol comes from the entity's
domain and device class, the same things Home Assistant picks its own icons by.

The fetch happens while the picture is being prepared, before the radio session with the tag,
and one `GET /api/states/<entity>` goes out per entity. States are kept for two minutes, so a
display that redraws every quarter of an hour asks once per update. If the token is wrong or an
entity is misspelled the card simply has fewer values on it - the display's **info screen**
says what went wrong, under *Home Assistant*.

Two limits worth knowing: the phone has to be able to reach the instance when the update runs
(the same network, a VPN, or Nabu Casa), and a self-signed certificate only works if its
authority is installed on the phone. On a home network, plain `http://` is the simple answer.

### The picture cards

The picture a display shows is its own: every display keeps its own choice, its own colour
reduction and its own framing, and the screen says whose picture is being chosen in its title.

**Framing.** Drag on the preview to move the picture, pinch to zoom, double tap to go back to
the whole of it. The preview is the card itself, so what is under the finger is what the panel
gets. A fit that scales the whole picture into the panel is right for a poster and wrong for a
photograph of a person, where the point is the face rather than the edges - and cropping it
beforehand, in another app, for a panel whose proportions nobody remembers, is not a
reasonable thing to ask.

**Nothing is sent until there is something to send.** Choosing *picture* and choosing the
picture are two steps, and in between the card carries nothing but the words "no picture".
Sending that would wipe what the panel was showing, cost a connection and a full refresh of
the display, and be replaced a few seconds later - so the display is left alone, and the
scheduled updates skip it, until a picture has been chosen. The same holds for the other cards
whose content is handed over by hand: the picture from a URL, the QR code, the feed and the
text poster. A source that was set but does not answer is a different matter - there the card
saying so is the useful answer.

### A second display

Pairing another display takes the settings of one that is already set up: the weather location,
the calendars, the feed, the Home Assistant instance, the update schedule. None of that is
about the panel - it is about the household - and typing it in again for every tag on the wall
is work nobody should be asked to do twice. What is copied is an ordinary setting afterwards:
the new display can be pointed somewhere else, and nothing remembers where its values came
from. What it *shows* is deliberately not copied, nor its picture, rotation or colours, so a
second display starts as ready to use as the first one but not as a copy of it.

### Telling the displays apart

*Settings → Name tags on every display* is one switch for all of them at once. While it is
on, every display carries its own Bluetooth name in a small box in its top left corner, black
on white with a black border around it, drawn over whatever the card shows.

The text is deliberately small - a dozen pixels at most, however large the panel - because this
is a note to whoever is standing in front of the display rather than part of the card, and the
box is only as wide as the name needs.

The name is never shortened. A box reading `Hamb…` cannot be matched against anything, which is
the one job it has, so a name too long for the panel is set smaller and then broken across
lines - and never cut. The Bluetooth name is used rather than the alias: that is what a scan
shows and what the device list has to be matched against, while the alias is the name *you*
gave the display and is already on its card in the list. An unnamed display falls back to its
address.

Flipping the switch draws every display again at once and offers each of them the new picture,
so the boxes appear - and disappear again - without waiting for the next scheduled update. A
display that is out of range gets its box the next time it is reached. The name is written
before the card is turned into the panel buffer, so the corner is the one you are looking at,
and the design picker shows the box too, which is the quickest way to see that the mode is on.

### The quartered card

`panel_quad` is not a design of its own, it is the box the others come in: *Content → Four
quarters* holds four slots, and each slot takes any other card. Two cards split the panel along
its longer edge, three give the first one the full width and put the other two underneath, four
make a grid. A panel too small to hold them all readably shows fewer of them rather than four
fragments - a pane has to be at least 118 pixels wide, which is about the narrowest a card was
ever designed for.

A quarter is drawn by the very same code that would fill the whole panel, so everything keeps
working: the weather quarter still reads the weather settings, the feed quarter still fetches
the feed before the tag is contacted. A quarter cannot be another quartered card.

### Showing a Google (or any other) calendar

Gadgetbridge never talks to Google. It reads the phone's own calendar database, which is where
the Android calendar app keeps everything that any account syncs into it - Google, Exchange,
Nextcloud, a local calendar. So a Google calendar reaches the panel the moment the phone has
it:

1. In Android's settings, make sure the account is added and its calendar sync is on. A
   calendar that the phone itself does not show will not reach the display either.
2. Grant Gadgetbridge the calendar permission. It is one of the permissions on the first-run
   screen, and can be given later under Android's app permissions. Without it the switch in the
   next step stays greyed out and says so.
3. In the display's settings, open **Calendar** and turn on **Sync calendar events**.
4. Still in **Calendar**, open **Choose calendars to be synced** and leave only the ones that
   belong on this panel. An account usually carries a handful - holidays, birthdays, a shared
   family calendar - and a wall display rarely wants all of them.
5. Pick a card that shows appointments: `oepl_11` (list), `oepl_11w` (the week), `panel_board`
   (the appointment board), or one of the split cards that pairs the weather or the date with
   the next appointments.

**How far ahead** decides how much the card may look into the future, seven days by default.
The week view needs seven to fill itself; a list of the next four appointments is happy with
less. **Birthdays** adds the ones from the contacts, which needs the contacts permission as
well.

While the calendar is off, an appointment card says so on the panel rather than claiming there
is nothing in the diary.

## How updates are scheduled

A tag is awake for a few seconds at a time and asleep in between, and Android is not keen on
letting an app do anything in the background. The schedule is built around both:

- **Every fifteen minutes by default**, settable from 5 minutes to 12 hours. An update whose
  picture turns out to be the same as the one already on the panel never opens a connection at
  all, so a short interval costs the cell nothing on a card that has not changed - and keeps a
  clock, a reading or an appointment from standing there wrong for an hour.
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
- **Every setting draws again at once.** Changing anything that affects the picture - the card,
  a caption, which calendars are read, the frame, the rotation - redraws and offers the result
  to the display right away, so a setting can be judged by looking at the panel. Only the three
  settings that belong to the display itself and the few about how the link is opened do not.
  This is the default rather than a list of known settings: a forgotten setting would look
  broken, while a redraw too many costs a drawing and no connection, because an unchanged
  picture is never sent and a run already under way is never cut into.
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
    ├── OeplCanvas.java                   measured text, pictograms, rules and bars
    ├── Oepl*Card.java                    the twelve OpenEPaperLink layouts
    └── Oepl{BigTemperature,Range,Sun,Tiles,Board,Poster}Card.java   our own six

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
   `values/` and `values-de/`. A full panel card needs an id beginning with `oepl_` (a layout
   taken from the access point) or `panel_` (one of our own) — that prefix is what routes it to
   `OeplCardFactory` and keeps the frame settings away from it.
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

`OeplLayoutAuditTest` is the sweep: every design on **every geometry in the tag catalogue, in
both orientations**, and every design a second time with the longest text it could plausibly
be given - a place name that fills the panel, entity labels named after where the thing hangs,
appointment titles with a doctor's full name in them. That is 102 designs across 77 shapes and
close to eight thousand cards a run. The long forms are the ones that matter: a layout that
holds "Hamburg" says nothing about what it does with "Schmalkalden-Meiningen Nord", and the
long names are where the overlaps were.
It draws through `OeplInkRecorder`, a canvas that writes down the box every piece of ink really
covers, and fails on two things:

- **No run of ink may leave the area the card was given.** The box the layout asked for is kept
  apart from the box that survived the clip, so an overflow is still caught where the renderer's
  clip would have hidden it - and a clipped letter is a letter the reader does not get.
- **No two pieces of text may share more than a square pixel or two.** Ink over ink looks
  exactly like ink to a pixel count, so this is measured from the glyph boxes. Both thresholds
  are nearly zero; every design currently passes at a single square pixel, and what is allowed
  is for the rounding a different build of a font brings, not for anything anybody could see.

`OeplDashboardTest` adds what those two cannot see. It measures the **size** of what was
drawn, because a panel with two or three colours turns a thin grey stroke into paper: text
under about eight pixels is not small, it is gone. So the dashboard cards show fewer values
rather than smaller ones, and the test holds them to it - including the case where half the
values are sentences rather than figures, which must not shrink the figures. It also checks
the pictograms against the text beside them, which the layout audit leaves out: a symbol drawn
as a shape is not a piece of text, and nothing else would notice it landing on a caption.

The report of a run is written to `app/build/oepl-gallery/layout-audit.txt`, whether it passed
or not. Between the two, what is left for the contact sheets is the question no assertion
answers: whether a card is worth looking at. Look at them after changing a layout.
