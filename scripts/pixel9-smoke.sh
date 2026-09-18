#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$ANDROID_DIR"

gradle :app:assembleDebug

if command -v adb >/dev/null 2>&1; then
  adb devices
  if adb get-state >/dev/null 2>&1; then
    adb install -r "$APK"
    adb shell monkey -p com.ainews.android 1
    echo "Installed and launched AI News."
  else
    echo "Build passed. adb is installed but no device is connected."
  fi
else
  echo "Build passed. adb is not installed in this shell."
fi

echo "APK: $APK"
