# Deen — an Islamic tool for the Light Phone III

A quiet, offline companion built on the official [Light SDK](https://github.com/lightphone/light-sdk), in the LightOS look.

- **Qibla** — live compass needle with true-north correction and a click when you face the Kaaba. On a phone without a compass sensor it falls back to directions from the sun.
- **Prayer times** — calculated on the phone from your GPS (or your Light dashboard default location), 12 calculation methods (chosen automatically by country), Hanafi/standard Asr, per-prayer minute adjustments.
- **Prayer alerts** — exact-minute notifications for each prayer you switch on, with sound or vibrate-only, an optional early reminder, and optional morning/evening azkar reminders. Alerts keep working after a reboot.
- **Quran** — the complete Uthmani text in the Amiri Quran typeface, with Black, Light or Cream backgrounds, adjustable size, surah and juz index, and it remembers where you stopped.
- **Azkar** — morning and evening adhkar from Hisn al-Muslim, one at a time with a tap counter, optional English, and sources.

Everything works offline. The tool never uses the network, and your location stays on the phone.

## Install on your Light Phone III

1. Download the latest `Deen-x.y.z.apk` from this repo's **Releases** page.
2. Sideload it. Either:
   - **ADB** (works today): enable Developer Options and USB debugging on the phone, connect it, then run
     `adb install -r Deen-1.0.0.apk`. In LightOS, allow the tool in the tools settings; LightOS will warn that it is not Light-vetted.
   - **Tool Manager** (once Light rolls it out): turn on developer mode on the [Light dashboard](https://dashboard.thelightphone.com), open the Tool Manager, upload a text file containing the SDK development key fingerprint
     `B9C33E29B0CCAD2BFF11ACAB55F65A3C517EF4BC92CD9C77785366FA353D5F28` under *Developer Keys*, then upload the APK under *Upload Tools*. See `docs/sideloading`.
3. Open **Deen**, allow location, and allow notifications in *Settings → Prayer alerts*.

## Why this is a fork of the Light SDK

LightOS tools must be built with the Light SDK, which (as of 0.2.1) blocks direct access to phone sensors and to exact alarms. This repository is the SDK with a small, clearly marked extension in `sdk/client/src/main/kotlin/com/thelightphone/sdk/device/`:

- `LightCompass.kt` — rotation-vector compass headings, corrected to true north.
- `LightAlerts.kt` — exact, doze-proof alarms that post notifications, re-armed after reboot.

The tool itself (`tool/`) still follows every SDK rule: no Context access, no reflection, only allow-listed permissions. Because of the extension, this build can't be submitted to Light's Tool Library as-is. If Light adds compass and alarm APIs, swap them in and delete those two files.

## Build it yourself

Pushing to `main` builds the APK with GitHub Actions and publishes a release. Locally: `./gradlew :tool:assembleDebug -DlightSdk.toolOnly=true` (JDK 17, Android SDK 36). Prayer-time tests: `./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true`.

## Credits

- Prayer time astronomy ported from [adhan-js](https://github.com/batoulapps/adhan-js) (MIT), after Meeus, *Astronomical Algorithms*.
- Quran text: King Fahd Glorious Quran Printing Complex Uthmani script, via [quranenc.com](https://quranenc.com) / [quran-json](https://github.com/risan/quran-json) (CC BY 4.0).
- Font: [Amiri Quran](https://github.com/aliftype/amiri) by Khaled Hosny (SIL OFL 1.1, see `tool/FONT_LICENSE_OFL.txt`).
- Azkar and city list: [muslim-data](https://github.com/my-prayers/muslim-data-flutter) (Apache 2.0).
- Light SDK © The Light Phone (MIT), see `LICENSE` and `docs/LIGHT_SDK_README.md`.
