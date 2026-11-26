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
`adb install` often times out on the unit's weak Wi-Fi. What works: `adb push <apk> /data/local/tmp/`,
compare `md5sum`, then `adb shell pm install -r /data/local/tmp/<apk>` and delete the file.
Wireless debugging switches itself off whenever the unit changes Wi-Fi network.
Developer options on the unit: long-press **Settings** in this launcher.

## Code map (`app/src/main/java/com/harislcd/launcher/`)
- `MainActivity` — home screen. Picks the theme layout, wires tiles, 1 s tick for clock (12 h AM/PM),
  Now Playing and the lights check (night mode switches only after 2 consecutive mismatches, so a
  blip doesn't flip the theme). GPS speed is smoothed (`steadySpeed`) to stop 0/1 flicker.
  Weather from Open-Meteo (`WeatherClient`, no API key). Now Playing via
  `MediaSessionManager.getActiveSessions` + `MediaListener` (a NotificationListenerService).
- `ThemeStore` — 14 themes x light/dark; modes `auto` / `light` / `dark`; prefs file `carlcd`
  (keys `theme`, `mode`, `favourites`); maps theme to style and layout.
- `Scale` — overrides density so every screen behaves like 1280x648 dp; fontScale fixed at 1.
- `CarView` — the static car with tap zones: lights toggle (auto 9 s cycle resumes 10 s after a tap),
  mirror fold (the head swings up about its hinge against the window), wheels spin, indicators flash
  with a relay rhythm (700 ms period, quick rise, slower fade; tap a fog lamp). D-pad OK toggles lights.
- **Turning the car:** a sideways drag on the car steps through six photos of the owner's car
  (`turn_0..5.png`: front, front right, right, rear, left, front left; sizes and plate corners in
  `CarTurn.java`, sources with the plates blanked in `tools/gen/turn/`). A side view shows only the photo
  and the plate (no lights, mirror, wheels or indicators); a tap, or 20 s without a touch, returns to the
  theme's own picture. The photos were cut out with macOS subject detection (Vision); blank the plate in
  any new photo before it goes in the repo.
- **Number plate:** the pictures ship blank (public repo). The owner types the plate on the Themes page
  ("Number plate"); it is kept in prefs (`plate`) on the unit only, never logged, and `CarView` draws it
  on the blank plate through the four `plate` corners in `geo_<picture>.json` (perspective warp).
- `CarParts` — **generated** per-theme geometry (leaning wheel ellipses, mirror patch, hinge and fold
  angle, fog lamp tap points). The float layout is documented in the file; `taptest.py` reads it too.
- `CompassView` — the owner's reference design, identical in every theme **and in both light and dark**
  (white rim, silver band, white face, red N, long orange/red needle = heading, N up). Sizes under
  100 dp drop the degree numbers. `setPosition(lat, lon)` places the **Kaaba (Qibla) marker** on the rim.
  Focusable (draws its own round focus ring); a tap on it or on the heading text opens `CompassActivity`.
- `CompassActivity` — the big compass: the same dial at 520 dp, the heading in large letters, the Qibla
  bearing and "Turn left/right N°". Starts from the heading and position the home screen passes in, then
  listens to GPS (and the rotation sensor where there is one) itself. A tap on the dial or Back closes it.
- `ThemesActivity` — theme cards with previews + mode pills. `AppsActivity` — all apps, search,
  favourites (star tap, touch-hold, or hold OK on a remote).

Resources: theme colours are attributes (`values/attrs.xml`: cFg, cAccent, cCard, ...) defined per
theme in `values/theme_colors.xml` and `values-night/theme_colors.xml`. Car images, overlays and
theme previews live in `drawable-nodpi/` (dark previews in `drawable-night-nodpi/`).

## In-app updates (GitHub Releases)
- `Updater.java`: the home screen reads `github.com/harisbinrehan/car-lcd/releases/latest/download/latest.json`
  every 3 h (public repo, no token; a plain download, so GitHub's 60-calls-an-hour API limit does not
  apply). If that file is missing it falls back to `api.github.com/.../releases/latest` (releases up to
  2.3 have no `latest.json`). A release must carry `car-lcd-<versionCode>.apk` and `latest.json`
  (`{code, name, notes, url, size}`, written by `tools/release.sh`); if the code is higher than the
  installed one, a dialog offers it (Install now / Later = 12 h snooze).
  Themes page has "Version x · Check for updates". The download shows a progress dialog ("Hide" leaves
  it running; Install now shows it again), **resumes** with an HTTP Range request after a dropped
  connection and keeps the part in the cache folder (`update-<code>.apk`) between tries — the unit's
  internet is slow (about 9 KB/s was measured) and a restart-from-zero download never finished. If it
  gives up, a dialog offers "Try again". The APK is verified (package, versionCode, same signing
  certificate) and installed via `PackageInstaller`; Android shows its own confirm screen.
- First time only, Android asks to "Allow from this source"; the app is killed when that changes, so
  the pending release is kept in prefs (`upd_wait_*`) and the install continues on return.
- After an update Android 11 won't let the app reopen itself (background activity start blocked);
  the screen shows the fallback launcher until Home is pressed. Car LCD stays the default Home.
- Publish: `tools/release.sh <versionName> "<notes>"` (bumps versionCode, signed release build, commit,
  tag `v<name>`, push, `gh release create`). Ask the owner before every release.
- **Signing key**: `~/.carlcd/signing.jks` + `~/.carlcd/signing.properties` (outside the repo). It is
  the same key as the Mac's debug keystore, so debug and release builds upgrade each other. If it is
  lost, no future update can install over the app (uninstall/reinstall needed). Keep a backup.
- Testing without publishing: debug builds read a `upd_api` pref (URL of a local `latest.json`, in
  either the format above or GitHub's API format), and
  the debug manifest allows cleartext so the emulator can use `http://10.0.2.2:<port>/`.

## Bug reports ("Send report" on the Themes page)
- `Diag.java` keeps the app's own diary in the app's files folder: `diag.log` (rolls to `diag.1.log` at
  200 KB) and `crash.txt` (uncaught exceptions, written by the handler set in `App.java`). It records
  what was opened, theme/lights switches, Now Playing changes, update steps and errors. **Never log the
  GPS position.** Use `Diag.log`, `Diag.err`, and `Diag.logChange` for anything checked every second.
- Nothing is sent until the owner taps **Send report**; that files one issue (unit, clock and state
  summary + crashes + the last ~42 KB of log) in the **private** repo `harisbinrehan/car-lcd-reports`.
  Read reports with `gh issue list -R harisbinrehan/car-lcd-reports` / `gh issue view <n> -R ...`.
- **Report key**: `~/.carlcd/reports.properties` with `token=<fine-grained token: that repo only, Issues
  read/write>`. `app/build.gradle` puts it in `BuildConfig.REPORT_KEY` (lightly scrambled). It can be
  pulled out of the public APK, which is why it must stay limited to creating issues in that one repo.
  Without the file the app still builds and the button says reports are not set up.
- Testing: debug builds read a `rep_api` pref (URL of a local server that answers 201 `{"number":1}`).
  `adb shell am crash com.harislcd.launcher` exercises the crash capture.

## Generated files — do not hand-edit
`tools/gen/` regenerates them; change the generator and rerun (run from `tools/gen`, it reads
`sizes.json` etc. from the current folder):
- Nine car pictures, `cut_<picture>.png` (cut out of the owner's mockups, plates blanked, lamps on). Each
  has its geometry in `geo_<picture>.json`, in that picture's pixels (lamps, indicator sections, fog
  lamps, wheels, mirror outline, hinge and fill directions; the format is described at the top of
  `carlib.py`, which also holds the shared image code). `THEMES` in `carlib.py` says which picture a
  theme shows and `BOX` the slot it gets: the nine older themes have their own picture, classic shares
  violet's, and nordic / midnight / mocha / carbon reuse gold / noir / sky / crimson. `cut_premium.png`
  + `geo_premium.json` (the single studio picture 2.3 to 2.5 used) are kept as a spare. Needs Pillow,
  numpy, opencv.
- `cars.py <res>` → `car_<theme>.png` (lamps **off**, derived) and `lights_<theme>.png` (the picture's real
  lit pixels; fully faded in it is the original). Fog lamps are not lit; they are only the indicator
  tap points.
- `parts.py <res> <javaDir>` → `wheel_f/r`, `mirror`, `mirrorlit`, `mirrorbg`, `blink` overlays + `CarParts.java`.
  Indicators are the lamp's own pixels turned amber, not painted blobs; the fog lamps do not flash.
- `layouts.py <res>` → `activity_main_<theme>.xml` for 13 themes and the stage block of the classic `activity_main.xml`.
  Icon-only buttons (`mini`) are 60 dp to touch; Now Playing uses 72 dp previous/next and an 84 dp play.
Theme previews are screenshots of the real screens from the emulator (`sweep.sh light|dark <themes>`),
with the 24 px status bar and 48 px navigation bar cropped off and scaled to 480x243; light ones go in
`drawable-nodpi/`, dark ones in `drawable-night-nodpi/`. Redo them whenever a layout or car changes.
Adding a theme: colours in both `theme_colors.xml`, a style in `themes.xml`, three entries in
`ThemeStore`, a layout block in `layouts.py`, a picture and slot in `carlib.py`, then previews.

## Testing
- Emulator AVD: 1280x720 @ 160 dpi, Android 30 google_apis arm64 (`emulator-5554`, headless works).
- `uiautomator dump` hangs on the home screen (it never goes idle because of the animations). Read
  view bounds from `adb shell dumpsys activity com.harislcd.launcher/.MainActivity` instead
  (`tools/test/pos.sh`).
- `tools/test/`: `emu.sh` (set theme/mode, screenshot), `sweep.sh` (all themes), `taptest.py`
  (car parts in every theme; an odd single-theme failure is usually timing, rerun that theme),
  `dpadwalk.py` (remote reachability), `unit.sh` (flaky-link adb).
  They use `$ADB` (default `adb`) and write screenshots to `$CARLCD_WORK` (default `/tmp/carlcd`).
- Check every change in **all 14 themes x light and dark** for clipping, and with a remote (D-pad):
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
- Car is static; only parts move (lights, mirrors, wheels, indicators). Use the owner's own car pictures:
  the nine from the mockups, one per theme. The single "premium" studio picture was tried in 2.3 to 2.5
  and the owner asked for the previous pictures back (they liked the 2.3 lights, mirror and indicators,
  which are kept). Don't swap in other car art, don't mirror a picture.
- Large touch targets and fonts (used while driving); nothing clipped; 12-hour clock.
- Change only what was asked; suggest extras instead of slipping them in.
- Never commit or push without the owner approving that specific commit/push.
- No `Co-Authored-By: Claude` (or any Claude attribution) in commit messages or PRs.

## Open requests (as of 2026-11-26)
- Clock on the unit is wrong — investigate (time zone / format on the unit vs. the app).
- Remote diagnostics: built as "Send report" (see Bug reports above). The owner still has to create the
  report key and the first release carrying it has to reach the unit.
