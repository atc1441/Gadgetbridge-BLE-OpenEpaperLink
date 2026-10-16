# Releases

## What happens on a push

`.github/workflows/release.yml` runs on every push to the default branch:

1. Builds `assembleMainlineNopebble`.
2. Tags the commit `v<versionName>-<run number>`, for example `v0.95.1-7`. The version name
   comes from `app/build.gradle`, the run number from GitHub, so every push gets a version of
   its own without anything having to be bumped by hand.
3. Creates a GitHub release with that tag and attaches the APK as `Gadgetbridge-OEPL.apk`.

The asset name never changes, so this link always points at the newest build:

```
https://github.com/atc1441/Gadgetbridge-BLE-OpenEpaperLink/releases/latest/download/Gadgetbridge-OEPL.apk
```

A commit whose message contains `[skip release]` is built but not released. Pull requests and
other branches run `.github/workflows/ci.yml` instead, which builds, runs the unit tests and
keeps the rendered card previews as an artifact.

## The signing key

Releases are signed with a key of this project's own:

| | |
| --- | --- |
| Alias | `gadgetbridge-oepl` |
| Key | RSA 4096, `SHA256withRSA`, valid until October 2056 |
| Subject | `CN=Gadgetbridge OEPL Weather, O=atc1441, C=DE` |

Every build carries the same signature, which is what lets Android install a new release over
an older one. The keystore itself lives outside the repository, on the maintainer's machine
only, and is never committed.

Note for anyone who installed a release built before October 2026: those were signed with a
throwaway debug key, so the first update to a properly signed build has to be preceded by
uninstalling the old app once. After that, updates install over each other.

### How the build finds it

`app/build.gradle` reads four system properties and signs the `nightly` and `nopebble` build
types with them when they are present, falling back to the debug key when they are not:

| Property | Meaning |
| --- | --- |
| `nightly_store_file` | path to the keystore |
| `nightly_store_password` | the store password |
| `nightly_key_alias` | the alias inside the keystore |
| `nightly_key_password` | that key's password |

On a local machine they belong in `~/.gradle/gradle.properties` (`%USERPROFILE%\.gradle` on
Windows) rather than on the command line, so that no plain `./gradlew` call ever produces an
unsigned-looking build by accident:

```properties
systemProp.nightly_store_file=/path/to/oepl-release.jks
systemProp.nightly_store_password=...
systemProp.nightly_key_alias=gadgetbridge-oepl
systemProp.nightly_key_password=...
```

That file sits in the user's home directory, outside any checkout, so the credentials cannot
be committed by mistake.

### Setting up a fork

Create a key once:

```sh
keytool -genkeypair -v \
  -keystore oepl-release.jks -storetype PKCS12 \
  -alias gadgetbridge-oepl \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10950 \
  -storepass CHOOSE_ONE -keypass CHOOSE_ONE \
  -dname "CN=Your Build, O=you, C=DE"
```

Turn it into one line of base64:

```sh
base64 -w0 oepl-release.jks > oepl-release.jks.base64.txt   # macOS: base64 -i ... -o ...
```

Then add four repository secrets under **Settings -> Secrets and variables -> Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the one line from `oepl-release.jks.base64.txt` |
| `KEYSTORE_PASSWORD` | the `-storepass` chosen above |
| `KEY_ALIAS` | `gadgetbridge-oepl` |
| `KEY_PASSWORD` | the `-keypass` chosen above |

The workflow decodes the keystore into the runner's temporary directory, passes the four
properties to Gradle and deletes nothing else; without `KEYSTORE_BASE64` it warns and falls
back to a debug key.

Keep the keystore somewhere safe and **out of the repository**, with a backup. Losing it means
everyone who installed a release has to uninstall before they can update again.

## Building a release by hand

With the properties in `~/.gradle/gradle.properties` as above, a plain

```sh
./gradlew assembleMainlineNopebble
```

already produces a signed APK. To point a single build at a different keystore, pass the
properties on the command line; they override the ones from the properties file:

```sh
./gradlew assembleMainlineNopebble \
  -Dnightly_store_file=/path/to/other.jks \
  -Dnightly_store_password=... \
  -Dnightly_key_alias=... \
  -Dnightly_key_password=...
```

To check what came out of either:

```sh
apksigner verify --print-certs -v app/build/outputs/apk/mainline/nopebble/*.apk
```

Both the `v1` (JAR) and the `v2` signature scheme are applied, which covers every Android
version the app itself supports.

## Why the nopebble flavour

`mainlineNopebble` is the upstream nightly flavour that renames the Pebble content provider, so
it can be installed next to a normal Gadgetbridge. Its application id ends in
`.nightly_nopebble`, which is what keeps the two apart. Some Pebble integrations do not work in
that flavour; nothing in the weather display depends on them.
