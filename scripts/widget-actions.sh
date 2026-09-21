#!/usr/bin/env bash
# Drives every control the widget offers, from a clean install, and checks the
# widget really changed each time. Prints PASS/FAIL per action.
set -uo pipefail

ANDROID_SERIAL="${ANDROID_SERIAL:-$("$HOME/Library/Android/sdk/platform-tools/adb" devices | awk '/emulator-/{print $1; exit}')}"
export ANDROID_SERIAL
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHOTS="${SHOTS:-/tmp/ai-news-actions}"
SKIP_INSTALL="${SKIP_INSTALL:-0}"
rm -rf "$SHOTS"; mkdir -p "$SHOTS"
PASS=0; FAIL=0; FAILED=()

check() { # check <name> <0|1> [detail]
  if [ "$2" = "0" ]; then echo "PASS  $1${3:+  — $3}"; PASS=$((PASS+1))
  else echo "FAIL  $1${3:+  — $3}"; FAIL=$((FAIL+1)); FAILED+=("$1"); fi
}
widget() { "$HERE/ui_text.py" "$ADB"; }
has() { widget | grep -qiF "$1"; }
counter() { widget | grep -o '^[0-9]*-[0-9]* of [0-9]*' | head -1; }
status() { widget | grep -o '^Updated [^|]*' | head -1; }
titles() { widget | grep -v '^ |' | grep -vE '^(NEW|[0-9]+-[0-9]+ of|Updated |All Feeds)' | head -3 | tr '\n' ' '; }
point() { "$HERE/ui_find.py" "$ADB" "$1" "${2:-any}" 2>/dev/null; }
tap() { # tap <needle> [text|desc|any] [settle seconds]
  local p; p="$(point "$1" "${2:-any}")" || return 1
  [ -z "$p" ] && return 1
  "$ADB" shell input tap $p; sleep "${3:-4}"; return 0
}
find_widget() { # the pinned widget may sit on a later home page
  for _ in 0 1 2 3; do
    widget | grep -q . && return 0
    "$ADB" shell input swipe 900 1200 200 1200 200
    sleep 2
  done
  widget | grep -q .
}
home() { "$ADB" shell input keyevent KEYCODE_HOME; sleep 3; find_widget; }
wait_for() { # wait_for <needle> [seconds] — poll until the widget shows it
  local deadline=$(( SECONDS + ${2:-12} ))
  while [ $SECONDS -lt $deadline ]; do has "$1" && return 0; sleep 2; done
  return 1
}
wait_gone() { # wait_gone <needle> [seconds]
  local deadline=$(( SECONDS + ${2:-12} ))
  while [ $SECONDS -lt $deadline ]; do has "$1" || return 0; sleep 2; done
  return 1
}
scroll() { # scroll <down|up> [times]  — inside the widget's own list
  local box; box="$("$HERE/ui_box.py" "$ADB" list)" || return 1
  read -r x1 y1 x2 y2 <<<"$box"
  local cx=$(( (x1 + x2) / 2 )) top=$(( y1 + 60 )) bot=$(( y2 - 60 ))
  for _ in $(seq 1 "${2:-6}"); do
    if [ "$1" = down ]; then "$ADB" shell input swipe $cx $bot $cx $top 180
    else "$ADB" shell input swipe $cx $top $cx $bot 180; fi
    sleep 0.4
  done
  sleep 1
}
shot() { "$ADB" shell screencap -p /sdcard/s.png >/dev/null; "$ADB" pull /sdcard/s.png "$SHOTS/$1.png" >/dev/null 2>&1; }
images_ok() { shot "$1"; "$HERE/has_thumbnails.py" "$SHOTS/$1.png" >/dev/null 2>&1; }
reach() { # reach <needle> — scroll the list until the control is on screen
  point "$1" any >/dev/null && return 0
  for _ in 1 2 3 4 5 6 7 8; do
    scroll down 1
    point "$1" any >/dev/null && return 0
  done
  return 1
}

if [ "$SKIP_INSTALL" != "1" ]; then
  echo "== Clean install with the widget pinned to the home screen"
  SHOT="$SHOTS/00-install.png" "$HERE/widget-clean-install.sh" >"$SHOTS/install.log" 2>&1
  grep -q "^done" "$SHOTS/install.log" || { echo "FAIL  clean install"; tail -5 "$SHOTS/install.log"; exit 1; }
