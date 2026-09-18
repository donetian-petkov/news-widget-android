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

The repository has been initialized with the product and implementation plan.
The next step is to create the native Android project skeleton under `android/`.
