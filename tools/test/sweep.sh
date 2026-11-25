#!/bin/bash
# sweep.sh <mode> theme...  -> screenshots e_<theme>_<mode>.png from the emulator
D="$(cd "$(dirname "$0")" && pwd)"
m=$1; shift
for t in "$@"; do $D/emu.sh set $t $m; sleep 6; $D/emu.sh shot ${t}_$m; done
