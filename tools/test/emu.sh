#!/bin/bash
# emu.sh set <theme> <mode>   -> write prefs and restart the launcher
# emu.sh shot <name>          -> screenshot to $CARLCD_WORK/e_<name>.png (default /tmp/carlcd)
S="${CARLCD_WORK:-/tmp/carlcd}"; mkdir -p "$S"
P="${ADB:-adb}"; E="-s ${DEV:-emulator-5554}"
case "$1" in
 # upd_checked = now, so a test restart does not ask GitHub for updates: the public API allows only
 # 60 requests an hour per network, and using them up here blocks the update check on the car too
 set) printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"theme\">%s</string>\n    <string name=\"mode\">%s</string>\n    <long name=\"upd_checked\" value=\"%s000\" />\n</map>\n" $2 $3 $(date +%s) > $S/pref.xml
      "$P" $E shell am force-stop com.harislcd.launcher
      "$P" $E shell "run-as com.harislcd.launcher sh -c 'mkdir -p shared_prefs && cat > shared_prefs/carlcd.xml'" < $S/pref.xml
      "$P" $E shell am start -n com.harislcd.launcher/.MainActivity >/dev/null 2>&1 ;;
 shot) "$P" $E exec-out screencap -p > $S/e_$2.png ;;
esac