fi
"$ADB" logcat -c >/dev/null 2>&1
find_widget || { echo "FAIL  the widget is not on any home screen"; exit 1; }

N=0
for _ in $(seq 1 40); do
  N="$(status | grep -o '[0-9]*' | tail -1)"; [ "${N:-0}" -gt 20 ] 2>/dev/null && break; sleep 3
done
check "the widget shows a fetched feed" "$([ "${N:-0}" -gt 20 ] && echo 0 || echo 1)" "$(status)"
check "the story counter is there" "$(counter | grep -q 'of' && echo 0 || echo 1)" "$(counter)"
images_ok 01-first-page && check "thumbnails on the first page" 0 || check "thumbnails on the first page" 1
has "Fetching" && check "no stuck Fetching" 1 || check "no stuck Fetching" 0
has "Update failed" && check "no update failure" 1 || check "no update failure" 0

echo
echo "== Header"
tap "Power off" desc 3
wait_for "Power on" 25 || tap "Power off" desc 3
wait_for "Power on" 25 && check "power off" 0 "$(status)" || check "power off" 1 "$(status)"
tap "Power on" desc 3
wait_for "Power off" 20 && check "power on" 0 "$(status)" || check "power on" 1 "$(status)"

"$ADB" logcat -c
tap "Refresh" desc 3
OK=1; for _ in $(seq 1 30); do sleep 2; "$ADB" logcat -d | grep -q "AiNewsRefresh: fetched" && { OK=0; break; }; done
check "refresh fetches" "$OK"
wait_gone "Fetching" 30 && check "refresh finishes" 0 "$(status)" || check "refresh finishes" 1 "$(status)"
images_ok 02-after-refresh && check "thumbnails survive a refresh" 0 || check "thumbnails survive a refresh" 1

echo
echo "== Paging"
FIRST="$(titles)"
reach "Load next" && tap "Load next" any 7
C2="$(counter)"
[ -n "$C2" ] && [ "$C2" != "1-10 of ${N}" ] && check "load next stories" 0 "$C2" || check "load next stories" 1 "$C2"
[ "$FIRST" != "$(titles)" ] && check "the second page lands on new stories at the top" 0 || check "the second page lands on new stories at the top" 1
images_ok 03-page-two && check "thumbnails on the second page" 0 || check "thumbnails on the second page" 1
has "Load previous" && check "the previous-page button appears" 0 || check "the previous-page button appears" 1

reach "Load next" && tap "Load next" any 7
C3="$(counter)"
[ "$C3" != "$C2" ] && check "load next a second time" 0 "$C3" || check "load next a second time" 1 "$C3"
reach "Load next" && tap "Load next" any 7
C4="$(counter)"
[ "$C4" != "$C3" ] && check "load next a third time" 0 "$C4" || check "load next a third time" 1 "$C4"
images_ok 04-page-four && check "thumbnails on the fourth page" 0 || check "thumbnails on the fourth page" 1

tap "Load previous" any 7
[ "$(counter)" = "$C3" ] && check "load previous" 0 "$(counter)" || check "load previous" 1 "$(counter)"
tap "Back to the newest stories" desc 7
case "$(counter)" in "1-10 of"*) check "back to the newest" 0 "$(counter)";; *) check "back to the newest" 1 "$(counter)";; esac
has "Load previous" && check "the reset really shows the newest stories" 1 "still on a later page" || check "the reset really shows the newest stories" 0

echo
echo "== Footer"
tap "Show unread only" desc 3
wait_for "Show every story" 20 && check "unread filter on" 0 || check "unread filter on" 1
tap "Show every story" desc 3
wait_for "Show unread only" 20 && check "unread filter off" 0 || check "unread filter off" 1
tap "Widget settings" desc 6
"$ADB" shell dumpsys activity activities | grep -q "NewsWidgetConfigureActivity" && check "widget settings opens" 0 || check "widget settings opens" 1
"$ADB" shell input keyevent KEYCODE_BACK; sleep 2; home
HAD_NEW="$(widget | grep -c '^NEW')"
tap "Mark everything read" desc 3
for _ in 1 2 3 4 5 6 7 8; do NOW_NEW="$(widget | grep -c '^NEW')"; [ "$NOW_NEW" -lt "$HAD_NEW" ] && break; sleep 2; done
[ "$NOW_NEW" -lt "$HAD_NEW" ] || [ "$HAD_NEW" = "0" ] && check "mark everything read" 0 "$HAD_NEW -> $NOW_NEW" || check "mark everything read" 1 "$HAD_NEW -> $NOW_NEW"

