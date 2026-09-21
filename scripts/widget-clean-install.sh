#!/usr/bin/env bash
# Clean-room widget check: wipes the app, installs the current debug build, then
# asks the launcher to pin the widget to the home screen and screenshots it.
set -euo pipefail

# Target the emulator by default: a plugged-in phone must not be touched by tests.
ANDROID_SERIAL="${ANDROID_SERIAL:-$("$HOME/Library/Android/sdk/platform-tools/adb" devices | awk '/emulator-/{print $1; exit}')}"
export ANDROID_SERIAL
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APK="${APK:-android/app/build/outputs/apk/debug/app-debug.apk}"
SHOT="${SHOT:-/tmp/ai-news-widget.png}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=com.ainews.android

say() { printf '\n== %s\n' "$1"; }

tap_named() { # tap_named <needle> <text|desc|any> <description>
  local point
  if ! point="$("$HERE/ui_find.py" "$ADB" "$1" "$2")"; then
    echo "could not find $3" >&2
    return 1
  fi
  "$ADB" shell input tap $point
}

say "Uninstalling $PKG"
"$ADB" uninstall "$PKG" >/dev/null 2>&1 || true

say "Installing $APK"
"$ADB" install -r "$APK" >/dev/null

say "First run"
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 15

say "Asking the launcher for the widget"
tap_named "open navigation" any "the menu button" || "$ADB" shell input tap 60 200
sleep 2
for _ in 1 2 3 4 5 6; do
  "$HERE/ui_find.py" "$ADB" "add the widget" any >/dev/null && break
  "$ADB" shell input swipe 300 1600 300 1000 200
  sleep 1
done
tap_named "add the widget" any "the add-widget entry"
sleep 4
tap_named "add" any "the launcher's confirm button"
sleep 5
"$ADB" shell input keyevent KEYCODE_HOME
sleep 8

say "Widget instances"
"$ADB" shell dumpsys appwidget | grep -c "com.ainews.android/com.ainews.android.widget.NewsWidgetReceiver" || true

say "Errors"
"$ADB" logcat -d | grep -iE "GlanceAppWidget.*Error|FATAL EXCEPTION" | tail -5 || true

say "Screenshot -> $SHOT"
"$ADB" shell screencap -p /sdcard/widget-check.png
"$ADB" pull /sdcard/widget-check.png "$SHOT" >/dev/null
echo done
