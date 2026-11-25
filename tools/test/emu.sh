#!/bin/bash
# emu.sh set <theme> <mode>   -> write prefs and restart the launcher
# emu.sh shot <name>          -> screenshot to $CARLCD_WORK/e_<name>.png (default /tmp/carlcd)
S="${CARLCD_WORK:-/tmp/carlcd}"; mkdir -p "$S"
P="${ADB:-adb}"; E="-s ${DEV:-emulator-5554}"
case "$1" in
 set) printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"theme\">%s</string>\n    <string name=\"mode\">%s</string>\n</map>\n" $2 $3 > $S/pref.xml
      "$P" $E shell am force-stop com.harislcd.launcher
      "$P" $E shell "run-as com.harislcd.launcher sh -c 'mkdir -p shared_prefs && cat > shared_prefs/carlcd.xml'" < $S/pref.xml
      "$P" $E shell am start -n com.harislcd.launcher/.MainActivity >/dev/null 2>&1 ;;
 shot) "$P" $E exec-out screencap -p > $S/e_$2.png ;;
esac
