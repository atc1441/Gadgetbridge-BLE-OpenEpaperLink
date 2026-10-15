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

## Give it a signing key

Without a key the build falls back to Android's debug signing, and the debug key on a CI runner
is generated fresh every time. Two releases then have two different signatures and Android
refuses to install the second over the first — which makes updating painful for everybody who
uses the releases.

Create a key once:

```sh
keytool -genkeypair -v \
  -keystore release.jks \
  -alias gadgetbridge-oepl \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass CHOOSE_ONE -keypass CHOOSE_ONE \
  -dname "CN=Gadgetbridge OEPL, OU=, O=, L=, S=, C=DE"
```

Turn it into one line of base64:

```sh
base64 -w0 release.jks > release.jks.base64   # macOS: base64 -i release.jks -o release.jks.base64
```

Then add four repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the contents of `release.jks.base64` |
| `KEYSTORE_PASSWORD` | the `-storepass` you chose |
| `KEY_ALIAS` | `gadgetbridge-oepl` |
| `KEY_PASSWORD` | the `-keypass` you chose |

Keep `release.jks` somewhere safe and **out of the repository**. Losing it means everyone who
installed a release has to uninstall before they can update again.

## Building a release by hand

```sh
./gradlew assembleMainlineNopebble \
  -Dnightly_store_file=/path/to/release.jks \
  -Dnightly_store_password=... \
  -Dnightly_key_alias=gadgetbridge-oepl \
  -Dnightly_key_password=...
```

Leaving the properties off builds a debug signed APK, which is fine for a phone that has never
had a release build on it.

## Why the nopebble flavour

`mainlineNopebble` is the upstream nightly flavour that renames the Pebble content provider, so
it can be installed next to a normal Gadgetbridge. Its application id ends in
`.nightly_nopebble`, which is what keeps the two apart. Some Pebble integrations do not work in
that flavour; nothing in the weather display depends on them.
