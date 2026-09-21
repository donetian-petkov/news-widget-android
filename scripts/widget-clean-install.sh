#!/usr/bin/env bash
# Clean-room widget check: wipes the app, installs the current debug build, then
# adds the widget to the home screen the way a person would and screenshots it.
# Elements are located by name from a UI dump, so it survives layout changes.
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
"$ADB" shell input keyevent KEYCODE_HOME
sleep 3

say "Opening the widget picker"
"$ADB" shell input swipe 540 1000 540 1000 1200
sleep 3
tap_named "widgets" text "the Widgets menu entry"
sleep 4
tap_named "ai news" any "the AI News group"
sleep 3

say "Dragging the widget onto the home screen"
preview="$("$HERE/ui_find.py" "$ADB" "ai news widget" any || "$HERE/ui_find.py" "$ADB" "4 × 3" any)"
read -r px py <<<"$preview"
"$ADB" shell "input motionevent DOWN $px $py; sleep 2; \
  input motionevent MOVE $((px + 5)) $((py - 10)); sleep 0.4; \
  input motionevent MOVE $((px + 10)) $((py - 60)); sleep 0.4; \
  input motionevent MOVE $((px + 10)) $((py - 120)); sleep 0.4; \
  input motionevent MOVE $((px + 5)) $((py - 180)); sleep 0.4; \
  input motionevent MOVE $px $((py - 240)); sleep 1; \
  input motionevent UP $px $((py - 240))"
sleep 5

say "Finishing setup"
tap_named "use current settings" any "the setup screen" || true
sleep 6
"$ADB" shell input keyevent KEYCODE_HOME
sleep 6

say "Widget instances"
"$ADB" shell dumpsys appwidget | grep -c "hostCategory" || true

say "Errors"
"$ADB" logcat -d | grep -iE "GlanceAppWidget.*Error|FATAL EXCEPTION" | tail -5 || true

say "Screenshot -> $SHOT"
"$ADB" shell screencap -p /sdcard/widget-check.png
"$ADB" pull /sdcard/widget-check.png "$SHOT" >/dev/null
echo done
