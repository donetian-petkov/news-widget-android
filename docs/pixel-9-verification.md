# Pixel 9 Build And Widget Verification

This project is intended to build directly on a Pixel 9 in Termux.

## Termux Setup

Install Termux from F-Droid, then install the development tools:

```bash
pkg update
pkg install git openjdk-21 gradle android-tools
```

Install Android SDK command-line tools inside Termux storage, accept licenses, and
export one of these:

```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Enable Developer Options and Wireless Debugging on the Pixel 9. Pair Termux adb
with the same phone:

```bash
adb pair <pairing-host>:<pairing-port>
adb connect <debug-host>:<debug-port>
adb devices
```

## Build And Install

```bash
git clone https://github.com/donetian-petkov/news-widget-android.git
cd news-widget-android/android
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Manual Verification

1. Launch AI News.
2. Confirm the first screen is the news feed, not settings.
3. Press `Refresh` while runtime is on.
4. Confirm `NEW` labels clear when no changed story set is fetched.
5. Press `Power off`.
6. Confirm the app status changes to `Runtime off - Fetch paused`.
7. Press `Power on`.
8. Confirm refresh is enabled again.
9. Press `1h`, then confirm the status says auto power-off is armed.
10. Press `Scan monitors`, then confirm alert matches appear when matching title
    or summary terms exist.
11. Add the AI News home-screen widget.
12. Confirm the widget shows feed title, runtime/fetch status, power, refresh,
    story rows, topic labels, `NEW` or `ALERT`, hide, and share.
13. Press widget `Hide` on a story.
14. Confirm the story disappears from both widget and app.
15. Press widget `Refresh`.
16. Confirm visible fetching/success or failure feedback appears in app/widget
    status.

## Useful Debug Commands

```bash
adb shell cmd appwidget list
adb logcat | grep -i "ainews\\|workmanager\\|glance"
adb shell dumpsys jobscheduler | grep -i ai-news -A 12
```
