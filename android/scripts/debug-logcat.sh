#!/usr/bin/env bash
# Stream logcat for the turnTable app (debug). Re-run after reinstall / process restart (new PID).
set -euo pipefail
PKG="${1:-com.secondlifetech.turntable}"
PID="$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  echo "No running process for $PKG. Launch the app, then run this again." >&2
  exit 1
fi
echo "Logging $PKG pid=$PID (threadtime,color). Ctrl+C to stop." >&2
exec adb logcat -v threadtime,color --pid="$PID"
