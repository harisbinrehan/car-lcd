#!/bin/bash
# unit.sh connect            -> find the car unit, connect, print serial (or nothing)
# unit.sh run <secs> <adb args...>  -> run an adb command against the unit with a time limit
S="${CARLCD_WORK:-/tmp/carlcd}"; mkdir -p "$S"
P="${ADB:-adb}"
tm() { local t=$1; shift; ( "$@" 2>&1 & W=$!; ( sleep $t; kill $W 2>/dev/null ) & wait $W ); }
if [ "$1" = "connect" ]; then
  for try in 1 2 3; do
    PORT=$( ( "$P" mdns services 2>&1 & sleep 4; kill $! 2>/dev/null ) | grep -oE '192\.168\.1\.[0-9]+:[0-9]+' | head -1)
    [ -z "$PORT" ] && { sleep 3; continue; }
    tm 10 "$P" connect $PORT >/dev/null; sleep 1
    [ "$(tm 6 "$P" -s $PORT shell echo up | tr -d '\r')" = "up" ] && { echo "$PORT" > $S/port.txt; echo "$PORT"; exit 0; }
    tm 5 "$P" disconnect $PORT >/dev/null; sleep 2
  done
  exit 1
fi
if [ "$1" = "run" ]; then t=$2; shift 2; tm $t "$P" -s $(cat $S/port.txt) "$@"; fi
