# CLAUDE.md — Car LCD

Context for working on this project. Read this first; `README.md` is the user-facing summary.

## What this is
A custom **Home app (launcher)** for an aftermarket Android car head unit fitted to a Toyota Vitz
hybrid. It replaces the look of the home screen only. The unit's own software, stock launcher and
hardware buttons are **never** modified, disabled or remapped. The app is an optional HOME app the
user can pick, and the stock launcher must keep working.

Why a launcher and not a firmware change: the unit's firmware cannot be updated or replaced (no
public firmware, password-locked updater, no root), and its physical Home button is wired in
firmware to the stock launcher. Don't propose firmware flashing, rooting or button remapping.

## The head unit (target hardware)
- Model `auto_rk_t11`, Rockchip RK3326, **Android 11 (API 30)**, 2 GB RAM.
- Screen 1280x720 @ 160 dpi; the system bar takes 72 px at the **top**, so the app area is 1280x648 dp.
- Vendor apps the tiles open (package names may differ on other units):
  | Tile | Target |
  |---|---|
  | Radio | `com.imotor.fmam` |
  | Bluetooth music | `com.imotor.btmusic` |
  | Music | `com.imotor.music` |
  | Android Auto | `com.imotor.phoneconnect/.AndroidAuto` (explicit component; also `.Carplay`, `.Airplay`, `.AndroidLink`) |
  | Equalizer | `com.imotor.soundequalizer/.MainActivity` (no launcher entry, so explicit component) |
  | YouTube | `com.google.android.youtube` (must open at any time, never "parked only") |
- Android Auto runs inside a background host app `com.carletter.car/.ui.CarletterActivity` with no
  launcher entry. Its media session can be the "now playing" one, so never assume a session's
  package has a launch intent (see `MainActivity.openPlayer()`).
- **Parking lights signal:** system setting `carletter_night_mode`, **1 = lights on, 0 = off**.
  Auto mode: lights off = light theme, lights on = dark theme.
- No compass sensor: heading comes from GPS bearing while moving. No `curl`/`wget` on the unit.

## Build
Plain **Java + Android Views** (no Kotlin, no Compose) to stay small and fast on 2 GB RAM.
```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
gradle :app:assembleDebug --no-daemon --console=plain -q     # Gradle 8.2, AGP 8.1.4
```
- APK: `app/build/outputs/apk/debug/app-debug.apk` (~10 MB, debug-signed).
- Package `com.harislcd.launcher`, minSdk 24, targetSdk 33, compileSdk 34.
- `app/build.gradle` forces kotlin-stdlib 1.8.22 to avoid duplicate-class errors from AndroidX.
- `local.properties` (sdk.dir) is machine-specific and git-ignored.

## Install on the unit (wireless adb)
The wireless adb link is flaky and the port changes every time wireless debugging is toggled.
Find it with `adb mdns services` or ask the user for the port on the unit's Wireless debugging
screen. Wrap every adb call in a time limit. Pairing failed with adb 37; adb 34.0.5 works.
```sh
adb pair <ip>:<pair-port> <code>        # only if connect is refused
adb connect <ip>:<port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd notification allow_listener com.harislcd.launcher/com.harislcd.launcher.MediaListener
adb shell pm grant com.harislcd.launcher android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.harislcd.launcher android.permission.ACCESS_COARSE_LOCATION
```
Re-grant the notification listener after reinstalling; Now Playing is blank without it.
Developer options on the unit: long-press **Settings** in this launcher.

