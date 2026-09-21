#!/usr/bin/env bash
# The check that runs after every widget change: a clean install, the widget pinned to the
# home screen, every control driven once, plus the background-refresh check.
set -uo pipefail

ANDROID_SERIAL="${ANDROID_SERIAL:-$("$HOME/Library/Android/sdk/platform-tools/adb" devices | awk '/emulator-/{print $1; exit}')}"
export ANDROID_SERIAL
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "$0")" && pwd)"

"$HERE/widget-actions.sh"
ACTIONS=$?

echo
echo "== Background refresh"
if "$ADB" shell dumpsys alarm | grep -q "RefreshAlarmReceiver"; then
  echo "PASS  the repeating refresh alarm is registered"
else
  echo "FAIL  the repeating refresh alarm is registered"
  ACTIONS=1
fi

exit "$ACTIONS"