echo
echo "== A story's own buttons"
if tap "Save story" desc 3; then
  wait_for "Remove from library" 20 && check "save a story" 0 || check "save a story" 1
  tap "Remove from library" desc 3 && wait_for "Save story" 20 && check "unsave a story" 0 || check "unsave a story" 1
else check "save a story" 1 "no save button"; fi

if tap "Show the whole story" desc 5; then
  reach "Show less" && check "expand a story" 0 || check "expand a story" 1
  tap "Show less" desc 5 && check "collapse a story" 0 || check "collapse a story" 1
else check "expand a story" 1 "no expand button"; fi

scroll up 6
BEFORE="$(titles)"
if tap "Hide story" desc 6; then
  [ "$BEFORE" != "$(titles)" ] && check "hide a story" 0 || check "hide a story" 1
else check "hide a story" 1 "no hide button"; fi

if tap "Share story" desc 5; then
  "$ADB" shell dumpsys activity activities | grep -qiE "ChooserActivity|ResolverActivity" && check "share opens the sheet" 0 || check "share opens the sheet" 1
  "$ADB" shell input keyevent KEYCODE_BACK; sleep 2; home
else check "share opens the sheet" 1 "no share button"; fi

echo
echo "== Opening things"
if tap "All Feeds" text 6; then
  "$ADB" shell dumpsys activity activities | grep -q "com.ainews.android/.MainActivity" && check "the title opens the app" 0 || check "the title opens the app" 1
  home
else check "the title opens the app" 1; fi

STORY="$(widget | grep -vE '^ \||^(NEW|Updated|All Feeds|[0-9]+-[0-9]+ of)' | sed 's/ |.*//' | awk 'length > 24' | head -1)"
if [ -n "$STORY" ] && tap "${STORY:0:18}" text 7; then
  "$ADB" shell dumpsys activity activities | grep -qiE "com.android.chrome|CustomTabActivity|BrowserActivity" && check "a story opens in the browser" 0 || check "a story opens in the browser" 1
  home
else check "a story opens in the browser" 1 "no story to tap"; fi

echo
echo "== Stack mode"
tap "Show stack mode" desc 3
wait_for "Next story" 20 && check "switch to stack mode" 0 || check "switch to stack mode" 1
S1="$(titles)"
tap "Next story" desc 5
[ "$S1" != "$(titles)" ] && check "next story" 0 || check "next story" 1
tap "Previous story" desc 5
[ "$S1" = "$(titles)" ] && check "previous story" 0 || check "previous story" 1
images_ok 05-stack && check "thumbnail in stack mode" 0 || check "thumbnail in stack mode" 1
if tap "Pin story" desc 5; then
  has "Unpin story" && check "pin a story" 0 || check "pin a story" 1
  tap "Unpin story" desc 5 && check "unpin a story" 0 || check "unpin a story" 1
else check "pin a story" 1 "no pin button"; fi
if tap "Copy link" desc 4; then check "copy link" 0; else check "copy link" 1 "no copy button"; fi
if tap "Open summary" desc 6; then
  "$ADB" shell dumpsys activity activities | grep -q "com.ainews.android/.MainActivity" && check "open the summary" 0 || check "open the summary" 1
  home
else check "open the summary" 1 "no summary button"; fi
if tap "Open source" desc 7; then
  "$ADB" shell dumpsys activity activities | grep -qiE "com.android.chrome|CustomTabActivity|BrowserActivity" && check "open the source in the browser" 0 || check "open the source in the browser" 1
  home
else check "open the source in the browser" 1 "no open button"; fi
tap "Show column mode" desc 3
wait_for " of " 20 && check "back to column mode" 0 "$(counter)" || check "back to column mode" 1

echo
echo "== Health"
ERR="$("$ADB" logcat -d | grep -E "GlanceAppWidget|FATAL EXCEPTION|AndroidRuntime" | grep -c "ainews")"
[ "$ERR" = "0" ] && check "no widget errors in the log" 0 || check "no widget errors in the log" 1 "$ERR lines"
has "Update failed" && check "no failure message left on screen" 1 || check "no failure message left on screen" 0
shot 99-final

echo
echo "$PASS passed, $FAIL failed.  Screenshots in $SHOTS"
[ "$FAIL" = "0" ] || printf 'failed: %s\n' "${FAILED[@]}"
[ "$FAIL" = "0" ]