## Code map (`app/src/main/java/com/harislcd/launcher/`)
- `MainActivity` — home screen. Picks the theme layout, wires tiles, 1 s tick for clock (12 h AM/PM),
  Now Playing and the lights check (night mode switches only after 2 consecutive mismatches, so a
  blip doesn't flip the theme). GPS speed is smoothed (`steadySpeed`) to stop 0/1 flicker.
  Weather from Open-Meteo (`WeatherClient`, no API key). Now Playing via
  `MediaSessionManager.getActiveSessions` + `MediaListener` (a NotificationListenerService).
- `ThemeStore` — 10 themes x light/dark; modes `auto` / `light` / `dark`; prefs file `carlcd`
  (keys `theme`, `mode`, `favourites`); maps theme to style and layout.
- `Scale` — overrides density so every screen behaves like 1280x648 dp; fontScale fixed at 1.
- `CarView` — the static car with tap zones: lights toggle (auto 9 s cycle resumes 10 s after a tap),
  mirror fold/unfold, wheels spin, indicators blink. D-pad OK toggles lights.
- `CarParts` — **generated** per-theme geometry (wheel ellipses, mirror rect and hinge, fog/indicator points).
- `CompassView` — classic compass (silver ring, 0–315 numbers, red N, orange/red needle); white
  face in light, dark face in dark; small sizes drop the numbers.
- `ThemesActivity` — theme cards with previews + mode pills. `AppsActivity` — all apps, search,
  favourites (star tap, touch-hold, or hold OK on a remote).

Resources: theme colours are attributes (`values/attrs.xml`: cFg, cAccent, cCard, ...) defined per
theme in `values/theme_colors.xml` and `values-night/theme_colors.xml`. Car images, overlays and
theme previews live in `drawable-nodpi/` (dark previews in `drawable-night-nodpi/`).

## In-app updates (GitHub Releases)
- `Updater.java`: the home screen checks `api.github.com/repos/harisbinrehan/car-lcd/releases/latest`
  every 3 h (public repo, no token). A release must carry an asset named `car-lcd-<versionCode>.apk`;
  if that code is higher than the installed one, a dialog offers it (Install now / Later = 12 h snooze).
  Themes page has "Version x · Check for updates". The APK is verified (package, versionCode, same
  signing certificate) and installed via `PackageInstaller`; Android shows its own confirm screen.
- First time only, Android asks to "Allow from this source"; the app is killed when that changes, so
  the pending release is kept in prefs (`upd_wait_*`) and the install continues on return.
- After an update Android 11 won't let the app reopen itself (background activity start blocked);
  the screen shows the fallback launcher until Home is pressed. Car LCD stays the default Home.
- Publish: `tools/release.sh <versionName> "<notes>"` (bumps versionCode, signed release build, commit,
  tag `v<name>`, push, `gh release create`). Ask the owner before every release.
- **Signing key**: `~/.carlcd/signing.jks` + `~/.carlcd/signing.properties` (outside the repo). It is
  the same key as the Mac's debug keystore, so debug and release builds upgrade each other. If it is
  lost, no future update can install over the app (uninstall/reinstall needed). Keep a backup.
- Testing without publishing: debug builds read a `upd_api` pref (URL of a local `latest.json`), and
  the debug manifest allows cleartext so the emulator can use `http://10.0.2.2:<port>/`.

## Generated files — do not hand-edit
`tools/gen/` regenerates them; change the generator and rerun (run from `tools/gen`, it reads
`sizes.json` etc. from the current folder):
- `cars.py <res>` → `car_<theme>.png`, `lights_<theme>.png` from the `cut_*.png` cutouts.
- `parts.py <res> <javaDir>` → wheel/mirror/indicator overlays + `CarParts.java` (positions in `parts.json`).
- `layouts.py <res>` → `activity_main_<theme>.xml` for 9 themes and the stage block of the classic `activity_main.xml`.
Theme previews are screenshots of the real screens from the emulator, cropped to 480x243.

## Testing
- Emulator AVD: 1280x720 @ 160 dpi, Android 30 google_apis arm64 (`emulator-5554`, headless works).
- `uiautomator dump` hangs on the home screen (it never goes idle because of the animations). Read
  view bounds from `adb shell dumpsys activity com.harislcd.launcher/.MainActivity` instead
  (`tools/test/pos.sh`).
- `tools/test/`: `emu.sh` (set theme/mode, screenshot), `sweep.sh` (all themes), `taptest.py`
  (car parts in every theme), `dpadwalk.py` (remote reachability), `unit.sh` (flaky-link adb).
  They use `$ADB` (default `adb`) and write screenshots to `$CARLCD_WORK` (default `/tmp/carlcd`).
- Check every change in **all 10 themes x light and dark** for clipping, and with a remote (D-pad):
  every control must be reachable and show a focus ring.
- Things only the real unit can prove: radio/Android Auto/Bluetooth playback and station
  switching, the parking-light signal, GPS speed/heading, the physical Home button, remote hold.

## Public repo — keep personal details out
The GitHub repo is public. Never commit: the owner's name in the UI, a precise home/default
location (the weather default is city-level on purpose), the car's real number plate (plates in
all car images are blanked; keep it that way when adding images), IPs/ports/pairing codes, or
machine-specific paths.

## Product rules from the owner
- Keep the apps they use: Radio, Bluetooth, Android Auto, Equalizer, YouTube, Music, Apps, Themes, Settings.
- Every feature must work in **every** theme and in both light and dark.
- Car is static; only parts move (lights, mirrors, wheels, indicators). Use the exact car images from
  the owner's mockups; don't swap in other car art.
- Large touch targets and fonts (used while driving); nothing clipped; 12-hour clock.
- Change only what was asked; suggest extras instead of slipping them in.
- Never commit or push without the owner approving that specific commit/push.
- No `Co-Authored-By: Claude` (or any Claude attribution) in commit messages or PRs.

## Open requests (as of 2026-11-26)
- Indicator blink looks fake; mirror looks awkward when folded — redo both (check screenshots).
- Rotate the car with a finger drag (needs the car angle images from the owner, or a 3D approach).
- Clock on the unit is wrong — investigate (time zone / format on the unit vs. the app).
- Make the Now Playing player bigger and easier to use.
- Add the **Qibla** marker (Kaaba icon on the compass ring pointing to Mecca from GPS position).
- Install the latest build on the unit once over adb (needs the current wireless debugging port);
  from then on updates come from GitHub Releases.
- Remote diagnostics ("know what's happening in the app") — not built; owner to choose how.
