# AI News Android Widget

Native Android implementation of AI News with a real Android home-screen widget.

This repository is intended for the Pixel 9 on-device workflow described in
[`docs/android-native-implementation-plan.md`](docs/android-native-implementation-plan.md).

## Goal

- Build a native Android app, not a web wrapper.
- Provide a normal news-reading app experience.
- Add a real Android AppWidget with runtime status, power, refresh, hide, share,
  and story actions.
- Keep the default implementation phone-native, with optional remote backend
  support later.

## Current State

The repository now contains a buildable native Android app under `android/`.

Implemented so far:

- Compose news feed and story detail screen.
- Real Android home-screen widget through Jetpack Glance.
- Visible runtime/fetch status, power, refresh, hide, share, and story actions.
- Room-backed story storage.
- DataStore-backed runtime preferences.
- WorkManager refresh, monitor scan, and auto power-off jobs.
- RSS fetching with `NEW` merge rules and hidden-story preservation.
- Natural-language monitor scanning over titles and summaries, with alert
  pinning in the app and widget.

## Build

```bash
cd android
gradle :app:assembleDebug
```

For Pixel 9 setup and manual widget verification, see
[`docs/pixel-9-verification.md`](docs/pixel-9-verification.md).
