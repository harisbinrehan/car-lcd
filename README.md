# Car LCD — custom home screen for the car head unit

A home-screen app for the `auto_rk_t11` head unit (Rockchip RK3326, Android 11, 1280x720).
It is an **optional** Home app: the stock launcher stays installed and enabled, and no hardware
button is remapped, so nothing in the unit's own software is changed.

- Package: `com.harislcd.launcher`, version 2.0 (versionCode 2), debug-signed
- APK after a build: `app/build/outputs/apk/debug/app-debug.apk`

## What it does

**Home screen, in every theme**
- Apps: Radio, Bluetooth, Android Auto, Equalizer, YouTube, Music, Apps, Themes, Settings.
  YouTube opens at any time.
- 12-hour clock with AM/PM and date, weather (Open-Meteo, no key), GPS speed (smoothed so it does
  not flicker between 0 and 1) and a compass showing the heading, with a Kaaba on its rim marking
  the direction of Mecca (Qibla) from where the car is.
  Tap the compass for a full-screen one with the heading in large letters and how far to turn to
  face the Qibla.
- **Now Playing**: shows the track or station and the app it comes from (radio, Android Auto,
  music, YouTube, ...) with previous / play-pause / next. With nothing playing, play opens Music.
- **The car** is a still image with moving parts:
  - tap the car: lights on / off; the automatic light cycle resumes after 10 seconds
  - tap the mirror: it folds back against the window / opens again
  - while driving, the wheels turn in proportion to the speed
  - tap a tyre: the wheels spin (stop on a second tap, or after 10 seconds)
  - tap a fog lamp: the indicators flash, in the headlamps and on the mirror (stop on a second tap,
    or after 30 seconds)
- Everything is reachable with a remote (arrow keys + OK).
- Hold **Settings** to open Developer options.

**Themes page** (Themes button on Home and on the Apps page)
- 14 themes, each with its own layout: Classic, Crimson, Ivory, Gold, Forest, Violet, Silver, Sky,
  Noir, Amber, Nordic, Midnight, Mocha, Carbon. Each card shows a preview of that theme.
- Every theme has a light and a dark version. Mode: **Auto (car lights)**, **Light**, **Dark**.
  Auto follows the parking lights: lights off = light, lights on = dark.

**Apps page**
- Every installed app, a search box, and favourites: tap the star (or hold the tile / hold OK on
  the remote) to keep an app at the top.

**Updates**
- The app checks this repo's GitHub Releases and offers a newer version on the car screen
  (any internet connection). Install now downloads, checks and installs it; Android asks to confirm.
  Themes page: "Check for updates". Publish a version with `tools/release.sh 2.1 "what's new"`.

**Bug reports**
- The app keeps a small log of its own (what was opened, theme and lights changes, errors, crashes;
  never the GPS position). Nothing is sent by itself: "Send report" on the Themes page files it as an
  issue in a private repo. To use this in your own build, point `Diag.REPO` at a private repo of yours
  and put a token limited to that repo's issues in `~/.carlcd/reports.properties` (`token=...`).

## Apps it links to (edit in `MainActivity.java` if a package differs)
| Tile | Target |
|------|--------|
| Radio | `com.imotor.fmam` |
| Bluetooth | `com.imotor.btmusic` |
| Music | `com.imotor.music` |
| Android Auto | `com.imotor.phoneconnect/.AndroidAuto` |
| Equalizer | `com.imotor.soundequalizer/.MainActivity` |
| YouTube | `com.google.android.youtube` |

The parking-light signal is the system setting `carletter_night_mode` (1 = lights on).

## Install (wireless adb)
```sh
adb connect <unit-ip>:<port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Now Playing needs notification access; location is for speed/heading/weather
adb shell cmd notification allow_listener com.harislcd.launcher/com.harislcd.launcher.MediaListener
adb shell pm grant com.harislcd.launcher android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.harislcd.launcher android.permission.ACCESS_COARSE_LOCATION
```
Without adb, notification access can be given on the unit under Settings > Apps > Special app
access > Notification access.

## Build
```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
gradle :app:assembleDebug        # Gradle 8.2, AGP 8.1.4
```
Plain Java + Android Views (no Kotlin/Compose), about 10 MB, about 46 MB of memory in use.

## Regenerating themes
The thirteen non-classic home layouts, the car images, the wheel / mirror / indicator overlays and
`CarParts.java` are generated. Do not edit them by hand; change the generator and rerun:
```sh
cd tools/gen
python3 cars.py ../../app/src/main/res          # car + lights images from the cut_*.png pictures
python3 parts.py ../../app/src/main/res ../../app/src/main/java/com/harislcd/launcher
python3 layouts.py ../../app/src/main/res       # all theme layouts
```
`tools/test/` holds the emulator test scripts (theme sweep, car tap test, remote walk). Set `ADB`
to your adb binary (default `adb`); screenshots go to `CARLCD_WORK` (default `/tmp/carlcd`).
