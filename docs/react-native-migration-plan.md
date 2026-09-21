# Moving the app to React Native

## Read this first

React Native cannot draw the widget. An Android home-screen widget is a
RemoteViews tree that the launcher inflates in its own process; only a small
fixed set of views is allowed, and no JavaScript engine runs there. Glance,
Compose and the RemoteViews they produce all stay in Kotlin whatever happens to
the rest of the app. Since the widget is the product here and the app is barely
used, a migration moves the part that matters least and leaves the hard part
exactly where it is.

It is still doable, and the plan below is the honest version of it: what moves,
what cannot, and what it costs.

## What the app is today

About 10,300 lines of Kotlin:

| Part | Lines | Moves to JavaScript? |
| --- | --- | --- |
| App screens (`ui/AiNewsApp.kt`) | 3,461 | Yes |
| Widget (`widget/NewsWidget.kt` and friends) | 1,900 | No - stays Kotlin |
| Repository and state (`data/NewsRepository.kt`) | 1,463 | Yes, or stays as the shared core |
| Models, theme, preference codecs | 1,100 | Yes |
| RSS, AI and image clients | 640 | Yes |
| Room storage, DAO, entities | 250 | Replaced by an RN database |
| Workers, alarms, notifications, tile | 500 | No - stays Kotlin |

So roughly 6,600 lines can become TypeScript and roughly 2,400 lines cannot.

## The shape that works

One app, two halves, with the storage as the boundary.

- **Kotlin keeps**: the widget and everything it touches, WorkManager and the
  alarm that refreshes below fifteen minutes, notifications, the quick-settings
  tile, and the widget configuration screen.
- **React Native takes**: the reading screens, feeds, monitors, digests,
  schedules, settings, the library and the AI usage screen.
- **They share the database.** The widget cannot call into JavaScript, so it
  must read stories from a store it owns. Keep Room as that store and expose it
  to JavaScript through a Turbo Module, rather than giving React Native its own
  database and trying to keep two copies in step.

The alternative - JavaScript owns the data and pushes it to the widget - means
the widget shows stale stories whenever the app has not run, which is most of
the time. Do not do that.

## Phases

**1. Shell (about a week).** Add React Native to the existing Gradle project as
a second source set; keep one APK. `MainActivity` gains a React root; the widget,
workers and Room are untouched. Ship it with one trivial screen behind a debug
flag to prove the build, the release signing and the app size.

**2. The bridge (one to two weeks).** A Turbo Module over `NewsRepository`:
stories, feeds, settings, runtime state as a subscribable stream, plus the
commands (refresh, save, hide, pin, mark read, run an AI action). One module,
typed with Codegen, so the JavaScript side never talks to Room directly. Every
write must end by asking the widget to redraw, the same way the Kotlin actions
do now - a redraw needs a real state change, or Glance ignores it.

**3. Screens, one at a time (four to six weeks).** Port in this order: the feed
list, story detail, library, feeds, settings, then the long tail - monitors,
digests, schedules, source health, fetch history, AI usage. Each screen ships
behind a flag that picks the Compose or the React version, so a bad port is one
setting away from being reverted.

**4. Remove the Compose app (a few days).** Delete `ui/AiNewsApp.kt` and its
theme plumbing once every screen has a React twin. `data/AppTheme.kt` stays: the
widget uses the same palettes.

**5. Tidy the boundary (a week).** Decide where the RSS, AI and image clients
live. Keeping them in Kotlin means the background refresh works with no
JavaScript running, which is what you want - the widget refreshes every ten
minutes whether or not anyone has opened the app. Porting them to TypeScript
means running headless JS from a worker, which is slower to start and fails more
often. Recommendation: leave them in Kotlin.

## What gets harder

- **Two languages for one behaviour.** A change to how stories are merged or
  deduplicated has to be understood in Kotlin and surfaced in TypeScript.
- **Start-up.** The React runtime adds roughly half a second on a cold start and
  about 8 MB to the APK. The widget is unaffected.
- **The acceptance run.** `scripts/widget-actions.sh` drives the widget through
  the accessibility tree and keeps working unchanged. The app-side checks in it -
  the settings screen, the AI key test - need new selectors, because React
  Native's tree is different.
- **Debug signing.** Keep `android/keystore/debug.keystore` as it is, or every
  install starts taking the widget off the home screen again.

## What gets easier

Only two things, honestly: the screens become faster to write and change, and if
an iOS version is ever wanted, the reading app comes nearly free. The iOS widget
would still have to be written from scratch in SwiftUI.

## Rough cost

Eight to eleven weeks of focused work to stand still - the same features, in two
languages instead of one. Worth it if an iOS app is coming or if someone who
writes TypeScript is going to own the screens. Not worth it for the widget,
which cannot benefit at all.

## A smaller option

If the goal is faster work on the screens rather than React specifically: the
Compose app is one 3,461 line file. Splitting it into a file per screen and
pulling the shared pieces into a small design layer costs a few days and gets
much of the same benefit, with no bridge, no second language and no risk to the
widget.
